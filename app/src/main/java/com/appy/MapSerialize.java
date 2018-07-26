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
    interface Converter<M, N>
    {
        N convert(M m);
        M invert(N n);
    }

    static class IntKey implements Converter<Integer, String>
    {
        @Override
        public String convert(Integer integer)
        {
            return integer.toString();
        }

        @Override
        public Integer invert(String s)
        {
            return Integer.parseInt(s);
        }
    }

    static class LongKey implements Converter<Long, String>
    {
        @Override
        public String convert(Long l)
        {
            return l.toString();
        }

        @Override
        public Long invert(String s)
        {
            return Long.parseLong(s);
        }
    }

    static class IntValue implements Converter<Integer, Object>
    {

        @Override
        public Object convert(Integer integer)
        {
            return integer;
        }

        @Override
        public Integer invert(Object o)
        {
            return (Integer)o;
        }
    }

    static class StringValue implements Converter<String, Object>
    {

        @Override
        public Object convert(String s)
        {
            return s;
        }

        @Override
        public String invert(Object o)
        {
            return o.toString();
        }
    }

    public String serialize(HashMap<T, S> map, Converter<T, String> keySerializer, Converter<S, Object> valueSerializer)
    {
        try
        {
            return toJson(map, keySerializer, valueSerializer).toString();
        }
        catch (JSONException e)
        {
            throw new IllegalArgumentException("json serialization failed", e);
        }
    }

    public HashMap<T, S> deserialize(String str, Converter<T, String> keyDeserializer, Converter<S, Object> valueDeserializer)
    {
        try
        {
            return fromJson(new JSONObject(str), keyDeserializer, valueDeserializer);
        }
        catch (JSONException e)
        {
            throw new IllegalArgumentException("json deserialization failed", e);
        }
    }

    public JSONObject toJson(HashMap<T, S> map, Converter<T, String> keySerializer, Converter<S, Object> valueSerializer) throws JSONException
    {
        JSONObject obj = new JSONObject();
        for(Map.Entry<T, S> entry : map.entrySet())
        {
            obj.put(keySerializer.convert(entry.getKey()), valueSerializer.convert(entry.getValue()));
        }
        return obj;
    }

    public HashMap<T, S> fromJson(JSONObject obj, Converter<T, String> keyDeserializer, Converter<S, Object> valueDeserializer) throws JSONException
    {
        HashMap<T, S> map = new HashMap<>();
        Iterator<String> iter = obj.keys();
        while(iter.hasNext())
        {
            String key = iter.next();
            map.put(keyDeserializer.invert(key), valueDeserializer.invert(obj.get(key)));
        }
        return map;
    }
}
