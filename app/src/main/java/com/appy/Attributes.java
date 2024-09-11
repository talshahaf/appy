package com.appy;

import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Tal on 16/02/2018.
 */

public class Attributes
{
    public enum Type
    {
        LEFT,
        TOP,
        RIGHT,
        BOTTOM,
        WIDTH,
        HEIGHT,
    }

    public static class AttributeValue
    {
        //such as text.top * 2
        public static class Reference
        {
            public long id;
            public Type type;
            public double factor;

            public Reference(long id, Type type, double factor)
            {
                this.id = id;
                this.type = type;
                this.factor = factor;
            }
        }

        enum Function
        {
            IDENTITY,
            MIN,
            MAX,
        }

        // such as min(text.top + widget.height / 5 + 10, text.bottom - 10)
        public Function function = Function.IDENTITY;

        // list elements such as (text.top * 2 + widget.height / 5 + 1)
        public ArrayList<Pair<ArrayList<Reference>, Double>> arguments = new ArrayList<>();
        public double finalFactor = 1.0;
        public Double resolvedValue;

        public boolean isResolved()
        {
            return resolvedValue != null;
        }

        public boolean hasConstraints()
        {
            return !arguments.isEmpty();
        }

        public AttributeValue()
        {

        }

        public static AttributeValue fromDict(DictObj.Dict obj)
        {
            AttributeValue ret = new AttributeValue();

            ret.function = Function.valueOf(obj.getString("function"));

            ret.finalFactor = obj.getDouble("finalFactor", 1.0);

            DictObj.List argumentsArray = obj.getList("arguments");
            for (int i = 0; i < argumentsArray.size(); i++)
            {
                DictObj.Dict argumentObj = argumentsArray.getDict(i);

                ArrayList<Reference> references = new ArrayList<>();
                DictObj.List referenceArray = argumentObj.getList("references");
                for (int j = 0; j < referenceArray.size(); j++)
                {
                    DictObj.Dict referenceObj = referenceArray.getDict(j);

                    Reference ref = new Reference(referenceObj.getLong("id", 0), Type.valueOf(referenceObj.getString("type")), referenceObj.getDouble("factor", 0));
                    references.add(ref);
                }

                ret.arguments.add(new Pair<>(references, argumentObj.getDouble("amount", 0)));
            }

            return ret;
        }

        public DictObj.Dict toDict()
        {
            DictObj.Dict obj = new DictObj.Dict();
            obj.put("function", function.name());
            DictObj.List argArray = new DictObj.List();
            for (Pair<ArrayList<Reference>, Double> arg : arguments)
            {
                DictObj.Dict argObj = new DictObj.Dict();
                argObj.put("amount", arg.second);

                DictObj.List refArray = new DictObj.List();
                for (Reference ref : arg.first)
                {
                    DictObj.Dict refObj = new DictObj.Dict();
                    refObj.put("id", ref.id);
                    refObj.put("type", ref.type.name());
                    refObj.put("factor", ref.factor);
                    refArray.add(refObj);
                }
                argObj.put("references", refArray);
                argArray.add(argObj);
            }
            obj.put("arguments", argArray);
            obj.put("finalFactor", finalFactor);
            if (resolvedValue != null)
            {
                obj.put("resolvedValue", resolvedValue);
            }
            return obj;
        }
    }

    public HashMap<Type, AttributeValue> attributes;

    public Attributes()
    {
        attributes = new HashMap<>();
        for (Type t : Type.values())
        {
            attributes.put(t, new AttributeValue()); //fill with unresolved values
        }
    }

    public DictObj.Dict toDict()
    {
        DictObj.Dict obj = new DictObj.Dict();
        for (Map.Entry<Type, AttributeValue> e : attributes.entrySet())
        {
            obj.put(e.getKey().toString(), e.getValue().toDict());
        }
        return obj;
    }

    public static Attributes fromDict(DictObj.Dict obj)
    {
        Attributes attributes = new Attributes();
        for (DictObj.Entry entry : obj.entries())
        {
            attributes.attributes.put(Type.valueOf(entry.key), AttributeValue.fromDict((DictObj.Dict)entry.value));
        }
        return attributes;
    }

    public HashMap<Type, AttributeValue> unresolved()
    {
        HashMap<Type, AttributeValue> ret = new HashMap<>();
        for (Map.Entry<Type, AttributeValue> e : attributes.entrySet())
        {
            if (e.getValue().resolvedValue == null)
            {
                ret.put(e.getKey(), e.getValue());
            }
        }
        return ret;
    }
}