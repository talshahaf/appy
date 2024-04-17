package com.appy;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import java.io.File;

/**
 * Created by Tal on 19/03/2018.
 */

public class SettingsFragment extends MySettingsFragment implements SharedPreferences.OnSharedPreferenceChangeListener
{
    Preference externalDirPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        addPreferencesFromResource(R.xml.pref);

        externalDirPreference = getPreferenceScreen().findPreference("external_dir");
        Context context = getContext();
        File externalDir = context != null ? getContext().getExternalFilesDir(null) : null;
        externalDirPreference.setSummary(externalDir != null ? externalDir.getAbsolutePath() : "No available directory");

        externalDirPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference)
            {
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(preference.getTitle(), preference.getSummary());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Path copied to clipboard", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause()
    {
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
        if (getWidgetService() == null)
        {
            return;
        }

        getWidgetService().loadCorrectionFactors(false);
        getWidgetService().loadForeground();
    }
}
