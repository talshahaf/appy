import logcat
import faulthandler
import json
import functools
import copy
import traceback
import inspect
import java
from utils import AttrDict, dumps, loads, cap, get_args
from state import State, clean_local_state

id_counter = 1
def get_id():
    global id_counter
    c = id_counter
    id_counter += 1
    return c

@functools.lru_cache(maxsize=128, typed=True)
def validate_type(type):
    return java.clazz.com.appy.Widget().typeToClass.containsKey(type)

@functools.lru_cache(maxsize=128, typed=True)
def get_param_setter(type, attr):
    method = f'set{cap(attr)}'
    setter = java.clazz.com.appy.Widget().getSetterMethod(type, method)
    return setter if setter != java.Null else None, method

class Reference:
    def __init__(self, id, key, factor):
        self.id = id
        self.key = key
        self.factor = factor

    def compile(self):
        return dict(id=self.id, type=self.key, factor=self.factor)

class AttributeValue:
    def __init__(self, *args):
        self.pol = args

    def __add__(self, other):
        if isinstance(other, AttributeValue):
            return AttributeValue(*self.pol, *other.pol)
        else:
            return AttributeValue(*self.pol, other)

    def __mul__(self, other):
        return AttributeValue(*(Reference(e.id, e.key, e.factor * other) for e in self.pol))

    def __truediv__(self, other):
        return self * (1/other)

    def compile(self):
        amount = 0
        refs = []
        for e in self.pol:
            if isinstance(e, Reference):
                refs.append(e.compile())
            else:
                amount += e
        return dict(function='IDENTITY', arguments=[dict(amount=amount, references=refs)])

attrs = dict(left='LEFT', top='TOP', right='RIGHT', bottom='BOTTOM', width='WIDTH', height='HEIGHT')
class WidgetAttribute:
    def __getattr__(self, item):
        if item in attrs:
            return AttributeValue(Reference(-1, attrs[item], 1))
        raise AttributeError()

class Element:
    def __init__(self, d):
        self.__dict__['d'] = AttrDict.make(d)
        if 'id' not in self.d:
            self.d.id = get_id()
        if 'children' in self.d:
            self.d.children = [[c if isinstance(c, Element) else Element(c) for c in arr] for arr in self.d.children]

    def __event__(self, key, **kwargs):
        if 'tag' in self.d and key in self.d.tag:
            f, captures = self.d.tag[key]
            captures = loads(captures)
            func = usable_functions[f]
            pass_args = captures
            pass_args.update(kwargs) #kwargs priority

            args, kwargs, has_vargs, has_vkwargs = get_args(func)
            func(**{k:v for k,v in pass_args.items() if k in args or k in kwargs or has_vkwargs})

    def set_handler(self, key, f, **captures):
        func_id, f = get_usable_function(f)
        if 'tag' not in self.d:
            self.d.tag = {}
        self.d.tag[key] = (func_id, dumps(captures))

    def click(self, f, **captures):
        self.set_handler('click', f, **captures)

    def itemclick(self, f, **captures):
        self.set_handler('itemclick', f, **captures)

    def __getattr__(self, item):
        if item in attrs:
            return AttributeValue(Reference(self.d.id, attrs[item], 1))
        raise AttributeError()

    def __setattr__(self, key, value):
        if key in attrs:
            if not isinstance(value, AttributeValue):
                value = AttributeValue(value)
            if 'attributes' not in self.d:
                self.d.attributes = {}
            self.d.attributes[attrs[key]] = value.compile()
        elif key in ('click', 'itemclick'):
            if not isinstance(value, (list, tuple)):
                value = (value, {})
            self.set_handler(key, value[0], **value[1])
        else:
            param_setter, method = get_param_setter(self.d.type, key)
            if param_setter is not None:
                identifier = method
                arguments = [method, value]
                method = param_setter
            else:
                identifier = method
                if not isinstance(value, (list, tuple)):
                    value = [value]
                arguments = [getattr(v, '__raw__', lambda: v)() for v in value]

            if 'methodCalls' not in self.d:
                self.d.methodCalls = []
            self.d.methodCalls = [c for c in self.d.methodCalls if c.identifier != method] + [AttrDict(identifier=identifier, method=method, arguments=arguments)]

    @classmethod
    def create(cls, type, children=None, **kwargs):
        if children is None:
            children = []
        if not isinstance(children, (list, tuple)):
            children = [children]
        children = [c if isinstance(c, (list, tuple)) else [c] for c in children]

        #children is now list of lists
        e = cls(dict(type=type, children=children))
        [setattr(e, k, v) for k,v in kwargs.items()]
        return e

    def dict(self, without_id=None):
        d = AttrDict.make({k:copy.deepcopy(v) for k,v in self.d.items() if k != 'children' and (not without_id or k != 'id')})
        if 'children' in self.d:
            d.children = [[c.dict(without_id=without_id) if isinstance(c, Element) else c for c in arr] for arr in self.d.children]
        return d

    def duplicate(self):
        return Element(self.dict(without_id=True))

    #TODO write to children

