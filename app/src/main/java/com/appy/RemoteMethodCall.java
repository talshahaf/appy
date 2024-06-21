package com.appy;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.widget.RemoteViews;

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
    private final String identifier;
    private final boolean parentCall;
    private final Method method;
    private final Object[] originalArguments;
    private final Object[] arguments;
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
        resolveResourcePrefix.add(new Pair<>("xml.resource.appy.R.", Constants.APP_PACKAGE_NAME + ".R"));
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
        Class<?> cls = obj.getClass();

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
        else if (required.isEnum())
        {
            return required.cast((int)lng);
        }
        else if (required == ColorStateList.class)
        {
            return ColorStateList.valueOf((int)lng);
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

        this.originalArguments = args;

        Class<?>[] types = this.method.getParameterTypes();
        this.arguments = new Object[originalArguments.length];
        for (int i = 0; i < originalArguments.length; i++)
        {
            arguments[i] = tryResolveXmlResource(originalArguments[i], types[i + 1]);
            arguments[i] = cast(arguments[i], types[i + 1]);
        }
    }

    public String getIdentifier()
    {
        return identifier;
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

    public static Object parameterFromDict(DictObj.Dict dict)
    {
        String type = dict.getString("type");

        if (type.equals("null"))
        {
            return null;
        }
        else if (type.equals("primitive"))
        {
            return dict.get("value");
        }

        if (!dict.hasKey("class"))
        {
            throw new IllegalArgumentException("required key class not found in dict");
        }

        try
        {
            Class clazz = Class.forName(dict.getString("class"));
            switch (type) {
                case "enum":
                {
                    return Enum.valueOf(clazz, dict.getString("value"));
                }
                case "stringable":
                {
                    if (Uri.class.isAssignableFrom(clazz)) {
                        return Uri.parse(dict.getString("value"));
                    }
                    //...
                    else
                    {
                        throw new IllegalArgumentException("unsupported stringable class: " + clazz.getName());
                    }
                }
                case "parcelable":
                {
                    Parcel parcel = Parcel.obtain();
                    try {
                        byte[] b = Base64.decode(dict.getString("value"), Base64.DEFAULT);
                        parcel.unmarshall(b, 0, b.length);
                        parcel.setDataPosition(0);

                        if (ColorStateList.class.isAssignableFrom(clazz))
                        {
                            return ColorStateList.CREATOR.createFromParcel(parcel);
                        }
                        //...
                        else
                        {
                            throw new IllegalArgumentException("unsupported parcellable class: " + clazz.getName());
                        }
                    } finally {
                        parcel.recycle();
                    }
                }
            }
        }
        catch (ClassNotFoundException e)
        {
            throw new IllegalArgumentException("unknown class: " + dict.getString("class"));
        }

        throw new IllegalArgumentException("unknown type: " + type);
    }

    public static RemoteMethodCall fromDict(DictObj.Dict obj)
    {
        Object[] args = new Object[0];
        if (obj.hasKey("arguments"))
        {
            DictObj.List dictargs = obj.getList("arguments");
            args = new Object[dictargs.size()];
            for (int i = 0; i < dictargs.size(); i++)
            {
                args[i] = parameterFromDict(dictargs.getDict(i));
            }
        }

        boolean parentCall = false;
        if (obj.hasKey("parentCall"))
        {
            parentCall = obj.getBoolean("parentCall", false);
        }
        return new RemoteMethodCall(obj.getString("identifier"), parentCall, obj.getString("method"), args);
    }

    public static DictObj.Dict parameterToDict(Object param)
    {
        DictObj.Dict dict = new DictObj.Dict();

        if (param == null)
        {
            dict.put("type", "null");
        }
        else if (param instanceof Boolean)
        {
            dict.put("type", "primitive");
            dict.put("value", (Boolean)param);
        }
        else if (param instanceof Byte)
        {
            dict.put("type", "primitive");
            dict.put("value", (Byte)param);
        }
        else if (param instanceof Character)
        {
            dict.put("type", "primitive");
            dict.put("value", (Character)param);
        }
        else if (param instanceof Short)
        {
            dict.put("type", "primitive");
            dict.put("value", (Short)param);
        }
        else if (param instanceof Integer)
        {
            dict.put("type", "primitive");
            dict.put("value", (Integer)param);
        }
        else if (param instanceof Long)
        {
            dict.put("type", "primitive");
            dict.put("value", (Long)param);
        }
        else if (param instanceof Float)
        {
            dict.put("type", "primitive");
            dict.put("value", (Float)param);
        }
        else if (param instanceof Double)
        {
            dict.put("type", "primitive");
            dict.put("value", (Double)param);
        }
        else if (param instanceof String)
        {
            dict.put("type", "primitive");
            dict.put("value", (String)param);
        }
        else if (param instanceof CharSequence)
        {
            dict.put("type", "primitive");
            dict.put("value", ((CharSequence)param).toString());
        }
        else if (param.getClass().isEnum())
        {
            dict.put("type", "enum");
            dict.put("class", param.getClass().getName());
            dict.put("value", ((Enum<?>) param).name());
        }
        else if (param instanceof Uri)
        {
            dict.put("type", "stringable");
            dict.put("class", param.getClass().getName());
            dict.put("value", param.toString());
        }
        else if (param instanceof Parcelable)
        {
            dict.put("type", "parcelable");
            dict.put("class", param.getClass().getName());
            Parcel parcel = Parcel.obtain();
            try
            {
                ((ColorStateList) param).writeToParcel(parcel, 0);
                dict.put("value", Base64.encodeToString(parcel.marshall(), Base64.DEFAULT));
            }
            finally
            {
                parcel.recycle();
            }
        }
        else
        {
            throw new IllegalArgumentException("cannot serialize remote call parameter " + param.getClass().getName() + " " + param.toString());
        }

        return dict;
    }

    public DictObj.Dict toDict()
    {
        DictObj.Dict obj = new DictObj.Dict();
        obj.put("identifier", identifier);
        obj.put("parentCall", parentCall);
        obj.put("method", method.getName());
        if (arguments.length > 0)
        {
            DictObj.List args = new DictObj.List();
            for (Object arg : originalArguments)
            {
                args.add(parameterToDict(arg));
            }
            obj.put("arguments", args);
        }
        return obj;
    }
}
