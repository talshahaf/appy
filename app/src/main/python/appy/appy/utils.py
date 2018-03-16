import base64
import pickle
import inspect

def cap(s):
    return s[0].upper() + s[1:]

def dumps(obj):
    return base64.b64encode(pickle.dumps(obj, protocol=pickle.HIGHEST_PROTOCOL)).decode()

def loads(data):
    return pickle.loads(base64.b64decode(data.encode()))

def get_args(f):
    args, varargs, varkw, defaults = inspect.getargspec(f)
    kwargs = []
    if defaults:
        args = args[:-len(defaults)]
        kwargs = args[-len(defaults):]
    return args, kwargs, varargs is not None, varkw is not None

class AttrDict(dict):
    def __getattr__(self, item):
        try:
            return self.__getitem__(item)
        except KeyError:
            raise AttributeError()

    def __setattr__(self, key, value):
        try:
            return self.__setitem__(key, value)
        except KeyError:
            raise AttributeError()

    @classmethod
    def make(cls, d):
        if isinstance(d, dict):
            return AttrDict({k: cls.make(v) for k,v in d.items()})
        elif isinstance(d, (list, tuple, set)):
            return type(d)(cls.make(v) for v in d)
        else:
            return d