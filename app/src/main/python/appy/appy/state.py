import pprint, copy, time
from . import java
from .utils import AttrDict, dumps, loads
import urllib.parse
import threading

java_widget_manager = None
global_state = None

# state layout:
#  {
#    'globals': {'globals': {global_name -> global_value}}
#    'nonlocals: {widget_name: {nonlocal_name -> nonlocal_value}}
#    'locals': {widget_id}: {local_name -> nonlocal_value}}
#  }

def default_state():
    return AttrDict.make({
        'globals': {},
        'nonlocals': {},
        'locals': {},
    })

def escape_join(keys):
    return ':'.join(urllib.parse.quote_from_bytes(str(key).encode('utf8', errors="surrogatepass")) for key in keys)

def unescape_join(s):
    return [urllib.parse.unquote_to_bytes(key).decode('utf8', errors="surrogatepass") for key in s.split(':')]

def load_state():
    global global_state

    saved_state = java_widget_manager.loadAllState()

    global_state = default_state()

    if saved_state:
        for key, value in java.build_python_dict_from_java(saved_state).items():
            if not value:
                print(f'state {key} is null?')
                continue
            global_state[value['scope_name']].setdefault(value['scope_key'], AttrDict())[value['key']] = loads(value['value'])

    AttrDict.__recursive_resetmodified__(global_state)

def save_modified():
    modified = []
    for scope_name, scope_dict in list(global_state.items()):
        #ignore keyerrors from race conditions
        try:
            for scope_key, d in list(scope_dict.items()):
                try:
                    for k,v in list(d.items()):
                        try:
                            if AttrDict.__recursive_ismodified__(v):
                                modified.append((scope_name, scope_key, k))
                                AttrDict.__recursive_resetmodified__(v)
                        except KeyError:
                            pass
                    changed_keys = list(d.keys()) if d.__modified__ is True else d.__modified__
                    for k in changed_keys:
                        modified.append((scope_name, scope_key, k))
                    d.__resetmodified__()
                except KeyError:
                    pass
        except KeyError:
            pass

    if not modified:
        return

    saveSpecificStateDict = {}
    for scope_name, scope_key, key in modified:
        try:
            # save keys as strings, just for uniqueness
            saveSpecificStateDict[escape_join((scope_name, repr(scope_key), repr(key)))] = dict(scope_name=scope_name,
                                                                                                scope_key=scope_key,
                                                                                                key=key,
                                                                                                value=dumps(global_state[scope_name].get(scope_key, {}).get(key)),
                                                                                                deleted=scope_key not in global_state[scope_name] or key not in global_state[scope_name][scope_key])
        except KeyError:
            #race condition?
            continue

    java_widget_manager.saveSpecificState(java.build_java_dict(saveSpecificStateDict))

def setter(d, key, value=None, delete=None):
    if delete:
        del d[key]
    else:
        d[key] = AttrDict.make(value)

def getter(d, key):
    return d.get(key), key in d

