import logcat
import time
import native_hapy

known_classes = {}
known_methods = {}
known_fields = {}

class jref:
    _slots__ = []
    def __init__(self, handle):
        if not isinstance(handle, int):
            raise ValueError('handle must be int')
        self.handle = handle
        #print('created {}'.format(self))

    def __del__(self):
        #TODO make sure this doesn't get called twice for the same handle
        if self.handle:
            #print('deleting {}'.format(self))
            native_hapy.delete_global_ref(self.handle)
            self.handle = None

    def __bool__(self):
        return bool(self.handle)

def know_class(clazz):
    known_classes[clazz.class_name] = clazz
    return known_classes[clazz.class_name]

def get_class(ref):
    return know_class(jclass(jref(native_hapy.get_object_class(ref.handle))))

class jobjectbase:
    def __bool__(self):
        return bool(self.ref)

class jobject(jobjectbase):
    def __init__(self, ref, info):
        self.ref = ref
        self.info = info
        self._clazz = None

    def __repr__(self):
        return 'jobject: {} {} ({})'.format(self.info, self.clazz, self.ref.handle)

    @property
    def clazz(self):
        if self._clazz is None:
            self._clazz = get_class(self.ref)
        return self._clazz

class jclass(jobjectbase):
    def __init__(self, ref):
        self.ref = ref
        self.class_name, self.code, is_array, element_class, self.element_code, self.unboxed_element_code = native_hapy.inspect_class(self.ref.handle)
        self.is_array = bool(is_array)
        if element_class:
            self.element_class = know_class(jclass(jref(element_class)))

    def __repr__(self):
        return 'class {} ({})'.format(self.class_name, self.ref.handle)

    @property
    def clazz(self):
        return find_class('java.lang.Class')


class jstring(jobjectbase):
    def __init__(self, ref):
        self.ref = ref
        self._value = None

    def __repr__(self):
        return 'jstring {} ({})'.format(self.value, self.ref.handle)

    @property
    def value(self):
        if self._value is None:
            self._value = native_hapy.unbox_string(self.ref.handle)
        return self._value

    @property
    def clazz(self):
        return find_class('java.lang.String')

    @classmethod
    def from_str(cls, v):
        return jstring(jref(native_hapy.make_string(str(v))))


def find_class(path):
    if path not in known_classes:
        known_classes[path] = know_class(jclass(jref(native_hapy.find_class(path.replace('.', '/')))))
    return known_classes[path]

JNULL = jobject(jref(0), 'null')
OBJECT_CLASS = find_class('java.lang.Object')

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

primitive_codes = {
    'object': -1,
    'boolean': 0,
    'byte': 1,
    'char': 2,
    'short': 3,
    'int': 4,
    'long': 5,
    'float': 6,
    'double': 7,
    'void': 8,
    'const': 9,
}

wrapper_to_primitive_code = {
    find_class('java.lang.Boolean'): primitive_codes['boolean'],
    find_class('java.lang.Byte'): primitive_codes['byte'],
    find_class('java.lang.Character'): primitive_codes['char'],
    find_class('java.lang.Short'): primitive_codes['short'],
    find_class('java.lang.Integer'): primitive_codes['int'],
    find_class('java.lang.Long'): primitive_codes['long'],
    find_class('java.lang.Float'): primitive_codes['float'],
    find_class('java.lang.Double'): primitive_codes['double'],
}
revd=dict([reversed(i) for i in wrapper_to_primitive_code.items()])
wrapper_to_primitive_code.update(revd)

class meta_primitive(type):
    def __new__(cls, *args, **kwargs):
        inst = type.__new__(cls, *args, **kwargs)
        k = inst.__name__[1:] #drop the 'j'
        if k != 'primitive':
            inst.code = primitive_codes[k]
            inst.wrapper_class = wrapper_to_primitive_code[inst.code]
        return inst

class jprimitive(metaclass=meta_primitive):
    pass

class jboolean(jprimitive):
    def __init__(self, v):
        self.value = bool(v)

class jshort(jprimitive):
    def __init__(self, v):
        self.value = int(v)

class jint(jprimitive):
    def __init__(self, v):
        self.value = int(v)

