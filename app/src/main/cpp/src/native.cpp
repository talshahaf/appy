#include "Python.h"
#include <stdio.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include <jni.h>
#include <android/log.h>
#include <type_traits>
#include <string>
#include <chrono>
#include <functional>
#include <deque>
#include "native.h"

#define LOG(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG, "HAPY", fmt, ##__VA_ARGS__)
#define PYTHON_CALL

JavaVM * vm = NULL;
jclass class_class = NULL;
jclass reflection_class = NULL;
jmethodID compatibleMethod = NULL;
jmethodID getField = NULL;

//primitives
jclass boolean_class = NULL;
jclass byte_class = NULL;
jclass character_class = NULL;
jclass short_class = NULL;
jclass integer_class = NULL;
jclass long_class = NULL;
jclass float_class = NULL;
jclass double_class = NULL;

jmethodID booleanCtor = NULL;
jmethodID byteCtor = NULL;
jmethodID charCtor = NULL;
jmethodID shortCtor = NULL;
jmethodID intCtor = NULL;
jmethodID longCtor = NULL;
jmethodID floatCtor = NULL;
jmethodID doubleCtor = NULL;

jmethodID booleanValueMethod = NULL;
jmethodID byteValueMethod = NULL;
jmethodID charValueMethod = NULL;
jmethodID shortValueMethod = NULL;
jmethodID intValueMethod = NULL;
jmethodID longValueMethod = NULL;
jmethodID floatValueMethod = NULL;
jmethodID doubleValueMethod = NULL;

enum ReturnType
{
    OBJECT = -1,
    BOOLEAN = 0,
    BYTE = 1,
    CHARACTER = 2,
    SHORT = 3,
    INTEGER = 4,
    LONG = 5,
    FLOAT = 6,
    DOUBLE = 7,
    VOID = 8,
    CONST = 9,
};

enum Ops
{
    NOOP = 0,
    CALL_METHOD = 1,
    CALL_STATIC_METHOD = 2,
    GET_FIELD = 3,
    GET_STATIC_FIELD = 4,
    SET_FIELD = 5,
    SET_STATIC_FIELD = 6,
};

struct Parameter
{
    int type;
    int real_type;
};

class jni_exception : public std::exception
{
public:
    jni_exception(const char * str) : str(str)
    {

    }
    const char * what() noexcept
    {
        return str.c_str();
    }
private:
    std::string str;
};

static void check_java_exc(JNIEnv * env, int line);
#define CHECK_JAVA_EXC(env) check_java_exc(env, __LINE__)

static void find_types(JNIEnv * env);

static std::string get_string(JNIEnv * env, jstring str)
{
    const char * cstr = env->GetStringUTFChars(str, NULL);
    if(cstr == NULL)
    {
        throw jni_exception("failed to get string utf chars");
    }
    const jint length = env->GetStringLength(str);
    std::string path(cstr, length);
    env->ReleaseStringUTFChars(str, cstr);
    return path;
}

static int get_integer(JNIEnv * env, jobject integer)
{
    find_types(env);
    int i = env->CallIntMethod(integer, intValueMethod);
    CHECK_JAVA_EXC(env);
    return i;
}

JNIEnv * get_env()
{
    JNIEnv * env = NULL;
    if(vm->AttachCurrentThread(&env, NULL) != JNI_OK)
    {
        throw jni_exception("failed to attach current thread");
    }
    return env;
}


class java_exception : public std::exception
{
public:
    java_exception(JNIEnv * env, jthrowable exc, int line) : env(env), exception(exc)
    {
        try
        {
            jclass clazz = env->GetObjectClass(exception);
            jmethodID getMessage = env->GetMethodID(clazz,
                                                    "getMessage",
                                                    "()Ljava/lang/String;");
            if(env->ExceptionCheck())
            {
                env->ExceptionClear();
                throw jni_exception("exception in exception");
            }
            jstring msg = (jstring)env->CallObjectMethod(exc, getMessage);
            if(env->ExceptionCheck())
            {
                env->ExceptionClear();
                throw jni_exception("exception in exception");
            }

            char buf[16] = {};
            snprintf(buf, sizeof(buf) - 1, "%d", line);
            message = get_string(env, msg) + " in line " + buf;
        }
        catch(...)
        {
            message = "exception while getting info for exception";
        }
    }
    virtual ~java_exception()
    {
        if(exception != NULL)
        {
            env->DeleteGlobalRef(exception);
        }
    }
    const char * what() noexcept
    {
        return message.c_str();
    }

public:
    JNIEnv * env;
    jthrowable exception;
    std::string message;
};

