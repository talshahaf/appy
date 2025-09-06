package com.appy;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

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

        handler = new Handler();

        manageWidgets.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                startActivity(new Intent(getActivity(), WidgetManagerActivity.class));
            }
        });

        resetExamples.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                Utils.showConfirmationDialog(getActivity(),
                        "Reset examples", "Reset examples?", android.R.drawable.ic_dialog_alert,
                        null, null, new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                getWidgetService().unpackExamples(true);
                                debounce(v);
                            }
                        });
            }
        });

        dumpStacktrace.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                getWidgetService().dumpStacktrace();
                debounce(v);
            }
        });

        restart.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                getWidgetService().restart(false);
                debounce(v);
            }
        });

        reinstall.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                Utils.showConfirmationDialog(getActivity(),
                        "Reinstall package", "This would also restart the app", android.R.drawable.ic_dialog_alert,
                        null, null, new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                getWidgetService().restart(true);
                                debounce(v);
                            }
                        }
                );
            }
        });

        return layout;
    }

    public void debounce(final View v)
    {
        v.setEnabled(false);
        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                v.setEnabled(true);
            }
        }, 1000);
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
