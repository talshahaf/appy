package com.appy;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/**
 * Created by Tal on 19/03/2018.
 */

public class ControlFragment extends MyFragment
{
    ProgressBar startupProgress;
    ImageView startupStatus;
    Button manageWidgets;
    Button resetExamples;
    Button dumpStacktrace;
    Button restart;
    Button reinstall;
    Button clearCache;
    String clearCacheText;

    Handler handler;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View layout = inflater.inflate(R.layout.fragment_control, container, false);

        startupProgress = layout.findViewById(R.id.startup_progress);
        startupStatus = layout.findViewById(R.id.startup_status);
        manageWidgets = layout.findViewById(R.id.manage_widgets);
        resetExamples = layout.findViewById(R.id.reset_examples);
        dumpStacktrace = layout.findViewById(R.id.dump_stacktrace);
        restart = layout.findViewById(R.id.restart);
        reinstall = layout.findViewById(R.id.reinstall);
        clearCache = layout.findViewById(R.id.clear_cache);

        clearCacheText = clearCache.getText().toString();

        handler = new Handler();

        manageWidgets.setOnClickListener(v -> startActivity(new Intent(getActivity(), WidgetManagerActivity.class)));

        resetExamples.setOnClickListener(v -> Utils.showConfirmationDialog(getActivity(),
                "Reset examples", "Reset examples?", android.R.drawable.ic_dialog_alert,
                null, null, () -> {
                    getWidgetService().unpackExamples(true);
                    debounce(v);
                }));

        dumpStacktrace.setOnClickListener(v -> {
            getWidgetService().dumpStacktrace();
            debounce(v);
        });

        restart.setOnClickListener(v -> {
            getWidgetService().restart(false);
            debounce(v);
        });

        reinstall.setOnClickListener(v -> Utils.showConfirmationDialog(getActivity(),
                "Reinstall package", "This would also restart the app", android.R.drawable.ic_dialog_alert,
                null, null, () -> {
                    getWidgetService().restart(true);
                    debounce(v);
                }
        ));

        clearCache.setOnClickListener(v -> {
            Utils.RunnableArgs<String, Pair<Integer, Long>> action = (String cachedir, Pair<Integer, Long> dirSummary) -> {
                String message = dirSummary == null ? "Unable to summerize cache directory content" :
                        "This would remove " + dirSummary.first + " files (" + Utils.formatSize(dirSummary.second) + ")";

                Utils.showConfirmationDialog(getActivity(),
                        "Clear cache", message, android.R.drawable.ic_dialog_alert,
                        null, null, () -> {
                            File[] files = new File(cachedir).listFiles();
                            if (files != null)
                            {
                                for (File f : files)
                                {
                                    f.delete();
                                }
                            }
                            debounce(v);
                        }
                );
            };

            String cachedir = Utils.getResourcesCacheDir(getActivity());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            {
                action.run(cachedir, null);
                return;
            }

            new Thread(() -> {
                try
                {
                    handler.post(() -> clearCache.setText("Calculating size..."));
                    Pair<Integer, Long> dirSummary = Utils.dirSummary(cachedir);
                    handler.post(() -> {
                        clearCache.setText(clearCacheText);
                        action.run(cachedir, dirSummary);
                    });
                    return;
                }
                catch (IOException e)
                {
                    Log.e("APPY", "Failed to calculate cache dir size", e);
                }
                handler.post(() -> action.run(cachedir, null));
            }).start();

        });

        return layout;
    }

    public void debounce(final View v)
    {
        v.setEnabled(false);
        handler.postDelayed(() -> v.setEnabled(true), 1000);
    }

    @Override
    public void onResumedAndBound()
    {
        onStartupStatusChange();
    }

    @Override
    public void onStartedAndBound()
    {
        onStartupStatusChange();
    }

    public void onStartupStatusChange()
    {
        if (getWidgetService() == null)
        {
            return;
        }
        switch (getWidgetService().getStartupState())
        {
            case IDLE:
                startupStatus.setImageResource(R.drawable.idle_indicator);
                startupProgress.setVisibility(View.INVISIBLE);
                startupStatus.setVisibility(View.VISIBLE);
                break;
            case RUNNING:
                startupProgress.setVisibility(View.VISIBLE);
                startupStatus.setVisibility(View.INVISIBLE);
                break;
            case ERROR:
                startupStatus.setImageResource(R.drawable.error_indicator);
                startupProgress.setVisibility(View.INVISIBLE);
                startupStatus.setVisibility(View.VISIBLE);
                Toast.makeText(getWidgetService(), "Error occurred during initialization. Check the logcat tab for more details.", Toast.LENGTH_SHORT).show();
                break;
            case COMPLETED:
                startupStatus.setImageResource(R.drawable.success_indicator);
                startupProgress.setVisibility(View.INVISIBLE);
                startupStatus.setVisibility(View.VISIBLE);
                break;
        }
    }
}
