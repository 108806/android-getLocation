package com.example.get_location;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.w3c.dom.Text;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private Location oldLocation;

    private double globalDist;
    private long startTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView terminalTextView = findViewById(R.id.terminalTextView);
        terminalTextView.setText("Waiting for the data...\n");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationRequest();
        createLocationCallback();
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setSmallestDisplacement(0.00001f);
        locationRequest.setInterval(2500);
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

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView textView = findViewById(R.id.terminalTextView);
                            long currentTimeMillis = System.currentTimeMillis();
                            if (startTime == 0) startTime = currentTimeMillis;
                            //long epochTime = getEpochTime(currentTimeMillis);
                            String humanReadableTime = getHumanReadableTime(currentTimeMillis);
                            long timeDiffMillis = currentTimeMillis - startTime;
                            String timeStamp = String.valueOf((timeDiffMillis / 3600000)) + "h "
                                    +  (timeDiffMillis / 600000) + "m "
                                    +  (timeDiffMillis / 10000 ) + "s @ " + humanReadableTime;
                            textView.setText(timeStamp);
                            String locData = "\nLat: " + latitude + "\nLon: " + longitude + "," + location.getTime();
                            textView.append(locData);

                            if (oldLocation != null) {
                                long oldTime = oldLocation.getTime(), newTime = location.getTime();
                                long timeDiff = newTime - oldTime;
                                double speedKmph = SpeedCalculator.calculateSpeed(oldLocation, location, timeDiff);
                                double dist = SpeedCalculator.calculateDistance(oldLocation.getLatitude(), oldLocation.getLongitude(), latitude, longitude);
                                globalDist += dist;
                                String moveData = String.format(Locale.GERMANY, "\nSPEED: %.3f kmph", speedKmph)
                                        + String.format(Locale.GERMANY, "\nTIME: %03d", timeDiff)
                                        + String.format(Locale.GERMANY, "\nDIST: %.3f m", dist)
                                        + String.format(Locale.GERMANY, "\nGLOBAL DIST: %.3f m\n", globalDist);
                                textView.append(moveData);
                                textView.scrollBy(0, 256);
                            }
                        }
                    });
                    Log.d("createLocationCallback", "Lat:" + latitude + ", Lon:" + longitude + ", " + prov);
                    oldLocation = location;
                }
            }
        };
    }

    public class SpeedCalculator {
        private static final double EARTH_RADIUS_KM = 6371;
        private static final double EARTH_RADIUS_M = 6371000;

        public static double calculateSpeed(Location startPoint, Location endPoint, long timeDifference) {
            // Calculate distance using Haversine formula
            double distance = calculateDistance(startPoint.getLatitude(), startPoint.getLongitude(),
                    endPoint.getLatitude(), endPoint.getLongitude());

            // Calculate speed based on distance and time difference
            double speedMps = distance / timeDifference; // Speed in meters per second
            double speedKmph = speedMps * 3.6; // Speed in kilometers per hour

            Log.d("Speed", "Speed in m/s: " + speedMps);
            Log.d("Speed", "Speed in km/h: " + speedKmph);

            return speedKmph;
        }

        private static double calculateDistance(double startLat, double startLon, double endLat, double endLon) {
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
        stopLocationUpdates();
    }

    private boolean checkLocationPermission() {
        int permissionState = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                Log.e("onRequest", "Permissions denied.");
            }
        }
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
}