package com.appy;

import android.util.Log;

import java.util.ArrayList;

public class Stacktrace
{
    public static String stackTraceString(Throwable t)
    {
        try
        {
            if (t == null)
            {
                return "null";
            }

            StringBuffer sb = new StringBuffer();
            ArrayList<Throwable> exceptionStack = new ArrayList<>();

            Throwable cause = t;
            while (cause != null)
            {
                exceptionStack.add(0, cause);
                cause = cause.getCause();
            }

            stackTraceStringBuffer(sb, exceptionStack.get(0), null);
            for (int i = 1; i < exceptionStack.size(); i++)
            {
                sb.append("\nThe above exception was the direct cause of the following exception:\n");
                stackTraceStringBuffer(sb, exceptionStack.get(i), exceptionStack.get(i - 1));
            }

            return sb.toString();
        }
        catch (Exception e)
        {
            Log.e("APPY", "Exception while handling exception", e);
            return "Exception while handling exception.";
        }
    }

    private static String stackElementToString(StackTraceElement element)
    {

        StringBuilder result = new StringBuilder();
        result.append("File: ");
        if (element.isNativeMethod())
        {
            result.append("Unknown (Native Method)");
        }
        else if (element.getFileName() != null)
        {
            if (element.getLineNumber() >= 0)
            {
                result.append("\"").append(element.getFileName()).append("\", line ").append(element.getLineNumber());
            }
            else
            {
                result.append("\"").append(element.getFileName()).append("\"");
            }
        }
        else
        {
            result.append("Unknown");
            if (element.getLineNumber() >= 0)
            {
                // The line number is actually the dex pc.
                result.append(", line ").append(element.getLineNumber());
            }
        }

        result.append(", in ").append(element.getMethodName());

        return result.toString();
    }

    private static void stackTraceStringBuffer(StringBuffer sb, Throwable t, Throwable parent)
    {
        sb.append("Traceback (most recent call last):\n");

        StackTraceElement[] stack = t.getStackTrace();

        // The stacktrace
        if (stack == null || stack.length == 0)
        {
            sb.append("  <<No stacktrace available>>\n");
        }
        else
        {
            StackTraceElement prevElement = null;
            int identicalPrev = 0;
            int lastUniqueFrame = stack.length - 1;
            // don't print parent stack again
            if (parent != null)
            {
                StackTraceElement[] parentStack = parent.getStackTrace();
                if (parentStack != null)
                {
                    int parentCounter = parentStack.length - 1;
                    while (lastUniqueFrame > 0 && parentCounter > 0 && stack[lastUniqueFrame].equals(parentStack[parentCounter]))
                    {
                        lastUniqueFrame--;
                        parentCounter--;
                    }
                }
            }
            for (int i = lastUniqueFrame; i >= 0; i--)
            {
                if (prevElement != null && prevElement.equals(stack[i]))
                {
                    identicalPrev++;
                    continue;
                }
                else if (identicalPrev > 0)
                {
                    sb.append("  [Previous line repeated ").append(identicalPrev).append(" more times]\n");
                }
                sb.append("  ");
                sb.append(stack[i] == null ? "Unknown" : stackElementToString(stack[i]));
                sb.append("\n");
                prevElement = stack[i];
                identicalPrev = 0;
            }
            if (identicalPrev > 0)
            {
                sb.append("  [Previous line repeated ").append(identicalPrev).append(" more times]\n");
            }
        }
        sb.append(t.getClass().getName());
        if (t.getMessage() != null)
        {
            sb.append(": ").append(t.getMessage());
        }
    }
}
