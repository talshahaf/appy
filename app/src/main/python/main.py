import logcat
import faulthandler
import random
import json
import functools
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


elements = {}
def get_element(view):
    id = view.id
    if id not in elements:
        elements[id] = Element(id, view, view.type)
    return elements[id]

class Element:
    @classmethod
    def create(cls, type, **kwargs):
        view = new.com.happy.DynamicView(type)
        id = view.getId()
        e = Element(id, view, type)
        for k,v in kwargs.items():
            if k == 'children':
                for c in v:
                    e.append(c)
            else:
                setattr(e, k, v)
        print(f'creating element {id} {type}')
        return e

    def duplicate(self):
        return Element(self.id, self.view.duplicate(), self.type)

    def __init__(self, id, view, type):
        self.__dict__['type'] = type
        self.__dict__['known_attrs'] = {}
        self.__dict__['view'] = view
        self.__dict__['id'] = id

    def __setattr__(self, attr, value):
        if attr == 'onClick':
            print('setting special onclick')
            self.view.onClick = create_interface({'onClick': lambda view, *args: value(get_element(view), *args)}, clazz.com.happy.DynamicView.OnClick())
            return
        elif attr == 'onItemClick':
            print('setting special onitemclick')
            self.view.onItemClick = create_interface({'onClick': lambda col, item, *args: value(get_element(col), get_element(item), *args)}, clazz.com.happy.DynamicView.OnItemClick())
            return

        param_setter, method = get_param_setter(self.type, attr)
        if param_setter is not None:
            print(f'calling trivial {param_setter} method: {method} with {value}')
            self.view.removeIdentifierMethods(method)
            self.view.methodCalls.add(new.com.happy.RemoteMethodCall(method, param_setter, new.java.lang.Object[()]([method, value])))
            self.known_attrs[attr] = value
            return

        #maybe try remoteViews method
        try:
            print(f'calling {method} with {value}')
            if not isinstance(value, (list, tuple)):
                value = [value]
            self.view.removeIdentifierMethods(method)
            self.view.methodCalls.add(new.com.happy.RemoteMethodCall(method, method, new.java.lang.Object[()](value)))
        except:
            raise AttributeError()

    def __getattr__(self, attr):
        return self.known_attrs[attr]

    def __len__(self):
        return self.view.children.size()

    def __getitem__(self, key):
        if isinstance(key, slice):
            return [get_element(self.view.children.get(i)) for i in range(*key.indices(len(self)))]
        elif isinstance(key, int):
            return get_element(self.view.children.get(key))
        else:
            raise IndexError()

    def __delitem__(self, i):
        self.view.children.remove(i)

    def __setitem__(self, key, val):
        if isinstance(key, slice):
            items = [val[c] for c,i in enumerate(range(*key.indices(len(self))))]
            [self.view.children.set(i, item.view) for i, item in zip(range(*key.indices(len(self))), items)]
        elif isinstance(key, int):
            self.view.children.set(key, val.view)
        else:
            raise IndexError()

    def insert(self, i, val):
        self.view.children.add(i, val.view)

    def append(self, val):
        self.view.children.add(val.view)

    def set_children(self, vals):
        self.view.children.clear()
        [self.view.children.add(val.view) for val in vals]

    def __str__(self):
        return f'{self.type}: {self.known_attrs} : [{", ".join(str(c) for c in self)}]'

callbacks = {}

class new_element:
    @classmethod
    def create(cls, type, **kwargs):
        if not validate_type(type):
            raise TypeError(f'unknown type {type}')

        d = dict(id=get_id(), type=type)
        if 'children' in kwargs:
            d['children'] = [e.json() for e in kwargs['children']]
            del kwargs['children']

        e = new_element(json.dumps(d))
        [setattr(e, k, v) for k,v in kwargs.items()]
        return e

    def __init__(self, j):
        self.__dict__['d'] = json.loads(j)
        if 'methodCalls' not in self.d:
            self.d['methodCalls'] = []
        if 'children' not in self.d:
            self.d['children'] = []
        else:
            self.d['children'] = [new_element(json.dumps(child)) for child in self.d['children']]

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
        if attr == 'onClick':
            print('setting special onclick')
            callbacks[id(value)] = value
            if 'tag' not in self.d:
                self.d['tag'] = {}
            self.d['tag']['click'] = id(value)
            return
        elif attr == 'onItemClick':
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

    def pure_dict(self, e):
        if isinstance(e, new_element):
            e = e.d
        if isinstance(e, dict):
            return {k: self.pure_dict(v) for k,v in e.items()}
        elif isinstance(e, list):
            return [self.pure_dict(v) for v in e]
        elif isinstance(e, tuple):
            return tuple(self.pure_dict(v) for v in e)
        else:
            return e

    def json(self):
        return json.dumps(self.pure_dict(self.d))

