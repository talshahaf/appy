package com.happy;

import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Tal on 15/12/2017.
 */

public class Reflection
{
//    /**
//     * Get a compatible constructor to the supplied parameter types.
//     *
//     * @param clazz the class which we want to construct
//     * @param parameterTypes the types required of the constructor
//     *
//     * @return a compatible constructor or null if none exists
//     * @throws InvocationTargetException
//     * @throws IllegalArgumentException
//     * @throws IllegalAccessException
//     */
//
//    public static Object invoke(Method m, Object cls, Object... args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException
//    {
//        Class<?>[] parameterTypes = m.getParameterTypes();
//        for(int i = 0; i < m.getParameterCount(); i++)
//        {
//            if(args[i].getClass().equals(Integer.class))
//            {
//                args[i] = ((long)args[i]);
//            }
//            else
//            {
//                args[i] = parameterTypes[i].cast(args[i]);
//            }
//        }
//        return m.invoke(cls, args);
//    }

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
        return new Object[]{f, type == null ? OBJECT_TYPE : type, Modifier.isStatic(f.getModifiers()) ? 1 : 0};
    }

    public static void printFunc(Class<?> clazz, String method, Class<?>[] parameterTypes)
    {
        String types = "";
        for(Class<?> t : parameterTypes)
        {
            types += ", "+t.getName();
        }
        Log.d("HAPY", clazz.getName() +"."+ method + " " + types);
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

        //printFunc(clazz, method, parameterTypes);

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
                        Class<?> unboxedMethodType = unbox(methodTypes[j]);
                        Class<?> unboxedParameterType = unbox(parameterTypes[j]);
                        if (!unboxedMethodType.isAssignableFrom(unboxedParameterType)) {
                            Integer methodValue = groups.get(unboxedMethodType);
                            Integer parameterValue = groups.get(unboxedParameterType);
                            if (methodValue != null && parameterValue != null && methodValue / 10 == parameterValue / 10 && (python_types || methodValue > parameterValue))
                            {
                                // conversion allowed
                                continue;
                            }
                            Log.d("HAPY", "method fails because "+methodTypes[j].getName() + " != "+parameterTypes[j].getName());
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
                Integer real_t = enumTypes.get(unbox(argTypes[i]));
                args[i] = new int[]{ t == null ? OBJECT_TYPE : t, real_t == null ? OBJECT_TYPE : real_t};
            }

            Integer t = enumTypes.get(result.getReturnType());
            Integer real_t = enumTypes.get(unbox(result.getReturnType()));
            args[args.length - 1] = new int[]{t == null ? OBJECT_TYPE : t, real_t == null ? OBJECT_TYPE : real_t};

            return new Object[]{result.get(), result.isStatic() ? 1 : 0, args};
        }

        Log.d("HAPY", "no such func");
        return null;
    }

    /**
     * <p> Checks if a primitive type is assignable with a boxed type.</p>
     *
     * @param primitive a primitive class type
     *
     * @return true if primitive and boxed are assignment compatible
     */
    private static Class<?> unbox(Class<?> primitive)
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
}
