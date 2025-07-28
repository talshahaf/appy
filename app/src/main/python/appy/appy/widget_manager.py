import json, functools, copy, traceback, inspect, threading, os, collections, importlib.util, sys, hashlib, struct, re, time, faulthandler, base64, io
from .utils import AttrDict, dumps, loads, cap, get_args, prepare_image_cache_dir, preferred_script_dir, timeit
from . import widgets, java, state, configs, __version__

def gen_id():
    id = 0
    while id in (0, -1):
        id = struct.unpack('<q', os.urandom(8))[0]
    return id

@functools.lru_cache(maxsize=128, typed=True)
def validate_type(type):
    return java.clazz.appy.Constants().typeToClass.containsKey(type)

def method_from_attr(attr):
    return f'set{cap(attr)}'

@functools.lru_cache(maxsize=128, typed=True)
def get_param_setter(type, attr):
    method = method_from_attr(attr)
    setter = java.clazz.appy.Constants().getSetterMethod(type, method)
    return setter if setter != java.Null else None, method

@functools.lru_cache(maxsize=128, typed=True)
def validate_remoteviews_method(method):
    return java.clazz.appy.RemoteMethodCall().remoteViewMethods.containsKey(method)

@functools.lru_cache(maxsize=128, typed=True)
def unit_constants():
    dp_to_px = float(java_widget_manager.convertUnit(1.0, java.clazz.android.util.TypedValue().COMPLEX_UNIT_DIP, java.clazz.android.util.TypedValue().COMPLEX_UNIT_PX))
    sp_to_px = float(java_widget_manager.convertUnit(1.0, java.clazz.android.util.TypedValue().COMPLEX_UNIT_SP,  java.clazz.android.util.TypedValue().COMPLEX_UNIT_PX))
    pt_to_px = float(java_widget_manager.convertUnit(1.0, java.clazz.android.util.TypedValue().COMPLEX_UNIT_PT,  java.clazz.android.util.TypedValue().COMPLEX_UNIT_PX))
    in_to_px = float(java_widget_manager.convertUnit(1.0, java.clazz.android.util.TypedValue().COMPLEX_UNIT_IN,  java.clazz.android.util.TypedValue().COMPLEX_UNIT_PX))
    mm_to_px = float(java_widget_manager.convertUnit(1.0, java.clazz.android.util.TypedValue().COMPLEX_UNIT_MM,  java.clazz.android.util.TypedValue().COMPLEX_UNIT_PX))
    return {'px': 1.0, 'dp': dp_to_px, 'dip': dp_to_px, 'sp': sp_to_px, 'pt': pt_to_px, 'in': in_to_px, 'mm': mm_to_px}

def parse_unit(value):
    if not isinstance(value, str):
        return value
    reg = re.match(r'^([+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+))\s*\*?\s*([a-zA-Z]+)$', value)
    if reg:
        value, unit = reg.groups()
        unit = unit.lower()
        units = unit_constants()
        if unit in units:
            return float(value) * units[unit]
    return float(value)

class Reference:
    def __init__(self, id, key):
        self.id = id
        self.key = key

    def compile(self):
        return dict(id=self.id, type=self.key)

class WaitingForElse:
    def __init__(self, cond, t):
        self.cond = cond
        self.t = t

    def else_(self, f):
        return self.cond.raw_if_(self.t, f)

class AttributeValue:
    def __init__(self, f, *args):
        self.debug_name = None
        self.args = []
        for arg in args:
            if isinstance(arg, self.__class__):
                if arg.f == 'I':
                    # collapse identities
                    self.args.append(arg.args[0])
                else:
                    self.args.append(arg)
            else:
                self.args.append(parse_unit(arg))
        self.args = tuple(self.args)
        self.f = f if f else 'I'
        if self.f == 'I' and len(self.args) != 1:
            raise ValueError(f'Cannot have identity AttributeValue with {len(self.args)} args')

    @classmethod
    def wrap(cls, n):
        return cls(None, n)

    def compile_(self):
        lst = []
        for arg in self.args:
            if isinstance(arg, self.__class__):
                lst.extend(arg.compile_())
            elif isinstance(arg, Reference):
                lst.append(arg.compile())
            elif isinstance(arg, int) or isinstance(arg, float):
                lst.append(dict(value=float(arg)))
            elif isinstance(arg, Element) and arg.d.type == 'Empty':
                # auto take misc attribute in Empty
                lst.extend(arg.misc.compile_())
            else:
                raise ValueError(f'Attribute cannot compile {type(arg)}')
        lst.append(dict(func=self.f, num=len(self.args))) #pos is filled later
        return lst

    def compile(self):
        lst = self.compile_()
        nonfuncs = []
        funcs = []
        for e in lst:
            if isinstance(e, dict):
                if 'func' in e:
                    funcs.append(dict(type=e['func'], num=e['num'], pos=len(nonfuncs)))
                elif 'id' in e:
                    nonfuncs.append(dict(id=e['id'], type=e['type']))
                elif 'value' in e:
                    nonfuncs.append(dict(value=e['value']))
                else:
                    raise ValueError(f'unknown dict in AttributeValue')
        return dict(arguments=nonfuncs, functions=funcs, **(dict(debug_name=self.debug_name) if self.debug_name else {}))

    @classmethod
    def min(cls, *args):
        return cls('MIN', *args)
    @classmethod
    def max(cls, *args):
        return cls('MAX', *args)

    def debug_print(self, name):
        self.debug_name = name
        return self

    def __add__(self, other):
        return self.__class__('ADD', self, other)
    def __mul__(self, other):
        return self.__class__('MUL', self, other)
    def __truediv__(self, other):
        return self.__class__('DIV', self, other)
    def __mod__(self, other):
        return self.__class__('MOD', self, other)
    def floor(self):
        return self.__class__('FLOOR', self)
    def ceil(self):
        return self.__class__('CEIL', self)
    def raw_if_(self, t, f):
        return self.__class__('IF', self, t, f)

    def if_(self, cond):
        return WaitingForElse(cond, self)

    def __invert__(self):
        return self.__class__('NOT', self)
    def __and__(self, other):
        return self.__class__('AND', self, other)
    def __or__(self, other):
        return self.__class__('OR', self, other)
    def __eq__(self, other):
        return self.__class__('EQ', self, other)
    def __lt__(self, other):
        return self.__class__('LT', self, other)
    def __le__(self, other):
        return self.__class__('LE', self, other)

    def __neg__(self):
        return self * (-1)
    def __sub__(self, other):
        return self + (-other)
    def __floordiv__(self, other):
        return (self / other).floor()

    def __ne__(self, other):
        return ~(self == other)
    def __gt__(self, other):
        return ~(self <= other)
    def __ge__(self, other):
        return ~(self < other)
    def __xor__(self, other):
        return (self | other) & (~(self & other))

    def __radd__(self, other):
        return self + other
    def __rsub__(self, other):
        return (-self) + other
    def __rmul__(self, other):
        return self * other
    def __rtruediv__(self, other):
        return self.__class__('DIV', other, self)
    def __rfloordiv__(self, other):
        return self.__rtruediv__(other).floor()
    def __rmod__(self, other):
        return self.__class__('MOD', other, self)
    def __rand__(self, other):
        return self & other
    def __ror__(self, other):
        return self | other
    def __rxor__(self, other):
        return self ^ other


