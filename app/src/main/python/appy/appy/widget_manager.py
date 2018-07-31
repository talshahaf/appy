import json, functools, copy, traceback, inspect, threading, os, collections, importlib.util, sys, hashlib, struct, re
from .utils import AttrDict, dumps, loads, cap, get_args, prepare_image_cache_dir
from . import widgets, java, state

def gen_id():
    id = 0
    while id in (0, -1):
        id = struct.unpack('<q', os.urandom(8))[0]
    return id

def json_dumps(d):
    #c json fucks up because we're not a regular dict, indent=* causes python to use the python implementation
    return json.dumps(d, indent=2)
def json_loads(s):
    return json.loads(s)

@functools.lru_cache(maxsize=128, typed=True)
def validate_type(type):
    return java.clazz.com.appy.Constants().typeToClass.containsKey(type)

def method_from_attr(attr):
    return f'set{cap(attr)}'

@functools.lru_cache(maxsize=128, typed=True)
def get_param_setter(type, attr):
    method = method_from_attr(attr)
    setter = java.clazz.com.appy.Constants().getSetterMethod(type, method)
    return setter if setter != java.Null else None, method

@functools.lru_cache(maxsize=128, typed=True)
def validate_remoteviews_method(method):
    return java.clazz.com.appy.RemoteMethodCall().remoteViewMethods.containsKey(method)

@functools.lru_cache(maxsize=128, typed=True)
def unit_constants():
    dp_to_px = float(java_widget_manager.convertUnit(1.0, java.clazz.android.util.TypedValue().COMPLEX_UNIT_DIP, java.clazz.android.util.TypedValue().COMPLEX_UNIT_PX))
    sp_to_px = float(java_widget_manager.convertUnit(1.0, java.clazz.android.util.TypedValue().COMPLEX_UNIT_SP,  java.clazz.android.util.TypedValue().COMPLEX_UNIT_PX))
    pt_to_px = float(java_widget_manager.convertUnit(1.0, java.clazz.android.util.TypedValue().COMPLEX_UNIT_PT,  java.clazz.android.util.TypedValue().COMPLEX_UNIT_PX))
    in_to_px = float(java_widget_manager.convertUnit(1.0, java.clazz.android.util.TypedValue().COMPLEX_UNIT_IN,  java.clazz.android.util.TypedValue().COMPLEX_UNIT_PX))
    mm_to_px = float(java_widget_manager.convertUnit(1.0, java.clazz.android.util.TypedValue().COMPLEX_UNIT_MM,  java.clazz.android.util.TypedValue().COMPLEX_UNIT_PX))
    return {'px': 1.0, 'dp': dp_to_px, 'dip': dp_to_px, 'sp': sp_to_px, 'pt': pt_to_px, 'in': in_to_px, 'mm': mm_to_px}

def convert_unit(value):
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
    def __init__(self, id, key, factor):
        self.id = id
        self.key = key
        self.factor = factor

    def compile(self):
        return dict(id=self.id, type=self.key, factor=self.factor)

class AttributeBase:
    pass
    
class AttributeValue(AttributeBase):
    def __init__(self, *args):
        if any(isinstance(arg, AttributeBase) for arg in args):
            raise ValueError('AttributeValue misuse')
        self.pol = tuple(convert_unit(arg) for arg in args)

    def __add__(self, other):
        if isinstance(other, AttributeBase):
            if not isinstance(other, AttributeValue):
                raise ValueError('cannot add min/max attributes')
            return AttributeValue(*self.pol, *other.pol)
        else:
            return AttributeValue(*self.pol, other)
            
    def __mul__(self, other):
        if isinstance(other, AttributeBase):
            raise ValueError('cannot multiply attributes')
        return AttributeValue(*((Reference(e.id, e.key, e.factor * other) if isinstance(e, Reference) else e * convert_unit(other)) for e in self.pol))

    def __sub__(self, other):
        return self + (-other)
    
    def __truediv__(self, other):
        return self * (1 / other)

    def __neg__(self):
        return self.__mul__(-1.0)

    def __radd__(self, other):
        return self.__add__(other)
    def __rsub__(self, other):
        return self.__sub__(other)
    def __rmul__(self, other):
        return self.__mul__(other)
    def __rtruediv__(self, other):
        return self.__truediv__(other)

    def compile(self):
        amount = 0
        refs = []
        for e in self.pol:
            if isinstance(e, Reference):
                refs.append(e.compile())
            else:
                amount += e
        return dict(function='IDENTITY', arguments=[dict(amount=amount, references=refs)])

