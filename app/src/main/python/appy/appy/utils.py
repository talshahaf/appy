import base64, pickle, inspect, shutil, functools, mimetypes, hashlib, os, time, re
from pathlib import Path
from . import java

def timeit(f):
    def wrapper(*args, **kwargs):
        ts = time.time()
        result = f(*args, **kwargs)
        te = time.time()
        if te - ts >= 0.005:
            print(f'+++ {f.__name__} {te-ts:.3f} sec')
        return result

    return wrapper

def cap(s):
    return s[0].upper() + s[1:]

def dumps(obj):
    return base64.b64encode(pickle.dumps(obj, protocol=pickle.HIGHEST_PROTOCOL)).decode()

def loads(data):
    return pickle.loads(base64.b64decode(data.encode()))

def get_args(f):
    args, varargs, varkw, defaults, kwonlyargs, kwonlydefaults, annotations = inspect.getfullargspec(f)
    kwargs = []
    if defaults:
        kwargs = args[-len(defaults):]
        args = args[:-len(defaults)]
    kwargs += kwonlyargs
    return args, kwargs, varargs is not None, varkw is not None

def settermethod(use_key=True):
    def dec(f):
        @functools.wraps(f)
        def wrapper(self, *args, **kwargs):
            if use_key and len(args) > 0 and args[0] in self.reserved:
                raise AttributeError(f'The following keys are reserved: {", ".join(self.reserved)}')
            ret = f(self, *args, **kwargs)
            self.__setmodified__(args[0] if use_key and len(args) > 0 else None, use_key and len(args) > 0)
            return ret
        return wrapper
    return dec

class AttrDict(dict):
    reserved = ['__modified__']
    def __init__(self, *args, **kwargs):
        if len(args) == 1 and isinstance(args[0], AttrDict):
            super().__init__({k:v for k,v in args[0].items()})
            object.__setattr__(self, '__modified__', args[0].__modified__)
        else:
            super().__init__(*args, **kwargs)
            self.__resetmodified__()

        if any(res in self for res in self.reserved):
            raise KeyError(f'The following keys are reserved: {", ".join(self.reserved)}')

    def __reduce__(self):
        return (dict, (dict(self),))

    def __resetmodified__(self):
        object.__setattr__(self, '__modified__', set())

    def __setmodified__(self, key, use_key):
        if use_key:
            modified = self.__dict__.setdefault('__modified__', set())
            # once modified is True, there's no point adding keys until reset
            if modified is not True:
                modified.add(key)
        else:
            # everything was modified
            object.__setattr__(self, '__modified__', True)

    ###attr dict

    def __getattr__(self, key):
        try:
            return self.__getitem__(key)
        except KeyError as e:
            raise AttributeError from e

    @settermethod(True)
    def __delattr__(self, key):
        try:
            return self.__delitem__(key)
        except KeyError as e:
            raise AttributeError from e

    @settermethod(True)
    def __setattr__(self, key, value):
        try:
            return self.__setitem__(key, value)
        except KeyError as e:
            raise AttributeError from e

    ###all dict setters

    @settermethod(True)
    def __setitem__(self, *args, **kwargs):
        return super().__setitem__(*args, **kwargs)

    @settermethod(True)
    def __delitem__(self, *args, **kwargs):
        return super().__delitem__(*args, **kwargs)

    @settermethod(False)
    def clear(self, *args, **kwargs):
        return super().clear(*args, **kwargs)

    @settermethod(True)
    def setdefault(self, *args, **kwargs):
        return super().setdefault(*args, **kwargs)

    @settermethod(False)
    def pop(self, *args, **kwargs):
        return super().pop(*args, **kwargs)

    @settermethod(False)
    def popitem(self, *args, **kwargs):
        return super().popitem(*args, **kwargs)

    @settermethod(False)
    def update(self, *args, **kwargs):
        return super().update(*args, **kwargs)

    #################

    @classmethod
    def make(cls, d):
        if isinstance(d, AttrDict):
            return d
        elif isinstance(d, dict):
            for k in d:
                d[k] = cls.make(d[k])
            return AttrDict(d)
        elif isinstance(d, list):
            for i in range(len(d)):
                d[i] = cls.make(d[i])
            return d
        elif isinstance(d, (tuple, set)):
            return type(d)(cls.make(v) for v in d)
        else:
            return d

    @classmethod
    def __recursive_ismodified__(cls, d):
        if not isinstance(d, AttrDict):
            return False
        return any(cls.__recursive_ismodified__(v) for v in list(d.values())) or d.__modified__

    @classmethod
    def __recursive_resetmodified__(cls, d):
        if isinstance(d, AttrDict):
            for v in list(d.values()):
                cls.__recursive_resetmodified__(v)
            d.__resetmodified__()

def prepare_image_cache_dir():
    #TODO somehow cleanup cache every now and then
    #shutil.rmtree(cache_dir(), ignore_errors=True)
    os.makedirs(cache_dir(), exist_ok=True)

RESOURCE_CACHE_DIR = os.path.join(os.environ['TMP'], 'resources')
# for matplotlib
os.environ['MPLCONFIGDIR'] = RESOURCE_CACHE_DIR

def cache_dir():
    return Path(RESOURCE_CACHE_DIR)

saved_script_dir = None
def preferred_script_dir():
    global saved_script_dir
    if saved_script_dir is None:
        saved_script_dir = java.get_java_arg().getPreferredScriptDir()
    return Path(saved_script_dir)

def generate_filename(url):
    extension = url[url.rfind('.'):]
    extension = extension if '.' in extension and extension in mimetypes.types_map else ''
    return cache_dir() / (hashlib.sha256(url.encode()).hexdigest() + extension)