def attribute_ileft(e):
    return e.right + e.width
def attribute_itop(e):
    return e.bottom + e.height
def attribute_iright(e):
    return e.left + e.width
def attribute_ibottom(e):
    return e.top + e.height
def attribute_hcenter(e):
    return e.left + (e.width / 2)
def attribute_vcenter(e):
    return e.top + (e.height / 2)
def attribute_ihcenter(e):
    return e.right + (e.width / 2)
def attribute_ivcenter(e):
    return e.bottom + (e.height / 2)
def attribute_center(e):
    return (attribute_hcenter(e), attribute_vcenter(e))
def attribute_icenter(e):
    return (attribute_ihcenter(e), attribute_ivcenter(e))
def attribute_write_hcenter(e, value):
    if value is None:
        del e.left
    else:
        debug_name = getattr(value, 'debug_name', '')
        attr = -(e.width / 2) + value
        if debug_name:
            attr.debug_print(debug_name)
        e.left = attr
def attribute_write_vcenter(e, value):
    if value is None:
        del e.top
    else:
        debug_name = getattr(value, 'debug_name', '')
        attr = -(e.height / 2) + value
        if debug_name:
            attr.debug_print(debug_name)
        e.top = attr
def attribute_write_center(e, value):
    h, v = value
    attribute_write_hcenter(e, h)
    attribute_write_vcenter(e, v)

attrs = dict(left='LEFT', top='TOP', right='RIGHT', bottom='BOTTOM', width='WIDTH', height='HEIGHT', misc='MISC')
composite_attrs = dict(ileft=attribute_ileft, itop=attribute_itop, iright=attribute_iright, ibottom=attribute_ibottom,
                       hcenter=attribute_hcenter, vcenter=attribute_vcenter, center=attribute_center, ihcenter=attribute_ihcenter, ivcenter=attribute_ivcenter, icenter=attribute_icenter)
write_attrs = dict(hcenter=attribute_write_hcenter, vcenter=attribute_write_vcenter, center=attribute_write_center)
class WidgetAttribute:
    def __getattr__(self, item):
        if item in attrs:
            return AttributeValue(None, Reference(-1, attrs[item]))
        if item in composite_attrs:
            return composite_attrs[item](self)
        raise AttributeError(item)

class BaseR:
    def __init__(self, path):
        self.__path = path

    def __getattr__(self, key):
        if key.startswith('__'):
            return object.__getattribute__(self, key)
        return self.__class__(self.__path + [key])

    def export_to_views(self):
        return f'xml.resource.{".".join(self.__path)}'

    def resolve_now(self):
        cls_res = java.clazz
        for part in self.__path[:-1]:
            cls_res = getattr(cls_res, part)
        return getattr(cls_res(), self.__path[-1])

    def __java__(self):
        return self.resolve_now()

    @classmethod
    def resolve_color(cls, res):
        return java_context().getResources().getColor(res, None)

androidR = BaseR(['android', 'R'])
R = BaseR(['appy', 'R'])

last_func_for_widget_id = {}
def call_function(func, captures, **kwargs):
    #for tracing errors to their module
    if 'widget' in kwargs and hasattr(kwargs['widget'], 'widget_id'):
        last_func_for_widget_id[kwargs['widget'].widget_id] = func

    if not isinstance(captures, dict):
        raise ValueError('Captures must be a keyword dict')

    pass_args = dict(captures)
    pass_args.update(kwargs) #kwargs priority

    args, kwargs, has_vargs, has_vkwargs = get_args(func)
    try:
        return func(**{k:v for k,v in pass_args.items() if k in args or k in kwargs or has_vkwargs})
    except:
        set_module_error(inspect.getmodule(func), traceback.format_exc())
        raise

def call_general_function(func, **kwargs):
    if not isinstance(func, (list, tuple)):
        if not hasattr(func, '__call__'):
            raise ValueError('Only <Callable> or (<Callable>, <Capture Dict>) are supported.')
        func = (func, {})
    elif len(func) != 2 or not isinstance(func[1], dict):
        raise ValueError('Only <Callable> or (<Callable>, <Capture Dict>) are supported.')
    return call_function(func[0], func[1], **kwargs)
    
def dump_general_function(func, captures):
    #handles f containing captures itself as well, for backward compatibility
    if isinstance(func, (list, tuple)) and len(func) == 2:
        if not hasattr(func[0], '__call__') or not isinstance(func[1], dict):
            raise ValueError('Only <Callable> or (<Callable>, <Capture Dict>) are supported.')
        
        captures = dict(**func[1], **captures) #captures take precedence
        f = func[0]
    elif hasattr(func, '__call__'):
        f = func
    else:
        raise ValueError('Only <Callable> or (<Callable>, <Capture Dict>) are supported.')
    
    return dumps((f, captures))

