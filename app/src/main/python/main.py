import sys
import logcat
import time
import native_hapy

known_classes = {}
known_methods = {}
known_fields = {}

class _jobject:
    _slots__ = []
    def __init__(self, handle, info):
        self.handle = handle
        if type(info) != str:
            raise ValueError('only strings in info')
        self.info = info
        #print('created {}'.format(self))

    def __repr__(self):
        return 'jobject {} ({})'.format(self.info, self.handle)

    def __del__(self):
        #TODO make sure this doesn't get called twice for the same handle
        if self.handle:
            #print('deleting {}'.format(self))
            native_hapy.delete_global_ref(self.handle)
            self.handle = None

    def __bool__(self):
        return bool(self.handle)

class jobject(_jobject):
    def __init__(self, handle, info):
        super().__init__(handle, info)
        self._t = None

    @property
    def t(self):
        if self._t is None:
            self._t = self.__class__(native_hapy.get_object_class(self.handle), 'class of {}'.format(self))
        return self._t

    def __repr__(self):
        return '{}'.format(super().__repr__())

class id:
    def __init__(self, value):
        self.value = value

def _find_class(path):
    if path not in known_classes:
        known_classes[path] = jobject(native_hapy.find_class(path), path)
    return known_classes[path]

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

OBJECT_CLASS = _find_class('java/lang/Object')

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
        self.v = int(self.v)
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
        self.v = int(self.v)
        self.t = primitives['character']
        self.w = _find_class('java/lang/Character')

def auto_handle_wrapping(arg, needed_type, unboxed_needed_type):
    if arg is None:
        return jobject(0, 'null')

    if isinstance(arg, jprimitive):
        if needed_type == primitives['object']:
            return jobject(native_hapy.box(arg.v, arg.t if unboxed_needed_type == primitives['object'] else unboxed_needed_type), 'arg')
        else:
            return arg.v

    if isinstance(arg, jobject):
        return arg
    raise ValueError('error converting {} to {}'.format(type(arg), needed_type))

def handle_ret(ret, unboxed_ret_type):
    if unboxed_ret_type in (primitives['object'], primitives['const']) and ret is not None:
        return jobject(ret, 'ret')
    return ret

#returns arg as well so it won't be freed until we call the method
#this is only a problem with inner functions extracting handles from objects
def prepare_value(arg, needed_type, unboxed_needed_type):
    arg = auto_handle_wrapping(arg, needed_type, unboxed_needed_type)
    return native_hapy.make_value(arg.handle if isinstance(arg, jobject) else arg, needed_type), arg

def convert_arg(arg):
    if isinstance(arg, jobject):
        return arg, arg.t
    elif isinstance(arg, bytes) or isinstance(arg, str):
        arg = jstring(arg)
        return arg, arg.t
    elif arg is None:
        return arg, OBJECT_CLASS
    else:
        if isinstance(arg, bool):
            arg = jboolean(arg)
        elif isinstance(arg, int):
            if - 2 ** 31 <= arg < 2 ** 31:
                arg = jint(arg)
            else:
                arg = jlong(arg)
        elif isinstance(arg, float):
            arg = jdouble(arg)
        elif not isinstance(arg, jprimitive):
            raise ValueError('cannot pass {} to java'.format(type(arg)))
        return arg, arg.w

def _get_method(handle, name, arg_types):
    key = handle, name, arg_types
    if key not in known_methods:
        known_methods[key] = native_hapy.get_method(*key)
    return known_methods[key]

def _get_field(handle, name):
    key = handle, name
    if key not in known_fields:
        known_fields[key] = native_hapy.get_field(*key)
    return known_fields[key]

def call_method(clazz, obj, name, *args):
    args = list(args)
    arg_types = [None] * len(args)
    for i, arg in enumerate(args):
        args[i], arg_types[i] = convert_arg(arg)

    method_id, needed_types, is_static = _get_method(clazz.handle, name, tuple(arg.handle for arg in arg_types))

    ret_type, unboxed_ret_type = needed_types[-1]
    needed_types = needed_types[:-1]

    all_args = tuple(prepare_value(arg, needed_type, unboxed_needed_type) for arg, (needed_type, unboxed_needed_type) in zip(args, needed_types))
    args = tuple(arg for arg,_ in all_args)

    if is_static:
        ret = native_hapy.act(clazz.handle, method_id, args, ret_type, unboxed_ret_type, OP_CALL_STATIC_METHOD)
    else:
        ret = native_hapy.act(obj.handle, method_id, args, ret_type, unboxed_ret_type, OP_CALL_METHOD)

    return handle_ret(ret, unboxed_ret_type)

def get_field(clazz, obj, name):
    field_id, (field_type, unboxed_field_type), is_static = _get_field(clazz.handle, name)
    if is_static:
        ret = native_hapy.act(clazz.handle, field_id, None, field_type, unboxed_field_type, OP_GET_STATIC_FIELD)
    else:
        ret = native_hapy.act(obj.handle, field_id, None, field_type, unboxed_field_type, OP_GET_FIELD)
    return handle_ret(ret, unboxed_field_type)

def set_field(clazz, obj, name, value):
    field_id, (field_type, unboxed_field_type), is_static = _get_field(clazz.handle, name)
    print(field_type, unboxed_field_type)
    value, _ = convert_arg(value)
    arg, ref = prepare_value(value, field_type, unboxed_field_type)
    if is_static:
        native_hapy.act(clazz.handle, field_id, (arg,), field_type, unboxed_field_type, OP_SET_STATIC_FIELD)
    else:
        native_hapy.act(obj.handle, field_id, (arg,), field_type, unboxed_field_type, OP_SET_FIELD)

print('=================================begin')

Test = _find_class("com/happy/MainActivity$Test")

def test1():
    test = call_method(Test, None, '', True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                       None, None,        None,        None,    None,          None,           None, None,
                                       Test)

    print('test1', test)
    test = call_method(Test, None, '', True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                       True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                       jshort(1000000))
    print('test2', test)

    result = call_method(Test, None, 'all', True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                            None, None,        None,        None,    None,          None,           None, None,
                                            Test)
    print('result1', result)

    result = call_method(Test, None, 'all', True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                            True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                            jshort(1000000))
    print('result2', result)

def test2():
    print('result1', call_method(Test, None, 'test_void'))
    print('result2', call_method(Test, None, 'void_test', 67))
    print('result3', call_method(Test, None, 'void_void'))

def test3():
    test = call_method(Test, None, '')
    print('result1', get_field(Test, None, 'value'))
    print('result2', get_field(Test, test, 'ins_value'))

    set_field(Test, None, 'value', 18)
    set_field(Test, test, 'ins_value', 19)

    print('result1', get_field(Test, None, 'value'))
    print('result2', get_field(Test, test, 'ins_value'))

def test4():
    start_time = time.time()
    n = 1000
    for i in range(n):
        test = call_method(Test, None, '', True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                           None, None,        None,        None,    None,          None,           None, None,
                                           Test)
        # ~ == [i ** 2 for i in range(1000)] (0.8 ms per call) ((0.7ms per call with find_class memoize)) (((0.3ms with get_method memoize + find_class memoize)))
    t = time.time() - start_time
    print('{}/{} = {}'.format(t, n, t / n))
test4()


print('====================================end')