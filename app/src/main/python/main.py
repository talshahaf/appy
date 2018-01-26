import logcat
import faulthandler
import random
import json
import functools
import pprint
from java import *

context = get_java_arg()

id_counter = 0
def get_id():
    global id_counter
    id_counter += 1
    return id_counter

def cap(s):
    return s[0].upper() + s[1:]

@functools.lru_cache(maxsize=128, typed=True)
def validate_type(type):
    return clazz.com.happy.Widget().typeToLayout.containsKey(type)

@functools.lru_cache(maxsize=128, typed=True)
def get_param_setter(type, attr):
    method = f'set{cap(attr)}'
    setter = clazz.com.happy.Widget().getSetterMethod(type, method)
    return setter if setter != Null else None, method

callbacks = {}

class Element:
    @classmethod
    def create(cls, type, **kwargs):
        if not validate_type(type):
            raise TypeError(f'unknown type {type}')

        d = dict(type=type)
        if 'children' in kwargs:
            d['children'] = [cls.pure_dict(e) for e in kwargs['children']]
            del kwargs['children']

        e = Element(json.dumps(d))
        [setattr(e, k, v) for k,v in kwargs.items()]
        return e

    def __init__(self, j):
        self.__dict__['d'] = json.loads(j)
        if 'id' not in self.d:
            self.d['id'] = get_id()
        if 'methodCalls' not in self.d:
            self.d['methodCalls'] = []

        if 'children' not in self.d:
            self.d['children'] = []
        else:
            print(self.d)
            self.d['children'] = [Element(json.dumps(child)) for child in self.d['children']]

    @property
    def type(self):
        return self.d['type']

    @property
    def id(self):
        return self.d['id']

    @property
    def children(self):
        return self.d['children'] #readwrite

    def find(self, view_id):
        if self.d['id'] == view_id:
            return self
        for c in self.d['children']:
            ret = c.find(view_id)
            if ret is not None:
                return ret
        return None

    def duplicate(self):
        return Element(self.json(without_id=True))

    def __click__(self, *args):
        if 'tag' not in self.d or 'click' not in self.d['tag']:
            #raise ValueError('no click?')
            print(f'no onclick: {d}, {callbacks}')
            return
        callbacks[self.d['tag']['click']](*args)

    def __itemclick__(self, *args):
        if 'tag' not in self.d or 'item_click' not in self.d['tag']:
            #raise ValueError('no item_click?')
            return
        callbacks[self.d['tag']['item_click']](*args)

    def __setattr__(self, attr, value):
        if attr == 'click':
            print('setting special onclick')
            callbacks[id(value)] = value
            if 'tag' not in self.d:
                self.d['tag'] = {}
            self.d['tag']['click'] = id(value)
            return
        elif attr == 'itemclick':
            print('setting special onitemclick')
            callbacks[id(value)] = value
            if 'tag' not in self.d:
                self.d['tag'] = {}
            self.d['tag']['item_click'] = id(value)
            return

        param_setter, method = get_param_setter(self.type, attr)
        if param_setter is not None:
            identifier = method
            arguments = [method, value]
            method = param_setter
        else:
            identifier = method
            if not isinstance(value, (list, tuple)):
                value = [value]
            arguments = value

        self.d['methodCalls'] = [c for c in self.d['methodCalls'] if c['identifier'] != method] + [dict(identifier=identifier, method=method, arguments=arguments)]

    @classmethod
    def pure_dict(cls, e, without_id=False):
        if isinstance(e, Element):
            return {k: cls.pure_dict(v, without_id=without_id) for k,v in e.d.items() if k != 'id' or not without_id}
        if isinstance(e, dict):
            return {k: cls.pure_dict(v, without_id=without_id) for k,v in e.items()}
        elif isinstance(e, list):
            return [cls.pure_dict(v, without_id=without_id) for v in e]
        elif isinstance(e, tuple):
            return tuple(cls.pure_dict(v, without_id=without_id) for v in e)
        else:
            return e

    def json(self, without_id=False):
        return json.dumps(self.pure_dict(self, without_id=without_id))

def creator(type):
    return lambda **kwargs: Element.create(type, **kwargs)

