package com.pipit.agc.agc;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.pipit.agc.agc.data.DayRecord;
import com.pipit.agc.agc.data.DBRecordsSource;
import com.pipit.agc.agc.data.MySQLiteHelper;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Eric on 12/12/2015.
 */
public class AlarmManagerBroadcastReceiver extends BroadcastReceiver
{
    Context _context;
    String TAG = "AlarmManagerBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();
        _context=context;

        String purpose = intent.getStringExtra("purpose");
        Log.d(TAG, "Received broadcast for " + purpose);
        switch(purpose){
            case "daylogging" :
                doDayLogging(context);
                break;
            case "locationlogging" :
                doLocationCheck(context);
                break;
            default:
                break;
        }
        wl.release();
    }

    public void setAlarmForDayLog(Context context, Calendar calendar)
    {
        AlarmManager am =( AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, AlarmManagerBroadcastReceiver.class);
        i.putExtra("purpose", "daylogging");
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        am.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);
    }

    public void setAlarmForLocationLog(Context context){
        AlarmManager am =( AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, AlarmManagerBroadcastReceiver.class);
        i.putExtra("purpose", "locationlogging");
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.MINUTE, 10);
        am.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
        Log.d(TAG, "set alarm for location log, current time is " + System.currentTimeMillis()
            + " alarm set for " + calendar.getTimeInMillis());
    }
    public void CancelAlarm(Context context)
    {
        Intent intent = new Intent(context, AlarmManagerBroadcastReceiver.class);
        intent.putExtra("purpose", "locationlogging");
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }

    /**
     * Executed by Alarm Manager at midnight to add a new day into database
     */
    private void doDayLogging(Context context){
        //Logging
        Log.d(TAG, "Starting dayLogging");
        SharedPreferences prefs = context.getSharedPreferences(Constants.SHARED_PREFS, Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor editor = prefs.edit();
        String mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        String logUpdate = prefs.getString("locationlist", "none") + "\n" + "Alarm Manager Update at " + mLastUpdateTime;
        editor.putString("locationlist", logUpdate);
        editor.commit();

        //Progress the day
        DBRecordsSource datasource;
        datasource = DBRecordsSource.getInstance();
        if (datasource==null){
            DBRecordsSource.initializeInstance(new MySQLiteHelper(context));
            datasource = DBRecordsSource.getInstance();
        }
        datasource.openDatabase();
        DayRecord day = new DayRecord();
        day.setComment("You have not been to the gym");
        day.setDate(new Date());
        day.setHasBeenToGym(false);
        day.setIsGymDay(false);
        DayRecord dayRecord = datasource.createDayRecord(day);
        datasource.closeDatabase();
        Toast.makeText(context, "new day added!", Toast.LENGTH_LONG);

        //Show the gym status card in Newsfeed
        editor.putBoolean("showGymStatus", true);
    }

    /**
     *  This function checks if todays beentogym status needs to be updated
     *
     *  When a proximity alert is entered, there is a high chance of a false positive.
     *  Therefore when proximity receiver gets a broadcast, it will use AlarmManager to
     *  wait two or three minutes to check if the false positive has corrected itself (usually
     *  takes one minute).
     *
     *  This function will be executed after the allotted wait time. It simply checks if
     *  the prox alert has been nullified in the last two minutes. If not, it updates beentogym
     *  status.
     */
    private void doLocationCheck(Context context){
        SharedPreferences prefs = context.getSharedPreferences(Constants.SHARED_PREFS, Context.MODE_MULTI_PROCESS);
        if (!prefs.contains("justEntered")){
            Log.e(TAG, "No enter status found in doLocationCheck, shared preferences does not contain 'justEntered'");
            return;
        }
        String verdict;
        if (prefs.getBoolean("justEntered", false)){
            updateLastDayRecord(true);
            verdict="Prox alert accepted; Updating gym status";
            Log.d(TAG, verdict);
            Util.sendNotification(context, "Location Update", "Entered proximity at " + new Date());
        }
        else{
            verdict="Prox alert rejected as false positive";
            Log.d(TAG, verdict);
        }
        //Update logs
        SharedPreferences.Editor editor = prefs.edit();
        String lastLocation = prefs.getString("locationlist", "none");
        String body = lastLocation+"\n" + verdict;
        editor.putString("locationlist", body).commit();
    }

    private void updateLastDayRecord(boolean beenToGymToday){
        DBRecordsSource datasource = DBRecordsSource.getInstance();
        datasource.openDatabase();
        datasource.updateLatestDayRecordGymStatus(beenToGymToday);
        DBRecordsSource.getInstance().closeDatabase();
    }

}
