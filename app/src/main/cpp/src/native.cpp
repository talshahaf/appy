#define PY_SSIZE_T_CLEAN

#include "Python.h"
#include <stdio.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include <dirent.h>
#include <jni.h>
#include <android/log.h>
#include <type_traits>
#include <string>
#include <chrono>
#include <functional>
#include <deque>
#include <vector>
#include <exception>
#include <cxxabi.h>
#include "native.h"

#define LOG(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG, "APPY", fmt, ##__VA_ARGS__)
#define PYTHON_CALL

int populate_common_java_objects_stage = 0;

JavaVM * vm = NULL;
jobject g_java_arg = NULL;
jclass class_class = NULL;
jclass reflection_class = NULL;
jclass python_exception_class = NULL;
jmethodID compatibleMethod = NULL;
jmethodID getField = NULL;
jmethodID unboxClassToEnum = NULL;
jmethodID inspectClass = NULL;
jmethodID inspectClassContent = NULL;
jmethodID stringToBytes = NULL;
jmethodID bytesToString = NULL;
jmethodID createInterface = NULL;
jmethodID formatException = NULL;

//primitives
jclass boolean_class = NULL;
jclass byte_class = NULL;
jclass character_class = NULL;
jclass short_class = NULL;
jclass integer_class = NULL;
jclass long_class = NULL;
jclass float_class = NULL;
jclass double_class = NULL;
jclass string_class = NULL;

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

static void populate_common_java_objects(JNIEnv * env);

static std::string jstring_to_stdstring(JNIEnv * env, jstring str)
{
    if (str == NULL)
    {
        return "";
    }
    const char * cstr = env->GetStringUTFChars(str, NULL);
    if (cstr == NULL)
    {
        throw jni_exception("failed to get string utf chars");
    }
    const jint length = env->GetStringLength(str);
    std::string path(cstr, length);
    env->ReleaseStringUTFChars(str, cstr);
    return path;
}

static int jinteger_to_int(JNIEnv * env, jobject integer)
{
    populate_common_java_objects(env);
    int i = env->CallIntMethod(integer, intValueMethod);
    CHECK_JAVA_EXC(env);
    return i;
}

#define GET_JNI_ENV_WITHOUT_SCOPES() \
JNIEnv * env = _get_jni_env(); \
env->PushLocalFrame(15); \
CHECK_JAVA_EXC(env); \
onexit += [env]{ env->PopLocalFrame(NULL); };

#define GET_JNI_ENV() \
scope_guards onexit; \
GET_JNI_ENV_WITHOUT_SCOPES()

