import json, functools, copy, traceback, inspect, random
import threading, os, collections, importlib.util
import sys, hashlib, struct, re, time
import faulthandler, base64, io, asyncio

from .utils import AttrDict, dumps, loads, cap, get_args, prepare_image_cache_dir, preferred_script_dir, timeit
from . import widgets, java, state, configs, __version__

def gen_id():
    return random.randrange(1, 2 ** 63 - 1)

@functools.lru_cache(maxsize=128, typed=True)
def validate_type(type):
    return java.clazz.appy.Constants().typeToClass.containsKey(type)

@functools.lru_cache(maxsize=128, typed=True)
def is_collection(type):
    return java.clazz.appy.Widget().isCollection(type)

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
                self.args.append(self.parse_unit(arg))
        self.args = tuple(self.args)
        self.f = f if f else 'I'
        if self.f == 'I' and len(self.args) != 1:
            raise ValueError(f'Cannot have identity AttributeValue with {len(self.args)} args')

    @classmethod
    def wrap(cls, n):
        return cls(None, n)

    @classmethod
    def parse_unit(cls, value):
        if not isinstance(value, str):
            return value
        reg = re.match(r'^([+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+))\s*\*?\s*([a-zA-Z]+)$', value)
        if not reg:
            return float(value)

        value, unit = reg.groups()
        return cls.unit_to_px(float(value), unit)

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
    @classmethod
    def unit_to_px(cls, value, unit):
        attribute_unit_codes = {'': 'FROM_PX', 'px': 'FROM_PX', 'dp': 'FROM_DP', 'dip': 'FROM_DP', 'sp': 'FROM_SP', 'pt': 'FROM_PT', 'in': 'FROM_IN', 'mm': 'FROM_MM'}

        unit = unit.lower()
        widget_scaled = False
        if unit.startswith('w'):
            widget_scaled = True
            unit = unit[1:]

        if unit not in attribute_unit_codes:
            raise ValueError(f'Unsupported unit: {unit}')

        return cls(attribute_unit_codes[unit], value, float(widget_scaled))

    def debug(self, name='Var'):
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
        return self + -self.wrap(other)
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
            attr.debug(debug_name)
        e.left = attr
def attribute_write_vcenter(e, value):
    if value is None:
        del e.top
    else:
        debug_name = getattr(value, 'debug_name', '')
        attr = -(e.height / 2) + value
        if debug_name:
            attr.debug(debug_name)
        e.top = attr
def attribute_write_center(e, value):
    h, v = value
    attribute_write_hcenter(e, h)
    attribute_write_vcenter(e, v)

layout_attrs = dict(left='LEFT', top='TOP', right='RIGHT', bottom='BOTTOM', width='WIDTH', height='HEIGHT', misc='MISC')
composite_attrs = dict(ileft=attribute_ileft, itop=attribute_itop, iright=attribute_iright, ibottom=attribute_ibottom,
                       hcenter=attribute_hcenter, vcenter=attribute_vcenter, center=attribute_center, ihcenter=attribute_ihcenter, ivcenter=attribute_ivcenter, icenter=attribute_icenter)
write_attrs = dict(hcenter=attribute_write_hcenter, vcenter=attribute_write_vcenter, center=attribute_write_center)
class ContainerAttributes:
    def __init__(self, level):
        self.__level = level

    def __getattr__(self, item):
        if item in layout_attrs:
            return AttributeValue(None, Reference(self.__level, layout_attrs[item]))
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
def call_general_function_(func, kwargs, async_run_coroutines):
    if not isinstance(func, (list, tuple)):
        if not hasattr(func, '__call__'):
            raise ValueError('Only <Callable> or (<Callable>, <Capture Dict>) are supported.')
        func = (func, {})
    elif len(func) != 2 or not isinstance(func[1], dict):
        raise ValueError('Only <Callable> or (<Callable>, <Capture Dict>) are supported.')

    func, captures = func[0], func[1]

    #for tracing errors to their module
    if 'widget' in kwargs and hasattr(kwargs['widget'], 'widget_id'):
        last_func_for_widget_id[kwargs['widget'].widget_id] = func

    if not isinstance(captures, dict):
        raise ValueError('Captures must be a keyword dict')

    pass_args = dict(captures)
    pass_args.update(kwargs) #kwargs priority

    args, kwargs, has_vargs, has_vkwargs = get_args(func)
    try:
        ret = func(**{k:v for k,v in pass_args.items() if k in args or k in kwargs or has_vkwargs})
        if async_run_coroutines and hasattr(ret, '__await__'):
            #check event loop
            try:
                asyncio.get_running_loop()
                return ret
            except RuntimeError:
                pass
            return asyncio.run(ret)
        return ret
    except:
        set_module_error(inspect.getmodule(func), traceback.format_exc())
        raise

def call_general_function(func, **kwargs):
    return call_general_function_(func, kwargs, True)

def call_general_function_no_async_run(func, **kwargs):
    return call_general_function_(func, kwargs, False)

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
        if not isinstance(arg, (bool, int, float, str, bytes)):
            raise ValueError(f'Cannot serialize type {type(arg).__name__}')
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
                            padding='viewPadding',
                            alignment='gravity',
                            display='visibility')

View = java.clazz.android.view.View()
Gravity = java.clazz.android.view.Gravity()
element_attr_value_aliases = dict(display=dict(visible=View.VISIBLE, invisible=View.INVISIBLE, gone=View.GONE), alignment=dict(
    center=Gravity.CENTER, center_horizontal=Gravity.CENTER_HORIZONTAL, center_vertical=Gravity.CENTER_VERTICAL, center_left=Gravity.CENTER_VERTICAL | Gravity.LEFT, center_start=Gravity.CENTER_VERTICAL | Gravity.START, center_right=Gravity.CENTER_VERTICAL | Gravity.RIGHT, center_end=Gravity.CENTER_VERTICAL | Gravity.END,
    bottom=Gravity.BOTTOM, bottom_left=Gravity.BOTTOM | Gravity.LEFT, bottom_center=Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, bottom_right=Gravity.BOTTOM | Gravity.RIGHT, bottom_start=Gravity.BOTTOM | Gravity.START, bottom_end=Gravity.BOTTOM | Gravity.END,
    top=Gravity.TOP, top_left=Gravity.TOP | Gravity.LEFT, top_center=Gravity.TOP | Gravity.CENTER_HORIZONTAL, top_right=Gravity.TOP | Gravity.RIGHT, top_start=Gravity.TOP | Gravity.START, top_end=Gravity.TOP | Gravity.END,
    left=Gravity.LEFT, start=Gravity.START, right=Gravity.RIGHT, end=Gravity.END,
))

