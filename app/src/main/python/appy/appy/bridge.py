import time
import traceback
import native_appy

known_classes = {}
known_methods = {}
known_fields = {}

PACKAGE_NAME = 'com.appy'
SPECIAL_CLASSES = {'appy': PACKAGE_NAME}

class jref:
    _slots__ = []
    def __init__(self, handle):
        if not isinstance(handle, int):
            raise ValueError('handle must be int')
        self.handle = handle
        #print(f'created {self.handle}')

    def __del__(self):
        #TODO make sure this doesn't get called twice for the same handle
        if self.handle:
            #print(f'deleting {self.handle}')
            native_appy.delete_global_ref(self.handle)
            self.handle = None

    def __bool__(self):
        return bool(self.handle)

def know_class(clazz):
    if clazz.class_name not in known_classes:
        known_classes[clazz.class_name] = clazz
    return known_classes[clazz.class_name] #additional global refs to a known class will be destructed here

def get_class(ref):
    return know_class(jclass(jref(native_appy.get_object_class(ref.handle))))

def cast(obj, cast_class):
    if not native_appy.check_is_jclass_castable(obj.clazz.ref.handle, cast_class.ref.handle):
        raise ValueError(f'{obj.clazz} cannot be cast to {cast_class}')

    dup = jobject(obj.ref, obj.info)
    dup.cast_class = cast_class
    return dup

class jobjectbase:
    def __bool__(self):
        return bool(self.ref)

class jobject(jobjectbase):
    def __init__(self, ref, info):
        self.ref = ref
        self.info = info
        self._clazz = None

    def __repr__(self):
        rep = f'jobject: {self.info} {self.clazz} ({self.ref.handle})'
        if hasattr(self, 'cast_class'):
            rep = f'{rep} cast as {self.cast_class}'
        return rep

    @property
    def clazz(self):
        if self._clazz is None:
            self._clazz = get_class(self.ref)
        return self._clazz

class jclass(jobjectbase):
    def __init__(self, ref):
        self.ref = ref
        self.class_name, self.code, is_array, element_class, self.element_code, self.unboxed_element_code = native_appy.inspect_class(self.ref.handle)
        self.is_array = bool(is_array)
        if element_class:
            self.element_class = know_class(jclass(jref(element_class)))

    def __repr__(self):
        return f'class {self.class_name} ({self.ref.handle})'

    @property
    def clazz(self):
        return find_class('java.lang.Class')

    def __dir__(self):
        return list(native_appy.inspect_class_content(self.ref.handle, False))


class jstring(jobjectbase):
    def __init__(self, ref):
        self.ref = ref
        self._value = None

    def __repr__(self):
        return f'jstring {self.value} ({self.ref.handle})'

    @property
    def value(self):
        if self._value is None:
            self._value = native_appy.jstring_to_python_str(self.ref.handle)
        return self._value

    @property
    def info(self):
        return self.value

    @property
    def clazz(self):
        return find_class('java.lang.String')

    @classmethod
    def from_str(cls, v):
        if isinstance(v, bytes):
            v = v.decode()
        return jstring(jref(native_appy.python_str_to_jstring(str(v))))

def find_class(path):
    for key, value in SPECIAL_CLASSES.items():
        if path.startswith(f'{key}.'):
            path = f'{value}.{path[len(key) + 1:]}'
            break
    return know_class(jclass(jref(native_appy.find_class(path.replace('.', '/')))))

def array_of_class(clazz):
    return know_class(jclass(jref(native_appy.jclass_to_array_of_jclass(clazz.ref.handle))))

def find_primitive_array(code):
    return primitive_code_to_array[code]

JNULL = jobject(jref(0), 'null')
OBJECT_CLASS = find_class('java.lang.Object')
CLASS_CLASS = find_class('java.lang.Class')

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

primitive_code_to_wrapper = {
    primitive_codes['boolean']: find_class('java.lang.Boolean'),
    primitive_codes['byte']:    find_class('java.lang.Byte'),
    primitive_codes['char']:    find_class('java.lang.Character'),
    primitive_codes['short']:   find_class('java.lang.Short'),
    primitive_codes['int']:     find_class('java.lang.Integer'),
    primitive_codes['long']:    find_class('java.lang.Long'),
    primitive_codes['float']:   find_class('java.lang.Float'),
    primitive_codes['double']:  find_class('java.lang.Double'),
}