JNIEnv * _get_jni_env()
{
    JNIEnv * env = NULL;
    if (vm->AttachCurrentThread(&env, NULL) != JNI_OK)
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
            if (reflection_class == NULL || formatException == NULL)
            {
                throw jni_exception(("exception in exception, jni constants are null: " +
                                     std::to_string(populate_common_java_objects_stage)).c_str());
            }

            jstring result = (jstring) env->CallStaticObjectMethod(reflection_class,
                                                                   formatException, exception);
            if (env->ExceptionCheck())
            {
                env->ExceptionClear();
                throw jni_exception("exception in exception, in call formatException");
            }

            std::string strresult = jstring_to_stdstring(env, result);
            if (env->ExceptionCheck())
            {
                env->ExceptionClear();
                throw jni_exception("exception in exception: jstring to stdstring");
            }

            message = strresult + "\n in line " + std::to_string(line);
        }
        catch (jni_exception & e)
        {
            message = std::string("exception while getting info for exception: ") + e.what();
        }
        catch (...)
        {
            message = "exception while getting info for exception";
        }
    }

    virtual ~java_exception()
    {
        if (exception != NULL)
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

class scope_guards : public std::deque<std::function<void()>>
{
public:
    template<class Callable>
    scope_guards & operator+=(Callable && undo_func)
    {
        emplace_front(std::forward<Callable>(undo_func));
        return *this;
    }

    ~scope_guards()
    {
        try
        {
            for (auto & f: *this)
            {
                f();
            }
        }
        catch (...)
        {
        }
    }

    void dismiss() noexcept
    {
        clear();
    }

    scope_guards() = default;

    scope_guards(const scope_guards &) = delete;

    void operator=(const scope_guards &) = delete;
};

void check_java_exc(JNIEnv * env, int line)
{
    if (env->ExceptionCheck() == JNI_TRUE)
    {
        jthrowable local_exc = (jthrowable) env->ExceptionOccurred();
        //env->ExceptionDescribe();
        env->ExceptionClear();
        jthrowable exc = (jthrowable) env->NewGlobalRef(local_exc);
        env->DeleteLocalRef(local_exc);
        if (exc == NULL)
        {
            throw jni_exception("failed to global ref exception");
        }
        throw java_exception(env, exc, line);
    }
}

#define JNI_FUNCTIONS_CASE(TYPE, Type, union_name, refcode) \
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

static jobject make_global_java_ref(JNIEnv * env, jobject local)
{
    if (local == NULL)
    {
        throw jni_exception("cannot make global reference of null");
    }
    jobject glob;
    glob = env->NewGlobalRef(local);
    env->DeleteLocalRef(local);
    if (glob == NULL)
    {
        throw jni_exception("failed to create global ref");
    }
    return glob;
}

jvalue
call_jni_object_functions_impl(JNIEnv * env, jobject self, void * id, jvalue * values,
                               int returnType,
                               int op)
{
    jvalue ret;

    switch (returnType)
    {
        JNI_FUNCTIONS_CASE(BOOLEAN, Boolean, z,)
        JNI_FUNCTIONS_CASE(BYTE, Byte, b,)
        JNI_FUNCTIONS_CASE(CHARACTER, Char, c,)
        JNI_FUNCTIONS_CASE(SHORT, Short, s,)
        JNI_FUNCTIONS_CASE(INTEGER, Int, i,)
        JNI_FUNCTIONS_CASE(LONG, Long, j,)
        JNI_FUNCTIONS_CASE(FLOAT, Float, f,)
        JNI_FUNCTIONS_CASE(DOUBLE, Double, d,)
        JNI_FUNCTIONS_CASE(OBJECT, Object, l,
                           ret.l = ret.l == NULL ? NULL : make_global_java_ref(env, ret.l))

        case VOID:
        {
            switch (op)
            {
                case CALL_METHOD:
                {
                    env->CallVoidMethodA(self, (jmethodID) id, values);
                    break;
                }
                case CALL_STATIC_METHOD:
                {
                    env->CallStaticVoidMethodA((jclass) self, (jmethodID) id, values);
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
            if (op != CALL_STATIC_METHOD)
            {
                throw jni_exception("constructors must be called with CALL_STATIC_METHOD op");
            }
            ret.l = env->NewObjectA((jclass) self, (jmethodID) id, values);
            if (ret.l == NULL)
            {
                throw jni_exception("failed to create new object");
            }
            ret.l = make_global_java_ref(env, ret.l);
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

static void populate_common_java_objects(JNIEnv * env)
{
    if (class_class == NULL)
    {
        jclass local_class_class = env->FindClass("java/lang/Class");
        CHECK_JAVA_EXC(env);
        class_class = (jclass) make_global_java_ref(env, (jobject) local_class_class);
    }
    populate_common_java_objects_stage = 1;
    if (reflection_class == NULL)
    {
        jclass local_reflection = env->FindClass("com/appy/Reflection");
        CHECK_JAVA_EXC(env);
        reflection_class = (jclass) make_global_java_ref(env, (jobject) local_reflection);
    }
    populate_common_java_objects_stage = 2;
    if (python_exception_class == NULL)
    {
        jclass local_python_exception = env->FindClass("com/appy/PythonException");
        CHECK_JAVA_EXC(env);
        python_exception_class = (jclass) make_global_java_ref(env,
                                                               (jobject) local_python_exception);
    }
    populate_common_java_objects_stage = 3;
    if (formatException == NULL)
    {
        formatException = env->GetStaticMethodID(reflection_class, "formatException",
                                                 "(Ljava/lang/Throwable;)Ljava/lang/String;");
        CHECK_JAVA_EXC(env);
    }
    populate_common_java_objects_stage = 4;
    //-------primitives-----------------
    if (boolean_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Boolean");
        CHECK_JAVA_EXC(env);
        boolean_class = (jclass) make_global_java_ref(env, (jobject) local);
    }
    if (byte_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Byte");
        CHECK_JAVA_EXC(env);
        byte_class = (jclass) make_global_java_ref(env, (jobject) local);
    }
    if (character_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Character");
        CHECK_JAVA_EXC(env);
        character_class = (jclass) make_global_java_ref(env, (jobject) local);
    }
    if (short_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Short");
        CHECK_JAVA_EXC(env);
        short_class = (jclass) make_global_java_ref(env, (jobject) local);
    }
    if (integer_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Integer");
        CHECK_JAVA_EXC(env);
        integer_class = (jclass) make_global_java_ref(env, (jobject) local);
    }
    if (long_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Long");
        CHECK_JAVA_EXC(env);
        long_class = (jclass) make_global_java_ref(env, (jobject) local);
    }
    if (float_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Float");
        CHECK_JAVA_EXC(env);
        float_class = (jclass) make_global_java_ref(env, (jobject) local);
    }
    if (double_class == NULL)
    {
        jclass local = env->FindClass("java/lang/Double");
        CHECK_JAVA_EXC(env);
        double_class = (jclass) make_global_java_ref(env, (jobject) local);
    }
    if (string_class == NULL)
    {
        jclass local = env->FindClass("java/lang/String");
        CHECK_JAVA_EXC(env);
        string_class = (jclass) make_global_java_ref(env, (jobject)local);
    }
    populate_common_java_objects_stage = 5;
    //----------------------------------
    if (compatibleMethod == NULL)
    {
        compatibleMethod = env->GetStaticMethodID(reflection_class, "getCompatibleMethod",
                                                  "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)[Ljava/lang/Object;");
        CHECK_JAVA_EXC(env);
    }
    populate_common_java_objects_stage = 6;
    if (getField == NULL)
    {
        getField = env->GetStaticMethodID(reflection_class, "getField",
                                          "(Ljava/lang/Class;Ljava/lang/String;Z)[Ljava/lang/Object;");
        CHECK_JAVA_EXC(env);
    }
    populate_common_java_objects_stage = 7;
    if (unboxClassToEnum == NULL)
    {
        unboxClassToEnum = env->GetStaticMethodID(reflection_class, "unboxClassToEnum",
                                                  "(Ljava/lang/Class;)I");
        CHECK_JAVA_EXC(env);
    }
    populate_common_java_objects_stage = 8;
    if (inspectClass == NULL)
    {
        inspectClass = env->GetStaticMethodID(reflection_class, "inspectClass",
                                              "(Ljava/lang/Class;)[Ljava/lang/Object;");
        CHECK_JAVA_EXC(env);
    }
    populate_common_java_objects_stage = 9;
    if (inspectClassContent == NULL)
    {
        inspectClassContent = env->GetStaticMethodID(reflection_class, "inspectClassContent",
                                                     "(Ljava/lang/Class;ZZ)[Ljava/lang/Object;");
        CHECK_JAVA_EXC(env);
    }
    populate_common_java_objects_stage = 10;
    if (stringToBytes == NULL)
    {
        stringToBytes = env->GetStaticMethodID(reflection_class, "stringToBytes",
                                               "(Ljava/lang/String;)[B");
        CHECK_JAVA_EXC(env);
    }
    populate_common_java_objects_stage = 11;
    if (bytesToString == NULL)
    {
        bytesToString = env->GetStaticMethodID(reflection_class, "bytesToString",
                                               "([B)Ljava/lang/String;");
        CHECK_JAVA_EXC(env);
    }
    populate_common_java_objects_stage = 12;
    if (createInterface == NULL)
    {
        createInterface = env->GetStaticMethodID(reflection_class, "createInterface",
                                                 "(J[Ljava/lang/Class;)Ljava/lang/Object;");
        CHECK_JAVA_EXC(env);
    }
    populate_common_java_objects_stage = 13;
    //---------primitive-accessors--------------------------
    if (booleanCtor == NULL)
    {
        booleanCtor = env->GetMethodID(boolean_class, "<init>", "(Z)V");
        CHECK_JAVA_EXC(env);
    }
    if (byteCtor == NULL)
    {
        byteCtor = env->GetMethodID(byte_class, "<init>", "(B)V");
        CHECK_JAVA_EXC(env);
    }
    if (charCtor == NULL)
    {
        charCtor = env->GetMethodID(character_class, "<init>", "(C)V");
        CHECK_JAVA_EXC(env);
    }
    if (shortCtor == NULL)
    {
        shortCtor = env->GetMethodID(short_class, "<init>", "(S)V");
        CHECK_JAVA_EXC(env);
    }
    if (intCtor == NULL)
    {
        intCtor = env->GetMethodID(integer_class, "<init>", "(I)V");
        CHECK_JAVA_EXC(env);
    }
    if (longCtor == NULL)
    {
        longCtor = env->GetMethodID(long_class, "<init>", "(J)V");
        CHECK_JAVA_EXC(env);
    }
    if (floatCtor == NULL)
    {
        floatCtor = env->GetMethodID(float_class, "<init>", "(F)V");
        CHECK_JAVA_EXC(env);
    }
    if (doubleCtor == NULL)
    {
        doubleCtor = env->GetMethodID(double_class, "<init>", "(D)V");
        CHECK_JAVA_EXC(env);
    }
    populate_common_java_objects_stage = 14;
    if (booleanValueMethod == NULL)
    {
        booleanValueMethod = env->GetMethodID(boolean_class, "booleanValue", "()Z");
        CHECK_JAVA_EXC(env);
    }
    if (byteValueMethod == NULL)
    {
        byteValueMethod = env->GetMethodID(byte_class, "byteValue", "()B");
        CHECK_JAVA_EXC(env);
    }
    if (charValueMethod == NULL)
    {
        charValueMethod = env->GetMethodID(character_class, "charValue", "()C");
        CHECK_JAVA_EXC(env);
    }
    if (shortValueMethod == NULL)
    {
        shortValueMethod = env->GetMethodID(short_class, "shortValue", "()S");
        CHECK_JAVA_EXC(env);
    }
    if (intValueMethod == NULL)
    {
        intValueMethod = env->GetMethodID(integer_class, "intValue", "()I");
        CHECK_JAVA_EXC(env);
    }
    if (longValueMethod == NULL)
    {
        longValueMethod = env->GetMethodID(long_class, "longValue", "()J");
        CHECK_JAVA_EXC(env);
    }
    if (floatValueMethod == NULL)
    {
        floatValueMethod = env->GetMethodID(float_class, "floatValue", "()F");
        CHECK_JAVA_EXC(env);
    }
    if (doubleValueMethod == NULL)
    {
        doubleValueMethod = env->GetMethodID(double_class, "doubleValue", "()D");
        CHECK_JAVA_EXC(env);
    }
    populate_common_java_objects_stage = 15;
    //------------------------------------------------------
}

jfieldID
get_fieldid_impl(JNIEnv * env, jclass clazz, const char * field, Parameter * out_type,
                 int * out_static,
                 int * out_has_same_name_method)
{
    populate_common_java_objects(env);

    jstring field_str = env->NewStringUTF(field);
    CHECK_JAVA_EXC(env);
    jobjectArray result = (jobjectArray) env->CallStaticObjectMethod(reflection_class, getField,
                                                                     clazz, field_str, true);
    CHECK_JAVA_EXC(env);

    jfieldID fieldID = NULL;
    if (result != NULL)
    {
        jobject fieldIdReflected = env->GetObjectArrayElement(result, 0);
        if (fieldIdReflected != NULL)
        {
            fieldID = env->FromReflectedField(fieldIdReflected);
        }

        jintArray parameter = (jintArray) env->GetObjectArrayElement(result, 1);
        CHECK_JAVA_EXC(env);
        if (parameter != NULL)
        {
            jint * paramarr = env->GetIntArrayElements(parameter, NULL);
            if (paramarr == NULL)
            {
                throw jni_exception(
                        "failed to GetIntArrayElements after finding compatible method");
            }
            out_type->type = paramarr[0];
            out_type->real_type = paramarr[1];
            env->ReleaseIntArrayElements(parameter, paramarr, JNI_ABORT);
        }

        jobject staticInteger = env->GetObjectArrayElement(result, 2);
        CHECK_JAVA_EXC(env);
        jobject hasSameNameMethodInteger = env->GetObjectArrayElement(result, 3);
        CHECK_JAVA_EXC(env);

        *out_static = jinteger_to_int(env, staticInteger);
        *out_has_same_name_method = jinteger_to_int(env, hasSameNameMethodInteger);
    }
    return fieldID;
}

jmethodID
get_methodid_impl(JNIEnv * env, jclass clazz, const char * method, int n, jobjectArray type_arr,
                  Parameter * outTypes, int * outStatic, int * outHasSameNameField)
{
    populate_common_java_objects(env);

    jobjectArray result = NULL;
    if (method == NULL || strlen(method) == 0)
    {
        result = (jobjectArray) env->CallStaticObjectMethod(reflection_class, compatibleMethod,
                                                            clazz, 0, type_arr);
    }
    else
    {
        jstring method_str = env->NewStringUTF(method);
        CHECK_JAVA_EXC(env);
        result = (jobjectArray) env->CallStaticObjectMethod(reflection_class, compatibleMethod,
                                                            clazz, method_str, type_arr);
    }
    CHECK_JAVA_EXC(env);

    jmethodID methodID = NULL;
    if (result != NULL)
    {
        jobject methodIdReflected = env->GetObjectArrayElement(result, 0);
        if (methodIdReflected != NULL)
        {
            methodID = env->FromReflectedMethod(methodIdReflected);
        }
        jobject staticInteger = env->GetObjectArrayElement(result, 1);
        CHECK_JAVA_EXC(env);
        jobject hasSameNameFieldInteger = env->GetObjectArrayElement(result, 2);
        CHECK_JAVA_EXC(env);

        *outStatic = jinteger_to_int(env, staticInteger);
        *outHasSameNameField = jinteger_to_int(env, hasSameNameFieldInteger);

        jobjectArray realTypes = (jobjectArray) env->GetObjectArrayElement(result, 3);
        CHECK_JAVA_EXC(env);

        if (realTypes != NULL)
        {
            for (size_t i = 0; i < n + 1; i++)
            {
                jintArray parameter = (jintArray) env->GetObjectArrayElement(realTypes, i);
                CHECK_JAVA_EXC(env);
                jint * paramarr = env->GetIntArrayElements(parameter, NULL);
                if (paramarr == NULL)
                {
                    throw jni_exception(
                            "failed to GetIntArrayElements after finding compatible method");
                }
                outTypes[i].type = paramarr[0];
                outTypes[i].real_type = paramarr[1];

                env->ReleaseIntArrayElements(parameter, paramarr, JNI_ABORT);
            }
        }
    }
    return methodID;
}

static jvalue unpack_java_primitive_impl(JNIEnv * env, jobject obj, int value_type)
{
    populate_common_java_objects(env);
    jvalue ret;
    switch (value_type)
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

static jobject pack_java_primitive_impl(JNIEnv * env, jvalue value, int value_type)
{
    populate_common_java_objects(env);
    jobject obj = NULL;
    switch (value_type)
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

    if (obj == NULL)
    {
        throw jni_exception("failed to create new object");
    }
    return make_global_java_ref(env, obj);
}

static PyObject * unpacked_jvalue_to_python(jvalue v, int t)
{
    switch (t)
    {
        case BOOLEAN:
        {
            return PyBool_FromLong((long) (jbyte) v.z);
        }
        case BYTE:
        {
            return PyLong_FromLong((long) v.b);
        }
        case CHARACTER:
        {
            return PyLong_FromLong((long) (jshort) v.c);
        }
        case SHORT:
        {
            return PyLong_FromLong((long) v.s);
        }
        case INTEGER:
        {
            return PyLong_FromLong((long) v.i);
        }
        case LONG:
        {
            return PyLong_FromLongLong((long long) v.j);
        }
        case FLOAT:
        {
            return PyFloat_FromDouble((double) v.f);
        }
        case DOUBLE:
        {
            return PyFloat_FromDouble(v.d);
        }
        case CONST:
        case OBJECT:
        {
            return PyLong_FromUnsignedLong((unsigned long) v.l);
        }
        case VOID:
        {
            Py_RETURN_NONE;
        }
    }

    PyErr_SetString(PyExc_ValueError, "unknown type");
    return NULL;
}

static PyObject * call_jni_object_functions(PyObject * self, PyObject * args)
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

        if (!PyArg_ParseTuple(args, "kkOii", &self, &method, &values, &return_type, &op))
        {
            return NULL;
        }

        if (values != Py_None)
        {
            if (!PyTuple_Check(values))
            {
                PyErr_SetString(PyExc_ValueError, "values must be a tuple");
                return NULL;
            }

            Py_ssize_t value_num = PyTuple_Size(values);
            if (value_num > 0)
            {
                jvalues = new jvalue[value_num];
                onexit += [&jvalues] {
                    if (jvalues != NULL)
                    {
                        delete[] jvalues;
                        jvalues = NULL;
                    }
                };

                for (Py_ssize_t i = 0; i < value_num; i++)
                {
                    PyObject * item = PyTuple_GetItem(values, i);
                    if (!PyLong_Check(item))
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
        GET_JNI_ENV_WITHOUT_SCOPES();

        std::exception_ptr exc = nullptr;

        Py_BEGIN_ALLOW_THREADS
            try
            {
                ret = call_jni_object_functions_impl(env, (jobject) self, (void *) method, jvalues,
                                                     return_type, op);
            }
            catch (...)
            {
                exc = std::current_exception();
            }
        Py_END_ALLOW_THREADS

        if (exc != nullptr)
        {
            std::rethrow_exception(exc);
        }

        if (op == SET_FIELD || op == SET_STATIC_FIELD)
        {
            return_type = VOID;
        }

        if (return_type == OBJECT)
        {
            if (ret.l == NULL)
            {
                Py_RETURN_NONE;
            }
        }
        return unpacked_jvalue_to_python(ret, return_type);
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * get_methodid(PyObject * self, PyObject * args)
{
    try
    {
        unsigned long clazz = 0;
        const char * method = NULL;
        PyObject * types = NULL;

        if (!PyArg_ParseTuple(args, "ksO", &clazz, &method, &types))
        {
            return NULL;
        }

        if (!PyTuple_Check(types))
        {
            PyErr_SetString(PyExc_ValueError, "types must be a tuple");
            return NULL;
        }

        Py_ssize_t type_num = PyTuple_Size(types);

        GET_JNI_ENV();
        populate_common_java_objects(env);

        jobjectArray type_arr = env->NewObjectArray(type_num, class_class, NULL);
        CHECK_JAVA_EXC(env);

        for (Py_ssize_t i = 0; i < type_num; i++)
        {
            PyObject * type = PyTuple_GetItem(types, i);
            if (type == NULL)
            {
                return NULL;
            }
            if (!PyLong_Check(type))
            {
                PyErr_SetString(PyExc_ValueError, "type must be long");
                return NULL;
            }
            env->SetObjectArrayElement(type_arr, i, (jobject) PyLong_AsUnsignedLong(type));
            CHECK_JAVA_EXC(env);
        }

        Parameter * out_types = new Parameter[type_num + 1];
        onexit += [&out_types] {
            if (out_types != NULL)
            {
                delete[] out_types;
                out_types = NULL;
            }
        };

        int out_static = 0;
        int out_has_same_name_field = 0;
        jmethodID res = get_methodid_impl(env, (jclass) clazz, method, type_num, type_arr,
                                          out_types, &out_static, &out_has_same_name_field);
        if (res == NULL)
        {
            //no such method, still return same_name_field
            return Py_BuildValue("sssi", NULL, NULL, NULL, out_has_same_name_field);
        }

        PyObject * out_types_tuple = PyTuple_New(type_num + 1);
        if (out_types_tuple == NULL)
        {
            return NULL;
        }
        for (Py_ssize_t i = 0; i < type_num + 1; ++i)
        {
            PyObject * t = Py_BuildValue("ii", out_types[i].type, out_types[i].real_type);
            if (t == NULL)
            {
                Py_XDECREF(out_types_tuple);
                return NULL;
            }
            PyTuple_SET_ITEM(out_types_tuple, i, t);
        }
        return Py_BuildValue("kNii", (unsigned long) res, out_types_tuple, out_static,
                             out_has_same_name_field);
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * get_fieldid(PyObject * self, PyObject * args)
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
        int out_has_same_name_method = 0;
        GET_JNI_ENV();
        jfieldID res = get_fieldid_impl(env, (jclass) clazz, field, &out_type, &out_static,
                                        &out_has_same_name_method);
        if (res == NULL)
        {
            //no such field
            return Py_BuildValue("ssssi", NULL, NULL, NULL, NULL, out_has_same_name_method);
        }

        return Py_BuildValue("kiiii", (unsigned long) res, out_type.type, out_type.real_type,
                             out_static, out_has_same_name_method);
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * find_class(PyObject * self, PyObject * args)
{
    try
    {
        const char * clazz = NULL;
        if (!PyArg_ParseTuple(args, "s", &clazz))
        {
            return NULL;
        }

        GET_JNI_ENV();
        jclass local_clazz = env->FindClass(clazz);
        CHECK_JAVA_EXC(env);
        return PyLong_FromUnsignedLong(
                (unsigned long) make_global_java_ref(env, (jobject) local_clazz));
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * unpack_primitive_class(PyObject * self, PyObject * args)
{
    try
    {
        unsigned long object = 0;
        if (!PyArg_ParseTuple(args, "k", &object))
        {
            return NULL;
        }

        GET_JNI_ENV();
        populate_common_java_objects(env);
        jint e = env->CallStaticIntMethod(reflection_class, unboxClassToEnum, (jobject) object);
        CHECK_JAVA_EXC(env);
        return PyLong_FromLong(e);
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * get_object_class(PyObject * self, PyObject * args)
{
    try
    {
        unsigned long object = 0;
        if (!PyArg_ParseTuple(args, "k", &object))
        {
            return NULL;
        }

        GET_JNI_ENV();
        jclass local_clazz = env->GetObjectClass((jobject) object);
        CHECK_JAVA_EXC(env);
        return PyLong_FromUnsignedLong(
                (unsigned long) make_global_java_ref(env, (jobject) local_clazz));
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static bool python_to_unpacked_jvalue_impl(PyObject * object, int type, jvalue * result)
{
    switch (type)
    {
        case BOOLEAN:
        {
            if (!PyBool_Check(object))
            {
                return false;
            }
            result->z = (jboolean) PyLong_AsLong(object) != 0;
            break;
        }
        case BYTE:
        {
            if (!PyLong_Check(object))
            {
                return false;
            }
            result->b = (jbyte) PyLong_AsLong(object);
            break;
        }
        case CHARACTER:
        {
            if (!PyLong_Check(object))
            {
                return false;
            }
            result->c = (jchar) PyLong_AsUnsignedLong(object);
            break;
        }
        case SHORT:
        {
            if (!PyLong_Check(object))
            {
                return false;
            }
            result->s = (jshort) PyLong_AsLong(object);
            break;
        }
        case INTEGER:
        {
            if (!PyLong_Check(object))
            {
                return false;
            }
            result->i = (jint) PyLong_AsLong(object);
            break;
        }
        case LONG:
        {
            if (!PyLong_Check(object))
            {
                return false;
            }
            result->j = (jlong) PyLong_AsLongLong(object);
            break;
        }
        case FLOAT:
        {
            if (!PyFloat_Check(object))
            {
                return false;
            }
            result->f = (jfloat) PyFloat_AsDouble(object);
            break;
        }
        case DOUBLE:
        {
            if (!PyFloat_Check(object))
            {
                return false;
            }
            result->d = (jdouble) PyFloat_AsDouble(object);
            break;
        }
        case OBJECT:
        {
            if (!PyLong_Check(object))
            {
                return false;
            }
            result->l = (jobject) PyLong_AsUnsignedLong(object);
            break;
        }
    }
    return true;
}

static bool
python_to_unpacked_jvalue_helper(PyObject * self, PyObject * args, jvalue * outresult,
                                 int * outtype)
{
    static_assert(sizeof(jvalue) == sizeof(unsigned long long));

    PyObject * object = NULL;
    int type = 0;
    if (!PyArg_ParseTuple(args, "Oi", &object, &type))
    {
        return false;
    }

    if (outtype != NULL)
    {
        *outtype = type;
    }

    outresult->j = 0; //easier for debugging
    if (!python_to_unpacked_jvalue_impl(object, type, outresult))
    {
        PyErr_SetString(PyExc_ValueError, "type mismatch");
        return false;
    }

    return true;
}

static PyObject * python_to_unpacked_jvalue(PyObject * self, PyObject * args)
{
    jvalue result;
    if (!python_to_unpacked_jvalue_helper(self, args, &result, NULL))
    {
        return NULL;
    }

    unsigned long long ret = 0;
    memcpy(&ret, &result, sizeof(result));
    return PyLong_FromUnsignedLongLong(ret);
}

static PyObject * python_to_packed_java_primitive(PyObject * self, PyObject * args)
{
    try
    {
        jvalue result;
        int type = 0;
        if (!python_to_unpacked_jvalue_helper(self, args, &result, &type))
        {
            return NULL;
        }

        GET_JNI_ENV();
        return PyLong_FromLong((unsigned long) pack_java_primitive_impl(env, result, type));
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * packed_java_primitive_to_python(PyObject * self, PyObject * args)
{
    try
    {
        unsigned long object = 0;
        int unboxed_type = 0;
        if (!PyArg_ParseTuple(args, "ki", &object, &unboxed_type))
        {
            return NULL;
        }

        GET_JNI_ENV();
        populate_common_java_objects(env);
        jvalue v = unpack_java_primitive_impl(env, (jobject) object, unboxed_type);
        return unpacked_jvalue_to_python(v, unboxed_type);
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

struct OptionalJValue
{
    jvalue value;
    bool exists;
};

#define JNI_ARRAY_FUNCTIONS_CASE(TYPE, Type, jtype, union_name) \
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
        return make_global_java_ref(env, array); \
    } \
    return NULL; \
}

static jobject
call_jni_array_functions_impl(JNIEnv * env, jobject array, int type, OptionalJValue * values,
                              int values_start, int values_len, int op, int * out_array_len,
                              jclass objectclass)
{

    switch (type)
    {
        JNI_ARRAY_FUNCTIONS_CASE(BOOLEAN, Boolean, jboolean, z)
        JNI_ARRAY_FUNCTIONS_CASE(BYTE, Byte, jbyte, b)
        JNI_ARRAY_FUNCTIONS_CASE(CHARACTER, Char, jchar, c)
        JNI_ARRAY_FUNCTIONS_CASE(SHORT, Short, jshort, s)
        JNI_ARRAY_FUNCTIONS_CASE(INTEGER, Int, jint, i)
        JNI_ARRAY_FUNCTIONS_CASE(LONG, Long, jlong, j)
        JNI_ARRAY_FUNCTIONS_CASE(FLOAT, Float, jfloat, f)
        JNI_ARRAY_FUNCTIONS_CASE(DOUBLE, Double, jdouble, d)
        case OBJECT:
        {
            jint array_len = 0;
            if (op == NEW_ARRAY)
            {
                array_len = values_start + values_len;
                array = env->NewObjectArray(array_len, objectclass, NULL);
                CHECK_JAVA_EXC(env);
                if (array == NULL)
                {
                    throw jni_exception("failed to create new array");
                }
            }
            else
            {
                array_len = env->GetArrayLength((jobjectArray) array);
            }

            *out_array_len = array_len;

            if (op == GET_ARRAY_LENGTH)
            {
                return NULL;
            }

            if (values != NULL)
            {
                if (op == NEW_ARRAY || op == SET_ITEMS)
                {
                    for (int i = values_start;
                         i < values_start + values_len && i < array_len; i++)
                    {
                        if (values[i - values_start].exists)
                        {
                            env->SetObjectArrayElement((jobjectArray) array, i,
                                                       values[i - values_start].value.l);
                            CHECK_JAVA_EXC(env);
                        }
                    }
                }

                if (op == GET_ITEMS)
                {
                    for (int i = values_start;
                         i < values_start + values_len && i < array_len; i++)
                    {
                        jobject l = env->GetObjectArrayElement((jobjectArray) array, i);
                        CHECK_JAVA_EXC(env);
                        if (l == NULL)
                        {
                            values[i - values_start].value.l = NULL;
                            values[i - values_start].exists = false;
                        }
                        else
                        {
                            values[i - values_start].value.l = make_global_java_ref(env, l);
                            values[i - values_start].exists = true;
                        }
                    }
                }
            }
            if (op == NEW_ARRAY)
            {
                return make_global_java_ref(env, array);
            }
            return NULL;
        }
    }
    throw jni_exception("unknown type");
}

static PyObject * call_jni_array_functions(PyObject * self, PyObject * args)
{
    try
    {
        static_assert(sizeof(jvalue) == sizeof(unsigned long long));

        OptionalJValue * jvalues = NULL;
        scope_guards onexit;

        unsigned long obj = 0;
        PyObject * values = NULL;
        int start = 0;
        int type = 0;
        int op = 0;
        unsigned long objclass = 0;

        if (!PyArg_ParseTuple(args, "kOiiik", &obj, &values, &start, &type, &op, &objclass))
        {
            return NULL;
        }

        Py_ssize_t value_num = 0;
        if (values != Py_None)
        {
            if (!PyTuple_Check(values))
            {
                PyErr_SetString(PyExc_ValueError, "values must be a tuple");
                return NULL;
            }

            value_num = PyTuple_Size(values);
            if (value_num > 0)
            {
                jvalues = new OptionalJValue[value_num];
                onexit += [&jvalues] {
                    if (jvalues != NULL)
                    {
                        delete[] jvalues;
                        jvalues = NULL;
                    }
                };

                for (Py_ssize_t i = 0; i < value_num; i++)
                {
                    PyObject * item = PyTuple_GetItem(values, i);
                    if (item == Py_None)
                    {
                        jvalues[i].exists = false;
                    }
                    else
                    {
                        if (!PyLong_Check(item))
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
        if (out_tuple == NULL)
        {
            return NULL;
        }

        GET_JNI_ENV_WITHOUT_SCOPES();
        int out_array_len = 0;
        jobject ret = call_jni_array_functions_impl(env, (jobject) obj, type, jvalues, start,
                                                    value_num, op, &out_array_len,
                                                    (jclass) objclass);

        for (Py_ssize_t i = 0; i < value_num; ++i)
        {
            if (jvalues[i].exists)
            {
                PyTuple_SET_ITEM(out_tuple, i, unpacked_jvalue_to_python(jvalues[i].value, type));
            }
            else
            {
                Py_INCREF(Py_None);
                PyTuple_SET_ITEM(out_tuple, i, Py_None);
            }
        }

        if (ret == NULL)
        {
            ret = (jobject) obj;
        }
        return Py_BuildValue("ikN", out_array_len, (unsigned long) ret, out_tuple);
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * delete_global_ref(PyObject * self, PyObject * args)
{
    try
    {
        unsigned long ref_lng = 0;
        if (!PyArg_ParseTuple(args, "k", &ref_lng))
        {
            return NULL;
        }

        jobject ref = (jobject) ref_lng;
        if (ref != NULL)
        {
            GET_JNI_ENV();
            env->DeleteGlobalRef(ref);
        }
        Py_RETURN_NONE;
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * new_global_ref(PyObject * self, PyObject * args)
{
    try
    {
        unsigned long ref_lng = 0;
        if (!PyArg_ParseTuple(args, "k", &ref_lng))
        {
            return NULL;
        }

        jobject ref = (jobject) ref_lng;
        jobject global_ref = NULL;
        if (ref != NULL)
        {
            GET_JNI_ENV();
            global_ref = env->NewGlobalRef(ref);
            if (global_ref == NULL)
            {
                throw jni_exception("failed to create global ref");
            }
        }
        return Py_BuildValue("k", (unsigned long) global_ref);
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static std::string
inspect_class_impl(JNIEnv * env, jclass ref, int * enum_type, int * is_array,
                   jclass * out_component,
                   int * component_enum_type, int * unboxed_component_enum_type)
{
    populate_common_java_objects(env);

    jobjectArray result = (jobjectArray) env->CallStaticObjectMethod(reflection_class, inspectClass,
                                                                     ref);
    CHECK_JAVA_EXC(env);

    jstring className = (jstring) env->GetObjectArrayElement(result, 0);
    CHECK_JAVA_EXC(env);
    std::string class_name = jstring_to_stdstring(env, className);

    jobject enumType = env->GetObjectArrayElement(result, 1);
    CHECK_JAVA_EXC(env);
    *enum_type = jinteger_to_int(env, enumType);

    jobject isArray = env->GetObjectArrayElement(result, 2);
    CHECK_JAVA_EXC(env);
    *is_array = jinteger_to_int(env, isArray);

    jclass component = (jclass) env->GetObjectArrayElement(result, 3);
    CHECK_JAVA_EXC(env);

    jobject componentEnumType = env->GetObjectArrayElement(result, 4);
    CHECK_JAVA_EXC(env);
    *component_enum_type = jinteger_to_int(env, componentEnumType);

    jobject unboxedComponentEnumType = env->GetObjectArrayElement(result, 5);
    CHECK_JAVA_EXC(env);
    *unboxed_component_enum_type = jinteger_to_int(env, unboxedComponentEnumType);

    if (component == NULL)
    {
        *out_component = NULL;
    }
    else
    {
        *out_component = (jclass) make_global_java_ref(env, component);
    }
    return class_name;
}

static PyObject * inspect_class(PyObject * self, PyObject * args)
{
    try
    {
        unsigned long ref_lng = 0;
        if (!PyArg_ParseTuple(args, "k", &ref_lng))
        {
            return NULL;
        }

        jclass ref = (jclass) ref_lng;
        GET_JNI_ENV();
        int enum_type = 0;
        int is_array = 0;
        jclass component = NULL;
        int component_enum_type = 0;
        int unboxed_component_enum_type = 0;
        std::string class_name = inspect_class_impl(env, ref, &enum_type, &is_array, &component,
                                                    &component_enum_type,
                                                    &unboxed_component_enum_type);
        return Py_BuildValue("siikii", class_name.c_str(), enum_type, is_array,
                             (unsigned long) component, component_enum_type,
                             unboxed_component_enum_type);
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static std::vector<std::string> inspect_class_content_impl(JNIEnv * env, jclass ref, bool withargs)
{
    populate_common_java_objects(env);

    jobjectArray result = (jobjectArray) env->CallStaticObjectMethod(reflection_class,
                                                                     inspectClassContent, ref,
                                                                     withargs, true);
    CHECK_JAVA_EXC(env);

    jint array_len = env->GetArrayLength(result);

    std::vector<std::string> out;
    for (int i = 0; i < array_len; i++)
    {
        jstring s = (jstring) env->GetObjectArrayElement(result, i);
        CHECK_JAVA_EXC(env);

        out.push_back(jstring_to_stdstring(env, s));
    }

    return out;
}

static PyObject * inspect_class_content(PyObject * self, PyObject * args)
{
    try
    {
        unsigned long ref_lng = 0;
        int withargs = 0;
        if (!PyArg_ParseTuple(args, "ki", &ref_lng, &withargs))
        {
            return NULL;
        }

        jclass ref = (jclass) ref_lng;
        GET_JNI_ENV();
        std::vector<std::string> content = inspect_class_content_impl(env, ref, withargs);

        PyObject * out_tuple = PyTuple_New(content.size());
        if (out_tuple == NULL)
        {
            return NULL;
        }
        for (Py_ssize_t i = 0; i < content.size(); ++i)
        {
            PyObject * s = NULL;
            if (content[i].data() == NULL)
            {
                s = Py_BuildValue("s#", 0x1, 0);
            }
            else
            {
                s = Py_BuildValue("s#", content[i].data(), content[i].size());
            }
            if (s == NULL)
            {
                Py_XDECREF(out_tuple);
                return NULL;
            }
            PyTuple_SET_ITEM(out_tuple, i, s);
        }

        return out_tuple;
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static jstring char_array_to_jstring(const char * str, int len)
{
    GET_JNI_ENV();
    populate_common_java_objects(env);

    jbyteArray barr = env->NewByteArray(len);
    if (barr == NULL) \

    {
        throw jni_exception("failed to create byte array");
    }

    jbyte * bytes = env->GetByteArrayElements(barr, NULL);
    if (bytes == NULL)
    {
        throw jni_exception("failed to get byte array elements");
    }

    memcpy(bytes, str, len);
    env->ReleaseByteArrayElements(barr, bytes, 0);

    jstring jstr = (jstring) env->CallStaticObjectMethod(reflection_class, bytesToString, barr);
    CHECK_JAVA_EXC(env);

    return (jstring) make_global_java_ref(env, jstr);
}

static PyObject * python_str_to_jstring(PyObject * self, PyObject * args)
{
    try
    {
        const char * str = NULL;
        Py_ssize_t len = 0;
        if (!PyArg_ParseTuple(args, "s#", &str, &len))
        {
            return NULL;
        }

        return Py_BuildValue("k", (unsigned long) char_array_to_jstring(str, len));
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static std::vector<char> jstring_to_char_array(jstring jstr)
{
    GET_JNI_ENV();
    populate_common_java_objects(env);

    jbyteArray barr = (jbyteArray) env->CallStaticObjectMethod(reflection_class, stringToBytes,
                                                               jstr);
    jsize len = env->GetArrayLength(barr);
    std::vector<char> str(len, 0);
    jbyte * bytes = env->GetByteArrayElements(barr, NULL);
    if (bytes == NULL)
    {
        throw jni_exception("failed to get byte array elements");
    }

    memcpy(str.data(), bytes, len);
    env->ReleaseByteArrayElements(barr, bytes, 0);

    return str;
}

static PyObject * jstring_to_python_str(PyObject * self, PyObject * args)
{
    try
    {
        unsigned long jstr = 0;
        if (!PyArg_ParseTuple(args, "k", &jstr))
        {
            return NULL;
        }

        std::vector<char> str = jstring_to_char_array((jstring) jstr);

        if (str.data() == NULL)
        {
            return Py_BuildValue("s#", 0x1, 0);
        }
        else
        {
            return Py_BuildValue("s#", str.data(), str.size());
        }
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static jclass jclass_to_array_of_jclass_impl(jclass clazz)
{
    GET_JNI_ENV();

    jobjectArray arr = env->NewObjectArray(0, clazz, NULL);
    CHECK_JAVA_EXC(env);
    if (arr == NULL)
    {
        throw jni_exception("failed to create new array");
    }
    jclass local_clazz = env->GetObjectClass(arr);
    CHECK_JAVA_EXC(env);
    return (jclass) make_global_java_ref(env, local_clazz);
}

static PyObject * jclass_to_array_of_jclass(PyObject * self, PyObject * args)
{
    try
    {
        unsigned long ref_lng = 0;
        if (!PyArg_ParseTuple(args, "k", &ref_lng))
        {
            return NULL;
        }

        return Py_BuildValue("k", jclass_to_array_of_jclass_impl((jclass) ref_lng));
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static jobject create_java_interface_impl(jlong id, jobjectArray classes)
{
    GET_JNI_ENV();
    populate_common_java_objects(env);

    jobject iface = env->CallStaticObjectMethod(reflection_class, createInterface, id, classes);
    CHECK_JAVA_EXC(env);
    if (iface == NULL)
    {
        throw jni_exception("failed to create interface");
    }
    return make_global_java_ref(env, iface);
}

static PyObject * create_java_interface(PyObject * self, PyObject * args)
{
    try
    {
        long long id = 0;
        unsigned long classes = 0;
        if (!PyArg_ParseTuple(args, "Lk", &id, &classes))
        {
            return NULL;
        }

        return Py_BuildValue("k", (unsigned long) create_java_interface_impl((jlong) id,
                                                                             (jobjectArray) classes));
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * callback = NULL;

static PyObject * set_python_callback(PyObject * self, PyObject * args)
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

static PyObject * get_java_init_arg(PyObject * self, PyObject * args)
{
    try
    {
        if (g_java_arg == NULL)
        {
            PyErr_SetString(PyExc_SystemError, "no java arg available");
            return NULL;
        }
        GET_JNI_ENV();
        jobject ref = env->NewGlobalRef(g_java_arg);
        if (ref == NULL)
        {
            throw jni_exception("failed to create global ref");
        }
        return PyLong_FromUnsignedLong((unsigned long) ref);
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * check_is_jclass_castable(PyObject * self, PyObject * args)
{
    try
    {
        unsigned long src = 0;
        unsigned long dest = 0;
        if (!PyArg_ParseTuple(args, "kk", &src, &dest))
        {
            return NULL;
        }

        GET_JNI_ENV();
        jboolean ret = env->IsAssignableFrom((jclass) src, (jclass) dest);
        if (ret == JNI_TRUE)
        {
            Py_RETURN_TRUE;
        }
        else
        {
            Py_RETURN_FALSE;
        }
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static jclass dict_class = NULL;
static jclass dict_list_class = NULL;
static jclass dict_entry_class = NULL;
static jmethodID dict_ctor = NULL;
static jmethodID dict_list_ctor = NULL;
static jmethodID dict_put_dictobj = NULL;
static jmethodID dict_list_add_dictobj = NULL;

static jmethodID dict_put_boolean = NULL;
static jmethodID dict_put_int = NULL;
static jmethodID dict_put_long = NULL;
static jmethodID dict_put_double = NULL;
static jmethodID dict_put_string = NULL;

static jmethodID dict_list_array = NULL;
static jmethodID dict_entries = NULL;

static jfieldID dict_entry_key = NULL;
static jfieldID dict_entry_value = NULL;

static bool dict_fields_inited = false;

static void init_dict_fields(JNIEnv * env)
{
    if (dict_fields_inited)
    {
        return;
    }

    jclass local_dict_class = env->FindClass("com/appy/DictObj$Dict");
    CHECK_JAVA_EXC(env);
    if (local_dict_class == NULL)
    {
        LOG("no dict_class");
        return;
    }
    dict_class = (jclass) make_global_java_ref(env, (jobject)local_dict_class);

    jclass local_dict_list_class = env->FindClass("com/appy/DictObj$List");
    CHECK_JAVA_EXC(env);
    if (local_dict_list_class == NULL)
    {
        LOG("no dict_list_class");
        return;
    }
    dict_list_class = (jclass) make_global_java_ref(env, (jobject)local_dict_list_class);

    jclass local_dict_entry_class = env->FindClass("com/appy/DictObj$Entry");
    CHECK_JAVA_EXC(env);
    if (local_dict_entry_class == NULL)
    {
        LOG("no dict_entry_class");
        return;
    }
    dict_entry_class = (jclass) make_global_java_ref(env, (jobject)local_dict_entry_class);

    dict_ctor = env->GetMethodID(dict_class, "<init>", "()V");
    CHECK_JAVA_EXC(env);
    if (dict_ctor == NULL)
    {
        LOG("no dict_ctor");
        return;
    }

    dict_list_ctor = env->GetMethodID(dict_list_class, "<init>", "()V");
    CHECK_JAVA_EXC(env);
    if (dict_list_ctor == NULL)
    {
        LOG("no dict_list_ctor");
        return;
    }

    dict_put_dictobj = env->GetMethodID(dict_class, "put", "([BLcom/appy/DictObj;)V");
    CHECK_JAVA_EXC(env);
    if (dict_put_dictobj == NULL)
    {
        LOG("no dict_put_dictobj");
        return;
    }

    dict_list_add_dictobj = env->GetMethodID(dict_list_class, "add", "(Lcom/appy/DictObj;)V");
    CHECK_JAVA_EXC(env);
    if (dict_list_add_dictobj == NULL)
    {
        LOG("no dict_list_add_dictobj");
        return;
    }

    dict_put_boolean = env->GetMethodID(dict_class, "put", "([BZ)V");
    CHECK_JAVA_EXC(env);
    if (dict_put_boolean == NULL)
    {
        LOG("no dict_put_boolean");
        return;
    }
    dict_put_int = env->GetMethodID(dict_class, "put", "([BI)V");
    CHECK_JAVA_EXC(env);
    if (dict_put_int == NULL)
    {
        LOG("no dict_put_int");
        return;
    }
    dict_put_long = env->GetMethodID(dict_class, "put", "([BJ)V");
    CHECK_JAVA_EXC(env);
    if (dict_put_long == NULL)
    {
        LOG("no dict_put_long");
        return;
    }
    dict_put_double = env->GetMethodID(dict_class, "put", "([BD)V");
    CHECK_JAVA_EXC(env);
    if (dict_put_double == NULL)
    {
        LOG("no dict_put_double");
        return;
    }
    dict_put_string = env->GetMethodID(dict_class, "put", "([B[B)V");
    CHECK_JAVA_EXC(env);
    if (dict_put_string == NULL)
    {
        LOG("no dict_put_string");
        return;
    }

    dict_list_array = env->GetMethodID(dict_list_class, "array", "()[Ljava/lang/Object;");
    CHECK_JAVA_EXC(env);
    if (dict_list_array == NULL)
    {
        LOG("no dict_list_array");
        return;
    }

    dict_entries = env->GetMethodID(dict_class, "entries", "()[Lcom/appy/DictObj$Entry;");
    CHECK_JAVA_EXC(env);
    if (dict_entries == NULL)
    {
        LOG("no dict_entries");
        return;
    }

    dict_entry_key = env->GetFieldID(dict_entry_class, "key", "Ljava/lang/String;");
    CHECK_JAVA_EXC(env);
    if (dict_entry_key == NULL)
    {
        LOG("no dict_entry_key");
        return;
    }
    dict_entry_value = env->GetFieldID(dict_entry_class, "value", "Ljava/lang/Object;");
    CHECK_JAVA_EXC(env);
    if (dict_entry_value == NULL)
    {
        LOG("no dict_entry_value");
        return;
    }

    dict_fields_inited = true;
}

static jobject build_java_dict_object(PyObject * obj, JNIEnv * env)
{
    PyObject *key_obj, *value_obj;
    Py_ssize_t pos = 0;
    Py_ssize_t cstr_size = 0;

    if (PyDict_Check(obj))
    {
        jobject javadict = env->NewObject(dict_class, dict_ctor);
        if (env->ExceptionCheck())
        {
            return NULL;
        }
        if (javadict == NULL)
        {
            return NULL;
        }

        while (PyDict_Next(obj, &pos, &key_obj, &value_obj))
        {
            const char *key_cstr = PyUnicode_AsUTF8AndSize(key_obj, &cstr_size); //TODO multithread?
            if (key_cstr == NULL)
            {
                env->DeleteLocalRef(javadict);
                return NULL;
            }

            jbyteArray keyarr = env->NewByteArray(cstr_size);
            if (keyarr == NULL)
            {
                env->DeleteLocalRef(javadict);
                return NULL;
            }

            jbyte * key_bytes = env->GetByteArrayElements(keyarr, NULL);
            if (key_bytes == NULL)
            {
                env->DeleteLocalRef(keyarr);
                env->DeleteLocalRef(javadict);
                return NULL;
            }

            memcpy(key_bytes, key_cstr, cstr_size);
            env->ReleaseByteArrayElements(keyarr, key_bytes, 0);

            if (env->ExceptionCheck())
            {
                env->DeleteLocalRef(keyarr);
                env->DeleteLocalRef(javadict);
                return NULL;
            }

            if (PyDict_Check(value_obj) || PyList_Check(value_obj) || PyTuple_Check(value_obj))
            {
                jobject val = build_java_dict_object(value_obj, env);
                if (val == NULL)
                {
                    env->DeleteLocalRef(keyarr);
                    env->DeleteLocalRef(javadict);
                    return NULL;
                }
                env->CallVoidMethod(javadict, dict_put_dictobj, keyarr, val);
                env->DeleteLocalRef(val);
                env->DeleteLocalRef(keyarr);
            }
            else if (PyBool_Check(value_obj))
            {
                bool val = value_obj == Py_True;
                env->CallVoidMethod(javadict, dict_put_boolean, keyarr, val);
                env->DeleteLocalRef(keyarr);
            }
            else if (PyLong_Check(value_obj))
            {
                long val = PyLong_AsLong(value_obj);
                if (PyErr_Occurred())
                {
                    env->DeleteLocalRef(keyarr);
                    env->DeleteLocalRef(javadict);
                    return NULL;
                }
                env->CallVoidMethod(javadict, dict_put_long, keyarr, val);
                env->DeleteLocalRef(keyarr);
            }
            else if (PyUnicode_Check(value_obj))
            {
                const char *value_cstr = PyUnicode_AsUTF8AndSize(value_obj, &cstr_size); //TODO multithread?
                if (value_cstr == NULL)
                {
                    env->DeleteLocalRef(keyarr);
                    env->DeleteLocalRef(javadict);
                    return NULL;
                }

                jbyteArray valuearr = env->NewByteArray(cstr_size);
                if (valuearr == NULL)
                {
                    env->DeleteLocalRef(keyarr);
                    env->DeleteLocalRef(javadict);
                    return NULL;
                }

                jbyte * value_bytes = env->GetByteArrayElements(valuearr, NULL);
                if (value_bytes == NULL)
                {
                    env->DeleteLocalRef(valuearr);
                    env->DeleteLocalRef(keyarr);
                    env->DeleteLocalRef(javadict);
                    return NULL;
                }

                memcpy(value_bytes, value_cstr, cstr_size);
                env->ReleaseByteArrayElements(valuearr, value_bytes, 0);

                if (env->ExceptionCheck())
                {
                    env->DeleteLocalRef(valuearr);
                    env->DeleteLocalRef(keyarr);
                    env->DeleteLocalRef(javadict);
                    return NULL;
                }

                env->CallVoidMethod(javadict, dict_put_string, keyarr, valuearr);
                env->DeleteLocalRef(valuearr);
                env->DeleteLocalRef(keyarr);
            }
            else if (PyFloat_Check(value_obj))
            {
                double val = PyFloat_AsDouble(value_obj);
                if (PyErr_Occurred())
                {
                    env->DeleteLocalRef(keyarr);
                    env->DeleteLocalRef(javadict);
                    return NULL;
                }
                env->CallVoidMethod(javadict, dict_put_double, keyarr, val);
                env->DeleteLocalRef(keyarr);
            }
            else if (value_obj == Py_None)
            {
                env->CallVoidMethod(javadict, dict_put_dictobj, keyarr, (jobject)NULL);
                env->DeleteLocalRef(keyarr);
            }
            else
            {
                env->DeleteLocalRef(keyarr);
            }

            if (env->ExceptionCheck())
            {
                env->DeleteLocalRef(javadict);
                return NULL;
            }
        }

        return javadict;
    }
    else if (PyList_Check(obj) || PyTuple_Check(obj))
    {
        bool is_list = PyList_Check(obj);
        jobject javalist = env->NewObject(dict_list_class, dict_list_ctor);
        if (env->ExceptionCheck())
        {
            return NULL;
        }
        if (javalist == NULL)
        {
            return NULL;
        }

        unsigned int size = is_list ? PyList_Size(obj) : PyTuple_Size(obj);
        if (PyErr_Occurred())
        {
            env->DeleteLocalRef(javalist);
            return NULL;
        }
        for (unsigned int i = 0; i < size; i++)
        {
            PyObject * item = is_list ? PyList_GetItem(obj, i) : PyTuple_GetItem(obj, i);
            if (item == NULL || PyErr_Occurred())
            {
                env->DeleteLocalRef(javalist);
                return NULL;
            }
            if (PyDict_Check(item) || PyList_Check(item) || PyTuple_Check(item))
            {
                jobject val = build_java_dict_object(item, env);
                if (val == NULL)
                {
                    env->DeleteLocalRef(javalist);
                    return NULL;
                }
                env->CallVoidMethod(javalist, dict_list_add_dictobj, val);
                env->DeleteLocalRef(val);
            }
            else if (item == Py_None)
            {
                env->CallVoidMethod(javalist, dict_list_add_dictobj, (jobject)NULL);
            }
            if (env->ExceptionCheck())
            {
                env->DeleteLocalRef(javalist);
                return NULL;
            }
        }

        return javalist;
    }

    return NULL;
}

static PyObject * build_java_dict(PyObject * self, PyObject * args)
{
    try
    {
        PyObject * obj = NULL;
        if (!PyArg_ParseTuple(args, "O", &obj))
        {
            return NULL;
        }

        if (obj == NULL || obj == Py_None)
        {
            PyErr_SetString(PyExc_TypeError, "object is None");
            return NULL;
        }

        //not using GET_JNI_ENV because build_java_dict_object allocates an unknown amount of local refs
        JNIEnv * env = _get_jni_env();

        init_dict_fields(env);
        if (!dict_fields_inited)
        {
            PyErr_SetString(PyExc_TypeError, "dict fields init failed");
            return NULL;
        }

        jobject local_jobj = build_java_dict_object(obj, env);
        CHECK_JAVA_EXC(env);
        if (local_jobj == NULL)
        {
            PyErr_SetString(PyExc_TypeError, "failed to build java dict object");
            return NULL;
        }

        return PyLong_FromUnsignedLong((unsigned long)make_global_java_ref(env, local_jobj));
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static PyObject * build_python_dict(jobject obj, JNIEnv * env)
{
    if (env->IsInstanceOf(obj, dict_class))
    {
        jobjectArray entryarray = (jobjectArray)env->CallObjectMethod(obj, dict_entries);
        if (env->ExceptionCheck() || entryarray == NULL)
        {
            return NULL;
        }

        int size = env->GetArrayLength(entryarray);
        PyObject * pyobj = PyDict_New();
        if (pyobj == NULL)
        {
            env->DeleteLocalRef(entryarray);
            return NULL;
        }
        for (unsigned int i = 0; i < size; i++)
        {
            jobject entry = env->GetObjectArrayElement(entryarray, i);
            if (env->ExceptionCheck() || entry == NULL)
            {
                Py_DECREF(pyobj);
                env->DeleteLocalRef(entryarray);
                return NULL;
            }

            jstring key = (jstring)env->GetObjectField(entry, dict_entry_key);
            if (env->ExceptionCheck() || key == NULL)
            {
                env->DeleteLocalRef(entry);
                Py_DECREF(pyobj);
                env->DeleteLocalRef(entryarray);
                return NULL;
            }
            jstring value = (jstring)env->GetObjectField(entry, dict_entry_value);
            if (env->ExceptionCheck())
            {
                env->DeleteLocalRef(key);
                env->DeleteLocalRef(entry);
                Py_DECREF(pyobj);
                env->DeleteLocalRef(entryarray);
                return NULL;
            }
            env->DeleteLocalRef(entry);

            int keylen = env->GetStringUTFLength(key);
            const char * key_mem = env->GetStringUTFChars(key, NULL);
            if (key_mem == NULL)
            {
                env->DeleteLocalRef(value);
                env->DeleteLocalRef(key);
                Py_DECREF(pyobj);
                env->DeleteLocalRef(entryarray);
                return NULL;
            }

            PyObject * pykey = PyUnicode_FromStringAndSize(key_mem, keylen);
            env->ReleaseStringUTFChars(key, key_mem);
            env->DeleteLocalRef(key);
            if (pykey == NULL)
            {
                env->DeleteLocalRef(value);
                Py_DECREF(pyobj);
                env->DeleteLocalRef(entryarray);
                return NULL;
            }

            if (value == NULL)
            {
                int err = PyDict_SetItem(pyobj, pykey, Py_None);
                Py_DECREF(pykey);
                if (err != 0)
                {
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }
            }
            else if (env->IsInstanceOf(value, dict_class) || env->IsInstanceOf(value, dict_list_class))
            {
                PyObject * pyvalue = build_python_dict(value, env);
                env->DeleteLocalRef(value);
                if (pyvalue == NULL)
                {
                    Py_DECREF(pykey);
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }

                int err = PyDict_SetItem(pyobj, pykey, pyvalue);
                Py_DECREF(pykey);
                Py_DECREF(pyvalue);
                if (err != 0)
                {
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }
            }
            else if (env->IsInstanceOf(value, boolean_class))
            {
                bool v = env->CallBooleanMethod(value, booleanValueMethod);
                env->DeleteLocalRef(value);
                if (env->ExceptionCheck())
                {
                    Py_DECREF(pykey);
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }

                int err = PyDict_SetItem(pyobj, pykey, v ? Py_True : Py_False);
                Py_DECREF(pykey);
                if (err != 0)
                {
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }
            }
            else if (env->IsInstanceOf(value, double_class) || env->IsInstanceOf(value, float_class))
            {
                double v = env->IsInstanceOf(value, double_class) ? env->CallDoubleMethod(value, doubleValueMethod) :
                            env->CallFloatMethod(value, floatValueMethod);
                env->DeleteLocalRef(value);
                if (env->ExceptionCheck())
                {
                    Py_DECREF(pykey);
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }

                PyObject * pyvalue = PyFloat_FromDouble(v);
                if (pyvalue == NULL)
                {
                    Py_DECREF(pykey);
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }

                int err = PyDict_SetItem(pyobj, pykey, pyvalue);
                Py_DECREF(pykey);
                Py_DECREF(pyvalue);
                if (err != 0)
                {
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }
            }
            else if (env->IsInstanceOf(value, short_class) || env->IsInstanceOf(value, integer_class) || env->IsInstanceOf(value, long_class))
            {
                long v = env->IsInstanceOf(value, short_class) ? env->CallShortMethod(value, shortValueMethod) :
                         (env->IsInstanceOf(value, integer_class) ? env->CallIntMethod(value, intValueMethod) :
                         env->CallLongMethod(value, longValueMethod));
                env->DeleteLocalRef(value);
                if (env->ExceptionCheck())
                {
                    Py_DECREF(pykey);
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }

                PyObject * pyvalue = PyLong_FromLong(v);
                if (pyvalue == NULL)
                {
                    Py_DECREF(pykey);
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }

                int err = PyDict_SetItem(pyobj, pykey, pyvalue);
                Py_DECREF(pykey);
                Py_DECREF(pyvalue);
                if (err != 0)
                {
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }
            }
            else if (env->IsInstanceOf(value, string_class))
            {
                int valuelen = env->GetStringUTFLength(value);
                const char * value_mem = env->GetStringUTFChars(value, NULL);
                if (value_mem == NULL)
                {
                    env->DeleteLocalRef(value);
                    Py_DECREF(pykey);
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }

                PyObject * pyvalue = PyUnicode_FromStringAndSize(value_mem, valuelen);
                env->ReleaseStringUTFChars(value, value_mem);
                env->DeleteLocalRef(value);
                if (pyvalue == NULL)
                {
                    Py_DECREF(pykey);
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }

                int err = PyDict_SetItem(pyobj, pykey, pyvalue);
                Py_DECREF(pykey);
                Py_DECREF(pyvalue);
                if (err != 0)
                {
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }
            }
            else
            {
                //unknown object
                env->DeleteLocalRef(value);
                int err = PyDict_SetItem(pyobj, pykey, Py_None);
                Py_DECREF(pykey);
                if (err != 0)
                {
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }
            }
        }
        env->DeleteLocalRef(entryarray);
        return pyobj;
    }
    else if (env->IsInstanceOf(obj, dict_list_class))
    {
        jobjectArray entryarray = (jobjectArray)env->CallObjectMethod(obj, dict_list_array);
        if (env->ExceptionCheck() || entryarray == NULL)
        {
            return NULL;
        }
        int size = env->GetArrayLength(entryarray);

        PyObject * pyobj = PyList_New(size);
        if (pyobj == NULL)
        {
            env->DeleteLocalRef(entryarray);
            return NULL;
        }

        for (unsigned int i = 0; i < size; i++)
        {
            jobject item = env->GetObjectArrayElement(entryarray, i);
            if (env->ExceptionCheck())
            {
                Py_DECREF(pyobj);
                env->DeleteLocalRef(entryarray);
                return NULL;
            }

            if (item == NULL)
            {
                PyList_SetItem(pyobj, i, Py_None);
            }
            else if (env->IsInstanceOf(item, dict_class) || env->IsInstanceOf(item, dict_list_class))
            {
                PyObject  * pyitem = build_python_dict(item, env);
                env->DeleteLocalRef(item);
                if (pyitem == NULL)
                {
                    Py_DECREF(pyobj);
                    env->DeleteLocalRef(entryarray);
                    return NULL;
                }
                PyList_SetItem(pyobj, i, pyitem);
            }
        }
        return pyobj;
    }
}

static PyObject * build_python_dict_from_java(PyObject * self, PyObject * args)
{
    try
    {
        unsigned long ref_lng = 0;
        if (!PyArg_ParseTuple(args, "k", &ref_lng))
        {
            return NULL;
        }

        if (ref_lng == 0)
        {
            PyErr_SetString(PyExc_TypeError, "object is Null");
            return NULL;
        }

        //not using GET_JNI_ENV because build_python_dict allocats an unknown amount of local refs
        JNIEnv * env = _get_jni_env();

        init_dict_fields(env);
        if (!dict_fields_inited)
        {
            PyErr_SetString(PyExc_TypeError, "json fields init failed");
            return NULL;
        }

        PyObject * pyobj = build_python_dict((jobject)ref_lng, env);
        if (pyobj == NULL)
        {
            PyErr_SetString(PyExc_TypeError, "failed to build python dict");
            return NULL;
        }
        if (env->ExceptionCheck())
        {
            Py_DECREF(pyobj);
            CHECK_JAVA_EXC(env);
        }

        return pyobj;
    }
    catch (java_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    catch (jni_exception & e)
    {
        PyErr_SetString(PyExc_RuntimeError, e.what());
    }
    return NULL;
}

static bool python_initialized = false;

extern "C" JNIEXPORT jbyteArray JNICALL Java_com_appy_DictObj_DictObjtojson(JNIEnv * env, jclass clazz, jobject Dict)
{
    try
    {
        if (!python_initialized)
        {
            env->ThrowNew(python_exception_class, "Python uninitialized");
            return NULL;
        }

        if (Dict == NULL)
        {
            return NULL;
        }

        PyGILState_STATE gstate;
        gstate = PyGILState_Ensure();

        scope_guards guards;
        guards += [&gstate] { PyGILState_Release(gstate); };

        static PyObject * dumps_func = NULL;
        if (dumps_func == NULL)
        {
            PyObject * scope = PyDict_New();
            if (scope == NULL)
            {
                return NULL;
            }
            dumps_func = PyRun_String("exec('import json') or json.dumps", Py_eval_input, scope, scope);
            Py_DECREF(scope);
            if (PyErr_Occurred())
            {
                PyErr_Clear();
                env->ThrowNew(python_exception_class, "Exception in dumps_func");
                return NULL;
            }
        }

        if (dumps_func == NULL)
        {
            return NULL;
        }

        init_dict_fields(env);
        if (!dict_fields_inited)
        {
            return NULL;
        }

        PyObject * pyobj = build_python_dict(Dict, env);
        if (pyobj == NULL || PyErr_Occurred())
        {
            PyErr_Clear();
            env->ThrowNew(python_exception_class, "Exception in build_python_dict");
            return NULL;
        }

        PyObject * ser = PyObject_CallOneArg(dumps_func, pyobj);
        Py_DECREF(pyobj);
        if (PyErr_Occurred() || ser == NULL)
        {
            PyObject * type = NULL, * value = NULL, * traceback = NULL;
            PyErr_Fetch(&type, &value, &traceback);

            if (value != NULL)
            {
                PyObject *str = PyObject_Str(value);
                if (str != NULL)
                {
                    Py_ssize_t cstr_size = 0;
                    const char *python_cstr = PyUnicode_AsUTF8AndSize(str, &cstr_size);
                    env->ThrowNew(python_exception_class, python_cstr);
                    Py_DECREF(str);
                }
                else
                {
                    PyErr_Clear();
                    env->ThrowNew(python_exception_class, "Exception in str call");
                }
            }
            else
            {
                PyErr_Clear();
                env->ThrowNew(python_exception_class, "Exception in dumps call");
            }
            Py_XDECREF(type);
            Py_XDECREF(value);
            Py_XDECREF(traceback);
            return NULL;
        }

        Py_ssize_t size = 0;
        const char * cstr = PyUnicode_AsUTF8AndSize(ser, &size);
        if (cstr == NULL)
        {
            Py_DECREF(ser);
            env->ThrowNew(python_exception_class, "Exception in PyUnicode_AsUTF8AndSize");
            return NULL;
        }

        jbyteArray arr = env->NewByteArray(size);
        if (arr == NULL)
        {
            Py_DECREF(ser);
            return NULL;
        }

        jbyte * arr_mem = env->GetByteArrayElements(arr, NULL);
        if (arr_mem == NULL)
        {
            env->DeleteLocalRef(arr);
            Py_DECREF(ser);
            return NULL;
        }

        memcpy(arr_mem, cstr, size);
        env->ReleaseByteArrayElements(arr, arr_mem, 0);

        Py_DECREF(ser);

        return arr;
    }
    catch (java_exception & e)
    {
        env->ThrowNew(python_exception_class, e.what());
    }
    catch (jni_exception & e)
    {
        env->ThrowNew(python_exception_class, e.what());
    }
    catch (std::exception & e)
    {
        LOG("exception: %s %s", typeid(e).name(), e.what());
        env->ThrowNew(python_exception_class, e.what());
    }
    catch (...)
    {
        env->ThrowNew(python_exception_class, "exception was thrown from DictObjtojson");
    }
    return NULL;
}

extern "C" JNIEXPORT jobject JNICALL Java_com_appy_DictObj_jsontoDictObj(JNIEnv * env, jclass clazz, jbyteArray json)
{
    try
    {
        if (!python_initialized)
        {
            env->ThrowNew(python_exception_class, "Python uninitialized");
            return NULL;
        }

        if (json == NULL)
        {
            return NULL;
        }

        PyGILState_STATE gstate;
        gstate = PyGILState_Ensure();

        scope_guards guards;
        guards += [&gstate] { PyGILState_Release(gstate); };

        static PyObject * loads_func = NULL;
        if (loads_func == NULL)
        {
            PyObject * scope = PyDict_New();
            if (scope == NULL)
            {
                return NULL;
            }

            loads_func = PyRun_String("exec('import json') or json.loads", Py_eval_input, scope, scope);
            Py_DECREF(scope);
            if (PyErr_Occurred())
            {
                PyErr_Clear();
                env->ThrowNew(python_exception_class, "Exception in loads_func");
                return NULL;
            }
        }

        if (loads_func == NULL)
        {
            return NULL;
        }

        init_dict_fields(env);
        if (!dict_fields_inited)
        {
            return NULL;
        }

        int json_size = env->GetArrayLength(json);
        jbyte * json_mem = env->GetByteArrayElements(json, NULL);
        if (json_mem == NULL)
        {
            return NULL;
        }

        PyObject * pystr = PyUnicode_FromStringAndSize((const char *)json_mem, json_size);
        env->ReleaseByteArrayElements(json, json_mem, 0);
        if (pystr == NULL)
        {
            PyErr_Clear();
            env->ThrowNew(python_exception_class, "Exception in PyUnicode_FromStringAndSize");
            return NULL;
        }

        PyObject * deser = PyObject_CallOneArg(loads_func, pystr);
        Py_DECREF(pystr);
        if (PyErr_Occurred() || deser == NULL)
        {
            PyObject * type = NULL, * value = NULL, * traceback = NULL;
            PyErr_Fetch(&type, &value, &traceback);

            if (value != NULL)
            {
                PyObject *str = PyObject_Str(value);
                if (str != NULL)
                {
                    Py_ssize_t cstr_size = 0;
                    const char *python_cstr = PyUnicode_AsUTF8AndSize(str, &cstr_size);
                    env->ThrowNew(python_exception_class, python_cstr);
                    Py_DECREF(str);
                }
                else
                {
                    PyErr_Clear();
                    env->ThrowNew(python_exception_class, "Exception in str call");
                }
            }
            else
            {
                PyErr_Clear();
                env->ThrowNew(python_exception_class, "Exception in loads call");
            }
            Py_XDECREF(type);
            Py_XDECREF(value);
            Py_XDECREF(traceback);
            return NULL;
        }

        jobject obj = build_java_dict_object(deser, env);
        Py_DECREF(deser);
        if (obj == NULL || PyErr_Occurred())
        {
            PyErr_Clear();
            env->ThrowNew(python_exception_class, "Exception in build_java_dict_object");
            return NULL;
        }

        return obj;
    }
    catch (java_exception & e)
    {
        env->ThrowNew(python_exception_class, e.what());
    }
    catch (jni_exception & e)
    {
        env->ThrowNew(python_exception_class, e.what());
    }
    catch (std::exception & e)
    {
        LOG("exception: %s %s", typeid(e).name(), e.what());
        env->ThrowNew(python_exception_class, e.what());
    }
    catch (...)
    {
        env->ThrowNew(python_exception_class, "exception was thrown from jsontoDictObj");
    }
    return NULL;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_appy_Widget_pythonCall(JNIEnv * env, jclass clazz, jobjectArray args)
{
    try
    {
        if (!python_initialized)
        {
            env->ThrowNew(python_exception_class, "Python uninitialized");
            return NULL;
        }

        populate_common_java_objects(env);

        if (callback == NULL)
        {
            env->ThrowNew(python_exception_class, "No callback defined");
            return NULL;
        }

        PyGILState_STATE gstate;
        gstate = PyGILState_Ensure();

        scope_guards guards;
        guards += [&gstate] { PyGILState_Release(gstate); };

        jobject glob = NULL;
        if (args != NULL)
        {
            glob = env->NewGlobalRef(args);
            if (glob == NULL)
            {
                throw jni_exception("failed to create global ref");
            }
        }
        PyObject * arg = Py_BuildValue("(k)", (unsigned long) glob);

        if (arg == NULL)
        {
            PyErr_Clear();
            env->ThrowNew(python_exception_class, "build value failed");
            return NULL;
        }

        PyObject * result = PyObject_CallObject(callback, arg);
        Py_XDECREF(arg);
        if (result == NULL)
        {
            PyObject * type = NULL, * value = NULL, * traceback = NULL;
            PyErr_Fetch(&type, &value, &traceback);
            const char * default_msg = "Unknown python exception";
            char * python_msg = NULL;

            if (value != NULL)
            {
                PyObject * str = PyObject_Str(value);
                if (str != NULL)
                {
                    Py_ssize_t cstr_size = 0;
                    const char *python_cstr = PyUnicode_AsUTF8AndSize(str, &cstr_size);
                    if (python_cstr != NULL)
                    {
                        python_msg = new char[cstr_size + 1];
                        for (unsigned int i = 0; i < cstr_size; i++)
                        {
                            python_msg[i] = python_cstr[i] < 0x80 ? python_cstr[i] : '_';
                        }
                        python_msg[cstr_size] = 0;
                    }
                }
                else
                {
                    PyErr_Clear();
                }
                Py_XDECREF(str);
            }
            Py_XDECREF(type);
            Py_XDECREF(value);
            Py_XDECREF(traceback);
            env->ThrowNew(python_exception_class, python_msg != NULL ? python_msg : default_msg);
            delete[] python_msg;
            return NULL;
        }

        if (!PyLong_Check(result))
        {
            Py_DECREF(result);
            env->ThrowNew(python_exception_class, "callback result is invalid");
            return NULL;
        }

        jobject global_ref = (jobject) PyLong_AsUnsignedLong(result);
        Py_DECREF(result);

        if (global_ref != NULL)
        {
            jobject local = env->NewLocalRef(global_ref);
            env->DeleteGlobalRef(global_ref);
            return local;
        }
    }
    catch (java_exception & e)
    {
        env->ThrowNew(python_exception_class, e.what());
    }
    catch (jni_exception & e)
    {
        env->ThrowNew(python_exception_class, e.what());
    }
    catch (std::exception & e)
    {
        LOG("exception: %s %s", typeid(e).name(), e.what());
        env->ThrowNew(python_exception_class, e.what());
    }
    catch (...)
    {
        env->ThrowNew(python_exception_class, "exception was thrown from pythonCall");
    }
    return NULL;
}

static PyObject * logcat_write(PyObject * self, PyObject * args)
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
    return Py_BuildValue("i", ret);
}

static PyMethodDef native_appy_methods[] = {
        {"call_jni_object_functions",       call_jni_object_functions,       METH_VARARGS, "Interacts with java objects"},
        {"get_methodid",                    get_methodid,                    METH_VARARGS, "Finds a java method id"},
        {"get_fieldid",                     get_fieldid,                     METH_VARARGS, "Finds a java field id"},
        {"find_class",                      find_class,                      METH_VARARGS, "Finds a java class"},
        {"get_object_class",                get_object_class,                METH_VARARGS, "Retrieves the object's class"},
        {"python_to_packed_java_primitive", python_to_packed_java_primitive, METH_VARARGS, "Turns a python object into a corresponding java object"},
        {"packed_java_primitive_to_python", packed_java_primitive_to_python, METH_VARARGS, "Turns a java object into the corresponding python object"},
        {"python_to_unpacked_jvalue",       python_to_unpacked_jvalue,       METH_VARARGS, "Turns a python object into a corresponding jvalue"},
        {"call_jni_array_functions",        call_jni_array_functions,        METH_VARARGS, "Interacts with java arrays"},
        {"unpack_primitive_class",          unpack_primitive_class,          METH_VARARGS, "unpacks a java primitive class to a java type (Integer -> int)"},
        {"delete_global_ref",               delete_global_ref,               METH_VARARGS, "Delete a java reference"},
        {"new_global_ref",                  new_global_ref,                  METH_VARARGS, "created a new global reference (should be used only on callbacks)"},
        {"inspect_class",                   inspect_class,                   METH_VARARGS, "inspects a java class, returns if its primitive, array, etc"},
        {"inspect_class_content",           inspect_class_content,           METH_VARARGS, "returns a list of all accessible fields, methods and constructors in class"},
        {"python_str_to_jstring",           python_str_to_jstring,           METH_VARARGS, "makes jstring from str"},
        {"jstring_to_python_str",           jstring_to_python_str,           METH_VARARGS, "makes str from jstring"},
        {"jclass_to_array_of_jclass",       jclass_to_array_of_jclass,       METH_VARARGS, "gets the class which is the array of a given class"},
        {"set_python_callback",             set_python_callback,             METH_VARARGS, "sets python callback to be called from java"},
        {"create_java_interface",           create_java_interface,           METH_VARARGS, "creates java interface that implements given classes"},
        {"get_java_init_arg",               get_java_init_arg,               METH_VARARGS, "gets the arg passed from java on init"},
        {"check_is_jclass_castable",        check_is_jclass_castable,        METH_VARARGS, "checks whether an object can be cast into class"},
        {"logcat_write",                    logcat_write,                    METH_VARARGS, "writes to logcat"},
        {"build_java_dict",                 build_java_dict,                 METH_VARARGS, "builds json object from dict, list or tuple"},
        {"build_python_dict_from_java",     build_python_dict_from_java,     METH_VARARGS, "builds a python dict from json object or json array"},
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

//we do this so that we control the path our libraries are loaded from (for example, get our own libssl)
static void preload_shared_libraries(const std::string & dirpath)
{
    std::deque<std::string> unloaded;
    DIR * dir = opendir(dirpath.c_str());
    if (dir != NULL)
    {
        struct dirent * ent = NULL;

        while ((ent = readdir(dir)) != NULL)
        {
            // no links
            if (ent->d_type == DT_REG)
            {
                std::string path = dirpath + "/" + ent->d_name;
                unloaded.push_back(path);
            }
        }
        closedir(dir);
    }

    unsigned int prevsize;
    do
    {
        prevsize = unloaded.size();
        auto it = unloaded.begin();
        while (it != unloaded.end())
        {
            void * handle = dlopen(it->c_str(), RTLD_LAZY | RTLD_GLOBAL);
            if (handle == NULL)
            {
                LOG("could not load library: %s (%s)", it->c_str(), dlerror());
                it++;
            }
            else
            {
                LOG("pre loaded library: %s (%p)", it->c_str(), handle);
                it = unloaded.erase(it);
            }
            //not dlclosing but i really want to.
        }
    } while (unloaded.size() != prevsize); //keep going if the list gets smaller

    if (unloaded.size() != 0)
    {
        LOG("could not load %lu libraries", unloaded.size());
    }
}

int stdout_to_file(const char * path)
{
    setvbuf(stdout, 0, _IOLBF, 0);
    setvbuf(stderr, 0, _IONBF, 0);

    int fd = open(path, O_WRONLY | O_APPEND | O_CREAT, 0644);
    if (fd == -1)
    {
        LOG("cannot open logger");
        return -1;
    }

    dup2(fd, 1);
    dup2(fd, 2);
    return 0;
}


extern "C" JNIEXPORT void JNICALL
Java_com_appy_Widget_pythonInit(JNIEnv * env, jclass clazz, jstring j_pythonhome,
                                jstring j_cachepath, jstring j_pythonlib, jstring j_scriptpath,
                                jstring j_nativepath, jobject j_arg)
{
    try
    {
        std::set_terminate([]() {
            if (auto exc = std::current_exception())
            {
                try
                {
                    std::rethrow_exception(exc);
                }
                catch (std::exception & e)
                {
                    LOG("unhandled exception: %s %s", typeid(e).name(), e.what());
                }
                catch (...)
                {
                    LOG("unhandled exception: %s",
                        __cxxabiv1::__cxa_current_exception_type()->name());
                }
            }
            abort();
        });

        LOG("python init");

        populate_common_java_objects(env);

        int ret = env->GetJavaVM(&vm);
        if (ret != 0)
        {
            env->ThrowNew(python_exception_class, "GetJavaVM failed");
            return;
        }

        auto pythonhome = jstring_to_stdstring(env, j_pythonhome);
        auto cachepath = jstring_to_stdstring(env, j_cachepath);
        auto pythonlib = jstring_to_stdstring(env, j_pythonlib);
        auto scriptpath = jstring_to_stdstring(env, j_scriptpath);
        auto nativepath = jstring_to_stdstring(env, j_nativepath);

        //stdout_to_file("/sdcard/Android/media/com.appy.widgets/stdout.txt");

        auto exepath = pythonhome + "/bin/python3";

        LOG("setting env");

        setenv("PYTHONHOME", pythonhome.c_str(), 1);
        setenv("HOME", pythonhome.c_str(), 1);
        setenv("SHELL", "/system/bin/sh", 1);
        setenv("TMP", cachepath.c_str(), 1);
        setenv("NATIVELIBS", nativepath.c_str(), 1);

        //LD_LIBRARY_PATH hack
        preload_shared_libraries(pythonhome + "/lib");
        //--------------------

        LOG("registering python module");
        ret = PyImport_AppendInittab("native_appy", PyInit_native_appy);
        if (ret == -1)
        {
            env->ThrowNew(python_exception_class, "PyImport_AppendInittab failed");
            return;
        }

        wchar_t * pythonexe_w = Py_DecodeLocale(exepath.c_str(), NULL);
        if (pythonexe_w == NULL)
        {
            env->ThrowNew(python_exception_class, "Py_DecodeLocale failed");
            return;
        }

        LOG("running python");
        Py_SetProgramName(pythonexe_w);
        Py_InitializeEx(0);

        if (j_arg != NULL)
        {
            if (g_java_arg == NULL)
            {
                g_java_arg = env->NewGlobalRef(j_arg);
            }
            if (g_java_arg == NULL)
            {
                env->ThrowNew(python_exception_class, "NewGlobalRef failed");
                return;
            }
        }

        FILE * fh = fopen(scriptpath.c_str(), "r");
        if (fh == NULL)
        {
            env->ThrowNew(python_exception_class, "fopen failed");
            return;
        }

        wchar_t * program = Py_DecodeLocale(scriptpath.c_str(), NULL);
        if (program == NULL)
        {
            env->ThrowNew(python_exception_class, "Py_DecodeLocale failed");
            return;
        }

        PySys_SetArgv(1, &program);

        LOG("executing init script");

        ret = PyRun_SimpleFileExFlags(fh, scriptpath.c_str(), 1, NULL);

        LOG("done executing init script");
        PyEval_InitThreads();

        //TODO not sure if this is ok
        PyThreadState * mainPyThread = PyEval_SaveThread();

        if (ret == -1)
        {
            env->ThrowNew(python_exception_class, "PyRun_SimpleFileExFlags failed");
            return;
        }

        LOG("python init done");
        python_initialized = true;
        return;
    }
    catch (java_exception & e)
    {
        env->ThrowNew(python_exception_class, e.what());
    }
    catch (jni_exception & e)
    {
        env->ThrowNew(python_exception_class, e.what());
    }
    catch (std::exception & e)
    {
        LOG("exception: %s %s", typeid(e).name(), e.what());
        env->ThrowNew(python_exception_class, e.what());
    }
    catch (...)
    {
        env->ThrowNew(python_exception_class, "exception was thrown from pythonInit");
    }
}