selector_attrs = dict(shadowPosition=['center', 'left', 'right'],
                      shadowColor=['black', 'white'],
                      shadowRadius=['small', 'medium', 'large'],
                      autoTextSize=[True, False],
                      mode=['determinate'])

element_event_hooks = {} # global for all
class Element:
    __slots__ = ('d',)
    METHOD_ADDED = 'ma'
    METHOD_DELETED = 'md'
    D_ADDED = 'da'
    D_DELETED = 'dm'
    SPECIAL_SETTABLES = ['style', 'name', 'click', 'itemclick', 'selectors', 'attributes']

    @classmethod
    def create(cls, type, **kwargs):
        obj = cls(type)
        obj.update(**kwargs)
        return obj

    @classmethod
    def from_dict(cls, d):
        obj = cls(type=d['type'], id=d['id'])
        obj.d.tag = AttrDict.make(loads(d['tag'])) if d.get('tag') else AttrDict()
        if 'methodCalls' in d:
            obj.d.baseMethodCalls = collections.OrderedDict((c['identifier'], AttrDict(c)) for c in d['methodCalls'])
        if 'attributes' in d:
            obj.d.settables.attributes = AttrDict(d['attributes'])
        if 'selectors' in d:
            obj.d.settables.selectors = AttrDict(d['selectors'])
        for k,v in d.get('customAttributes', {}).items():
            obj.d.settables[k] = v
        if 'children' in d:
            obj.d.children = ChildrenList.from_dict(d['children'])
        return obj

    def __init__(self, type, id=None):
        object.__setattr__(self, 'd', AttrDict())

        self.d.newly_created = id is None
        self.d.id = gen_id() if id is None else id
        self.d.type = type
        if not isinstance(self.d.id, int) or not isinstance(self.d.type, str):
            raise ValueError(f'id and type are wrong: {self.d.id}, {self.d.type}')

        self.d.tag = AttrDict()
        self.d.tagAccessed = False
        self.d.baseMethodCalls = collections.OrderedDict()
        self.d.newActions = collections.OrderedDict()
        self.d.settables = AttrDict()
        self.d.children = None

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
        try:
            cb = self.d_get(key, None)
        except AttributeError:
            cb = None
        if cb is not None:
            return call_general_function(loads(cb), **kwargs)

    def update(self, **attrs):
        for k,v in attrs.items():
            setattr(self, k, v)

    def __delattr__(self, key):
        #special
        if key in layout_attrs:
            self.d_delete('attributes', layout_attrs[key])
        elif key in selector_attrs:
            self.d_delete('selectors', key)
        elif key == 'style':
            self.d_delete('style', None)
            self.delete_all_prefix_method_calls('style')
        elif key in ('name', 'click', 'itemclick'):
            self.d_delete(key, None)
        #children
        elif key == 'children':
            if self.d.children is not None:
                self.d.children.clear()
        #tag
        elif key == 'tag':
            self.tag.clear()
            self.d.tagAccessed = True
        #aliases
        elif key in write_attrs:
            write_attrs[key](self, None)
        elif key in element_attr_aliases:
            delattr(self, element_attr_aliases[key])
        else:
            self.delete_method_call(key)

    def __getattr__(self, item):
        #special
        if item in layout_attrs:
            return AttributeValue(None, Reference(self.d.id, layout_attrs[item]))
        if item in composite_attrs:
            return composite_attrs[item](self)
        if item in ('type', 'id'):
            return getattr(self.d, item)
        if item in selector_attrs:
            return self.d_get('selectors', item)
        if item in ('click', 'itemclick'):
            attr = self.d_get(item, None)
            if attr is not None:
                attr = loads(attr)
            return attr
        if item in ('name', 'style'):
            return self.d_get(item, None)
        #children
        if item == 'children':
            if self.d.children is None:
                self.d.children = ChildrenList()
            return self.d.children
        #tag
        if item == 'tag':
            self.d.tagAccessed = True
            return self.d.tag
        #aliases
        if item in element_attr_aliases:
            value = getattr(self, element_attr_aliases[item])
            if item in element_attr_value_aliases:
                #convert back
                backs = [k for k,v in element_attr_value_aliases[item].items() if v == value]
                #take first option if available, default to actual value
                if backs:
                    value = backs[0]
            return value

        if item in ('paddingLeft', 'paddingTop', 'paddingRight', 'paddingBottom'):
            try:
                return self.viewPadding[('paddingLeft', 'paddingTop', 'paddingRight', 'paddingBottom').index(item)]
            except AttributeError:
                raise AttributeError(item)

        if self.d.type == 'Chronometer' and item in ('base', 'format', 'started'):
            try:
                return self.chronometer[('base', 'format', 'started').index(item)]
            except AttributeError:
                raise AttributeError(item)

        method_call = self.get_method_call('', item)
        args = method_call.arguments
        if method_call.identifier != method_call.method:
            args = args[1:]
        return deserialize_arg(args[0]) if len(args) == 1 else [deserialize_arg(arg) for arg in args]

    def __setattr__(self, key, value):
        if hasattr(value, 'export_to_views'):
            value = value.export_to_views()

        #special
        if key in layout_attrs:
            if value is None:
                delattr(self, key)
            else:
                if not isinstance(value, AttributeValue):
                    value = AttributeValue(None, value)
                self.d_add('attributes', layout_attrs[key], value.compile())
        elif key in ('click', 'itemclick'):
            self.d_add(key, None, dump_general_function(value, {}) if value is not None else None)
        elif key == 'name':
            self.d_add('name', None, value)
        elif key in selector_attrs:
            if selector_attrs[key] and value not in selector_attrs[key]:
                raise AttributeError(f'{key} must be one of: {', '.join(selector_attrs[key])}')
            self.d_add('selectors', key, value)
        elif key == 'style':
            style_attrs = style_attr_parse(self.d.type, value)
            for k,v in style_attrs.items():
                self.add_method_call('style', k, v)
            self.d_add('style', None, value) #save for getattr
        #children
        elif key == 'children':
            if value is None:
                if self.d.children is not None:
                    self.d.children.clear()
            else:
                if isinstance(value, Element):
                    value = ChildrenList([value])
                elif isinstance(value, (tuple, list, elist)):
                    value = ChildrenList(value)
                if self.d.children is not None:
                    self.d.children.replace(value)
                else:
                    self.d.children = value
        #tag
        elif key == 'tag':
            # goes to self.d.tag through getattr
            self.tag.clear()
            self.tag.update(value)
        #aliases
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
        elif key in write_attrs:
            write_attrs[key](self, value)
        elif key in element_attr_aliases:
            if key in element_attr_value_aliases:
                value = element_attr_value_aliases[key].get(value, value)
            setattr(self, element_attr_aliases[key], value)
        elif key in ('paddingLeft', 'paddingTop', 'paddingRight', 'paddingBottom'):
            try:
                old_left, old_top, old_right, old_bottom = self.viewPadding
            except AttributeError:
                old_left, old_top, old_right, old_bottom = 0, 0, 0, 0
            self.viewPadding = (value if key == 'paddingLeft'   else old_left,
                                value if key == 'paddingTop'    else old_top,
                                value if key == 'paddingRight'  else old_right,
                                value if key == 'paddingBottom' else old_bottom)
        elif self.d.type == 'Chronometer' and key in ('base', 'format', 'started'):
            try:
                old_base, old_format, old_started = self.chronometer
            except AttributeError:
                old_base, old_format, old_started = 0, None, False
            self.chronometer = (value if key == 'base' else old_base,
                                value if key == 'format' else old_format,
                                value if key == 'started' else old_started)
        else:
            self.add_method_call('', key, value)

    def add_method_call(self, prefix, key, value):
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

        if prefix:
            identifier = f'{prefix}*{identifier}'

        arguments = tuple(serialize_arg(arg) for arg in arguments)
        self.d.newActions.pop((self.METHOD_DELETED, identifier, None), None)
        self.d.newActions[(self.METHOD_ADDED, identifier, None)] = AttrDict(identifier=identifier, method=method, arguments=arguments)

    def delete_method_call(self, key):
        param_setter, identifier = get_param_setter(self.d.type, key)

        if (self.METHOD_ADDED, identifier, None) in self.d.newActions:
            del self.d.newActions[(self.METHOD_ADDED, identifier, None)]
            self.d.newActions[(self.METHOD_DELETED, identifier, None)] = True
        else:
            self.d.newActions[(self.METHOD_DELETED, identifier, None)] = True
            if identifier not in self.baseMethodCalls:
                raise AttributeError(identifier)

    def delete_all_prefix_method_calls(self, prefix):
        identifiers = [identifier for action, identifier in self.d.newActions.keys() if identifier.startswith(f'{prefix}*')]
        for identifier in identifiers:
            self.d.newActions.pop((self.METHOD_ADDED, identifier, None), None)
            self.d.newActions[(self.METHOD_DELETED, identifier, None)] = True

    def get_method_call(self, prefix, key):
        param_setter, identifier = get_param_setter(self.d.type, key)
        if prefix:
            identifier = f'{prefix}*{identifier}'

        if (self.METHOD_DELETED, identifier, None) in self.d.newActions:
            raise AttributeError(identifier)

        if (self.METHOD_ADDED, identifier, None) in self.d.newActions:
            return self.d.newActions[(self.METHOD_ADDED, identifier, None)]

        try:
            return self.d.baseMethodCalls[identifier]
        except KeyError as e:
            raise AttributeError(e)

    def any_methods_deleted(self):
        return [identifier for action, _, _ in self.d.newActions.keys() if action == self.METHOD_DELETED] or self.d.get('newly_created', False)

    def d_add(self, key1, key2, value):
        self.d.newActions.pop((self.D_DELETED, key1, key2), None)
        self.d.newActions[(self.D_ADDED, key1, key2)] = value

    def d_delete(self, key1, key2):
        if (self.D_ADDED, key1, key2) in self.d.newActions:
            del self.d.newActions[(self.D_ADDED, key1, key2)]
            self.d.newActions[(self.D_DELETED, key1, key2)] = True
        else:
            self.d.newActions[(self.D_DELETED, key1, key2)] = True
            if key1 not in self.d.settables or key2 not in self.d.settables[key1]:
                raise AttributeError(identifier)

    def d_get(self, key1, key2):
        if (self.D_DELETED, key1, key2) in self.d.newActions:
            raise AttributeError(key2)

        if (self.D_ADDED, key1, key2) in self.d.newActions:
            return self.d.newActions[(self.D_ADDED, key1, key2)]

        try:
            if key2:
                return self.d.settables[key1][key2]
            else:
                return self.d.settables[key1]
        except KeyError as e:
            raise AttributeError(e)

    @classmethod
    def set_event_hooks(cls, type, event_hooks):
        element_event_hooks[type] = event_hooks

    def changes(self):
        d = dict(type=self.d.type, id=self.d.id, newActions=[dict(action=action, key1=key1, key2=(key2 if key2 else ''), value=value) for (action, key1, key2), value in self.d.newActions.items()])
        if self.d.tagAccessed or self.d.get('newly_created', False):
            d['tag'] = dumps(self.d.tag)
        if self.d.children is not None:
            d['children'] = self.d.children.changes()
        return d

    def no_changes(self):
        return not self.d.newActions and not self.d.tagAccessed and not self.d.get('newly_created', False) and (self.d.children is None or self.d.children.no_changes())

    def apply_specific_changes(self, changes):
        d = dict(id=changes['id'], type=changes['type'], tag=changes.get('tag', ''))

        settables = {k: (dict(settable) if isinstance(settable, dict) else settable) for k, settable in self.d.settables.items()}
        methodCalls = dict(self.d.baseMethodCalls)
        for action in changes['newActions']:
            action, key1, key2, value = action['action'], action['key1'], action['key2'], action['value']
            if action == self.METHOD_ADDED:
                methodCalls[key1] = dict(value)
            elif action == self.METHOD_DELETED:
                del methodCalls[key1]
            elif action == self.D_ADDED:
                if key2:
                    settables.setdefault(key1, {})[key2] = value
                else:
                    settables[key1] = value
            elif action == self.D_DELETED:
                if key2:
                    if key1 in settables:
                        del settables[key1][key2]
                else:
                    del settables[key1]

        if methodCalls:
            d['methodCalls'] = list(methodCalls.values())

        if settables:
            d['customAttributes'] = {}
            for k,v in settables.items():
                if isinstance(v, dict):
                    d[k] = v
                else:
                    d['customAttributes'][k] = v

        children = None
        if 'children' in changes:
            if self.d.children is not None:
                children = self.d.children.apply_specific_changes(changes['children'])
            else:
                children = ChildrenList(id=changes['children']['id']).apply_specific_changes(changes['children'])
        else:
            if self.d.children is not None:
                children = self.d.children.apply_changes()
        if children:
            d['children'] = children
        return d

    def apply_changes(self):
        return self.apply_specific_changes(self.changes())

    def __repr__(self):
        return repr(self.apply_changes())

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
    def debug(self, name):
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

