package com.appy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by Tal on 02/03/2018.
 */

public class MapSerialize<T, S>
{
    interface IConverter<M>
    {
        M fromString(String str);
    }

    static class IntConverter implements IConverter<Integer>
    {
        @Override
        public Integer fromString(String str)
        {
            return Integer.parseInt(str);
        }
    }

    static class StringConverter implements IConverter<String>
    {
        @Override
        public String fromString(String str)
        {
            return str;
        }
    }

    public String serialize(HashMap<T, S> map)
    {
        try
        {
            return toJson(map).toString();
        }
        catch (JSONException e)
        {
            e.printStackTrace();
            throw new IllegalArgumentException("json serialization failed");
        }
    }

    public HashMap<T, S> deserialize(String str, IConverter<T> toT, IConverter<S> toS)
    {
        try
        {
            return fromJson(new JSONObject(str), toT, toS);
        }
        catch (JSONException e)
        {
            e.printStackTrace();
            throw new IllegalArgumentException("json deserialization failed");
        }
    }

    public JSONObject toJson(HashMap<T, S> map) throws JSONException
    {
        JSONObject obj = new JSONObject();
        for(Map.Entry<T, S> entry : map.entrySet())
        {
            obj.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return obj;
    }

    public HashMap<T, S> fromJson(JSONObject obj, IConverter<T> toT, IConverter<S> toS) throws JSONException
    {
        HashMap<T, S> map = new HashMap<>();
        Iterator<String> iter = obj.keys();
        while(iter.hasNext())
        {
            String key = iter.next();
            map.put(toT.fromString(key), toS.fromString(obj.getString(key)));
        }
        return map;
    }
}
