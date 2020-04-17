package com.appy;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

/**
 * Created by Tal on 19/03/2018.
 */

public class SettingsFragment extends MySettingsFragment implements SharedPreferences.OnSharedPreferenceChangeListener
{
    CheckBoxPreference foregroundPreference;
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        addPreferencesFromResource(R.xml.pref);

        foregroundPreference = (CheckBoxPreference)getPreferenceScreen().findPreference("foreground_service");

        foregroundPreference.setChecked(Widget.getForeground(getActivity()));

        foregroundPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                if(newValue instanceof Boolean && !((boolean)newValue) && Widget.needForeground())
                {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Foreground Service")
                            .setMessage("Turning off foreground service causes problems in android 8+. Are you sure?")
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    foregroundPreference.setChecked(false);
                                }})
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    return false;
                }
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        updateConfig();
    }

    public void onBound()
    {
        updateConfig();
    }

    public void updateConfig()
    {
        if(getWidgetService() == null)
        {
            return;
        }

        getWidgetService().loadCorrectionFactors(false);
        getWidgetService().loadForeground();
    }
}
