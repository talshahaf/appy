import pprint
import java
from utils import AttrDict, dumps, loads

java_widget_manager = None

def default_state():
    return AttrDict({
        'globals': {},
        'widget': {},
        'locals': {},
    })

global_state = None

def save():
    java_widget_manager.saveState(dumps(global_state))

def setter(d, k, v):
    d[k] = v
    save()

def getter(d, k):
    return d.get(k), k in d

class State:
    def __init__(self, widget_name, widget_id):
        self.__dict__['__info__'] = AttrDict(AttrDict(scope_keys=AttrDict(widget=widget_name, locals=widget_id, globals=None), scopes={}))
    
    def __getscope__(self, scope_name, scope_key):
        return global_state[scope_name].setdefault(scope_key, {}) if scope_key is not None else global_state[scope_name]
        
    def __act__(self, f, scope_name, scope_key, attr, *args):
        return f(self.__getscope__(scope_name, scope_key), attr, *args)
        
    def widget(self, key):
        self.__info__.scopes[key] = 'widget'
        
    def globals(self, key):
        self.__info__.scopes[key] = 'globals'
        
    def locals(self, key):
        self.__info__.scopes[key] = 'locals'
            
    def __getattr__(self, attr):
        scope = self.__info__.scopes.get(attr, 'locals')
        
        if scope is None or scope == 'locals':
            v, found = self.__act__(getter, 'locals', self.__info__.scope_keys.locals, attr)
            if found:
                return v
        if scope is None or scope == 'widget':
            v, found = self.__act__(getter, 'widget', self.__info__.scope_keys.widget, attr)
            if found:
                return v
        if scope is None or scope == 'globals':
            v, found = self.__act__(getter, 'globals', self.__info__.scope_keys.globals, attr)
            if found:
                return v
        raise AttributeError()
        
    def __setattr__(self, attr, value):
        if attr in self.__info__.scopes:
            self.__act__(setter, self.__info__.scopes[attr], self.__info__.scope_keys[self.__info__.scopes[attr]], attr, value)
        else:
            self.__act__(setter, 'locals', self.__info__.scope_keys.locals, attr, value)
            
    def __dir__(self):
        return list(
                        set(self.__getscope__('locals', self.__info__.scope_keys.locals).keys()) |
                        set(self.__getscope__('widget', self.__info__.scope_keys.widget).keys()) |
                        set(self.__getscope__('globals', self.__info__.scope_keys.globals).keys())
                    )

    def __contains__(self, attr):
        return hasattr(self, attr)

    def get(self, attr, default=None):
        return getattr(self, attr, default)

    def setdefault(self, attr, default=None):
        try:
            return getattr(self, attr)
        except AttributeError:
            setattr(self, attr, default)
            return default

def init():
    global java_widget_manager, global_state
    java_widget_manager = java.get_java_arg()
    state = java_widget_manager.loadState()
    if state != java.Null:
        global_state = AttrDict(loads(str(state)))
    else:
        global_state = default_state()

def print_state():
    pprint.pprint(global_state)

def wipe_state():
    global global_state
    global_state = default_state()
    save()

def clean_widget_state(name):
    global_state.widgets.pop(name, None)
    save()

def clean_local_state(widget_id):
    global_state.locals.pop(widget_id, None)
    save()

init()
