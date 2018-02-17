package com.happy;

import android.content.Context;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Created by Tal on 14/01/2018.
 */
public class RemoteMethodCall
{
    private String identifier;
    private boolean parentCall;
    private Method method;
    private Object[] arguments;
    public static HashMap<String, Method> remoteViewMethods = new HashMap<>();
    static
    {
        Method[] methods = RemoteViews.class.getMethods();
        for(Method method : methods)
        {
            remoteViewMethods.put(method.getName(), method);
        }
    }

    public RemoteMethodCall(String identifier, boolean parentCall, String method, Object... args)
    {
        this.identifier = identifier;
        this.parentCall = parentCall;
        arguments = args;
        this.method = remoteViewMethods.get(method);
        if(this.method == null)
        {
            throw new IllegalArgumentException("no method "+method);
        }
    }

    public String getIdentifier()
    {
        return identifier;
    }

    public String toString()
    {
        try
        {
            return toJSONObj().toString(2);
        }
        catch (JSONException e)
        {
            return "failed to string";
        }
    }

    public boolean isParentCall()
    {
        return parentCall;
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

    public static RemoteMethodCall fromJSON(String json)
    {
        try
        {
            return fromJSON(new JSONObject(json));
        }
        catch (JSONException e)
        {
            e.printStackTrace();
            throw new IllegalArgumentException("json deserialization failed");
        }
    }

    public static RemoteMethodCall fromJSON(JSONObject obj) throws JSONException
    {
        Object[] args = new Object[0];
        if(obj.has("arguments"))
        {
            JSONArray jsonargs = obj.getJSONArray("arguments");
            args = new Object[jsonargs.length()];
            for (int i = 0; i < jsonargs.length(); i++)
            {
                args[i] = jsonargs.get(i);
            }
        }

        boolean parentCall = false;
        if(obj.has("parentCall"))
        {
            parentCall = obj.getBoolean("parentCall");
        }
        return new RemoteMethodCall(obj.getString("identifier"), parentCall, obj.getString("method"), args);
    }

    public JSONObject toJSONObj() throws JSONException
    {
        JSONObject obj = new JSONObject();
        obj.put("identifier", identifier);
        obj.put("parentCall", parentCall);
        obj.put("method", method.getName());
        if (arguments.length > 0)
        {
            JSONArray jsonargs = new JSONArray();
            for (Object arg : arguments)
            {
                jsonargs.put(arg);
            }
            obj.put("arguments", jsonargs);
        }
        return obj;
    }
}
