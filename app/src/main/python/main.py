import faulthandler
faulthandler.enable()

import logcat
import subprocess
import os
import sys
import traceback
import time

def execute(command):
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

    killed = False
    # Poll process for new output until finished
    while True:
        nextline = process.stdout.readline()
        if nextline == '' and process.poll() is not None:
            break
        sys.stdout.write(nextline)
        sys.stdout.flush()

        #XXX until next version of pip
        if b'Successfully installed' in nextline:
            time.sleep(2)
            process.kill()
            killed = True
            break

    output = process.communicate()[0]
    exitCode = process.returncode

    if exitCode == 0 or killed:
        return output
    else:
        raise subprocess.CalledProcessError(command, exitCode)

exe_dir = os.path.join(os.environ['PYTHONHOME'], 'bin')
exe = os.path.join(exe_dir, 'python3.7')

try:
    import pip
except ImportError:
    import ensurepip
    ensurepip._main()
    import pip

try:
    import requests
except ImportError:
    print('installing requests setuptools wheel')
    execute([exe, '-m', 'pip', 'install', '--upgrade', 'pip', 'setuptools', 'wheel', 'requests'])
    import requests

try:
    import appy
except ImportError as e:
    print('error importing appy: ', traceback.format_exc())
    print('installing appy')
    execute([exe, '-m', 'pip', 'install', os.path.join(os.environ['TMP'], 'appy.tar.gz')])
    import appy
