package com.artha.rntracking;

import android.*;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.facebook.common.activitylistener.ActivityListener;
import com.facebook.react.ReactActivity;
import com.facebook.react.ReactActivityDelegate;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactBridge;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.common.ReactConstants;
import com.facebook.react.bridge.Callback;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.io.File;
import java.io.FileWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by arthaprihardana on 27/01/18.
 */

public class TrackerServiceModule extends ReactContextBaseJavaModule implements LocationListener {

    final ReactApplicationContext reactContext;

    private static final String TAG = TrackerServiceModule.class.getSimpleName();
    public static final String STATUS_INTENT = "status";

    private static final int PERMISSIONS_REQUEST = 1;
    private static String[] PERMISSIONS_REQUIRED = new String[]{
            android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int NOTIFICATION_ID = 1;
    private static final int FOREGROUND_SERVICE_ID = 1;
    private static final int CONFIG_CACHE_EXPIRY = 600;  // 10 minutes.

    private GoogleApiClient mGoogleApiClient;
    private DatabaseReference mFirebaseTransportRef;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;
    private LinkedList<Map<String, Object>> mTransportStatuses = new LinkedList<>();
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationBuilder;
    private PowerManager.WakeLock mWakelock;

    private SharedPreferences mPrefs;

    public TrackerServiceModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "TrackerService";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        return constants;
    }

    @ReactMethod
    public void connect(
            String transport_id,
            String email,
            String password,
            Callback successCallback,
            Callback errorCallback) {
        if(transport_id.length() == 0 || email.length() == 0 || password.length() == 0) {
            Toast.makeText(getReactApplicationContext(), R.string.missing_inputs, Toast.LENGTH_SHORT).show();
            errorCallback.invoke(false);
        } else {
            mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
            FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                    .setDeveloperModeEnabled(BuildConfig.DEBUG)
                    .build();
            mFirebaseRemoteConfig.setConfigSettings(configSettings);
            mFirebaseRemoteConfig.setDefaults(R.xml.remote_config_defaults);

            authenticate(transport_id, email, password);
            successCallback.invoke(true);
        }
    }

    @ReactMethod
    public void stopLocationService() {
        Toast.makeText(getReactApplicationContext(), "Stop Tracking", Toast.LENGTH_SHORT).show();
        mGoogleApiClient = new GoogleApiClient.Builder(reactContext)
                .addConnectionCallbacks(mLocationRequestCallback)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.disconnect();
//        getReactApplicationContext().stopService(new Intent(getReactApplicationContext(), TrackerServiceModule.class));
//        reactContext.stopService(new Intent(reactContext, TrackerServiceModule.class));
//        stopService(new Intent(this, TrackerService.class));
    }

    private void authenticate(final String transport_id, String email, String password) {
        final FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.signInAnonymously()
            .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    Log.i(TAG, "authenticate: " + task.isSuccessful());
                    if (task.isSuccessful()) {
                        fetchRemoteConfig();
                        loadPreviousStatuses(transport_id);
                    } else {
                        Toast.makeText(getReactApplicationContext(), R.string.auth_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            });
    }

