package com.appy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
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
    Preference debugBoundingBoxesPreference;
    Preference disableGilPreference;
    final Handler handler = new Handler();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        addPreferencesFromResource(R.xml.pref);

        widthCorrectionPreference = getPreferenceScreen().findPreference("global_width_correction_factor");
        heightCorrectionPreference = getPreferenceScreen().findPreference("global_height_correction_factor");
        globalSizeFactorPreference = getPreferenceScreen().findPreference("global_size_factor");
        debugBoundingBoxesPreference = getPreferenceScreen().findPreference("global_debug_bounding_boxes");
        disableGilPreference = getPreferenceScreen().findPreference("disable_gil");

        Preference.OnPreferenceChangeListener validateFloat = (preference, newValue) -> Utils.parseFloatOrNull((String)newValue) != null;

        widthCorrectionPreference.setOnPreferenceChangeListener(validateFloat);
        heightCorrectionPreference.setOnPreferenceChangeListener(validateFloat);
        globalSizeFactorPreference.setOnPreferenceChangeListener(validateFloat);
        debugBoundingBoxesPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            Widget service = getWidgetService();
            if (service != null)
            {
                service.updateAll(true, true);
            }
            return true;
        });
        disableGilPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            handler.post(() -> Utils.showConfirmationDialog(getContext(), "Restart required", "Restart is required for GIL setting to take place. Restart now?", android.R.drawable.ic_dialog_alert, "Restart", "No", () -> {
                Widget service = getWidgetService();
                if (service != null)
                {
                    service.restart(0);
                }
            }));
            return true;
        });

        sizeFactorsPreference = getPreferenceScreen().findPreference("drawing_settings");
        sizeFactorsPreference.setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(getActivity(), WidgetDrawingSettingsActivity.class));
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

        getWidgetService().loadGlobalDrawingSettings(false);
        getWidgetService().loadForeground();

        Widget.DrawingSettings drawingSettings = getWidgetService().getGlobalDrawingSettings();
        globalSizeFactorPreference.setSummary(Utils.formatFloat(drawingSettings.sizeFactor));
        widthCorrectionPreference.setSummary(Utils.formatFloat(drawingSettings.widthCorrectionFactor));
        heightCorrectionPreference.setSummary(Utils.formatFloat(drawingSettings.heightCorrectionFactor));
        pythonVersionPreference.setSummary(getWidgetService().pythonVersion());
    }
}
