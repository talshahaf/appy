import logcat
import faulthandler
import random
from java import *

context = get_java_arg()

param_setters = {}

id_counter = 1

def gen_id():
    global id_counter
    id_counter += 1
    return id_counter

def cap(s):
    return s[0].upper() + s[1:]

def get_param_setter(type, attr):
    method = f'set{cap(attr)}'
    key = (type, method)
    if key not in param_setters:
        param_setters[key] = clazz.com.happy.Widget().getSetterMethod(type, method)
        param_setters[key] = param_setters[key] if param_setters[key] != Null else None
    return param_setters[key], method

elements = {}
def get_element(id, view):
    if id not in elements:
        elements[id] = Element(id, view, view.type)
    return elements[id]

class Element:
    @classmethod
    def create(cls, type, **kwargs):
        id = gen_id()
        view = new.com.happy.DynamicView()
        view.id = id
        view.type = type
        e = Element(gen_id(), view, type)
        for k,v in kwargs.items():
            setattr(e, k, v)

        return e

    def __init__(self, id, view, type):
        self.__dict__['type'] = type
        self.__dict__['known_attrs'] = {}
        self.__dict__['view'] = view
        self.__dict__['id'] = id

    def __setattr__(self, attr, value):
        if attr == 'onClick':
            print('setting special onclick')
            self.view.onClick = create_interface({'onClick': lambda view, *args: value(get_element(view.id, view), *args)}, clazz.com.happy.DynamicView.OnClick())
            return
        elif attr == 'onItemClick':
            print('setting special onitemclick')
            self.view.onItemClick = create_interface({'onClick': lambda col, item, *args: value(get_element(col.id, col), get_element(item.id, item), *args)}, clazz.com.happy.DynamicView.OnItemClick())
            return

        param_setter, method = get_param_setter(self.type, attr)
        if param_setter is not None:
            print(f'calling trivial {param_setter} method: {method} with {value}')
            self.view.methodCalls.add(new.com.happy.RemoteMethodCall(param_setter, new.java.lang.Object[()]([method, value])))
            self.known_attrs[attr] = value
            return

        #maybe try remoteViews method
        try:
            print(f'calling {method} with {value}')
            if not isinstance(value, (list, tuple)):
                value = [value]
            self.view.methodCalls.add(new.com.happy.RemoteMethodCall(method, new.java.lang.Object[()](value)))
        except:
            raise AttributeError()


    def __getattr__(self, attr):
        return self.known_attrs[attr]

    def __lshift__(self, other):
        self.view.children.add(other.view)


class Handler:
    def __init__(self):
        self.widgets = set()
        self.iface = create_interface(self, clazz.com.happy.WidgetUpdateListener())

    @interface
    def onUpdate(self, widget_id, widget):
        print('got update')
        if widget_id in self.widgets:
            return
        self.widgets.add(widget_id)

        def click(e):
            print('clickccc')
            e.text = str(random.randint(0, 100))

        element = Element.create('TextView',
                                 text='dfg',
                                 textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30),
                                 onClick=click)

        #element.setTextViewTextSize(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30) #TODO

        widget.children.add(element.view)
        print('end got update')


def init(widget_manager):
    print('init')
    widget_manager.registerOnWidgetUpdate(Handler().iface)

faulthandler.enable()
init(context)
