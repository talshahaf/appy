import json
from .utils import AttrDict
from . import java

global_configs = AttrDict()

class ChangeListener(java.implements(java.clazz.appy.Configurations.ChangeListener())):
    @java.override
    def onChange(self):
        sync()

def set_defaults(widget, dic):
    k, v = zip(*dic.items())
    v = (json.dumps(e) for e in v)
    java_widget_manager.getConfigurations().setDefaultConfig(widget, java.new.java.lang.String[()](k), java.new.java.lang.String[()](v))
    sync()

def sync():
    global global_configs
    global_configs = AttrDict.make(json.loads(java_widget_manager.getConfigurations().serialize()))
    for widget, widget_configs in global_configs.items():
        for key in widget_configs:
            widget_configs[key] = AttrDict.make(json.loads(widget_configs[key]['value']))

def init():
    global java_widget_manager
    java_widget_manager = java.get_java_arg()
    java_widget_manager.getConfigurations().setListener(ChangeListener())
    sync()

init()
