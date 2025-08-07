import json, copy
from .utils import AttrDict
from . import java

class ConfigDict(AttrDict):
    #TODO disable writes
    #TODO read lazy hooks?
    pass

global_configs = ConfigDict()
global_raw_configs = ConfigDict()
previous_dict_config = ''

def set_defaults(widget, dic, descriptions):
    jsoned = {k : {'default': (json.dumps(v) if not k.endswith('_nojson') else v), 'description': descriptions.get(k) if descriptions else None} for k,v in dic.items()}
    java.get_java_arg().getConfigurations().setDefaultConfig(widget, java.build_java_dict(jsoned))

def sync(config_dict):
    global global_configs, global_raw_configs, previous_dict_config

    if config_dict == previous_dict_config:
        # no update needed
        return

    new_configs = ConfigDict.make(copy.deepcopy(config_dict))
    new_raw_configs = ConfigDict.make(copy.deepcopy(config_dict))

    previous_dict_config = config_dict

    for widget, widget_configs in new_configs.items():
        for key in widget_configs:
            value = widget_configs[key]['value']
            if not key.endswith('_nojson'):
                try:
                    value = ConfigDict.make(json.loads(value))
                except json.decoder.JSONDecodeError:
                    value = None
            widget_configs[key] = value
            
    for widget, widget_configs in new_raw_configs.items():
        for key in widget_configs:
            widget_configs[key] = widget_configs[key]['value']

    global_configs = new_configs
    global_raw_configs = new_raw_configs
