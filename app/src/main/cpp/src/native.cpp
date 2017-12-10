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
#include "native.h"

#define LOG(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG, "HAPY", fmt, ##__VA_ARGS__)

static void * pythonhandle = NULL;

std::string get_string(JNIEnv * env, jstring str)
{
    const char * cstr = env->GetStringUTFChars(str, NULL);
    const jint length = env->GetStringLength(str);
    std::string path(cstr, length);
    env->ReleaseStringUTFChars(str, cstr);
    return cstr;
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
    auto setArgv = (std::add_pointer<decltype(PySys_SetArgv)>::type)dlsym(pythonhandle, "PySys_SetArgv");
    wchar_t *program = decodeLocale(path.c_str(), NULL);
    setArgv(1, &program);

    auto func = (std::add_pointer<decltype(PyRun_SimpleFileExFlags)>::type)dlsym(pythonhandle, "PyRun_SimpleFileExFlags");
    func(fh, path.c_str(), 1, NULL);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL Java_com_happy_MainActivity_pythonInit(JNIEnv * env, jclass clazz, jstring pythonpath)
{
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

    return 0;
}