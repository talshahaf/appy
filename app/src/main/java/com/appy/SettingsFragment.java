package com.appy;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
    Preference pythonVersionPreference;
    Preference sizeFactorsPreference;

    Preference widthCorrectionPreference;
    Preference heightCorrectionPreference;
    Preference globalSizeFactorPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        addPreferencesFromResource(R.xml.pref);

        widthCorrectionPreference = getPreferenceScreen().findPreference("width_correction");
        heightCorrectionPreference = getPreferenceScreen().findPreference("height_correction");
        globalSizeFactorPreference = getPreferenceScreen().findPreference("global_size_factor");

        Preference.OnPreferenceChangeListener validateFloat = new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(@NonNull Preference preference, Object newValue)
            {
                try
                {
                    Float.parseFloat((String)newValue);
                    return true;
                }
                catch (NumberFormatException e)
                {

                }
                return false;
            }
        };

        widthCorrectionPreference.setOnPreferenceChangeListener(validateFloat);
        heightCorrectionPreference.setOnPreferenceChangeListener(validateFloat);
        globalSizeFactorPreference.setOnPreferenceChangeListener(validateFloat);

        sizeFactorsPreference = getPreferenceScreen().findPreference("size_factors");
        sizeFactorsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference)
            {
                startActivity(new Intent(getActivity(), WidgetSizeFactorActivity.class));
                return true;
            }
        });

        externalDirPreference = getPreferenceScreen().findPreference("external_dir");
        externalDirPreference.setSummary(getContext() != null ? Widget.getPreferredScriptDirStatic(getContext()) : "No available directory");

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

        pythonVersionPreference = getPreferenceScreen().findPreference("python_version");
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

        float[] correctionFactors = getWidgetService().getCorrectionFactors();
        widthCorrectionPreference.setSummary(Utils.formatFloat(correctionFactors[0]));
        heightCorrectionPreference.setSummary(Utils.formatFloat(correctionFactors[1]));
        globalSizeFactorPreference.setSummary(Utils.formatFloat(getWidgetService().getGlobalSizeFactor()));
        pythonVersionPreference.setSummary(getWidgetService().pythonVersion());
    }
}
