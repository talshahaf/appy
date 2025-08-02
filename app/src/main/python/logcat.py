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
    
    def __init__(self, lvl, tag):
        self.lvl = lvl
        if isinstance(tag, str):
            tag = tag.encode('utf8')
        self.tag = tag
        self.buf = b''
        self.crash_handler = None

    def chunksplit(self, s, size):
        return [s[i : i + size] for i in range(0, len(s), size)]
        
    def write(self, s):
        if isinstance(s, str):
            s = s.encode()
        self.buf = self.buf + s

        if not self.buf:
            return

        lines = self.buf.split(b'\n')
        for line in lines[:-1]:
            for chunk in self.chunksplit(line, self.MAX_LINE):
                if chunk:
                    native_appy.logcat_write(self.lvl, self.tag, chunk)
        #last line
        if lines[-1]:
            last_chunks = self.chunksplit(lines[-1], self.MAX_LINE)
            for chunk in last_chunks[:-1]:
                if chunk:
                    native_appy.logcat_write(self.lvl, self.tag, chunk)

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
            
sys.stdout = LogcatWriter(LogcatWriter.INFO,  'APPY')
sys.stderr = LogcatWriter(LogcatWriter.ERROR, 'APPY')
