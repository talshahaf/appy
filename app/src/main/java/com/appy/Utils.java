package com.appy;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Icon;
import android.util.Log;
import android.util.Pair;

import androidx.appcompat.app.AlertDialog;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

public class Utils
{

    public static abstract class ArgRunnable implements Runnable
    {
        Object[] args;

        ArgRunnable(Object... args)
        {
            this.args = args;
        }

        public abstract void run();
    }

    public static String hashFile(String path)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            InputStream fis = new FileInputStream(path);
            while (true)
            {
                int read = fis.read(buffer);
                if (read > 0)
                {
                    digest.update(buffer, 0, read);
                }
                else
                {
                    break;
                }
            }
            byte[] digestResult = digest.digest();
            return hexlify(digestResult);
        }
        catch (IOException | NoSuchAlgorithmException e)
        {
            return "";
        }
    }

    public static String hexlify(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
        {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public static String readFile(File path) throws IOException
    {
        FileReader reader = new FileReader(path);

        StringBuilder sb = new StringBuilder();

        char[] buf = new char[4096];
        int readed;
        do
        {
            readed = reader.read(buf, 0, buf.length);
            sb.append(buf);
        } while (readed == buf.length);
        return sb.toString();
    }

    public static <T> Pair<Set<T>, Set<T>> intersectionAndXor(Set<T> a, Set<T> b)
    {
        Set<T> union = new HashSet<>(a);
        union.addAll(b);

        Set<T> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<T> xor = new HashSet<>(union);
        xor.removeAll(intersection);

        return new Pair<>(intersection, xor);
    }

    public static void showConfirmationDialog(Context context, String title, String message, int icon, String yes, String no, Runnable yesAction)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message);

        if (icon > 0)
        {
            builder.setIcon(icon);
        }

        DialogInterface.OnClickListener yesClick = new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                yesAction.run();
            }
        };

        if (yes == null)
        {
            builder.setPositiveButton(android.R.string.yes, yesClick);
        }
        else
        {
            builder.setPositiveButton(yes, yesClick);
        }

        if (no == null)
        {
            builder.setNegativeButton(android.R.string.no, null);
        }
        else
        {
            builder.setNegativeButton(no, null);
        }

        builder.show();
    }

    public static void setCrashHandlerIfNeeded(Context context)
    {
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        if (handler == null || !(handler instanceof CrashHandler))
        {
            //not ours
            Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(new File(context.getCacheDir(), "javacrash.txt").getAbsolutePath(), handler));
        }
    }

    public static class CrashHandler implements Thread.UncaughtExceptionHandler
    {
        String path;
        Thread.UncaughtExceptionHandler prev;

        public CrashHandler(String path, Thread.UncaughtExceptionHandler prev)
        {
            this.path = path;
            this.prev = prev;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e)
        {
            String trace = Stacktrace.stackTraceString(e);

            BufferedWriter bw = null;
            try
            {
                bw = new BufferedWriter(new FileWriter(path, true));
                bw.write(trace);
                bw.flush();
                bw.close();
            }
            catch (IOException e1)
            {
                Log.e("APPY", "Exception on uncaught exception", e1);
            }
            finally
            {
                if (bw != null)
                {
                    try
                    {
                        bw.close();
                    }
                    catch (IOException e1)
                    {

                    }
                }
            }

            for (String line : trace.split("\n"))
            {
                Log.e("APPY", line);
            }

            if (prev != null)
            {
                prev.uncaughtException(t, e);
            }
        }
    }
}