class elist:
    ITEM_ADDED = 'ia'
    ITEM_DELETED = 'id'

    def check_type(self, args):
        res = []
        for e in args:
            if not isinstance(e, (Element, elist, list, tuple)):
                raise TypeError(f'elist must contain only Elements or elists, not {type(e)} ({repr(e)})')
            if isinstance(e, (list, tuple)):
                res.append(elist(e))
            else:
                res.append(e)
        return res

    @classmethod
    def from_dict(cls, d):
        obj = cls(id=d['id'])
        object.__setattr__(obj, 'baseDict', {e['id']: (cls.from_dict(e) if 'list' in e else Element.from_dict(e)) for e in d['list']})
        object.__setattr__(obj, 'baseList', [e['id'] for e in d['list']])
        object.__setattr__(obj, 'lst', list(obj.baseList))
        return obj

    def __init__(self, args=None, **kwargs):
        if args is None:
            args = []
        if isinstance(args, elist):
            args = list(args)
        args = self.check_type(args)

        id = kwargs['id'] if 'id' in kwargs else gen_id()
        if not isinstance(id, int):
            raise ValueError(f'id is wrong: {id}')

        object.__setattr__(self, 'id', id)
        object.__setattr__(self, 'baseDict', {})
        object.__setattr__(self, 'baseList', [])
        object.__setattr__(self, 'lst', [])
        object.__setattr__(self, 'newActions', collections.OrderedDict())

        for arg in args:
            self.append(arg)

    def __setattr__(self, key, value):
        if key in ('id', 'baseDict', 'lst', 'newActions'):
            raise ValueError(f'cannot change {key}')
        return super().__setattr__(key, value)

    @classmethod
    def _all_rec(cls, elst):
        for e in elst:
            if isinstance(e, elist):
                yield from cls._all_rec(e)
            else:
                yield e
                if e.d.children is not None:
                    yield from cls._all_rec(e.d.children)

    def all(self):
        yield from self._all_rec(self)

    def _find_elements(self, fname, rec, raise_error, name=None, id=None):
        if (name is None and id is None) or (name is not None and id is not None):
            raise ValueError(f'{fname} takes either name or id')
        if name is not None:
            found = list(dict.fromkeys(e for e in (self.all() if rec else self) if getattr(e, 'name', None) == name))
            if not found and raise_error:
                raise KeyError(f'element named {name} not found{' recursively' if rec else ' in top level'}')
        else:
            found = list(dict.fromkeys(e for e in (self.all() if rec else self) if getattr(e, 'id', None) == id))
            if not found and raise_error:
                raise KeyError(f'element with id {id} not found{' recursively' if rec else ' in top level'} {repr(self)}')
        return found

    # returns all occurrences or empty list
    def find_all(self, name=None, id=None):
        return self._find_elements('find_all', True, False, name=name, id=id)

    # returns first occurrence or None
    def find(self, name=None, id=None):
        found = self._find_elements('find', True, False, name=name, id=id)
        return found[0] if found else None

    # returns first occurrence or raises KeyError
    def get(self, name=None, id=None):
        found = self._find_elements('get', True, True, name=name, id=id)
        return found[0] if found else None

    def names(self):
        return list(set(getsttr(e, 'name', None) for e in self.all()))
    def ids(self):
        return list(set(getsttr(e, 'id', None) for e in self.all()))

    def resolve_item(self, id):
        if (self.ITEM_ADDED, id) in self.newActions:
            return self.newActions[(self.ITEM_ADDED, id)]
        if id in self.baseDict and (self.ITEM_DELETED, id) not in self.newActions:
            return self.baseDict[id]
        raise KeyError(f'{id}')

    def __getitem__(self, item):
        if isinstance(item, slice):
            return [self.resolve_item(id) for id in self.lst[item]]
        if isinstance(item, int):
            # try id first
            result = self.find(id=item)
            if result:
                return result
            # then index
            return self.resolve_item(self.lst[item])
        return self.find(name=item)

    def __delitem__(self, item):
        # remove from list, record changed to dict

        if isinstance(item, slice):
            content = self.lst[item]
            # index will not remove all duplicates -> might still contain
            del self.lst[item]
            deleted = [i for i in content if i not in self.lst]
        # delete only non recursively
        # id, name will remove all duplicates -> cannot contain
        elif isinstance(item, int):
            # try id first
            deleted = [e.id for e in self._find_elements('del', False, False, id=item)]
            if deleted:
                for d in deleted:
                    self.lst.remove(d)
            else:
                # try index
                # index will not remove all duplicates -> might still contain
                content = self.lst[item]
                del self.lst[item]
                deleted = [content] if content not in self.lst else []
        else:
            deleted = [e.id for e in self._find_elements('del', False, True, name=item)]
            for d in deleted:
                self.lst.remove(d)

        for d in deleted:
            self.newActions.pop((self.ITEM_ADDED, d), None)
            self.newActions[(self.ITEM_DELETED, d)] = True

    def __setitem__(self, index, value):
        del self[index]
        self.insert(index, value)

    def __contains__(self, item):
        if isinstance(item, (Element, elist)):
            for e in self:
                if e == item:
                    return True
            return False
        return self.find(name=item) or self.find(id=item)

    def append(self, item):
        item, = self.check_type([item])
        if item.id not in self.lst:
            self.newActions.pop((self.ITEM_DELETED, item.id), None)
            self.newActions[(self.ITEM_ADDED, item.id)] = item
        self.lst.append(item.id)

    def insert(self, index, item):
        item, = self.check_type([item])
        if item.id not in self.lst:
            self.newActions.pop((self.ITEM_DELETED, item.id), None)
            self.newActions[(self.ITEM_ADDED, item.id)] = item
        self.lst.insert(index, item.id)

    def extend(self, iterable):
        lst = list(iterable)
        lst = self.check_type(lst)
        for e in lst:
            self.append(e)

    def pop(self):
        del self[len(self) - 1]

    def replace(self, items):
        self.clear()
        self.extend(items)

    def clear(self):
        while self.lst:
            self.pop()

    def __iter__(self):
        for id in self.lst:
            yield self.resolve_item(id)

    def __len__(self):
        return len(self.lst)

    def __bool__(self):
        return bool(self.lst)

    def count(self, item):
        return list(self).count()

    def index(self, item):
        return self.lst.index(item.id)

    def copy(self):
        return type(self)(self)

    def changes(self):
        return dict(id=self.id,
                    list=self.lst,
                    addedActions={str(id): (dict(type=value.type) if isinstance(value, Element) else dict()) for (action, id), value in self.newActions.items() if action == self.ITEM_ADDED},
                    deletedActions={str(id): True for (action, id), value in self.newActions.items() if action == self.ITEM_DELETED},
                    elementActions={str(id): self.resolve_item(id).changes() for id in self.lst if not self.resolve_item(id).no_changes()})

    def no_changes(self):
        return self.baseList == self.lst and not self.newActions and all(self.resolve_item(id).no_changes() for id in self.lst)

    def any_collection_methods_deleted(self):
        for e in self.all():
            if is_collection(e.type) and e.any_methods_deleted():
                return True
        return False

    def apply_specific_changes(self, changes):
        list = []
        for id in changes['list']:
            if str(id) in changes['addedActions']:
                added = changes['addedActions'][str(id)]
                if 'type' in added:
                    e = Element(id=id, type=added['type'])
                else:
                    e = elist(id=id)
                list.append(e.apply_specific_changes(changes['elementActions'][str(id)]))
            elif id in self.baseDict and id not in changes['deletedActions']:
                if str(id) in changes['elementActions']:
                    list.append(self.baseDict[id].apply_specific_changes(changes['elementActions'][str(id)]))
                else:
                    list.append(self.baseDict[id].apply_changes())
            else:
                raise KeyError(f'changes out of sync for id: {id} {id in self.baseDict} {id in changes['deletedActions']}')
        return dict(id=self.id, list=list)

    def apply_changes(self):
        return self.apply_specific_changes(self.changes())

    def __repr__(self):
        return f'elist({', '.join(repr(e) for e in self)})'

