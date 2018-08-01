package com.appy;

import org.apache.tools.ant.types.Commandline;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by Tal on 23/03/2018.
 */
public class Runner implements Runnable
{
    private String command;
    private File cwd;

    private RunnerListener callback;
    private Thread thread;
    private Process process = null;
    private boolean shouldStop = false;

    public boolean isRunning()
    {
        return !shouldStop && thread.isAlive();
    }

    public Runner(String command, File cwd, RunnerListener cb)
    {
        this.command = command;
        this.cwd = cwd;

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
                e.printStackTrace();
            }
        }
    }

    private String nextLine(StringBuffer str)
    {
        int index = str.indexOf("\n");
        if(index < 0)
        {
            return null;
        }

        String ret = str.substring(0, index);
        str.delete(0, index + 1);
        return ret;
    }

    public void writeToStdin(byte[] data)
    {
        if(process != null)
        {
            try
            {
                process.getOutputStream().write(data);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run()
    {
        Integer exitCode = null;
        try
        {
            process = Runtime.getRuntime().exec(Commandline.translateCommandline(command), null, cwd);
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
                catch(IllegalThreadStateException e)
                {

                }

                boolean nothingHappened = true;
                if(bufferedOut.ready())
                {
                    nothingHappened = false;
                    int len = bufferedOut.read(buf);
                    out.append(buf, 0, len);
                }
                if(bufferedErr.ready())
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

                if(nothingHappened)
                {
                    if(exited)
                    {
                        shouldStop = true;
                    }
                    else
                    {
                        try
                        {
                            //avoid busyloop
                            Thread.sleep(1000);
                        }
                        catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            kill();
        }

        if(callback != null)
        {
            callback.onExited(exitCode);
        }
    }
}
