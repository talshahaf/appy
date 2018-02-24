package com.appy;

import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by Tal on 16/02/2018.
 */

public class Attributes
{
    enum Type
    {
        LEFT,
        TOP,
        RIGHT,
        BOTTOM,
        WIDTH,
        HEIGHT,
    }

    static class AttributeValue
    {
        static class Reference
        {
            public int id;
            public Type type;
            public double factor;
        }

        enum Function
        {
            IDENTITY,
            MIN,
            MAX,
        }

        public Function function = Function.IDENTITY;
        public ArrayList<Pair<ArrayList<Reference>, Double>> arguments = new ArrayList<>();
        public Double resolvedValue;
        public boolean triviallyResolved = false;

        public boolean isResolved()
        {
            return resolvedValue != null;
        }

        public void tryTrivialResolve(double value)
        {
            if(arguments.isEmpty() && resolvedValue == null)
            {
                resolvedValue = value;
                triviallyResolved = true;
            }
        }

        public static AttributeValue fromJSON(JSONObject obj) throws JSONException
        {
            AttributeValue ret = new AttributeValue();

            ret.function = Function.valueOf(obj.getString("function"));

            JSONArray argumentsArray = obj.getJSONArray("arguments");
            for(int i = 0; i < argumentsArray.length(); i++)
            {
                JSONObject argumentObj = argumentsArray.getJSONObject(i);

                ArrayList<Reference> references = new ArrayList<>();
                JSONArray referenceArray = argumentObj.getJSONArray("references");
                for(int j = 0; j < referenceArray.length(); j++)
                {
                    JSONObject referenceObj = referenceArray.getJSONObject(j);

                    Reference ref = new Reference();
                    ref.id = referenceObj.getInt("id");
                    ref.type = Type.valueOf(referenceObj.getString("type"));
                    ref.factor = referenceObj.getDouble("factor");
                    references.add(ref);
                }

                ret.arguments.add(new Pair<>(references, argumentObj.getDouble("amount")));
            }

            return ret;
        }

        public JSONObject toJSON() throws JSONException
        {
            JSONObject obj = new JSONObject();
            obj.put("function",  function.name());
            JSONArray argArray = new JSONArray();
            for(Pair<ArrayList<Reference>, Double> arg : arguments)
            {
                JSONObject argObj = new JSONObject();
                argObj.put("amount", arg.second);

                JSONArray refArray = new JSONArray();
                for(Reference ref : arg.first)
                {
                    JSONObject refObj = new JSONObject();
                    refObj.put("id", ref.id);
                    refObj.put("type", ref.type.name());
                    refObj.put("factor", ref.factor);
                    refArray.put(refObj);
                }
                argObj.put("references", refArray);
                argArray.put(argObj);
            }
            obj.put("arguments", argArray);
            obj.put("triviallyResolved", triviallyResolved);
            if(resolvedValue != null)
            {
                obj.put("resolvedValue", resolvedValue);
            }
            return obj;
        }

        public String toString()
        {
            try
            {
                return toJSON().toString(2);
            }
            catch (JSONException e)
            {
                e.printStackTrace();
                throw new IllegalArgumentException("json serialization failed");
            }
        }
    }

    public HashMap<Type, AttributeValue> attributes;

    public Attributes()
    {
        attributes = new HashMap<>();
        for(Type t : Type.values())
        {
            attributes.put(t, new AttributeValue()); //fill with unresolved values
        }
    }

    public JSONObject toJSON() throws JSONException
    {
        JSONObject obj = new JSONObject();
        for(Map.Entry<Type, AttributeValue> e : attributes.entrySet())
        {
            obj.put(e.getKey().toString(), e.getValue().toJSON());
        }
        return obj;
    }

    public static Attributes fromJSON(JSONObject obj) throws JSONException
    {
        Attributes attributes = new Attributes();
        Iterator<String> it = obj.keys();
        while(it.hasNext())
        {
            String key = it.next();
            attributes.attributes.put(Type.valueOf(key), AttributeValue.fromJSON(obj.getJSONObject(key)));
        }
        return attributes;
    }

    public ArrayList<AttributeValue> unresolved()
    {
        ArrayList<AttributeValue> ret = new ArrayList<>();
        for(Map.Entry<Type, AttributeValue> e : attributes.entrySet())
        {
            if(e.getValue().resolvedValue == null)
            {
                ret.add(e.getValue());
            }
        }
        return ret;
    }

    public String toString()
    {
        try
        {
            return toJSON().toString(2);
        }
        catch (JSONException e)
        {
            e.printStackTrace();
            throw new IllegalArgumentException("json serialization failed");
        }
    }
}