package com.appy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashMap;

/**
 * Created by Tal on 15/12/2017.
 */

public class Reflection
{

    private static HashMap<Class<?>, Integer> groups;
    private static HashMap<Class<?>, Integer> enumTypes;
    private static final int OBJECT_TYPE = -1;
    static
    {
        groups = new HashMap<>();
        groups.put(java.lang.Boolean.TYPE, 0);
        groups.put(java.lang.Byte.TYPE, 10);
        groups.put(java.lang.Character.TYPE, 10);
        groups.put(java.lang.Short.TYPE, 11);
        groups.put(java.lang.Integer.TYPE, 12);
        groups.put(java.lang.Long.TYPE, 13);
        groups.put(java.lang.Float.TYPE, 20);
        groups.put(java.lang.Double.TYPE, 21);

        enumTypes = new HashMap<>();
        enumTypes.put(java.lang.Boolean.TYPE, 0);
        enumTypes.put(java.lang.Byte.TYPE, 1);
        enumTypes.put(java.lang.Character.TYPE, 2);
        enumTypes.put(java.lang.Short.TYPE, 3);
        enumTypes.put(java.lang.Integer.TYPE, 4);
        enumTypes.put(java.lang.Long.TYPE, 5);
        enumTypes.put(java.lang.Float.TYPE, 6);
        enumTypes.put(java.lang.Double.TYPE, 7);
        enumTypes.put(java.lang.Void.TYPE, 8);
        enumTypes.put(null, 9); //constructors
    }

    public static Object[] getField(Class<?> clazz, String field) throws NoSuchFieldException {
        Field f = clazz.getField(field);
        Integer type = enumTypes.get(f.getType());
        boolean isStatic = Modifier.isStatic(f.getModifiers());
        if(isStatic)
        {
            try
            {
                f.get(null);
            }
            catch (IllegalAccessException e)
            {

            }
        }
        return new Object[]{f, new int[]{type == null ? OBJECT_TYPE : type, unboxClassToEnum(f.getType())}, isStatic ? 1 : 0};
    }

    public static void printFunc(Class<?> clazz, String method, Class<?>[] parameterTypes)
    {
        String types = "";
        for(Class<?> t : parameterTypes)
        {
            types += ", "+t.getName();
        }
        Log.d("APPY", clazz.getName() +"."+ method + " " + types);
    }

    public static final boolean python_types = true;

    public interface Callable
    {
        String getName();
        Class<?>[] getParameterTypes();
        Class<?> getReturnType();
        boolean isStatic();
        Object get();
    }

    public static class CallableMethod implements Callable
    {
        private Method m;
        CallableMethod(Method m)
        {
            this.m = m;
        }

        @Override
        public String getName()
        {
            return m.getName();
        }

        @Override
        public Class<?>[] getParameterTypes()
        {
            return m.getParameterTypes();
        }

        @Override
        public Class<?> getReturnType()
        {
            return m.getReturnType();
        }

        @Override
        public boolean isStatic()
        {
            return Modifier.isStatic(m.getModifiers());
        }

        @Override
        public Object get()
        {
            return m;
        }
    }

    public static class CallableConstructor implements Callable
    {
        private Constructor<?> m;
        CallableConstructor(Constructor<?> m)
        {
            this.m = m;
        }

        @Override
        public String getName()
        {
            return m.getName();
        }

        @Override
        public Class<?>[] getParameterTypes()
        {
            return m.getParameterTypes();
        }

        @Override
        public Class<?> getReturnType()
        {
            return null;
        }

        @Override
        public boolean isStatic()
        {
            return true; //convention
        }

        @Override
        public Object get()
        {
            return m;
        }
    }

