package com.appy;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Serializer
{
    public static JSONObject serialize(Object obj) throws JSONException
    {
        JSONObject jsonobj = new JSONObject();

        if(obj == null)
        {
            jsonobj.put("type", "null");
            return jsonobj;
        }

        if(obj instanceof Boolean || obj instanceof Byte || obj instanceof Character || obj instanceof Short || obj instanceof Integer || obj instanceof Long || obj instanceof Float || obj instanceof Double
                || obj instanceof String || obj instanceof CharSequence || obj instanceof JSONArray || obj instanceof JSONObject)
        {
            jsonobj.put("type", "primitive");
            jsonobj.put("value", obj);
            return jsonobj;
        }

        if(obj.getClass().isEnum())
        {
            jsonobj.put("type", "enum");
            jsonobj.put("class", obj.getClass().getName());
            jsonobj.put("value", ((Enum)obj).name());
            return jsonobj;
        }

        if(obj instanceof Uri)
        {
            jsonobj.put("type", "complicated");
            jsonobj.put("class", obj.getClass().getName());
            jsonobj.put("value", obj.toString());
        }

//        if(obj instanceof Bitmap || obj instanceof Icon) // || obj instanceof Bundle || obj instanceof Intent
//        {
//            jsonobj.put("type", "complicated");
//            jsonobj.put("class", obj.getClass().getName());
//            jsonobj.put("value", obj);
//            return jsonobj;
//        }

        throw new IllegalArgumentException("cannot serialize " + obj.getClass().getName() + " " + obj.toString());
    }

    public static Object deserialize(JSONObject value) throws JSONException
    {
        if(value.getString("type").equals("null"))
        {
            return null;
        }

        if(value.getString("type").equals("primitive"))
        {
            return value.get("value");
        }

        try
        {
            Class clazz = Class.forName(value.getString("class"));
            if(value.getString("type").equals("enum"))
            {
                return Enum.valueOf(clazz, value.getString("value"));
            }
            if(value.getString("type").equals("complicated"))
            {
                if(clazz.equals(Uri.class))
                {
                    return Uri.parse(value.getString("value"));
                }
            }
        }
        catch (ClassNotFoundException e)
        {
            throw new IllegalArgumentException("no such class \"" + value.getString("class") + "\"");
        }

        throw new IllegalArgumentException("cannot deserialize " + value.getString("type"));
    }

    public static Object deserializeString(String value)
    {
        try
        {
            return deserialize(new JSONObject(value));
        }
        catch (JSONException e)
        {
            e.printStackTrace();
            throw new IllegalArgumentException("json deserialization failed "+value);
        }
    }

    public static String serializeToString(Object value)
    {
        try
        {
            return serialize(value).toString(2);
        }
        catch (JSONException e)
        {
            e.printStackTrace();
            throw new IllegalArgumentException("json serialization failed");
        }
    }
}
