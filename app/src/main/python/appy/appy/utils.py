import base64, pickle, inspect, shutil, functools, mimetypes, hashlib, os, time

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

class AttrDict(dict):
    def __init__(self, *args, **kwargs):
        if len(args) == 1 and isinstance(args[0], AttrDict):
            super().__init__({k:v for k,v in args[0].items()})
        else:
            super().__init__(*args, **kwargs)

    def __getattr__(self, key):
        try:
            return self.__getitem__(key)
        except KeyError as e:
            raise AttributeError from e

    def __delattr__(self, key):
        try:
            return self.__delitem__(key)
        except KeyError as e:
            raise AttributeError from e

    def __setattr__(self, key, value):
        try:
            return self.__setitem__(key, value)
        except KeyError as e:
            raise AttributeError from e


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


def prepare_image_cache_dir():
    #TODO somehow cleanup cache every now and then
    #shutil.rmtree(cache_dir(), ignore_errors=True)
    os.makedirs(cache_dir(), exist_ok=True)

RESOURCE_CACHE_DIR = os.path.join(os.environ['TMP'], 'resources')

def cache_dir():
    return RESOURCE_CACHE_DIR

saved_script_dir = None
def preferred_script_dir():
    global saved_script_dir
    if saved_script_dir is None:
        from .widget_manager import java_context
        saved_script_dir = java_context().getPreferredScriptDir()
    return saved_script_dir

def generate_filename(url):
    extension = url[url.rfind('.'):]
    extension = extension if '.' in extension and extension in mimetypes.types_map else ''
    return os.path.join(cache_dir(), hashlib.sha256(url.encode()).hexdigest() + extension)

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

# for matplotlib
os.environ['MPLCONFIGDIR'] = RESOURCE_CACHE_DIR
