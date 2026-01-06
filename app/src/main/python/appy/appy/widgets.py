import datetime
from dataclasses import dataclass
from . import java, state, widget_manager, utils, configs, colors

def checkable_click_hook(kwargs):
    if 'checked' in kwargs:
        kwargs['view'].checked = kwargs['checked']

widget_manager.Element.set_event_hooks('CheckBox', dict(click=checkable_click_hook))
widget_manager.Element.set_event_hooks('Switch', dict(click=checkable_click_hook))

AnalogClock    = lambda **kwargs: widget_manager.Element.create('AnalogClock',    **kwargs)
Button         = lambda **kwargs: widget_manager.Element.create('Button',         **kwargs)
CheckBox       = lambda **kwargs: widget_manager.Element.create('CheckBox',       **kwargs)
Chronometer    = lambda **kwargs: widget_manager.Element.create('Chronometer',    **kwargs)
ImageButton    = lambda **kwargs: widget_manager.Element.create('ImageButton',    **kwargs)
ImageView      = lambda **kwargs: widget_manager.Element.create('ImageView',      **kwargs)
ProgressBar    = lambda **kwargs: widget_manager.Element.create('ProgressBar',    **kwargs)
Switch         = lambda **kwargs: widget_manager.Element.create('Switch',         **kwargs)
TextClock      = lambda **kwargs: widget_manager.Element.create('TextClock',      **kwargs)
TextView       = lambda **kwargs: widget_manager.Element.create('TextView',       **kwargs)
RelativeLayout = lambda **kwargs: widget_manager.Element.create('RelativeLayout', **kwargs)

ListView           = lambda **kwargs: widget_manager.Element.create('ListView',           **kwargs)
GridView           = lambda **kwargs: widget_manager.Element.create('GridView',           **kwargs)
StackView          = lambda **kwargs: widget_manager.Element.create('StackView',          **kwargs)
AdapterViewFlipper = lambda **kwargs: widget_manager.Element.create('AdapterViewFlipper', **kwargs)

Var = lambda attr: widget_manager.EmptyElement.create(attr)
parentattributes = widget_manager.ContainerAttributes()
    
class Widget:
    def __init__(self, widget_id, widget_name):
        self.widget_id = widget_id
        if widget_name is not None:
            self.name = widget_name
            self.state = state.State(widget_name, widget_id)
        else:
            self.name = None
            self.state = None
        self.widget_dims = parentattributes

    def __copy__(self):
        new = self.__class__.__new__(self.__class__)
        return new.__setstate__(self.__getstate__())

    def __deepcopy__(self, memo):
        raise RuntimeError('Cannot deep copy Widget object')

    def __getstate__(self):
        return dict(widget_id=self.widget_id, name=self.name)

    def __setstate__(self, state):
        self.__init__(state['widget_id'], state['name'])

    def __getattr__(self, item):
        if item == 'config':
            return configs.global_configs[self.name]
        if item == 'raw_config':
            return configs.global_raw_configs[self.name]
        return getattr(self.widget_dims, item)
        
    def __eq__(self, other):
        return self.widget_id == other.widget_id

    def locals(self, *attrs):
        self.state.locals(*attrs)

    def nonlocals(self, *attrs):
        self.state.nonlocals(*attrs)

    def globals(self, *attrs):
        self.state.globals(*attrs)

    def local_token(self, token):
        return self._token(token, self.locals, self.clean_local)

    def nonlocal_token(self, token):
        return self._token(token, self.widget, self.clean_nonlocal)

    def global_token(self, token):
        return self._token(token, self.globals, self.wipe_global)

    def _token(self, token, scope, clean):
        scope('__token__')
        existing_token = getattr(self.state, '__token__', None)
        if existing_token != token:
            print(f'{existing_token} != {token} cleaning {scope.__name__}')
            clean()
            self.state.__token__ = token
            return True
        return False

    def clean_local(self):
        state.clean_local_state(self.widget_id)

    def clean_nonlocal(self):
        state.clean_nonlocal_state(self.name)

    def clean_global(self):
        state.clean_global_state()

    def set_absolute_timer(self, timepoint, f, **captures):
        return self.set_timeout(max(0, (timepoint - datetime.datetime.now(tz=timepoint.tzinfo)).total_seconds()), f, **captures)

    def set_timeout(self, seconds, f, **captures):
        return self._set_timer(seconds, java.clazz.appy.Constants().TIMER_RELATIVE, f, captures)

    def set_interval(self, seconds, f, **captures):
        return self._set_timer(seconds, java.clazz.appy.Constants().TIMER_REPEATING, f, captures)

    def _set_timer(self, seconds, t, f, captures):
        return widget_manager.java_context().setTimer(int(seconds * 1000), t, self.widget_id, widget_manager.dump_general_function(f, captures))

    def cancel_timer(self, timer_id):
        return widget_manager.java_context().cancelTimer(timer_id)

    def invalidate(self):
        widget_manager.java_context().update(self.widget_id)

    def set_loading(self):
        widget_manager.java_context().setLoadingWidget(self.widget_id)

    def cancel_all_timers(self):
        return widget_manager.java_context().cancelWidgetTimers(self.widget_id)

    def post(self, f, **captures):
        widget_manager.java_context().setPost(self.widget_id, widget_manager.dump_general_function(f, captures))

    def size(self):
        size_arr = widget_manager.java_context().getWidgetDimensions(self.widget_id)
        return int(size_arr[0]), int(size_arr[1])

    def parse_unit(self, value):
        utils = widget_manager.java_context().getUtils()
        return utils.parseUnit(widget_manager.java_context(), value, java.jint[()](self.size()))

    def start_activity(self, screen=None):
        widget_manager.java_context().startMainActivity(screen, None)

    def start_config_activity(self):
        widget_manager.java_context().startConfigFragment(self.name)

    def request_config_change(self, config, timeout=None):
        completed = widget_manager.java_context().requestConfigChange(self.name, config, int(timeout * 1000) if timeout is not None else -1)
        if not completed:
            raise RuntimeError('timeout')

    @staticmethod
    def click_invoker(element_id, views, **kwargs):
        view = views.find_id(element_id)
        view.__event__('click', views=views, view=view, **kwargs)

    @staticmethod
    def itemclick_invoker(element_id, views, **kwargs):
        view = views.find_id(element_id)
        view.__event__('itemclick', views=views, view=view, **kwargs)

    def invoke_click(self, element):
        self.post(self.click_invoker, element_id=element.id)

    def invoke_item_click(self, element, position):
        self.post(self.itemclick_invoker, element_id=element.id, position=position)

    @staticmethod
    def by_id(widget_id):
        return Widget(widget_id, widget_manager.get_widget_name(widget_id))
        
    @staticmethod
    def by_name(name):
        return [Widget(widget_id, name) for widget_id in widget_manager.get_widgets_by_name(name)]
   