#children is list of lists
class ChildrenList(elist):
    def __init__(self, args=None, **kwargs):
        if args:
            args = [elist([arg]) if isinstance(arg, Element) else arg for arg in args]
        super().__init__(args, **kwargs)

    def append(self, item):
        if isinstance(item, Element):
            item = elist([item])
        return super().append(item)
    def insert(self, index, item):
        if isinstance(item, Element):
            item = elist([item])
        return super().insert(index, item)

available_widgets_lock = threading.Lock()
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

def set_widget_file_error(name, error):
    pythonfile = python_file_by_name(name)
    if not pythonfile:
        return None
    java_widget_manager.setFileLastError(pythonfile, error)
    return pythonfile

def set_unknown_error(error):
    java_widget_manager.setFileLastError(None, error)
    return None

def python_file_by_name(name):
    if name not in available_widgets:
        return None
    return available_widgets[name].get('pythonfile')

def call_widget_chosen_listener(widget_id, name):
    java_widget_manager.callWidgetChosenListener(widget_id, name)

import_locks_lock = threading.Lock()
import_locks = {}
def load_module(path):
    with import_locks_lock:
        if path not in import_locks:
            import_locks[path] = threading.RLock()
    with import_locks[path]:
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
    with import_locks_lock:
        if path not in import_locks:
            import_locks[path] = threading.RLock()

    with import_locks[path]:
        mod = sys.modules.get(module_name(path))
        if mod and hasattr(mod, '__del__'):
            try:
                mod.__del__()
            except:
                #destructor should be nothrow
                print(traceback.format_exc())
        with available_widgets_lock:
            available_widgets = {k:v for k,v in available_widgets.items() if v['pythonfile'] != path}
        sys.modules.pop(module_name(path), None)

