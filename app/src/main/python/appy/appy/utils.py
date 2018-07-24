import base64, pickle, inspect, shutil, functools, mimetypes, hashlib, os

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
    def __getattr__(self, item):
        try:
            return self.__getitem__(item)
        except KeyError:
            raise AttributeError()

    def __setattr__(self, key, value):
        try:
            return self.__setitem__(key, value)
        except KeyError:
            raise AttributeError()

    @classmethod
    def make(cls, d):
        if isinstance(d, dict):
            return AttrDict({k: cls.make(v) for k,v in d.items()})
        elif isinstance(d, (list, tuple, set)):
            return type(d)(cls.make(v) for v in d)
        else:
            return d

IMAGE_CACHE_DIR = os.path.join(os.environ['TMP'], 'resources')
def prepare_image_cache_dir():
    #TODO somehow cleanup cache every now and then
    #shutil.rmtree(IMAGE_CACHE_DIR, ignore_errors=True)
    os.makedirs(IMAGE_CACHE_DIR, exist_ok=True)

def generate_filename(url):
    extension = url[url.rfind('.'):]
    extension = extension if '.' in extension and extension in mimetypes.types_map else ''
    return os.path.join(IMAGE_CACHE_DIR, hashlib.sha256(url.encode()).hexdigest() + extension)

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