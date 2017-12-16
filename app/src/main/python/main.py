import sys
import ctypes
import logcat

print('begin')

class void_p(ctypes.c_void_p):
    pass

class jboolean(ctypes.c_uint8):
    def __init__(self, b):
        super().__init__(1 if b else 0)

class jbyte(ctypes.c_int8):
    pass

class jchar(ctypes.c_uint16):
    pass

class jshort(ctypes.c_int16):
    pass

class jint(ctypes.c_int32):
    pass

class jlong(ctypes.c_uint64):
    pass

class jfloat(ctypes.c_float):
    pass

class jdouble(ctypes.c_double):
    pass

class jvalue(ctypes.Union):
    _fields_ = [('z', jboolean),
                ('b', jbyte),
                ('c', jchar),
                ('s', jshort),
                ('i', jint),
                ('j', jlong),
                ('f', jfloat),
                ('d', jdouble),
                ('l', void_p),]


native = ctypes.cdll.LoadLibrary('libnative.so')

find_class = getattr(native, "find_class")
find_class.argtypes = [ctypes.c_char_p]
find_class.restype = void_p

get_method = getattr(native, 'get_method')
get_method.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int, ctypes.POINTER(ctypes.c_void_p), ctypes.POINTER(ctypes.c_int)]
get_method.restype = void_p

call_static = getattr(native, 'call_static')
call_static.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.POINTER(jvalue), ctypes.c_int]
call_static.restype = jvalue

delete_global_ref = getattr(native, 'delete_global_ref')
delete_global_ref.argtypes = [ctypes.c_void_p]
delete_global_ref.restype = None

##############################################

class jclass:
    def __init__(self, path):
        self.path = path
        if isinstance(self.path, str):
            self.path = self.path.encode()
        if not isinstance(self.path, bytes):
            raise ValueError('only str or bytes, got {}'.format(type(self.path)))
        self.value = find_class(self.path)
        if not self.value:
            raise ValueError('no such class')

    def __del__(self):
        print('deleting {}'.format(self.path))
        if not self.value:
            delete_global_ref(self.value)


Long = jclass('java/lang/Long')
MainActivity = jclass('com/happy/MainActivity')

arg_types = [Long.value]
arg_types_c_arr = (ctypes.c_void_p * len(arg_types))(*arg_types)
real_types_c_arr = (ctypes.c_int * (len(arg_types) + 1))()

print(arg_types, arg_types_c_arr)

test_method = get_method(MainActivity.value, b"test", len(arg_types), arg_types_c_arr, real_types_c_arr)
if not test_method:
    raise ValueError('shit')

real_types = [real_types_c_arr[i] for i in range(len(arg_types) + 1)]

print(test_method, arg_types, '->', real_types)

args = [jvalue(j=31)]
args_c_arr = (jvalue * len(args))(*args)

result = call_static(MainActivity.value, test_method, args_c_arr, real_types[-1])
print(result)

print('end')