widget_dims = WidgetAttribute()
Button = lambda *args, **kwargs: Element.create('Button', *args, **kwargs)
TextView = lambda *args, **kwargs: Element.create('TextView', *args, **kwargs)
ListView = lambda *args, **kwargs: Element.create('ListView', *args, **kwargs)

available_widgets = {}

usable_functions = {}
__simpledefined_locked = False

def lock_simplydefined():
    global __simpledefined_locked
    __simpledefined_locked = True

def unique_function_id(f):
    return f'{inspect.getsourcefile(f)}:{f.__name__}'

def simplydefined(f):
    if __simpledefined_locked:
        raise ValueError('function is not simply defined')
    usable_functions[unique_function_id(f)] = f
    return f

def get_usable_function(f):
    try:
        i = unique_function_id(f)
        return i, usable_functions[i]
    except KeyError:
        raise KeyError(f'function {f.__name__} is not decorated with @simplydefined')

def assert_usable(*args):
    for f in args:
        get_usable_function(f)

@simplydefined
def choose_widget(widget_id, name):
    print(f'choosing widget: {widget_id} -> {name}')
    state = State(None, widget_id) #special own scope
    state.name = name
    state.inited = False

@simplydefined
def restart():
    restart_app()

def widget_manager_create(widget_id):
    state = State(None, widget_id) #special own scope
    state.name = None
    #clear state
    btn = Button(text="restart", click=restart, width=widget_dims.width)

    lst = ListView(top=btn.height+20, children=[TextView(text=name, textViewTextSize=(java.clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30),
                                                                click=(choose_widget, dict(name=name))) for name in available_widgets])
    return [btn, lst]

def widget_manager_update(widget_id, views, state):
    try:
        manager_state = State(None, widget_id) #special own scope
        if manager_state.get('name') is not None:
            on_create, on_update = available_widgets[manager_state.name]
            if not state.inited:
                manager_state.inited = True
                if on_create:
                    return on_create(widget_id, state)
            else:
                if on_update:
                    return on_update(widget_id, views, state)
            return None #doesn't update views
    except Exception:
        print(traceback.format_exc())
        return widget_manager_create(widget_id) #maybe present error widget

class Handler:
    def __init__(self):
        self.iface = java.create_interface(self, java.clazz.com.appy.WidgetUpdateListener())

    def export(self, views):
        if not views:
            return None
        if not isinstance(views, (list, tuple)):
            views = [views]
        return json.dumps([e.dict() for e in views], indent=4)

    def import_(self, s):
        return [Element(e) for e in json.loads(s)]

    def find(self, views, id):
        for e in views:
            if e.d.id == id:
                return e
            if 'children' in e.d:
                for c in e.d.children:
                    r = self.find(c, id)
                    if r is not None:
                        return r
        return None

    @java.interface
    def onCreate(self, widget_id):
        print(f'python got onCreate')
        return self.export(widget_manager_create(widget_id))

    @java.interface
    def onUpdate(self, widget_id, views):
        print(f'python got onUpdate')
        state = State(State(None, widget_id).get('name'), widget_id)
        return self.export(widget_manager_update(widget_id, self.import_(views), state))

    @java.interface
    def onDelete(self, widget_id):
        print(f'python got onDelete')
        clean_local_state(widget_id)

    @java.interface
    def onItemClick(self, widget_id, views, collection_id, position):
        print(f'python got onitemclick {widget_id} {collection_id} {position}')
        #print(f'{views}')
        views = self.import_(views)
        v = self.find(views, collection_id)
        state = State(State(None, widget_id).get('name'), widget_id)
        handled = v.__event__('itemclick', widget_id=widget_id, views=views, view=v, position=position, state=state)
        if not handled:
            return None #TODO fix not flushing root in this case
        return self.export(views)

    @java.interface
    def onClick(self, widget_id, views, view_id):
        print(f'python got onclick {widget_id} {view_id}')
        #print(f'{views}')
        views = self.import_(views)
        v = self.find(views, view_id)
        state = State(State(None, widget_id).get('name'), widget_id)
        v.__event__('click', widget_id=widget_id, views=views, view=v, state=state)
        return self.export(views)
        
def register_widget(name, on_create, on_update=None):
    if name in available_widgets:
        raise ValueError(f'name {name} exists')

    assert_usable(on_create, on_update)
    available_widgets[name] = (on_create, on_update)

java_widget_manager = None

def init():
    global java_widget_manager
    faulthandler.enable()
    lock_simplydefined()
    context = java.get_java_arg()
    java_widget_manager = context
    print('init')
    context.registerOnWidgetUpdate(Handler().iface)

def restart_app():
    print('restarting')
    java_widget_manager.restart()

def pip_install(package):
    import pip
    pip.main(['install', package])

def ensurepip():
    import ensurepip
    ensurepip._main()