def obtain_manager_state():
    manager_state = state.State('__widgetmanager__', -1) #special own scope
    manager_state.locals('__token__')
    manager_state.locals('chosen')
    manager_state.locals('manager')
    token = 1
    if getattr(manager_state, '__token__', None) != token:
        manager_state.chosen = {}
        manager_state.manager = {}
        manager_state.__token__ = token

    manager_state.setdefault('chosen', {})
    manager_state.setdefault('manager', {})
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
    if manager_state.chosen.get(widget_id) is None:
        raise KeyError(f'no such widget {widget_id}')
    return manager_state.chosen[widget_id].name

def get_widgets_by_name(name):
    manager_state = obtain_manager_state()
    return [widget_id for widget_id, chosen in manager_state.chosen.items() if chosen is not None and chosen.name == name]

def get_all_widget_names():
    manager_state = obtain_manager_state()
    return {widget_id: (chosen.get('name') if chosen else None) for widget_id, chosen in manager_state.chosen.items()}

def choose_widget(widget, views, name):
    print(f'choosing widget: {widget.widget_id} -> {name}')
    manager_state = obtain_manager_state()
    manager_state.chosen[widget.widget_id] = AttrDict(name=name, inited=False)

    for view in views:
        view.display = 'invisible'

    views.append(widgets.TextView(top=10, left=10, right=10, bottom=10, text='Loading...', maxLines=1, textColor=0xb3ffffff, textSize='auto'))
    widget.invalidate()

