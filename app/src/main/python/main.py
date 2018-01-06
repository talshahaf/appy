import logcat
import bridge

primitive_wraps = {}

def wrap(obj, *args, **kwargs):
    if obj is None:
        return Null(obj, *args, **kwargs), False

    if not isinstance(obj, bridge.jobjectbase):
        return obj, True

    if isinstance(obj, bridge.array):
        return Array(obj, *args, **kwargs), False

    if isinstance(obj, bridge.jclass):
        return Class(obj, *args, **kwargs), False

    if isinstance(obj, bridge.jobject):
        return Object(obj, *args, **kwargs), False

def unwrap(obj):
    if isinstance(obj, (Object, Class, Array)):
        return obj.bridge
    return obj

def unwrap_args(args):
    return (unwrap(arg) for arg in args)

class New:
    def __init__(self, path):
        self.path = path

    def __getattr__(self, attr):
        return New(attr if not self.path else '{}.{}'.format(self.path, attr))

    def __call__(self, *args):
        return wrap(bridge.call_method(bridge.find_class(self.path), None, '', *unwrap_args(args)))[0]

    def __getitem__(self, key):
        if not isinstance(key, tuple) or len(key) != 0:
            raise ValueError('must be ()')
        clazz = bridge.find_class(self.path)
        return ClassArray(bridge.array_of_class(clazz), Class(clazz))

def _call(parent, attrname, *args):
    return wrap(bridge.call_method(parent.bridge.clazz, parent.bridge, attrname, *unwrap_args(args)))[0]

class UnknownField:
    def __init__(self, parent, attrname):
        self.parent = parent
        self.attrname = attrname

    def __call__(self, *args):
        return _call(self.parent, self.attrname, *args)

class Object:
    def __init__(self, bridge_obj):
        self.__dict__['bridge'] = bridge_obj

    def __getattr__(self, attr):
        try:
            obj, primitive = wrap(bridge.get_field(self.bridge.clazz, self.bridge, attr))
            if primitive:
                if type(obj) not in primitive_wraps:
                    primitive_wraps[type(obj)] = type('Wrapped_{}'.format(type(obj).__name__), (type(obj),),
                                                    dict(__call__=lambda self, *args: _call(self.__jparent__, self.__jattrname__, *args)))
                obj = primitive_wraps[type(obj)](obj)
                obj.__jparent__ = self
                obj.__jattrname__ = attr
            else:
                obj.__dict__['__parent__'] = self
                obj.__dict__['__attrname__'] = attr
            return obj
        except Exception as e: #TODO specific
            return UnknownField(self, attr)

    def __call__(self, *args):
        return _call(self.__parent__, self.__attrname__, *args)

    def __setattr__(self, attr, value):
        bridge.set_field(self.bridge.clazz, self.bridge, attr, unwrap(value))

    def __invert__(self):
        return wrap(self.bridge.clazz)[0]

    def __repr__(self):
        return repr(self.bridge)

class Null(Object):
    def __eq__(self, other):
        return other == Null or isinstance(other, Null)
    def __bool__(self):
        return False
    def __getattr__(self, item):
        raise ValueError('Object is null')
    def __setattr__(self, item, value):
        raise ValueError('Object is null')
    def __invert__(self):
        return self

class Class(Object):
    def __call__(self, *args):
        return wrap(bridge.call_method(self.bridge, None, '', *unwrap_args(args)))[0]

class ClassArray(Class):
    def __init__(self, bridge_obj, element_class):
        super().__init__(bridge_obj)
        self.__dict__['element_class'] = element_class

    def __call__(self, l):
        if not isinstance(l, int):
            items = list(l)
            l = len(items)
        else:
            items = None
        arr = wrap(bridge.make_array(l, self.element_class.bridge))[0]
        if items is not None:
            arr[:] = items
        return arr

    def __getitem__(self, key):
        if not isinstance(key, tuple) or len(key) != 0:
            raise ValueError('must be ()')
        return ClassArray(bridge.array_of_class(self.bridge), self)

    def __repr__(self):
        return 'array class of {}'.format(self.bridge)

class Array(Object):
    def __len__(self):
        return self.bridge.length

    def __getitem__(self, key):
        return self.bridge[key]

    def __setitem__(self, key, value):
        if isinstance(key, slice):
            value = unwrap_args(value)
        else:
            value = unwrap(value)
        self.bridge[key] = value

class jprimitive:
    def __init__(self, bridge_class):
        self.bridge_class = bridge_class
        self.bridge = self.bridge_class.code
    def __call__(self, *args, **kwargs):
        return self.bridge_class(*args, **kwargs)
    def __getitem__(self, key):
        if not isinstance(key, tuple) or len(key) != 0:
            raise ValueError('must be ()')
        return ClassArray(bridge.find_primitive_array(self.bridge), self)

jlong = jprimitive(bridge.jboolean)
jbyte = jprimitive(bridge.jbyte)
jchar = jprimitive(bridge.jchar)
jshort = jprimitive(bridge.jshort)
jint = jprimitive(bridge.jint)
jlong = jprimitive(bridge.jlong)
jfloat = jprimitive(bridge.jfloat)
jdouble = jprimitive(bridge.jdouble)
new = New('')

#====================================
def test1():
    test = new.com.happy.Test()

    print(test.ins_value)
    test.ins_value = 38
    print(test.ins_value)
    print(~test.test_value)
    print(test.test_value.ins_value)
    test.test_value.ins_value = 59
    print(test.test_value.ins_value)
    print('=====')
    print(test.value)
    print(test.value())

    arr = test.test_integer_array(13)
    print(arr[1], arr[1:-2])
    arr[1] = 15
    arr[1:-2] = range(1,11)
    arr[:4] = [50, 51, 52, 53]
    print(arr[1], arr[1:-2])
    print(arr)
    print(arr.length)
    print('===')
    print(new.com.happy.Test[()])
    print(new.com.happy.Test[()]([new.com.happy.Test(), new.com.happy.Test()]))
    print(new.com.happy.Test[()][()]([new.com.happy.Test[()]([new.com.happy.Test(), new.com.happy.Test()])]))
    print(jlong(13))
    print(jlong[()](3))
    print(jlong[()]([jlong(1), jlong(2), jlong(3)]))
    print(jlong[()][()]([
                            jlong[()]([jlong(1), jlong(2), jlong(3)]),
                            jlong[()]([jlong(4), jlong(5), jlong(6)]),
                            jlong[()]([jlong(7), jlong(8), jlong(9)])
                        ]))
    #bridge.tests()
    #nul = test.null_test
    #print(type(nul), nul == Null, nul == nul, nul is Null, nul, test.null_test, test.null_test())
    #print(nul.get)
    #print(nul.get())
    #nul.set = 3

def test2():
    test = new.com.happy.Test()
    print('callback test returned: ', test.test_callback(test))

test2()