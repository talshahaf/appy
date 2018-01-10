package com.happy;

import android.util.Log;

public class Test
{

    public static Long value = 23L;
    public Long ins_value = 24L;
    public Test2 test_value = new Test2();
    public static Long null_test = null;

    public static Long value()
    {
        return 85L;
    }

    public static Long null_test()
    {
        return 87L;
    }

    public class Test2
    {
        public Long ins_value = 11L;
    }



    public Test()
    {

    }

    public Test(boolean z, byte b, char c,      short s, int i,     long j, float f, double d,
                Boolean Z, Byte B, Character C, Short S, Integer I, Long J, Float F, Double D,
                Object O)
    {
        //Log.d("HAPY", "const got: "+z+" "+b+" "+c+" "+s+" "+i+" "+j+" "+f+" "+d+" | "+Z+" "+B+" "+C+" "+S+" "+I+" "+J+" "+F+" "+D+" | "+O);
    }

    public static long test(long i)
    {
        Log.d("HAPY", "test: "+i);
        return i * 2;
    }
    public long ins_test(long i)
    {
        Log.d("HAPY", "ins_test: "+i);
        return i * 7;
    }

    public static long test_void()
    {
        Log.d("HAPY", "test_void");
        return 48;
    }

    public static void void_test(long l)
    {
        Log.d("HAPY", "test_void: "+l);
    }

    public static void void_void()
    {
        Log.d("HAPY", "void_void");
    }

    public static Object all(boolean z, byte b, char c,      short s, int i,     long j, float f, double d,
                             Boolean Z, Byte B, Character C, Short S, Integer I, Long J, Float F, Double D,
                             Object O)
    {
        Log.d("HAPY", "all got: "+z+" "+b+" "+c+" "+s+" "+i+" "+j+" "+f+" "+d+" | "+Z+" "+B+" "+C+" "+S+" "+I+" "+J+" "+F+" "+D+" | "+O);
        return O;
    }

    public static Short primitive(Short x)
    {
        return x != null ? x : -1;
    }

    public static void test_work(int times)
    {
        String text = "";
        for(int i = 0; i < 256; i++)
        {
            text += "a";
        }
        for(int i = 0; i < times; i++)
        {
            text = text.toLowerCase();
            text = text.toUpperCase();
        }
    }

    public static int[] test_int_array(int len)
    {
        return new int[len];
    }

    public static Integer[] test_integer_array(int len)
    {
        return new Integer[len];
    }

    public static Object[] test_object_array(int len)
    {
        return new Object[len];
    }

    public static String test_string(String s)
    {
        return "=" + s + "=";
    }

    public static String test_unicode(String s)
    {
        Log.d("HAPY", ""+s.length());
        return "בדיקה";
    }

    public static Object test_callback(TestInterface iface, Object obj) throws Throwable
    {
        Log.d("HAPY", "test_callback!");
        return iface.action(obj);
    }

    public static class Inner
    {

    }
}