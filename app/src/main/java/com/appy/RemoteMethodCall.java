package com.appy;

import android.content.res.Resources;
import android.util.Log;
import android.util.Pair;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
    public static ArrayList<Pair<String, String>> resolveResourcePrefix = new ArrayList<>();

    static
    {
        Method[] methods = Reflection.getMethods(RemoteViews.class);
        for (Method method : methods)
        {
            Class<?>[] types = method.getParameterTypes();

            if (!remoteViewMethods.containsKey(method.getName()) || (types.length == 3 && method.getName().toLowerCase().contains(types[2].getSimpleName().toLowerCase()))) //replace method with a better overload
            {
                remoteViewMethods.put(method.getName(), method);
            }
        }

        resolveResourcePrefix.add(new Pair<>("xml.resource.R.", Constants.APP_PACKAGE_NAME + ".R"));
        resolveResourcePrefix.add(new Pair<>("xml.resource.android.R.", "android.R"));
    }

    public static Object reflectStaticPath(String path)
    {
        int index = path.lastIndexOf(".");
        if (index == -1)
        {
            return null;
        }
        String member = path.substring(index + 1);
        path = path.substring(0, index);
        try
        {
            Class<?> cls = Reflection.findClass(path, true, R.drawable.class.getClassLoader());
            Field fld = Reflection.getFieldRaw(cls, member);
            if (fld != null)
            {
                return fld.get(null);
            }
        }
        catch (RuntimeException | IllegalAccessException ignored)
        {
        }

        return null;
    }

    public Object tryResolveXmlResource(Object obj, Class<?> required)
    {
        if (obj == null)
        {
            return null;
        }

        Class<?> cls = obj.getClass();
        if (cls == String.class && (required == Integer.class || required == Integer.TYPE))
        {
            for (Pair<String, String> prefix : resolveResourcePrefix)
            {
                if (((String) obj).startsWith(prefix.first))
                {
                    String path = ((String) obj).substring(prefix.first.length());

                    Object resolved = reflectStaticPath(prefix.second + "." + path);
                    if (resolved != null)
                    {
                        return resolved;
                    }

                    throw new Resources.NotFoundException("Resource '" + prefix.second + "." + path + "' does not exist");
                }
            }
        }

        return obj;
    }

    public static Object cast(Object obj, Class<?> required)
    {
        if (obj == null)
        {
            return null;
        }
        long lng;
        double dbl;
        Class cls = obj.getClass();

        if (cls == Byte.class)
        {
            lng = (long) (Byte) obj;
            dbl = (double) (Byte) obj;
        }
        else if (cls == Short.class)
        {
            lng = (long) (Short) obj;
            dbl = (double) (Short) obj;
        }
        else if (cls == Integer.class)
        {
            lng = (long) (Integer) obj;
            dbl = (double) (Integer) obj;
        }
        else if (cls == Long.class)
        {
            lng = (long) (Long) obj;
            dbl = (double) (Long) obj;
        }
        else if (cls == Float.class)
        {
            lng = ((Float) obj).longValue();
            dbl = (double) (Float) obj;
        }
        else if (cls == Double.class)
        {
            lng = ((Double) obj).longValue();
            dbl = (double) (Double) obj;
        }
        else
        {
            return obj;
        }

        if (required == Byte.class || required == Byte.TYPE)
        {
            return (byte) lng;
        }
        else if (required == Short.class || required == Short.TYPE)
        {
            return (short) lng;
        }
        else if (required == Integer.class || required == Integer.TYPE)
        {
            return (int) lng;
        }
        else if (required == Long.class || required == Long.TYPE)
        {
            return lng;
        }
        else if (required == Float.class || required == Float.TYPE)
        {
            return (float) dbl;
        }
        else if (required == Double.class || required == Double.TYPE)
        {
            return dbl;
        }
        return (int) lng;
    }

    public RemoteMethodCall(String identifier, boolean parentCall, String methodName, Object... args)
    {
        this.identifier = identifier;
        this.parentCall = parentCall;

        this.method = remoteViewMethods.get(methodName);
        if (this.method == null)
        {
            throw new IllegalArgumentException("no remotable method " + methodName + " " + identifier);
        }
        Class<?>[] types = this.method.getParameterTypes();
        this.arguments = new Object[args.length];
        for (int i = 0; i < args.length; i++)
        {
            arguments[i] = tryResolveXmlResource(args[i], types[i + 1]);
            arguments[i] = cast(arguments[i], types[i + 1]);
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
        try
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
                    throw new IllegalArgumentException("cannot call function with " + arguments.length + " arguments");
            }
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Illegal argument exception calling method " + identifier + " " + method.getName() + " " + method.getParameterTypes().length, e);
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
            throw new IllegalArgumentException("json deserialization failed", e);
        }
    }

    public static RemoteMethodCall fromJSON(JSONObject obj) throws JSONException
    {
        Object[] args = new Object[0];
        if (obj.has("arguments"))
        {
            JSONArray jsonargs = obj.getJSONArray("arguments");
            args = new Object[jsonargs.length()];
            for (int i = 0; i < jsonargs.length(); i++)
            {
                args[i] = Serializer.deserialize(jsonargs.getJSONObject(i));
            }
        }

        boolean parentCall = false;
        if (obj.has("parentCall"))
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
