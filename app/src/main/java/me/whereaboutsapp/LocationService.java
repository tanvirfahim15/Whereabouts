package me.whereaboutsapp;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.Calendar;
import java.util.List;

import static me.whereaboutsapp.StaticGlobal.DEBUG;
import static me.whereaboutsapp.StaticGlobal.DELAY;


public class LocationService extends BroadcastReceiver {

    LocationManager locationManager;
    LocationListener locationListener;
    DBHelper db;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    public void onReceive(final Context context, Intent intent) {
        db = new DBHelper(context);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);

        Log.d(DEBUG,"Location Service Triggered");

        AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        Intent nextIntent = new Intent(context, LocationService.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 100, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= 23) {
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    DELAY, pendingIntent);
        }

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        long lastTime = Long.parseLong(sharedPref.getString("time","0"));
        if(lastTime+5*60*1000>System.currentTimeMillis()){
            return;
        }

        boolean gps_enabled = false;
        boolean network_enabled = false;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        try {
            gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch(Exception e) {
            Crashlytics.logException(e);}

        try {
            network_enabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch(Exception e) {
            Crashlytics.logException(e);}

        if(!gps_enabled&&!network_enabled){
            locationOnNotification("Location is Disabled","Tap here to continue",context);
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "101");
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Location is Disabled");
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);

            return;
        }
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                locationManager.removeUpdates(locationListener);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationRequestNotification("Location Permission is Denied","Tap here to continue",context);

            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "102");
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Location Permission is denied");
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
            locationManager = null;
            locationListener = null;
            return;
        }

        locationManager.requestLocationUpdates("gps", 10, 0, locationListener);
        List<String> providers = locationManager.getProviders(true);
        Location location =null;
        for (String provider : providers) {
            Location l = locationManager.getLastKnownLocation(provider);
            if (l == null) {
                continue;
            }
            if (location == null || l.getAccuracy() < location.getAccuracy()) {
                location = l;
            }
        }

        if(location!=null){

            String lastLatStr = sharedPref.getString("lat","---");
            String lastLngStr = sharedPref.getString("lng","---");
            if(!lastLatStr.equals("---")&&!lastLngStr.equals("---")){
                if(distFrom((float) location.getLatitude(),(float) location.getLongitude(),Float.parseFloat(lastLatStr),Float.parseFloat(lastLngStr))<250){
                    return;
                }
            }

            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("lat", String.valueOf(location.getLatitude()));
            editor.putString("lng", String.valueOf(location.getLongitude()));
            editor.putString("time", String.valueOf(System.currentTimeMillis()));
            editor.apply();

            Log.d(DEBUG,"Location inserted to database");

            LocationRecord locationRecord = new LocationRecord(System.currentTimeMillis(),location.getLatitude(),location.getLongitude());

            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "103");
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Location inserted to database");
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "location");
            bundle.putString(FirebaseAnalytics.Param.CONTENT, locationRecord.toString());
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
            db.insert(locationRecord);

        }else {
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "104");
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Location not found");
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
            Log.d(DEBUG,"Location Not Found");

        }
    }


    public void locationRequestNotification(String title, String message, Context context) {


        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = 111;
        String channelId = "channel-id";
        String channelName = "Channel Name";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    channelId, channelName, importance);
            notificationManager.createNotificationChannel(mChannel);
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher_icon)//R.mipmap.ic_launcher
                .setContentTitle(title)
                .setContentText(message)
                .setVibrate(new long[]{100, 250})
                .setLights(Color.YELLOW, 500, 5000)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setColor(ContextCompat.getColor(context, R.color.colorPrimary));

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntent(new Intent(context, LocationRequestActivity.class));
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);

        notificationManager.notify(notificationId, mBuilder.build());
    }


    public void locationOnNotification(String title, String message, Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = 112;
        String channelId = "channel-id";
        String channelName = "Channel Name";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    channelId, channelName, importance);
            notificationManager.createNotificationChannel(mChannel);
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher_icon)//R.mipmap.ic_launcher
                .setContentTitle(title)
                .setContentText(message)
                .setVibrate(new long[]{100, 250})
                .setLights(Color.YELLOW, 500, 5000)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setColor(ContextCompat.getColor(context, R.color.colorPrimary));

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntent(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        notificationManager.notify(notificationId, mBuilder.build());
    }

    public static float distFrom(float lat1, float lng1, float lat2, float lng2) {
        double earthRadius = 6371000;
        double dLat = Math.toRadians(lat2-lat1);
        double dLng = Math.toRadians(lng2-lng1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng/2) * Math.sin(dLng/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return (float) (earthRadius * c);
    }
}
