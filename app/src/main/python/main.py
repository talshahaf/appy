import faulthandler
faulthandler.enable()

import logcat

import subprocess, os, sys, traceback, time, email, tarfile, importlib

def tar_version(path):
    with tarfile.open(path) as tar:
        info_members = [member for member in tar.getmembers() if os.path.basename(member.name) == 'PKG-INFO']
        info_member = min(info_members, key=lambda member: len(member.name))
        info = tar.extractfile(info_member).read()
    return email.message_from_bytes(info)['Version']

INSTALL_PHRASES = [b'Successfully installed']
UNINSTALL_PHRASES = [b'Successfully uninstalled', b'as it is not installed']

def execute(command, kill_phrases=None):
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
        if kill_phrases is not None and any(phrase in nextline for phrase in kill_phrases):
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

#replace all bins and replace with symlinks to our own python3 binary because android forbids executing from app data dir
exe_dir = os.environ['NATIVELIBS']
exe = os.path.join(exe_dir, 'libpythonexe.so')
lib_dir = os.path.join(os.environ['PYTHONHOME'], 'lib')
bin_dir = os.path.join(os.environ['PYTHONHOME'], 'bin')

if os.environ['PATH']:
    os.environ['PATH'] += ':'
os.environ['PATH'] += exe_dir + ":" + bin_dir
os.environ['LD_LIBRARY_PATH'] = lib_dir
os.chdir(bin_dir)

python_links = ['python', 'python3', 'python3.12']
for link in python_links:
    try:
        full = os.path.join(bin_dir, link)
        try:
            os.unlink(full)
        except OSError as e:
            pass
        os.symlink(exe, full)
    except OSError as e:
        pass

#TODO offline initialization
try:
    import pip
except ImportError:
    import ensurepip
    ensurepip._main()
    import pip

try:
    needed_packages = ['pip', 'setuptools', 'wheel', 'requests', 'packaging', 'pyparsing', 'python-dateutil']
    try:
        #TODO maybe import all?
        import requests
    except ImportError:
        print(f'installing {" ".join(needed_packages)}')
        execute([exe, '-m', 'pip', 'install', '--upgrade'] + needed_packages, kill_phrases=INSTALL_PHRASES)
        import requests
except Exception:
    pass #optional packages

upgrade = False
tar = os.path.join(os.environ['TMP'], 'appy.tar.gz')
try:
    module_spec = importlib.util.find_spec('appy')
    if not module_spec:
        raise ImportError('appy is not installed')

    version_file = os.path.join(os.path.dirname(module_spec.origin), '__version__.py')
    out_locals = {}
    exec(open(version_file, 'r').read(), {}, out_locals)
    existing_version = out_locals['__version__']

    available_version = tar_version(tar)
    print(f'versions - existing: {existing_version}, available: {available_version}')
    if existing_version != available_version:
        upgrade = True
        raise ImportError('outdated version')
    import appy
except Exception as e:
    print('error importing appy: ', traceback.format_exc())
    print('installing appy')
    execute([exe, '-m', 'pip', 'uninstall', 'appy' ,'--yes'], kill_phrases=UNINSTALL_PHRASES)
    execute([exe, '-m', 'pip', 'install', os.path.join(os.environ['TMP'], 'appy.tar.gz')], kill_phrases=INSTALL_PHRASES)
    import appy
