package com.happy;

import android.content.Context;
import android.util.Log;
import android.widget.RemoteViews;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Created by Tal on 14/01/2018.
 */
public class RemoteMethodCall
{
    Method method;
    Object[] arguments;
    public static HashMap<String, Method> remoteViewMethods = new HashMap<>();
    static
    {
        Method[] methods = RemoteViews.class.getMethods();
        for(Method method : methods)
        {
            remoteViewMethods.put(method.getName(), method);
        }
    }

    public RemoteMethodCall(String method, Object... args)
    {
        this.method = remoteViewMethods.get(method);
        arguments = args;
    }

    public void call(RemoteViews view, int id) throws InvocationTargetException, IllegalAccessException
    {
        Log.d("HAPY", "calling " + id + " " + method.getName());
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