def deserialize_arg(arg):
    if not isinstance(arg, dict):
        return arg
    if arg['type'] == 'null':
        return None
    if arg['type'] == 'primitive':
        return arg['value']

    #gotta go to java
    return java.clazz.appy.RemoteMethodCall().parameterFromDict(java.build_java_dict(arg))

def serialize_arg(arg):
    #probably already serialized
    if isinstance(arg, dict):
        return arg

    if arg is None or arg == java.Null:
        return AttrDict(type='null')

    if not isinstance(arg, java.Object):
        return AttrDict(type='primitive', value=arg)

    #gotta go to java
    return java.build_python_dict_from_java(java.clazz.appy.RemoteMethodCall().parameterToDict(arg))

def style_attr_parse(type, style):
    #parse
    parts = style.split('_')
    outline = parts[0] == 'outline'
    if outline:
        parts = parts[1:]

    name = parts[0]

    oval = len(parts) > 1 and parts[1] == 'oval'
    if oval:
        parts = parts[2:]
    else:
        parts = parts[1:]

    size = ''
    if parts:
        size = parts[0]

    #validate
    if name not in ['primary', 'secondary', 'success', 'danger', 'warning', 'info', 'light', 'dark']:
        raise ValueError(f'Unknown style color: {name}')
    if size not in ['', 'sml', 'lg']:
        raise ValueError(f'Unknown style size: {size}')

    drawable_name = f'drawable{'_outline' if outline else ''}_{name}_btn{'_oval' if oval else ''}'
    color_name = f'color{'_outline' if outline else ''}_{name}_text'

    #left top right bottom
    sizes_pads = {'': ('24sp', '16sp', '24sp', '16sp'),
                  'sml': ('14sp', '10sp', '14sp', '10sp'),
                  'lg': ('28sp', '16sp', '28sp', '16sp')}
    sizes_text = {'': '12sp',
                  'sml': '10sp',
                  'lg': '15sp'}

    attrs = dict(backgroundResource=getattr(R.drawable, drawable_name).export_to_views(), viewPadding=sizes_pads[size])
    if type == 'Button':
        attrs['textSize'] = sizes_text[size]
        attrs['textColor'] = getattr(R.color, color_name).export_to_views()
    return attrs

element_attr_aliases = dict(checked='compoundButtonChecked',
                            compoundDrawables='textViewCompoundDrawables',
                            compoundDrawablesRelative='textViewCompoundDrawablesRelative',
                            padding='viewPadding')
