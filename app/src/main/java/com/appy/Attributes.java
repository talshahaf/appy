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
        MISC, //used as variable
    }

    public static class AttributeValue
    {
        public interface Argument{}
        //such as text.top
        public static class Reference implements Argument
        {
            public long id;
            public Type type;

            public Reference(long id, Type type)
            {
                this.id = id;
                this.type = type;
            }
        }
        public static class Value implements Argument
        {
            public double value;
            public Value(double value)
            {
                this.value = value;
            }
        }

        public static class Function
        {
            public enum FunctionType
            {
                IDENTITY,
                MIN,
                MAX,
                ADD,
                MUL,
                FLOOR,
                CEIL,
                DIV,
                MOD,
                IF,
                //booleans
                EQ,
                LT,
                LE,
                NOT,
                AND, //these instead of ADD/MUL to prevent overflow
                OR,
            }

            FunctionType type;
            int numberOfArguments;
            int totalPosition;

            public Function(String type, int numberOfArguments, int totalPosition)
            {
                //optimization
                if (type.equals("I"))
                {
                    type = "IDENTITY";
                }
                this.type = FunctionType.valueOf(type);
                this.numberOfArguments = numberOfArguments;
                this.totalPosition = totalPosition;
            }

            public String name()
            {
                String n = type.name();
                //optimization
                if (n.equals("IDENTITY"))
                {
                    return "I";
                }
                return n;
            }
        }

        // such as min(text.top + widget.height / 5 + 10, text.bottom - 10)
        //
        public ArrayList<Function> functions = new ArrayList<>();

        // list elements such as (text.top * 2 + widget.height / 5 + 1)
        public ArrayList<Argument> arguments = new ArrayList<>();
        public Double resolvedValue;
        public String debugName = null;

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

            DictObj.List argumentArray = obj.getList("arguments");
            for (int i = 0; i < argumentArray.size(); i++)
            {
                DictObj.Dict argumentObj = argumentArray.getDict(i);
                if (argumentObj.hasKey("id"))
                {
                    //reference
                    ret.arguments.add(new Reference(argumentObj.getLong("id", 0), Type.valueOf(argumentObj.getString("type"))));
                }
                else
                {
                    ret.arguments.add(new Value(argumentObj.getDouble("value", 0)));
                }
            }

            int argsSize = ret.arguments.size();
            ret.functions = new ArrayList<>();
            if (obj.hasKey("functions"))
            {
                DictObj.List functions = obj.getList("functions");
                for (int i = 0; i < functions.size(); i++)
                {
                    DictObj.Dict f = functions.getDict(i);
                    ret.functions.add(new Function(f.getString("type"), f.getInt("num", argsSize), f.getInt("pos", argsSize)));
                }

            }
            else if (obj.hasKey("function"))
            {
                //LEGACY
                ret.functions.add(new Function(obj.getString("function"), argsSize, argsSize));
            }
            else
            {
                throw new IllegalArgumentException("Either function or functions is required");
            }

            if (obj.hasKey("debug_name"))
            {
                ret.debugName = obj.getString("debug_name");
            }

            return ret;
        }

        public DictObj.Dict toDict()
        {
            DictObj.Dict obj = new DictObj.Dict();
            DictObj.List funcsObj = new DictObj.List();
            for (Function f : functions)
            {
                DictObj.Dict funcObj = new DictObj.Dict();
                funcObj.put("type", f.name());
                funcObj.put("num", f.numberOfArguments);
                funcObj.put("pos", f.totalPosition);
                funcsObj.add(funcObj);
            }
            obj.put("functions", funcsObj);

            DictObj.List argArray = new DictObj.List();
            for (Argument arg : arguments)
            {
                DictObj.Dict argObj = new DictObj.Dict();
                if (arg instanceof Reference)
                {
                    argObj.put("id", ((Reference)arg).id);
                    argObj.put("type", ((Reference)arg).type.name());
                }
                else
                {
                    argObj.put("value", ((Value)arg).value);
                }
                argArray.add(argObj);
            }
            obj.put("arguments", argArray);
            if (resolvedValue != null)
            {
                obj.put("resolvedValue", resolvedValue);
            }
            if (debugName != null)
            {
                obj.put("debug_name", debugName);
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