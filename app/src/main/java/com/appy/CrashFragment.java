package com.appy;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Date;

/**
 * Created by Tal on 19/03/2018.
 */

public class CrashFragment extends MyFragment
{
    TextView javaView = null;
    TextView pythonView = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View layout = inflater.inflate(R.layout.fragment_crash, container, false);
        javaView = layout.findViewById(R.id.java_crash);
        pythonView = layout.findViewById(R.id.python_crash);
        onShow();
        return layout;
    }

    public String readFile(File path) throws IOException
    {
        FileReader reader = new FileReader(path);

        StringBuilder sb = new StringBuilder();

        char[] buf = new char[4096];
        int readed;
        do
        {
            readed = reader.read(buf, 0, buf.length);
            sb.append(buf);
        } while(readed == buf.length);
        return sb.toString();
    }

    public String getCrash(File path)
    {
        try
        {
            if (path.exists()) {
                return "Last crash from " + new Date(path.lastModified()).toString() + "\n\n" + readFile(path);
            }
            else
            {
                return "No crash";
            }
        }
        catch (IOException e)
        {
            return "Cannot print crash:\n\n"+e.getMessage();
        }
    }

    @Override
    public void onShow()
    {
        if(getActivity() == null)
        {
            return;
        }
        if(javaView != null && pythonView != null)
        {
            javaView.setText(getCrash(new File(getActivity().getCacheDir(), "javacrash.txt")));
            pythonView.setText(getCrash(new File(getActivity().getCacheDir(), "pythoncrash.txt")));
        }
    }
}