FrameLayout = creator('FrameLayout')
LinearLayout = creator('LinearLayout')
RelativeLayout = creator('RelativeLayout')
GridLayout = creator('GridLayout')
AnalogClock = creator('AnalogClock')
Button = creator('Button')
Chronometer = creator('Chronometer')
ImageButton = creator('ImageButton')
ImageView = creator('ImageView')
ProgressBar = creator('ProgressBar')
TextView = creator('TextView')
ViewFlipper = creator('ViewFlipper')
ListView = creator('ListView')
GridView = creator('GridView')
StackView = creator('StackView')
AdapterViewFlipper = creator('AdapterViewFlipper')

available_widgets = {}
chosen_widgets = {}

def choose_widget(widget_id, name):
    print(f'choosing widget {widget_id} -> {name}')
    chosen_widgets[widget_id] = (name, False)

def widget_manager_create(widget_id):
    chosen_widgets.pop(widget_id, None)
    return ListView(children=[TextView(text=name, textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30),
                                       click=(lambda widget_id, name: (lambda _: choose_widget(widget_id, name)))(widget_id, name)) #capture
                              for name in available_widgets])

def widget_manager_update(widget_id, root):
    try:
        if widget_id in chosen_widgets:
            name, inited = chosen_widgets[widget_id]
            on_create, on_update = available_widgets[name]
            if not inited:
                chosen_widgets[widget_id] = (name, True)
                print(f'calling oncreate of {name}')
                if on_create:
                    return on_create()
            else:
                print(f'calling onupdate of {name}')
                if on_update:
                    newroot = on_update(root)
                    if newroot is not None:
                        return newroot
            return None #doesn't update the root

    except Exception as e:
        print('got exception')
        print(e)
        return widget_manager_create(widget_id) #maybe present error widget

class Handler:
    def __init__(self):
        self.iface = create_interface(self, clazz.com.happy.WidgetUpdateListener())

    def export(self, e):
        if e is None:
            return None
        return clazz.com.happy.DynamicView().fromJSON(e.json())

    @interface
    def onCreate(self, widget_id):
        print(f'python got onCreate')
        return self.export(widget_manager_create(widget_id))

    @interface
    def onUpdate(self, widget_id, root):
        print(f'python got onUpdate')
        return self.export(widget_manager_update(widget_id, Element(root.toJSON())))

    @interface
    def onItemClick(self, widget_id, root, parent_id, view_id, position):
        print(f'python got onitemclick {widget_id}')
        root = Element(root.toJSON())
        v = root.find(view_id)
        handled = v.__itemclick__(root.find(parent_id), v, position)
        if not handled:
            return None #TODO fix not flushing root in this case
        return self.export(root)

    @interface
    def onClick(self, widget_id, root, view_id):
        print(f'python got onclick {widget_id}')
        root = Element(root.toJSON())
        v = root.find(view_id)
        v.__click__(v)
        return self.export(root)


def init(widget_manager):
    print('init')
    widget_manager.registerOnWidgetUpdate(Handler().iface)

faulthandler.enable()
init(context)



#=========================================================================
def example_on_create():
    txt = TextView(text='zxc', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30), click=lambda e: setattr(e, 'text', str(random.randint(50, 60))))
    lst = ListView(children=[
        LinearLayout(children=[
            txt
        ]),
        LinearLayout(children=[
            txt.duplicate()
        ]),
    ])
    return lst

def example2_on_create():
    return LinearLayout(children=[Button(text='ref', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30), click=lambda e: None),
                                  TextView(text='bbb', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30))
                                  ])

def logcat_on_create():
    return ListView(children=[TextView(text='ready...', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 15), click=lambda e: None)])

def logcat_on_update(root):
    return ListView(children=[TextView(text=l.decode(), textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 15), click=lambda e: None) for l in logcat.buffer()[-50:]])

def newwidget_on_create():
    return None

def newwidget_on_update(root):
    print(root)

available_widgets['example'] = (example_on_create, None)
available_widgets['example2'] = (example2_on_create, None)
available_widgets['logcat'] = (logcat_on_create, logcat_on_update)

#TODO fix bytes serialization
