package com.appy;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.util.TypedValue;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import kotlin.Triple;

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

    public interface KeyMapper<T>
    {
        float map(T t);
    }

    public static ArrayList<String> bundleDiff(Bundle a, Bundle b)
    {

        Set<String> aKeys = a == null ? new HashSet<>() : a.keySet();
        Set<String> bKeys = b == null ? new HashSet<>() : b.keySet();

        Pair<Set<String>, Set<String>> intersection_xor = Utils.intersectionAndXor(aKeys, bKeys);

        ArrayList<String> changedKeys = new ArrayList<>(intersection_xor.second);

        for (String key : intersection_xor.first)
        {
            Object aV = a.get(key);
            Object bV = b.get(key);
            if ((aV == null && bV != null) || (aV != null && !aV.equals(bV)))
            {
                changedKeys.add(key);
            }
        }

        return changedKeys;
    }

    public static <T> Pair<T, T> minmax(Collection<T> collection, KeyMapper<T> mapper)
    {
        boolean first = true;
        T minE = null;
        T maxE = null;
        float minV = 0;
        float maxV = 0;

        for (T element : collection)
        {
            float value = mapper.map(element);
            if (first)
            {
                first = false;
                minE = element;
                maxE = element;
                minV = value;
                maxV = value;
            }
            else if (value < minV)
            {
                minE = element;
                minV = value;
            }
            else if (value > maxV)
            {
                maxE = element;
                maxV = value;
            }
        }

        return new Pair<>(minE, maxE);
    }

    public static byte[] chooseIcon(DictObj.Dict icons, String selector, int width, int height)
    {
        if (icons == null || icons.size() == 0)
        {
            return null;
        }

        Pattern reg = Pattern.compile(Constants.APP_ICON_REGEX);

        ArrayList<Triple<String, String, Size>> parsed = new ArrayList<>();
        for (String key : icons.keyset())
        {
            Matcher m = reg.matcher(key);
            if (m.matches())
            {
                parsed.add(new Triple<>(key, m.group(1), new Size(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))));
            }
        }

        if (parsed.isEmpty())
        {
            //default to first
            return icons.getBytes(icons.keys()[0]);
        }

        ArrayList<Triple<String, String, Size>> selector_filtered = new ArrayList<>();

        if (selector != null)
        {
            for (Triple<String, String, Size> key : parsed)
            {
                if (key.component2().equalsIgnoreCase(selector))
                {
                    selector_filtered.add(key);
                }
            }
        }

        if (selector_filtered.isEmpty())
        {
            //default to empty selector
            for (Triple<String, String, Size> key : parsed)
            {
                if (key.component2().isEmpty())
                {
                    selector_filtered.add(key);
                }
            }

            if (selector_filtered.isEmpty())
            {
                //default to ignoring selector
                selector_filtered = parsed;
            }
        }

        //choose best according to smallest bigger than
        ArrayList<Triple<String, String, Size>> larger_than = new ArrayList<>();

        for (Triple<String, String, Size> key : selector_filtered)
        {
            Size size = key.component3();

            if (size.getWidth() >= width && size.getHeight() >= height)
            {
                larger_than.add(key);
            }
        }

        String key;
        if (larger_than.isEmpty())
        {
            // default to largest
            key = Utils.minmax(selector_filtered, (Triple<String, String, Size> e) -> e.component3().getWidth()).second.component1();
        }
        else
        {
            //choose smallest
            key = Utils.minmax(larger_than, (Triple<String, String, Size> e) -> e.component3().getWidth()).first.component1();
        }

        return icons.getBytes(key);
    }

    public static DictObj.Dict prepareAppIcons(DictObj.Dict data)
    {
        DictObj.Dict result = new DictObj.Dict();
        for (String key : data.keys())
        {
            byte[] buf = data.getBytes(key);
            resizeAppIcons(result, key, BitmapFactory.decodeByteArray(buf, 0, buf.length));
        }
        return result;
    }

    public static byte[] bitmapToBytes(Bitmap bitmap)
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    public static Bitmap drawableToBitmap(Drawable drawable, Integer backgroundColor, Float canvasSizeFactor)
    {
        if (canvasSizeFactor == null)
        {
            canvasSizeFactor = 1f;
        }

        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        if (drawableWidth <= 0)
        {
            drawableWidth = 1;
        }
        if (drawableHeight <= 0)
        {
            drawableHeight = 1;
        }

        Bitmap bitmap = Bitmap.createBitmap((int)Math.ceil(drawableWidth * canvasSizeFactor), (int)Math.ceil(drawableHeight * canvasSizeFactor), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        if (backgroundColor != null)
        {
            canvas.drawColor(backgroundColor);
        }

        float pos = (canvasSizeFactor - 1) / 2;
        int left = (int)Math.floor(pos * drawableWidth);
        int top = (int)Math.floor(pos * drawableHeight);

        drawable.setBounds(left, top, left + drawableWidth, top + drawableHeight);
        drawable.draw(canvas);

        return bitmap;
    }

    public static void resizeAppIcons(DictObj.Dict result, String selector, Bitmap bitmap)
    {
        for (Size size : Constants.APP_ICON_SIZES)
        {
            Bitmap resized = resizeBitmap(bitmap, size, true);
            result.put(selector+"_"+size.getWidth()+"x"+size.getHeight(), bitmapToBytes(resized), false);
        }
    }

    public static Bitmap resizeBitmap(Bitmap bitmap, Size newsize, boolean keepRatio)
    {
        float srcRatio = (float)bitmap.getWidth() / (float)bitmap.getHeight();
        float dstRatio = (float)newsize.getWidth() / (float)newsize.getHeight();

        if (!keepRatio || Math.abs(srcRatio - dstRatio) < 1e-3f)
        {
            return Bitmap.createScaledBitmap(bitmap, newsize.getWidth(), newsize.getHeight(), true);
        }

        int bestWidth, bestHeight;
        if (Float.compare(srcRatio, dstRatio) > 0)
        {
            //src is wider
            bestWidth = newsize.getWidth();
            bestHeight = Math.round((float)bestWidth / srcRatio);
        }
        else
        {
            //dst is wider
            bestHeight = newsize.getHeight();
            bestWidth = Math.round((float)bestHeight * srcRatio);
        }

        Bitmap bestScale = Bitmap.createScaledBitmap(bitmap, bestWidth, bestHeight, true);

        Bitmap newbitmap = Bitmap.createBitmap(newsize.getWidth(), newsize.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newbitmap);

        int left = (newsize.getWidth() - bestWidth) / 2;
        int top = (newsize.getHeight() - bestHeight) / 2;
        canvas.drawBitmap(bestScale, left, top, null);
        return newbitmap;
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

    public static int resolveColor(int colorRes, Resources resources)
    {
        return resources.getColor(colorRes);
    }

    public static Drawable resolveDrawable(int drawableRes, Resources resources)
    {
        return ResourcesCompat.getDrawable(resources, drawableRes, null);
    }

    public static Drawable resolveDrawable(Context context, int drawableRes)
    {
        return resolveDrawable(drawableRes, context.getResources());
    }

    public static String formatFloat(float f)
    {
        DecimalFormat format = new DecimalFormat("0.###"); // Choose the number of decimal places to work with in case they are different than zero and zero value will be removed
        format.setRoundingMode(RoundingMode.HALF_DOWN); // Choose your Rounding Mode
        return format.format(f);
    }

    public static double widgetScaleValue(int[] widgetSize)
    {
        return ((double)Math.min(widgetSize[0], widgetSize[1])) / 500;
    }

    public static double convertUnit(DisplayMetrics metrics, double value, int from, int to)
    {
        //applyDimension takes the "from" unit
        return value * TypedValue.applyDimension(from, 1.0f, metrics) / TypedValue.applyDimension(to, 1.0f, metrics);
    }

    public static double convertUnit(Context context, double value, int from, int to)
    {
        return convertUnit(context.getResources().getDisplayMetrics(), value, from, to);
    }

    public static double parseUnit(Context context, String s, int[] widgetSize)
    {
        return parseUnit(s, context.getResources().getDisplayMetrics(), widgetSize);
    }

    public static double parseUnit(String s, DisplayMetrics metrics, int[] widgetSize)
    {
        s = s.toLowerCase();

        int unit;
        int unitlen = 2;
        boolean widgetScaled = false;

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
        else if (s.endsWith("w"))
        {
            unit = TypedValue.COMPLEX_UNIT_PX;
            unitlen = 1;
            widgetScaled = true;
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

        if (unitlen == s.length())
        {
            throw new RuntimeException("missing numerical value in " + s);
        }

        // handle w prefix to units
        if (unitlen != 0 && s.charAt(s.length() - unitlen - 1) == 'w')
        {
            widgetScaled = true;
            unitlen++;
        }

        if (metrics == null)
        {
            throw new RuntimeException("metrics cannot be NULL");
        }

        if (widgetScaled && widgetSize == null)
        {
            throw new RuntimeException("widget scaled units requested, but no widgetSize supplied");
        }

        double value = convertUnit(metrics, Double.parseDouble(s.substring(0, s.length() - unitlen)), unit, TypedValue.COMPLEX_UNIT_PX);
        if (widgetScaled)
        {
            value *= widgetScaleValue(widgetSize);
        }
        return value;
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

        DialogInterface.OnClickListener yesClick = (dialog, whichButton) -> yesAction.run();

        DialogInterface.OnClickListener noClick = otherAction == null ? null : (dialog, which) -> otherAction.run();

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

        builder.setOnCancelListener(otherAction == null ? null : dialog -> otherAction.run());

        builder.show();
    }

    public static String capWithEllipsis(String s, int maxlen)
    {
        if (s.length() > maxlen)
        {
            return s.substring(0, maxlen - 3) + "...";
        }
        return s;
    }

    public static String enumerableFormat(int n, String singular, String plural)
    {
        if (n == 0)
        {
            return "no " + plural;
        }
        if (n == 1)
        {
            return "1 " + singular;
        }
        return n + " " + plural;
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

    public interface DownloadProgress
    {
        void progress(float progress);
    }

    public static byte[] downloadFile(String url, String savePath, DownloadProgress callback) throws IOException
    {
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.connect();

        int size = connection.getContentLength();

        InputStream input = new BufferedInputStream(connection.getInputStream());
        OutputStream output = savePath != null ? new FileOutputStream(savePath) : new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];
        int count = 0;
        long total = 0;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
            total += count;
            if (size > 0 && callback != null) {
                callback.progress((float)total / size);
            }
        }

        output.flush();

        byte[] ret = null;
        if (savePath == null)
        {
            ret = ((ByteArrayOutputStream)output).toByteArray();
        }

        output.close();
        input.close();
        return ret;
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