primitive_code_to_array = {
    primitive_codes['boolean']: find_class('[Z'),
    primitive_codes['byte']:    find_class('[B'),
    primitive_codes['char']:    find_class('[C'),
    primitive_codes['short']:   find_class('[S'),
    primitive_codes['int']:     find_class('[I'),
    primitive_codes['long']:    find_class('[J'),
    primitive_codes['float']:   find_class('[F'),
    primitive_codes['double']:  find_class('[D'),
}

class meta_primitive(type):
    def __new__(cls, *args, **kwargs):
        inst = type.__new__(cls, *args, **kwargs)
        k = inst.__name__[1:] #drop the 'j'
        if k != 'primitive':
            inst.code = primitive_codes[k]
            inst.wrapper_class = primitive_code_to_wrapper[inst.code]
        return inst

class jprimitive(metaclass=meta_primitive):
    def __lt__(self, other):
        return self.value.__lt__(other)
    def __le__(self, other):
        return self.value.__le__(other)
    def __eq__(self, other):
        return self.value.__eq__(other)
    def __ne__(self, other):
        return self.value.__ne__(other)
    def __gt__(self, other):
        return self.value.__gt__(other)
    def __ge__(self, other):
        return self.value.__ge__(other)

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
            return jobject(jref(native_appy.python_to_packed_java_primitive(arg.value, arg.code if code_is_object(unboxed_needed_code) else unboxed_needed_code)), 'arg')
        else:
            return arg.value

    if isinstance(arg, jobjectbase):
        return arg
    raise ValueError(f'error converting {type(arg)} to {needed_code}')

def handle_ret(ret, ret_code):
    if code_is_object(ret_code) and ret is not None:
        ret = upcast(jobject(jref(ret), 'ret'))
    return ret

def box_python(val):
    arg, _, code = convert_arg(val)
    if code == primitive_codes['object']:
        return arg

    return jobject(jref(native_appy.python_to_packed_java_primitive(arg, code)), 'boxed')

#convert python jtype to native jvalue
#returns arg as well so it won't be freed until we call the method
#this is only a problem with inner functions extracting handles from objects
def prepare_value(arg, needed_code, unboxed_needed_code):
    arg = auto_handle_wrapping(arg, needed_code, unboxed_needed_code)
    return native_appy.python_to_unpacked_jvalue(arg.ref.handle if isinstance(arg, jobjectbase) else arg, needed_code), arg

#convert regular python type to our python jtypes
def convert_arg(arg):
    if isinstance(arg, jobjectbase):
        return arg, arg.cast_class if hasattr(arg, 'cast_class') else arg.clazz, primitive_codes['object']
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
            raise ValueError(f'cannot pass {type(arg)} to java: {arg}')
        return arg, arg.wrapper_class, arg.code

def clear_known_cache():
    known_classes.clear()
    known_methods.clear()
    known_fields.clear()
    
def get_methodid(clazz, name, arg_codes):
    key = clazz.class_name, name, arg_codes
    if key not in known_methods:
        known_methods[key] = native_appy.get_methodid(clazz.ref.handle, name, arg_codes)
    return known_methods[key]

def get_fieldid(clazz, name):
    key = clazz.class_name, name
    if key not in known_fields:
        known_fields[key] = native_appy.get_fieldid(clazz.ref.handle, name)
    return known_fields[key]

def has_field_or_method(clazz, name):
    field_id, _, _, _, has_same_name_method = get_fieldid(clazz, name)
    return (field_id is not None, has_same_name_method != 0)
    
def call_method(clazz, obj, name, *args):
    args, arg_classes, _ = zip(*(convert_arg(arg) for arg in args)) if args else ([], [], 0)

    method_id, needed_codes, is_static, _ = get_methodid(clazz, name, tuple(arg.ref.handle for arg in arg_classes))
    if method_id is None:
        raise AttributeError(f'No method {name} found for the supplied signature')

    ret_code, _ = needed_codes[-1]
    needed_codes = needed_codes[:-1]

    all_args = tuple(prepare_value(arg, needed_code, unboxed_needed_code) for arg, (needed_code, unboxed_needed_code) in zip(args, needed_codes))
    args = tuple(arg for arg,_ in all_args)

    if is_static:
        ret = native_appy.call_jni_object_functions(clazz.ref.handle, method_id, args, ret_code, OP_CALL_STATIC_METHOD)
    else:
        ret = native_appy.call_jni_object_functions(obj.ref.handle, method_id, args, ret_code, OP_CALL_METHOD)

    return handle_ret(ret, ret_code)