class AttributeFunction(AttributeBase):
    def __init__(self, function, *attrs):
        self.args = tuple(attr if isinstance(attr, AttributeBase) else AttributeValue(attr) for attr in attrs)
        self.function = function
    
    def compile(self):
        return dict(function=self.function, arguments=[arg.compile()['arguments'][0] for arg in self.args])
        
    @classmethod
    def min(cls, *args):
        return cls('MIN', *args)
        
    @classmethod
    def max(cls, *args):
        return cls('MAX', *args)
        
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
def attribute_write_hcenter(e, value):
    if value is None:
        del e.left
    else:
        e.left = -(e.width / 2) + value
def attribute_write_vcenter(e, value):
    if value is None:
        del e.top
    else:
        e.top = -(e.height / 2) + value
        
attrs = dict(left='LEFT', top='TOP', right='RIGHT', bottom='BOTTOM', width='WIDTH', height='HEIGHT')
composite_attrs = dict(ileft=attribute_ileft, itop=attribute_itop, iright=attribute_iright, ibottom=attribute_ibottom,
                       hcenter=attribute_hcenter, vcenter=attribute_vcenter, ihcenter=attribute_ihcenter, ivcenter=attribute_ivcenter)
write_attrs = dict(hcenter=attribute_write_hcenter, vcenter=attribute_write_vcenter)
class WidgetAttribute:
    def __getattr__(self, item):
        if item in attrs:
            return AttributeValue(Reference(-1, attrs[item], 1))
        if item in composite_attrs:
            return composite_attrs[item](self)
        raise AttributeError(item)

last_func_for_widget_id = {}
def call_function(func, captures, **kwargs):
    #for tracing errors to their module
    if 'widget' in kwargs and hasattr(kwargs['widget'], 'widget_id'):
        last_func_for_widget_id[kwargs['widget'].widget_id] = func

    pass_args = copy.deepcopy(captures)
    pass_args.update(kwargs) #kwargs priority

    args, kwargs, has_vargs, has_vkwargs = get_args(func)
    try:
        return func(**{k:v for k,v in pass_args.items() if k in args or k in kwargs or has_vkwargs})
    except Exception:
        set_module_error(inspect.getmodule(func), traceback.format_exc())
        raise

def call_general_function(func, **kwargs):
    if not isinstance(func, (list, tuple)):
        func = (func, {})
    return call_function(func[0], func[1], **kwargs)

def deserialize_arg(arg):
    if not isinstance(arg, dict):
        return arg
    if arg['type'] == 'null':
        return None
    if arg['type'] == 'primitive':
        return arg['value']

    #gotta go to java
    return java.clazz.com.appy.Serializer().deserializeString(json_dumps(arg))

def serialize_arg(arg):
    #probably already serialized
    if isinstance(arg, dict):
        return arg

    if arg is None or arg == java.Null:
        return AttrDict(type='null')

    if not isinstance(arg, java.Object):
        return AttrDict(type='primitive', value=arg)

    #gotta go to java
    return json_loads(java.clazz.com.appy.Serializer().serializeToString(arg))