def creator(type):
    return lambda **kwargs: new_element.create(type, **kwargs)

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
                                       onClick=(lambda widget_id, name: (lambda _: choose_widget(widget_id, name)))(widget_id, name)) #capture
                              for name in available_widgets]).view

def widget_manager_update(widget_id, root):
    try:
        if widget_id in chosen_widgets:
            name, inited = chosen_widgets[widget_id]
            on_create, on_update = available_widgets[name]
            if not inited:
                chosen_widgets[widget_id] = (name, True)
                print(f'calling oncreate of {name}')
                return on_create().view
            else:
                print(f'calling onupdate of {name}')
                if on_update:
                    on_update(root)
                return None #doesn't update the root
    except Exception as e:
        print('got exception')
        print(e)
        return widget_manager_create(widget_id) #maybe present error widget

class Handler:
    def __init__(self):
        self.iface = create_interface(self, clazz.com.happy.WidgetUpdateListener())

    @interface
    def onCreate(self, widget_id):
        print('got create')
        e = TextView(text='hey', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30))
        e.onClick = lambda w, self: setattr(self, 'text', chr(random.randint(65, 75)) )
        return clazz.com.happy.DynamicView().fromJSON(e.json())
        try:

            return widget_manager_create(widget_id)
        except Exception:
            print('exception: {}'.format(traceback.format_exc()))

    @interface
    def onUpdate(self, widget_id, root):
        print('got update')
        e = new_element(root.toJSON())
        # d['type'] = 'ListView'
        # d['children'] = [
        #             {
        #                 'type': 'TextView',
        #                 'methodCalls': [
        #                                     {
        #                                         'identifier': 'setText',
        #                                         'method': 'setCharSequence',
        #                                         'arguments': [
        #                                             'setText',
        #                                             str(random.randint(100, 1000)),
        #                                         ]
        #                                     }
        #                                ]
        #             }
        #         for i in range(100)]
        # print(d)
        #view = clazz.com.happy.DynamicView().fromJSON(e.json())
        #print(view.toString())
        return None

        return widget_manager_update(widget_id, get_element(root))

    @interface
    def onItemClick(self, widget_id, root, parent_id, view_id, position):
        print(f'on itemclick {widget_id}')
        root = new_element(root.toJSON())
        p = root.find(parent_id)
        v = root.find(view_id)
        handled = v.__itemclick__(widget_id, p, v, position)
        if not handled:
            return None #TODO fix not flushing root in this case
        return clazz.com.happy.DynamicView().fromJSON(root.json())

    @interface
    def onClick(self, widget_id, root, view_id):
        print(f'on click {widget_id}')
        root = new_element(root.toJSON())
        v = root.find(view_id)
        v.__click__(widget_id, v)
        return clazz.com.happy.DynamicView().fromJSON(root.json())


def init(widget_manager):
    print('init')
    widget_manager.registerOnWidgetUpdate(Handler().iface)



faulthandler.enable()
init(context)



#=========================================================================
def example_on_create():
    txt = TextView(text='zxc', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30), onClick=lambda e: setattr(e, 'text', str(random.randint(50, 60))))
    lst = ListView(children=[
        LinearLayout(children=[
            txt
        ]),
        LinearLayout(children=[
            txt.duplicate()
        ]),
    ])

    #root.append(LinearLayout(children=[]))
    return lst

def example2_on_create():
    return LinearLayout(children=[Button(text='ref', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30), onClick=lambda e: None),
                                  TextView(text='bbb', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30))
                                  ])

def logcat_on_create():
    return ListView(children=[TextView(text='ready...', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 15), onClick=lambda e: None)])

def logcat_on_update(root):
    views = [TextView(text=str(random.randint(100, 1000)), textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 15), onClick=lambda e: None) for l in range(100)]
    print('sup')

def newwidget_on_create():
    return None

def newwidget_on_update(root):
    print(root)

available_widgets['example'] = (example_on_create, None)
available_widgets['example2'] = (example2_on_create, None)
available_widgets['logcat'] = (logcat_on_create, logcat_on_update)