from . import bridge
import time, inspect, dis

# this function is called from a __getattr__ method. it determines whether the attribute being searched will be called right after getting it.
# example:
# class Obj:
#     def __getattr__(self, key):
#         if is_calling():
#             return lambda: None
#         else:
#             return None
#
# Obj().a == None
# Obj().a() == None (lambda was called)
#
# x = Obj().a # x == None
# x()         # TypeError: 'NoneType' object is not callable
def is_calling():
    frame = inspect.stack()[2]
    bytecode = dis.Bytecode(frame.frame.f_code)
    inline = False
    expr = []
    for instr in bytecode:
        if instr.offset == frame.frame.f_lasti:
            inline = True
        if instr.opname == 'POP_TOP':
            inline = False
        if inline:
            expr.append(instr)
    
    if expr[0].opname == 'LOAD_METHOD':
        #must be used with CALL_METHOD
        return True
        
    if expr[0].opname != 'LOAD_ATTR':
        #shouldn't happen
        return False

    load_nums = 1 #current opcode is LOAD_ATTR
    #waiting to see which opcode uses our argnum
    for instr in expr[1:]:
        if instr.opname.startswith('LOAD_'):
            load_nums += 1
        if instr.opname == 'CALL' and instr.arg + 2 >= load_nums: #call argument is number of args + ((NULL, func) or (func, self))
            return True
        if instr.arg == expr[0].arg:
            if instr.opname.startswith('CALL_FUNCTION'):
                #found our call
                return True
            if instr.opname.startswith('LOAD_') and instr.opname != 'LOAD_CONST':
                #value overwritten, no call
                return False
    #nothing found
    return False

def raise_(exc):
    raise exc

def wrap(obj, *args, **kwargs):
    if obj is None or obj == bridge.JNULL:
        return Null, False

    if not isinstance(obj, bridge.jobjectbase):
        return obj, True

    if isinstance(obj, bridge.array):
        if obj.type_unboxed_code == bridge.primitive_codes['byte']:
            return ByteArray(obj, *args, **kwargs), False
        elif obj.type_unboxed_code == bridge.primitive_codes['char']:
            return CharArray(obj, *args, **kwargs), False
        else:
            return Array(obj, *args, **kwargs), False

    if isinstance(obj, bridge.jclass):
        return Class(obj, *args, **kwargs), False

    return Object(obj, *args, **kwargs), False

def unwrap(obj):
    if hasattr(obj, '__java__'):
        return unwrap(obj.__java__())

    if isinstance(obj, (Object, Class, Array)):
        return obj.__bridge__
    if isinstance(obj, InterfaceBase):
        return obj.java_object.__bridge__
    if isinstance(obj, MethodCaller):
        raise RuntimeError('field does not exists')

    if obj == Null:
        return None
    return obj

def unwrap_args(args):
    return (unwrap(arg) for arg in args)

def find_class_with_inner(path):
    while True:
        try:
            return bridge.find_class(path)
        except RuntimeError:
            if '.' not in path:
                raise
            start, sep, end = path.rpartition('.')
            path = f'{start}${end}'

class Path:
    def __init__(self, path_func=None, cls_func=None, arr_func=None, path='', array_dim=0):
        self.__path_func = path_func
        self.__cls_func = cls_func
        self.__arr_func = arr_func
        self.__path = path
        self.__array_dim = array_dim

    def __getattr__(self, attr):
        if attr.startswith('__'):
            return object.__getattribute__(self, attr)
        if self.__array_dim != 0:
            raise ValueError('invalid path')
        return Path(cls_func=self.__cls_func, arr_func=self.__arr_func, path=attr if not self.__path else f'{self.__path}.{attr}')

    def __call__(self, *args):
        if self.__path_func is not None:
            return self.__path_func(self.__path, *args)
        cls = find_class_with_inner(self.__path)
        if self.__array_dim == 0:
            if self.__cls_func is None:
                raise RuntimeError('operation not supported')
            return self.__cls_func(Class(cls), *args)
        else:
            if self.__arr_func is None:
                raise RuntimeError('operation not supported')
            element_cls = cls
            for _ in range(self.__array_dim - 1):
                element_cls = bridge.array_of_class(element_cls)
            arr_cls = bridge.array_of_class(element_cls)
            wrapped_element_cls = Class(element_cls)
            return self.__arr_func(Class(arr_cls, array_element_class=wrapped_element_cls), wrapped_element_cls, *args)

    def __getitem__(self, key):
        if not isinstance(key, tuple) or len(key) != 0:
            raise ValueError('must be ()')
        return Path(cls_func=self.__cls_func, arr_func=self.__arr_func, path=self.__path, array_dim=self.__array_dim + 1)

