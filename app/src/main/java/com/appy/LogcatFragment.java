package com.appy;

import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by Tal on 19/03/2018.
 */

public class LogcatFragment extends MyFragment implements RunnerListener
{
    TextView logcatView;
    Handler handler;
    ScrollView scroller;
    boolean atEnd = true;
    ArrayList<String> selectionBuffer = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View layout = inflater.inflate(R.layout.fragment_logcat, container, false);

        handler = new Handler();
        logcatView = layout.findViewById(R.id.logcat_view);
        scroller = layout.findViewById(R.id.scroller);
        scroller.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                atEnd = scroller.getScrollY() == (logcatView.getBottom() + scroller.getPaddingBottom() - scroller.getHeight());
                return false;
            }
        });

        onShow();
        return layout;
    }

    public static final int INITIAL_LOGCAT_LINES = 200;

    Runner logcat = null;

    @Override
    public void onLine(final String line)
    {
        if(handler != null)
        {
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    // freeze when selecting
                    if(logcatView.hasSelection() || !atEnd)
                    {
                        selectionBuffer.add(line);
                    }
                    else
                    {
                        for(String bufferedLine : selectionBuffer)
                        {
                            logcatView.append("\n" + bufferedLine);
                        }
                        selectionBuffer.clear();

                        logcatView.append("\n" + line);
                        handler.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                scroller.fullScroll(View.FOCUS_DOWN);
                            }
                        });
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
        if(logcat != null)
        {
            logcat.stop();
            logcat = null;
        }
    }

    public void startLogcat()
    {
        stopLogcat();
        logcat = new Runner(new String[]{"logcat", "-b", "main", "-v", "time", "-T", ""+INITIAL_LOGCAT_LINES}, null, null, this);
        logcat.start();
        if(handler != null)
        {
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    logcatView.setText("Loading...");
                }
            });
        }
    }

    @Override
    public void onShow()
    {
        startLogcat();
    }

    @Override
    public void onHide()
    {
        stopLogcat();
    }
}
