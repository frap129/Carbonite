package org.carbonrom.carbonite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.carbonrom.carbonite.R;

public class MainActivity extends Activity implements CompoundButton.OnCheckedChangeListener {
    public static String TAG = "Carbonite";
    SharedPreferences settings;
    SharedPreferences.Editor editor;
    Boolean isDozeEnabledByOEM = true;
    Boolean isSuAvailable = false;
    Boolean isDozeDisabled = false;
    Boolean serviceEnabled = false;
    Boolean isDumpPermGranted = false;
    Boolean ignoreLockscreenTimeout = true;
    Boolean showDonateDevDialog = true;
    Switch toggleForceDozeSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        isDozeEnabledByOEM = Utils.checkForAutoPowerModesFlag();
        showDonateDevDialog = settings.getBoolean("showDonateDevDialog1", true);
        serviceEnabled = settings.getBoolean("serviceEnabled", false);
        isDozeDisabled = settings.getBoolean("isDozeDisabled", false);
        isSuAvailable = settings.getBoolean("isSuAvailable", false);
        ignoreLockscreenTimeout = settings.getBoolean("ignoreLockscreenTimeout", true);
        toggleForceDozeSwitch = (Switch) findViewById(R.id.switch1);
        isDumpPermGranted = Utils.isDumpPermissionGranted(getApplicationContext());

        toggleForceDozeSwitch.setOnCheckedChangeListener(null);

        toggleForceDozeSwitch.setOnCheckedChangeListener(this);

        if (isDumpPermGranted) {
            Log.i(TAG, "android.permission.DUMP already granted");
            if (serviceEnabled) {
                toggleForceDozeSwitch.setChecked(true);
                if (!Utils.isMyServiceRunning(ForceDozeService.class, MainActivity.this)) {
                    Log.i(TAG, "Starting ForceDozeService");
                    startService(new Intent(this, ForceDozeService.class));
                } else {
                    Log.i(TAG, "Service already running");
                }
            } else {
                Log.i(TAG, "Service not enabled");
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        isDumpPermGranted = Utils.isDumpPermissionGranted(getApplicationContext());

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_doze_more_info:
                showMoreInfoDialog();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (b) {
            editor = settings.edit();
            editor.putBoolean("serviceEnabled", true);
            editor.apply();
            if (!Utils.isMyServiceRunning(ForceDozeService.class, MainActivity.this)) {
                Log.i(TAG, "Enabling ForceDoze");
                startService(new Intent(MainActivity.this, ForceDozeService.class));
            }
        } else {
            editor = settings.edit();
            editor.putBoolean("serviceEnabled", false);
            editor.apply();
            if (Utils.isMyServiceRunning(ForceDozeService.class, MainActivity.this)) {
                Log.i(TAG, "Disabling ForceDoze");
                stopService(new Intent(MainActivity.this, ForceDozeService.class));
            }
        }
    }

    public void showMoreInfoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle(getString(R.string.more_info_text));
        builder.setMessage(getString(R.string.how_doze_works_dialog_text));
        builder.setPositiveButton(getString(R.string.okay_button_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }

    public void launchSettingsActivity(View v) {
        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
    }
}
