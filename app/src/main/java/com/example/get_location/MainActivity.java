package com.example.get_location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
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
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private Location oldLocation;

    private double globalDist;
    private long startTime = 0;
    List<ScanResult> scanResults;

    public static String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.WAKE_LOCK,
    };

    Map<String, HashMap<String, Object>> dataMap = new ConcurrentHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView terminalTextView = findViewById(R.id.terminalTextView);
        terminalTextView.setText("Waiting for the data...\n");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationRequest();
        createLocationCallback();
        if (checkLocationPermission()) {
            startWifiScan();
        } else {
            requestLocationPermission();
        }
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setSmallestDisplacement(0.00001f);
        locationRequest.setInterval(2500);
    }

    private boolean checkLocationPermission() {
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
                for (Location location : locationResult.getLocations()) {
                    // Handle the obtained location
                    String prov = location.getProvider();
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();

                    String CWD = Environment.getExternalStorageDirectory().getPath();
                    CWD = emulateMyC0KK(CWD);
                    File wlanDataFile = new File(CWD + "/" + "wlan_data.json");
                    if (!wlanDataFile.exists()) {
                        try {
                            //boolean result = wlanDataFile.createNewFile(); not working
                            FileOutputStream fos = new FileOutputStream(wlanDataFile); /// not working too
                            fos.close();
                            Log.d("Create new file:", "File created:" + wlanDataFile.getAbsolutePath());
                        } catch (IOException e) {
                            Log.e("Create new file:", "Cannot create the file: " + wlanDataFile.getAbsolutePath());
                            throw new RuntimeException(e); // Cannot create the file: /storage/sdcard0/wlan_data.json
                        }
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Gson gson = new Gson();

                            TextView textView = findViewById(R.id.terminalTextView);
                            long currentTimeMillis = System.currentTimeMillis();
                            if (startTime == 0) startTime = currentTimeMillis;

                            String humanReadableTime = getHumanReadableTime(currentTimeMillis);
                            long timeDiffMillis = currentTimeMillis - startTime;
                            String timeStamp = timeDiffMillis / 3600000 + "h "
                                    + (timeDiffMillis / 60000) + "m "
                                    + (timeDiffMillis / 1000) + "s @ " + humanReadableTime;
                            textView.setText(timeStamp);
                            String locData = "\nLat: " + latitude + "\nLon: " + longitude + "," + location.getTime();
                            textView.append(locData);

                            if (oldLocation != null) {
                                long oldTime = oldLocation.getTime(), newTime = location.getTime();
                                long timeDiff = newTime - oldTime;
                                double speedKmph = SpeedCalculator.calculateSpeed(oldLocation, location, timeDiff);
                                double dist = SpeedCalculator.calculateMoveDistance(oldLocation.getLatitude(), oldLocation.getLongitude(), latitude, longitude);
                                globalDist += dist;
                                String moveData = String.format(Locale.GERMANY, "\nSPEED: %.3f kmph", speedKmph)
                                        + String.format(Locale.GERMANY, "\nTIME: %03d", timeDiff)
                                        + String.format(Locale.GERMANY, "\nDIST: %.3f m", dist)
                                        + String.format(Locale.GERMANY, "\nGLOBAL DIST: %.3f m\n", globalDist);
                                textView.append(moveData);
                                textView.scrollBy(0, 256);
                            }

                            try {
                                String jsonContentWLAN = new String(Files.readAllBytes(wlanDataFile.toPath()));
                                if (!jsonContentWLAN.isEmpty()) {
                                    Type hashMapType = new TypeToken<HashMap<String, HashMap<String, Object>>>() {
                                    }.getType();
                                    dataMap = gson.fromJson(jsonContentWLAN, hashMapType);
                                    Log.d("Wlan Data:", wlanDataFile.toString());
                                } else {
                                    Log.e("JsonReader:", "Empty JSON content in " + wlanDataFile);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                Log.e("JsonReader:", "Error reading the " + wlanDataFile);
                            }

                            for (ScanResult sr : scanResults) {
                                try {
                                    String uniqueName = getUniqueName(sr.SSID, sr.BSSID);
                                    if (isBetter(dataMap, uniqueName, sr.level)) {
                                        // Save the JSON object to a file

                                        HashMap<String, Object> jsonWLAN = new HashMap<String, Object>();
                                        try {
                                            jsonWLAN.put("SSID", sr.SSID);
                                            jsonWLAN.put("BSSID", sr.BSSID);
                                            jsonWLAN.put("frequency", sr.frequency);
                                            jsonWLAN.put("channelWidth", sr.channelWidth);
                                            jsonWLAN.put("level", sr.level);
                                            jsonWLAN.put("loc", new double[]{latitude, longitude});
                                            jsonWLAN.put("dist", calculateWLANDistance(sr.level, sr.frequency));
                                            jsonWLAN.put("sec", sr.capabilities);
                                            jsonWLAN.put("time", getEpochTime(System.currentTimeMillis()));
                                            dataMap.put(uniqueName, jsonWLAN);
                                        } catch (Exception e) {
                                            Log.e("jsonWLAN HashMap:", "Adding data to jsonWLAN failed.");
                                            e.printStackTrace();
                                        }
                                        try (FileWriter writer = new FileWriter(wlanDataFile, false)) {
                                            String dataMapJSON = gson.toJson(dataMap);
                                            writer.write(dataMapJSON);
                                            final String TAG = "JSON file writer";
                                            Log.d(TAG, "WLAN data saved to file: " + wlanDataFile.getAbsolutePath());
                                        } catch (IOException e) {
                                            final String TAG = "JSON file writer";
                                            Log.e(TAG, "Cannot write to JSON.");
                                            e.printStackTrace();
                                        }
                                    } else {
                                        Log.d("isBetter:", "We got better than:" + sr.toString());
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
        if (checkLocationPermission()) {
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
        return millis / 1000;
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
        boolean cond1 = dataMap.isEmpty(), cond2 = uniqueName.isEmpty(), cond3 = !dataMap.containsKey(uniqueName);
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
        Log.d("isBetter:", "Accepting better:" + uniqueName + " : " + level);
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