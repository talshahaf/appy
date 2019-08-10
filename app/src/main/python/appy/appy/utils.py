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
    args, varargs, varkw, defaults = inspect.getargspec(f)
    kwargs = []
    if defaults:
        kwargs = args[-len(defaults):]
        args = args[:-len(defaults)]
    return args, kwargs, varargs is not None, varkw is not None

class AttrDict(dict):
    def __init__(self, *args, **kwargs):
        if len(args) == 1 and isinstance(args[0], AttrDict):
            self.__dict__ = {k:v for k,v in args[0].items()}
        else:
            self.__dict__ = dict(*args, **kwargs)

    def __iter__(self, *args, **kwargs):
        return self.__dict__.__iter__(*args, **kwargs)
    def keys(self, *args, **kwargs):
        return self.__dict__.keys(*args, **kwargs)
    def values(self, *args, **kwargs):
        return self.__dict__.values(*args, **kwargs)
    def items(self, *args, **kwargs):
        return self.__dict__.items(*args, **kwargs)
    def __len__(self, *args, **kwargs):
        return self.__dict__.__len__(*args, **kwargs)
    def __str__(self, *args, **kwargs):
        return self.__dict__.__str__(*args, **kwargs)
    def __repr__(self, *args, **kwargs):
        return self.__dict__.__repr__(*args, **kwargs)
    def __getitem__(self, *args, **kwargs):
        return self.__dict__.__getitem__(*args, **kwargs)
    def __setitem__(self, *args, **kwargs):
        return self.__dict__.__setitem__(*args, **kwargs)
    def __delitem__(self, *args, **kwargs):
        return self.__dict__.__delitem__(*args, **kwargs)
    def __contains__(self, *args, **kwargs):
        return self.__dict__.__contains__(*args, **kwargs)
    def __eq__(self, *args, **kwargs):
        return self.__dict__.__eq__(*args, **kwargs)
    
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

RESOURCE_CACHE_DIR = os.path.join(os.environ['TMP'], 'resources')
def prepare_image_cache_dir():
    #TODO somehow cleanup cache every now and then
    #shutil.rmtree(RESOURCE_CACHE_DIR, ignore_errors=True)
    os.makedirs(RESOURCE_CACHE_DIR, exist_ok=True)

def generate_filename(url):
    extension = url[url.rfind('.'):]
    extension = extension if '.' in extension and extension in mimetypes.types_map else ''
    return os.path.join(RESOURCE_CACHE_DIR, hashlib.sha256(url.encode()).hexdigest() + extension)

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