def unchoose_widget(widget_id):
    widget, manager_state = create_widget(widget_id)
    state.clean_local_state(widget_id)
    widget.cancel_all_timers()
    manager_state.chosen.pop(widget_id, None)
    last_func_for_widget_id.pop(widget_id, None)

def recreate_widget(widget_id):
    manager_state = obtain_manager_state()
    if widget_id in manager_state.chosen and manager_state.chosen[widget_id] is not None:
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

def widget_manager_create(widget, manager_state, views):
    print(f'creating widget manager for {widget.widget_id}')
    widget.cancel_all_timers()
    #clear state
    manager_state.chosen[widget.widget_id] = None

    with available_widgets_lock:
        names = [name for name in available_widgets]
    names.sort()

    list_item = lambda name: widgets.TextView(text=name, textSize=30, textColor=0xb3ffffff, click=(choose_widget, dict(name=name)))
    empty_list = lambda: widgets.TextView(top=10, left=10, text='No widgets', textColor=0xb3ffffff, textSize=15)
    full_list = lambda: widgets.ListView(top=10, left=10, children=[list_item(name) for name in names])

    if views is None:
        bg = widgets.RelativeLayout(top=0, left=0, bottom=0, right=0)
        bg.backgroundResource = R.drawable.rect
        bg.backgroundTint = widgets.color(r=0, g=0, b=0, a=100)

        if not names:
            lst = empty_list()
        else:
            lst = full_list()
        return elist([bg, lst])
    else:
        if views[1].type == 'ListView':
            if not names:
                views[1] = empty_list()
            existing_names = [(i, child[0].text) for i, child in enumerate(views[1].children)]
            existing_names.sort(key=lambda x: x[0], reverse=True)
            #removed
            for i, existing_name in existing_names:
                if existing_name not in names:
                    del views[1].children[i]

            existing_names = {child[0].text for child in views[1].children}
            for i, name in enumerate(names):
                if name not in existing_names:
                    views[1].children.insert(i, list_item(name))
        elif names:
            views[1] = full_list()

        return views

def widget_manager_update(widget, manager_state, views, is_app):
    chosen = manager_state.chosen.setdefault(widget.widget_id, None)
    manager = manager_state.manager.setdefault(widget.widget_id, AttrDict())
    if manager is None:
        manager_state.manager[widget.widget_id] = AttrDict()
        manager = manager_state.manager[widget.widget_id]

    if chosen is not None and chosen.name is not None:
        with available_widgets_lock:
            if chosen.name not in available_widgets:
                raise RuntimeError(f"chosen widget '{chosen.name}' is not loaded")
            available_widget = available_widgets[chosen.name]
            on_create, on_update, on_app, debug = available_widget['create'], available_widget['update'], available_widget['on_app'], available_widget['debug']
        if views is None:
            print('views is None, recreating')
            chosen.inited = False
            widget.cancel_all_timers()
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

                manager.rendered = False
                chosen.inited = True
                return elist(elements), dict(name=chosen.name, newviews=True, inited=True, **appinfo)
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
    should_reuse = manager.rendered if 'rendered' in manager else False
    elements = widget_manager_create(widget, manager_state, views if should_reuse else None)
    manager.rendered = True
    return elements, dict(name=None, newviews=not should_reuse)

def widget_manager_callback(widget, manager_state, views, callback_key, default_cb=None, **kwargs):
    chosen = manager_state.chosen[widget.widget_id]
    if chosen is not None and chosen.name is not None and chosen.inited:
        with available_widgets_lock:
            available_widget = available_widgets[chosen.name]
            cb = available_widget[callback_key]
        if cb:
            call_general_function(cb, widget=widget, views=views, **kwargs)
        elif default_cb:
            call_general_function(default_cb, widget=widget, views=views, **kwargs)
    return views

def set_error_to_widget_id(widget_id, error):
    #try to get the last call_function
    func = last_func_for_widget_id.get(widget_id)
    if func is None:
        #try to get the create or update functions
        widget, manager_state = create_widget(widget_id)
        chosen = manager_state.chosen.get(widget.widget_id)
        with available_widgets_lock:
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
    with available_widgets_lock:
        widget_names = [k for k,v in available_widgets.items() if v['pythonfile'] == path]
    manager_state = obtain_manager_state()
    chosen_copy = manager_state.chosen.copy()
    for widget_id, chosen in chosen_copy.items():
        if chosen is not None and chosen.name is not None and chosen.name in widget_names:
            widget = widgets.Widget(widget_id, None)
            if removed:
                unchoose_widget(widget_id)
                widget.set_loading()
            widget.invalidate()
    state.save_modified()


register_widget_waiters_enabled = True
register_widget_waiters = {}