class Element:
    __slots__ = ('d',)
    def __init__(self, d):
        self.init(d)

    def init(self, d):
        self.d = AttrDict.make(d)
        if 'id' not in self.d:
            self.d.id = gen_id()
        if 'children' in self.d:
            self.d.children = ChildrenList([c if isinstance(c, Element) else Element(c) for c in arr] for arr in self.d.children)
        else:
            self.d.children = ChildrenList()

    def __getstate__(self):
        return self.dict(do_copy=False)

    def __setstate__(self, state):
        self.init(state)

    def __event__(self, key, **kwargs):
        if 'tag' in self.d and key in self.d.tag:
            func, captures = loads(self.d.tag[key])
            return call_function(func, captures, **kwargs)

    def set_handler(self, key, f, captures):
        if 'tag' not in self.d:
            self.d.tag = {}
        self.d.tag[key] = dumps((f, captures))

    def __delattr__(self, key):
        if key in attrs:
            del self.d.attributes[attrs[key]]
        elif key in write_attrs:
            write_attrs[key](self, None)
        elif 'selectors' in self.d and key in ('style', 'alignment'):
            del self.d.selectors[key]
        elif key in ('children',):
            self.d[key].clear()
        elif key in ('tag',):
            raise AttributeError(f'{key} can not be deleted')
        elif 'tag' in self.d and key in self.d.tag:
            del self.d.tag[key]
        else:
            self.d.methodCalls = [c for c in self.d.methodCalls if c.identifier != key]

    def __getattr__(self, item):
        if item in attrs:
            return AttributeValue(Reference(self.d.id, attrs[item], 1))
        if item in composite_attrs:
            return composite_attrs[item](self)
        if item in ('type', 'id', 'children'):
            return getattr(self.d, item)
        if item in ('style', 'alignment'):
            return getattr(self.d.selectors, item)
        if item in ('tag',):
            if 'tag' not in self.d:
                self.d.tag = {}
            if 'tag' not in self.d.tag:
                self.d.tag['tag'] = AttrDict()
            if isinstance(self.d.tag['tag'], str):
                self.d.tag['tag'] = loads(self.d.tag['tag'])
            return self.d.tag['tag']

        if 'tag' in self.d and item in self.d.tag:
            attr = self.d.tag[item]
            if item in ('click', 'itemclick'):
                attr = loads(attr)
            return attr

        args = [c.arguments if c.identifier == c.method else c.arguments[1:] for c in self.d.methodCalls if c.identifier == method_from_attr(item)] if 'methodCalls' in self.d else []
        if args:
            return deserialize_arg(args[0][0]) if len(args[0]) == 1 else [deserialize_arg(arg) for arg in args[0]]
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

        if key in attrs:
            if value is None:
                delattr(self, key)
            else:
                if not isinstance(value, AttributeBase):
                    value = AttributeValue(value)
                if 'attributes' not in self.d:
                    self.d.attributes = {}
                self.d.attributes[attrs[key]] = value.compile()
        elif key in write_attrs:
            write_attrs[key](self, value)
        elif key in ('click', 'itemclick'):
            if not isinstance(value, (list, tuple)):
                value = (value, {})
            self.set_handler(key, value[0], value[1])
        elif key in ('name',):
            if 'tag' not in self.d:
                self.d.tag = {}
            self.d.tag[key] = value
        elif key in ('tag',):
            raise AttributeError(f'{key} can not be modified')
        elif key in ('style', 'alignment'):
            if 'selectors' not in self.d:
                self.d.selectors = {}
            self.d.selectors[key] = value
        elif key in ('children',):
            if value is None:
                value = ChildrenList()
            elif not isinstance(value, (list, tuple)):
                value = ChildrenList([value])
            else:
                value = ChildrenList(value)
            self.d[key].set(value)
        elif key in ('tint', 'backgroundTint'):
            prev_alpha = -1
            if hasattr(self, 'drawableParameters'):
                prev_alpha = self.drawableParameters[1]
            self.drawableParameters = (key == 'backgroundTint', prev_alpha, value, java.clazz.android.graphics.PorterDuff.Mode().SRC_ATOP, -1)
        elif key in ('alpha', 'backgroundAlpha'):
            prev_color, prev_mode = -1, None
            if hasattr(self, 'drawableParameters'):
                prev_color, prev_mode = self.drawableParameters[2], self.drawableParameters[3]
            self.drawableParameters = (key == 'backgroundAlpha', value & 0xff, prev_color, prev_mode, -1)
        else:
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
                self.d.methodCalls = []

            arguments = [serialize_arg(arg.__raw__() if isinstance(arg, tuple(java.primitive_wraps.values())) else arg) for arg in arguments]
            self.d.methodCalls = [c for c in self.d.methodCalls if c.identifier != identifier] + [AttrDict(identifier=identifier, method=method, arguments=arguments)]
            
    @classmethod
    def create(cls, type, **kwargs):
        e = cls(dict(type=type))
        [setattr(e, k, v) for k,v in kwargs.items()]
        return e

    def dict(self, do_copy, without_id=None):
        if 'tag' in self.d and 'tag' in self.d.tag and not isinstance(self.d.tag['tag'], str):
            self.d.tag['tag'] = dumps(self.d.tag['tag'])
        d = {k: (copy.deepcopy(v) if do_copy else v) for k,v in self.d.items() if k != 'children' and (not without_id or k != 'id')}
        d['children'] = [[c.dict(do_copy=do_copy, without_id=without_id) if isinstance(c, Element) else c for c in arr] for arr in self.children]
        return d

    def duplicate(self):
        return Element(self.dict(do_copy=True, without_id=True))

    def __repr__(self):
        return repr(self.dict(do_copy=False))

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

    def __getitem__(self, item):
        try:
            return super().__getitem__(item)
        except TypeError:
            return self.find_name(item)

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