class jlong(jprimitive):
    def __init__(self, v):
        self.value = int(v)

class jfloat(jprimitive):
    def __init__(self, v):
        self.value = float(v)

class jdouble(jprimitive):
    def __init__(self, v):
        self.value = float(v)

class jbyte(jprimitive):
    def __init__(self, v):
        try:
            self.value = ord(v[0])
        except ValueError:
            pass
        except TypeError:
            pass
        self.value = int(self.value)

class jchar(jprimitive):
    def __init__(self, v):
        try:
            self.value = ord(v[0])
        except ValueError:
            pass
        except TypeError:
            pass
        self.value = int(self.value)

def code_is_object(code):
    return code in (primitive_codes['object'], primitive_codes['const'])

def auto_handle_wrapping(arg, needed_code, unboxed_needed_code):
    if arg is None:
        return JNULL

    if isinstance(arg, jprimitive):
        if code_is_object(needed_code):
            return jobject(jref(native_hapy.box(arg.value, arg.code if code_is_object(unboxed_needed_code) else unboxed_needed_code)), 'arg')
        else:
            return arg.value

    if isinstance(arg, jobjectbase):
        return arg
    raise ValueError('error converting {} to {}'.format(type(arg), needed_code))

def handle_ret(ret, ret_code):
    if code_is_object(ret_code) and ret is not None:
        ret = upcast(jobject(jref(ret), 'ret'))
    return ret

#convert python jtype to native jvalue
#returns arg as well so it won't be freed until we call the method
#this is only a problem with inner functions extracting handles from objects
def prepare_value(arg, needed_code, unboxed_needed_code):
    arg = auto_handle_wrapping(arg, needed_code, unboxed_needed_code)
    return native_hapy.make_value(arg.ref.handle if isinstance(arg, jobjectbase) else arg, needed_code), arg

#convert regular python type to our python jtypes
def convert_arg(arg):
    if isinstance(arg, jobjectbase):
        return arg, arg.clazz, primitive_codes['object']
    elif isinstance(arg, bytes) or isinstance(arg, str):
        arg = jstring.from_str(arg)
        return arg, arg.clazz, primitive_codes['object']
    elif arg is None:
        return arg, OBJECT_CLASS, primitive_codes['object']
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
        return arg, arg.wrapper_class, arg.code

def _get_method(clazz, name, arg_codes):
    key = clazz.class_name, name, arg_codes
    if key not in known_methods:
        known_methods[key] = native_hapy.get_method(clazz.ref.handle, name, arg_codes)
    return known_methods[key]

def _get_field(clazz, name):
    key = clazz.class_name, name
    if key not in known_fields:
        known_fields[key] = native_hapy.get_field(clazz.ref.handle, name)
    return known_fields[key]

def call_method(clazz, obj, name, *args):
    args, arg_codes, _ = zip(*(convert_arg(arg) for arg in args)) if args else ([], [], 0)

    method_id, needed_codes, is_static = _get_method(clazz, name, tuple(arg.ref.handle for arg in arg_codes))

    ret_code, _ = needed_codes[-1]
    needed_codes = needed_codes[:-1]

    all_args = tuple(prepare_value(arg, needed_code, unboxed_needed_code) for arg, (needed_code, unboxed_needed_code) in zip(args, needed_codes))
    args = tuple(arg for arg,_ in all_args)

    if is_static:
        ret = native_hapy.act(clazz.ref.handle, method_id, args, ret_code, OP_CALL_STATIC_METHOD)
    else:
        ret = native_hapy.act(obj.ref.handle, method_id, args, ret_code, OP_CALL_METHOD)

    return handle_ret(ret, ret_code)

def get_field(clazz, obj, name):
    field_id, (field_code, _), is_static = _get_field(clazz, name)
    if is_static:
        ret = native_hapy.act(clazz.ref.handle, field_id, None, field_code, OP_GET_STATIC_FIELD)
    else:
        ret = native_hapy.act(obj.ref.handle, field_id, None, field_code, OP_GET_FIELD)
    return handle_ret(ret, field_code)

