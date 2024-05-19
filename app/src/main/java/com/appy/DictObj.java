package com.appy;

import android.os.Parcel;
import android.util.Base64;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DictObj
{
    public static native Object jsontoDictObj(byte[] json);
    public static native byte[] DictObjtojson(Object dict);

    public DictObj copy()
    {
        return deserialize(serialize());
    }

    public static String makeJson(DictObj obj)
    {
        return new String(DictObjtojson(obj), StandardCharsets.UTF_8);
    }

    public String serializeB64()
    {
        return Base64.encodeToString(serialize(), Base64.DEFAULT);
    }

    public byte[] serialize()
    {
        Parcel parcel = Parcel.obtain();
        try
        {
            serialize_inner(parcel, this);
            return parcel.marshall();
        }
        finally
        {
            parcel.recycle();
        }
    }

    private static void serialize_inner(Parcel parcel, Object obj)
    {
        if (obj == null)
        {
            parcel.writeByte((byte)'N');
        }
        else if (obj instanceof DictObj.Dict)
        {
            parcel.writeByte((byte) '{');
            parcel.writeInt(((DictObj.Dict) obj).size());
            for (DictObj.Entry entry : ((DictObj.Dict)obj).entries())
            {
                serialize_inner(parcel, entry.key);
                serialize_inner(parcel, entry.value);
            }
        }
        else if (obj instanceof DictObj.List)
        {
            parcel.writeByte((byte) '[');
            parcel.writeInt(((DictObj.List) obj).size());
            for (Object item : ((DictObj.List)obj).list)
            {
                serialize_inner(parcel, item);
            }
        }
        else if (obj instanceof String)
        {
            parcel.writeByte((byte)'S');
            parcel.writeString((String)obj);
        }
        else if (obj instanceof Boolean)
        {
            parcel.writeByte((byte)'B');
            parcel.writeByte((byte)(((Boolean)obj) ? 1 : 0));
        }
        else if (obj instanceof Long)
        {
            parcel.writeByte((byte)'L');
            parcel.writeLong((Long)obj);
        }
        else if (obj instanceof Double)
        {
            parcel.writeByte((byte)'D');
            parcel.writeDouble((Double)obj);
        }
    }

    public static DictObj deserializeB64(String arr)
    {
        return deserialize(Base64.decode(arr, Base64.DEFAULT));
    }

    public static DictObj deserialize(byte[] arr)
    {
        Parcel parcel = Parcel.obtain();
        try
        {
            parcel.unmarshall(arr, 0, arr.length);
            parcel.setDataPosition(0);
            return (DictObj) deserialize_inner(parcel);
        }
        finally
        {
            parcel.recycle();
        }
        //return (DictObj) jsontoDictObj(json.getBytes(StandardCharsets.UTF_8));
    }

    public static Object deserialize_inner(Parcel parcel)
    {

        char type = (char)parcel.readByte();
        switch (type)
        {
            case 'N':
                return null;
            case '{':
            {
                DictObj.Dict obj = new DictObj.Dict();
                int size = parcel.readInt();
                for (int i = 0; i < size; i++) {
                    obj.put((String) deserialize_inner(parcel), deserialize_inner(parcel));
                }
                return obj;
            }
            case '[':
            {
                DictObj.List obj = new DictObj.List();
                int size = parcel.readInt();
                for (int i = 0; i < size; i++) {
                    obj.add(deserialize_inner(parcel));
                }
                return obj;
            }
            case 'S':
                return parcel.readString();
            case 'B':
                return parcel.readByte() == 1;
            case 'L':
                return parcel.readLong();
            case 'D':
                return parcel.readDouble();
        }

        throw new RuntimeException("unknown type: " + type + "(" + (int)type + ")");
    }

    public static class List extends DictObj
    {
        private final ArrayList<Object> list = new ArrayList<>();

        public List()
        {

        }

        public Object[] array()
        {
            return list.toArray();
        }

        public int size()
        {
            return list.size();
        }

        public Object get(int index)
        {
            return list.get(index);
        }

        private void set(int index, Object val)
        {
            list.set(index, val);
        }

        private void add(Object val)
        {
            list.add(val);
        }

        public DictObj.Dict getDict(int index)
        {
            return (DictObj.Dict)get(index);
        }

        public DictObj.List getList(int index)
        {
            return (DictObj.List)get(index);
        }

        public String getString(int index)
        {
            return (String)get(index);
        }

        public long getLong(int index, long def)
        {
            Long val = (Long)get(index);
            if (val == null)
            {
                return def;
            }
            return val;
        }

        public double getDouble(int index, double def)
        {
            Double val = (Double)get(index);
            if (val == null)
            {
                return def;
            }
            return val;
        }

        public float getFloat(int index, float def)
        {
            return (float)getDouble(index, def);
        }

        public short getShort(int index, short def)
        {
            return (short)getLong(index, def);
        }

        public int getInt(int index, int def)
        {
            return (int)getLong(index, def);
        }

        public boolean getBoolean(int index, boolean def)
        {
            Boolean val = (Boolean)get(index);
            if (val == null)
            {
                return def;
            }
            return val;
        }

        public void set(int index, DictObj val)
        {
            set(index, (Object)val);
        }

        public void set(int index, String val)
        {
            set(index, (Object)val);
        }

        public void set(int index, byte[] val)
        {
            set(index, (Object)(new String(val, StandardCharsets.UTF_8)));
        }

        public void set(int index, long val)
        {
            set(index, Long.valueOf(val));
        }

        public void set(int index, double val)
        {
            set(index, Double.valueOf(val));
        }

        public void set(int index, float val)
        {
            set(index, (double)val);
        }

        public void set(int index, short val)
        {
            set(index, (long)val);
        }

        public void set(int index, int val)
        {
            set(index, (long)val);
        }

        public void set(int index, boolean val)
        {
            set(index, Boolean.valueOf(val));
        }

        public void add(DictObj val)
        {
            add((Object)val);
        }

        public void add(String val)
        {
            add((Object)val);
        }

        public void add(byte[] val)
        {
            add((Object)(new String(val, StandardCharsets.UTF_8)));
        }

        public void add(long val)
        {
            add(Long.valueOf(val));
        }

        public void add(double val)
        {
            add(Double.valueOf(val));
        }

        public void add(float val)
        {
            add((double)val);
        }

        public void add(short val)
        {
            add((long)val);
        }

        public void add(int val)
        {
            add((long)val);
        }

        public void add(boolean val)
        {
            add(Boolean.valueOf(val));
        }

        public Object remove(int index)
        {
            return list.remove(index);
        }

        public static DictObj.List deserialize(byte[] arr)
        {
            return (DictObj.List) DictObj.deserialize(arr);
        }
    }

    public static class Entry
    {
        public String key;
        public Object value;
    }

    public static class Dict extends DictObj {

        private final HashMap<String, Object> items = new HashMap<>();

        public Dict() {

        }

        public DictObj.Entry[] entries() {
            DictObj.Entry[] entries = new DictObj.Entry[items.size()];
            int c = 0;
            for (Map.Entry<String, Object> entry : items.entrySet()) {
                entries[c] = new Entry();
                entries[c].key = entry.getKey();
                entries[c].value = entry.getValue();
                c++;
            }
            return entries;
        }

        public String[] keys()
        {
            String[] keys = new String[items.size()];
            return items.keySet().toArray(keys);
        }

        public Set<String> keyset()
        {
            return items.keySet();
        }

        public int size() {
            return items.size();
        }

        public boolean hasKey(String key) {
            return items.containsKey(key);
        }

        public Object get(String key) {
            return items.get(key);
        }

        private void put(String key, Object val) {
            items.put(key, val);
        }

        public DictObj.Dict getDict(String key) {
            return (Dict) get(key);
        }

        public DictObj.List getList(String key) {
            return (DictObj.List) get(key);
        }

        public String getString(String key) {
            return (String) get(key);
        }

        public long getLong(String key, long def) {
            Long val = (Long) get(key);
            if (val == null) {
                return def;
            }
            return val;
        }

        public double getDouble(String key, double def) {
            Double val = (Double) get(key);
            if (val == null) {
                return def;
            }
            return val;
        }

        public float getFloat(String key, float def) {
            return (float) getDouble(key, def);
        }

        public short getShort(String key, short def) {
            return (short) getLong(key, def);
        }

        public int getInt(String key, int def) {
            return (int) getLong(key, def);
        }

        public boolean getBoolean(String key, boolean def) {
            Boolean val = (Boolean) get(key);
            if (val == null) {
                return def;
            }
            return val;
        }

        public void put(String key, DictObj val) {
            put(key, (Object) val);
        }

        public void put(String key, String val) {
            put(key, (Object) val);
        }

        public void put(String key, long val) {
            put(key, Long.valueOf(val));
        }

        public void put(String key, double val) {
            put(key, Double.valueOf(val));
        }

        public void put(String key, float val) {
            put(key, (double) val);
        }

        public void put(String key, short val) {
            put(key, (long) val);
        }

        public void put(String key, int val) {
            put(key, (long) val);
        }

        public void put(String key, boolean val) {
            put(key, Boolean.valueOf(val));
        }

        public void put(byte[] key, DictObj val) {
            put(new String(key, StandardCharsets.UTF_8), (Object) val);
        }

        public void put(byte[] key, byte[] val) {
            put(new String(key, StandardCharsets.UTF_8), (Object)(new String(val, StandardCharsets.UTF_8)));
        }

        public void put(byte[] key, long val) {
            put(new String(key, StandardCharsets.UTF_8), Long.valueOf(val));
        }

        public void put(byte[] key, double val) {
            put(new String(key, StandardCharsets.UTF_8), Double.valueOf(val));
        }

        public void put(byte[] key, float val) {
            put(new String(key, StandardCharsets.UTF_8), (double) val);
        }

        public void put(byte[] key, short val) {
            put(new String(key, StandardCharsets.UTF_8), (long) val);
        }

        public void put(byte[] key, int val) {
            put(new String(key, StandardCharsets.UTF_8), (long) val);
        }

        public void put(byte[] key, boolean val) {
            put(new String(key, StandardCharsets.UTF_8), Boolean.valueOf(val));
        }

        public Object remove(String key)
        {
            return items.remove(key);
        }

        public static DictObj.Dict deserialize(byte[] arr)
        {
            return (DictObj.Dict) DictObj.deserialize(arr);
        }
    }
}