def register_for_register_widget(name, f_id, f):
    with available_widgets_lock:
        if name in available_widgets or not register_widget_waiters_enabled:
            return False
        if name not in register_widget_waiters:
            register_widget_waiters[name] = {}
        if f is not None and f_id not in register_widget_waiters[name]:
            register_widget_waiters[name][f_id] = f
        return True

def notify_registered_widget(name):
    callbacks = {}
    with available_widgets_lock:
        if name in register_widget_waiters:
            callbacks = register_widget_waiters[name]
            del register_widget_waiters[name]
    for id, callback in callbacks.items():
        callback() # simple lambda

def disable_register_widget_waiters():
    global register_widget_waiters, register_widget_waiters_enabled
    current = {}
    with available_widgets_lock:
        register_widget_waiters_enabled = False
        current = register_widget_waiters
        register_widget_waiters = {}

    #flush all waiters
    for callbacks in current.values():
        for callback in callbacks:
            callback()

#flatten inner lists
def flatten_elements(elements):
    for e in elements:
        if isinstance(e, Element):
            yield e
        else:
            yield from flatten_elements(e)

cached_views = {}
WidgetClass = java.clazz.com.appy.Widget()
def java_views_dict_to_views(views_dict):
    return elist.from_dict(java.build_python_dict_from_java(views_dict))

def import_java_views(widget_id, widget_views, views_token):
    out = None
    if views_token != 0 and widget_id is not None and widget_id in cached_views and cached_views[widget_id]['token'] == views_token:
        out = cached_views[widget_id]['views']
    else:
        d = java.build_python_dict_from_java(WidgetClass.widgetDict(widget_views))
        if d is not None:
            out = elist.from_dict(d)
            cached_views[widget_id] = dict(token=views_token, views=out)
    return out