def set_field(clazz, obj, name, value):
    field_id, (field_code, unboxed_field_code), is_static = _get_field(clazz, name)
    value, _, _ = convert_arg(value)
    arg, ref = prepare_value(value, field_code, unboxed_field_code)
    if is_static:
        native_hapy.act(clazz.ref.handle, field_id, (arg,), field_code, OP_SET_STATIC_FIELD)
    else:
        native_hapy.act(obj.ref.handle, field_id, (arg,), field_code, OP_SET_FIELD)

class array(jobjectbase):
    def __init__(self, ref, type_code, type_unboxed_code, element_class, length=None):
        self.ref = ref
        self.type_code = type_code
        self.type_unboxed_code = type_unboxed_code
        self._length = length
        self.element_class = element_class

    @property
    def length(self):
        if self._length is None:
            self._length, _, _ = native_hapy.array(self.ref.handle, tuple(), 0, self.type_code, OP_GET_ARRAY_LENGTH, JNULL.ref.handle)
        return self._length

    #the tuple in native_hapy.array must contain elements waiting to be filled with make_value, and None if it shouldn't be read from java at all
    #therefore, None should never be actually passed from outside and will be changed to JNULL
    def setitems(self, start, items):
        if start < 0:
            start += self.length
        if start < 0 or start + len(items) > self.length:
            raise IndexError('out of range: {}'.format(self.length))

        args = tuple(convert_arg(item) for item in items)
        values = tuple(prepare_value(arg, self.type_code, t) for arg, w, t in args)

        native_hapy.array(self.ref.handle, tuple(v for v, _ in values), start, self.type_code, OP_SET_ITEMS, JNULL.ref.handle)

    #the tuple returned by native_hapy.array will contain primitives, jobject or None to denote NULL
    def getitems(self, start, end):
        if start < 0:
            start += self.length
        if end < 0:
            end += self.length
        if end - start == 1 and start < 0 or start >= self.length:
            raise IndexError('out of range: {}'.format(self.length))
        if end <= start:
            return tuple()

        array_len, obj, elements = native_hapy.array(self.ref.handle, (0,) * (end - start), start, self.type_code, OP_GET_ITEMS, JNULL.ref.handle)

        if not code_is_object(self.type_code):
            return elements
        else:
            elements = tuple(jobject(jref(e), 'array element') if e is not None else None for e in elements)
            return tuple(upcast(e) for e in elements)

def make_array(l, type_code=None, clazz=None):
    if type_code is None and not isinstance(clazz, jobjectbase):
        raise ValueError('must specify type_code or clazz')

    if isinstance(clazz, jobjectbase):
        type_unboxed_code = native_hapy.unbox_class(clazz.ref.handle)
        clazz_obj = clazz
        type_code = primitive_codes['object']
    else:
        clazz_obj = JNULL
        type_unboxed_code = type_code

    array_len, obj, elements = native_hapy.array(JNULL.ref.handle, (None,) * l, 0, type_code, OP_NEW_ARRAY, clazz_obj.ref.handle)
    return array(jref(obj), type_code, type_unboxed_code, clazz, array_len)

def upcast(obj):
    if obj is None:
        return None
    if not isinstance(obj, jobjectbase):
        raise ValueError('must be jobject')

    if not obj:
        return None

    if obj.clazz.is_array:
        return array(obj.ref, obj.clazz.element_code, obj.clazz.unboxed_element_code, obj.clazz.element_class)

    if not code_is_object(obj.clazz.code):
        return native_hapy.unbox(obj.ref.handle, obj.clazz.code)

    if obj.clazz.class_name == 'java.lang.String':
        return jstring(obj.ref).value

    return obj


