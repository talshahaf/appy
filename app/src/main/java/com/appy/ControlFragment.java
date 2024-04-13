package com.appy;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AlertDialog;
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
    Button clearWidgets;
    Button clearTimers;
    Button clearState;
    Button resetExamples;
    Button restart;

    Handler handler;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View layout = inflater.inflate(R.layout.fragment_control, container, false);

        startupProgress = layout.findViewById(R.id.startup_progress);
        startupStatus = layout.findViewById(R.id.startup_status);
        clearWidgets = layout.findViewById(R.id.clear_widgets);
        clearTimers = layout.findViewById(R.id.clear_timers);
        clearState = layout.findViewById(R.id.clear_state);
        resetExamples = layout.findViewById(R.id.reset_examples);
        restart = layout.findViewById(R.id.restart);

        handler = new Handler();

        clearWidgets.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Clear widgets")
                        .setMessage("Clear all widgets?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int whichButton)
                            {
                                getWidgetService().resetWidgets();
                                debounce(v);
                            }
                        })
                        .setNegativeButton(android.R.string.no, null);
                builder.show();
            }
        });

        clearTimers.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Clear timers")
                        .setMessage("Clear all timers?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int whichButton)
                                    {
                                        getWidgetService().cancelAllTimers();
                                        debounce(v);
                                    }
                                })
                        .setNegativeButton(android.R.string.no, null);
                builder.show();
            }
        });

        clearState.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Clear state")
                        .setMessage("Clear state?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int whichButton)
                            {
                                getWidgetService().resetState();
                                debounce(v);
                            }
                        })
                        .setNegativeButton(android.R.string.no, null);
                builder.show();
            }
        });

        resetExamples.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Reset examples")
                        .setMessage("Reset examples?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int whichButton)
                            {
                                getWidgetService().unpackExamples(true);
                                debounce(v);
                            }
                        })
                        .setNegativeButton(android.R.string.no, null);
                builder.show();
            }
        });

        restart.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                getWidgetService().restart();
                debounce(v);
            }
        });

        onStartupStatusChange();

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

    public void onBound()
    {
        onStartupStatusChange();
    }

    public void onStartupStatusChange()
    {
        if(getWidgetService() == null)
        {
            return;
        }
        switch(getWidgetService().getStartupState())
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
                break;
            case COMPLETED:
                startupStatus.setImageResource(R.drawable.success_indicator);
                startupProgress.setVisibility(View.INVISIBLE);
                startupStatus.setVisibility(View.VISIBLE);
                break;
        }
    }
}
