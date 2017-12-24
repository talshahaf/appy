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
        if not isinstance(handle, int):
            raise ValueError('handle must be int')
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
OP_NEW_ARRAY = 7
OP_SET_ITEMS = 8
OP_GET_ITEMS = 9
OP_GET_ARRAY_LENGTH = 10

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

trivials = {
    _find_class('java/lang/Boolean'): primitives['boolean'],
    _find_class('java/lang/Byte'): primitives['byte'],
    _find_class('java/lang/Character'): primitives['character'],
    _find_class('java/lang/Short'): primitives['short'],
    _find_class('java/lang/Integer'): primitives['integer'],
    _find_class('java/lang/Long'): primitives['long'],
    _find_class('java/lang/Float'): primitives['float'],
    _find_class('java/lang/Double'): primitives['double'],
}
revd=dict([reversed(i) for i in trivials.items()])
trivials.update(revd)

OBJECT_CLASS = _find_class('java/lang/Object')
JNULL = jobject(0, 'null')

class jprimitive:
    pass

class jboolean(jprimitive):
    def __init__(self, v):
        self.v = bool(v)
        self.t = primitives['boolean']
        self.w = trivials[primitives['boolean']]

class jshort(jprimitive):
    def __init__(self, v):
        self.v = int(v)
        self.t = primitives['short']
        self.w = trivials[primitives['short']]

class jint(jprimitive):
    def __init__(self, v):
        self.v = int(v)
        self.t = primitives['integer']
        self.w = trivials[primitives['integer']]

class jlong(jprimitive):
    def __init__(self, v):
        self.v = int(v)
        self.t = primitives['long']
        self.w = trivials[primitives['long']]

class jfloat(jprimitive):
    def __init__(self, v):
        self.v = float(v)
        self.t = primitives['float']
        self.w = trivials[primitives['float']]

class jdouble(jprimitive):
    def __init__(self, v):
        self.v = float(v)
        self.t = primitives['double']
        self.w = trivials[primitives['double']]

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
        self.w = trivials[primitives['byte']]

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
        self.w = trivials[primitives['character']]

class jstring(jobject):
    def __init__(self, v):
        self.v = make_string(v)

def auto_handle_wrapping(arg, needed_type, unboxed_needed_type):
    if arg is None:
        return JNULL

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
        return arg, arg.t, primitives['object']
    elif arg is None:
        return arg, OBJECT_CLASS, primitives['object']
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
        return arg, arg.w, arg.t

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
        args[i], arg_types[i], _ = convert_arg(arg)

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
    value, _, _ = convert_arg(value)
    arg, ref = prepare_value(value, field_type, unboxed_field_type)
    if is_static:
        native_hapy.act(clazz.handle, field_id, (arg,), field_type, unboxed_field_type, OP_SET_STATIC_FIELD)
    else:
        native_hapy.act(obj.handle, field_id, (arg,), field_type, unboxed_field_type, OP_SET_FIELD)

class array:
    def __init__(self, obj, t, proper_t, clazz, length):
        self.obj = obj
        self.clazz = clazz
        self.t = t
        self.proper_t = proper_t
        self.length = length

    #the tuple in native_hapy.array must contain elements waiting to be filled with make_value, and None if it shouldn't be read from java at all
    #therefore, None should never be actually passed from outside and will be changed to JNULL
    def setitems(self, start, items): #TODO raise indexerror
        args = tuple(convert_arg(item) for item in items)
        values = tuple(prepare_value(arg, self.proper_t, t) for arg, w, t in args)

        native_hapy.array(self.obj.handle, tuple(v for v, _ in values), start, self.proper_t, OP_SET_ITEMS, JNULL.handle)

    #the tuple returned by native_hapy.array will contain primitives, jobject or None to denote NULL
    def getitems(self, start, end):
        array_len, obj, elements = native_hapy.array(self.obj.handle, (0,) * (end - start), start, self.proper_t, OP_GET_ITEMS, JNULL.handle)

        if self.clazz is None:
            return elements
        else:
            elements = [jobject(e, 'array element') if e is not None else None for e in elements]
            return [e if e is None or self.t == primitives['object'] else native_hapy.unbox(e.handle, self.t) for e in elements]

def make_array(l, array_type=None, clazz=None):
    if array_type is None and not isinstance(clazz, jobject):
        raise ValueError('must specify array_type or clazz')

    if isinstance(clazz, jobject):
        array_type = native_hapy.unbox_class(clazz.handle)
        clazz_obj = clazz
        proper_type = primitives['object']
    else:
        clazz_obj = JNULL
        proper_type = array_type

    array_len, obj, elements = native_hapy.array(JNULL.handle, (None,) * l, 0, proper_type, OP_NEW_ARRAY, clazz_obj.handle)
    return array(jobject(obj, 'newarray'), array_type, proper_type, clazz, array_len)

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

def test5():
    arr = make_array(5, primitives['integer'])
    arr.setitems(1, list(range(40, 40 + arr.length)))
    print('arr1', arr.getitems(0, arr.length))

    arr = make_array(5, primitives['object'], _find_class('java/lang/Long'))
    print('arr2', arr.getitems(0, arr.length))
    arr.setitems(1, list(jlong(i) for i in range(40, 40 + arr.length)))
    print('arr2 2', arr.getitems(0, arr.length))

test5()


print('====================================end')