def tests():
    print('=================================begin')

    Test = find_class("com.happy.Test")

    def test1():
        test = call_method(Test, None, '', True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                           None, None,        None,        None,    None,          None,           None, None,
                                           Test)
        print('test1', test)
        assert(type(test) == jobject)

        test = call_method(Test, None, '', True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                           True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                           jshort(1000000))
        print('test2', test)
        assert(type(test) == jobject)


        result = call_method(Test, None, 'all', True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                                None, None,        None,        None,    None,          None,           None, None,
                                                Test)
        print('result1', result)
        assert(type(result) == jobject)


        result = call_method(Test, None, 'all', True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                                True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                                jshort(1000000))
        print('result2', result)
        assert(type(test) == jobject)

        result = call_method(Test, None, 'primitive', jshort(1000000))
        print('result3', result)
        assert(result == 16960)


    def test2():
        n = call_method(Test, None, 'test_void')
        assert(n == 48);
        print('result1', n)
        n = call_method(Test, None, 'void_test', 67)
        assert(n is None);
        print('result2', n)
        n = call_method(Test, None, 'void_void')
        assert(n is None);
        print('result3', n)

    def test3():
        test = call_method(Test, None, '')
        value = get_field(Test, None, 'value')
        ins_value = get_field(Test, test, 'ins_value')
        assert((value == 23 or value == 18) and ins_value == 24)
        print('result1', value, ins_value)

        value = set_field(Test, None, 'value', 18)
        ins_value = set_field(Test, test, 'ins_value', 19)
        assert(value is None and ins_value is None)

        value = get_field(Test, None, 'value')
        ins_value = get_field(Test, test, 'ins_value')
        assert(value == 18 and ins_value == 19)
        print('result1', value, ins_value)

    def test4():
        n = 1000
        start_time = time.time()
        for i in range(n):
            test = call_method(Test, None, '', True, jbyte('b'),  jchar('c'),  10 ** 3, 2 * (10 ** 5), 3 * (10 ** 10), 1.1,  3.141529,
                                               None, None,        None,        None,    None,          None,           None, None,
                                               Test)

        mid_time = time.time()

        #sum(i ** 2 for i in range(n * 300))
        call_method(Test, None, 'test_work', n * 5)

        end_time = time.time()

        java_time = mid_time - start_time
        square_time = end_time - mid_time

        print('{}/{} = {}    ,     {}/{} = {}'.format(java_time, n, java_time / n, square_time, n, square_time / n))
        #assert(java_time < square_time)

    def test5():
        arr = make_array(5, primitive_codes['int'])
        assert(type(arr) == array)

        items = arr.setitems(1, list(range(40, 40 + arr.length - 1)))
        assert(items == None)

        items = arr.getitems(0, arr.length)
        print('arr1', items)
        assert(items == (0, 40, 41, 42, 43))

        arr = make_array(5, primitive_codes['object'], find_class('java.lang.Long'))
        assert(type(arr) == array)

        items = arr.getitems(0, arr.length)
        print('arr2', items)
        assert(items == (None,) * 5)

        items = arr.setitems(1, list(jlong(i) for i in range(40, 40 + arr.length - 1)))
        assert(items is None)

        items = arr.getitems(0, arr.length)
        print('arr2 2', items)
        assert(items == (None, 40, 41, 42, 43))

    def test6():
        pos = 1

        arr = call_method(Test, None, 'test_int_array', 13)
        assert(arr.length == 13)
        items = arr.setitems(pos, list(range(40, 40 + arr.length - pos)))
        assert(items == None)
        items = arr.getitems(0, arr.length)
        print(items)

        arr = call_method(Test, None, 'test_integer_array', 13)
        assert(arr.length == 13)
        items = arr.setitems(pos, list(range(40, 40 + arr.length - pos)))
        assert(items == None)
        items = arr.getitems(0, arr.length)
        print(items)

        arr = call_method(Test, None, 'test_object_array', 13)
        assert(arr.length == 13)
        items = arr.setitems(pos, list(range(40, 40 + arr.length - pos)))
        assert(items == None)
        items = arr.getitems(0, arr.length)
        print(items)

    def test7():
        ret = call_method(Test, None, 'test_string', 'שלום')
        print(ret.encode())
        assert(ret == '=שלום=')

        ret = call_method(Test, None, 'test_string', 'abcd\x00efgh')
        print(ret.encode())
        assert(ret == '=abcd\x00efgh=')

        ret = call_method(Test, None, 'test_string', 'abשל\x00וםgh')
        print(ret.encode())
        assert(ret == '=abשל\x00וםgh=')

    test1()
    test2()
    test3()
    test4()
    test5()
    test6()
    test7()

    print('====================================end')

if __name__ == '__main__':
    tests()