widget_dims    = WidgetAttribute()

available_widgets = {}

__importing_module = threading.local()

def __set_importing_module(path):
    __importing_module.path = path

def __clear_importing_module():
    __importing_module.path = None

def module_name(path):
    return f'{os.path.splitext(os.path.basename(path))[0]}_{int(hashlib.sha1(path.encode()).hexdigest(), 16) % (10 ** 8)}'

def set_module_error(module, error):
    java_widget_manager.setFileInfo(module.__file__, error)

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

def create_manager_state():
    manager_state = state.State(None, -1) #special own scope
    manager_state.locals('__token__')
    manager_state.locals('chosen')
    token = 1
    if getattr(manager_state, '__token__', None) != token:
        manager_state.chosen = {}
        manager_state.__token__ = token

    manager_state.setdefault('chosen', {})
    return manager_state

def create_widget(widget_id):
    manager_state = create_manager_state()
    manager_state.chosen.setdefault(widget_id, None)
    name = None
    if manager_state.chosen[widget_id] is not None:
        name = manager_state.chosen[widget_id].name
    widget = widgets.Widget(widget_id, name)
    return widget, manager_state
    
def get_widget_name(widget_id):
    manager_state = create_manager_state()
    if widget_id in manager_state.chosen and manager_state.chosen[widget_id] is not None:
        return manager_state.chosen[widget_id].name
    raise KeyError(f'no such widget {widget_id}')
    
def get_widgets_by_name(name):
    manager_state = create_manager_state()
    return [widget_id for widget_id, chosen in manager_state.chosen.items() if chosen is not None and chosen.name == name]
    
def choose_widget(widget, name):
    print(f'choosing widget: {widget.widget_id} -> {name}')
    manager_state = create_manager_state()
    manager_state.chosen[widget.widget_id] = AttrDict(name=name, inited=False)
    widget.set_loading()
    widget.invalidate()

def widget_manager_create(widget, manager_state):
    print('widget_manager_create')
    widget.cancel_all_timers()

    #clear state
    manager_state.chosen[widget.widget_id] = None

    restart_btn = widgets.ImageButton(style='success_btn_oval_nopad', click=widgets.restart, colorFilter=0xffffffff, width=140, height=140, right=0, bottom=0, imageResource=java.clazz.android.R.drawable().ic_lock_power_off)
    restart_btn.backgroundTint = widgets.color(r=0, g=0, b=0, a=128)

    #calling java releases the gil and available_widgets might be changed while iterating it
    names = [name for name in available_widgets]

    if not available_widgets:
        lst = widgets.TextView(text='No widgets')
    else:
        lst = widgets.ListView(children=[widgets.TextView(text=name, textSize=30,
                                                                click=(choose_widget, dict(name=name))) for name in names])
    return [lst, restart_btn]

def widget_manager_update(widget, manager_state, views):
    manager_state.chosen.setdefault(widget.widget_id, None)
    chosen = manager_state.chosen[widget.widget_id]
    if chosen is not None and chosen.name is not None:
        available_widget = available_widgets[chosen.name]
        on_create, on_update = available_widget['create'], available_widget['update']
        if not chosen.inited:
            chosen.inited = True
            if on_create:
                return call_general_function(on_create, widget=widget)
            return None
        else:
            if on_update:
                call_general_function(on_update, widget=widget, views=views)
            return views
    return widget_manager_create(widget, manager_state) #maybe present error widget

def set_error_to_widget_id(widget_id, error):
    #try to get the last call_function
    func = last_func_for_widget_id.get(widget_id)
    if func is None:
        #try to get the create or update functions
        widget, manager_state = create_widget(widget_id)
        chosen = manager_state.chosen.get(widget.widget_id)
        if chosen is not None and chosen.name is not None:
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
        set_module_error(inspect.getmodule(func), error)