class scope_guards : public std::deque<std::function<void()>> {
public:
    template<class Callable>
    scope_guards& operator += (Callable && undo_func) {
        emplace_front(std::forward<Callable>(undo_func));
        return *this;
    }

    ~scope_guards() {
        for(auto &f : *this) f(); // must not throw
    }

    void dismiss() noexcept {
        clear();
    }

    scope_guards() = default;
    scope_guards(const scope_guards&) = delete;
    void operator = (const scope_guards&) = delete;
};

void check_java_exc(JNIEnv * env, int line)
{
    if(env->ExceptionCheck() == JNI_TRUE)
    {
        jthrowable local_exc = (jthrowable)env->ExceptionOccurred();
        env->ExceptionClear();
        jthrowable exc = (jthrowable)env->NewGlobalRef(local_exc);
        env->DeleteLocalRef(local_exc);
        if(exc == NULL)
        {
            throw jni_exception("failed to global ref exception");
        }
        throw java_exception(env, exc, line);
    }
}

#define CASE(TYPE, Type, union_name, refcode) \
case TYPE: \
{ \
   switch(op) \
   { \
       case CALL_METHOD: \
       { \
           ret.union_name = env->Call##Type##MethodA(self, (jmethodID)id, values); \
           refcode; \
           break; \
       } \
       case CALL_STATIC_METHOD: \
       { \
           ret.union_name = env->CallStatic##Type##MethodA((jclass)self, (jmethodID)id, values); \
           refcode; \
           break; \
       } \
      case GET_FIELD: \
      { \
          ret.union_name = env->Get##Type##Field(self, (jfieldID)id); \
          refcode; \
          break; \
      } \
      case GET_STATIC_FIELD: \
      { \
          ret.union_name = env->GetStatic##Type##Field((jclass)self, (jfieldID)id); \
          refcode; \
          break; \
      } \
      case SET_FIELD: \
      { \
          env->Set##Type##Field(self, (jfieldID)id, values[0].union_name); \
          break; \
      } \
      case SET_STATIC_FIELD: \
      { \
          env->SetStatic##Type##Field((jclass)self, (jfieldID)id, values[0].union_name); \
          break; \
      } \
   } \
   break; \
}

static jobject make_global_ref(JNIEnv * env, jobject local)
{
    if(local == NULL)
    {
        throw jni_exception("cannot make global reference of null");
    }
    jobject glob;
    glob = env->NewGlobalRef(local);
    env->DeleteLocalRef(local);
    if(glob == NULL)
    {
        throw jni_exception("failed to create global ref");
    }
    return glob;
}

jvalue act_raw(JNIEnv * env, jobject self, void * id, jvalue * values, int returnType, int op)
{
    jvalue ret;

    switch(returnType)
    {
    CASE(BOOLEAN, Boolean, z,)
    CASE(BYTE, Byte, b,)
    CASE(CHARACTER, Char, c,)
    CASE(SHORT, Short, s,)
    CASE(INTEGER, Int, i,)
    CASE(LONG, Long, j,)
    CASE(FLOAT, Float, f,)
    CASE(DOUBLE, Double, d,)
    CASE(OBJECT, Object, l, ret.l = ret.l == NULL ? NULL : make_global_ref(env, ret.l))

    case VOID:
    {
        switch(op)
        {
            case CALL_METHOD:
            {
                env->CallVoidMethodA(self, (jmethodID)id, values);
                break;
            }
            case CALL_STATIC_METHOD:
            {
                env->CallStaticVoidMethodA((jclass)self, (jmethodID)id, values);
                break;
            }
            default:
            {
                throw jni_exception("only method calls works with void");
            }
        }
        break;
    }

    case CONST:
    {
        if(op != CALL_STATIC_METHOD)
        {
            throw jni_exception("constructors must be called with CALL_STATIC_METHOD op");
        }
        ret.l = env->NewObjectA((jclass)self, (jmethodID)id, values);
        if(ret.l == NULL)
        {
            throw jni_exception("failed to create new object");
        }
        ret.l = make_global_ref(env, ret.l);
        break;
    }

    default:
    {
        throw jni_exception("unknown type");
    }

    }
    CHECK_JAVA_EXC(env);
    return ret;
}

