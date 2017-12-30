import logcat
import bridge

#TODO wrap and unwrap
def wrap(obj, *args, **kwargs):
    if not isinstance(obj, bridge.jobjectbase):
        return obj

    if isinstance(obj, bridge.array):
        return Array(obj, *args, **kwargs)

    if isinstance(obj, bridge.jclass):
        return Class(obj, *args, **kwargs)

    if isinstance(obj, bridge.jobject):
        return Object(obj, *args, **kwargs)

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
        return wrap(bridge.call_method(bridge.find_class(self.path), None, '', *unwrap_args(args)))

class Object:
    def __init__(self, bridge_obj, attr=None):
        self.__dict__['bridge'] = bridge_obj
        self.__dict__['attr'] = attr

    def __getattr__(self, attr):
        if self.attr is not None:
            #get field
            return wrap(bridge.get_field(self.bridge.clazz, self.bridge, self.attr), attr=attr)
        else:
            return Object(self.bridge, attr=attr)

    def __pos__(self):
        if self.attr is not None:
            #get field
            return wrap(bridge.get_field(self.bridge.clazz, self.bridge, self.attr))
        raise ValueError('invalid')

    def __call__(self, *args):
        if self.attr is not None:
            #call method
            return wrap(bridge.call_method(self.bridge.clazz, self.bridge, self.attr, *unwrap_args(args)))
        raise ValueError('invalid')

    def __setattr__(self, attr, value):
        if self.attr is not None:
            self = wrap(bridge.get_field(self.bridge.clazz, self.bridge, self.attr))
        wrap(bridge.set_field(self.bridge.clazz, self.bridge, attr, unwrap(value)))

    def __invert__(self):
        if self.attr is not None:
            #get field
            self = wrap(bridge.get_field(self.bridge.clazz, self.bridge, self.attr))
        return wrap(self.bridge.clazz)

    def __repr__(self):
        return repr(self.bridge)



class Class(Object):
    def __call__(self, *args):
        return wrap(bridge.call_method(self.bridge, None, '', *unwrap_args(args)))

class Array(Object):
    def __len__(self):
        return self.bridge.length

new = New('')

test = new.com.happy.Test()

print(test.ins_value)
test.ins_value = 38
print(test.ins_value)
print(~test.test_value)
print(test.test_value.ins_value)
test.test_value.ins_value = 59
print(test.test_value.ins_value)