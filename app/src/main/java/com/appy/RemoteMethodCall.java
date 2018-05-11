package com.appy;

import android.util.Log;
import android.util.Pair;
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

    public static Object cast(Object obj, Class<?> required)
    {
        long lng;
        double dbl;
        Class cls = obj.getClass();

        if(cls == Byte.class)
        {
            lng = (long)(Byte)obj;
            dbl = (double)(Byte)obj;
        }
        else if(cls == Short.class)
        {
            lng = (long)(Short)obj;
            dbl = (double)(Short)obj;
        }
        else if(cls == Integer.class)
        {
            lng = (long)(Integer)obj;
            dbl = (double)(Integer)obj;
        }
        else if(cls == Long.class)
        {
            lng = (long)(Long)obj;
            dbl = (double)(Long)obj;
        }
        else if(cls == Float.class)
        {
            lng = ((Float)obj).longValue();
            dbl = (double)(Float)obj;
        }
        else if(cls == Double.class)
        {
            lng = ((Double)obj).longValue();
            dbl = (double)(Double)obj;
        }
        else
        {
            return obj;
        }

        if(required == Byte.class)
        {
            return (byte)lng;
        }
        else if(required == Short.class)
        {
            return (short)lng;
        }
        else if(required == Integer.class)
        {
            return (int)lng;
        }
        else if(required == Long.class)
        {
            return lng;
        }
        else if(required == Float.class)
        {
            return (float)dbl;
        }
        else if(required == Double.class)
        {
            return dbl;
        }
        return (int)lng;
    }

    public RemoteMethodCall(String identifier, boolean parentCall, String method, Object... args)
    {
        this.identifier = identifier;
        this.parentCall = parentCall;
        this.method = remoteViewMethods.get(method);
        if(this.method == null)
        {
            throw new IllegalArgumentException("no remotable method "+method);
        }

        Class<?>[] types = this.method.getParameterTypes();
        arguments = new Object[args.length];
        for(int i = 0; i < args.length; i++)
        {
            arguments[i] = cast(args[i], types[i]);
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
        //Log.d("APPY", "calling " + id + " " + method.getName());
        switch (arguments.length)
        {
            case 0:
                method.invoke(view, id);
                break;
            case 1:
                method.invoke(view, id, arguments[0]);
                break;
            case 2:
                method.invoke(view, id, arguments[0], arguments[1]);
                break;
            case 3:
                method.invoke(view, id, arguments[0], arguments[1], arguments[2]);
                break;
            case 4:
                method.invoke(view, id, arguments[0], arguments[1], arguments[2], arguments[3]);
                break;
            case 5:
                method.invoke(view, id, arguments[0], arguments[1], arguments[2], arguments[3], arguments[4]);
                break;
            case 6:
                method.invoke(view, id, arguments[0], arguments[1], arguments[2], arguments[3], arguments[4], arguments[5]);
                break;
            case 7:
                method.invoke(view, id, arguments[0], arguments[1], arguments[2], arguments[3], arguments[4], arguments[5], arguments[6]);
                break;
            case 8:
                method.invoke(view, id, arguments[0], arguments[1], arguments[2], arguments[3], arguments[4], arguments[5], arguments[6], arguments[7]);
                break;
            case 9:
                method.invoke(view, id, arguments[0], arguments[1], arguments[2], arguments[3], arguments[4], arguments[5], arguments[6], arguments[7], arguments[8]);
                break;
            default:
                throw new IllegalArgumentException("cannot call function with "+arguments.length+" arguments");
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
                args[i] = Serializer.deserialize(jsonargs.getJSONObject(i));
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
                jsonargs.put(Serializer.serialize(arg));
            }
            obj.put("arguments", jsonargs);
        }
        return obj;
    }
}