def get_field(clazz, obj, name):
    field_id, field_code, _, is_static, _ = get_fieldid(clazz, name)
    if field_id is None:
        raise AttributeError(f'No such field: {name}')
        
    if is_static:
        ret = native_appy.call_jni_object_functions(clazz.ref.handle, field_id, None, field_code, OP_GET_STATIC_FIELD)
    else:
        ret = native_appy.call_jni_object_functions(obj.ref.handle, field_id, None, field_code, OP_GET_FIELD)
    return handle_ret(ret, field_code)

def set_field(clazz, obj, name, value):
    field_id, field_code, unboxed_field_code, is_static, _ = get_fieldid(clazz, name)
    if field_id is None:
        raise AttributeError(f'No such field: {name}')
        
    value, _, _ = convert_arg(value)
    arg, ref = prepare_value(value, field_code, unboxed_field_code)
    if is_static:
        native_appy.call_jni_object_functions(clazz.ref.handle, field_id, (arg,), field_code, OP_SET_STATIC_FIELD)
    else:
        native_appy.call_jni_object_functions(obj.ref.handle, field_id, (arg,), field_code, OP_SET_FIELD)

class array(jobjectbase):
    def __init__(self, ref, type_code, type_unboxed_code, element_class, length=None):
        self.ref = ref
        self.type_code = type_code
        self.type_unboxed_code = type_unboxed_code
        self._length = length
        self.element_class = element_class

    def __len__(self):
        return self.length

    @property
    def length(self):
        if self._length is None:
            self._length, _, _ = native_appy.call_jni_array_functions(self.ref.handle, tuple(), 0, self.type_code, OP_GET_ARRAY_LENGTH, JNULL.ref.handle)
        return self._length

    @property
    def clazz(self):
        if code_is_object(self.type_code):
            return array_of_class(self.element_class)
        else:
            return find_primitive_array(self.type_code)

    #the tuple in native_appy.array must contain elements waiting to be filled with make_value, and None if it shouldn't be read from java at all
    #therefore, None should never be actually passed from outside and will be changed to JNULL
    def __setitem__(self, key, items):
        if isinstance(key, slice):
            start, stop, step = key.indices(self.length)
            items = list(items)
            if step != 1:
                raise ValueError('only step = 1 are supported')
            if len(items) != stop - start:
                raise IndexError(f'invalid array size: {len(items)}, needs to be {stop - start}')
        elif isinstance(key, int):
            start, stop = key, key + 1
            items = [items]
        else:
            raise IndexError(f'invalid index: {key}')

        args = tuple(convert_arg(item) for item in items)
        values = tuple(prepare_value(arg, self.type_code, t) for arg, _, t in args)

        native_appy.call_jni_array_functions(self.ref.handle, tuple(v for v, _ in values), start, self.type_code, OP_SET_ITEMS, JNULL.ref.handle)

    #the tuple returned by native_appy.array will contain primitives, jobject or None to denote NULL
    def __getitem__(self, key):
        if isinstance(key, slice):
            start, stop, step = key.indices(self.length)
            if step != 1:
                raise ValueError('only step = 1 are supported')
            if stop <= start:
                return tuple()
        elif isinstance(key, int):
            if key < 0 or key >= self.length:
                raise IndexError(f'index out of range: {key}, len is {self.length}')
            start, stop = key, key + 1
        else:
            raise IndexError('invalid index: {key}')

        array_len, obj, elements = native_appy.call_jni_array_functions(self.ref.handle, (0,) * (stop - start), start, self.type_code, OP_GET_ITEMS, JNULL.ref.handle)

        if code_is_object(self.type_code):
            elements = tuple(upcast(jobject(jref(e), 'array element')) if e is not None else None for e in elements)

        if isinstance(key, int):
            return elements[0]
        return elements

    def __repr__(self):
        return f'array of length {self.length}: {self[:]}'

