package org.carbonrom.carbonite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.MenuItem;

import com.carbonrom.carbonite.R;
import org.carbonrom.nanotasks.BackgroundWork;
import org.carbonrom.nanotasks.Completion;
import org.carbonrom.nanotasks.Tasks;

import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class SettingsActivity extends Activity {
    public static String TAG = "ForceDoze";
    static AlertDialog progressDialog1 = null;
    static boolean isSuAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Utils.isMyServiceRunning(ForceDozeService.class, SettingsActivity.this)) {
            Intent intent = new Intent("reload-settings");
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.prefs);
            PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("preferenceScreen");
            PreferenceCategory mainSettings = (PreferenceCategory) findPreference("mainSettings");
            PreferenceCategory dozeSettings = (PreferenceCategory) findPreference("dozeSettings");
            Preference dozeDelay = findPreference("dozeEnterDelay");
            Preference nonRootSensorWorkaround = findPreference("useNonRootSensorWorkaround");
            Preference enableSensors = findPreference("enableSensors");
            Preference turnOffDataInDoze = findPreference("turnOffDataInDoze");
            Preference autoRotateBrightnessFix = findPreference("autoRotateAndBrightnessFix");
            SwitchPreference autoRotateFixPref = (SwitchPreference) findPreference("autoRotateAndBrightnessFix");

            dozeDelay.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    int delay = (int) o;
                    if (delay >= 5) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
                        builder.setTitle(getString(R.string.doze_delay_warning_dialog_title));
                        builder.setMessage(getString(R.string.doze_delay_warning_dialog_text));
                        builder.setPositiveButton(getString(R.string.okay_button_text), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        });
                        builder.show();
                    }
                    return true;
                }
            });

            autoRotateFixPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (!Settings.System.canWrite(getActivity())) {
                        requestWriteSettingsPermission();
                        return false;
                    } else return true;
                }
            });

            nonRootSensorWorkaround.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    boolean newValue = (boolean) o;
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    if (newValue) {
                        editor.putBoolean("enableSensors", true);
                        editor.apply();
                    }
                    return true;
                }
            });

            turnOffDataInDoze.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    final boolean newValue = (boolean) o;
                    if (!newValue) {
                        return true;
                    } else {
                        progressDialog1 = new AlertDialog.Builder(getActivity())
                                .setTitle(getString(R.string.please_wait_text))
                                .setCancelable(false)
                                .setMessage(getString(R.string.requesting_su_access_text))
                                .show();
                        Log.i(TAG, "Check if SU is available, and request SU permission if it is");
                        Tasks.executeInBackground(getActivity(), new BackgroundWork<Boolean>() {
                            @Override
                            public Boolean doInBackground() throws Exception {
                                return Shell.SU.available();
                            }
                        }, new Completion<Boolean>() {
                            @Override
                            public void onSuccess(Context context, Boolean result) {
                                if (progressDialog1 != null) {
                                    progressDialog1.dismiss();
                                }
                                isSuAvailable = result;
                                Log.i(TAG, "SU available: " + Boolean.toString(result));
                                if (isSuAvailable) {
                                    Log.i(TAG, "Phone is rooted and SU permission granted");
                                    Log.i(TAG, "Granting android.permission.READ_PHONE_STATE to org.carbonrom.carbonite");
                                    executeCommand("pm grant org.carbonrom.carbonite android.permission.READ_PHONE_STATE");
                                } else {
                                    Log.i(TAG, "SU permission denied or not available");
                                    AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppCompatAlertDialogStyle);
                                    builder.setTitle(getString(R.string.error_text));
                                    builder.setMessage(getString(R.string.su_perm_denied_msg));
                                    builder.setPositiveButton(getString(R.string.close_button_text), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            dialogInterface.dismiss();
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

                        return true;
                    }
                }
            });

            Preference whitelistAppsFromDozeMode = findPreference("whitelistAppsFromDozeMode");
            whitelistAppsFromDozeMode.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                public boolean onPreferenceClick(Preference preference) {
                    Intent viewIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(viewIntent);

                    return true;
                }
            });

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            isSuAvailable = sharedPreferences.getBoolean("isSuAvailable", false);

            if (sharedPreferences.getBoolean("useNonRootSensorWorkaround", false)) {
                mainSettings.removePreference(autoRotateBrightnessFix);
                mainSettings.removePreference(enableSensors);
            }

            if (!isSuAvailable) {
                dozeSettings.removePreference(turnOffDataInDoze);
            }

        }

        public void requestWriteSettingsPermission() {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle);
            builder.setTitle(getString(R.string.auto_rotate_brightness_fix_dialog_title));
            builder.setMessage(getString(R.string.auto_rotate_brightness_fix_dialog_text));
            builder.setPositiveButton(getString(R.string.authorize_button_text), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
                    startActivity(intent);
                }
            });
            builder.setNegativeButton(getString(R.string.deny_button_text), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
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
    }
}
