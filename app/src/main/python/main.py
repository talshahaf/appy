import faulthandler
faulthandler.enable()

print('python main start2')

import sys, os
selfdir = os.path.dirname(__file__)
sys.path.append(selfdir)

import logcat

sys.path.remove(selfdir)

print('python main start')

import subprocess, signal, traceback, time, email, tarfile, importlib.util, site, shutil
from pathlib import Path
from threading import Thread

def tar_version(path):
    with tarfile.open(path) as tar:
        info_members = [member for member in tar.getmembers() if os.path.basename(member.name) == 'PKG-INFO']
        info_member = min(info_members, key=lambda member: len(member.name))
        info = tar.extractfile(info_member).read()
    return email.message_from_bytes(info)['Version']

def print_only_diff(prev, current):
    if not current:
        return
    if prev and current.startswith(prev):
        return print(current[len(prev):].decode(encoding='utf8', errors='ignore'))
    return print(current.decode(encoding='utf8', errors='ignore'))

def execute(command):
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    killed = False

    nochange_counter = 0
    nochange_counter_max = 20
    prev_stdout = b''
    prev_stderr = b''
    while True:
        try:
            output, errs = process.communicate(timeout=1)
            break
        except subprocess.TimeoutExpired as e:
            if prev_stdout != e.stdout or prev_stderr != e.stderr:
                print_only_diff(prev_stdout, e.stdout)
                print_only_diff(prev_stderr, e.stderr)
                prev_stdout = e.stdout
                prev_stderr = e.stderr
                nochange_counter = 0
                continue
            nochange_counter += 1
            if nochange_counter >= nochange_counter_max:
                print("Process didn't output for a while. killing")
                process.kill()
                killed = True
                output, errs = process.communicate()
                break

    print_only_diff(prev_stdout, output)
    print_only_diff(prev_stderr, errs)

    #output = process.communicate()[0]
    exitCode = process.returncode

    if exitCode == 0 or killed:
        return output
    else:
        raise RuntimeError(f'{command} failed with code: {exitCode}')

def install_optional_packages(do_upgrade):
    try:
        needed_packages = ['pip', 'setuptools', 'wheel', 'requests', 'requests-futures', 'packaging', 'pyparsing', 'python-dateutil', 'cycler']
        try:
            #TODO maybe import all?
            import requests, setuptools, cycler
        except ImportError:
            print(f'installing {" ".join(needed_packages)}')
            execute([exe, '-m', 'pip', 'install', *(['--upgrade'] if do_upgrade else [])] + needed_packages)
            import requests
    except Exception as e:
        print("Failed to install optional packages, maybe offline?", e) #TODO propagate


def install_package_with_tar(pkg_path):
    site_dir = Path(site.getsitepackages()[0])

    with tarfile.open(pkg_path) as tar:
        for f in tar.getmembers():
            path = Path(f.name)
            if len(path.parts) > 2 and 'egg-info' not in f.name:
                dest = site_dir / Path(*path.parts[1:])
                print(f'extracting {f.name} to {dest}')
                Path.mkdir(dest.parent, exist_ok=True)
                with open(dest, 'wb') as fh:
                    fh.write(tar.extractfile(f).read())

def uninstall_package_manually(pkg):
    site_dir = Path(site.getsitepackages()[0])
    shutil.rmtree(site_dir / pkg, ignore_errors=True)

def do_init():
    flags = sys.argv[-1]
    try:
        flags = int(flags)
    except ValueError:
        flags = 0

    always_reinstall = bool(flags & 1)

    #replace all bins and replace with symlinks to our own python3 binary because android forbids executing from app data dir
    exe_dir = os.environ['NATIVELIBS']
    exe = os.path.join(exe_dir, 'libpythonexe.so')
    lib_dir = os.path.join(os.environ['PYTHONHOME'], 'lib')
    bin_dir = os.path.join(os.environ['PYTHONHOME'], 'bin')

    if os.environ['PATH']:
        os.environ['PATH'] += ':'
    os.environ['PATH'] += exe_dir + ":" + bin_dir
    os.environ['LD_LIBRARY_PATH'] = lib_dir
    os.environ['LD_PRELOAD'] = os.path.join(exe_dir, 'libprehelpers.so')
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

    try:
        import pip
    except ImportError:
        print('Installing pip')
        try:
            import ensurepip
            ensurepip._main()
            import pip
        except BaseException as e:
            print('Failed to install pip: ', e)

    #running in background
    optional_packages_thread = Thread(target=lambda: install_optional_packages(False))
    optional_packages_thread.start()

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
            raise ImportError('outdated version')
        if always_reinstall:
            raise ImportError('reinstall required')
        import appy
    except Exception as e:
        print('error importing appy: ', traceback.format_exc())
        print('installing appy')
        #execute([exe, '-m', 'pip', 'uninstall', 'appy' ,'--yes'])
        uninstall_package_manually('appy')
        install_package_with_tar(os.path.join(os.environ['TMP'], 'appy.tar.gz'))
        import appy

    optional_packages_thread.join()

    #upgrade packages in background
    Thread(target=lambda: install_optional_packages(True)).start()

    print('appy init start')
    appy.do_init()
    print('python main end')

do_init()
