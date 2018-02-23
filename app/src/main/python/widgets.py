import logcat
import faulthandler
import json
import functools
import copy
import traceback
import java
import pickle
import base64

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

id_counter = 1
def get_id():
    global id_counter
    c = id_counter
    id_counter += 1
    return c

def cap(s):
    return s[0].upper() + s[1:]

@functools.lru_cache(maxsize=128, typed=True)
def validate_type(type):
    return java.clazz.com.happy.Widget().typeToClass.containsKey(type)

@functools.lru_cache(maxsize=128, typed=True)
def get_param_setter(type, attr):
    method = f'set{cap(attr)}'
    setter = java.clazz.com.happy.Widget().getSetterMethod(type, method)
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
class Element:
    def __init__(self, d):
        self.__dict__['d'] = AttrDict.make(d)
        if 'id' not in self.d:
            self.d.id = get_id()
        if 'children' in self.d:
            self.d.children = [[c if isinstance(c, Element) else Element(c) for c in arr] for arr in self.d.children]

    def __event__(self, key, *args):
        if 'tag' in self.d and key in self.d.tag:
            f, captures = self.d.tag[key]
            captures = pickle.loads(base64.b64decode(captures.encode()))
            usable_functions[f](*args, **captures)

    def set_handler(self, key, f, **captures):
        assert_usable(f)
        if 'tag' not in self.d:
            self.d.tag = {}
        self.d.tag[key] = (id(f), base64.b64encode(pickle.dumps(captures, protocol=pickle.HIGHEST_PROTOCOL)).decode())

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

Button = lambda *args, **kwargs: Element.create('Button', *args, **kwargs)
TextView = lambda *args, **kwargs: Element.create('TextView', *args, **kwargs)
ListView = lambda *args, **kwargs: Element.create('ListView', *args, **kwargs)

available_widgets = {}
chosen_widgets = {}

usable_functions = {}
__simpledefined_locked = False

def lock_simplydefined():
    global __simpledefined_locked
    __simpledefined_locked = True

def simplydefined(f):
    if __simpledefined_locked:
        raise ValueError('function is not simply defined')
    usable_functions[id(f)] = f
    return f

def is_usable_function(f):
    return id(f) in usable_functions

def assert_usable(*args):
    for f in args:
        if not is_usable_function(f):
            raise ValueError(f'function {f.__name__} is not decorated with @simplydefined')

@simplydefined
def choose_widget(*args, widget_id, name):
    chosen_widgets[widget_id] = (name, False)

def widget_manager_create(widget_id):
    chosen_widgets.pop(widget_id, None)
    return ListView(children=[TextView(text=name, textViewTextSize=(java.clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30),
                                       click=(choose_widget, dict(widget_id=widget_id, name=name)))
                              for name in available_widgets])

def widget_manager_update(widget_id, views):
    try:
        if widget_id in chosen_widgets:
            name, inited = chosen_widgets[widget_id]
            on_create, on_update = available_widgets[name]
            if not inited:
                chosen_widgets[widget_id] = (name, True)
                if on_create:
                    return on_create(widget_id)
            else:
                if on_update:
                    return on_update(widget_id, views)
            return None #doesn't update the root
    except Exception as e:
        print(traceback.format_exc())
        return widget_manager_create(widget_id) #maybe present error widget

# class State:
#     def __init__(self, widget_id):
#         self.widget_id = widget_id
#
#     def __getitem__(self, item):


class Handler:
    def __init__(self):
        self.iface = java.create_interface(self, java.clazz.com.happy.WidgetUpdateListener())

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
        return self.export(widget_manager_update(widget_id, self.import_(views)))

    @java.interface
    def onItemClick(self, widget_id, views, collection_id, position):
        print(f'python got onitemclick {widget_id} {collection_id} {position}')
        #print(f'{views}')
        views = self.import_(views)
        v = self.find(views, collection_id)
        handled = v.__event__('itemclick', v, position)
        if not handled:
            return None #TODO fix not flushing root in this case
        return self.export(views)

    @java.interface
    def onClick(self, widget_id, views, view_id):
        print(f'python got onclick {widget_id} {view_id}')
        #print(f'{views}')
        views = self.import_(views)
        v = self.find(views, view_id)
        v.__event__('click', v)
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

def pip_install(package):
    import pip
    pip.main(['install', package])

def ensurepip():
    import ensurepip
    ensurepip._main()