element_event_hooks = {} #global for all
class Element:
    __slots__ = ('d',)
    def __init__(self, d):
        self.init(d)

    def init(self, d):
        self.d = AttrDict.make(d)
        if 'id' not in self.d:
            self.d.id = gen_id()
        if 'methodCalls' in self.d:
            self.d.methodCalls = collections.OrderedDict((c.identifier, c) for c in self.d.methodCalls)
        if 'tag' not in self.d:
            self.d.tag = {}

        if 'children' in self.d:
            self.d.children = ChildrenList([c if isinstance(c, Element) else Element(c) for c in arr] for arr in self.d.children)
        else:
            self.d.children = ChildrenList()

    def __copy__(self):
        raise RuntimeError('Cannot copy Element object')

    def __deepcopy__(self, memo):
        raise RuntimeError('Cannot copy Element object')

    def __getstate__(self):
        raise RuntimeError('Cannot pickle Element object')

    def __setstate__(self, state):
        raise RuntimeError('Cannot pickle Element object')

    def __event__(self, key, **kwargs):
        event_hook = element_event_hooks.get(self.d.type, {}).get(key)
        if event_hook:
            event_hook(kwargs)
        if key in self.d.tag and self.d.tag[key] is not None:
            func = loads(self.d.tag[key])
            return call_general_function(func, **kwargs)

    def set_handler(self, key, f):
        self.d.tag[key] = dump_general_function(f, {}) if f is not None else None

    def __delattr__(self, key):
        if key in attrs:
            del self.d.attributes[attrs[key]]
        elif key in write_attrs:
            write_attrs[key](self, None)
        elif 'selectors' in self.d and key == 'alignment':
            del self.d.selectors['alignment']
        elif key == 'children':
            self.d[key].clear()
        elif key in ('paddingLeft', 'paddingTop', 'paddingRight', 'paddingBottom'):
            del self.d[key]
        elif key in element_attr_aliases:
            delattr(self, element_attr_aliases[key])
        elif key == 'tag':
            self.tag.clear()
        elif key == 'style':
            del self.d.tag['style']
            style_attrs = [k for k in self.d.methodCalls.keys() if k.startswith('style*')]
            for attr in style_attrs:
                del self.d.methodCalls[attr]
        elif key in ('name', 'click', 'itemclick'):
            del self.d.tag[key]
        else:
            # might throw
            del self.d.methodCalls[key]

    def __getattr__(self, item):
        if item in attrs:
            return AttributeValue(None, Reference(self.d.id, attrs[item]))
        if item in composite_attrs:
            return composite_attrs[item](self)
        if item in ('type', 'id', 'children'):
            return getattr(self.d, item)
        if item == 'alignment':
            return getattr(self.d.selectors, item)
        if item in element_attr_aliases:
            return getattr(self, element_attr_aliases[key])
        if item in ('paddingLeft', 'paddingTop', 'paddingRight', 'paddingBottom'):
            return getattr(self.d, item, 0)
        if self.d.type == 'Chronometer' and item in ('base', 'format', 'started'):
            try:
                return self.chronometer[('base', 'format', 'started').index(item)]
            except AttributeError:
                raise AttributeError(item)
        if item == 'tag':
            if 'tag' not in self.d.tag:
                self.d.tag['tag'] = AttrDict()
            if isinstance(self.d.tag['tag'], str):
                self.d.tag['tag'] = loads(self.d.tag['tag'])
            return self.d.tag['tag']

        if item in self.d.tag:
            attr = self.d.tag[item]
            if item in ('click', 'itemclick'):
                if attr is not None:
                    attr = loads(attr)
            return attr

        identifier = method_from_attr(item)
        if 'methodCalls' in self.d and identifier in self.d.methodCalls:
            method_call = self.d.methodCalls[identifier]
            args = method_call.arguments
            if method_call.identifier != method_call.method:
                args = args[1:]
            return deserialize_arg(args[0]) if len(args) == 1 else [deserialize_arg(arg) for arg in args]

        raise AttributeError(item)

    def __setattr__(self, key, value):
        #special handling of 'd' member allowing it to be set once and without using __dict__
        if key == 'd':
            already_set = False
            try:
                object.__getattribute__(self, key)
                already_set = True
            except AttributeError:
                pass
            if not already_set:
                object.__setattr__(self, key, value)
                return
            else:
                raise AttributeError(f'{key} can not be modified')

        if hasattr(value, 'export_to_views'):
            value = value.export_to_views()

        if key in attrs:
            if value is None:
                delattr(self, key)
            else:
                if not isinstance(value, AttributeValue):
                    value = AttributeValue(None, value)
                if 'attributes' not in self.d:
                    self.d.attributes = {}
                self.d.attributes[attrs[key]] = value.compile()
        elif key in write_attrs:
            write_attrs[key](self, value)
        elif key in ('click', 'itemclick'):
            self.set_handler(key, value)
        elif key == 'name':
            self.d.tag['name'] = value
        elif key == 'tag':
            # goes to self.d.tag['tag'] through getattr
            self.tag.clear()
            self.tag.update(value)
        elif key in ('alignment', 'mode'):
            if 'selectors' not in self.d:
                self.d.selectors = {}
            self.d.selectors[key] = value
        elif key == 'style':
            style_attrs = style_attr_parse(self.d.type, value)
            for k,v in style_attrs.items():
                self.add_method_call('style', k, v, True) #order at start so it can be overwritten
            self.d.tag['style'] = value #save for getattr
        elif key == 'children':
            if value is None:
                value = ChildrenList()
            elif not isinstance(value, (list, tuple)):
                value = ChildrenList([value])
            else:
                value = ChildrenList(value)
            self.d[key].set(value)
        elif key in ('tint', 'backgroundTint'):
            background = key == 'backgroundTint'

            attr = 'backgroundTintList' if background else 'foregroundTintList'
            mode_attr = 'backgroundTintBlendMode' if background else 'foregroundTintBlendMode'
            param_setter, method = get_param_setter(self.d.type, attr)
            if param_setter is not None:
                 # android 9+
                 setattr(self, attr, value)

                 if get_param_setter(self.d.type, mode_attr)[0] is not None:
                    # android 10+
                    setattr(self, mode_attr, java.clazz.android.graphics.BlendMode().SRC)

            elif validate_remoteviews_method('setDrawableParameters'):
                # android 8-
                self.drawableParameters = (background, (value >> 24) & 0xff, value & 0xffffff, java.clazz.android.graphics.PorterDuff.Mode().SRC_ATOP, -1)
            elif validate_remoteviews_method('setDrawableTint'):
                # android 9+
                self.drawableTint = (background, value, java.clazz.android.graphics.PorterDuff.Mode().SRC)
        elif key in element_attr_aliases:
            setattr(self, element_attr_aliases[key], value)
        elif key in ('paddingLeft', 'paddingTop', 'paddingRight', 'paddingBottom'):
            self.d[key] = value
            self.viewPadding = (self.d.get('paddingLeft', 0), self.d.get('paddingTop', 0), self.d.get('paddingRight', 0), self.d.get('paddingBottom', 0))
        elif self.d.type == 'Chronometer' and key in ('base', 'format', 'started'):
            try:
                old_base, old_format, old_started = self.chronometer
            except AttributeError:
                old_base, old_format, old_started = 0, None, False
            self.chronometer = (value if key == 'base' else old_base, value if key == 'format' else old_format, value if key == 'started' else old_started)
        else:
            self.add_method_call('', key, value, False)

    def add_method_call(self, prefix, key, value, order_at_start):
        param_setter, method = get_param_setter(self.d.type, key)
        if param_setter is not None:
            identifier = method
            arguments = [method, value]
            method = param_setter
        else:
            if not validate_remoteviews_method(method):
                raise AttributeError(method)
            identifier = method
            if not isinstance(value, (list, tuple)):
                value = [value]
            arguments = value

        if 'methodCalls' not in self.d:
            self.d.methodCalls = collections.OrderedDict()

        if prefix:
            identifier = f'{prefix}*{identifier}'

        arguments = [serialize_arg(arg) for arg in arguments]
        self.d.methodCalls[identifier] = AttrDict(identifier=identifier, method=method, arguments=arguments)
        if order_at_start:
            self.d.methodCalls.move_to_end(identifier, False)

    @classmethod
    def create(cls, type, **kwargs):
        e = cls(dict(type=type))
        [setattr(e, k, v) for k,v in kwargs.items()]
        return e

    @classmethod
    def set_event_hooks(cls, type, event_hooks):
        element_event_hooks[type] = event_hooks

    def dict(self, do_copy, without_id=None):
        if 'tag' in self.d and 'tag' in self.d.tag and not isinstance(self.d.tag['tag'], str):
            self.d.tag['tag'] = dumps(self.d.tag['tag'])
        d = {k: (copy.deepcopy(v) if do_copy else v) for k,v in self.d.items() if k != 'children' and k != 'methodCalls' and (not without_id or k != 'id')}
        if 'methodCalls' in self.d:
            d['methodCalls'] = list(copy.deepcopy(v) for v in self.d.methodCalls.values())
        d['children'] = [[c.dict(do_copy=do_copy, without_id=without_id) if isinstance(c, Element) else c for c in arr] for arr in self.children]
        return d

    def duplicate(self):
        return Element(self.dict(do_copy=True, without_id=True))

    def __repr__(self):
        return repr(self.dict(do_copy=True))


