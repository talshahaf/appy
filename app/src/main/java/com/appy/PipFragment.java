package com.appy;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * Created by Tal on 19/03/2018.
 */

public class PipFragment extends MyFragment implements RunnerListener
{
    EditText command;
    Button run;
    Button stop;
    TextView output;
    ScrollView scroller;

    Runner runner = null;

    Handler handler;

    File cwd = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View layout = inflater.inflate(R.layout.fragment_pip, container, false);

        command = layout.findViewById(R.id.command);
        run = layout.findViewById(R.id.run);
        stop = layout.findViewById(R.id.stop);
        output = layout.findViewById(R.id.output);
        scroller = layout.findViewById(R.id.scroller);

        command.setText("");
        command.append("pip install ");

        handler = new Handler();

        cwd = new File(System.getenv("PYTHONHOME"), "bin");

        run.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                if(runner != null)
                {
                    runner.stop();
                }
                output.setText("Running...\n\n");
                runner = new Runner(command.getText().toString(), cwd, PipFragment.this);
                runner.start();

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
        });

        stop.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                if(runner != null)
                {
                    if(runner.isRunning())
                    {
                        output.setText(output.getText() + "\n\nStopping...");
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
                    runner.stop();
                }
            }
        });

        return layout;
    }

    @Override
    public void onLine(final String line)
    {
        handler.post(new Runnable()
        {
            @Override
            public void run()
            {
                output.setText(output.getText() + "\n" + line);
                scroller.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    @Override
    public void onExited(final Integer code)
    {
        handler.post(new Runnable()
        {
            @Override
            public void run()
            {
                if(code == null)
                {
                    output.setText(output.getText() + "\nTerminated");
                }
                else
                {
                    output.setText(output.getText() + "\nExited: "+code);
                }
            }
        });
    }
}
