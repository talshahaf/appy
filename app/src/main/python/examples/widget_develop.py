import time, os, functools
import requests
from urllib.parse import urlparse
from appy import widget_manager, java
from appy.widgets import register_widget, background, TextView, ImageButton, Switch, color, R, show_dialog, preferred_script_dir

def on_config(widget, views):
    reset_timer_if_needed(widget)

# cancel all timers every chance we get to control behaviour
def reset_timer_if_needed(widget):
    widget.cancel_all_timers()
    if widget.state.enabled:
        # set to 1 second so we can have a countdown
        widget.set_interval(1, on_timer)
    else:
        widget.state.auto_disable_counter = 0

def on_timer(widget, views):
    widget.state.interval_counter = widget.state.setdefault('interval_counter', 0) + 1
    widget.state.auto_disable_counter += 1
    
    interval = max(1, int(widget.config.interval))
    
    views['countdown'].text = str(interval - widget.state.interval_counter)
    
    if widget.state.interval_counter >= interval:
        # using post so that countdown text changes before on_refresh call
        widget.post(on_refresh)
        
    if widget.config.auto_disable_after and widget.state.auto_disable_counter >= widget.config.auto_disable_after:
        views['enabled'].checked = False
        on_enable(widget, False)

def drive_url_convert(url):
    # extracting file id and using it with a different url to download file directly
    parsed = urlparse(url)
    if parsed.netloc.lower() != 'drive.google.com':
        # ignore unmatched domain
        return url
        
    file_id = max(parsed.path.split('/'), key=len)
    new_url = f'https://drive.usercontent.google.com/download?id={file_id}&confirm=xxx'
    
    print(f'Changing {url} to {new_url}')
    return new_url
    
def github_url_convert(url):
    # extracting file path to download it in raw form
    parsed = urlparse(url)
    if parsed.netloc.lower() != 'github.com' and not parsed.netloc.lower().endswith('.github.com'):
        # ignore unmatched domain
        return url
    
    path = [part for part in parsed.path.split('/') if part != 'blob']
    new_url = f'https://raw.githubusercontent.com/{"/".join(path)}'
    
    print(f'Changing {url} to {new_url}')
    return new_url

converters = [drive_url_convert, github_url_convert]
    
def download(url, local, last_update_time):
    #apply all converters one by one, converters only modify url if it matches
    url = functools.reduce(lambda v, f: f(v), converters, url)
    
    try:
        # if the web server supports the If-Modified-Since header it might save some time and bandwidth
        timestr = time.strftime('%a, %d %b %Y %H:%M:%S GMT', time.gmtime(last_update_time))
        resp = requests.get(url, headers={"If-Modified-Since": timestr}, stream=True)
        if resp.status_code == 304:
            print('Got 304, not downloading')
            return True
        if resp.status_code != 200:
            return False
            
        with open(local, 'wb') as fh:
            for chunk in resp.iter_content(chunk_size=1024):
                if chunk:
                    fh.write(chunk)
            
        return True
    except requests.exceptions.RequestException as e:
        return False
    
def on_refresh(widget, views):
    # stop timer until done
    widget.cancel_all_timers()
    widget.state.interval_counter = 0
    
    widget.state.setdefault('known_locals', {})
    success = 0
    
    # Filter example entry
    files = [file for file in widget.config.files if not file.get('url', '').startswith('https://www.example.com/')]
    for file in files:
        # parse file config
        if isinstance(file, str):
            # allow for just a list of urls
            url = file
            local = None
        elif isinstance(file, dict):
            # local can be optional
            url = file.get('url')
            local = file.get('local')
        else:
            url = None
            local = None
            
        if url is None:
            print(f"Skipping unknown entry: {file}, must be either str or dict with keys ('url',) or ('url', 'local').")
            continue
        
        if local == None:
            # try to infer local from url filename and preferred_script_dir
            local_name = url[url.rfind('/') + 1:]
            local = os.path.join(preferred_script_dir(), local_name)
            if not local.lower().endswith('.py'):
                print(f"Inferred local '{local_name}' does not end with '.py', skipping.")
                continue
                
        if '/' not in local:
            # local can be just filename
            local = os.path.join(preferred_script_dir(), local)
            
        if os.path.exists(local) and local not in widget.state.known_locals:
            # don't blindly overwrite existing files
            if show_dialog('File exists', f"File '{os.path.basename(local)}' exists and was not created by me. Overwrite?", buttons=('Yes', 'No')) != 0:
                print(f"Not overwriting '{os.path.basename(local)}'.")
                continue
        
        if download(url, local, widget.state.known_locals.get(local, 0)):
            if local in widget.state.known_locals:
                # reloading known files
                if not widget_manager.reload_python_file(local):
                    # reload failed, try adding
                    del widget.state.known_locals[local]
                    
            if local not in widget.state.known_locals:
                # adding unknown files
                if not widget_manager.add_python_file(local):
                    # adding failed
                    continue
            
            # update modified time and success counter
            widget.state.known_locals[local] = time.time()
            success += 1
    
    views['text'].textColor = color(g=255) if success == len(files) else \
                             (color(r=255) if success == 0 else \
                              color(r=255, g=255))
    views['text'].text = f'{success}/{len(files)}'
    
    # reset timer so that fetch time is not counted
    reset_timer_if_needed(widget)    

def on_enable(widget, checked):
    widget.state.enabled = checked
    
    # reset timer when switch is changed
    reset_timer_if_needed(widget)
        
def create(widget):
    bg = background()
    text = TextView(name='text', textSize=30, hcenter=widget.hcenter, vcenter=widget.vcenter)
    countdown = TextView(name='countdown', textColor=0xb3ffffff, textSize=20, bottom=text.itop + 10, hcenter=text.hcenter)
    refresh = ImageButton(click=on_refresh, name='refresh_btn', top=text.ibottom + 10, hcenter=text.hcenter,
                            style='dark_oval_sml', padding=(10, 10, 10, 10), adjustViewBounds=True, colorFilter=0xffffffff,
                            imageResource=R.drawable.ic_action_refresh)
    
    widget.state.enabled = True
    widget.state.auto_disable_counter = 0
    
    enabled = Switch(name='enabled', checked=widget.state.enabled, click=on_enable, top=20, left=10)
    
    reset_timer_if_needed(widget)
    
    widget.post(on_refresh)
    
    return [bg, text, countdown, refresh, enabled]
    
register_widget('widget_develop', create, 
                    config=dict(interval=5, auto_disable_after=3600, files=[{'url': 'https://www.example.com/widget.py&download=1', 'local': 'widget.py'}]), 
                    config_description=dict(interval='Check url every `interval` seconds', auto_disable_after="Disable after some time (in seconds) so it doesn't run forever", files="List of dicts with keys: 'url', 'local'. Downloads from 'url' to optional 'local'"),
                    on_config=on_config)
