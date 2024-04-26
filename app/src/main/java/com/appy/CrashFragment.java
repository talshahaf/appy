package com.appy;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;

/**
 * Created by Tal on 19/03/2018.
 */

public class CrashFragment extends MyFragment
{
    TextView javaView = null;
    TextView pythonView = null;
    Button clearBtn = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View layout = inflater.inflate(R.layout.fragment_crash, container, false);
        javaView = layout.findViewById(R.id.java_crash);
        pythonView = layout.findViewById(R.id.python_crash);
        clearBtn = layout.findViewById(R.id.crash_clear);
        onShow();

        clearBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (getActivity() == null)
                {
                    return;
                }

                try
                {
                    new File(getActivity().getCacheDir(), "javacrash.txt").delete();
                }
                catch (SecurityException e)
                {
                }
                try
                {
                    new File(getActivity().getCacheDir(), "pythoncrash.txt").delete();
                }
                catch (SecurityException e)
                {
                }

                onShow();
            }
        });
        return layout;
    }



    public String getCrash(File path)
    {
        try
        {
            if (path.exists())
            {
                return "Last crash from " + new Date(path.lastModified()).toString() + "\n\n" + Utils.readFile(path);
            }
            else
            {
                return "No crash";
            }
        }
        catch (IOException e)
        {
            return "Cannot print crash:\n\n" + e.getMessage();
        }
    }

    @Override
    public void onShow()
    {
        if (getActivity() == null)
        {
            return;
        }
        if (javaView != null && pythonView != null)
        {
            javaView.setText(getCrash(new File(getActivity().getCacheDir(), "javacrash.txt")));
            pythonView.setText(getCrash(new File(getActivity().getCacheDir(), "pythoncrash.txt")));
        }
    }
}
