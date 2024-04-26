import json
from .utils import AttrDict
from . import java

class ConfigDict(AttrDict):
    #TODO disable writes
    #TODO read lazy hooks?
    pass

global_configs = ConfigDict()
global_raw_configs = ConfigDict()
previous_serialized_config = ''

def set_defaults(widget, dic):
    pairs = ((k, json.dumps(v) if not k.endswith('_nojson') else v) for k,v in dic.items())
    ks, vs = zip(*pairs)
    configurations = java.get_java_arg().getConfigurations()
    configurations.setDefaultConfig(widget, java.new.java.lang.String[()](ks), java.new.java.lang.String[()](vs))
    sync(configurations.serialize())

def sync(serialized_config):
    global global_configs, global_raw_configs, previous_serialized_config

    if serialized_config == previous_serialized_config:
        # no update needed
        return

    global_configs = ConfigDict.make(json.loads(serialized_config))
    global_raw_configs = ConfigDict.make(json.loads(serialized_config))
    for widget, widget_configs in global_configs.items():
        for key in widget_configs:
            value = widget_configs[key]['value']
            if not key.endswith('_nojson'):
                try:
                    value = ConfigDict.make(json.loads(value))
                except json.decoder.JSONDecodeError:
                    value = None
            widget_configs[key] = value
            
    for widget, widget_configs in global_raw_configs.items():
        for key in widget_configs:
            widget_configs[key] = widget_configs[key]['value']

