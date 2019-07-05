package me.whereaboutsapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import java.util.Calendar;

import static me.whereaboutsapp.StaticGlobal.DELAY;

public class RestartService extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        Intent nextIntent = new Intent(context, LocationService.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 100, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= 23) {
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    10, pendingIntent);
        } else {
            alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP,
                    SystemClock.elapsedRealtime(),
                    DELAY, pendingIntent);
        }
    }
}
