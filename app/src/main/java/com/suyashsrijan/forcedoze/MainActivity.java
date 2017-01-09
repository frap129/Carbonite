package com.suyashsrijan.forcedoze;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.nanotasks.BackgroundWork;
import com.nanotasks.Completion;
import com.nanotasks.Tasks;

import java.util.List;

import eu.chainfire.libsuperuser.Shell;
import io.github.eliseomartelli.simplecustomtabs.CustomTabs;

public class MainActivity extends Activity implements CompoundButton.OnCheckedChangeListener {
    public static String TAG = "ForceDoze";
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
    MaterialDialog progressDialog = null;
    TextView textViewStatus;
    CustomTabs.Warmer warmer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        warmer = CustomTabs.with(getApplicationContext()).warm();
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
            Log.i(TAG, "android.permission.DUMP already granted, skipping SU check");
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


        } else {
            progressDialog = new MaterialDialog.Builder(this)
                    .title(R.string.please_wait_text)
                    .autoDismiss(false)
                    .cancelable(false)
                    .content(R.string.requesting_su_access_text)
                    .progress(true, 0)
                    .show();
            Log.i(TAG, "Check if SU is available, and request SU permission if it is");
            Tasks.executeInBackground(MainActivity.this, new BackgroundWork<Boolean>() {
                @Override
                public Boolean doInBackground() throws Exception {
                    return Shell.SU.available();
                }
            }, new Completion<Boolean>() {
                @Override
                public void onSuccess(Context context, Boolean result) {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                    }
                    isSuAvailable = result;
                    Log.i(TAG, "SU available: " + Boolean.toString(result));
                    if (isSuAvailable) {
                        Log.i(TAG, "Phone is rooted and SU permission granted");
                        editor = settings.edit();
                        editor.putBoolean("isSuAvailable", true);
                        editor.apply();
                        if (!Utils.isDumpPermissionGranted(getApplicationContext())) {
                            if (isSuAvailable) {
                                Log.i(TAG, "Granting android.permission.DUMP to com.suyashsrijan.forcedoze");
                                executeCommand("pm grant com.suyashsrijan.forcedoze android.permission.DUMP");
                                Log.i(TAG, "Granting android.permission.READ_PHONE_STATE to com.suyashsrijan.forcedoze");
                                executeCommand("pm grant com.suyashsrijan.forcedoze android.permission.READ_PHONE_STATE");
                            }
                        }
                        if (!Utils.isDevicePowerPermissionGranted(getApplicationContext())) {
                            if (Utils.isDeviceRunningOnNPreview()) {
                                executeCommand("pm grant com.suyashsrijan.forcedoze android.permission.DEVICE_POWER");
                            }
                        }
                        if (serviceEnabled) {
                            toggleForceDozeSwitch.setChecked(true);
                            if (!Utils.isMyServiceRunning(ForceDozeService.class, MainActivity.this)) {
                                Log.i(TAG, "Starting ForceDozeService");
                                startService(new Intent(context, ForceDozeService.class));
                            } else {
                                Log.i(TAG, "Service already running");
                            }
                        } else {
                            Log.i(TAG, "Service not enabled");
                        }
                    } else {
                        Log.i(TAG, "SU permission denied or not available");
                        toggleForceDozeSwitch.setChecked(false);
                        toggleForceDozeSwitch.setEnabled(false);
                        textViewStatus.setText(R.string.service_disabled);
                        editor = settings.edit();
                        editor.putBoolean("isSuAvailable", false);
                        editor.apply();
                        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppCompatAlertDialogStyle);
                        builder.setTitle(getString(R.string.error_text));
                        builder.setMessage(getString(R.string.root_workaround_text));
                        builder.setPositiveButton(getString(R.string.close_button_text), null);
                        builder.setNegativeButton(getString(R.string.root_workaround_button_text), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                                showRootWorkaroundInstructions();
                            }
                        });
                        builder.show();
                    }
                }

                @Override
                public void onError(Context context, Exception e) {
                    Log.e(TAG, "Error querying SU: " + e.getMessage());
                }
            });
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        warmer.unwarm();
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
            showForceDozeActiveDialog();
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

    public void openDonatePage() {
        CustomTabs.with(getApplicationContext())
                .setStyle(new CustomTabs.Style(getApplicationContext())
                        .setShowTitle(true)
                        .setExitAnimation(android.R.anim.slide_in_left, android.R.anim.slide_out_right))
                .openUrl("https://www.paypal.me/suyashsrijan", this);
    }

    public void showEnableDozeOnUnsupportedDeviceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle(getString(R.string.doze_unsupported_more_info_title));
        builder.setMessage(getString(R.string.doze_unsupported_more_info));
        builder.setPositiveButton(getString(R.string.close_button_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.setNegativeButton(getString(R.string.enable_doze_button_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                executeCommand("setprop persist.sys.doze_powersave true");
                if (Utils.isDeviceRunningOnNPreview()) {
                    executeCommand("dumpsys deviceidle disable all");
                    executeCommand("dumpsys deviceidle enable all");
                } else {
                    executeCommand("dumpsys deviceidle disable");
                    executeCommand("dumpsys deviceidle enable");
                }
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }


    public void showRootWorkaroundInstructions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle(getString(R.string.no_root_workaround_dialog_title));
        builder.setMessage(getString(R.string.no_root_workaround_dialog_text));
        builder.setPositiveButton(getString(R.string.okay_button_text), null);
        builder.setNegativeButton(getString(R.string.share_command_button_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, "adb -d shell pm grant com.suyashsrijan.forcedoze android.permission.DUMP");
                sendIntent.setType("text/plain");
                startActivity(sendIntent);

            }
        });
        builder.show();
    }

    public void showLockScreenTimeoutInfoDialog() {
        float lockscreenTimeout = Utils.getLockscreenTimeoutValue(getContentResolver());
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle(getString(R.string.lockscreen_timeout_dialog_title));
        builder.setMessage(getString(R.string.lockscreen_timeout_dialog_text_p1) + lockscreenTimeout + getString(R.string.lockscreen_timeout_dialog_text_p2) +
                getString(R.string.lockscreen_timeout_dialog_text_p3) + lockscreenTimeout + getString(R.string.lockscreen_timeout_dialog_text_p4) +
                getString(R.string.lockscreen_timeout_dialog_text_p5) + lockscreenTimeout + getString(R.string.lockscreen_timeout_dialog_text_p6));
        builder.setPositiveButton(getString(R.string.okay_button_text), null);
        builder.setNegativeButton(getString(R.string.open_security_settings_button_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                Intent securitySettingsIntent = new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS);
                startActivity(securitySettingsIntent);

            }
        });
        builder.show();
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

    public void showForceDozeActiveDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle(getString(R.string.forcedoze_active_dialog_title));
        builder.setMessage(getString(R.string.forcedoze_active_dialog_text));
        builder.setPositiveButton(getString(R.string.okay_button_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }

    public void showDonateDevDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle(getString(R.string.donate_dialog_title));
        builder.setMessage(getString(R.string.donate_dialog_text));
        builder.setPositiveButton(getString(R.string.donate_dialog_button_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                editor = settings.edit();
                editor.putBoolean("showDonateDevDialog1", false);
                editor.apply();
                dialogInterface.dismiss();
                openDonatePage();
            }
        });
        builder.setNegativeButton(getString(R.string.close_button_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                editor = settings.edit();
                editor.putBoolean("showDonateDevDialog1", false);
                editor.apply();
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }

    public void executeCommand(final String command) {
        if (isSuAvailable) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    List<String> output = Shell.SU.run(command);
                    if (output != null) {
                        printShellOutput(output);
                    } else {
                        Log.i(TAG, "Error occurred while executing command (" + command + ")");
                    }
                }
            });
        } else {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    List<String> output = Shell.SH.run(command);
                    if (output != null) {
                        printShellOutput(output);
                    } else {
                        Log.i(TAG, "Error occurred while executing command (" + command + ")");
                    }
                }
            });
        }
    }

    public void printShellOutput(List<String> output) {
        if (!output.isEmpty()) {
            for (String s : output) {
                Log.i(TAG, s);
            }
        }
    }

    public void launchSettingsActivity(View v) {
        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
    }
}