def file_uri(path):
    return widget_manager.java_context().getUriForPath(path)

def request_permissions(*permissions, timeout=None):
    return _request_permissions(True, *permissions, timeout=timeout)

def has_permissions(*permissions):
    return _request_permissions(False, *permissions)

def _request_permissions(request, *permissions, timeout=None):
    perm_map = {getattr(java.clazz.android.Manifest.permission(), permission) : permission for permission in permissions}
    result = widget_manager.java_context().requestPermissions(java.new.java.lang.String[()](perm_map.keys()), request, int(timeout * 1000) if timeout is not None else -1)
    if result == java.Null:
        raise RuntimeError('timeout')
    perms, states = list(result.first), list(result.second)
    granted = [perm_map[perm] for i, perm in enumerate(perms) if states[i] == java.clazz.android.content.pm.PackageManager().PERMISSION_GRANTED]
    denied  = [perm_map[perm] for i, perm in enumerate(perms) if states[i] != java.clazz.android.content.pm.PackageManager().PERMISSION_GRANTED]
    return granted, denied

@dataclass
class DialogEditText:
    initial_text: str = ''
    hint: str = ''
    options: list[str] = None

def show_dialog(title, text, buttons=('Yes', 'No'), edittexts: tuple[DialogEditText, ...] | DialogEditText = tuple(), icon_res=None, timeout=None):
    if isinstance(edittexts, DialogEditText):
        edittexts = (edittexts,)
    result = widget_manager.java_context().showAndWaitForDialog(icon_res, title, text,
                                                                java.new.java.lang.String[()](buttons),
                                                                java.new.java.lang.String[()](edittext.initial_text for edittext in edittexts),
                                                                java.new.java.lang.String[()](edittext.hint for edittext in edittexts),
                                                                java.new.java.lang.String[()][()]((java.new.java.lang.String[()](edittext.options) if edittext.options else None) for edittext in edittexts),
                                                                int(timeout * 1000) if timeout is not None else -1)
    if result == java.Null:
        raise RuntimeError('timeout')

    return result.first, *result.second

def color(*args, **kwargs):
    lst_get = lambda l, i, d: l[i] if len(l) > i else d
    float_handler = lambda f: int(f * 0xff) if isinstance(f, float) else f

    r = float_handler(kwargs.get('r', kwargs.get('R', lst_get(args, 0, 0x0 ))))
    g = float_handler(kwargs.get('g', kwargs.get('G', lst_get(args, 1, 0x0 ))))
    b = float_handler(kwargs.get('b', kwargs.get('B', lst_get(args, 2, 0x0 ))))
    a = float_handler(kwargs.get('a', kwargs.get('A', lst_get(args, 3, 0xff))))

    name = kwargs.get('name', kwargs.get('code', lst_get(args, 0, '')))

    found = colors.find_color(name, alpha=a)
    if found:
        return found
    
    return ((a & 0xff) << 24) + \
           ((r & 0xff) << 16) + \
           ((g & 0xff) << 8) + \
           ((b & 0xff))
           
def restart():
    print('restarting')
    widget_manager.java_context().restart(False)

def toast(text, long=False):
    widget_manager.java_context().toast(str(text), long)

def color_(**kwargs):
    return color(**kwargs)
    
def background(name=None, color=None, drawable=None):
    if isinstance(color, dict):
        color = color_(**color)
    elif isinstance(color, (list, tuple)):
        color = color_(*color)
    elif isinstance(color, int):
        color = color
    else:
        color = color_(r=0, g=0, b=0, a=100)

    if drawable is None:
        drawable = R.drawable.rounded_rect

    if isinstance(drawable, str):
        drawable = getattr(R.drawable, drawable)

    bg = RelativeLayout(top=0, left=0, bottom=0, right=0, backgroundResource=drawable)
    bg.backgroundTint = color
    if name is not None:
        bg.name = name
    return bg

from .widget_manager import register_widget, java_context, elist, call_general_function, AttributeValue, R, androidR
from .utils import download_resource, copy_resource, cache_dir, preferred_script_dir, drawable_resource_to_bytes
from .state import wipe_state
from .notifications import simple as simple_notification, cancel as cancel_notification, PermissionError as NotificationError
