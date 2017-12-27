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

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        makePython();

        System.load(new File(getFilesDir(), "/lib/libpython3.6m.so.1.0").getAbsolutePath());
        System.loadLibrary("native");

        pythonInit(getFilesDir().getAbsolutePath());

        //Log.d("HAPY", System.getenv("PYTHONHOME"));
        //Log.d("HAPY", System.getenv("LD_LIBRARY_PATH"));
    }

    public static class Test
    {
        public static Long value = 23L;
        public Long ins_value = 24L;

        public Test()
        {

        }

        public Test(boolean z, byte b, char c,      short s, int i,     long j, float f, double d,
                                 Boolean Z, Byte B, Character C, Short S, Integer I, Long J, Float F, Double D,
                                 Object O)
        {
            //Log.d("HAPY", "const got: "+z+" "+b+" "+c+" "+s+" "+i+" "+j+" "+f+" "+d+" | "+Z+" "+B+" "+C+" "+S+" "+I+" "+J+" "+F+" "+D+" | "+O);
        }

        public static long test(long i)
        {
            Log.d("HAPY", "test: "+i);
            return i * 2;
        }
        public long ins_test(long i)
        {
            Log.d("HAPY", "ins_test: "+i);
            return i * 7;
        }

        public static long test_void()
        {
            Log.d("HAPY", "test_void");
            return 48;
        }

        public static void void_test(long l)
        {
            Log.d("HAPY", "test_void: "+l);
        }

        public static void void_void()
        {
            Log.d("HAPY", "void_void");
        }

        public static Object all(boolean z, byte b, char c,      short s, int i,     long j, float f, double d,
                                 Boolean Z, Byte B, Character C, Short S, Integer I, Long J, Float F, Double D,
                                 Object O)
        {
            Log.d("HAPY", "all got: "+z+" "+b+" "+c+" "+s+" "+i+" "+j+" "+f+" "+d+" | "+Z+" "+B+" "+C+" "+S+" "+I+" "+J+" "+F+" "+D+" | "+O);
            return O;
        }

        public static Short primitive(Short x)
        {
            return x != null ? x : -1;
        }
    }

    protected static native int pythonInit(String pythonpath);
    protected static native int pythonRun(String script);
}
