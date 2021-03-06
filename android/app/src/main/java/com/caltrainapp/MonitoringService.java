package com.caltrainapp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;


public class MonitoringService extends Service {

    private static final String TAG = "TestingService";
    public static final String MY_FIRST_INTENT = "com.caltrainapp.MY_FIRST_INTENT";
    private LocationManager mLocationManager = null;
    private static final int MINIMUM_TIME_BETWEEN_UPDATES = 1;
    private static final float MINIMUM_DISTANCE_BETWEEN_UPDATES = 1;
    private String stationLat;
    private String stationLong;
    private String toneUri;
    private boolean audioValue = true;
    private boolean vibrateValue = true;
    private int minuteAlert = 1;
    public double lastLat;
    public double lastLong;
    public double currentLat;
    public double currentLong;
    public long lastMSeconds;
    public long currentMSeconds;
    Database db;
    LocationListener mLocationListener = new LocationListener(LocationManager.GPS_PROVIDER);
    IBinder mBinder;
    boolean mAllowRebind;
    NotificationCompat.Builder mBuilder;
    NotificationManager mNotifyMgr;
    int mNotificationId;

    public static final String ACTION_1 = "action_1";
    public static final int NOTIFICATION_ID = 1;

    public MonitoringService() {
        db = new Database(this);
    }

    private class LocationListener implements android.location.LocationListener {
        Location mLastLocation;
        public LocationListener(String provider) {
            Log.i(TAG, "LocationListener " + provider);
            mLastLocation = new Location(provider);
            Log.i(TAG, "Last Location: " + mLastLocation.toString());
        }

        @Override
        public void onLocationChanged(Location location) {
            mBuilder.setSound(null);
            mBuilder.mActions.clear();
            if (stationLat != null && stationLong != null) {
                float destLat = Float.parseFloat(stationLat);
                float destLong = Float.parseFloat(stationLong);
                Location destLocation = new Location("destLocation");
                destLocation.setLatitude(destLat);
                destLocation.setLongitude(destLong);
                currentLat = location.getLatitude();
                currentLong = location.getLongitude();
                double distChange = calculateDistance(lastLat, lastLong, currentLat, currentLong);
                currentMSeconds = java.lang.System.currentTimeMillis();
                long timeChange = currentMSeconds - lastMSeconds;

                lastMSeconds = currentMSeconds;
                lastLat = currentLat;
                lastLong = currentLong;
                double distanceMeters = location.distanceTo(destLocation);
                Intent myBroadcastIntent = new Intent(MY_FIRST_INTENT);

                PendingIntent pIntent = PendingIntent.getActivity(MonitoringService.this, (int) System.currentTimeMillis(), myBroadcastIntent, 0);
                double minutesAway = getMinutesAway((distChange*1000), timeChange, distanceMeters);
                if(minutesAway <= minuteAlert){
                    String currentText = "Get ready! Your stop is in " + String.format("%.1f", minutesAway) + " minutes!";
                    mBuilder.setContentText(currentText);
                    mBuilder.setContentTitle("Alert! Your stop is next!");
                    mBuilder.setContentIntent(pIntent);
                    displayNotification(mBuilder.mContext, mBuilder);
                } else {
                    String currentText = "You are " + String.format("%.1f", minutesAway) + " minutes away.";
                    mBuilder.setContentText(currentText);
                }
                mNotifyMgr.notify(
                        mNotificationId,
                        mBuilder.build());
                myBroadcastIntent.putExtra("distance", minutesAway);
                LocalBroadcastManager instance = LocalBroadcastManager.getInstance(MonitoringService.this);
                instance.sendBroadcast(myBroadcastIntent);
            }
            mLastLocation.set(location);
        }

        private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
            double theta = lon1 - lon2;
            double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
            dist = Math.acos(dist);
            dist = rad2deg(dist);
            dist = dist * 60 * 1.1515;
            dist = dist * 1.609344;
            return (dist);
        }

        private double deg2rad(double deg) {
            return (deg * Math.PI / 180.0);
        }

        private double rad2deg(double rad) {
            return (rad * 180.0 / Math.PI);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.e(TAG, "onStatusChanged: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.e(TAG, "onProviderEnabled: " + provider);
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.e(TAG, "onProviderDisabled: " + provider);
        }
    }

    private void initializeLocationManager() {
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }


    public double getMinutesAway(double distChange, long timeChange, double distanceMeters) {
        double speed = distChange/timeChange;
        double mSecondsAway = distanceMeters/speed;
        double minutesAway = (mSecondsAway/1000)/60;
        return minutesAway;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "onCreate");
        initializeLocationManager();
        try {
            lastMSeconds = java.lang.System.currentTimeMillis();
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, MINIMUM_TIME_BETWEEN_UPDATES, MINIMUM_DISTANCE_BETWEEN_UPDATES,
                    mLocationListener);
            Location location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                Context context = getApplicationContext();
                CharSequence text = "Cannot get current location. Please enable GPS and restart Station Alert.";
                int duration = Toast.LENGTH_SHORT;
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                Intent intent = new Intent();
                lastLat = location.getLatitude();
                lastLong = location.getLongitude();
            }
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "gps provider does not exist " + ex.getMessage());
        }
        mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.icon)
                        .setContentTitle("You are on your way!")
                        .setContentText("Getting distance...");
        mNotificationId = 1;
        mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (intent.hasExtra("stationLat") && intent.hasExtra("stationLong")) {
            stationLat = intent.getStringExtra("stationLat");
            stationLong = intent.getStringExtra("stationLong");
        }
        if (intent.hasExtra("minuteAlert")) {;
            minuteAlert = intent.getIntExtra("minuteAlert", 1);
        }
        if(intent.hasExtra("audioValue")) {
            audioValue = intent.getBooleanExtra("audioValue", true);
        }
        if (intent.hasExtra("vibrateValue")) {
            vibrateValue = intent.getBooleanExtra("vibrateValue", true);
        }
        if (!intent.hasExtra("tone")) {
            toneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString();
        }
        String action = intent.getAction();
        if (ACTION_1.equals(action)) {
            NotificationManagerCompat.from(this).cancel(1);
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return mAllowRebind;
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        super.onDestroy();
        if (mLocationManager != null) {
            try {
                mLocationManager.removeUpdates(mLocationListener);
            } catch (Exception ex) {
                Log.w(TAG, "fail to remove location listeners, ignore", ex);
            }
        }
    }

    public void displayNotification(Context context, NotificationCompat.Builder builder) {

        Intent action1Intent = new Intent(context, MonitoringService.class)
                .setAction(ACTION_1);

        PendingIntent action1PendingIntent = PendingIntent.getService(context, 0,
                action1Intent, PendingIntent.FLAG_ONE_SHOT);

        builder.addAction(new NotificationCompat.Action(0, "Close", action1PendingIntent));
        if (audioValue) {
            String[] selectionArgs = {"1"};
            Cursor ringtoneInfo = db.readDb(context, "id", "tone_uri", "station_alert_tone", selectionArgs);
            String uri = ringtoneInfo.getString(ringtoneInfo.getColumnIndex("tone_uri"));
            if (uri != null) {
                toneUri = uri;
            }
            builder.setSound(Uri.parse(toneUri));
        }
        if (vibrateValue) {
            builder.setVibrate(new long[] { 1000, 1000, 1000, 1000, 1000 });
        }
        builder.setLights(Color.CYAN, 3000, 3000);
        builder.mNotification.flags |= Notification.FLAG_INSISTENT;
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}