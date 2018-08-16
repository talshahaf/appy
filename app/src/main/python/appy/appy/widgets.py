from . import java, state, widget_manager, utils, configs

AnalogClock    = lambda *args, **kwargs: widget_manager.Element.create('AnalogClock',    *args, **kwargs)
Button         = lambda *args, **kwargs: widget_manager.Element.create('Button',         *args, **kwargs)
Chronometer    = lambda *args, **kwargs: widget_manager.Element.create('Chronometer',    *args, **kwargs)
ImageButton    = lambda *args, **kwargs: widget_manager.Element.create('ImageButton',    *args, **kwargs)
ImageView      = lambda *args, **kwargs: widget_manager.Element.create('ImageView',      *args, **kwargs)
ProgressBar    = lambda *args, **kwargs: widget_manager.Element.create('ProgressBar',    *args, **kwargs)
TextView       = lambda *args, **kwargs: widget_manager.Element.create('TextView',       *args, **kwargs)
RelativeLayout = lambda *args, **kwargs: widget_manager.Element.create('RelativeLayout', *args, **kwargs)

ListView           = lambda *args, **kwargs: widget_manager.Element.create('ListView',           *args, **kwargs)
GridView           = lambda *args, **kwargs: widget_manager.Element.create('GridView',           *args, **kwargs)
StackView          = lambda *args, **kwargs: widget_manager.Element.create('StackView',          *args, **kwargs)
AdapterViewFlipper = lambda *args, **kwargs: widget_manager.Element.create('AdapterViewFlipper', *args, **kwargs)
    
class Widget:
    def __init__(self, widget_id, widget_name):
        self.widget_id = widget_id
        if widget_name is not None:
            self.name = widget_name
            self.state = state.State(widget_name, widget_id)
        else:
            self.name = None
            self.state = None
        self.widget_dims = widget_manager.widget_dims

    def __getattr__(self, item):
        if item == 'config':
            return configs.global_configs[self.name]
        return getattr(self.widget_dims, item)

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

    def set_absolute_timer(self, seconds, f, **captures):
        return self._set_timer(seconds, java.clazz.com.appy.Constants().TIMER_ABSOLUTE, f, captures)

    def set_timeout(self, seconds, f, **captures):
        return self._set_timer(seconds, java.clazz.com.appy.Constants().TIMER_RELATIVE, f, captures)

    def set_interval(self, seconds, f, **captures):
        return self._set_timer(seconds, java.clazz.com.appy.Constants().TIMER_REPEATING, f, captures)

    def _set_timer(self, seconds, t, f, captures):
        return widget_manager.java_context().setTimer(int(seconds * 1000), t, self.widget_id, utils.dumps((f, captures)))

    def cancel_timer(self, timer_id):
        return widget_manager.java_context().cancelTimer(timer_id)

    def invalidate(self):
        widget_manager.java_context().update(self.widget_id)

    def set_loading(self):
        widget_manager.java_context().setLoadingWidget(self.widget_id)

    def cancel_all_timers(self):
        return widget_manager.java_context().cancelWidgetTimers(self.widget_id)

    def post(self, f, **captures):
        widget_manager.java_context().setPost(self.widget_id, utils.dumps((f, captures)))

    def size(self):
        size_arr = widget_manager.java_context().getWidgetDimensions(self.widget_id)
        return int(size_arr[0]), int(size_arr[1])

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
        
def wipe_global():
    state.wipe_state()
   
def file_uri(path):
    return widget_manager.java_context().getUriForPath(path)

def request_permissions(*permissions, timeout=None):
    return _request_permissions(True, *permissions, timeout=timeout)

def has_permissions(*permissions):
    return _request_permissions(False, *permissions)

def _request_permissions(request, *permissions, timeout=None):
    perm_map = {getattr(java.clazz.android.Manifest.permission(), permission) : permission for permission in permissions}
    result = widget_manager.java_context().requestPermissions(java.new.java.lang.String[()](perm_map.keys()), request, timeout if timeout is not None else -1)
    if result == java.Null:
        raise RuntimeError('timeout')
    perms, states = list(result.first), list(result.second)
    granted = [perm_map[perm] for i, perm in enumerate(perms) if states[i] == java.clazz.android.content.pm.PackageManager().PERMISSION_GRANTED]
    denied  = [perm_map[perm] for i, perm in enumerate(perms) if states[i] != java.clazz.android.content.pm.PackageManager().PERMISSION_GRANTED]
    return granted, denied

def color(r=0, g=0, b=0, a=255):
    return ((a & 0xff) << 24) + \
           ((r & 0xff) << 16) + \
           ((g & 0xff) << 8) + \
           ((b & 0xff))
           
def restart():
    print('restarting')
    widget_manager.java_context().restart()

from .widget_manager import register_widget, java_context, elist, call_general_function, AttributeFunction
from .utils import download_resource
