package com.appy;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;

import androidx.appcompat.app.AlertDialog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
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

    public static Pair<byte[], String> readAndHashFile(File path, int sizeLimit) throws IOException
    {
        InputStream fis = new FileInputStream(path);
        return readAndHashFile(fis, sizeLimit);
    }

    public static Pair<byte[], String> readAndHashFile(InputStream fis, int sizeLimit) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            ByteArrayOutputStream total = new ByteArrayOutputStream();

            while (true)
            {
                int read = fis.read(buffer);
                if (read > 0)
                {
                    digest.update(buffer, 0, read);
                    total.write(buffer, 0, read);

                    if (sizeLimit > 0 && sizeLimit < total.size())
                    {
                        throw new IOException("File size exceeded limit: " + sizeLimit);
                    }
                }
                else
                {
                    break;
                }
            }
            byte[] digestResult = digest.digest();
            return new Pair<>(total.toByteArray(), hexlify(digestResult));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static Pair<String, String> readAndHashFileAsString(InputStream fis, int sizeLimit) throws IOException
    {
        Pair<byte[], String> result = readAndHashFile(fis, sizeLimit);
        String text = new String(result.first, StandardCharsets.UTF_8);
        return new Pair<>(text, result.second);
    }

    public static Pair<String, String> readAndHashFileAsString(File path, int sizeLimit) throws IOException
    {
        Pair<byte[], String> result = readAndHashFile(path, sizeLimit);
        String text = new String(result.first, StandardCharsets.UTF_8);
        return new Pair<>(text, result.second);
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

    public static Resources globalResources = null;
    public static void updateGlobalResources(Context context)
    {
        globalResources = context.getResources();
    }

    public static int resolveColor(int colorRes)
    {
        return globalResources.getColor(colorRes);
    }

    public static double parseUnit(String s)
    {
        s = s.toLowerCase();

        int unit;
        int unitlen = 2;
        if (s.endsWith("px"))
        {
            unit = TypedValue.COMPLEX_UNIT_PX;
        }
        else if (s.endsWith("sp"))
        {
            unit = TypedValue.COMPLEX_UNIT_SP;
        }
        else if (s.endsWith("dp"))
        {
            unit = TypedValue.COMPLEX_UNIT_DIP;
        }
        else if (s.endsWith("dip"))
        {
            unit = TypedValue.COMPLEX_UNIT_DIP;
            unitlen = 3;
        }
        else if (s.endsWith("mm"))
        {
            unit = TypedValue.COMPLEX_UNIT_MM;
        }
        else if (s.endsWith("in"))
        {
            unit = TypedValue.COMPLEX_UNIT_IN;
        }
        else if (s.endsWith("pt"))
        {
            unit = TypedValue.COMPLEX_UNIT_PT;
        }
        else
        {
            //one last try
            try
            {
                Float.parseFloat(s);
                unit = TypedValue.COMPLEX_UNIT_PX;
                unitlen = 0;
            }
            catch (NumberFormatException e)
            {
                throw new RuntimeException("Cannot parse unit: " + s);
            }
        }

        if (globalResources == null)
        {
            throw new RuntimeException("globalResources is uninitialized");
        }

        return TypedValue.applyDimension(unit, Float.parseFloat(s.substring(0, s.length() - unitlen)), globalResources.getDisplayMetrics());
    }

    public static String getFilenameFromUri(Context context, Uri uri, String defaultName)
    {
        if ("content".equals(uri.getScheme()))
        {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null))
            {
                if (cursor != null && cursor.moveToFirst())
                {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1)
                    {
                        return cursor.getString(index);
                    }
                }
            }
        }

        String lastSegment = uri.getLastPathSegment();
        if (lastSegment != null)
        {
            return lastSegment;
        }

        return defaultName;
    }

    public static <T> Pair<Set<T>, Set<T>> intersectionAndXor(Collection<T> a, Collection<T> b)
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
        showConfirmationDialog(context, title, message, icon, yes, no, yesAction, null);
    }

    public static void showConfirmationDialog(Context context, String title, String message, int icon, String yes, String no, Runnable yesAction, Runnable otherAction)
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

        DialogInterface.OnClickListener noClick = otherAction == null ? null : new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                otherAction.run();
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
            builder.setNegativeButton(android.R.string.no, noClick);
        }
        else
        {
            builder.setNegativeButton(no, noClick);
        }

        builder.setOnCancelListener(otherAction == null ? null : new DialogInterface.OnCancelListener()
        {
            @Override
            public void onCancel(DialogInterface dialog)
            {
                otherAction.run();
            }
        });

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

        tryWriteStringFile(path, trace);

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

    public static void writeFile(File path, String data) throws IOException
    {
        writeFile(path, data.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeFile(File path, byte[] data) throws IOException
    {
        try(OutputStream os = new FileOutputStream(path))
        {
            os.write(data);
        }
    }

    public static void tryWriteStringFile(String path, String data)
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

            tryWriteStringFile(path, trace);

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
