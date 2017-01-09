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
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.view.MenuItem;

import com.carbonrom.carbonite.R;

public class SettingsActivity extends Activity {

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
            Preference dozeDelay = findPreference("dozeEnterDelay");
            Preference nonRootSensorWorkaround = findPreference("useNonRootSensorWorkaround");
            SwitchPreference autoRotateFixPref = (SwitchPreference) findPreference("autoRotateAndBrightnessFix");

            dozeDelay.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    int delay = (int) o;
                    if (delay >= 2) {
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
    }
}
