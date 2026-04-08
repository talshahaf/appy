package com.appy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.Preference;

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

        widthCorrectionPreference = getPreferenceScreen().findPreference("global_width_correction_factor");
        heightCorrectionPreference = getPreferenceScreen().findPreference("global_height_correction_factor");
        globalSizeFactorPreference = getPreferenceScreen().findPreference("global_size_factor");

        Preference.OnPreferenceChangeListener validateFloat = (preference, newValue) -> {
            try
            {
                Float.parseFloat((String)newValue);
                return true;
            }
            catch (NumberFormatException ignored)
            {

            }
            return false;
        };

        widthCorrectionPreference.setOnPreferenceChangeListener(validateFloat);
        heightCorrectionPreference.setOnPreferenceChangeListener(validateFloat);
        globalSizeFactorPreference.setOnPreferenceChangeListener(validateFloat);

        sizeFactorsPreference = getPreferenceScreen().findPreference("size_factors");
        sizeFactorsPreference.setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(getActivity(), WidgetSizeFactorActivity.class));
            return true;
        });

        externalDirPreference = getPreferenceScreen().findPreference("external_dir");
        externalDirPreference.setSummary(getContext() != null ? Widget.getPreferredScriptDirStatic(getContext()) : "No available directory");

        externalDirPreference.setOnPreferenceClickListener(preference -> {
            Utils.copyToClipboard(getContext(), preference.getTitle().toString(), preference.getSummary().toString(), "Path copied to clipboard");
            return true;
        });

        pythonVersionPreference = getPreferenceScreen().findPreference("python_version");
    }

    @Override
    public void onResume()
    {
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updateConfig();
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

        getWidgetService().loadSizeAndCorrectionFactors(false);
        getWidgetService().loadForeground();

        float[] sizeAndCorrectionFactors = getWidgetService().getGlobalSizeAndCorrectionFactors();
        globalSizeFactorPreference.setSummary(Utils.formatFloat(sizeAndCorrectionFactors[0]));
        widthCorrectionPreference.setSummary(Utils.formatFloat(sizeAndCorrectionFactors[1]));
        heightCorrectionPreference.setSummary(Utils.formatFloat(sizeAndCorrectionFactors[2]));
        pythonVersionPreference.setSummary(getWidgetService().pythonVersion());
    }
}
