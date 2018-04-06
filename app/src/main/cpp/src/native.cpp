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
#include <vector>
#include "native.h"

#define LOG(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG, "APPY", fmt, ##__VA_ARGS__)
#define PYTHON_CALL

JavaVM * vm = NULL;
jobject g_java_arg = NULL;
jclass class_class = NULL;
jclass reflection_class = NULL;
jclass python_exception_class = NULL;
jmethodID compatibleMethod = NULL;
jmethodID getField = NULL;
jmethodID unboxClassToEnum = NULL;
jmethodID inspect = NULL;
jmethodID stringToBytes = NULL;
jmethodID bytesToString = NULL;
jmethodID createInterface = NULL;

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
    NEW_ARRAY = 7,
    SET_ITEMS = 8,
    GET_ITEMS = 9,
    GET_ARRAY_LENGTH = 10,
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
    if(str == NULL)
    {
        return "";
    }
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

#define GET_ENV_WITHOUT_SCOPES() \
JNIEnv * env = _get_env(); \
env->PushLocalFrame(15); \
CHECK_JAVA_EXC(env); \
onexit += [env]{ env->PopLocalFrame(NULL); };

#define GET_ENV() \
scope_guards onexit; \
GET_ENV_WITHOUT_SCOPES()

JNIEnv * _get_env()
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

            jclass clazzclazz = env->GetObjectClass(clazz);
            jmethodID getName = env->GetMethodID(clazzclazz,
                                                    "getName",
                                                    "()Ljava/lang/String;");

            if(env->ExceptionCheck())
            {
                env->ExceptionClear();
                throw jni_exception("exception in exception");
            }

            jstring exc_name = (jstring)env->CallObjectMethod(clazz, getName);
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

            message = get_string(env, exc_name) + ": " + get_string(env, msg) + " in line " + buf;
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
        try
        {
            for(auto &f : *this)
            {
                f();
            }
        }
        catch(...)
        {
        }
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
        jclass local_reflection = env->FindClass("com/appy/Reflection");
        CHECK_JAVA_EXC(env);
        reflection_class = (jclass)make_global_ref(env, (jobject)local_reflection);
    }
    if(python_exception_class == NULL)
    {
        jclass local_python_exception = env->FindClass("com/appy/PythonException");
        CHECK_JAVA_EXC(env);
        python_exception_class = (jclass)make_global_ref(env, (jobject)local_python_exception);
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
    if(unboxClassToEnum == NULL)
    {
        unboxClassToEnum = env->GetStaticMethodID(reflection_class, "unboxClassToEnum", "(Ljava/lang/Class;)I");
        CHECK_JAVA_EXC(env);
    }
    if(inspect == NULL)
    {
        inspect = env->GetStaticMethodID(reflection_class, "inspect", "(Ljava/lang/Class;)[Ljava/lang/Object;");
        CHECK_JAVA_EXC(env);
    }
    if(stringToBytes == NULL)
    {
        stringToBytes = env->GetStaticMethodID(reflection_class, "stringToBytes", "(Ljava/lang/String;)[B");
        CHECK_JAVA_EXC(env);
    }
    if(bytesToString == NULL)
    {
        bytesToString = env->GetStaticMethodID(reflection_class, "bytesToString", "([B)Ljava/lang/String;");
        CHECK_JAVA_EXC(env);
    }
    if(createInterface == NULL)
    {
         createInterface = env->GetStaticMethodID(reflection_class, "createInterface", "(J[Ljava/lang/Class;)Ljava/lang/Object;");
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

static jvalue unbox_raw(JNIEnv * env, jobject obj, int value_type)
{
    find_types(env);
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
    find_types(env);
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

        jvalue * jvalues = NULL;
        scope_guards onexit;

        unsigned long self = 0;
        unsigned long method = 0;
        PyObject * values = NULL;
        int return_type = 0;
        int op = 0;

        if (!PyArg_ParseTuple(args, "kkOii", &self, &method, &values, &return_type, &op)) {
            return NULL;
        }

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

        jvalue ret;
        GET_ENV_WITHOUT_SCOPES();

        Py_BEGIN_ALLOW_THREADS

        ret = act_raw(env, (jobject)self, (void *)method, jvalues, return_type, op);

        Py_END_ALLOW_THREADS

        if(op == SET_FIELD || op == SET_STATIC_FIELD)
        {
            return_type = VOID;
        }

        if(return_type == OBJECT)
        {
            if(ret.l == NULL)
            {
                Py_RETURN_NONE;
            }
        }
        return unpack_value(ret, return_type);
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
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

        GET_ENV();
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
            CHECK_JAVA_EXC(env);
        }

        Parameter * out_types = new Parameter[type_num + 1];
        onexit += [&out_types]{ if(out_types != NULL) { delete[] out_types; out_types = NULL; } };

        int out_static = 0;
        jmethodID res = get_method_raw(env, (jclass)clazz, method, type_num, type_arr, out_types, &out_static);
        if(res == NULL)
        {
            PyErr_Format(PyExc_RuntimeError, "Method %s() not found", method);
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
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
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
        GET_ENV();
        jfieldID res = get_field_raw(env, (jclass)clazz, field, &out_type, &out_static);
        if(res == NULL)
        {
            PyErr_SetString(PyExc_RuntimeError, "Field not found");
            return NULL;
        }

        return Py_BuildValue("k(ii)i", (unsigned long)res, out_type.type, out_type.real_type, out_static);
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
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

        GET_ENV();
        jclass local_clazz = env->FindClass(clazz);
        CHECK_JAVA_EXC(env);
        return PyLong_FromUnsignedLong((unsigned long)make_global_ref(env, (jobject)local_clazz));
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * unbox_class(PyObject *self, PyObject *args)
{
    try
    {
        unsigned long object = 0;
        if (!PyArg_ParseTuple(args, "k", &object))
        {
            return NULL;
        }

        GET_ENV();
        find_types(env);
        jint e = env->CallStaticIntMethod(reflection_class, unboxClassToEnum, (jobject)object);
        CHECK_JAVA_EXC(env);
        return PyLong_FromLong(e);
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
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

        GET_ENV();
        jclass local_clazz = env->GetObjectClass((jobject)object);
        CHECK_JAVA_EXC(env);
        return PyLong_FromUnsignedLong((unsigned long)make_global_ref(env, (jobject)local_clazz));
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
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
    result.j = 0; //easier for debugging
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

        GET_ENV();
        return PyLong_FromLong((unsigned long)box_raw(env, value, type));
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * unbox(PyObject *self, PyObject *args)
{
    try
    {
        unsigned long object = 0;
        int unboxed_type = 0;
        if (!PyArg_ParseTuple(args, "ki", &object, &unboxed_type))
        {
            return NULL;
        }

        GET_ENV();
        find_types(env);
        jvalue v = unbox_raw(env, (jobject)object, unboxed_type);
        return unpack_value(v, unboxed_type);
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

struct Optional
{
    jvalue value;
    bool exists;
};

#define ARRAY_CASE(TYPE, Type, jtype, union_name) \
case TYPE: \
{ \
    jint array_len = 0; \
    if(op == NEW_ARRAY) \
    { \
        array_len = values_start + values_len; \
        array = env->New##Type##Array(array_len); \
        if(array == NULL) \
        { \
            throw jni_exception("failed to create new array"); \
        } \
    } \
    else \
    { \
        array_len = env->GetArrayLength((jtype##Array)array); \
    } \
\
    *out_array_len = array_len; \
\
    if(op == GET_ARRAY_LENGTH) \
    { \
        return NULL; \
    } \
\
    if(values != NULL) \
    { \
        jtype * nativearr = env->Get##Type##ArrayElements((jtype##Array)array, NULL); \
        if(nativearr == NULL) \
        { \
            throw jni_exception("failed to get array elements"); \
        } \
\
        if(op == NEW_ARRAY || op == SET_ITEMS) \
        { \
            for(int i = values_start; i < values_start + values_len && i < array_len; i++) \
            { \
                if(values[i - values_start].exists) \
                { \
                    nativearr[i] = values[i - values_start].value.union_name; \
                } \
            } \
        } \
\
        if(op == GET_ITEMS) \
        { \
            for(int i = values_start; i < values_start + values_len && i < array_len; i++) \
            { \
                values[i - values_start].exists = true; \
                values[i - values_start].value.union_name = nativearr[i]; \
            } \
        } \
        env->Release##Type##ArrayElements((jtype##Array)array, nativearr, 0); \
    } \
    if(op == NEW_ARRAY) \
    { \
        return make_global_ref(env, array); \
    } \
    return NULL; \
}

static jobject array_raw(JNIEnv * env, jobject array, int type, Optional * values, int values_start, int values_len, int op, int * out_array_len, jclass objectclass)
{

    switch(type)
    {
        ARRAY_CASE(BOOLEAN, Boolean, jboolean, z)
        ARRAY_CASE(BYTE, Byte, jbyte, b)
        ARRAY_CASE(CHARACTER, Char, jchar, c)
        ARRAY_CASE(SHORT, Short, jshort, s)
        ARRAY_CASE(INTEGER, Int, jint, i)
        ARRAY_CASE(LONG, Long, jlong, j)
        ARRAY_CASE(FLOAT, Float, jfloat, f)
        ARRAY_CASE(DOUBLE, Double, jdouble, d)
        case OBJECT:
        {
            jint array_len = 0;
            if(op == NEW_ARRAY)
            {
                array_len = values_start + values_len;
                array = env->NewObjectArray(array_len, objectclass, NULL);
                CHECK_JAVA_EXC(env);
                if(array == NULL)
                {
                    throw jni_exception("failed to create new array");
                }
            }
            else
            {
                array_len = env->GetArrayLength((jobjectArray)array);
            }

            *out_array_len = array_len;

            if(op == GET_ARRAY_LENGTH)
            {
                return NULL;
            }

            if(values != NULL)
            {
                if(op == NEW_ARRAY || op == SET_ITEMS)
                {
                    for(int i = values_start; i < values_start + values_len && i < array_len; i++)
                    {
                        if(values[i - values_start].exists)
                        {
                            env->SetObjectArrayElement((jobjectArray)array, i, values[i - values_start].value.l);
                            CHECK_JAVA_EXC(env);
                        }
                    }
                }

                if(op == GET_ITEMS)
                {
                    for(int i = values_start; i < values_start + values_len && i < array_len; i++)
                    {
                        jobject l = env->GetObjectArrayElement((jobjectArray)array, i);
                        CHECK_JAVA_EXC(env);
                        if(l == NULL)
                        {
                            values[i - values_start].value.l = NULL;
                            values[i - values_start].exists = false;
                        }
                        else
                        {
                            values[i - values_start].value.l = make_global_ref(env, l);
                            values[i - values_start].exists = true;
                        }
                    }
                }
            }
            if(op == NEW_ARRAY)
            {
                return make_global_ref(env, array);
            }
            return NULL;
        }
    }
    throw jni_exception("unknown type");
}

static PyObject * array(PyObject *self, PyObject *args)
{
    try
    {
        static_assert(sizeof(jvalue) == sizeof(unsigned long long));

        Optional * jvalues = NULL;
        scope_guards onexit;

        unsigned long obj = 0;
        PyObject * values = NULL;
        int start = 0;
        int type = 0;
        int op = 0;
        unsigned long objclass = 0;

        if (!PyArg_ParseTuple(args, "kOiiik", &obj, &values, &start, &type, &op, &objclass)) {
            return NULL;
        }

        Py_ssize_t value_num = 0;
        if(values != Py_None)
        {
            if(!PyTuple_Check(values))
            {
                PyErr_SetString(PyExc_ValueError, "values must be a tuple");
                return NULL;
            }

            value_num = PyTuple_Size(values);
            if(value_num > 0)
            {
                jvalues = new Optional[value_num];
                onexit += [&jvalues]{ if(jvalues != NULL) { delete[] jvalues; jvalues = NULL; } };

                for(Py_ssize_t i = 0; i < value_num; i++)
                {
                    PyObject * item = PyTuple_GetItem(values, i);
                    if(item == Py_None)
                    {
                        jvalues[i].exists = false;
                    }
                    else
                    {
                        if(!PyLong_Check(item))
                        {
                            PyErr_SetString(PyExc_ValueError, "value must be long");
                            return NULL;
                        }

                        unsigned long long lng = PyLong_AsUnsignedLongLong(item);
                        memcpy(&jvalues[i].value, &lng, sizeof(lng));
                        jvalues[i].exists = true;
                    }
                }
            }
        }

        PyObject * out_tuple = PyTuple_New(value_num);
        if(out_tuple == NULL)
        {
            return NULL;
        }

        GET_ENV_WITHOUT_SCOPES();
        int out_array_len = 0;
        jobject ret = array_raw(env, (jobject)obj, type, jvalues, start, value_num, op, &out_array_len, (jclass)objclass);

        for (Py_ssize_t i = 0; i < value_num; ++i) {
            if(jvalues[i].exists)
            {
                PyTuple_SET_ITEM(out_tuple, i, unpack_value(jvalues[i].value, type));
            }
            else
            {
                Py_INCREF(Py_None);
                PyTuple_SET_ITEM(out_tuple, i, Py_None);
            }
        }

        if(ret == NULL)
        {
            ret = (jobject)obj;
        }
        return Py_BuildValue("ikN", out_array_len, (unsigned long)ret, out_tuple);
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

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
             GET_ENV();
             env->DeleteGlobalRef(ref);
         }
         Py_RETURN_NONE;
     }
     catch(jni_exception & e)
     {
         PyErr_SetString(PyExc_RuntimeError, e.what());
     }
     return NULL;
 }

 static PyObject * new_global_ref(PyObject *self, PyObject *args)
 {
     try
     {
         unsigned long ref_lng = 0;
         if (!PyArg_ParseTuple(args, "k", &ref_lng))
         {
             return NULL;
         }

         jobject ref = (jobject)ref_lng;
         jobject global_ref = NULL;
         if(ref != NULL)
         {
             GET_ENV();
             global_ref = env->NewGlobalRef(ref);
             if(global_ref == NULL)
             {
                 throw jni_exception("failed to create global ref");
             }
         }
         return Py_BuildValue("k", (unsigned long)global_ref);
     }
     catch(jni_exception & e)
     {
         PyErr_SetString(PyExc_RuntimeError, e.what());
     }
     return NULL;
 }

static std::string inspect_class_raw(JNIEnv * env, jclass ref, int * enum_type, int * is_array, jclass * out_component, int * component_enum_type, int * unboxed_component_enum_type)
{
    find_types(env);

    jobjectArray result = (jobjectArray)env->CallStaticObjectMethod(reflection_class, inspect, ref);
    CHECK_JAVA_EXC(env);

    jstring className = (jstring)env->GetObjectArrayElement(result, 0);
    CHECK_JAVA_EXC(env);
    std::string class_name = get_string(env, className);

    jobject enumType = env->GetObjectArrayElement(result, 1);
    CHECK_JAVA_EXC(env);
    *enum_type = get_integer(env, enumType);

    jobject isArray = env->GetObjectArrayElement(result, 2);
    CHECK_JAVA_EXC(env);
    *is_array = get_integer(env, isArray);

    jclass component = (jclass)env->GetObjectArrayElement(result, 3);
    CHECK_JAVA_EXC(env);

    jobject componentEnumType = env->GetObjectArrayElement(result, 4);
    CHECK_JAVA_EXC(env);
    *component_enum_type = get_integer(env, componentEnumType);

    jobject unboxedComponentEnumType = env->GetObjectArrayElement(result, 5);
    CHECK_JAVA_EXC(env);
    *unboxed_component_enum_type = get_integer(env, unboxedComponentEnumType);

    if(component == NULL)
    {
        *out_component = NULL;
    }
    else
    {
        *out_component = (jclass)make_global_ref(env, component);
    }
    return class_name;
}

static PyObject * inspect_class(PyObject *self, PyObject *args)
{
    try
    {
        unsigned long ref_lng = 0;
        if (!PyArg_ParseTuple(args, "k", &ref_lng))
        {
            return NULL;
        }

        jclass ref = (jclass)ref_lng;
        GET_ENV();
        int enum_type = 0;
        int is_array = 0;
        jclass component = NULL;
        int component_enum_type = 0;
        int unboxed_component_enum_type = 0;
        std::string class_name = inspect_class_raw(env, ref, &enum_type, &is_array, &component, &component_enum_type, &unboxed_component_enum_type);
        return Py_BuildValue("siikii", class_name.c_str(), enum_type, is_array, (unsigned long)component, component_enum_type, unboxed_component_enum_type);
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static jstring make_string_raw(const char * str, int len)
{
    GET_ENV();
    find_types(env);

    jbyteArray barr = env->NewByteArray(len);
    if(barr == NULL) \
    {
        throw jni_exception("failed to create byte array");
    }

    jbyte * bytes = env->GetByteArrayElements(barr, NULL);
    if(bytes == NULL)
    {
        throw jni_exception("failed to get byte array elements");
    }

    memcpy(bytes, str, len);
    env->ReleaseByteArrayElements(barr, bytes, 0);

    jstring jstr = (jstring)env->CallStaticObjectMethod(reflection_class, bytesToString, barr);
    CHECK_JAVA_EXC(env);

    return (jstring)make_global_ref(env, jstr);
}

static PyObject * make_string(PyObject *self, PyObject *args)
{
    try
    {
        const char * str = NULL;
        Py_ssize_t len = 0;
        if (!PyArg_ParseTuple(args, "s#", &str, &len))
        {
            return NULL;
        }

        return Py_BuildValue("k", (unsigned long)make_string_raw(str, len));
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static std::vector<char> unbox_string_raw(jstring jstr)
{
    GET_ENV();
    find_types(env);

    jbyteArray barr = (jbyteArray)env->CallStaticObjectMethod(reflection_class, stringToBytes, jstr);
    jsize len = env->GetArrayLength(barr);
    std::vector<char> str(len, 0);
    jbyte * bytes = env->GetByteArrayElements(barr, NULL);
    if(bytes == NULL)
    {
        throw jni_exception("failed to get byte array elements");
    }

    memcpy(str.data(), bytes, len);
    env->ReleaseByteArrayElements(barr, bytes, 0);

    return str;
}

static PyObject * unbox_string(PyObject *self, PyObject *args)
{
    try
    {
        unsigned long jstr = 0;
        if (!PyArg_ParseTuple(args, "k", &jstr))
        {
            return NULL;
        }

        std::vector<char> str = unbox_string_raw((jstring)jstr);
        return Py_BuildValue("s#", str.data(), str.size());
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static jclass array_of_class_raw(jclass clazz)
{
    GET_ENV();

    jobjectArray arr = env->NewObjectArray(0, clazz, NULL);
    CHECK_JAVA_EXC(env);
    if(arr == NULL)
    {
        throw jni_exception("failed to create new array");
    }
    jclass local_clazz = env->GetObjectClass(arr);
    CHECK_JAVA_EXC(env);
    return (jclass)make_global_ref(env, local_clazz);
}

static PyObject * array_of_class(PyObject *self, PyObject *args)
{
    try
    {
        unsigned long ref_lng = 0;
        if (!PyArg_ParseTuple(args, "k", &ref_lng))
        {
            return NULL;
        }

        return Py_BuildValue("k", array_of_class_raw((jclass)ref_lng));
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static jobject create_interface_raw(jlong id, jobjectArray classes)
{
    GET_ENV();
    find_types(env);

    jobject iface = env->CallStaticObjectMethod(reflection_class, createInterface, id, classes);
    CHECK_JAVA_EXC(env);
    if(iface == NULL)
    {
        throw jni_exception("failed to create interface");
    }
    return make_global_ref(env, iface);
}

static PyObject * create_interface(PyObject *self, PyObject *args)
{
    try
    {
        long long id = 0;
        unsigned long classes = 0;
        if (!PyArg_ParseTuple(args, "Lk", &id, &classes))
        {
            return NULL;
        }

        return Py_BuildValue("k", (unsigned long)create_interface_raw((jlong)id, (jobjectArray)classes));
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * callback = NULL;
static PyObject * set_callback(PyObject *self, PyObject *args)
{
    PyObject * temp = NULL;
    if (!PyArg_ParseTuple(args, "O", &temp))
    {
        return NULL;
    }

    if (!PyCallable_Check(temp))
    {
        PyErr_SetString(PyExc_TypeError, "parameter must be callable");
        return NULL;
    }
    Py_XINCREF(temp);         /* Add a reference to new callback */
    Py_XDECREF(callback);  /* Dispose of previous callback */
    callback = temp;       /* Remember new callback */
    Py_RETURN_NONE;
}

static PyObject * get_java_arg(PyObject *self, PyObject *args)
{
    try
    {
        if(g_java_arg == NULL)
        {
            PyErr_SetString(PyExc_SystemError, "no java arg available");
            return NULL;
        }
        GET_ENV();
        jobject ref = env->NewGlobalRef(g_java_arg);
        if(ref == NULL)
        {
            throw jni_exception("failed to create global ref");
        }
        return PyLong_FromUnsignedLong((unsigned long)ref);
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * castable(PyObject *self, PyObject *args)
{
    try
    {
        unsigned long src = 0;
        unsigned long dest = 0;
        if (!PyArg_ParseTuple(args, "kk", &src, &dest))
        {
            return NULL;
        }

        GET_ENV();
        jboolean ret = env->IsAssignableFrom((jclass)src, (jclass)dest);
        if(ret == JNI_TRUE)
        {
            Py_RETURN_TRUE;
        }
        else
        {
            Py_RETURN_FALSE;
        }
    }
    catch(java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch(jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

extern "C" JNIEXPORT jobject JNICALL Java_com_appy_Widget_pythonCall(JNIEnv * env, jclass clazz, jobjectArray args)
{
    try
    {
        find_types(env);

        if(callback == NULL)
        {
            env->ThrowNew(python_exception_class, "No callback defined");
            return NULL;
        }

        PyGILState_STATE gstate;
        gstate = PyGILState_Ensure();

        scope_guards guards;
        guards += [&gstate] {PyGILState_Release(gstate);};

        PyObject * arg = Py_BuildValue("(k)", (unsigned long)make_global_ref(env, args));
        if(arg == NULL)
        {
            PyErr_Clear();
            env->ThrowNew(python_exception_class, "build value failed");
            return NULL;
        }
        PyObject * result = PyObject_CallObject(callback, arg);
        Py_XDECREF(arg);
        if (result == NULL)
        {
            PyObject *type = NULL, *value = NULL, *traceback = NULL;
            PyErr_Fetch(&type, &value, &traceback);
            const char * cstr = "python exception...";
            if(value != NULL)
            {
                PyObject * str = PyObject_Str(value);
                cstr = PyUnicode_AsUTF8(str);
                Py_XDECREF(str);
            }
            Py_XDECREF(type);
            Py_XDECREF(value);
            Py_XDECREF(traceback);
            env->ThrowNew(python_exception_class, cstr);
            return NULL;
        }

        if(!PyLong_Check(result))
        {
            Py_DECREF(result);
            env->ThrowNew(python_exception_class, "callback result is invalid");
            return NULL;
        }

        jobject global_ref = (jobject)PyLong_AsUnsignedLong(result);
        Py_DECREF(result);

        if(global_ref != NULL)
        {
            jobject local = env->NewLocalRef(global_ref);
            env->DeleteGlobalRef(global_ref);
            return local;
        }
    }
    catch(...)
    {
        LOG("cpp exception");
    }
    return NULL;
}

static PyObject * logcat_write(PyObject *self, PyObject *args)
{
    int level = 0;
    const char * tag = NULL;
    Py_ssize_t taglen = 0;
    const char * msg = NULL;
    Py_ssize_t msglen = 0;
    if (!PyArg_ParseTuple(args, "is#s#", &level, &tag, &taglen, &msg, &msglen))
    {
        return NULL;
    }
    int ret = __android_log_write(level, tag, msg);
    if(ret >= 0)
    {
        return Py_BuildValue("i", ret);
    }
    else
    {
        PyErr_SetString(PyExc_RuntimeError, "logcat write failed");
        return NULL;
    }
}

static PyMethodDef native_appy_methods[] = {
        {"act",  act, METH_VARARGS, "Interacts with java"},
        {"get_method",  get_method, METH_VARARGS, "Finds a java method"},
        {"get_field",  get_field, METH_VARARGS, "Finds a java field"},
        {"find_class",  find_class, METH_VARARGS, "Finds a java class"},
        {"get_object_class",  get_object_class, METH_VARARGS, "Retrieves the object's class"},
        {"box",  box, METH_VARARGS, "Performs boxing of a value"},
        {"unbox", unbox, METH_VARARGS, "Performs unboxing of a value"},
        {"make_value", make_value, METH_VARARGS, "Makes values"},
        {"array", array, METH_VARARGS, "everything array related"},
        {"unbox_class", unbox_class, METH_VARARGS, "unbox object class"},
        {"delete_global_ref",  delete_global_ref, METH_VARARGS, "Delete a java reference"},
        {"new_global_ref", new_global_ref, METH_VARARGS, "created a new global reference (should be used only on callbacks)"},
        {"inspect_class",  inspect_class, METH_VARARGS, "inspects a java class"},
        {"make_string", make_string, METH_VARARGS, "makes jstring from str"},
        {"unbox_string", unbox_string, METH_VARARGS, "unbox string"},
        {"array_of_class", array_of_class, METH_VARARGS, "gets the class which is the array of a given class"},
        {"set_callback", set_callback, METH_VARARGS, "sets callback to be called from java"},
        {"create_interface", create_interface, METH_VARARGS, "creates interface"},
        {"get_java_arg", get_java_arg, METH_VARARGS, "gets the arg passed from java"},
        {"castable", castable, METH_VARARGS, "checks whether an object can be cast into class"},
        {"logcat_write", logcat_write, METH_VARARGS, "writes to logcat"},
        {NULL, NULL, 0, NULL}        /* Sentinel */
};

static struct PyModuleDef native_appymodule = {
        PyModuleDef_HEAD_INIT,
        "native_appy",   /* name of module */
        NULL, /* module documentation, may be NULL */
        -1,       /* size of per-interpreter state of the module,
                 or -1 if the module keeps state in global variables. */
        native_appy_methods
};

PyMODINIT_FUNC PyInit_native_appy(void)
{
    return PyModule_Create(&native_appymodule);
}

extern "C" void android_get_LD_LIBRARY_PATH(char*, size_t);
extern "C" void android_update_LD_LIBRARY_PATH(const char*);

static void append_to_env(const char * env, const std::string & what)
{
    std::string newenv;
    char * prev_env = getenv(env);
    if(prev_env != NULL)
    {
        newenv = prev_env;
        newenv += ":";
    }

    newenv += what;
    setenv(env, newenv.c_str(), 1);
}

extern "C" JNIEXPORT void JNICALL Java_com_appy_Widget_pythonInit(JNIEnv * env, jclass clazz, jstring j_pythonhome, jstring j_cachepath, jstring j_pythonlib, jstring j_scriptpath, jobject j_arg)
{
    try
    {
        LOG("python init");

        find_types(env);

        int ret = env->GetJavaVM(&vm);
        if(ret != 0)
        {
            env->ThrowNew(python_exception_class, "GetJavaVM failed");
            return;
        }

        auto pythonhome = get_string(env, j_pythonhome);
        auto cachepath = get_string(env, j_cachepath);
        auto pythonlib = get_string(env, j_pythonlib);
        auto scriptpath = get_string(env, j_scriptpath);

        setenv("PYTHONHOME", pythonhome.c_str(), 1);
        setenv("HOME", pythonhome.c_str(), 1);
        setenv("SHELL", "/system/bin/sh", 1);
        setenv("TMP", cachepath.c_str(), 1);

        append_to_env("LD_LIBRARY_PATH", pythonhome + "/lib");
        append_to_env("PATH", pythonhome + "/bin");

        //LD_LIBRARY_PATH hack
        char buffer[1024] = {};
        ((decltype(&android_get_LD_LIBRARY_PATH))dlsym(RTLD_DEFAULT, "android_get_LD_LIBRARY_PATH"))(buffer, sizeof(buffer) - 1);

        std::string library_path(buffer);
        if(!library_path.empty())
        {
            library_path += ":";
        }
        library_path += pythonhome + "/lib";

        ((decltype(&android_update_LD_LIBRARY_PATH))dlsym(RTLD_DEFAULT, "android_update_LD_LIBRARY_PATH"))(library_path.c_str());
        //--------------------

        ret = PyImport_AppendInittab("native_appy", PyInit_native_appy);
        if(ret == -1)
        {
            env->ThrowNew(python_exception_class, "PyImport_AppendInittab failed");
            return;
        }

        wchar_t * pythonlib_w = Py_DecodeLocale(pythonlib.c_str(), NULL);
        if(pythonlib_w == NULL)
        {
            env->ThrowNew(python_exception_class, "Py_DecodeLocale failed");
            return;
        }

        Py_SetProgramName(pythonlib_w);
        Py_InitializeEx(0);

        if(j_arg != NULL)
        {
            if(g_java_arg == NULL)
            {
                g_java_arg = env->NewGlobalRef(j_arg);
            }
            if(g_java_arg == NULL)
            {
                env->ThrowNew(python_exception_class, "NewGlobalRef failed");
                return;
            }
        }

        FILE * fh = fopen(scriptpath.c_str(), "r");
        if(fh == NULL)
        {
            env->ThrowNew(python_exception_class, "fopen failed");
            return;
        }

        wchar_t *program = Py_DecodeLocale(scriptpath.c_str(), NULL);
        if(program == NULL)
        {
            env->ThrowNew(python_exception_class, "Py_DecodeLocale failed");
            return;
        }

        PySys_SetArgv(1, &program);

        ret = PyRun_SimpleFileExFlags(fh, scriptpath.c_str(), 1, NULL);

        PyEval_InitThreads();

        //TODO not sure if this is ok
        PyThreadState* mainPyThread = PyEval_SaveThread();

        if(ret == -1)
        {
            env->ThrowNew(python_exception_class, "PyRun_SimpleFileExFlags failed");
            return;
        }

        return;
    }
    catch(std::exception & e)
    {
        env->ThrowNew(python_exception_class, e.what());
        return;
    }
    catch(...)
    {
        env->ThrowNew(python_exception_class, "exception was thrown from pythonInit");
        return;
    }
}

