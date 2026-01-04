package com.appy;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Created by Tal on 23/03/2018.
 */
public class Runner implements Runnable
{
    private final String[] command;
    private final File cwd;
    private final String[] envp;

    private final RunnerListener callback;
    private final Thread thread;
    private Process process = null;
    private boolean shouldStop = false;

    public boolean isRunning()
    {
        return !shouldStop && thread.isAlive();
    }

    public Runner(String[] command, File cwd, String[] envp, RunnerListener cb)
    {
        this.command = command;
        this.cwd = cwd;
        this.envp = envp;

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
        kill();
    }

    private void kill()
    {
        if (process != null)
        {
            try
            {
                process.destroy();
            }
            catch (Exception e)
            {
                Log.e("APPY", "Exception on kill", e);
            }
        }
    }

    private String nextLine(StringBuffer str)
    {
        int index = str.indexOf("\n");
        if (index < 0)
        {
            return null;
        }

        String ret = str.substring(0, index);
        str.delete(0, index + 1);
        return ret;
    }

    public void writeToStdin(byte[] data)
    {
        if (process != null)
        {
            try
            {
                process.getOutputStream().write(data);
            }
            catch (IOException e)
            {
                Log.e("APPY", "Exception on writeToStdin", e);
            }
        }
    }

    public static String[] translateCommandline(String toProcess)
    {
        if (toProcess == null || toProcess.isEmpty())
        {
            // no command? no string
            return new String[0];
        }

        // parse with a simple finite state machine

        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;

        int state = normal;
        StringTokenizer tok = new StringTokenizer(toProcess, "\"' ", true);
        ArrayList<String> list = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean lastTokenHasBeenQuoted = false;

        while (tok.hasMoreTokens())
        {
            String nextTok = tok.nextToken();
            switch (state)
            {
                case inQuote:
                    if ("'".equals(nextTok))
                    {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    }
                    else
                    {
                        current.append(nextTok);
                    }
                    break;
                case inDoubleQuote:
                    if ("\"".equals(nextTok))
                    {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    }
                    else
                    {
                        current.append(nextTok);
                    }
                    break;
                default:
                    if ("'".equals(nextTok))
                    {
                        state = inQuote;
                    }
                    else if ("\"".equals(nextTok))
                    {
                        state = inDoubleQuote;
                    }
                    else if (" ".equals(nextTok))
                    {
                        if (lastTokenHasBeenQuoted || current.length() != 0)
                        {
                            list.add(current.toString());
                            current = new StringBuilder();
                        }
                    }
                    else
                    {
                        current.append(nextTok);
                    }
                    lastTokenHasBeenQuoted = false;
                    break;
            }
        }

        if (lastTokenHasBeenQuoted || current.length() != 0)
        {
            list.add(current.toString());
        }

        if (state == inQuote || state == inDoubleQuote)
        {
            throw new IllegalArgumentException("Unbalanced quotes in "
                    + toProcess);
        }

        String[] args = new String[list.size()];
        return list.toArray(args);
    }


    @Override
    public void run()
    {
        Integer exitCode = null;
        try
        {
            process = Runtime.getRuntime().exec(command, envp, cwd);
            BufferedReader bufferedOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader bufferedErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuffer out = new StringBuffer();
            StringBuffer err = new StringBuffer();
            char[] buf = new char[1024];

            boolean exited = false;
            while (!shouldStop)
            {
                try
                {
                    exitCode = process.exitValue();
                    exited = true;
                }
                catch (IllegalThreadStateException ignored)
                {

                }

                boolean nothingHappened = true;
                if (bufferedOut.ready())
                {
                    nothingHappened = false;
                    int len = bufferedOut.read(buf);
                    out.append(buf, 0, len);
                }
                if (bufferedErr.ready())
                {
                    nothingHappened = false;
                    int len = bufferedErr.read(buf);
                    err.append(buf, 0, len);
                }

                String line = nextLine(out);
                if (line != null && callback != null)
                {
                    nothingHappened = false;
                    callback.onLine(line);
                }

                line = nextLine(err);
                if (line != null && callback != null)
                {
                    nothingHappened = false;
                    callback.onLine(line);
                }

                if (nothingHappened)
                {
                    if (exited)
                    {
                        shouldStop = true;
                    }
                    else
                    {
                        try
                        {
                            //avoid busyloop
                            Thread.sleep(500);
                        }
                        catch (InterruptedException e)
                        {
                            Log.e("APPY", "Sleep interrupted", e);
                        }
                    }
                }
            }
        }
        catch (IOException e)
        {
            Log.e("APPY", "Exception on run", e);
        }
        finally
        {
            kill();
        }

        if (callback != null)
        {
            callback.onExited(exitCode);
        }
    }
}
