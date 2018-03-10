package com.appy;

import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity
{
    Button clearWidgets;
    Button clearTimers;
    Button clearState;
    Button restart;
    Handler handler;

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

        startService(new Intent(this, Widget.class));
    }
}