static void find_types(JNIEnv * env)
{
    if(class_class == NULL)
    {
        jclass local_class_class = env->FindClass("java/lang/Class");
        CHECK_JAVA_EXC(env);
        class_class = (jclass)make_global_ref(env, (jobject)local_class_class);
    }
    if(reflection_class == NULL)
    {
        jclass local_reflection = env->FindClass("com/happy/Reflection");
        CHECK_JAVA_EXC(env);
        reflection_class = (jclass)make_global_ref(env, (jobject)local_reflection);
    }
    //-------primitives-----------------
    if(boolean_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Boolean");
        CHECK_JAVA_EXC(env);
        boolean_class = (jclass)make_global_ref(env, (jobject)local);
    }
    if(byte_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Byte");
        CHECK_JAVA_EXC(env);
        byte_class = (jclass)make_global_ref(env, (jobject)local);
    }
    if(character_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Character");
        CHECK_JAVA_EXC(env);
        character_class = (jclass)make_global_ref(env, (jobject)local);
    }
    if(short_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Short");
        CHECK_JAVA_EXC(env);
        short_class = (jclass)make_global_ref(env, (jobject)local);
    }
    if(integer_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Integer");
        CHECK_JAVA_EXC(env);
        integer_class = (jclass)make_global_ref(env, (jobject)local);
    }
    if(long_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Long");
        CHECK_JAVA_EXC(env);
        long_class = (jclass)make_global_ref(env, (jobject)local);
    }
    if(float_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Float");
        CHECK_JAVA_EXC(env);
        float_class = (jclass)make_global_ref(env, (jobject)local);
    }
    if(double_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Double");
        CHECK_JAVA_EXC(env);
        double_class = (jclass)make_global_ref(env, (jobject)local);
    }
    //----------------------------------
    if(compatibleMethod == NULL)
    {
        compatibleMethod = env->GetStaticMethodID(reflection_class, "getCompatibleMethod", "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)[Ljava/lang/Object;");
        CHECK_JAVA_EXC(env);
    }
    if(getField == NULL)
    {
        getField = env->GetStaticMethodID(reflection_class, "getField", "(Ljava/lang/Class;Ljava/lang/String;)[Ljava/lang/Object;");
        CHECK_JAVA_EXC(env);
    }
    //---------primitive-accessors--------------------------
    if(booleanCtor == NULL)
    {
        booleanCtor = env->GetMethodID(boolean_class, "<init>", "(Z)V");
        CHECK_JAVA_EXC(env);
    }
    if(byteCtor == NULL)
    {
        byteCtor = env->GetMethodID(byte_class, "<init>", "(B)V");
        CHECK_JAVA_EXC(env);
    }
    if(charCtor == NULL)
    {
        charCtor = env->GetMethodID(character_class, "<init>", "(C)V");
        CHECK_JAVA_EXC(env);
    }
    if(shortCtor == NULL)
    {
        shortCtor = env->GetMethodID(short_class, "<init>", "(S)V");
        CHECK_JAVA_EXC(env);
    }
    if(intCtor == NULL)
    {
        intCtor = env->GetMethodID(integer_class, "<init>", "(I)V");
        CHECK_JAVA_EXC(env);
    }
    if(longCtor == NULL)
    {
        longCtor = env->GetMethodID(long_class, "<init>", "(J)V");
        CHECK_JAVA_EXC(env);
    }
    if(floatCtor == NULL)
    {
        floatCtor = env->GetMethodID(float_class, "<init>", "(F)V");
        CHECK_JAVA_EXC(env);
    }
    if(doubleCtor == NULL)
    {
        doubleCtor = env->GetMethodID(double_class, "<init>", "(D)V");
        CHECK_JAVA_EXC(env);
    }
    if(booleanValueMethod == NULL)
    {
        booleanValueMethod = env->GetMethodID(boolean_class, "booleanValue", "()Z");
        CHECK_JAVA_EXC(env);
    }
    if(byteValueMethod == NULL)
    {
        byteValueMethod = env->GetMethodID(byte_class, "byteValue", "()B");
        CHECK_JAVA_EXC(env);
    }
    if(charValueMethod == NULL)
    {
        charValueMethod = env->GetMethodID(character_class, "charValue", "()C");
        CHECK_JAVA_EXC(env);
    }
    if(shortValueMethod == NULL)
    {
        shortValueMethod = env->GetMethodID(short_class, "shortValue", "()S");
        CHECK_JAVA_EXC(env);
    }
    if(intValueMethod == NULL)
    {
        intValueMethod = env->GetMethodID(integer_class, "intValue", "()I");
        CHECK_JAVA_EXC(env);
    }
    if(longValueMethod == NULL)
    {
        longValueMethod = env->GetMethodID(long_class, "longValue", "()J");
        CHECK_JAVA_EXC(env);
    }
    if(floatValueMethod == NULL)
    {
        floatValueMethod = env->GetMethodID(float_class, "floatValue", "()F");
        CHECK_JAVA_EXC(env);
    }
    if(doubleValueMethod == NULL)
    {
        doubleValueMethod = env->GetMethodID(double_class, "doubleValue", "()D");
        CHECK_JAVA_EXC(env);
    }
    //------------------------------------------------------
}

