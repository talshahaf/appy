import json, copy
from .utils import AttrDict
from . import java

class ConfigDict(AttrDict):
    #TODO disable writes
    #TODO read lazy hooks?
    pass

global_configs = {}
previous_dict_config = ''

def get_value(config, widget_id, raw):
    if widget_id in config['raw_instance_values']:
        #instance override
        if raw or 'instance_values' not in config:
            return config['raw_instance_values'][widget_id]
        return config['instance_values'][widget_id]

    if raw or 'value' not in config:
        return config['raw_value']
    return config['value']

def get_dict(widget, widget_id, raw):
    return ConfigDict({k: get_value(config, widget_id, raw) for k, config in global_configs[widget].items()})

def set_defaults(widget, dic, descriptions):
    jsoned = {k : {'default': (json.dumps(v, indent=2) if not k.endswith('_nojson') else v), 'description': descriptions.get(k) if descriptions else None} for k,v in dic.items()}
    java.get_java_arg().getConfigurations().setDefaultConfig(widget, java.build_java_dict(jsoned))

def try_json_dict(s):
    try:
        value = ConfigDict.make(json.loads(s))
    except json.decoder.JSONDecodeError:
        value = None
    return value

def sync(config_dict):
    global global_configs, previous_dict_config

    if config_dict == previous_dict_config:
        # no update needed
        return

    new_configs = {}
    for widget, widget_configs in config_dict.items():
        new_configs[widget] = {}
        for key, config in widget_configs.items():
            new_configs[widget][key] = dict(raw_value=config['value'],
                                            raw_instance_values={int(k): v for k,v in config.get('instance_values', {}).items()})
            if not key.endswith('_nojson'):
                new_configs[widget][key]['value'] = try_json_dict(config['value'])
                new_configs[widget][key]['instance_values'] = {int(k): try_json_dict(v) for k,v in config.get('instance_values', {}).items()}

    previous_dict_config = config_dict
    global_configs = new_configs
