package org.carbonrom.carbonite;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;

public class DisableForceDozeService extends BroadcastReceiver {
    public static String TAG = "Carbonite";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "org.carbonrom.carbonite.DISABLE_FORCEDOZE broadcast intent received");
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("serviceEnabled", false).apply();
        if (Utils.isMyServiceRunning(ForceDozeService.class, context)) {
            context.stopService(new Intent(context, ForceDozeService.class));
        }
    }
}
