package com.appy;

import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;

public class MainActivity extends AppCompatActivity implements LogcatLines
{
    Button clearWidgets;
    Button clearTimers;
    Button clearState;
    Button restart;
    TextView logcatView;
    Handler handler;

    final ArrayDeque<String> logcatLines = new ArrayDeque<>();
    public static final int LOGCAT_LINES = 50;

    Logcat logcat = null;

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
            }
        });
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

    public static class Logcat implements Runnable
    {
        private LogcatLines callback;
        Thread thread;
        boolean shouldStop = false;

        public Logcat(LogcatLines cb)
        {
            callback = cb;
            thread = new Thread(this);
        }

        public void start()
        {
            thread.start();
        }

        public void stop()
        {
            shouldStop = true;
        }

        @Override
        public void run()
        {
            try
            {
                Process process = Runtime.getRuntime().exec("logcat -b main -v time -T "+LOGCAT_LINES);
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));

                String line;
                while (!shouldStop && ((line = bufferedReader.readLine()) != null)) {
                    if(callback != null)
                    {
                        callback.onLine(line);
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public void clickHandler(final View v, String action, String flag)
    {
        Intent intent = new Intent(this, Widget.class);
        intent.setAction(action);
        if(flag != null)
        {
            intent.putExtra(flag, true);
        }
        startService(intent);
        v.setEnabled(false);
        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                v.setEnabled(true);
            }
        }, 2000);
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
        logcat = new Logcat(this);
        logcat.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler();

        clearWidgets = findViewById(R.id.clear_widgets);
        clearTimers = findViewById(R.id.clear_timers);
        clearState = findViewById(R.id.clear_state);
        restart = findViewById(R.id.restart);
        logcatView = findViewById(R.id.logcat_view);

        clearWidgets.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                clickHandler(v, Widget.ACTION_CLEAR, "widgets");
            }
        });

        clearTimers.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                clickHandler(v, Widget.ACTION_CLEAR, "timers");
            }
        });

        clearState.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                clickHandler(v, Widget.ACTION_CLEAR, "state");
            }
        });

        restart.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                clickHandler(v, Widget.ACTION_RESTART, null);
            }
        });


        startLogcat();

        startService(new Intent(this, Widget.class));

    }

    @Override
    public void onPause()
    {
        super.onPause();
        stopLogcat();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        startLogcat();
    }
}