    public static Object[] getCompatibleMethod(Class<?> clazz, String method, Class<?>[] parameterTypes)
    {
        Callable result = null;
        try {
            if(method == null)
            {
                result = new CallableConstructor(clazz.getConstructor(parameterTypes));
            }
            else
            {
                result = new CallableMethod(clazz.getMethod(method, parameterTypes));
            }
        } catch (NoSuchMethodException e) {

        }

        // printFunc(clazz, method, parameterTypes);

        if (result == null) {
            Callable[] methods;
            if(method == null)
            {
                Constructor<?>[] _methods = clazz.getConstructors();
                methods = new CallableConstructor[_methods.length];
                for(int i = 0; i < methods.length; i++)
                {
                    methods[i] = new CallableConstructor(_methods[i]);
                }
            }
            else
            {
                Method[] _methods = clazz.getMethods();
                methods = new CallableMethod[_methods.length];
                int c = 0;
                for(int i = 0; i < methods.length; i++)
                {
                    if(_methods[i].getName().equals(method))
                    {
                        methods[c] = new CallableMethod(_methods[i]);
                        c++;
                    }
                }
                Callable[] copy = new CallableMethod[c];
                System.arraycopy(methods, 0, copy, 0, c);
                methods = copy;
            }

            for (Callable m : methods) {
                if (m.getParameterTypes().length == (parameterTypes != null ? parameterTypes.length : 0)) {
                    // If we have the same number of parameters there is a
                    // shot that we have a compatible
                    // constructor
                    Class<?>[] methodTypes = m.getParameterTypes();
                    boolean isCompatible = true;
                    for (int j = 0; j < (parameterTypes != null ? parameterTypes.length : 0); j++) {
                        if(methodTypes[j].isAssignableFrom(parameterTypes[j]))
                        {
                            // easy part
                            continue;
                        }
                        if(python_types && !methodTypes[j].isPrimitive() && parameterTypes[j] == Object.class)
                        {
                            // because Nones
                            continue;
                        }
                        Class<?> unboxedMethodType = unboxClass(methodTypes[j]);
                        Class<?> unboxedParameterType = unboxClass(parameterTypes[j]);
                        if (!unboxedMethodType.isAssignableFrom(unboxedParameterType)) {
                            Integer methodValue = groups.get(unboxedMethodType);
                            Integer parameterValue = groups.get(unboxedParameterType);
                            if (methodValue != null && parameterValue != null && methodValue / 10 == parameterValue / 10 && (python_types || methodValue > parameterValue))
                            {
                                // conversion allowed
                                continue;
                            }
                            Log.d("APPY", "method " + m.getName() + " fails because "+methodTypes[j].getName() + " != "+parameterTypes[j].getName());
                            isCompatible = false;
                            break;
                        }
                    }
                    if (isCompatible) {
                        result = m;
                        break;
                    }
                }
            }
        }
        if(result != null)
        {
            //printFunc(clazz, method, result.getParameterTypes());
            Class<?>[] argTypes = result.getParameterTypes();
            int[][] args = new int[argTypes.length + 1][];
            for(int i = 0; i < argTypes.length; i++)
            {
                Integer t = enumTypes.get(argTypes[i]);
                args[i] = new int[]{ t == null ? OBJECT_TYPE : t, unboxClassToEnum(argTypes[i])};
            }

            Integer t = enumTypes.get(result.getReturnType());
            args[args.length - 1] = new int[]{t == null ? OBJECT_TYPE : t, unboxClassToEnum(result.getReturnType())};

            return new Object[]{result.get(), result.isStatic() ? 1 : 0, args};
        }

        Log.d("APPY", "no such func "+method);
        return null;
    }

    /**
     * <p> Checks if a primitive type is assignable with a boxed type.</p>
     *
     * @param primitive a primitive class type
     *
     * @return true if primitive and boxed are assignment compatible
     */
    public static Class<?> unboxClass(Class<?> primitive)
    {
        if(primitive == null)
        {
            return null;
        }
        if(primitive.isPrimitive())
        {
            return primitive;
        }
        if (primitive.equals(java.lang.Boolean.class)) {
            return java.lang.Boolean.TYPE;
        }
        if (primitive.equals(java.lang.Byte.class)) {
            return java.lang.Byte.TYPE;
        }
        if (primitive.equals(java.lang.Character.class)) {
            return java.lang.Character.TYPE;
        }
        if (primitive.equals(java.lang.Double.class)) {
            return java.lang.Double.TYPE;
        }
        if (primitive.equals(java.lang.Float.class)) {
            return java.lang.Float.TYPE;
        }
        if (primitive.equals(java.lang.Integer.class)) {
            return java.lang.Integer.TYPE;
        }
        if (primitive.equals(java.lang.Long.class)) {
            return java.lang.Long.TYPE;
        }
        if (primitive.equals(java.lang.Short.class)) {
            return java.lang.Short.TYPE;
        }
        return primitive;
    }

    public static int unboxClassToEnum(Class<?> primitive)
    {
        Class<?> unboxed = unboxClass(primitive);
        Integer t = enumTypes.get(unboxed);
        return t == null ? OBJECT_TYPE : t;
    }

    public static Object[] inspect(Class<?> clazz)
    {
        int unboxedEnumType = unboxClassToEnum(clazz);
        int isArray = clazz.isArray() ? 1 : 0;
        Class<?> component = clazz.getComponentType();
        Integer t = enumTypes.get(component);
        int componentEnumType = t == null ? OBJECT_TYPE : t;
        int unboxedComponentEnumType = unboxClassToEnum(component);
        return new Object[] {clazz.getCanonicalName(), unboxedEnumType, isArray, component, componentEnumType, unboxedComponentEnumType};
    }

    public static byte[] stringToBytes(String s) throws UnsupportedEncodingException
    {
        return s.getBytes("UTF-8");
    }

    public static String bytesToString(byte[] bytes) throws UnsupportedEncodingException
    {
        return new String(bytes, "UTF-8");
    }

    public static class ProxyListener implements java.lang.reflect.InvocationHandler {
        private long id;
        public ProxyListener(long id)
        {
            this.id = id;
        }
        public Object invoke(Object proxy, Method m, Object[] args) throws Throwable
        {
            return Widget.pythonCall(id, m.getDeclaringClass(), m.getName(), args);
        }
    }

    public static Object createInterface(long id, Class<?>[] classes)
    {
        return Proxy.newProxyInstance(classes[0].getClassLoader(), classes, new ProxyListener(id));
    }

    public static String formatException(Throwable t)
    {
        return t.getClass().getName() + ":\n" + t.getMessage() + "\n" + Widget.getStacktrace(t);
    }

}