class Handler(java.implements(java.clazz.appy.WidgetUpdateListener())):
    def export(self, widget_id, output, attrs):
        if not output or output.no_changes():
            out = None
            collectionMethodsDeleted = False
            change_token = 0
        else:
            out = output.changes()
            collectionMethodsDeleted = output.any_collection_methods_deleted()
            change_token = gen_id()
            prev_token = cached_views.get(widget_id, dict(token=0))['token']
            cached_views[widget_id] = dict(token=prev_token + change_token, views=output)
        ret = java.build_java_dict(dict(views=out, collectionMethodsDeleted=collectionMethodsDeleted, changeToken=change_token, **attrs))
        return ret

    def import_(self, widget_id, java_dict, views_token):
        return import_java_views(widget_id, java_dict, views_token)

    @java.override
    def onUpdate(self, widget_id, views_java_dict, views_token, is_app):
        print(f'python got onUpdate for {'app' if is_app else 'widget'} {widget_id}')
        widget, manager_state = create_widget(widget_id)

        if widget.name is not None:
            # post again if not ready
            if register_for_register_widget(widget.name, 'update', widget.invalidate):
                # waiting
                print(f'not handling update for {widget_id} yet')
                return self.export(widget_id, None, dict(requestViews=True))

        if views_java_dict == None: # might by java.Null
            views = None
        else:
            views = self.import_(widget_id, views_java_dict, views_token)
        output, attrs = widget_manager_update(widget, manager_state, views, is_app)
        return self.export(widget_id, output, attrs)

    @java.override
    def onDelete(self, widget_id):
        print(f'python got onDelete for {widget_id}')
        unchoose_widget(widget_id)
        widgets.Widget(widget_id, None).set_loading()
        state.save_modified()

    @java.override
    def onItemClick(self, widget_id, views_java_dict, views_token, collection_id, position, view_id):
        print(f'python got onitemclick {widget_id} {collection_id} {position} {view_id}')
        views = self.import_(widget_id, views_java_dict, views_token)

        collection = views.find(id=collection_id)
        if not collection:
            #element not found, must be stale pendingintent
            #just invalidate
            print(f'Clicked collection item does not exist in widget {widget_id}, invalidating')
            return self.export(widget_id, views, dict(requestViews=True))
        view = collection.children.find(id=view_id) if view_id != 0 else None

        widget, manager_state = create_widget(widget_id)

        if widget.name is not None and register_for_register_widget(widget.name, None, None):
            print(f'suppressing item click event on {widget_id} because widget is not loaded')
            return self.export(widget_id, views, dict(requestViews=True))

        handled = collection.__event__('itemclick', widget=widget, views=views, collection=collection, position=position, view=view)
        handled = handled is True
        return self.export(widget_id, views, dict(handled=handled))

    @java.override
    def onClick(self, widget_id, views_java_dict, views_token, view_id, checked):
        print(f'python got on click {widget_id} {view_id} {checked}')
        views = self.import_(widget_id, views_java_dict, views_token)
        v = views.find(id=view_id)
        if not v:
            #element not found, must be stale pendingintent
            #just invalidate
            print(f'Clicked element does not exist in widget {widget_id}, invalidating')
            return self.export(widget_id, views, dict(requestViews=True))
        widget, manager_state = create_widget(widget_id)

        if widget.name is not None and register_for_register_widget(widget.name, None, None):
            print(f'suppressing click event on {widget_id} because widget is not loaded')
            return self.export(widget_id, views, dict(requestViews=True))

        v.__event__('click', widget=widget, views=views, view=v, checked=checked)
        return self.export(widget_id, views, {})

    @java.override
    def onTimer(self, timer_id, widget_id, views_java_dict, views_token, data):
        print(f'timer called for widget {widget_id}, timer_id: {timer_id}')
        func = loads(data)
        widget, manager_state = create_widget(widget_id)

        if widget.name is not None:
            # post again if not ready
            if register_for_register_widget(widget.name, f'timer_{timer_id}', lambda: widget.post(func)):
                # waiting
                print(f'not handling timer {timer_id} on widget {widget_id} yet because widget is not loaded')
                return self.export(widget_id, None, dict(requestViews=True))

        views = self.import_(widget_id, views_java_dict, views_token)
        call_general_function(func, timer_id=timer_id, widget=widget, views=views)
        return self.export(widget_id, views, {})

    @java.override
    def onPost(self, widget_id, views_java_dict, views_token, data):
        print(f'post called for widget {widget_id}')
        widget, manager_state = create_widget(widget_id)
        func = loads(data)

        if widget.name is not None:
            # post again if not ready
            if register_for_register_widget(widget.name, 'post', lambda: widget.post(func)):
                # waiting
                print(f'not handling post on widget {widget_id} yet because widget is not loaded')
                return self.export(widget_id, None, dict(requestViews=True))

        views = self.import_(widget_id, views_java_dict, views_token)
        call_general_function(func, widget=widget, views=views)
        return self.export(widget_id, views, {})

    def onEvent(self, widget_id, views_java_dict, views_token, event, default_cb=None, **kwargs):
        views = self.import_(widget_id, views_java_dict, views_token)
        widget, manager_state = create_widget(widget_id)

        if widget.name is not None and register_for_register_widget(widget.name, None, None):
            print(f'suppressing {event} because widget {widget_id} is not loaded')
            return self.export(widget_id, views, dict(requestViews=True))

        return self.export(widget_id, widget_manager_callback(widget, manager_state, views, event, default_cb=default_cb, **kwargs), {})

    @java.override
    def onConfig(self, widget_id, views_java_dict, views_token, key):
        print(f'onConfig called with key: {key}')
        return self.onEvent(widget_id, views_java_dict, views_token, 'on_config', key=key)

    @java.override
    def onShare(self, widget_id, views_java_dict, views_token, mime, text, datas):
        datas = java.build_python_dict_from_java(datas)
        text = text if text != java.Null else None
        print(f'onShare called for widget {widget_id}', mime, text, len(datas))
        return self.onEvent(widget_id, views_java_dict, views_token, 'on_share', mimetype=mime, text=text, data=datas)

    @java.override
    def onCustomSettings(self, widget_id, views_java_dict, views_token):
        print(f'onCustomSettings called')
        return self.onEvent(widget_id, views_java_dict, views_token, 'on_settings_action', default_cb=lambda: widgets.toast('No custom action configured for this widget'))

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
    def initImportFilesDone(self):
        disable_register_widget_waiters()
        refresh_managers()

    @java.override
    def onError(self, widget_id, error):
        return set_error_to_widget_id(widget_id, error)

    @java.override
    def getStateLayoutSnapshot(self, scope, scope_key):
        if scope == 'locals' and scope_key:
            scope_key = int(scope_key)
        snapshot = state.state_snapshot(scope, scope_key)
        if scope == 'locals' and not scope_key:
            # group by widget name
            widget_names = {}
            new_layout = {}
            for widget_id in snapshot:
                if widget_id != -1 and widget_id not in widget_names:
                    try:
                        widget_name = get_widget_name(widget_id)
                    except KeyError:
                        continue

                    widget_names[widget_id] = widget_name
                    new_layout[widget_name] = {}

            for widget_id, num_entries in snapshot.items():
                if widget_id not in widget_names:
                    #dont include widget manager's state and bad widgets
                    continue

                new_layout[widget_names[widget_id]][widget_id] = num_entries
            snapshot = new_layout
        if not scope:
            # group by widget name
            new_layout = {}
            for k, v in snapshot.items():
                if k == 'locals':
                    widget_names = {}
                    for widget_id in v:
                        if widget_id != -1 and widget_id not in widget_names:
                            try:
                                widget_name = get_widget_name(widget_id)
                            except KeyError:
                                continue
                            widget_names[widget_id] = widget_name
                    new_layout[k] = len(set(widget_names.values()))
                else:
                    new_layout[k] = len(v)
            snapshot = new_layout
        return java.build_java_dict(snapshot)

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
    def cleanLocalStateByName(self, name):
        widgets = get_widgets_by_name(name)
        for widget in widgets:
            try:
                state.clean_state('locals', widget, None)
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
    def getWidgetName(self, widget_id):
        return get_widget_name(widget_id)

    @java.override
    def getPythonFileByName(self, name):
        return python_file_by_name(name)

    @java.override
    def syncConfig(self, config_java_dict):
        print('sync config called')

        config_dict = java.build_python_dict_from_java(config_java_dict)
        configs.sync(config_dict)

    @java.override
    def dumpStacktrace(self, path, header, trim_size):
        print('Dumping python stacktrace.')
        try:
            prev_data = open(path, 'r', encoding='latin-1').read(trim_size)
        except OSError:
            prev_data = ''
        fh = open(path, 'w', encoding='latin-1')
        fh.write(header)
        faulthandler.dump_traceback(fh)
        fh.write('\n\n')
        fh.write(prev_data)
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

    configs.sync(java.build_python_dict_from_java(context.getConfigurations().getDict()))

    from . import notifications
    notifications._init()

def java_context():
    return java_widget_manager

def reload_python_file(path):
    return java_context().refreshPythonFileByPath(path)

def add_python_file(path):
    return java_context().addPythonFileByPathWithDialog(path)

reserved_names = [configs.global_widget_config_name]
def register_widget(name, create, update=None, config=None, config_description=None, on_config=None, on_share=None, on_app=None, on_settings_action=None, debug=False):
    if not name or type(name) != str:
        raise ValueError('name must be str')
    if len(name) > 256:
        raise ValueError('name length > 256')
    if name in reserved_names:
        raise ValueError(f'{name} is reserved')

    path = getattr(__importing_module, 'path', None)
    if path is None:
        raise ValueError('register_widget can only be called on import')

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
    if on_settings_action is not None:
        dump_general_function(on_settings_action, {})

    with available_widgets_lock:
        if name in available_widgets and available_widgets[name]['pythonfile'] != path:
            raise ValueError(f'name {name} exists')
        available_widgets[name] = dict(pythonfile=path, create=create, update=update, on_config=on_config, on_share=on_share, on_app=on_app, on_settings_action=on_settings_action, debug=bool(debug))

    if config is not None:
        configs.set_defaults(name, config, config_description)

    notify_registered_widget(name)
