package com.appy;

import android.os.Build;
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
    Switch runpip;
    Button run;
    Button stop;
    TextView output;
    ScrollView scroller;
    String fulloutput = "";

    Runner runner = null;

    Handler handler;

    File cwd = null;
    File lib = null;

    public static final String PIP_COMMAND = "python -m pip install ";
    public static final String EVAL_COMMAND = "python -c \"";

    public void applyCommand(boolean pip)
    {
        int posStart = command.getSelectionStart();
        int posEnd = command.getSelectionEnd();

        String text = command.getText().toString();
        boolean empty = text.isEmpty();
        int off = 0;
        if (!pip && (text.startsWith(PIP_COMMAND) || empty))
        {
            text = EVAL_COMMAND + (empty ? "" : text.substring(PIP_COMMAND.length())) + "\"";
            off = EVAL_COMMAND.length() - (empty ? 0 : PIP_COMMAND.length());
        }
        if (pip && (text.startsWith(EVAL_COMMAND) || empty))
        {
            if (text.endsWith("\""))
            {
                text = text.substring(0, text.length() - 1);
            }
            text = PIP_COMMAND + (empty ? "" : text.substring(EVAL_COMMAND.length()));
            off = PIP_COMMAND.length() - (empty ? 0 : EVAL_COMMAND.length());
        }

        posStart = posStart == -1 ? -1 : (posStart + off);
        posEnd = posEnd == -1 ? -1 : (posEnd + off);
        if (posStart < 0 || posStart > text.length() || posEnd < 0 || posEnd > text.length())
        {
            posStart = -1;
            posEnd = -1;
        }
        command.setText(text);
        if (posStart == -1 && posEnd == -1)
        {
            command.setSelected(false);
        }
        else
        {
            command.setSelection(posStart, posEnd);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View layout = inflater.inflate(R.layout.fragment_pip, container, false);

        command = layout.findViewById(R.id.command);
        useShell = layout.findViewById(R.id.useshell);
        runpip = layout.findViewById(R.id.runpip);
        run = layout.findViewById(R.id.run);
        stop = layout.findViewById(R.id.stop);
        output = layout.findViewById(R.id.output);
        scroller = layout.findViewById(R.id.scroller);

        applyCommand(runpip.isChecked());

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

        runpip.setOnCheckedChangeListener((buttonView, isChecked) -> applyCommand(isChecked));

        return layout;
    }

    @Override
    public void onResume()
    {
        output.setText(fulloutput);
        super.onResume();
    }

    Runnable updateTask = new Runnable()
    {
        @Override
        public void run()
        {
            output.setText(fulloutput);
            boolean commandFocused = command.hasFocus();
            scroller.fullScroll(View.FOCUS_DOWN);
            if (commandFocused)
            {
                command.requestFocus();
            }
        }
    };

    @Override
    public void onLines(final String[] lines)
    {
        fulloutput += "\n" + String.join("\n", lines);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && handler.hasCallbacks(updateTask))
        {
            handler.removeCallbacks(updateTask);
            handler.postDelayed(updateTask, 500);
        }
        else
        {
            handler.removeCallbacks(updateTask);
            handler.post(updateTask);
        }
    }

    @Override
    public void onExited(final Integer code)
    {
        fulloutput += code == null ? "\nTerminated" : ("\nExited: " + code);
        handler.post(() -> output.setText(fulloutput));
    }
}