def _call(parent, attrname, *args):
    if parent.__use_static__:
        return wrap(bridge.call_method(parent.__bridge__, None, attrname, *unwrap_args(args)))[0]
    else:
        return wrap(bridge.call_method(parent.__bridge__.clazz, parent.__bridge__, attrname, *unwrap_args(args)))[0]

class MethodCaller:
    def __init__(self, parent, attrname):
        self.parent = parent
        self.attrname = attrname

    def __call__(self, *args):
        return _call(self.parent, self.attrname, *args)

class Object:
    def __init__(self, bridge_obj, use_static=False):
        self.__dict__['__bridge__'] = bridge_obj
        self.__dict__['__use_static__'] = use_static
        self.__dict__['__parent__'] = None
        self.__dict__['__attrname__'] = None

    def __eq__(self, other):
        return self.__bridge__ == getattr(other, '__bridge__', None)

    def __getattr__(self, attr):
        hint_is_field = None
        if '·' in attr:
            attr, hint_str = attr.split('·')
            if hint_str not in ('field', 'method'):
                raise AttributeError('middle dot character hint must be "field" or "method"')
            hint_is_field = hint_str == 'field'
        
        if hint_is_field is None:
            #if we don't know, search
            has_field, has_method = bridge.has_field_or_method(self.__bridge__ if self.__use_static__ else self.__bridge__.clazz, attr)
            if has_field and has_method:
                raise AttributeError(f'class has both field and method named {attr}, please use the middle dot character (U+00B7) to specify:\n'+
                                     f'.{attr}·field or .{attr}·method')
            else:
                hint_is_field = not has_method #if we don't have either, assume field and fail later (to be consistent with hint behaviour)
        
        if hint_is_field:
            if self.__use_static__:
                args = (self.__bridge__, None, attr)
            else:
                args = (self.__bridge__.clazz, self.__bridge__, attr)
                
            obj, primitive = wrap(bridge.get_field(*args))
            if not primitive:
                obj.__parent__ = self
                obj.__attrname__ = attr
            return obj
        else:
            return MethodCaller(self, attr)

    def __call__(self, *args):
        return _call(self.__parent__, self.__attrname__, *args)

    def __setattr__(self, attr, value):
        if attr in self.__dict__:
            self.__dict__[attr] = value
            return
        bridge.set_field(self.__bridge__.clazz, self.__bridge__, attr, unwrap(value))

    def __invert__(self):
        cls = wrap(self.__bridge__.clazz)[0]
        cls.__use_static__ = False
        return cls

    def __repr__(self):
        return repr(self.__bridge__)

    def __dir__(self):
        return dir(self.__bridge__.clazz)


class NullType(Object):
    def __init__(self):
        super().__init__(bridge.JNULL)
    def __eq__(self, other):
        return other is NullType or isinstance(other, NullType) or other is None
    def __bool__(self):
        return False
    def __getattr__(self, attr):
        raise AttributeError('Object is null')
    def __setattr__(self, attr, value):
        if attr in self.__dict__:
            self.__dict__[attr] = value
            return
        raise AttributeError('Object is null')
    def __invert__(self):
        return self
    def __repr__(self):
        return 'Null'
    def __str__(self):
        return repr(self)

Null = NullType()

class Class(Object):
    def __init__(self, *args, array_element_class=None, **kwargs):
        super().__init__(*args, use_static=True, **kwargs)
        self.__dict__['array_element_class'] = array_element_class

    def __call__(self, *args):
        if self.array_element_class is not None:
            return make_array(self.array_element_class.__bridge__, *args)
        else:
            return wrap(bridge.call_method(self.__bridge__, None, '', *unwrap_args(args)))[0]

    def __lshift__(self, obj):
        return wrap(bridge.cast(bridge.box_python(unwrap(obj)), self.__bridge__))[0]
        
    def __getitem__(self, key):
        if not isinstance(key, tuple) or len(key) != 0:
            raise ValueError('must be ()')
        return Class(bridge.array_of_class(self.__bridge__), array_element_class=self)
        
    @property
    def name(self):
        return self.__bridge__.class_name

    def __dir__(self):
        return dir(self.__bridge__)

