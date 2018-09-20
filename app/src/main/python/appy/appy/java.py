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
            
    if expr[0].opname not in ('LOAD_ATTR', 'LOAD_METHOD'):
        #shouldn't happen
        return False
    #argnum is the source arg in LOAD_METHOD
    argnum = None if expr[0].opname == 'LOAD_METHOD' else expr[0].arg
    for instr in expr[1:]:
        if argnum is None:
            #searching for CALL_METHOD until another LOAD_METHOD
            if instr.opname == 'LOAD_METHOD':
                return False
            if instr.opname == 'CALL_METHOD':
                return True
        elif instr.arg == argnum:
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
    if obj is None:
        return Null(obj, *args, **kwargs), False

    if not isinstance(obj, bridge.jobjectbase):
        return obj, True

    if isinstance(obj, bridge.array):
        return Array(obj, *args, **kwargs), False

    if isinstance(obj, bridge.jclass):
        return Class(obj, *args, **kwargs), False

    return Object(obj, *args, **kwargs), False

def unwrap(obj):
    if isinstance(obj, (Object, Class, Array)):
        return obj.bridge
    if isinstance(obj, InterfaceBase):
        return obj.java_object.bridge
    if isinstance(obj, UnknownField):
        raise RuntimeError('field does not exists')
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
        self.path_func = path_func
        self.cls_func = cls_func
        self.arr_func = arr_func
        self.path = path
        self.array_dim = array_dim

    def __getattr__(self, attr):
        if self.array_dim != 0:
            raise ValueError('invalid path')
        return Path(cls_func=self.cls_func, arr_func=self.arr_func, path=attr if not self.path else f'{self.path}.{attr}')

    def __call__(self, *args):
        if self.path_func is not None:
            return self.path_func(self.path, *args)
        cls = find_class_with_inner(self.path)
        if self.array_dim == 0:
            if self.cls_func is None:
                raise RuntimeError('operation not supported')
            return self.cls_func(Class(cls), *args)
        else:
            if self.arr_func is None:
                raise RuntimeError('operation not supported')
            element_cls = cls
            for _ in range(self.array_dim - 1):
                element_cls = bridge.array_of_class(element_cls)
            arr_cls = bridge.array_of_class(element_cls)
            wrapped_element_cls = Class(element_cls)
            return self.arr_func(Class(arr_cls, array_element_class=wrapped_element_cls), wrapped_element_cls, *args)

    def __getitem__(self, key):
        if not isinstance(key, tuple) or len(key) != 0:
            raise ValueError('must be ()')
        return Path(cls_func=self.cls_func, arr_func=self.arr_func, path=self.path, array_dim=self.array_dim + 1)

def _call(parent, attrname, *args):
    if parent.use_static:
        return wrap(bridge.call_method(parent.bridge, None, attrname, *unwrap_args(args)))[0]
    else:
        return wrap(bridge.call_method(parent.bridge.clazz, parent.bridge, attrname, *unwrap_args(args)))[0]

class UnknownField:
    def __init__(self, parent, attrname):
        self.parent = parent
        self.attrname = attrname

    def __call__(self, *args):
        return _call(self.parent, self.attrname, *args)

class Object:
    def __init__(self, bridge_obj, use_static=False):
        self.__dict__['bridge'] = bridge_obj
        self.__dict__['use_static'] = use_static
        self.__dict__['__parent__'] = None
        self.__dict__['__attrname__'] = None

    def __getattr__(self, attr):
        try:
            if self.use_static:
                obj, primitive = wrap(bridge.get_field(self.bridge, None, attr))
            else:
                obj, primitive = wrap(bridge.get_field(self.bridge.clazz, self.bridge, attr))
            if primitive:
                if is_calling():
                    return UnknownField(self, attr)
                else:
                    return obj
                if type(obj) not in primitive_wraps:
                    raise ValueError(f'primitive {type(obj)} does not have a wrapper')
                obj = primitive_wraps[type(obj)](obj)
                obj.__jparent__ = self
                obj.__jattrname__ = attr
            else:
                obj.__parent__ = self
                obj.__attrname__ = attr
            return obj
        except RuntimeError:
            return UnknownField(self, attr)

    def __call__(self, *args):
        return _call(self.__parent__, self.__attrname__, *args)

    def __setattr__(self, attr, value):
        if attr in self.__dict__:
            self.__dict__[attr] = value
            return
        bridge.set_field(self.bridge.clazz, self.bridge, attr, unwrap(value))

    def __invert__(self):
        cls = wrap(self.bridge.clazz)[0]
        cls.use_static = False
        return cls

    def __repr__(self):
        return repr(self.bridge)


class Null(Object):
    def __eq__(self, other):
        return other == Null or isinstance(other, Null) or other is None
    def __bool__(self):
        return False
    def __getattr__(self, item):
        raise ValueError('Object is null')
    def __setattr__(self, item, value):
        raise ValueError('Object is null')
    def __invert__(self):
        return self
    def __repr__(self):
        return 'Null'
    def __str__(self):
        return repr(self)

class Class(Object):
    def __init__(self, *args, array_element_class=None, **kwargs):
        super().__init__(*args, use_static=True, **kwargs)
        self.__dict__['array_element_class'] = array_element_class

    def __call__(self, *args):
        if self.array_element_class is not None:
            return make_array(self.array_element_class.bridge, *args)
        else:
            return wrap(bridge.call_method(self.bridge, None, '', *unwrap_args(args)))[0]

    def __lshift__(self, obj):
        return bridge.cast(bridge.box_python(unwrap(obj)), self.bridge)
        
    def __getitem__(self, key):
        if not isinstance(key, tuple) or len(key) != 0:
            raise ValueError('must be ()')
        return Class(bridge.array_of_class(self.bridge), array_element_class=self)
        
    @property
    def name(self):
        return self.bridge.class_name

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
        self.__dict__['length'] = self.bridge.length
        
    def __eq__(self, other):
        return self[:] == other
        
    def __bool__(self):
        return bool(self[:])

    def __len__(self):
        return self.bridge.length

    def __getitem__(self, key):
        items = self.bridge[key]
        if isinstance(key, slice):
            return tuple(wrap(item)[0] for item in items)
        else:
            return wrap(items)[0]

    def __setitem__(self, key, value):
        if isinstance(key, slice):
            value = unwrap_args(value)
        else:
            value = unwrap(value)
        self.bridge[key] = value

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
jstring = lambda x: wrap(bridge.jstring.from_str(x))[0]

new = Path(cls_func=lambda cls, *args: cls(*args),
           arr_func=lambda _, element_cls, *args: make_array(element_cls.bridge, *args))

clazz = Path(cls_func=lambda cls: cls,
             arr_func=lambda arr_cls, _: arr_cls)
    
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

#TODO think about scope
#package = Path(path_func=set_package)

#====================================
def tests():
    def test1():
        Test = clazz.appy.Test()
        test = new.appy.Test()
        Inner = clazz.appy.Test.Inner()
        test2 = Test()
        print(type(test2))
        print(test, test2)

        assert(test.ins_value == 24)
        test.ins_value = 38
        assert(test.ins_value == 38)
        assert((~test.test_value).name == bridge.PACKAGE_NAME + '.Test.Test2')
        assert(test.test_value.ins_value == 11)
        test.test_value.ins_value = 59
        assert(test.test_value.ins_value == 59)

        assert(test.value == 18)
        assert(test.value() == 85)
        assert(Test.value == 18)
        assert(Test.value() == 85)

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

    bridge.tests()
    test1()
    test2()
    test3()
    print('==================java tests end==================')

