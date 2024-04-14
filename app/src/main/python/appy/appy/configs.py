import json
from .utils import AttrDict
from . import java

global_configs = AttrDict()
global_raw_configs = AttrDict()

class ChangeListener(java.implements(java.clazz.appy.Configurations.ChangeListener())):
    @java.override
    def onChange(self):
        sync()

def set_defaults(widget, dic):
    pairs = ((k, json.dumps(v) if not k.endswith('_nojson') else v) for k,v in dic.items())
    ks, vs = zip(*pairs)
    java_widget_manager.getConfigurations().setDefaultConfig(widget, java.new.java.lang.String[()](ks), java.new.java.lang.String[()](vs))
    sync()

def sync():
    global global_configs, global_raw_configs
    global_configs = AttrDict.make(json.loads(java_widget_manager.getConfigurations().serialize()))
    global_raw_configs = AttrDict.make(json.loads(java_widget_manager.getConfigurations().serialize()))
    for widget, widget_configs in global_configs.items():
        for key in widget_configs:
            value = widget_configs[key]['value']
            if not key.endswith('_nojson'):
                try:
                    value = AttrDict.make(json.loads(value))
                except json.decoder.JSONDecodeError:
                    value = None
            widget_configs[key] = value
            
    for widget, widget_configs in global_raw_configs.items():
        for key in widget_configs:
            widget_configs[key] = widget_configs[key]['value']

def init():
    global java_widget_manager
    java_widget_manager = java.get_java_arg()
    java_widget_manager.getConfigurations().setListener(ChangeListener())
    sync()

init()
