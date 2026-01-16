package com.appy;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;

/**
 * Created by Tal on 19/03/2018.
 */

public class PipFragment extends MyFragment implements RunnerListener
{
    EditText command;
    Switch useShell;
    Button run;
    Button stop;
    TextView output;
    ScrollView scroller;
    String fulloutput = "";

    Runner runner = null;

    Handler handler;

    File cwd = null;
    File lib = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View layout = inflater.inflate(R.layout.fragment_pip, container, false);

        command = layout.findViewById(R.id.command);
        useShell = layout.findViewById(R.id.useshell);
        run = layout.findViewById(R.id.run);
        stop = layout.findViewById(R.id.stop);
        output = layout.findViewById(R.id.output);
        scroller = layout.findViewById(R.id.scroller);

        command.setText("");
        command.append("python -m pip install ");

        handler = new Handler();

        cwd = new File(System.getenv("PYTHONHOME"), "bin");
        lib = new File(System.getenv("PYTHONHOME"), "lib");

        run.setOnClickListener(v -> {
            if (runner != null)
            {
                runner.stop();
            }
            fulloutput = "Running...\n\n";
            output.setText(fulloutput);

            String cmd = command.getText().toString();
            String[] args = useShell.isChecked() ? new String[] {"sh", "-c", cmd} : Runner.translateCommandline(cmd);
            runner = new Runner(args, cwd, null, PipFragment.this);
            runner.start();

            v.setEnabled(false);
            handler.postDelayed(() -> v.setEnabled(true), 1000);
        });

        stop.setOnClickListener(v -> {
            if (runner != null)
            {
                if (runner.isRunning())
                {
                    fulloutput += "\n\nStopping...";
                    output.setText(fulloutput);
                    v.setEnabled(false);
                    handler.postDelayed(() -> v.setEnabled(true), 1000);
                }
                runner.stop();
            }
        });

        return layout;
    }

    @Override
    public void onResume()
    {
        output.setText(fulloutput);
        super.onResume();
    }

    @Override
    public void onLine(final String line)
    {
        handler.post(() -> {
            fulloutput += "\n" + line;
            output.setText(fulloutput);
            boolean commandFocused = command.hasFocus();
            scroller.fullScroll(View.FOCUS_DOWN);
            if (commandFocused)
            {
                command.requestFocus();
            }
        });
    }

    @Override
    public void onExited(final Integer code)
    {
        handler.post(() -> {
            fulloutput += code == null ? "\nTerminated" : ("\nExited: " + code);
            output.setText(fulloutput);
        });
    }
}
