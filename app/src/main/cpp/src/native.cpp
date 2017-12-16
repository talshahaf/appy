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

static void * pythonhandle = NULL;
static std::add_pointer<decltype(PyErr_SetString)>::type pyErr_SetString = NULL;
static decltype(PyExc_ValueError) pyExc_ValueError = NULL;

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
    return cstr;
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

jmethodID get_method_raw(JNIEnv * env, jclass clazz, const char * method, int n, jclass * types, int * outTypes)
{
    scope_guards onexit;
    env->PushLocalFrame(15);
    check_java_exc(env);
    onexit += [env]{ env->PopLocalFrame(NULL); };

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

    jobjectArray type_arr = env->NewObjectArray(n, class_class, NULL);
    check_java_exc(env);

    for(size_t i = 0; i < n; i++)
    {
        env->SetObjectArrayElement(type_arr, i, (jobject)types[i]);
        check_java_exc(env); //TODO should?
    }

    jobjectArray result = NULL;
    if(method == NULL)
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

extern "C" JNIEXPORT PYTHON_CALL jvalue call_static(void * clazz, void * method, jvalue * values, int returnType)
{
    try
    {
        JNIEnv * env = get_env();
        return call_static_raw(env, (jclass)clazz, (jmethodID)method, values, returnType);
    }
    catch(java_exception & e)
    {
        LOG("got java exception in call_static");
        pyErr_SetString(pyExc_ValueError, e.what());
    }
    catch(jni_exception & e)
    {
        LOG("got jni exception in call_static");
        pyErr_SetString(pyExc_ValueError, e.what());
    }
    jvalue nul;
    nul.l = NULL;
    return nul;
}

extern "C" JNIEXPORT PYTHON_CALL void * get_method(void * clazz, const char * method, int n, void ** types, int * outTypes)
{
    try
    {
        JNIEnv * env = get_env();
        return get_method_raw(env, (jclass)clazz, method, n, (jclass *)types, outTypes);
    }
    catch(java_exception & e)
    {
        LOG("got java exception in get_method");
        pyErr_SetString(pyExc_ValueError, e.what());
    }
    catch(jni_exception & e)
    {
        LOG("got jni exception in get_method");
        pyErr_SetString(pyExc_ValueError, e.what());
    }
    return NULL;
}

extern "C" JNIEXPORT PYTHON_CALL void * find_class(const char * clazz)
{
    try
    {
        JNIEnv * env = get_env();
        jclass local_clazz = env->FindClass(clazz);
        check_java_exc(env);
        jclass global_clazz = (jclass)env->NewGlobalRef(local_clazz);
        env->DeleteLocalRef(local_clazz);
        if(global_clazz == NULL)
        {
            throw jni_exception("failed to create global reference to class ?"); //TODO
        }
        return global_clazz;
    }
    catch(java_exception & e)
    {
        LOG("got java exception in find_class: %p", pyExc_ValueError);
        pyErr_SetString(pyExc_ValueError, e.what());
    }
    catch(jni_exception & e)
    {
        LOG("got jni exception in find_class");
        pyErr_SetString(pyExc_ValueError, e.what());
    }
    return NULL;
}

extern "C" JNIEXPORT PYTHON_CALL void delete_global_ref(jobject ref)
{
    try
    {
        if(ref != NULL)
        {
            JNIEnv * env = get_env();
            env->DeleteGlobalRef(ref);
        }
    }
    catch(jni_exception & e)
    {
        LOG("got jni exception in delete_global_ref");
        pyErr_SetString(pyExc_ValueError, e.what());
    }
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

    auto decodeLocale = (std::add_pointer<decltype(Py_DecodeLocale)>::type)dlsym(pythonhandle, "Py_DecodeLocale");
    if(decodeLocale == NULL)
    {
        LOG("decodeLocale dlsym failed: %s", dlerror());
        return -1;
    }
    auto setArgv = (std::add_pointer<decltype(PySys_SetArgv)>::type)dlsym(pythonhandle, "PySys_SetArgv");
    if(setArgv == NULL)
    {
        LOG("setArgv dlsym failed: %s", dlerror());
        return -1;
    }
    pyErr_SetString = (decltype(pyErr_SetString))dlsym(pythonhandle, "PyErr_Format");
    if(pyErr_SetString == NULL)
    {
        LOG("pyErr_SetString dlsym failed: %s", dlerror());
        return -1;
    }
    pyExc_ValueError = *(std::add_pointer<decltype(pyExc_ValueError)>::type)dlsym(pythonhandle, "PyExc_ValueError");
    if(pyExc_ValueError == NULL)
    {
        LOG("pyExc_ValueError dlsym failed: %s", dlerror());
        return -1;
    }

    wchar_t *program = decodeLocale(path.c_str(), NULL);
    setArgv(1, &program);

    auto func = (std::add_pointer<decltype(PyRun_SimpleFileExFlags)>::type)dlsym(pythonhandle, "PyRun_SimpleFileExFlags");
    func(fh, path.c_str(), 1, NULL);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL Java_com_happy_MainActivity_pythonInit(JNIEnv * env, jclass clazz, jstring pythonpath)
{
        env->GetJavaVM(&vm);

    auto path = get_string(env, pythonpath);

    setenv("PYTHONHOME", path.c_str(), 1);
    setenv("LD_LIBRARY_PATH", (path + "/lib").c_str(), 1);

    pythonhandle = dlopen((path + "/lib/libpython3.6m.so.1.0").c_str(), RTLD_NOW);
    if(pythonhandle == NULL)
    {
        LOG("dlopen failed: %s", dlerror());
        return -1;
    }

    auto init = (std::add_pointer<decltype(Py_InitializeEx)>::type)dlsym(pythonhandle, "Py_InitializeEx");
    init(0);

    auto pyEval_InitThreads = (std::add_pointer<decltype(PyEval_InitThreads)>::type)dlsym(pythonhandle, "PyEval_InitThreads");
    pyEval_InitThreads();
    return 0;
}
