package com.appy;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Icon;
import android.util.Log;
import android.util.Pair;

import androidx.appcompat.app.AlertDialog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
            sb.append(buf, 0, readed);
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

    public static String getCrashPath(Context context, Constants.CrashIndex index)
    {
        return new File(context.getCacheDir(), Constants.CRASHES_FILENAMES[index.ordinal()]).getAbsolutePath();
    }

    public static void zipWithoutPath(String[] files, String zipFile, boolean ignoreNonExistent) throws IOException
    {
        final int BUFFER_SIZE = 4096;

        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile))))
        {
            byte[] data = new byte[BUFFER_SIZE];

            for (String file : files)
            {
                if (!new File(file).exists() && ignoreNonExistent)
                {
                    continue;
                }
                FileInputStream fi = new FileInputStream(file);
                try (BufferedInputStream origin = new BufferedInputStream(fi, BUFFER_SIZE))
                {
                    ZipEntry entry = new ZipEntry(new File(file).getName());
                    out.putNextEntry(entry);
                    int count;
                    while ((count = origin.read(data, 0, BUFFER_SIZE)) != -1)
                    {
                        out.write(data, 0, count);
                    }
                }
            }
        }
    }

    public static void dumpStacktrace(String path)
    {
        Log.e("APPY", "Dumping java stacktrace:");

        Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet())
        {
            sb.append("Thread ");
            sb.append(entry.getKey().getId());
            sb.append("(");
            sb.append(entry.getKey().getName());
            sb.append("):\n");
            sb.append(buildStackTraceString(entry.getValue()));
            sb.append("\n");
        }

        String trace = sb.toString();

        tryWriteFile(path, trace);

        for (String line : trace.split("\n"))
        {
            Log.e("APPY", line);
        }
    }

    public static String buildStackTraceString(StackTraceElement[] elements) {
        StringBuilder sb = new StringBuilder();
        if (elements != null)
        {
            for (StackTraceElement element : elements)
            {
                sb.append(element.toString());
                sb.append("\n");
            }
        }
        else
        {
            sb.append("Stacktrace is null");
        }
        return sb.toString();
    }

    public static void setCrashHandlerIfNeeded(String path)
    {
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        if (handler == null || !(handler instanceof CrashHandler))
        {
            //not ours
            Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(path, handler));
        }
    }

    public static void tryWriteFile(String path, String data)
    {
        BufferedWriter bw = null;
        try
        {
            bw = new BufferedWriter(new FileWriter(path, true));
            bw.write(data);
            bw.flush();
            bw.close();
        }
        catch (IOException e)
        {
            Log.e("APPY", "Exception on tryWriteFile", e);
        }
        finally
        {
            if (bw != null)
            {
                try
                {
                    bw.close();
                }
                catch (IOException ignored)
                {

                }
            }
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

            tryWriteFile(path, trace);

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