def make_array(element_bridge_class, l):
    if not isinstance(l, int):
        items = list(l)
        l = len(items)
    else:
        items = None

    arr = wrap(bridge.make_array(l, element_bridge_class))[0]
    if items is not None:
        arr[:] = items
    return arr

class Array(Object):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.__dict__['length'] = self.__bridge__.length
        
    def __eq__(self, other):
        return self[:] == other
        
    def __bool__(self):
        return bool(self[:])

    def __len__(self):
        return self.__bridge__.length

    def __getitem__(self, key):
        items = self.__bridge__[key]
        if isinstance(key, slice):
            return tuple(wrap(item)[0] for item in items)
        else:
            return wrap(items)[0]

    def __setitem__(self, key, value):
        if isinstance(key, slice):
            value = unwrap_args(value)
        else:
            value = unwrap(value)
        self.__bridge__[key] = value

class ByteArray(Array):
    def value(self):
        return bytes(self[:])

class CharArray(Array):
    def value(self):
        return ''.join(chr(c) for c in self[:])

class primitive_array_creator:
    def __init__(self, code, array_dim=1):
        self.code = code
        self.array_dim = array_dim

    def __call__(self, *args):
        if self.array_dim == 1:
            return make_array(self.code, *args)

        element_cls = bridge.find_primitive_array(self.code)
        for _ in range(self.array_dim - 2):
            element_cls = bridge.array_of_class(element_cls)
        return make_array(element_cls, *args)

    def __getitem__(self, key):
        if not isinstance(key, tuple) or len(key) != 0:
            raise ValueError('must be ()')
        return primitive_array_creator(self.code, self.array_dim + 1)

class jprimitive:
    def __init__(self, bridge_class):
        self.bridge_class = bridge_class
        self.code = self.bridge_class.code
    def __call__(self, *args, **kwargs):
        return self.bridge_class(*args, **kwargs)
    def __getitem__(self, key):
        if not isinstance(key, tuple) or len(key) != 0:
            raise ValueError('must be ()')
        return primitive_array_creator(self.code)

def override(f):
    if hasattr(f, '__interface__'):
        return f
    def func(*args):
        return unwrap(f(*(wrap(arg)[0] for arg in args)))
    func.__interface__ = True
    return func

jboolean = jprimitive(bridge.jboolean)
jbyte = jprimitive(bridge.jbyte)
jchar = jprimitive(bridge.jchar)
jshort = jprimitive(bridge.jshort)
jint = jprimitive(bridge.jint)
jlong = jprimitive(bridge.jlong)
jfloat = jprimitive(bridge.jfloat)
jdouble = jprimitive(bridge.jdouble)

def jstring_ctor(x):
    return wrap(bridge.jstring.from_str(x))[0]
jstring = jstring_ctor

def path_new_cls_func(cls, *args):
    return cls(*args)
def path_new_arr_func(_, element_cls, *args):
    return make_array(element_cls.__bridge__, *args)

new = Path(cls_func=path_new_cls_func,
           arr_func=path_new_arr_func)

def path_clazz_cls_func(cls):
    return cls
def path_clazz_arr_func(arr_cls, _):
    return arr_cls
clazz = Path(cls_func=path_clazz_cls_func,
             arr_func=path_clazz_arr_func)
    
interface_cache = {}
class InterfaceBase:
    def __init__(self, *ifaces):
        self.ifaces += ifaces
        self.java_object = wrap(bridge.make_interface(self, unwrap_args(self.ifaces)))[0]

def implements(*ifaces):
    cache_key = tuple(iface.name for iface in ifaces)
    if cache_key in interface_cache:
        return interface_cache[cache_key]
    interface = type(str(cache_key), (InterfaceBase,), {'ifaces': tuple(ifaces)})
    interface_cache[cache_key] = interface
    return interface
        
def get_java_arg():
    return wrap(bridge.get_java_arg())[0]

def build_java_dict(obj):
    return wrap(bridge.build_java_dict(obj))[0]

def build_python_dict_from_java(java_obj):
    return bridge.build_python_dict_from_java(java_obj.__bridge__)

#TODO think about scope
#package = Path(path_func=set_package)

