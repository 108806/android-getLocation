package com.example.get_location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOError;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private Location oldLocation;

    private double globalDist;
    private long startTime = 0;
    List<ScanResult> scanResults;

    private SensorManager sensorManager;
    private Sensor magnetometer, accelerometer;
    private final float[] lastMagnetometerValues = new float[3];
    private final float[] lastAccelerometerValues = new float[3];
    private boolean hasLastMagnetometerValues = false;
    private boolean hasLastAccelerometerValues = false;
    private String moveDirection, address;

    private int[] gsmData;
    private Stream s;
    private final long launchTime = System.currentTimeMillis();
    public static String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission_group.SENSORS,
    };

    Map<String, HashMap<String, Object>> globalWifiMap = new ConcurrentHashMap<>();
    Map <String, HashMap<String, Object>> globalGsmMap = new ConcurrentHashMap<>();
    public static File wlanDataFile, gsmDataFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView terminalTextView = findViewById(R.id.terminalTextView);
        terminalTextView.setText("Waiting for the data...\n");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationRequest();
        createLocationCallback();

        // Initialize SensorManager and sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        Log.d("magnetometer VENDOR:", magnetometer.getVendor());
        // Register sensor listeners
        sensorManager.registerListener((SensorEventListener) this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener((SensorEventListener) this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        if (checkPermissions()) {
            startWifiScan();
        } else {
            requestLocationPermission();
        }

        String CWD = Environment.getExternalStorageDirectory().getPath();
        CWD = emulateMyC0KK(CWD);
        wlanDataFile = new File(CWD + "/" + "wlan_data.json");
        if (!wlanDataFile.exists()) {
            try {
                FileOutputStream fos = new FileOutputStream(wlanDataFile); /// not working too
                fos.close();
                Log.d("Create new file:", "File created:" + wlanDataFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e("Create new file:", "Cannot create the file: " + wlanDataFile.getAbsolutePath());
                throw new RuntimeException(e); // Cannot create the file: /storage/sdcard0/wlan_data.json
            }
        }else{
            boolean backupSuccess = createBackup(wlanDataFile);
            Log.v("Backup Success: ", Boolean.toString(backupSuccess));
            if (!backupSuccess) Log.e("BKP ERROR:", "NO BACKUP CREATED!");
        }

        gsmDataFile = new File(CWD + "/" + "gsm_data.json");
        if (!gsmDataFile.exists()){
            try {
                FileOutputStream fos = new FileOutputStream(gsmDataFile);
                fos.close();
                Log.d("Create new file:", "File created:" + gsmDataFile.getAbsolutePath());
            }catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else{
            createBackup(gsmDataFile);
        }

        Gson gson = new Gson();
        try {
            String jsonContentWLAN = new String(Files.readAllBytes(wlanDataFile.toPath()));
            if (!jsonContentWLAN.isEmpty()) {
                Type hashMapType = new TypeToken<HashMap<String, HashMap<String, Object>>>() {
                }.getType();
                globalWifiMap = gson.fromJson(jsonContentWLAN, hashMapType);
                Log.d("Wlan Data found:", globalWifiMap.size() + " : " + wlanDataFile.toString());

            } else {
                Log.e("JsonReader:", "Empty JSON content in " + wlanDataFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("JsonReader:", "Error reading the " + wlanDataFile);
        }
        try {
            String jsonContentGSM = new String(Files.readAllBytes(gsmDataFile.toPath()));
            if (!jsonContentGSM.isEmpty()) {
                Type hashMapType = new TypeToken<HashMap<String, HashMap<String, Object>>>() {
                }.getType();
                globalGsmMap = gson.fromJson(jsonContentGSM, hashMapType);
                Log.d("GSM Data found:", globalGsmMap.size() + " : " + gsmDataFile.toString());

            } else {
                Log.e("JsonReader:", "Empty JSON content in " + gsmDataFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("JsonReader:", "Error reading the " + gsmDataFile);
        }

    }

    //branch test
    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d("onSensorChanged: ", event.toString());
        if (event.sensor == magnetometer) {
            System.arraycopy(event.values, 0, lastMagnetometerValues, 0, event.values.length);
            hasLastMagnetometerValues = true;
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccelerometerValues, 0, event.values.length);
            hasLastAccelerometerValues = true;
        }

        float sigPwr = event.sensor.getPower();

        if (hasLastAccelerometerValues && hasLastMagnetometerValues) {
            float[] rotationMatrix = new float[9];
            if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometerValues, lastMagnetometerValues)) {
                float[] orientationValues = new float[3];
                SensorManager.getOrientation(rotationMatrix, orientationValues);
                String dirFromAz = null;
                // Calculate the orientation in degrees
                float azimuth = (float) Math.toDegrees(orientationValues[0]);
                if (azimuth < 0) {
                    azimuth += 360;
                }

                // Update the compass direction
                dirFromAz = getDirectionFromAzimuth(azimuth);
                TextView compassTextView = findViewById(R.id.CompassTextView);
                compassTextView.setText(azimuth + "°" + dirFromAz + " @\n" + address + " @ " + sigPwr);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d("onAccuracyChanged: ", String.valueOf(accuracy));
    }


    private int[] getGsmSignalInfo() {
        int[] gsmData = new int[3]; // To store CID, LAC, and Signal Strength
//        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
//        HashMap<String, HashMap<String, Object>> currentResults = new HashMap<>();
//        if (telephonyManager != null) {
//            try {
//                CellTypeUtil cellTypeUtil;
//                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                    // TODO: Consider calling
//                    //    ActivityCompat#requestPermissions
//                    // here to request the missing permissions, and then overriding
//                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                    //                                          int[] grantResults)
//                    // to handle the case where the user grants the permission. See the documentation
//                    // for ActivityCompat#requestPermissions for more details.
//                    Log.e("getGsmSignalInfo:", "No permissions.");
//                    return null;
//                }
//                for (CellInfo cellInfo : telephonyManager.getAllCellInfo()) {
//                    HashMap<String, Object> paramsMap = extractParamsFromCellinfoString(cellInfo.toString());
//                    paramsMap.put("addr", address);
//                    paramsMap.put("loc", new double[]{oldLocation.getLatitude(), oldLocation.getLongitude()});
//                    paramsMap.put("time", getEpochTime(System.currentTimeMillis()));
//                    //TODO: paramsMap.put("type", cellTypeUtil()
//                    String identifier = paramsMap.get("MCC") + "_" +
//                            paramsMap.get("MNC") + "_" +
//                            paramsMap.get("PCI") + "_" +
//                            paramsMap.get("EARFCN");
//                    if (currentResults.containsKey(identifier)) {
//                        identifier += "_WEIRD_DUP";
//                    }
//                    currentResults.put(identifier, paramsMap);
//                    globalGsmMap.put(identifier, paramsMap);
//                }
//            } catch (Exception e) {
//                Log.e("getGsmSignalInfo", "Error retrieving GSM signal info: " + e.toString());
//                // Provide fallback values in case of failure
//                gsmData[0] = -1; // Invalid CID
//                gsmData[1] = -1; // Invalid LAC
//                gsmData[2] = -1; // Invalid Signal Strength
//            }
//        }
        return gsmData;
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setSmallestDisplacement(0.00001f);
        locationRequest.setInterval(2500);

    }

    private boolean checkPermissions() {
        for (String permission : permissions) {
            int permissionState = ContextCompat.checkSelfPermission(this, permission);
            if (permissionState != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startWifiScan();
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Code: " + PERMISSION_REQUEST_CODE + " : DENIED", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startWifiScan() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            wifiManager.startScan();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            scanResults = wifiManager.getScanResults();
        }
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                startWifiScan();
                for (Location location : locationResult.getLocations()) {
                    // Handle the obtained location
                    String prov = location.getProvider();
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();


                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Gson gson = new Gson();

                            boolean[] scans = new boolean[128];
                            int scanIdx = 0;
                            boolean anyDanger = IntStream.range(0, scans.length)
                                    .anyMatch(i -> scans[i]);
                            TextView statusView = findViewById(R.id.statusTextView);
                            if (!anyDanger){
                                statusView.setBackgroundColor(Color.parseColor("#40EE60"));
                                statusView.setText(danger_code[0]);
                            }else{
                                statusView.setBackgroundColor(Color.parseColor("#EE4060"));
                                statusView.setText("DANGER DETECTED:");
                            }

                            TextView textView = findViewById(R.id.terminalTextView);
                            long currentTimeMillis = System.currentTimeMillis();
                            if (startTime == 0) startTime = currentTimeMillis;

                            String humanReadableTime = getHumanReadableTime(currentTimeMillis);
                            long timeDiffMillis = currentTimeMillis - startTime;
                            String timeStamp = timeDiffMillis / 3600000 + "h "
                                    + (timeDiffMillis / 60000 % 60000) + "m "
                                    + (timeDiffMillis / 1000 % 1000) + "s @ " + humanReadableTime
                                    + " SRs: " + scanResults.size();
                            textView.setText(timeStamp);
                            String locData = "\nLat: " + latitude + "\nLon: " + longitude + "," + location.getTime() + " map: " + globalWifiMap.size();
                            textView.append(locData);
                            TextView scrollView = findViewById(R.id.terminalScrollView);

                            // Calculate N/S/W/E direction
                            double bearing = oldLocation != null ? oldLocation.bearingTo(location) : 0.0;
                            moveDirection = getDirectionFromBearing(bearing);
                            address = getStreetName(latitude, longitude);
                            Log.d("direction: ", moveDirection + " : " + address + " @ " + bearing);

                            if (oldLocation != null) {
                                long oldTime = oldLocation.getTime(), newTime = location.getTime();
                                long timeDiff = newTime - oldTime;
                                double speedKmph = SpeedCalculator.calculateSpeed(oldLocation, location, timeDiff);
                                double dist = SpeedCalculator.calculateMoveDistance(oldLocation.getLatitude(), oldLocation.getLongitude(), latitude, longitude);
                                globalDist += dist;
                                String moveData = String.format(Locale.GERMANY, "\nSPEED: %.3f kmph", speedKmph)
                                        + String.format(Locale.GERMANY, " TIME: %03d", timeDiff)
                                        + String.format(Locale.GERMANY, " DIST: %.3f m ", dist)
                                        + "mDir:" + moveDirection
                                        + String.format(Locale.GERMANY, " GLOB DIST: %.3f m", globalDist);
                                textView.append(moveData);
                                textView.scrollBy(0, 256);
                                scrollView.setText("SRs:"+scanResults.size() + "\n");

//                                gsmData = getGsmSignalInfo();
//                                final String gsmDataForHuman = "\nCID:" + gsmData[0]
//                                        + "\nLAC: " + gsmData[1]
//                                        + "\nMCC: " + gsmData[2]
//                                        + "\nSTRENGTH: " + gsmData[3];
//                                TextView gsmView = findViewById(R.id.gsmView);
//                                gsmView.setText(gsmDataForHuman);
                            }


                            boolean DANGER_status = false;
                            int DANGER_reason = 0;

                            for (ScanResult sr : scanResults) {
                                try {
                                    String uniqueName = getUniqueName(sr.SSID, sr.BSSID);
                                    boolean DANGER = false;
                                    if (isBetter(globalWifiMap, uniqueName, sr.level)) {
                                        // Save the JSON object to a file

                                        HashMap<String, Object> jsonWLAN = new HashMap<String, Object>();
                                        //Todo: test all the cases.
                                        try {
                                            if (isSSIDCloned(sr, scanResults)){
                                                uniqueName.concat("_DUPLICATE_SSID");
                                                DANGER_status = true;
                                                DANGER_reason = 1; //Todo: make reasons array with selections too
                                                final int idx = getIndexOfScanResultBySSID(scanResults, sr.SSID);
                                                scans[idx] = true;
                                            }
                                            if (isBSSIDCloned(sr, scanResults)){
                                                uniqueName.concat("_DUPLICATE_BSSID");
                                                DANGER_status = true;
                                                DANGER_reason = 2;
                                                final int idx = getIndexOfScanResultBySSID(scanResults, sr.SSID);
                                                scans[idx] = true;
                                            }
                                            if (sr.level > 30){
                                                uniqueName.concat("_SUPER_SS");
                                                DANGER_status = true;
                                                DANGER_reason = 3;
                                                final int idx = getIndexOfScanResultBySSID(scanResults, sr.SSID);
                                                scans[idx] = true;
                                            }
                                            scans[scanIdx] = DANGER_status;
                                            scanIdx++;

                                            jsonWLAN.put("SSID", sr.SSID);
                                            jsonWLAN.put("BSSID", sr.BSSID);
                                            jsonWLAN.put("frequency", sr.frequency);
                                            jsonWLAN.put("channelWidth", sr.channelWidth);
                                            jsonWLAN.put("level", sr.level);
                                            jsonWLAN.put("loc", new double[]{latitude, longitude});
                                            jsonWLAN.put("dist", calculateWLANDistance(sr.level, sr.frequency));
                                            jsonWLAN.put("sec", sr.capabilities);
                                            jsonWLAN.put("addr", address);
                                            jsonWLAN.put("passpoint", sr.isPasspointNetwork());
                                            jsonWLAN.put("80211mcResponder", sr.is80211mcResponder());
                                            jsonWLAN.put("time", getEpochTime(System.currentTimeMillis()));
                                            jsonWLAN.put("danger", DANGER);
                                            globalWifiMap.put(uniqueName, jsonWLAN);
                                            if (DANGER) scrollView.setBackgroundColor(Color.parseColor("#DD1122"));
                                            scrollView.append("\ndataMap:" + globalWifiMap.size() + " vs " + scanResults.size() + "\nOK:" +
                                                    sr.SSID + " @ " + sr.BSSID + "-> " + sr.level);
                                        } catch (Exception e) {
                                            Log.e("jsonWLAN HashMap:", "Adding data to jsonWLAN failed.");
                                            e.printStackTrace();
                                        }
                                        try (FileWriter writer = new FileWriter(wlanDataFile, false)) {
                                            String dataMapJSON = gson.toJson(globalWifiMap);
                                            writer.write(dataMapJSON);
                                            final String TAG = "JSON file writer";
                                            Log.d(TAG, "WLAN data saved to file: " + wlanDataFile.getAbsolutePath());
                                        } catch (IOException e) {
                                            final String TAG = "JSON file writer";
                                            Log.e(TAG, "Cannot write to JSON.");
                                            e.printStackTrace();
                                        }

                                        if (DANGER_status){
                                            statusView.append(danger_code[DANGER_reason] + sr.SSID);
                                        }

                                    } else {
                                        Log.d("isBetter:", "We got better than:" + sr.toString());
                                        if (DANGER_status) scrollView.setBackgroundColor(Color.parseColor("#DD1122"));
                                        scrollView.append(sr.SSID + " @ " + sr.BSSID + "-> " + sr.level + "\n");
                                    }
                                } catch (Exception e) {
                                    Log.e("WRITER:", "ERROR.");
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    });
                    Log.d("createLocationCallback", "Lat:" + latitude + ", Lon:" + longitude + ", " + prov);
                    oldLocation = location;
                }
            }
        };
    }

    public int getIndexOfScanResultBySSID(List<ScanResult> scanResults, String targetSSID) {
        for (int i = 0; i < scanResults.size(); i++) {
            if (scanResults.get(i).SSID.equals(targetSSID)) {
                return i;
            }
        }
        return -1; // If not found
    }

    private boolean createBackup(File wlanDataFile) {
        File outputDir = new File(Environment.getExternalStorageDirectory(), "BKP");

        // Check if the output directory exists; if not, create it.
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                Log.e("Backup", "Failed to create backup directory");
                return false;
            }
        }
        File outputFile = new File(outputDir, String.valueOf(launchTime/1000)+"_"+wlanDataFile.getName());
        if (outputFile.exists()) {
            Log.e("BKP", "Backup already exists" + outputFile.getAbsolutePath());
            return false;
        }

        try (FileInputStream fis = new FileInputStream(wlanDataFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }

            Log.d("Backup", "File backed up successfully:" + wlanDataFile);

        } catch (IOException e) {
            Log.e("Backup", "Error backing up file", e);
        }
        return true;
    }

    public static final String[] danger_code = {
            "No danger found.", "SSID is cloned!",
            "BSSID is cloned!","Signal is SUPER STRONG!"
    };

    private boolean isSSIDCloned(ScanResult sr, List<ScanResult> scanResults) {
        final String ssid = sr.SSID;
        int idx = 0;
        for (ScanResult scanResult : scanResults){
            if (Objects.equals(scanResult.SSID, ssid)) {
                idx++;
                if (1 < idx) return true;
            }
        }
        return false;
    }
    private boolean isBSSIDCloned(ScanResult sr, List<ScanResult> scanResults){
        final String bssid = sr.BSSID;
        int idx = 0;
        for (ScanResult scanResult : scanResults){
            if (Objects.equals(scanResult.BSSID, bssid)) {
                idx++;
            }
            if(idx > 1) return true;
        }
        return false;
    }


    private String getStreetName(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && addresses.size() > 0) {
                Address address = addresses.get(0);
                return address.getAddressLine(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Unknown";
    }

    private String getDirectionFromBearing(double bearing) {
        if (bearing >= 337.5 || bearing < 22.5) {
            return "N";
        } else if (bearing >= 22.5 && bearing < 67.5) {
            return "NE";
        } else if (bearing >= 67.5 && bearing < 112.5) {
            return "E";
        } else if (bearing >= 112.5 && bearing < 157.5) {
            return "SE";
        } else if (bearing >= 157.5 && bearing < 202.5) {
            return "S";
        } else if (bearing >= 202.5 && bearing < 247.5) {
            return "SW";
        } else if (bearing >= 247.5 && bearing < 292.5) {
            return "W";
        } else if (bearing >= 292.5 && bearing < 337.5) {
            return "NW";
        }
        return "Unknown";
    }

    public HashMap<String, Object> extractParamsFromCellinfoString(String cellInfoString) {
        HashMap<String, Object> paramsMap = new HashMap<>();
        try {
            String[] parts = cellInfoString.split("\\s+");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid input string format.");
            }

            // Extract CellIdentityLte parameters
            String cellIdentity = parts[1];
            String[] identityParams = cellIdentity.split("\\{|\\}|mMcc=|mMnc=|mPci=|mEarfcn=");
            int mcc = Integer.parseInt(identityParams[2]);
            int mnc = Integer.parseInt(identityParams[4]);
            int pci = Integer.parseInt(identityParams[6]);
            int earfcn = Integer.parseInt(identityParams[8]);

            // Extract CellSignalStrengthLte parameters
            String signalStrength = parts[2];
            String[] signalParams = signalStrength.split("ss=| rsrp=| rsrq=| rssnr=| cqi=| ta=");
            int ss = Integer.parseInt(signalParams[1]);
            int rsrp = Integer.parseInt(signalParams[2]);
            int rsrq = Integer.parseInt(signalParams[3]);
            int rssnr = Integer.parseInt(signalParams[4]);
            int cqi = Integer.parseInt(signalParams[5]);
            int ta = Integer.parseInt(signalParams[6]);

            // Add parameters to the HashMap:
            // MCC (Mobile Country Code) - The mobile country code of the network operator.
            paramsMap.put("MCC", mcc);

            // MNC (Mobile Network Code) - The mobile network code of the network operator.
            paramsMap.put("MNC", mnc);

            // PCI (Physical Cell Identifier) - A unique identifier for the physical cell within the LTE network.
            paramsMap.put("PCI", pci);

            // EARFCN (E-UTRA Absolute Radio Frequency Channel Number) - The absolute radio frequency channel number used by the cell.
            paramsMap.put("EARFCN", earfcn);

            // SS (Signal Strength) - The signal strength of the cell in dBm (decibels relative to one milliwatt).
            paramsMap.put("SS", ss);

            // RSRP (Reference Signal Received Power) - The power level of the received signal reference signal in dBm.
            paramsMap.put("RSRP", rsrp);

            // RSRQ (Reference Signal Received Quality) - The quality of the received signal reference signal in dB.
            paramsMap.put("RSRQ", rsrq);

            // RSSNR (Reference Signal Signal-to-Noise Ratio) - The signal-to-noise ratio of the reference signal in dB.
            paramsMap.put("RSSNR", rssnr);

            // CQI (Channel Quality Indicator) - A measurement of the quality of the wireless channel.
            paramsMap.put("CQI", cqi);

             // TA (Timing Advance) - The timing advance value for the cell in units of micro-seconds (μs).
            paramsMap.put("TA", ta);


        } catch (Exception e) {
            Log.e("Params", "Error parsing string: " + e.getMessage());
        }

        return paramsMap;
    }

    public static class CellTypeUtil {

        public enum CellType {
            UNKNOWN,
            GSM,
            CDMA,
            WCDMA,
            LTE,
            TDSCDMA
        }

        public static CellType getCellTypeFromEarfcn(int earfcn) {
            if (isGsmFrequency(earfcn)) {
                return CellType.GSM;
            } else if (isCdmaFrequency(earfcn)) {
                return CellType.CDMA;
            } else if (isWcdmaFrequency(earfcn)) {
                return CellType.WCDMA;
            } else if (isLteFrequency(earfcn)) {
                return CellType.LTE;
            } else if (isTdscdmaFrequency(earfcn)) {
                return CellType.TDSCDMA;
            } else {
                return CellType.UNKNOWN;
            }
        }

        private static boolean isGsmFrequency(int earfcn) {
            // Check if the EARFCN falls within GSM frequency bands
            // Modify this condition based on the specific GSM bands you want to consider
            return (earfcn >= 900 && earfcn <= 1800);
        }

        private static boolean isCdmaFrequency(int earfcn) {
            // Check if the EARFCN falls within CDMA frequency bands
            // Modify this condition based on the specific CDMA bands you want to consider
            return (earfcn >= 1 && earfcn <= 1200);
        }

        private static boolean isWcdmaFrequency(int earfcn) {
            // Check if the EARFCN falls within WCDMA frequency bands
            // Modify this condition based on the specific WCDMA bands you want to consider
            return (earfcn >= 10562 && earfcn <= 10838);
        }

        private static boolean isLteFrequency(int earfcn) {
            // Check if the EARFCN falls within LTE frequency bands
            // Modify this condition based on the specific LTE bands you want to consider
            return (earfcn >= 1 && earfcn <= 3000);
        }

        private static boolean isTdscdmaFrequency(int earfcn) {
            // Check if the EARFCN falls within TD-SCDMA frequency bands
            // Modify this condition based on the specific TD-SCDMA bands you want to consider
            return (earfcn >= 9210 && earfcn <= 9659);
        }
    }


    private String getDirectionFromAzimuth(float azimuth) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = Math.round(azimuth / 45.0f) % 8;
        return directions[index];
    }


    public static class SpeedCalculator {
        private static final double EARTH_RADIUS_KM = 6371;
        private static final double EARTH_RADIUS_M = 6371000;

        public static double calculateSpeed(Location startPoint, Location endPoint, long timeDifference) {
            // Calculate distance using Haversine formula
            double distance = calculateMoveDistance(startPoint.getLatitude(), startPoint.getLongitude(),
                    endPoint.getLatitude(), endPoint.getLongitude());

            // Calculate speed based on distance and time difference
            double speedMps = distance / timeDifference; // Speed in meters per second
            double speedKmph = speedMps * 3.6; // Speed in kilometers per hour

            Log.d("Speed", "Speed in m/s: " + speedMps);
            Log.d("Speed", "Speed in km/h: " + speedKmph);

            return speedKmph;
        }

        private static double calculateMoveDistance(double startLat, double startLon, double endLat, double endLon) {
            double dLat = Math.toRadians(endLat - startLat);
            double dLon = Math.toRadians(endLon - startLon);

            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat))
                    * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

            // Calculate distance in meters
            double distanceMeters = EARTH_RADIUS_M * c;
            Log.d("calculateDistance", ": " + distanceMeters);
            return distanceMeters;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermissions()) {
            startLocationUpdates();
        } else {
            requestLocationPermission();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //stopLocationUpdates();
        Log.d("onPause", "Nah");
    }


    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private static String getHumanReadableTime(long millis) {
        // Create a SimpleDateFormat object with the desired format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        // Set the time zone to UTC or your desired time zone
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        // Convert the millisecond value to Date object
        Date date = new Date(millis);

        // Format the Date object to a human-readable string
        return sdf.format(date);
    }

    private static long getEpochTime(long millis) {
        // Divide the millisecond value by 1000 to get seconds
        return millis / 1000L;
    }

    private String getUniqueName(String ssid, String bssid) {
        return getHex(ssid) + "_" + getHex(bssid);
    }

    private String getHex(String string) {
        if (string != null && !(string.isEmpty())) {
            byte[] byteString = string.getBytes();
            StringBuilder stringBuilder = new StringBuilder();
            for (byte b : byteString) {
                stringBuilder.append(String.format("%02X", b));
            }
            return stringBuilder.toString();
        }
        return null;
    }

    public double calculateWLANDistance(int signalStrength, int frequency) {
        int referenceSignalStrength = -60; // Reference signal strength at 1 meter distance
        double referenceDistance = 1.0; // Reference distance in meters
        double exponent = 2.0; // Path loss exponent

        // Calculate the path loss
        double pathLoss = (referenceSignalStrength - signalStrength) / (10.0 * exponent);

        // Calculate the distance
        double distance = Math.pow(10.0, pathLoss) * referenceDistance;

        // Adjust distance for the frequency (optional)
        double frequencyAdjustment = 27.55;
        if (frequency != 0) {
            double frequencyInMHz = frequency / 1000.0;
            distance = distance * (frequencyAdjustment / frequencyInMHz);
        }
        return Math.round(distance * 100) / 100.0; // Round to 2 decimal places
    }

    private boolean isBetter(Map<String, HashMap<String, Object>> dataMap, String uniqueName, int level) {
        final boolean cond1 = dataMap.isEmpty(), cond2 = uniqueName.isEmpty(), cond3 = !dataMap.containsKey(uniqueName);
        if (cond1 || cond2 || cond3)
            return true;
        HashMap<String, Object> innerMap = dataMap.get(uniqueName);
        int oldLevel = 0;
        try {
            Object levelObj = innerMap.get("level");
            if (levelObj instanceof Integer) {
                oldLevel = (int) levelObj;
            } else if (levelObj instanceof String) {
                oldLevel = Integer.parseInt((String) levelObj);
            }
        } catch (NumberFormatException e) {
            Log.e("isBetter:", "Parsing oldLevel exception occurred:" + e);
        }
        return level > oldLevel;
    }

    private String createEmptyJSONFile(Boolean add_epoch, String CWD) {
        String fileName = null;
        try {
            // Define the file path
            if (add_epoch) {
                long epochTime = System.currentTimeMillis() / 1000;
                fileName = CWD + "/" + "wlan_data" + epochTime + ".json";
            } else {
                fileName = CWD + "/" + "wlan_data" + ".json";
            }

            File file = new File(fileName);

            // Create the directory if it doesn't exist
            File directory = file.getParentFile();
            //assert directory != null;
            if (directory != null && !directory.exists()) {
                boolean created = directory.mkdirs();
                Log.d("JSON file creator", "Directory created: " + directory.getAbsolutePath() + " : " + created);
            }

            boolean result = file.createNewFile();

            // Log a message indicating the file creation
            Log.d("JSON file creator", "New empty JSON file created: " + file.getAbsolutePath() + " : " + result);
            return fileName;
        } catch (IOException e) {
            Log.e("JSON creator ERROR:", fileName, e);
        }
        return fileName;
    }

    public String emulateMyC0KK(String CWD) {
        if (CWD.toLowerCase().contains("emulated")) {
            Log.d("Emulated in CWD", "Trying manual fix...");
            try {
                String writeDir = "/sdcard";
                if (new File(writeDir).isDirectory())
                    CWD = writeDir;
            } catch (IOError e) {
                Log.d("Manual fix:", "No such file : " + e);
            }
            try {
                String writeDir = "/sdcard0";
                if (new File(writeDir).isDirectory())
                    CWD = writeDir;
            } catch (IOError e) {
                Log.d("Manual fix:", "No such file : " + e);
            }
            try {
                String writeDir = "/storage/sdcard";
                if (new File(writeDir).isDirectory())
                    CWD = writeDir;
            } catch (IOError e) {
                Log.d("Manual fix:", "No such file : " + e);
            }
            try {
                String writeDir = "/storage/sdcard0";
                if (new File(writeDir).isDirectory())
                    CWD = writeDir;
            } catch (IOError e) {
                Log.d("Manual fix:", "No such file : " + e);
            }
        }
        return CWD;
    }
}