#include "Python.h"
#include <stdio.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
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
jclass reflection = NULL;
jmethodID compatibleMethod = NULL;
jmethodID compatibleConstructor = NULL;

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

void check_java_exc(JNIEnv * env);
std::string get_string(JNIEnv * env, jstring str)
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
    java_exception(JNIEnv * env, jthrowable exc) : env(env), exception(exc)
    {
        try
        {
            jclass clazz = env->GetObjectClass(exception);
            jmethodID getMessage = env->GetMethodID(clazz,
                                                    "getMessage",
                                                    "()Ljava/lang/String;");
            check_java_exc(env);
            jstring msg = (jstring)env->CallObjectMethod(exc, getMessage);
            check_java_exc(env);
            message = get_string(env, msg);
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

void check_java_exc(JNIEnv * env)
{
    if(env->ExceptionCheck() == JNI_TRUE)
    {
        jthrowable local_exc = (jthrowable)env->ExceptionOccurred();
        env->ExceptionClear();
        jthrowable exc = (jthrowable)env->NewGlobalRef(local_exc);
        if(exc == NULL)
        {
            throw jni_exception("failed to global ref exception");
        }
        throw java_exception(env, exc);
    }
}

jvalue call_static_raw(JNIEnv * env, jclass clazz, jmethodID method, jvalue * values, int returnType)
{
    jvalue ret;

    switch(returnType)
    {
    case OBJECT:
    {
        ret.l = env->CallStaticObjectMethodA(clazz, method, values);
        break;
    }
    case BOOLEAN:
    {
        ret.z = env->CallStaticBooleanMethodA(clazz, method, values);
        break;
    }
    case BYTE:
    {
        ret.b = env->CallStaticByteMethodA(clazz, method, values);
        break;
    }
    case CHARACTER:
    {
        ret.c = env->CallStaticCharMethodA(clazz, method, values);
        break;
    }
    case SHORT:
    {
        ret.s = env->CallStaticShortMethodA(clazz, method, values);
        break;
    }
    case INTEGER:
    {
        ret.i = env->CallStaticIntMethodA(clazz, method, values);
        break;
    }
    case LONG:
    {
        ret.j = env->CallStaticLongMethodA(clazz, method, values);
        break;
    }
    case FLOAT:
    {
        ret.f = env->CallStaticFloatMethodA(clazz, method, values);
        break;
    }
    case DOUBLE:
    {
        ret.d = env->CallStaticDoubleMethodA(clazz, method, values);
        break;
    }
    case VOID:
    {
        env->CallStaticVoidMethodA(clazz, method, values);
        ret.l = NULL;
    }
    default:
    {
        //shit
        break;
    }
    }
    check_java_exc(env);
    return ret;
}

static void find_types(JNIEnv * env)
{
    if(class_class == NULL)
    {
        jclass local_class_class = env->FindClass("java/lang/Class");
        check_java_exc(env);
        class_class = (jclass)env->NewGlobalRef(local_class_class);
        if(class_class == NULL)
        {
            throw jni_exception("failed to create global reference to class Class");
        }
    }
    if(reflection == NULL)
    {
        jclass local_reflection = env->FindClass("com/happy/Reflection");
        check_java_exc(env);
        reflection = (jclass)env->NewGlobalRef(local_reflection);
        if(reflection == NULL)
        {
            throw jni_exception("failed to create global reference to class Reflection");
        }
    }
    if(compatibleMethod == NULL)
    {
        compatibleMethod = env->GetStaticMethodID(reflection, "getCompatibleMethod", "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)[Ljava/lang/Object;");
        check_java_exc(env);
    }
    if(compatibleConstructor == NULL)
    {
        compatibleConstructor = env->GetStaticMethodID(reflection, "getCompatibleConstructor", "(Ljava/lang/Class;[Ljava/lang/Class;)[Ljava/lang/Object;");
        check_java_exc(env);
    }
}

jmethodID get_method_raw(JNIEnv * env, jclass clazz, const char * method, int n, jobjectArray type_arr, int * outTypes)
{
    scope_guards onexit;
    env->PushLocalFrame(15);
    check_java_exc(env);
    onexit += [env]{ env->PopLocalFrame(NULL); };

    find_types(env);

    jobjectArray result = NULL;
    if(method == NULL || strlen(method) == 0)
    {
        result = (jobjectArray)env->CallStaticObjectMethod(reflection, compatibleConstructor, clazz, type_arr);
    }
    else
    {
        jstring method_str = env->NewStringUTF(method);
        check_java_exc(env);
        result = (jobjectArray)env->CallStaticObjectMethod(reflection, compatibleMethod, clazz, method_str, type_arr);
    }
    check_java_exc(env);

    jmethodID methodID = NULL;
    if(result != NULL)
    {
        methodID = env->FromReflectedMethod(env->GetObjectArrayElement(result, 0));

        jintArray realTypes = (jintArray)env->GetObjectArrayElement(result, 1);
        check_java_exc(env);
        jint * intarr = env->GetIntArrayElements(realTypes, NULL);
        if(intarr == NULL)
        {
            throw jni_exception("failed to GetIntArrayElements after finding compatible method");
        }
        for(size_t i = 0; i < n + 1; i++)
        {
            outTypes[i] = intarr[i];
        }
        env->ReleaseIntArrayElements(realTypes, intarr, JNI_ABORT);
    }
    return methodID;
}

static PyObject * call_static(PyObject *self, PyObject *args)
{
    try
    {
        static_assert(sizeof(jvalue) == sizeof(unsigned long long));

        unsigned long clazz = 0;
        unsigned long method = 0;
        PyObject * values = NULL;
        int return_type = 0;

        if (!PyArg_ParseTuple(args, "kkOi", &clazz, &method, &values, &return_type)) {
            return NULL;
        }

        if(!PyTuple_Check(values))
        {
            PyErr_SetString(PyExc_ValueError, "values must be a tuple");
            return NULL;
        }

        Py_ssize_t value_num = PyTuple_Size(values);
        jvalue * jvalues = new jvalue[value_num];
        scope_guards onexit;
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
        JNIEnv * env = get_env();
        jvalue ret = call_static_raw(env, (jclass)clazz, (jmethodID)method, jvalues, return_type);
        unsigned long long lng;
        memcpy(&lng, &ret, sizeof(ret));
        return PyLong_FromUnsignedLongLong(lng);
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
        check_java_exc(env);
        onexit += [env]{ env->PopLocalFrame(NULL); };
        find_types(env);

        jobjectArray type_arr = env->NewObjectArray(type_num, class_class, NULL);
        check_java_exc(env);

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
            check_java_exc(env); //TODO should?
        }

        int * out_types = new int[type_num + 1];
        onexit += [&out_types]{ if(out_types != NULL) { delete[] out_types; out_types = NULL; } };

        jmethodID res = get_method_raw(env, (jclass)clazz, method, type_num, type_arr, out_types);
        if(res == NULL)
        {
            PyErr_SetString(PyExc_ValueError, "Method not found");
            return NULL;
        }

        PyObject * out_types_tuple = PyTuple_New(type_num);
        if(out_types_tuple == NULL)
        {

        }
        for (Py_ssize_t i = 0; i < type_num; ++i) {
            PyTuple_SET_ITEM(out_types_tuple, i, PyLong_FromLong(out_types[i]));
        }
        return Py_BuildValue("kNk", (unsigned long)res, out_types_tuple, out_types[type_num]);
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

static PyObject * find_class(PyObject *self, PyObject *args)
{
    try
    {
        const char *clazz;
        if (!PyArg_ParseTuple(args, "s", &clazz))
        {
            return NULL;
        }

        JNIEnv * env = get_env();
        jclass local_clazz = env->FindClass(clazz);
        check_java_exc(env);
        jclass global_clazz = (jclass)env->NewGlobalRef(local_clazz);
        env->DeleteLocalRef(local_clazz);
        if(global_clazz == NULL)
        {
            throw jni_exception("failed to create global reference to class ?"); //TODO
        }
        return PyLong_FromLong((unsigned long)global_clazz);
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
        {"call_static",  call_static, METH_VARARGS, "Call a static java method"},
        {"get_method",  get_method, METH_VARARGS, "Finds a java method"},
        {"find_class",  find_class, METH_VARARGS, "Finds a java class"},
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
    LOG("hello!!!!!!!!!!!!!!!!!!!!!!!!!!");

    env->GetJavaVM(&vm);

    auto path = get_string(env, pythonpath);

    setenv("PYTHONHOME", path.c_str(), 1);
    setenv("LD_LIBRARY_PATH", (path + "/lib").c_str(), 1);

    PyImport_AppendInittab("native_hapy", PyInit_native_hapy);

    Py_InitializeEx(0);

    return 0;
}
