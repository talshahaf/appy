import native_appy
import sys, os

class LogcatWriter:
    VERBOSE = 2
    DEBUG = 3
    INFO = 4
    WARN = 5
    ERROR = 6
    FATAL = 7

    MAX_LINE = 100
    
    def __init__(self, lvl, std):
        self.lvl = lvl
        self.buf = b''
        self.crash_handler = None
        self.std = std

    def chunksplit(self, s, size):
        return [s[i : i + size] for i in range(0, len(s), size)]
        
    def write(self, s):
#         don't tee to actual std, it might block
#         if self.std is not None:
#             # tee to actual std
#             if isinstance(s, bytes):
#                 self.std.write(s.decode())
#             else:
#                 self.std.write(s)

        if isinstance(s, str):
            s = s.encode()
        self.buf = self.buf + s

        if not self.buf:
            return

        lines = self.buf.split(b'\n')
        for line in lines[:-1]:
            for chunk in self.chunksplit(line, self.MAX_LINE):
                if chunk:
                    native_appy.logcat_write(self.lvl, b'APPY', chunk)
        #last line
        if lines[-1]:
            last_chunks = self.chunksplit(lines[-1], self.MAX_LINE)
            for chunk in last_chunks[:-1]:
                if chunk:
                    native_appy.logcat_write(self.lvl, b'APPY', chunk)

            self.buf = last_chunks[-1]
        else:
            self.buf = b''

    @property
    def fileno(self):
        if self.crash_handler is None:
            self.crash_handler = open(os.path.join(os.environ['TMP'], 'pythoncrash.txt'), 'ab')
        return self.crash_handler.fileno

    def isatty(self):
        return False

    def flush(self):
        self.write('\n')
            
sys.stdout = LogcatWriter(LogcatWriter.INFO,  sys.stdout)
sys.stderr = LogcatWriter(LogcatWriter.ERROR, sys.stderr)
