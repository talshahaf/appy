package com.happy;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
{
    public static void printFnames(File sDir){
        File[] faFiles = sDir.listFiles();
        for(File file: faFiles){
            Log.d("HAPY", file.getAbsolutePath());
            if(file.isDirectory()){
                printFnames(file);
            }
        }
    }

    public static void printFile(File file)
    {
        try
        {
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            while ((line = bufferedReader.readLine()) != null)
            {
                Log.e("HAPY", line);
            }
            fileReader.close();
        }
        catch(IOException e)
        {
            Log.e("HAPY", "exception", e);
        }
    }

    public static void printAll(InputStream is)
    {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line;
        try
        {
            while ( (line = br.readLine()) != null)
            {
                Log.d("HAPY", line);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void runProcess(String[] command)
    {
        try
        {
            Process process = Runtime.getRuntime().exec(command);
            printAll(process.getInputStream());
            process.waitFor();
        }
        catch (IOException|InterruptedException e)
        {
            Log.e("HAPY", "exception", e);
        }
    }

    public void makePython()
    {
        if(!new File(getFilesDir(), "lib/libpython3.so").exists())
        {
            Log.d("HAPY", "unpacking python");

            File newfile = new File(getFilesDir(), "test");
            //TODO without creating?

            runProcess(new String[]{"sh", "-c", "tar -xf /sdcard/python.tar -C " + getFilesDir().getAbsolutePath()+" 2>&1"});

            //printFnames(getFilesDir());
            Log.d("HAPY", "done unpacking python");
        }
        else
        {
            Log.d("HAPY", "not unpacking python");
        }
    }
    static {
        System.loadLibrary("native");
    }
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        makePython();
        pythonInit(getFilesDir().getAbsolutePath());

        //Log.d("HAPY", System.getenv("PYTHONHOME"));
        //Log.d("HAPY", System.getenv("LD_LIBRARY_PATH"));
    }

    public static void test(long i)
    {
        Log.d("HAPY", "test: "+i);
    }

    protected static native int pythonInit(String pythonpath);
    protected static native int pythonRun(String script);
}