def forward(attr):
    def h(f):
        @functools.wraps(f)
        def g(self, *args, **kwargs):
            ff = getattr(getattr(self, attr), f.__name__)
            return ff(*args, **kwargs)
        return g
    return h

class EmptyElement(Element):
    @classmethod
    def create(cls, attr):
        return super().create('Empty', misc=attr)

    #forward everything to misc
    @forward('misc')
    def min(self, *args):
        pass
    @forward('misc')
    def max(self, *args):
        pass
    @forward('misc')
    def debug_print(self, name):
        pass
    @forward('misc')
    def __add__(self, other):
        pass
    @forward('misc')
    def __mul__(self, other):
        pass
    @forward('misc')
    def __truediv__(self, other):
        pass
    @forward('misc')
    def __mod__(self, other):
        pass
    @forward('misc')
    def floor(self):
        pass
    @forward('misc')
    def ceil(self):
        pass
    @forward('misc')
    def if_(self, cond):
        pass
    @forward('misc')
    def __invert__(self):
        pass
    @forward('misc')
    def __and__(self, other):
        pass
    @forward('misc')
    def __or__(self, other):
        pass
    @forward('misc')
    def __eq__(self, other):
        pass
    @forward('misc')
    def __lt__(self, other):
        pass
    @forward('misc')
    def __le__(self, other):
        pass
    @forward('misc')
    def __neg__(self):
        pass
    @forward('misc')
    def __sub__(self, other):
        pass
    @forward('misc')
    def __floordiv__(self, other):
        pass
    @forward('misc')
    def __ne__(self, other):
        pass
    @forward('misc')
    def __gt__(self, other):
        pass
    @forward('misc')
    def __ge__(self, other):
        pass
    @forward('misc')
    def __xor__(self, other):
        pass
    @forward('misc')
    def __radd__(self, other):
        pass
    @forward('misc')
    def __rsub__(self, other):
        pass
    @forward('misc')
    def __rmul__(self, other):
        pass
    @forward('misc')
    def __rtruediv__(self, other):
        pass
    @forward('misc')
    def __rfloordiv__(self, other):
        pass
    @forward('misc')
    def __rmod__(self, other):
        pass
    @forward('misc')
    def __rand__(self, other):
        pass
    @forward('misc')
    def __ror__(self, other):
        pass
    @forward('misc')
    def __rxor__(self, other):
        pass

class elist(list):
    @classmethod
    def _all_rec(cls, lst):
        for e in lst:
            if isinstance(e, list):
                yield from cls._all_rec(e)
            else:
                yield e
                yield from cls._all_rec(e.children)

    def all(self):
        yield from self._all_rec(self)

    def _find_element(self, pred, hint=None):
        found = list(set(e for e in self.all() if pred(e)))
        if not found:
            raise KeyError(f'element {f"{hint} " if hint is not None else ""}not found')
        elif len(found) == 1:
            return found.pop()
        return found

    def find_name(self, name):
        return self._find_element((lambda name: (lambda e: getattr(e, 'name', None) == name))(name), f'named {name}') #capture name

    def find_id(self, id):
        return self._find_element((lambda id: (lambda e: getattr(e, 'id', None) == id))(id), f'with id {id}') #capture id

    def names(self):
        return list(set(getsttr(e, 'name', None) for e in self.all()))
    def ids(self):
            return list(set(getsttr(e, 'id', None) for e in self.all()))

    def __getitem__(self, item):
        try:
            return super().__getitem__(item)
        except TypeError:
            return self.find_name(item)

    def __contains__(self, item):
        try:
            if super().__contains__(item):
                return True
        except TypeError:
            pass
        try:
            self.find_name(item)
            return True
        except KeyError:
            pass
        return False

#children is list of lists
class ChildrenList(elist):
    def adapt(self, item):
        return elist(item if isinstance(item, (list, tuple)) else [item])

    def set(self, other):
        i = -1
        for i,e in enumerate(other):
            if len(self) <= i:
                self.append(e)
            else:
                self[i] = self.adapt(e)
        del self[i + 1:]

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.set(self)

    def append(self, item):
        super().append(self.adapt(item))

    def extend(self, iterable):
        super().extend(iterable)
        self.set(self)

    def remove(self, item):
        super().remove(self.adapt(item))

    def insert(self, i, item):
        super().insert(i, self.adapt(item))

    def index(self, item, *args, **kwargs):
        return super().index(self.adapt(item), *args, **kwargs)

    def count(self, item):
        return super().count(self.adapt(item))


widget_dims = WidgetAttribute()

available_widgets = {}

__importing_module = threading.local()

def __set_importing_module(path):
    __importing_module.path = path

def __clear_importing_module():
    __importing_module.path = None

def module_name(path):
    return f'{os.path.splitext(os.path.basename(path))[0]}_{int(hashlib.sha1(path.encode()).hexdigest(), 16) % (10 ** 8)}'

def set_module_error(module, error):
    if module is None:
        return None
    java_widget_manager.setFileLastError(module.__file__, error)
    return module.__file__

def set_unknown_error(error):
    java_widget_manager.setFileLastError(None, error)
    return None

def call_widget_chosen_listener(widget_id, name):
    java_widget_manager.callWidgetChosenListener(widget_id, name)

def load_module(path):
    __set_importing_module(path)
    clear_module(path)
    name = module_name(path)
    try:
        spec = importlib.util.spec_from_file_location(name, path)
        mod = importlib.util.module_from_spec(spec)
        sys.modules[name] = mod #for pickle
        spec.loader.exec_module(mod)
    except:
        sys.modules.pop(name, None)
        raise
    finally:
        __clear_importing_module()

def clear_module(path):
    global available_widgets
    mod = sys.modules.get(module_name(path))
    if mod and hasattr(mod, '__del__'):
        try:
            mod.__del__()
        except:
            #destructor should be nothrow
            print(traceback.format_exc())
    available_widgets = {k:v for k,v in available_widgets.items() if v['pythonfile'] != path}
    sys.modules.pop(module_name(path), None)

