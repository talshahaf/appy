package com.appy;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Tal on 19/03/2018.
 */

public class LogcatFragment extends MyFragment implements RunnerListener
{
    TextView logcatView;
    Handler handler;
    ScrollView scroller;
    Button clearButton;
    CheckBox filterButton;
    String filterTag = "";
    boolean atEnd = true;
    ArrayList<String> selectionBuffer = new ArrayList<>();
    ArrayList<String> allLines = new ArrayList<>();
    static final String EMPTY_TEXT = "Waiting for logcat...";

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View layout = inflater.inflate(R.layout.fragment_logcat, container, false);

        handler = new Handler();
        logcatView = layout.findViewById(R.id.logcat_view);
        scroller = layout.findViewById(R.id.scroller);
        scroller.setOnTouchListener((v, event) -> {
            atEnd = scroller.getScrollY() == (logcatView.getBottom() + scroller.getPaddingBottom() - scroller.getHeight());
            return false;
        });

        clearButton = layout.findViewById(R.id.logcat_clear);
        clearButton.setOnClickListener(v -> {
            try
            {
                Runtime.getRuntime().exec("logcat -c");
            }
            catch (IOException ignored)
            {

            }
            selectionBuffer.clear();
            allLines.clear();
            refillLines();
        });

        filterButton = layout.findViewById(R.id.logcat_filter);
        filterButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked)
            {
                filterTag = "APPY";
            }
            else
            {
                filterTag = "";
            }
            refillLines();
        });

        if (filterButton.isChecked())
        {
            filterTag = "APPY";
        }
        else
        {
            filterTag = "";
        }

        return layout;
    }

    public static final int INITIAL_LOGCAT_LINES = 200;

    Runner logcat = null;

    Pattern tagExtract = Pattern.compile("^[^VIDWEF]*[VIDWEF].([^\\s]+)\\s.*$");
    public boolean lineHidden(String line)
    {
        if (filterTag.isEmpty())
        {
            return false;
        }
        Matcher match = tagExtract.matcher(line);
        if (!match.matches())
        {
            //don't hide bad lines
            return false;
        }
        return !filterTag.equalsIgnoreCase(match.group(1));
    }

    public void refillLines()
    {
        ArrayList<String> shown = new ArrayList<>();
        for (String line : allLines)
        {
            if (!lineHidden(line))
            {
                shown.add(line);
            }
        }

        if (shown.isEmpty())
        {
            logcatView.setText(EMPTY_TEXT);
        }
        else
        {
            logcatView.setText(String.join("\n", shown));
        }
    }

    @Override
    public void onLine(final String line)
    {
        if (handler != null)
        {
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    // freeze when selecting
                    if (logcatView.hasSelection() || !atEnd)
                    {
                        selectionBuffer.add(line);
                    }
                    else
                    {
                        for (String bufferedLine : selectionBuffer)
                        {
                            allLines.add(bufferedLine);
                            if (!lineHidden(bufferedLine))
                            {
                                logcatView.append("\n" + bufferedLine);
                            }
                        }
                        selectionBuffer.clear();

                        allLines.add(line);
                        if (!lineHidden(line))
                        {
                            logcatView.append("\n" + line);
                        }
                        handler.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
                    }
                }
            });
        }
    }

    @Override
    public void onExited(Integer code)
    {

    }

    public void stopLogcat()
    {
        if (logcat != null)
        {
            logcat.stop();
            logcat = null;
        }
    }

    public void startLogcat()
    {
        stopLogcat();
        logcat = new Runner(new String[]{"logcat", "-b", "main", "-v", "time", "-T", "" + INITIAL_LOGCAT_LINES}, null, null, this);
        logcat.start();
        if (handler != null)
        {
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    logcatView.setText(EMPTY_TEXT);
                }
            });
        }
    }

    @Override
    public void onResume()
    {
        startLogcat();
        super.onResume();
    }

    @Override
    public void onPause()
    {
        stopLogcat();
        super.onPause();
    }
}
