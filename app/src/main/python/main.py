import sys
import logcat
import native_hapy

class _jobject:
    _slots__ = []
    def __init__(self, handle, info):
        self.handle = handle
        if type(info) != str:
            raise ValueError('only strings in info')
        self.info = info

    def __repr__(self):
        return '{}'.format(self.info)

    def __del__(self):
        #TODO make sure this doesn't get called twice for the same handle
        if self.handle:
            print('deleting {}'.format(self))
            native_hapy.delete_global_ref(self.handle)
            self.handle = None

class jobject(_jobject):
    def __init__(self, handle, info):
        super().__init__(handle, info)
        self._t = None

    @property
    def t(self):
        if self._t is None:
            self._t = self.__class__(native_hapy.get_object_class(self.handle))
        return self._t

    def __repr__(self):
        return '{}'.format(super().__repr__())

class id:
    def __init__(self, value):
        self.value = value

def _find_class(path):
    return jobject(native_hapy.find_class(path), path)

# def _get_field(clazz, name):
#     method_id, field_type, is_static = native_hapy.get_field(clazz.handle, name)
#     return id(method_id), field_type, bool(is_static)

def _get_object_class(obj):
    return jobject(native_hapy.get_object_class(obj.handle), 'class of {}'.format(obj))

def make_string(s):
    return jobject(native_hapy.make_string(str(s)), str(s))

OP_NOOP = 0
OP_CALL_METHOD = 1
OP_CALL_STATIC_METHOD = 2
OP_GET_FIELD = 3
OP_GET_STATIC_FIELD = 4
OP_SET_FIELD = 5
OP_SET_STATIC_FIELD = 6

def _call_method(ins, method, is_static, return_type, *args):
    op = OP_CALL_STATIC_METHOD if is_static else OP_CALL_METHOD
    native_hapy.act(ins.handle, method, tuple(args), return_type, op)

# type_dict = {
#     bool: 0,
#     lambda x: bytes(x)[0]: 1,
#     lambda x: str(x)[0]: 2,
#     Short.TYPE: 3,
#     Integer.TYPE: 4,
#     Long.TYPE: 5,
#     Float.TYPE: 6,
#     Double.TYPE: 7,
#     Void.TYPE: 8,
# enumTypes.put(null, 9,
# }

primitives = {
    'object': -1,
    'boolean': 0,
    'byte': 1,
    'character': 2,
    'short': 3,
    'integer': 4,
    'long': 5,
    'float': 6,
    'double': 7,
    'void': 8,
    'const': 9,
}

class jprimitive:
    pass

class jboolean(jprimitive):
    def __init__(self, v):
        self.v = bool(v)
        self.t = primitives['boolean']
        self.w = _find_class('java/lang/Boolean')

class jshort(jprimitive):
    def __init__(self, v):
        self.v = int(v)
        self.t = primitives['short']
        self.w = _find_class('java/lang/Short')

class jint(jprimitive):
    def __init__(self, v):
        self.v = int(v)
        self.t = primitives['integer']
        self.w = _find_class('java/lang/Integer')

class jlong(jprimitive):
    def __init__(self, v):
        self.v = int(v)
        self.t = primitives['long']
        self.w = _find_class('java/lang/Long')

class jfloat(jprimitive):
    def __init__(self, v):
        self.v = float(v)
        self.t = primitives['float']
        self.w = _find_class('java/lang/Float')

class jdouble(jprimitive):
    def __init__(self, v):
        self.v = float(v)
        self.t = primitives['double']
        self.w = _find_class('java/lang/Double')

class jstring(jobject):
    def __init__(self, v):
        self.v = make_string(v)

class jbyte(jprimitive):
    def __init__(self, v):
        try:
            self.v = ord(v[0])
        except ValueError:
            pass
        except TypeError:
            pass
        self.v = int(v)
        self.t = primitives['byte']
        self.w = _find_class('java/lang/Byte')

class jchar(jprimitive):
    def __init__(self, v):
        try:
            self.v = ord(v[0])
        except ValueError:
            pass
        except TypeError:
            pass
        self.v = int(v)
        self.t = primitives['character']
        self.w = _find_class('java/lang/Character')

def prepare_value(arg, needed_type):
    if isinstance(arg, jprimitive) and needed_type == primitives['object']:
        arg = jobject(native_hapy.box(arg.v))

    return native_hapy.make_value(arg.handle if isinstance(arg, jobject) else arg.v, needed_type)

def call_method(clazz, obj, name, *args):
    args = list(args)
    arg_types = [None] * len(args)
    for i, arg in enumerate(args):
        if isinstance(arg, jobject):
            arg_types[i] = arg.t
        elif isinstance(arg, bytes) or isinstance(arg, str):
            args[i] = jstring(arg)
            arg_types[i] = args[i].t
        else:
            if isinstance(arg, bool):
                args[i] = jboolean(arg)
            elif isinstance(arg, int):
                args[i] = jint(arg)
            elif isinstance(arg, float):
                args[i] = jdouble(arg)
            elif not isinstance(arg, jprimitive):
                raise ValueError('cannot pass {} to java'.format(type(arg)))
            arg_types[i] = args[i].w

    method_id, real_arg_types, ret_type, is_static = native_hapy.get_method(clazz.handle, name, tuple(arg.handle for arg in arg_types))
    print(ret_type)

    args = tuple(prepare_value(arg, real_type) for arg, real_type in zip(args, real_arg_types))
    if is_static:
        ret = native_hapy.act(clazz.handle, method_id, args, ret_type, OP_CALL_STATIC_METHOD)
    else:
        ret = native_hapy.act(obj.handle, method_id, args, ret_type, OP_CALL_METHOD)

    return ret if ret_type not in (primitives['object'], primitives['const']) else jobject(ret, '')

print('begin')

Test = _find_class("com/happy/MainActivity$Test")
test = call_method(Test, None, '')
print(test)

ret = call_method(Test, test, 'ins_test', 39)
print(ret)

print('end')