def obtain_manager_state():
    manager_state = state.State('__widgetmanager__', -1) #special own scope
    manager_state.locals('__token__')
    manager_state.locals('chosen')
    token = 1
    if getattr(manager_state, '__token__', None) != token:
        manager_state.chosen = {}
        manager_state.__token__ = token

    manager_state.setdefault('chosen', {})
    return manager_state

def create_widget(widget_id):
    manager_state = obtain_manager_state()
    manager_state.chosen.setdefault(widget_id, None)
    name = None
    if manager_state.chosen[widget_id] is not None:
        name = manager_state.chosen[widget_id].name
    widget = widgets.Widget(widget_id, name)
    return widget, manager_state
    
def get_widget_name(widget_id):
    manager_state = obtain_manager_state()
    if widget_id in manager_state.chosen and manager_state.chosen[widget_id] is not None:
        return manager_state.chosen[widget_id].name
    raise KeyError(f'no such widget {widget_id}')
    
def get_widgets_by_name(name):
    manager_state = obtain_manager_state()
    return [widget_id for widget_id, chosen in manager_state.chosen.items() if chosen is not None and chosen.name == name]

def get_all_widget_names():
    manager_state = obtain_manager_state()
    return {widget_id: (chosen.get('name') if chosen else None) for widget_id, chosen in manager_state.chosen.items()}
    
def choose_widget(widget, name):
    print(f'choosing widget: {widget.widget_id} -> {name}')
    manager_state = obtain_manager_state()
    manager_state.chosen[widget.widget_id] = AttrDict(name=name, inited=False)
    widget.set_loading()
    widget.invalidate()

def unchoose_widget(widget_id):
    widget, manager_state = create_widget(widget_id)
    state.clean_local_state(widget_id)
    widget.cancel_all_timers()
    manager_state.chosen.pop(widget_id, None)
    last_func_for_widget_id.pop(widget_id, None)

def recreate_widget(widget_id):
    manager_state = obtain_manager_state()
    if widget_id in manager_state.chosen:
        chosen = manager_state.chosen[widget_id]
        chosen.inited = False
        widget = widgets.Widget(widget_id, None)
        widget.cancel_all_timers()
        widget.invalidate()

def debug_button_click(widget):
    recreate_widget(widget.widget_id)

def parse_single_appinfo(arg):
    if isinstance(arg, str):
        #title
        return dict(title=arg)
    if isinstance(arg, bytes) or hasattr(arg, 'save'):
        #single icon
        return dict(icon=arg)
    if isinstance(arg, dict):
        #multiple icons
        return dict(icons=arg)

    raise ValueError(f'unknown appinfo arg: {type(arg)}')

def image_to_bytes(img, format='PNG'):
    buf = io.BytesIO()
    img.save(buf, format=format)
    return buf.getvalue()

def parse_appinfo(result):
    #can be just title, just icon (PIL, bytes), just icons (str -> icon dict), both (any order) or dict of {'title', 'icon'/'icons'}
    parsed = {}
    if isinstance(result, list) or isinstance(result, tuple):
        for e in result:
            arg = parse_single_appinfo(e)
            if any(k in parsed for k in arg.keys()):
                raise ValueError('ambiguous values for appinfo')
            parsed.update(arg)
    elif isinstance(result, dict):
        if any(k not in ('title', 'icon', 'icons') for k in result.keys()):
            parsed = parse_single_appinfo(result)
        else:
            parsed = result
    else:
        parsed = parse_single_appinfo(result)

    #validate
    if 'icon' in parsed and 'icons' in parsed:
        raise ValueError("supply either 'icon' or 'icons', not both")

    if 'icon' in parsed:
        parsed['icons'] = None if parsed['icon'] is None else {'': parsed['icon']}
        del parsed['icon']

    if 'title' in parsed:
        if not isinstance(parsed['title'], str) and parsed['title'] is not None:
            raise ValueError(f'invalid title of type {type(parsed['title'])}')
    if 'icons' in parsed:
        if not isinstance(parsed['icons'], dict) and parsed['icons'] is not None:
            raise ValueError(f'invalid icons of type {type(parsed['icons'])}')

        if parsed['icons'] is not None:
            keys_copy = list(parsed['icons'].keys())
            for k in keys_copy:
                v = parsed['icons'][k]
                if not isinstance(k, str):
                    raise ValueError(f'invalid icon list for key of type {type(k)}')
                if hasattr(v, 'save'):
                    parsed['icons'][k] = image_to_bytes(v)
                elif not isinstance(v, bytes):
                    raise ValueError(f'invalid icon list for value of type {type(v)}')

    return parsed

def widget_manager_create(widget, manager_state):
    print('widget_manager_create')
    widget.cancel_all_timers()

    #clear state
    manager_state.chosen[widget.widget_id] = None

    bg = widgets.RelativeLayout(top=0, left=0, bottom=0, right=0)
    bg.backgroundResource = R.drawable.rect
    bg.backgroundTint = widgets.color(r=0, g=0, b=0, a=100)

    #calling java releases the gil and available_widgets might be changed while iterating it
    names = [name for name in available_widgets]

    if not available_widgets:
        lst = widgets.TextView(top=10, left=10, text='No widgets', textColor=0xb3ffffff, textSize=15)
    else:
        lst = widgets.ListView(top=10, left=10, children=[widgets.TextView(text=name, textSize=30, textColor=0xb3ffffff,
                                                                            click=(choose_widget, dict(name=name))) for name in names])
    return [bg, lst], dict(name=None)

