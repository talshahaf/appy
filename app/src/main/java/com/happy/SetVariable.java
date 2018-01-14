package com.happy;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by Tal on 14/01/2018.
 */
public class SetVariable implements Variable
{
    String type;
    Object var;

    public SetVariable(String type, Object o)
    {
        type = "set" + type.substring(0, 1).toUpperCase() + type.substring(1);
        var = o;
    }

    public SetVariable(boolean bool)
    {
        type = "setBoolean";
        var = bool;
    }

    public SetVariable(byte b)
    {
        type = "setByte";
        var = b;
    }

    public SetVariable(short s)
    {
        type = "setShort";
        var = s;
    }

    public SetVariable(int i)
    {
        type = "setInt";
        var = i;
    }

    public SetVariable(long l)
    {
        type = "setLong";
        var = l;
    }

    public SetVariable(float f)
    {
        type = "setFloat";
        var = f;
    }

    public SetVariable(double d)
    {
        type = "setDouble";
        var = d;
    }

    public SetVariable(char c)
    {
        type = "setChar";
        var = c;
    }

    public SetVariable(String string)
    {
        type = "setString";
        var = string;
    }

    public SetVariable(CharSequence charSequence)
    {
        type = "setCharSequence";
        var = charSequence;
    }

    public SetVariable(Uri uri)
    {
        type = "setUri";
        var = uri;
    }

    public SetVariable(Bitmap bitmap)
    {
        type = "setBitmap";
        var = bitmap;
    }

    public SetVariable(Bundle bundle)
    {
        type = "setBundle";
        var = bundle;
    }

    public SetVariable(Intent intent)
    {
        type = "setIntent";
        var = intent;
    }

    public SetVariable(Icon icon)
    {
        type = "setIcon";
        var = icon;
    }

    public void Call(Context context, RemoteViews view, String remoteMethod, int id) throws InvocationTargetException, IllegalAccessException
    {
        Method[] methods = RemoteViews.class.getMethods();
        Method method = null;
        for (Method _method : methods)
        {
            if (_method.getName().equalsIgnoreCase(type))
            {
                method = _method;
                break;
            }
        }
        if (method == null)
        {
            throw new RuntimeException("no such type");
        }

        if (Widget.remotableMethods.get(view.getLayoutId()) == null)
        {
            Widget.remotableMethods.put(view.getLayoutId(), Widget.getRemotableMethods(context, view.getLayoutId()));
        }
        if (!Widget.remotableMethods.get(view.getLayoutId()).contains(remoteMethod))
        {
            throw new RuntimeException("method " + remoteMethod + " not remotable");
        }

        Log.d("HAPY", "calling " + id + " " + remoteMethod + " " + method.getName() + " " + var);
        method.invoke(view, id, remoteMethod, var);
    }
}