def refresh_managers():
    manager_state = create_manager_state()
    for widget_id, chosen in manager_state.chosen.items():
        if chosen is None:
            widgets.Widget(widget_id, None).invalidate()

class Handler:
    def __init__(self):
        self.iface = java.create_interface(self, java.clazz.com.appy.WidgetUpdateListener())

    def export(self, input, output):
        state.save() #flushing changes
        if not output:
            return None
        if not isinstance(output, (list, tuple)):
            output = [output]
        out = [e.dict(do_copy=False) for e in output]
        if input is not None and input == out:
            return None
        return json_dumps(out) #c json fucks up because we're not a regular dict, indent=* causes python to use the python implementation

    def import_(self, s):
        d = json_loads(s)
        return d, elist(Element(e) for e in d)

    @java.interface
    def onCreate(self, widget_id):
        print(f'python got onCreate')
        widget, manager_state = create_widget(widget_id)
        return self.export(None, widget_manager_create(widget, manager_state))

    @java.interface
    def onUpdate(self, widget_id, views_str):
        print(f'python got onUpdate')
        widget, manager_state = create_widget(widget_id)
        input, views = self.import_(views_str)
        return self.export(input, widget_manager_update(widget, manager_state, views))

    @java.interface
    def onDelete(self, widget_id):
        print(f'python got onDelete')
        widget, manager_state = create_widget(widget_id)
        state.clean_local_state(widget_id)
        manager_state.chosen.pop(widget_id, None)
        last_func_for_widget_id.pop(widget_id, None)

    @java.interface
    def onItemClick(self, widget_id, views_str, collection_id, position, view_id):
        print(f'python got onitemclick {widget_id} {collection_id} {position} {view_id}')
        input, views = self.import_(views_str)
        collection = views.find_id(collection_id)
        view = collection.children.find_id(view_id) if view_id != 0 else None
        widget, manager_state = create_widget(widget_id)
        handled = collection.__event__('itemclick', widget=widget, views=views, collection=collection, position=position, view=view)
        handled = handled is True
        return java.new.java.lang.Object[()]([handled, self.export(input, views)])

    @java.interface
    def onClick(self, widget_id, views_str, view_id):
        print(f'python got on click {widget_id} {view_id}')
        input, views = self.import_(views_str)
        v = views.find_id(view_id)
        widget, manager_state = create_widget(widget_id)
        v.__event__('click', widget=widget, views=views, view=v)
        return self.export(input, views)

    @java.interface
    def onTimer(self, timer_id, widget_id, views_str, data):
        print('timer called')
        input, views = self.import_(views_str)
        func, captures = loads(data)
        widget, manager_state = create_widget(widget_id)
        call_function(func, captures, timer_id=timer_id, widget=widget, views=views)
        return self.export(input, views)

    @java.interface
    def onPost(self, widget_id, views_str, data):
        print('post called')
        input, views = self.import_(views_str)
        func, captures = loads(data)
        widget, manager_state = create_widget(widget_id)
        call_function(func, captures, widget=widget, views=views)
        return self.export(input, views)

    @java.interface
    def wipeStateRequest(self):
        print('wipe state request called')
        state.wipe_state()

    @java.interface
    def importFile(self, path):
        print(f'import file request called on {path}')
        load_module(path)
        refresh_managers()

    @java.interface
    def deimportFile(self, path):
        clear_module(path)
        refresh_managers()

    @java.interface
    def onError(self, widget_id, error):
        set_error_to_widget_id(widget_id, error)

java_widget_manager = None

def init():
    global java_widget_manager
    context = java.get_java_arg()
    java_widget_manager = context
    print('init')
    prepare_image_cache_dir()
    context.registerOnWidgetUpdate(Handler().iface)
    
def java_context():
    return java_widget_manager
    
def register_widget(name, create, update=None):
    path = getattr(__importing_module, 'path', None)
    if path is None:
        raise ValueError('register_widget can only be called on import')

    if name in available_widgets and available_widgets[name]['pythonfile'] != path:
        raise ValueError(f'name {name} exists')

    dumps(create)
    dumps(update)

    available_widgets[name] = dict(pythonfile=path, create=create, update=update)