def widget_manager_update(widget, manager_state, views, is_app):
    manager_state.chosen.setdefault(widget.widget_id, None)
    chosen = manager_state.chosen[widget.widget_id]
    if chosen is not None and chosen.name is not None:
        if chosen.name not in available_widgets:
            raise RuntimeError(f"chosen widget '{chosen.name}' is not loaded")
        available_widget = available_widgets[chosen.name]
        on_create, on_update, on_app, debug = available_widget['create'], available_widget['update'], available_widget['on_app'], available_widget['debug']
        if not chosen.inited:
            try:
                elements = None
                if on_create:
                    elements = call_general_function(on_create, widget=widget, is_app=is_app)
                    if debug:
                        debug_button = widgets.Button(click=debug_button_click, backgroundTint=0xffff0000, style='success_oval_sml', padding=(0, 0, 0, 0), width=40, height=40, top=10, right=10)
                        if isinstance(elements, list):
                            elements.append(debug_button)
                        elif isinstance(elements, tuple):
                            elements = elements + (debug_button,)

                appinfo = {}
                if is_app and on_app:
                    onapp_result = call_general_function(on_app, widget=widget)
                    if onapp_result:
                        appinfo = parse_appinfo(onapp_result)

                chosen.inited = True
                return elements, dict(name=chosen.name, **appinfo)
            except BaseException as e:
                try:
                    #manually call widget chosen listener because of error
                    call_widget_chosen_listener(widget.widget_id, chosen.name)
                except:
                    #best effort
                    pass
                raise e
        else:
            if on_update:
                call_general_function(on_update, widget=widget, views=views)
            return views, dict(name=chosen.name)
    return widget_manager_create(widget, manager_state)

def widget_manager_callback(widget, manager_state, views, callback_key, **kwargs):
    chosen = manager_state.chosen[widget.widget_id]
    if chosen is not None and chosen.name is not None and chosen.inited:
        available_widget = available_widgets[chosen.name]
        cb = available_widget[callback_key]
        if cb:
            call_general_function(cb, widget=widget, views=views, **kwargs)
    return views

def set_error_to_widget_id(widget_id, error):
    #try to get the last call_function
    func = last_func_for_widget_id.get(widget_id)
    if func is None:
        #try to get the create or update functions
        widget, manager_state = create_widget(widget_id)
        chosen = manager_state.chosen.get(widget.widget_id)
        if chosen is not None and chosen.name is not None and chosen.name in available_widgets:
            available_widget = available_widgets[chosen.name]
            func = None
            if available_widget['create'] is not None:
                func = available_widget['create']
            elif available_widget['update'] is not None:
                func = available_widget['update']
            if func is not None:
                if isinstance(func, (list, tuple)):
                    func = func[0]

    if func is not None:
        return set_module_error(inspect.getmodule(func), error)
    else:
        return set_unknown_error(error)

def refresh_managers():
    manager_state = obtain_manager_state()
    chosen_copy = manager_state.chosen.copy()
    for widget_id, chosen in chosen_copy.items():
        if chosen is None:
            widgets.Widget(widget_id, None).invalidate()

def refresh_widgets(path, removed=False):
    widget_names = [k for k,v in available_widgets.items() if v['pythonfile'] == path]
    manager_state = obtain_manager_state()
    chosen_copy = manager_state.chosen.copy()
    for widget_id, chosen in chosen_copy.items():
        if chosen is not None and chosen.name is not None and chosen.name in widget_names:
            if removed:
                unchoose_widget(widget_id)
            widgets.Widget(widget_id, None).invalidate()
    state.save_modified()