jfieldID get_field_raw(JNIEnv * env, jclass clazz, const char * field, Parameter * out_type, int * out_static)
{
    scope_guards onexit;
    env->PushLocalFrame(15);
    CHECK_JAVA_EXC(env);
    onexit += [env]{ env->PopLocalFrame(NULL); };

    find_types(env);

    jstring field_str = env->NewStringUTF(field);
    CHECK_JAVA_EXC(env);
    jobjectArray result = (jobjectArray)env->CallStaticObjectMethod(reflection_class, getField, clazz, field_str);
    CHECK_JAVA_EXC(env);

    jfieldID fieldID = NULL;
    if(result != NULL)
    {
        fieldID = env->FromReflectedField(env->GetObjectArrayElement(result, 0));

        jintArray parameter = (jintArray)env->GetObjectArrayElement(result, 1);
        CHECK_JAVA_EXC(env);
        jint * paramarr = env->GetIntArrayElements(parameter, NULL);
        if(paramarr == NULL)
        {
            throw jni_exception("failed to GetIntArrayElements after finding compatible method");
        }
        out_type->type = paramarr[0];
        out_type->real_type = paramarr[1];
        env->ReleaseIntArrayElements(parameter, paramarr, JNI_ABORT);

        jobject staticInteger = env->GetObjectArrayElement(result, 2);
        CHECK_JAVA_EXC(env);

        *out_static = get_integer(env, staticInteger);
    }
    return fieldID;
}

jmethodID get_method_raw(JNIEnv * env, jclass clazz, const char * method, int n, jobjectArray type_arr, Parameter * outTypes, int * outStatic)
{
    scope_guards onexit;
    env->PushLocalFrame(15);
    CHECK_JAVA_EXC(env);
    onexit += [env]{ env->PopLocalFrame(NULL); };

    find_types(env);

    jobjectArray result = NULL;
    if(method == NULL || strlen(method) == 0)
    {
        result = (jobjectArray)env->CallStaticObjectMethod(reflection_class, compatibleMethod, clazz, 0, type_arr);
    }
    else
    {
        jstring method_str = env->NewStringUTF(method);
        CHECK_JAVA_EXC(env);
        result = (jobjectArray)env->CallStaticObjectMethod(reflection_class, compatibleMethod, clazz, method_str, type_arr);
    }
    CHECK_JAVA_EXC(env);

    jmethodID methodID = NULL;
    if(result != NULL)
    {
        methodID = env->FromReflectedMethod(env->GetObjectArrayElement(result, 0));
        jobject staticInteger = env->GetObjectArrayElement(result, 1);
        CHECK_JAVA_EXC(env);

        *outStatic = get_integer(env, staticInteger);

        jobjectArray realTypes = (jobjectArray)env->GetObjectArrayElement(result, 2);
        CHECK_JAVA_EXC(env);

        for(size_t i = 0; i < n + 1; i++)
        {
            jintArray parameter = (jintArray)env->GetObjectArrayElement(realTypes, i);
            CHECK_JAVA_EXC(env);
            jint * paramarr = env->GetIntArrayElements(parameter, NULL);
            if(paramarr == NULL)
            {
                throw jni_exception("failed to GetIntArrayElements after finding compatible method");
            }
            outTypes[i].type = paramarr[0];
            outTypes[i].real_type = paramarr[1];

            env->ReleaseIntArrayElements(parameter, paramarr, JNI_ABORT);
        }
    }
    return methodID;
}