@functools.lru_cache(maxsize=128, typed=True)
def download_resource(url):
    # we import it here to allow using appy without online initialization
    import requests
    filename = generate_filename(url)
    r = requests.get(url, stream=True)
    with open(filename, 'wb') as f:
        for chunk in r.iter_content(chunk_size=1024):
            if chunk:
                f.write(chunk)
    return filename

def download_resource_or_cached(url):
    filename = generate_filename(url)
    if filename.is_file():
        return filename
    return download_resource(url)

@functools.lru_cache(maxsize=128, typed=True)
def copy_resource(external_path):
    filename = generate_filename(external_path)
    with open(external_path, 'rb') as src, open(filename, 'wb') as dst:
        while True:
            buf = src.read(1024)
            if not buf:
                break
            dst.write(buf)
    return filename

def drawable_resource_to_bytes(resource_id, background_color=None, canvas_size_factor=None):
    utils = java.get_java_arg().getUtils()
    if isinstance(canvas_size_factor, int):
        canvas_size_factor = float(canvas_size_factor)
    return utils.bitmapToBytes(utils.drawableToBitmap(utils.resolveDrawable(java.get_java_arg(), resource_id), background_color, canvas_size_factor)).value()

def init_locale(init_args):
    try:
        global current_locale

        namelists = {
            'SHORTWEEK': ('short_week', 7),
            'LONGWEEK': ('long_week', 7),
            'SHORTMONTH': ('short_month', 12),
            'LONGMONTH': ('long_month', 12),
            'AMPM': ('ampm', 2),
        }

        formats = {
            'DATEFORMAT': 'date_format',
            'TIMEFORMAT': 'time_format',
            'DATETIMEFORMAT': 'datetime_format',
        }

        new_locale = {}
        for name, (k, n) in namelists.items():
            if name in init_args:
                new_locale[k] = init_args[name].split(',')
                if len(new_locale[k]) != n:
                    raise ValueError(f'wrong number of {k}: {len(new_locale[k])} != {n}')

        for k, v in formats.items():
            new_locale[v] = convert_java_date_format(init_args[k])

        current_locale = new_locale
    except Exception as e:
        print(f'Cannot init locale, using default ({e})')

def convert_java_date_format(java_format):
    ordered_conv = {
        'EEEE+': ('%25', '%A'),
        'EEE':   ('%24', '%a'),
        'EE':    ('%23', '%a'),
        'E':     ('%22', '%a'),
        'dd':    ('%21', '%d'),
        'd':     ('%20', '%d'),
        'MMMM+': ('%19', '%B'),
        'MMM':   ('%18', '%b'),
        'MM':    ('%17', '%m'),
        'M':     ('%16', '%m'),
        'yyy+':  ('%15', '%Y'),
        'yy':    ('%14', '%y'),
        'y':     ('%13', '%Y'),
        'a+':    ('%12', '%p'),
        'HH':    ('%11', '%H'),
        'H':     ('%10', '%H'),
        'hh':    ('%9',  '%I'),
        'h':     ('%8',  '%I'),
        'mm':    ('%7',  '%M'),
        'm':     ('%6',  '%M'),
        'ss':    ('%5',  '%S'),
        's':     ('%4',  '%S'),
        'S':     ('%3',  '%f'), #should be milliseconds
        'z+':    ('%2',  '%Z'),
        'Z+':    ('%1',  '%z'),
        'X+':    ('%0',  '%:z'),
    }

    format = java_format
    for r, (first_stage, _) in ordered_conv.items():
        format = re.sub(r, first_stage, format)
    for r, (first_stage, second_stage) in ordered_conv.items():
        format = format.replace(first_stage, second_stage)
    return format

def list_get(l, i, default=None):
    try:
        return l[i]
    except IndexError:
        return default

current_locale = None
format_replacements = dict(
    a=lambda tp: list_get(current_locale.get('short_week', []), tp.tm_wday),
    A=lambda tp: list_get(current_locale.get('long_week', []), tp.tm_wday),
    b=lambda tp: list_get(current_locale.get('short_month', []), tp.tm_mon - 1),
    B=lambda tp: list_get(current_locale.get('long_month', []), tp.tm_mon - 1),
    p=lambda tp: list_get(current_locale.get('ampm', []), int(tp.tm_hour >= 12)),
    x=lambda tp: current_locale.get('date_format'),
    X=lambda tp: current_locale.get('time_format'),
    c=lambda tp: current_locale.get('datetime_format'),
)

def strftime_replace(format, time_tuple):
    if current_locale:
        replacements = {}
        newformat = []
        push = newformat.append
        i, n = 0, len(format)
        while i < n:
            ch = format[i]
            i += 1
            if ch == '%':
                if i < n:
                    ch = format[i]
                    mod = ''
                    i += 1
                    if ch in ['-', '#'] and i < n:
                        mod = ch
                        ch = format[i]
                        i += 1
                    if ch in format_replacements:
                        if ch not in replacements:
                            evaluated_format = format_replacements[ch](time_tuple)
                            replacements[ch] = strftime_replace(evaluated_format, time_tuple) if evaluated_format and '%' in evaluated_format else evaluated_format
                        if replacements[ch] is not None:
                            newformat.extend(replacements[ch])
                        else:
                            push('%')
                            push(mod)
                            push(ch)
                    else:
                        push('%')
                        push(mod)
                        push(ch)
                else:
                    push('%')
            else:
                push(ch)
        format = "".join(newformat)
    return format

orig_strftime = time.strftime
@functools.wraps(orig_strftime)
def strftime_wrap(format, time_tuple):
    newformat = strftime_replace(format, time_tuple)
    return orig_strftime(newformat, time_tuple)

time.strftime = strftime_wrap
