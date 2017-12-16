import ctypes
import sys

class LogcatWriter:
    VERBOSE = 2
    DEBUG = 3
    INFO = 4
    WARN = 5
    ERROR = 6
    FATAL = 7
    
    def __init__(self, lvl):
        self.liblog = ctypes.cdll.LoadLibrary('liblog.so')
        self.android_log_print = getattr(self.liblog, '__android_log_print')
        self.lvl = lvl
        self.buf = b''
        
    def write(self, s):
        if isinstance(s, str):
            s = s.encode()
        self.buf = self.buf + s
        while True:
            i = self.buf.find(b'\n')
            if i == -1:
                break
            self.android_log_print(self.lvl, b'HAPY', self.buf[:i])
            self.buf = self.buf[i + 1:]
            
sys.stdout = LogcatWriter(LogcatWriter.INFO)
sys.stderr = LogcatWriter(LogcatWriter.ERROR)