    private void fetchRemoteConfig() {
        long cacheExpiration = CONFIG_CACHE_EXPIRY;
        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }
        mFirebaseRemoteConfig.fetch(cacheExpiration)
            .addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.i(TAG, "Remote config fetched");
                    mFirebaseRemoteConfig.activateFetched();
                }
            });
    }

    /**
     * Loads previously stored statuses from Firebase, and once retrieved,
     * start location tracking.
     */
    private void loadPreviousStatuses(String transport_id) {
        String transportId = transport_id;
        FirebaseAnalytics.getInstance(getReactApplicationContext()).setUserProperty("transportID", transportId);
        String path = reactContext.getString(R.string.firebase_path) + transport_id;
        mFirebaseTransportRef = FirebaseDatabase.getInstance().getReference(path);
        mFirebaseTransportRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot != null) {
                    for (DataSnapshot transportStatus : snapshot.getChildren()) {
                        mTransportStatuses.add(Integer.parseInt(transportStatus.getKey()),
                                (Map<String, Object>) transportStatus.getValue());
                    }
                }
                startLocationTracking();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // TODO: Handle gracefully
            }
        });
    }

    private GoogleApiClient.ConnectionCallbacks mLocationRequestCallback = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(Bundle bundle) {
            LocationRequest request = new LocationRequest();
            request.setInterval(mFirebaseRemoteConfig.getLong("LOCATION_REQUEST_INTERVAL"));
            request.setFastestInterval(mFirebaseRemoteConfig.getLong("LOCATION_REQUEST_INTERVAL_FASTEST"));
            request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, request, TrackerServiceModule.this);

            //  Hold a partial wake lock to keep CPU awake when the we're tracking location.
            PowerManager powerManager = (PowerManager) reactContext.getSystemService(Context.POWER_SERVICE);
            mWakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
            mWakelock.acquire();
        }

        @Override
        public void onConnectionSuspended(int reason) {
            // TODO: Handle gracefully
        }
    };

    /**
     * Starts location tracking by creating a Google API client, and
     * requesting location updates.
     */
    private void startLocationTracking() {
        mGoogleApiClient = new GoogleApiClient.Builder(reactContext)
                .addConnectionCallbacks(mLocationRequestCallback)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    private void shutdownAndScheduleStartup(int when) {
        Log.i(TAG, "overnight shutdown, seconds to startup: " + when);
        com.google.android.gms.gcm.Task task = new OneoffTask.Builder()
                .setService(TrackerTaskService.class)
                .setExecutionWindow(when, when + 60)
                .setUpdateCurrent(true)
                .setTag(TrackerTaskService.TAG)
                .setRequiredNetwork(com.google.android.gms.gcm.Task.NETWORK_STATE_ANY)
                .setRequiresCharging(false)
                .build();
        GcmNetworkManager.getInstance(getReactApplicationContext()).schedule(task);
    }

    private float getBatteryLevel() {
        Intent batteryStatus = reactContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int batteryLevel = -1;
        int batteryScale = 1;
        if (batteryStatus != null) {
            batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, batteryLevel);
            batteryScale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, batteryScale);
        }
        return batteryLevel / (float) batteryScale * 100;
    }

    /**
     * Determines if the current location is approximately the same as the location
     * for a particular status. Used to check if we'll add a new status, or
     * update the most recent status of we're stationary.
     */
    private boolean locationIsAtStatus(Location location, int statusIndex) {
        if (mTransportStatuses.size() <= statusIndex) {
            return false;
        }
        Map<String, Object> status = mTransportStatuses.get(statusIndex);
        Location locationForStatus = new Location("");
        locationForStatus.setLatitude((double) status.get("lat"));
        locationForStatus.setLongitude((double) status.get("lng"));
        float distance = location.distanceTo(locationForStatus);
        Log.d(TAG, String.format("Distance from status %s is %sm", statusIndex, distance));
        return distance < mFirebaseRemoteConfig.getLong("LOCATION_MIN_DISTANCE_CHANGED");
    }

    private void logStatusToStorage(Map<String, Object> transportStatus) {
        try {
            File path = new File(Environment.getExternalStoragePublicDirectory(""),
                    "transport-tracker-log.txt");
            if (!path.exists()) {
                path.createNewFile();
            }
            FileWriter logFile = new FileWriter(path.getAbsolutePath(), true);
            logFile.append(transportStatus.toString() + "\n");
            logFile.close();
        } catch (Exception e) {
            Log.e(TAG, "Log file error", e);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        fetchRemoteConfig();

        long hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int startupSeconds = (int) (mFirebaseRemoteConfig.getDouble("SLEEP_HOURS_DURATION") * 3600);
        if (hour == mFirebaseRemoteConfig.getLong("SLEEP_HOUR_OF_DAY")) {
            shutdownAndScheduleStartup(startupSeconds);
            return;
        }

        Map<String, Object> transportStatus = new HashMap<>();
        transportStatus.put("lat", location.getLatitude());
        transportStatus.put("lng", location.getLongitude());
        transportStatus.put("time", new Date().getTime());
        transportStatus.put("power", getBatteryLevel());

        if (locationIsAtStatus(location, 1) && locationIsAtStatus(location, 0)) {
            // If the most recent two statuses are approximately at the same
            // location as the new current location, rather than adding the new
            // location, we update the latest status with the current. Two statuses
            // are kept when the locations are the same, the earlier representing
            // the time the location was arrived at, and the latest representing the
            // current time.
            mTransportStatuses.set(0, transportStatus);
            // Only need to update 0th status, so we can save bandwidth.
            mFirebaseTransportRef.child("0").setValue(transportStatus);
        } else {
            // Maintain a fixed number of previous statuses.
            while (mTransportStatuses.size() >= mFirebaseRemoteConfig.getLong("MAX_STATUSES")) {
                mTransportStatuses.removeLast();
            }
            mTransportStatuses.addFirst(transportStatus);
            // We push the entire list at once since each key/index changes, to
            // minimize network requests.
            mFirebaseTransportRef.setValue(mTransportStatuses);
        }

        if (BuildConfig.DEBUG) {
            logStatusToStorage(transportStatus);
        }

        NetworkInfo info = ((ConnectivityManager) reactContext.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        boolean connected = info != null && info.isConnectedOrConnecting();
//        Toast.makeText(getReactApplicationContext(), connected ? R.string.tracking : R.string.not_tracking, Toast.LENGTH_SHORT).show();
//        setStatusMessage(connected ? R.string.tracking : R.string.not_tracking);
    }
}