class State:
    def __init__(self, widget_name, widget_id):
        if widget_name is None or widget_id is None:
            raise ValueError('Cannot initialize state without widget_name and widget_id')
        self.__dict__['__info__'] = AttrDict(scope_keys=AttrDict(locals=widget_id, nonlocals=widget_name, globals='globals'), scopes={})

    def __copy__(self):
        new = self.__class__.__new__(self.__class__)
        return new.__setstate__(self.__getstate__())

    def __deepcopy__(self, memo):
        raise RuntimeError('Cannot deep copy State object')

    def __getstate__(self):
        return dict(widget_name=self.__info__['scope_keys']['nonlocals'], widget_id=self.__info__['scope_keys']['locals'])

    def __setstate__(self, state):
        self.__init__(widget_name=state['widget_name'], widget_id=state['widget_id'])
        
    def __act__(self, f, scope_name, scope_key, attr, **kwargs):
        scope_dict = global_state[scope_name].setdefault(scope_key, AttrDict())

        return f(scope_dict, attr, **kwargs)
        
    def nonlocals(self, *attrs):
        for attr in attrs:
            self.__info__['scopes'][attr] = 'nonlocals'
        
    def globals(self, *attrs):
        for attr in attrs:
            self.__info__['scopes'][attr] = 'globals'
        
    def locals(self, *attrs):
        for attr in attrs:
            self.__info__['scopes'][attr] = 'locals'

    def __getattr__(self, attr):
        scope = self.__info__['scopes'].get(attr)
        
        if scope is None or scope == 'locals':
            v, found = self.__act__(getter, 'locals', self.__info__['scope_keys']['locals'], attr)
            if found:
                return v
        if scope is None or scope == 'nonlocals':
            v, found = self.__act__(getter, 'nonlocals', self.__info__['scope_keys']['nonlocals'], attr)
            if found:
                return v
        if scope is None or scope == 'globals':
            v, found = self.__act__(getter, 'globals', self.__info__['scope_keys']['globals'], attr)
            if found:
                return v
        raise AttributeError(attr)

    def __changeattr__(self, attr, **kwargs):
        attr_scope = self.__info__['scopes'].get(attr, 'locals')
        self.__act__(setter, attr_scope, self.__info__['scope_keys'][attr_scope], attr, **kwargs)
        
    def __setattr__(self, attr, value):
        self.__changeattr__(attr, value=value)

    def __delattr__(self, attr):
        self.__changeattr__(attr, delete=True)
       
    def __getitem__(self, key):
        return self.__getattr__(key)
        
    def __setitem__(self, key, value):
        return self.__setattr__(key, value)
        
    def __delitem__(self, key):
        return self.__delattr__(key)
        
    def __dir__(self):
        return list(
                        set(self.locals_keys()) |
                        set(self.nonlocals_keys()) |
                        set(self.globals_keys())
                    )

    def globals_keys(self):
        return list(global_state['globals'].get(self.__info__['scope_keys']['globals'], {}).keys())

    def nonlocals_keys(self):
        return list(global_state['nonlocals'].get(self.__info__['scope_keys']['nonlocals'], {}).keys())

    def locals_keys(self):
        return list(global_state['locals'].get(self.__info__['scope_keys']['locals'], {}).keys())

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

    def scopes(self):
        result = {}
        for k,v in self.__info__['scopes']:
            result.setdefault(v, []).append(k)
        return result

def init():
    global java_widget_manager
    java_widget_manager = java.get_java_arg()

    load_state()
        
def state_layout():
    global global_state
    layout = {}
    for scope_type, scope_dict in global_state.items():
        layout.setdefault(scope_type, {})
        for scope_key, scope_value in scope_dict.items():
            if scope_value:
                layout[scope_type].setdefault(scope_key, {})
                for key, value in scope_value.items():
                    layout[scope_type][scope_key][key] = repr(value)
                
    return layout

def state_snapshot(scope, scope_key):
    global global_state

    value_func = pprint.pformat
    if not scope:
        return dict(globals=global_state['globals'].get('globals', {}).keys(),
                    nonlocals=[k for k, v in global_state['nonlocals'].items() if v],
                    locals=[k for k, v in global_state['locals'].items() if v])
    elif not scope_key:
        if scope == 'globals':
            return {key: value_func(value) for key, value in global_state['globals']['globals'].items()}

        return {scope_key: len(scope_values) for scope_key, scope_values in global_state[scope].items() if scope_values}
    else:
        return {key: value_func(value) for key, value in global_state[scope][scope_key].items()}
    
def print_state():
    pprint.pp(global_state)

def wipe_state():
    global global_state
    global_state = default_state()
    java_widget_manager.deleteSavedState()
    
def clean_state(scope, widget, key):
    try:
        if scope in ('nonlocals', 'locals') and widget is None:
            raise ValueError(f'invalid operation: {scope} {widget} {key}')

        state = State(widget if scope == 'nonlocals' else '', widget if scope == 'locals' else -1)
        scope_funcs = {'globals': (state.globals, state.globals_keys),
                       'nonlocals': (state.nonlocals, state.nonlocals_keys),
                       'locals': (state.locals, state.locals_keys)}
        if scope not in scope_funcs:
            raise ValueError(f'no such scope {scope}')

        scope_def, scope_dir = scope_funcs[scope]

        if key is not None:
            scope_def(key)
            del state[key]
        else:
            keys = scope_dir()
            scope_def(*keys)
            for key in keys:
                del state[key]

        save_modified()
    except KeyError:
        pass #already cleared


def clean_nonlocal_state(name):
    clean_state('nonlocals', name, None)

def clean_local_state(widget_id):
    clean_state('locals', widget_id, None)

def clean_global_state():
    clean_state('globals', None, None)