#====================================
def tests():
    def test1():
        Test = clazz.appy.Test()
        test = new.appy.Test()
        Inner = clazz.appy.Test.Inner()
        test2 = Test()

        assert(test.ins_value == 24)
        test.ins_value = 38
        assert(test.ins_value == 38)
        assert((~test.test_value).name == bridge.PACKAGE_NAME + '.Test.Test2')
        assert(test.test_value.ins_value == 11)
        test.test_value.ins_value = 59
        assert(test.test_value.ins_value == 59)

        assert(test.value·field == 18)
        assert(test.value·method() == 85)
        assert(Test.value·field == 18)
        assert(Test.value·method() == 85)

        arr = test.test_integer_array(13)
        assert(arr == (Null,) * 13)
        assert(arr[1] == Null)
        assert(arr[1:-2] == (Null,) * 10)
        arr[1] = 15
        assert(arr[1] == 15)
        arr[1:-2] = range(1,11)
        assert(arr[1:-2] == tuple(range(1,11)))
        arr[:4] = [50, 51, 52, 53]
        assert(arr == (tuple(range(50, 53 + 1)) + tuple(range(4, 10 + 1)) + (Null, Null)))
        assert(len(arr) == arr.length == 13)

        arr = new.appy.Test[()](0)
        assert(len(arr) == 0)
        arr = new.appy.Test[()]([new.appy.Test(), new.appy.Test()])
        assert(len(arr) == 2)
        arr = new.appy.Test[()][()]([new.appy.Test[()]([new.appy.Test(), new.appy.Test()])])
        assert(len(arr) == 1)
        assert(len(arr[0]) == 2)
        assert(jlong(13) == 13)
        assert(jlong[()](3) == (0,) * 3)
        assert(jlong[()]([jlong(1), jlong(2), jlong(3)]) == (1,2,3))
        mat = jlong[()][()]([
                                jlong[()]([jlong(1), jlong(2), jlong(3)]),
                                jlong[()]([jlong(4), jlong(5), jlong(6)]),
                                jlong[()]([jlong(7), jlong(8), jlong(9)])
                            ])
        assert(mat == ((1,2,3),(4,5,6),(7,8,9)))
        assert(jlong[()]([1, 2, 3]) == (1,2,3))
        assert(len((~test)[()]([test])) == 1)

    def test2():
        class Receiver(implements(clazz.appy.BroadcastInterface())):
            @override
            def onReceive(self, context, intent):
                print('action ', intent.getAction())

        receiver = new.appy.BroadcastInterfaceBridge(Receiver())

        start_time = time.time()
        for _ in range(1):
            filter = new.android.content.IntentFilter(clazz.android.content.Intent().ACTION_USER_PRESENT)
        end_time = time.time()

        d = end_time - start_time
        #get_widget_manager().registerReceiver(receiver, filter)

    def test3():
        obj = jstring('test')
        obj_cast = clazz.java.lang.CharSequence() << obj
        obj_cast2 = clazz.java.lang.CharSequence() << 'tes2t'
        Test = clazz.appy.Test()
        assert(Test.cast_test(obj) == 'string')
        assert(Test.cast_test(obj_cast) == 'charsequence')

    def test4():
        ret = bytes([i for i in range(256)])
        b = jbyte[()] ([i for i in range(256)])
        b2 = jbyte[()] (ret)

        assert(b.value() == ret)
        assert(b2.value() == ret)

        B = new.java.lang.Byte[()] ([(jbyte(i) if i % 2 == 0 else Null) for i in range(256)])

        try:
            B.value()
            assert(False)
        except TypeError:
            pass

        ret = 'abcdefghijklmnopqrstuvwxyz'
        c = jchar[()] ([ord(c) for c in ret])
        c2 = jchar[()] (list(ret))
        c3 = jchar[()] (ret)

        uni = 'א'
        c4 = jchar[()] (uni)

        assert(c.value() == ret)
        assert(c2.value() == ret)
        assert(c3.value() == ret)
        assert(c4.value() == uni)

        C = new.java.lang.Character[()] ([(jchar(chr(i)) if i % 2 == 0 else Null) for i in range(256)])

        try:
            C.value()
            assert(False)
        except TypeError:
            pass




    bridge.tests()
    test1()
    test2()
    test3()
    test4()
    print('==================java tests end==================')