class Handler(java.implements(java.clazz.appy.WidgetUpdateListener())):
    def export(self, input, output, attrs):
        if not output:
            out = None
        else:
            if not isinstance(output, (list, tuple)):
                output = [output]

            out = [e.dict(do_copy=True) for e in output]
            if input is not None and input == out:
                out = None
        return java.build_java_dict(dict(views=out, **attrs))

    def import_(self, java_list):
        #make two copies
        d1 = java.build_python_dict_from_java(java_list)
        d2 = copy.deepcopy(d1)
        return d1, elist(Element(e) for e in d2)

    @java.override
    def onUpdate(self, widget_id, views_java_list, is_app):
        print(f'python got onUpdate', is_app)
        widget, manager_state = create_widget(widget_id)
        if views_java_list == None: # might by java.Null
            input, views = None, None
        else:
            input, views = self.import_(views_java_list)
        output, attrs = widget_manager_update(widget, manager_state, views, is_app)
        return self.export(input, output, attrs)

    @java.override
    def onDelete(self, widget_id):
        print(f'python got onDelete')
        unchoose_widget(widget_id)
        state.save_modified()

    @java.override
    def onItemClick(self, widget_id, views_java_list, collection_id, position, view_id):
        print(f'python got onitemclick {widget_id} {collection_id} {position} {view_id}')
        input, views = self.import_(views_java_list)
        try:
            collection = views.find_id(collection_id)
            view = collection.children.find_id(view_id) if view_id != 0 else None
        except KeyError:
            #element not found, must be stale pendingintent
            #just invalidate
            print('Clicked collection item does not exist, invalidating')
            return java.new.java.lang.Object[()]([True, self.export(None, views, {})])
        widget, manager_state = create_widget(widget_id)
        handled = collection.__event__('itemclick', widget=widget, views=views, collection=collection, position=position, view=view)
        handled = handled is True
        return self.export(input, views, dict(handled=handled))

    @java.override
    def onClick(self, widget_id, views_java_list, view_id, checked):
        print(f'python got on click {widget_id} {view_id} {checked}')
        input, views = self.import_(views_java_list)
        try:
            v = views.find_id(view_id)
        except KeyError:
            #element not found, must be stale pendingintent
            #just invalidate
            print('Clicked element does not exist, invalidating')
            return self.export(None, views, {})
        widget, manager_state = create_widget(widget_id)
        v.__event__('click', widget=widget, views=views, view=v, checked=checked)
        return self.export(input, views, {})

    @java.override
    def onTimer(self, timer_id, widget_id, views_java_list, data):
        print('timer called', widget_id, timer_id)
        input, views = self.import_(views_java_list)
        func = loads(data)
        widget, manager_state = create_widget(widget_id)
        call_general_function(func, timer_id=timer_id, widget=widget, views=views)
        return self.export(input, views, {})

    @java.override
    def onPost(self, widget_id, views_java_list, data):
        print('post called')
        input, views = self.import_(views_java_list)
        func = loads(data)
        widget, manager_state = create_widget(widget_id)
        call_general_function(func, widget=widget, views=views)
        return self.export(input, views, {})

    @java.override
    def onConfig(self, widget_id, views_java_list, key):
        print('onConfig called', key)
        input, views = self.import_(views_java_list)
        widget, manager_state = create_widget(widget_id)
        return self.export(input, widget_manager_callback(widget, manager_state, views, 'on_config', key=key), {})

    @java.override
    def onShare(self, widget_id, views_java_list, mime, text, datas):
        datas = java.build_python_dict_from_java(datas)
        text = text if text != java.Null else None
        print('onShare called', mime, text, len(datas))

        input, views = self.import_(views_java_list)
        widget, manager_state = create_widget(widget_id)
        return self.export(input, widget_manager_callback(widget, manager_state, views, 'on_share', mimetype=mime, text=text, data=datas), {})

    @java.override
    def wipeStateRequest(self):
        print('wipe state request called')
        manager_state = obtain_manager_state()
        token = manager_state.__token__
        chosen_copy = manager_state.chosen.copy()
        state.wipe_state()
        #don't lose manager
        manager_state.chosen = chosen_copy
        manager_state.__token__ = token
        state.save_modified()

    @java.override
    def importFile(self, path, skip_refresh):
        print(f'import file request called on {path}')
        load_module(path)
        if not skip_refresh:
            refresh_managers()
            refresh_widgets(path, removed=False)

    @java.override
    def deimportFile(self, path, skip_refresh):
        print(f'deimport file request called on {path}')
        if not skip_refresh:
            refresh_widgets(path, removed=True)
        clear_module(path)
        if not skip_refresh:
            refresh_managers()

    @java.override
    def recreateWidget(self, widget_id):
        recreate_widget(widget_id)

    @java.override
    def refreshManagers(self):
        refresh_managers()

    @java.override
    def onError(self, widget_id, error):
        return set_error_to_widget_id(widget_id, error)

    @java.override
    def getStateLayoutSnapshot(self):
        layout = state.state_layout()
        new_locals = {}
        #convert locals[widget_id] to locals[widget][widget_id]
        for widget_id, local_state in layout['locals'].items():
            if widget_id == -1:
                #dont include widget manager's state
                continue
                
            try:
                name = get_widget_name(widget_id)
            except KeyError:
                continue
            
            new_locals.setdefault(name, {})
            new_locals[name][str(widget_id)] = local_state
            
        layout['locals'] = new_locals
        
        #flatten globals
        
        if layout['globals']:
            layout['globals'] = layout['globals']['globals']
        
        #layout:
        #  globals:
        #     {key -> repr(value)}
        #  nonlocals:
        #     {widget_name -> {key -> repr(value)}}  
        #  locals:
        #     {widget_name -> {widget_id -> {key -> repr(value)}}}
        return java.build_java_dict(layout)
    
    @java.override
    def cleanState(self, scope, widget, key):
        if widget == java.Null:
            widget = None
        if key == java.Null:
            key = None
            
        if scope == 'locals' and widget is not None:
            widget = int(widget)
            
        try:
            state.clean_state(scope, widget, key)
        except KeyError:
            # if can't delete, ignore
            pass

    @java.override
    def saveState(self):
        state.save_modified()

    @java.override
    def findWidgetsByMame(self, name):
        widgets = get_widgets_by_name(name)
        return java.jint[()](widgets)

    @java.override
    def getAllWidgetNames(self):
        return java.build_java_dict({str(k): v for k,v in get_all_widget_names().items()})

    @java.override
    def syncConfig(self, config_java_dict):
        print('sync config called')

        config_dict = java.build_python_dict_from_java(config_java_dict)
        configs.sync(config_dict)

    @java.override
    def dumpStacktrace(self, path):
        print('Dumping python stacktrace.')
        fh = open(path, 'w')
        faulthandler.dump_traceback(fh)
        print('Dump python stacktrace done.')

    @java.override
    def getVersion(self):
        return __version__.__version__

            
java_widget_manager = None

def init():
    global java_widget_manager
    context = java.get_java_arg()
    java_widget_manager = context
    print('init')
    prepare_image_cache_dir()
    context.registerOnWidgetUpdate(Handler())

    from . import notifications
    notifications._init()
    
def java_context():
    return java_widget_manager

def reload_python_file(path):
    return java_context().refreshPythonFileByPath(path)

def add_python_file(path):
    return java_context().addPythonFileByPathWithDialog(path)
    
def register_widget(name, create, update=None, config=None, config_description=None, on_config=None, on_share=None, on_app=None, debug=False):
    if not name or not isinstance(name, str):
        raise ValueError('name must be str')

    path = getattr(__importing_module, 'path', None)
    if path is None:
        raise ValueError('register_widget can only be called on import')

    if name in available_widgets and available_widgets[name]['pythonfile'] != path:
        raise ValueError(f'name {name} exists')

    if config is not None:
        if isinstance(config, dict):
            if any(not isinstance(key, str) for key in config.keys()):
                raise ValueError('config must be dict(str: ...)')
        else:
            raise ValueError('config must be dict(str: ...)')

    if config_description is not None:
        if isinstance(config_description, dict):
            if any(not isinstance(key, str) or not isinstance(value, str) for key, value in config_description.items()):
                raise ValueError('config_description must be dict(str: str)')
            for desc in config_description.keys():
                if config is None or desc not in config:
                    raise ValueError(f'{desc} key in config_description is not in config')
        else:
            raise ValueError('config_description must be dict(str: str)')
    
    #validate
    if create is not None:
        dump_general_function(create, {})
    if update is not None:
        dump_general_function(update, {})
    if on_config is not None:
        dump_general_function(on_config, {})
    if on_share is not None:
        dump_general_function(on_share, {})
    if on_app is not None:
        dump_general_function(on_app, {})

    available_widgets[name] = dict(pythonfile=path, create=create, update=update, on_config=on_config, on_share=on_share, on_app=on_app, debug=bool(debug))
    if config is not None:
        configs.set_defaults(name, config, config_description)
