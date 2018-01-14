package com.happy;

import android.content.Context;
import android.util.Log;
import android.widget.RemoteViews;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by Tal on 14/01/2018.
 */
public class SimpleVariables implements Variable
{
    Object[] arguments;

    public SimpleVariables(Object... args)
    {
        arguments = args;
    }

    public void Call(Context context, RemoteViews view, String remoteMethod, int id) throws InvocationTargetException, IllegalAccessException
    {
        Method[] methods = RemoteViews.class.getMethods();
        Method method = null;
        for (Method _method : methods)
        {
            if (_method.getName().equalsIgnoreCase(remoteMethod))
            {
                method = _method;
                break;
            }
        }
        if (method == null)
        {
            throw new RuntimeException("no such type");
        }

        Log.d("HAPY", "calling " + id + " " + remoteMethod + " " + method.getName());
        switch (arguments.length)
        {
            case 0:
            {
                method.invoke(view, id);
                break;
            }
            case 1:
            {
                method.invoke(view, id, arguments[0]);
                break;
            }
            case 2:
            {
                method.invoke(view, id, arguments[0], arguments[1]);
                break;
            }
            case 3:
            {
                method.invoke(view, id, arguments[0], arguments[1], arguments[2]);
                break;
            }
            case 4:
            {
                method.invoke(view, id, arguments[0], arguments[1], arguments[2], arguments[3]);
                break;
            }
        }

    }
}