def make_array(l, type_code_or_clazz):
    if isinstance(type_code_or_clazz, jobjectbase):
        type_unboxed_code = native_appy.unpack_primitive_class(type_code_or_clazz.ref.handle)
        clazz_obj = type_code_or_clazz
        type_code = primitive_codes['object']
    elif type_code_or_clazz in primitive_code_to_array:
        clazz_obj = JNULL
        type_code = type_code_or_clazz
        type_unboxed_code = type_code_or_clazz
    else:
        raise ValueError('must be primitive code or class')

    array_len, obj, elements = native_appy.call_jni_array_functions(JNULL.ref.handle, (None,) * l, 0, type_code, OP_NEW_ARRAY, clazz_obj.ref.handle)
    return array(jref(obj), type_code, type_unboxed_code, clazz_obj, array_len)

def upcast(obj):
    if obj is None:
        return None
    if not isinstance(obj, jobjectbase):
        raise ValueError('must be jobject')

    if not obj:
        return None

    if obj.clazz.is_array:
        arr = array(jref(obj.ref.handle), obj.clazz.element_code, obj.clazz.unboxed_element_code, obj.clazz.element_class)
        obj.ref.handle = 0
        return arr

    if not code_is_object(obj.clazz.code):
        return native_appy.packed_java_primitive_to_python(obj.ref.handle, obj.clazz.code)

    if obj.clazz.class_name == 'java.lang.String':
        return jstring(obj.ref).value

    return obj

interfaces = {}

def make_interface(self, classes):
    key = id(self)
    if key in interfaces:
        raise ValueError('class already added')
    interfaces[key] = self
    classes = list(classes)
    arr = make_array(len(classes), CLASS_CLASS)
    arr[:] = classes
    return upcast(jobject(jref(native_appy.create_java_interface(key, arr.ref.handle)), 'interface'))

def get_java_arg():
    return upcast(jobject(jref(native_appy.get_java_init_arg()), 'java arg'))

def callback(arg):
    try:
        #print('callback called')
        args = upcast(jobject(jref(arg), 'callback arg'))
        key, cls, method, args = args

        if args is None:
            args = []

        if key not in interfaces:
            raise Exception(f'interface not registered: {key}')

        iface = interfaces[key]

        if hasattr(iface, method):
            func = getattr(iface, method)
        elif hasattr(iface, '__contains__') and method in iface:
            func = iface[method]
        else:
            raise Exception(f'no callback for method {method}')

        if not hasattr(func, '__interface__'):
            raise Exception(f'function not an interface: {method}')

        ret = func(*args)
        value, _, _ = convert_arg(ret)
        _, ref = prepare_value(value, primitive_codes['object'], primitive_codes['object'])
        return native_appy.new_global_ref(ref.ref.handle)
    except Exception:
        raise Exception('Python Exception\n\nThe above exception was the direct cause of the following exception:\n\n' + traceback.format_exc())

native_appy.set_python_callback(callback)

def tests():
    print('=================================begin')

    Test = find_class('appy.Test')

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

        print(f'{java_time}/{n} = {java_time / n}    ,     {square_time}/{n} = {square_time / n}')
        #assert(java_time < square_time)

    def test5():
        pos = 1

        arr = make_array(5, primitive_codes['int'])
        assert(type(arr) == array)

        arr[pos : arr.length] = list(range(40, 40 + arr.length - pos))
        items = arr[0:arr.length]
        print('arr1', items)
        assert(items == (0, 40, 41, 42, 43))

        arr = make_array(5, find_class('java.lang.Long'))
        assert(type(arr) == array)

        items = arr[0:arr.length]
        print('arr2', items)
        assert(items == (None,) * 5)

        arr[pos : arr.length] = list(jlong(i) for i in range(40, 40 + arr.length - pos))

        items = arr[0:arr.length]
        print('arr2 2', items)
        assert(items == (None, 40, 41, 42, 43))

    def test6():
        pos = 1

        arr = call_method(Test, None, 'test_int_array', 13)
        assert(arr.length == 13)
        arr[pos : arr.length] = list(range(40, 40 + arr.length - pos))
        items = arr[0:arr.length]
        print(items)

        arr = call_method(Test, None, 'test_integer_array', 13)
        assert(arr.length == 13)
        arr[pos : arr.length] = list(range(40, 40 + arr.length - pos))
        items = arr[0:arr.length]
        print(items)

        arr = call_method(Test, None, 'test_object_array', 13)
        assert(arr.length == 13)
        arr[pos : arr.length] = list(range(40, 40 + arr.length - pos))
        items = arr[0:arr.length]
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

    print('==================bridge tests end==================')

if __name__ == '__main__':
    tests()