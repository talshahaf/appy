package com.appy;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Collection;

/**
 * Created by Tal on 19/03/2018.
 */

public class LogcatFragment extends MyFragment implements RunnerListener
{
    TextView logcatView;
    Handler handler;
    ScrollView scroller;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View layout = inflater.inflate(R.layout.logcat_fragment, container, false);

        handler = new Handler();
        logcatView = layout.findViewById(R.id.logcat_view);
        scroller = layout.findViewById(R.id.scroller);

        onShow();
        return layout;
    }

    final ArrayDeque<String> logcatLines = new ArrayDeque<>();
    public static final int INITIAL_LOGCAT_LINES = 200;
    public static final int LOGCAT_LINES = 1000;

    Runner logcat = null;

    @Override
    public void onLine(String line)
    {
        synchronized (logcatLines)
        {
            logcatLines.add(line);
            while (logcatLines.size() > LOGCAT_LINES)
            {
                logcatLines.pop();
            }

            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    String lines;
                    synchronized (logcatLines)
                    {
                        lines = join("\n", logcatLines);
                    }
                    logcatView.setText(lines);
                    //TODO scroll cleverly
                }
            });
        }
    }

    @Override
    public void onExited(Integer code)
    {

    }

    public String join(String sep, Collection<String> arr)
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for(String item : arr){
            if(!first || (first = false)) sb.append(sep);
            sb.append(item);
        }
        return sb.toString();
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
        logcat = new Runner("logcat -b main -v time -T "+INITIAL_LOGCAT_LINES, null, this);
        logcat.start();
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