static jvalue unbox(JNIEnv * env, jobject obj, int value_type)
{
    jvalue ret;
    switch(value_type)
    {
    case BOOLEAN:
    {
        ret.z = env->CallBooleanMethod(obj, booleanValueMethod);
        break;
    }
    case BYTE:
    {
        ret.b = env->CallByteMethod(obj, byteValueMethod);
        break;
    }
    case CHARACTER:
    {
        ret.c = env->CallCharMethod(obj, charValueMethod);
        break;
    }
    case SHORT:
    {
        ret.s = env->CallShortMethod(obj, shortValueMethod);
        break;
    }
    case INTEGER:
    {
        ret.i = env->CallIntMethod(obj, intValueMethod);
        break;
    }
    case LONG:
    {
        ret.j = env->CallLongMethod(obj, longValueMethod);
        break;
    }
    case FLOAT:
    {
        ret.f = env->CallFloatMethod(obj, floatValueMethod);
        break;
    }
    case DOUBLE:
    {
        ret.d = env->CallDoubleMethod(obj, doubleValueMethod);
        break;
    }
    default:
    {
        throw jni_exception("non primitive type");
    }
    }
    return ret;
}

static jobject box_raw(JNIEnv * env, jvalue value, int value_type)
{
    jobject obj = NULL;
    switch(value_type)
    {
    case BOOLEAN:
    {
        obj = env->NewObject(boolean_class, booleanCtor, value.z);
        break;
    }
    case BYTE:
    {
        obj = env->NewObject(byte_class, byteCtor, value.b);
        break;
    }
    case CHARACTER:
    {
        obj = env->NewObject(character_class, charCtor, value.c);
        break;
    }
    case SHORT:
    {
        obj = env->NewObject(short_class, shortCtor, value.s);
        break;
    }
    case INTEGER:
    {
        obj = env->NewObject(integer_class, intCtor, value.i);
        break;
    }
    case LONG:
    {
        obj = env->NewObject(long_class, longCtor, value.j);
        break;
    }
    case FLOAT:
    {
        obj = env->NewObject(float_class, floatCtor, value.f);
        break;
    }
    case DOUBLE:
    {
        obj = env->NewObject(double_class, doubleCtor, value.d);
        break;
    }
    default:
    {
        throw jni_exception("non primitive type");
    }
    }

    if(obj == NULL)
    {
        throw jni_exception("failed to create new object");
    }
    return make_global_ref(env, obj);
}

static PyObject * unpack_value(jvalue v, int t)
{
    switch(t)
    {
    case BOOLEAN:
    {
        return PyBool_FromLong((long)(jbyte)v.z);
    }
    case BYTE:
    {
        return PyLong_FromLong((long)v.b);
    }
    case CHARACTER:
    {
        return PyLong_FromLong((long)(jshort)v.c);
    }
    case SHORT:
    {
        return PyLong_FromLong((long)v.s);
    }
    case INTEGER:
    {
        return PyLong_FromLong((long)v.i);
    }
    case LONG:
    {
        return PyLong_FromLongLong((long long)v.l);
    }
    case FLOAT:
    {
        return PyFloat_FromDouble((double)v.f);
    }
    case DOUBLE:
    {
        return PyFloat_FromDouble(v.d);
    }
    case CONST:
    case OBJECT:
    {
        return PyLong_FromUnsignedLong((unsigned long)v.l);
    }
    case VOID:
    {
        Py_RETURN_NONE;
    }
    }

    PyErr_SetString(PyExc_ValueError, "unknown type");
    return NULL;
}

static PyObject * act(PyObject *self, PyObject *args)
{
    try
    {
        static_assert(sizeof(jvalue) == sizeof(unsigned long long));

        scope_guards onexit;

        unsigned long self = 0;
        unsigned long method = 0;
        PyObject * values = NULL;
        int return_type = 0;
        int unboxed_return_type = 0;
        int op = 0;

        if (!PyArg_ParseTuple(args, "kkOiii", &self, &method, &values, &return_type, &unboxed_return_type, &op)) {
            return NULL;
        }

        jvalue * jvalues = NULL;

        if(values != Py_None)
        {
            if(!PyTuple_Check(values))
            {
                PyErr_SetString(PyExc_ValueError, "values must be a tuple");
                return NULL;
            }

            Py_ssize_t value_num = PyTuple_Size(values);
            if(value_num > 0)
            {
                jvalues = new jvalue[value_num];
                onexit += [&jvalues]{ if(jvalues != NULL) { delete[] jvalues; jvalues = NULL; } };

                for(Py_ssize_t i = 0; i < value_num; i++)
                {
                    PyObject * item = PyTuple_GetItem(values, i);
                    if(!PyLong_Check(item))
                    {
                        PyErr_SetString(PyExc_ValueError, "value must be long");
                        return NULL;
                    }

                    unsigned long long lng = PyLong_AsUnsignedLongLong(item);
                    memcpy(&jvalues[i], &lng, sizeof(lng));
                }
            }
        }
        JNIEnv * env = get_env();
        jvalue ret = act_raw(env, (jobject)self, (void *)method, jvalues, return_type, op);

        if(op == SET_FIELD || op == SET_STATIC_FIELD)
        {
            return_type = VOID;
        }

        if(return_type == OBJECT)
        {
            if(ret.l == NULL)
            {
                Py_RETURN_NONE; //TODO think about this
            }
            if(unboxed_return_type != OBJECT)
            {
                ret = unbox(env, ret.l, unboxed_return_type);
            }
        }
        PyObject * ret_obj = unpack_value(ret, return_type);
        return ret_obj;
    }
    catch(java_exception & e)
    {
        LOG("got java exception in call_static");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    catch(jni_exception & e)
    {
        LOG("got jni exception in call_static");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    return NULL;
}

static PyObject * get_method(PyObject *self, PyObject *args)
{
    try
    {
        unsigned long clazz = 0;
        const char * method = NULL;
        PyObject * types = NULL;

        if (!PyArg_ParseTuple(args, "ksO", &clazz, &method, &types)) {
            return NULL;
        }

        if(!PyTuple_Check(types))
        {
            PyErr_SetString(PyExc_ValueError, "types must be a tuple");
            return NULL;
        }

        Py_ssize_t type_num = PyTuple_Size(types);

        JNIEnv * env = get_env();
        scope_guards onexit;
        env->PushLocalFrame(15);
        CHECK_JAVA_EXC(env);
        onexit += [env]{ env->PopLocalFrame(NULL); };
        find_types(env);

        jobjectArray type_arr = env->NewObjectArray(type_num, class_class, NULL);
        CHECK_JAVA_EXC(env);

        for(Py_ssize_t i = 0; i < type_num; i++)
        {
            PyObject * type = PyTuple_GetItem(types, i);
            if(type == NULL)
            {
                return NULL;
            }
            if(!PyLong_Check(type))
            {
                PyErr_SetString(PyExc_ValueError, "type must be long");
                return NULL;
            }
            env->SetObjectArrayElement(type_arr, i, (jobject)PyLong_AsUnsignedLong(type));
            CHECK_JAVA_EXC(env); //TODO should?
        }

        Parameter * out_types = new Parameter[type_num + 1];
        onexit += [&out_types]{ if(out_types != NULL) { delete[] out_types; out_types = NULL; } };

        int out_static = 0;
        jmethodID res = get_method_raw(env, (jclass)clazz, method, type_num, type_arr, out_types, &out_static);
        if(res == NULL)
        {
            PyErr_SetString(PyExc_ValueError, "Method not found");
            return NULL;
        }

        PyObject * out_types_tuple = PyTuple_New(type_num + 1);
        if(out_types_tuple == NULL)
        {
            return NULL;
        }
        for (Py_ssize_t i = 0; i < type_num + 1; ++i) {
            PyObject * t = Py_BuildValue("ii", out_types[i].type, out_types[i].real_type);
            if(t == NULL)
            {
                return NULL;
            }
            PyTuple_SET_ITEM(out_types_tuple, i, t);
        }
        return Py_BuildValue("kNi", (unsigned long)res, out_types_tuple, out_static);
    }
    catch(java_exception & e)
    {
        LOG("got java exception in get_method");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    catch(jni_exception & e)
    {
        LOG("got jni exception in get_method");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    return NULL;
}

static PyObject * get_field(PyObject *self, PyObject *args)
{
    try
    {
        unsigned long clazz = 0;
        const char * field = NULL;
        if (!PyArg_ParseTuple(args, "ks", &clazz, &field))
        {
            return NULL;
        }

        Parameter out_type;
        int out_static = 0;
        JNIEnv * env = get_env();
        jfieldID res = get_field_raw(env, (jclass)clazz, field, &out_type, &out_static);
        if(res == NULL)
        {
            PyErr_SetString(PyExc_ValueError, "Field not found");
            return NULL;
        }

        return Py_BuildValue("k(ii)i", (unsigned long)res, out_type.type, out_type.real_type, out_static);
    }
    catch(java_exception & e)
    {
        LOG("got java exception in get_field");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    catch(jni_exception & e)
    {
        LOG("got jni exception in get_field");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    return NULL;
}

static PyObject * find_class(PyObject *self, PyObject *args)
{
    try
    {
        const char *clazz = NULL;
        if (!PyArg_ParseTuple(args, "s", &clazz))
        {
            return NULL;
        }

        JNIEnv * env = get_env();
        jclass local_clazz = env->FindClass(clazz);
        CHECK_JAVA_EXC(env);
        return PyLong_FromLong((unsigned long)make_global_ref(env, (jobject)local_clazz));
    }
    catch(java_exception & e)
    {
        LOG("got java exception in find_class");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    catch(jni_exception & e)
    {
        LOG("got jni exception in find_class");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    return NULL;
}

static PyObject * get_object_class(PyObject *self, PyObject *args)
{
    try
    {
        unsigned long object = 0;
        if (!PyArg_ParseTuple(args, "k", &object))
        {
            return NULL;
        }

        JNIEnv * env = get_env();
        jclass local_clazz = env->GetObjectClass((jobject)object);
        CHECK_JAVA_EXC(env);
        return PyLong_FromLong((unsigned long)make_global_ref(env, (jobject)local_clazz));
    }
    catch(java_exception & e)
    {
        LOG("got java exception in get_object_class");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    catch(jni_exception & e)
    {
        LOG("got jni exception in get_object_class");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    return NULL;
}

static bool make_value_raw(PyObject * object, int type, jvalue * result)
{
    switch(type)
    {
    case BOOLEAN:
    {
        if(!PyBool_Check(object))
        {
            return false;
        }
        result->z = (jboolean)PyLong_AsLong(object) != 0;
        break;
    }
    case BYTE:
    {
        if(!PyLong_Check(object))
        {
            return false;
        }
        result->b = (jbyte)PyLong_AsLong(object);
        break;
    }
    case CHARACTER:
    {
        if(!PyLong_Check(object))
        {
            return false;
        }
        result->c = (jchar)PyLong_AsUnsignedLong(object);
        break;
    }
    case SHORT:
    {
        if(!PyLong_Check(object))
        {
            return false;
        }
        result->s = (jshort)PyLong_AsLong(object);
        break;
    }
    case INTEGER:
    {
        if(!PyLong_Check(object))
        {
            return false;
        }
        result->i = (jint)PyLong_AsLong(object);
        break;
    }
    case LONG:
    {
        if(!PyLong_Check(object))
        {
            return false;
        }
        result->j = (jlong)PyLong_AsLongLong(object);
        break;
    }
    case FLOAT:
    {
        if(!PyFloat_Check(object))
        {
            return false;
        }
        result->f = (jfloat)PyFloat_AsDouble(object);
        break;
    }
    case DOUBLE:
    {
        if(!PyFloat_Check(object))
        {
            return false;
        }
        result->d = (jdouble)PyFloat_AsDouble(object);
        break;
    }
    case OBJECT:
    {
        if(!PyLong_Check(object))
        {
            return false;
        }
        result->l = (jobject)PyLong_AsUnsignedLong(object);
        break;
    }
    }
    return true;
}

static PyObject * make_value(PyObject *self, PyObject *args)
{
    static_assert(sizeof(jvalue) == sizeof(unsigned long long));

    PyObject * object = NULL;
    int type = 0;
    if(!PyArg_ParseTuple(args, "Oi", &object, &type))
    {
        return NULL;
    }

    jvalue result;
    result.j = 0; //easy for debugging
    if(!make_value_raw(object, type, &result))
    {
        PyErr_SetString(PyExc_ValueError, "type mismatch");
        return NULL;
    }

    unsigned long long ret = 0;
    memcpy(&ret, &result, sizeof(result));
    return PyLong_FromUnsignedLongLong(ret);
}

static PyObject * box(PyObject *self, PyObject *args)
{
    try
    {
        static_assert(sizeof(jvalue) == sizeof(unsigned long long));

        PyObject * object = NULL;
        int type = 0;
        if (!PyArg_ParseTuple(args, "Oi", &object, &type))
        {
            return NULL;
        }

        jvalue value;
        value.j = 0; //easy for debugging
        if(!make_value_raw(object, type, &value))
        {
            PyErr_SetString(PyExc_ValueError, "type mismatch");
            return NULL;
        }

        JNIEnv * env = get_env();
        return PyLong_FromLong((unsigned long)box_raw(env, value, type));
    }
    catch(java_exception & e)
    {
        LOG("got java exception in box");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    catch(jni_exception & e)
    {
        LOG("got jni exception in box");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    return NULL;
}

//static PyObject * new_primitive_array(PyObject *self, PyObject *args)
//{
//    try
//    {
//        int type = 0;
//        int length = 0;
//        if (!PyArg_ParseTuple(args, "ii", &type, &length))
//        {
//            return NULL;
//        }
//
//        JNIEnv * env = get_env();
//        jobject local_arr = NULL;
//        switch(type)
//        {
//            case BOOLEAN:
//            {
//                local_arr = env->NewBooleanArray(length);
//                break;
//            }
//        }
//
//        CHECK_JAVA_EXC(env);
//        return PyLong_FromLong((unsigned long)make_global_ref(env, local_arr));
//    }
//    catch(java_exception & e)
//    {
//        LOG("got java exception in find_class");
//        PyErr_SetString(PyExc_ValueError, e.what());
//    }
//    catch(jni_exception & e)
//    {
//        LOG("got jni exception in find_class");
//        PyErr_SetString(PyExc_ValueError, e.what());
//    }
//    return NULL;
//}

static PyObject * delete_global_ref(PyObject *self, PyObject *args)
{
    try
    {
        unsigned long ref_lng = 0;
        if (!PyArg_ParseTuple(args, "k", &ref_lng))
        {
            return NULL;
        }

        jobject ref = (jobject)ref_lng;
        if(ref != NULL)
        {
            JNIEnv * env = get_env();
            env->DeleteGlobalRef(ref);
        }
        Py_RETURN_NONE;
    }
    catch(jni_exception & e)
    {
        LOG("got jni exception in delete_global_ref");
        PyErr_SetString(PyExc_ValueError, e.what());
    }
    return NULL;
}

extern "C" JNIEXPORT jint JNICALL Java_com_happy_MainActivity_pythonRun(JNIEnv * env, jclass clazz, jstring script)
{
    auto path = get_string(env, script);

    FILE * fh = fopen(path.c_str(), "r");
    if(fh == NULL)
    {
        LOG("fopen failed");
        return -1;
    }

    wchar_t *program = Py_DecodeLocale(path.c_str(), NULL);
    PySys_SetArgv(1, &program);
    PyRun_SimpleFileExFlags(fh, path.c_str(), 1, NULL);
    return 0;
}

static PyMethodDef native_hapy_methods[] = {
        {"act",  act, METH_VARARGS, "Interacts with java"},
        {"get_method",  get_method, METH_VARARGS, "Finds a java method"},
        {"get_field",  get_field, METH_VARARGS, "Finds a java field"},
        {"find_class",  find_class, METH_VARARGS, "Finds a java class"},
        {"get_object_class",  get_object_class, METH_VARARGS, "Retrieves the object's class"},
        {"box",  box, METH_VARARGS, "Performs boxing of value"},
        {"make_value", make_value, METH_VARARGS, "Makes values"},
        {"delete_global_ref",  delete_global_ref, METH_VARARGS, "Delete a java reference"},
        {NULL, NULL, 0, NULL}        /* Sentinel */
};

static struct PyModuleDef native_hapymodule = {
        PyModuleDef_HEAD_INIT,
        "native_hapy",   /* name of module */
        NULL, /* module documentation, may be NULL */
        -1,       /* size of per-interpreter state of the module,
                 or -1 if the module keeps state in global variables. */
        native_hapy_methods
};

PyMODINIT_FUNC PyInit_native_hapy(void)
{
    return PyModule_Create(&native_hapymodule);
}

extern "C" JNIEXPORT jint JNICALL Java_com_happy_MainActivity_pythonInit(JNIEnv * env, jclass clazz, jstring pythonpath)
{
    env->GetJavaVM(&vm);

    auto path = get_string(env, pythonpath);

    setenv("PYTHONHOME", path.c_str(), 1);
    setenv("LD_LIBRARY_PATH", (path + "/lib").c_str(), 1);

    PyImport_AppendInittab("native_hapy", PyInit_native_hapy);

    Py_InitializeEx(0);

    return 0;
}
