import requests, io
from xml.etree import ElementTree as ET
from appy.widgets import register_widget, TextView, ImageView, ListView, Button, AdapterViewFlipper
from appy.templates import background, refresh_button, reset_refresh_buttons_if_needed
from appy import widgets, java

FEED = 'http://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml'

def large_get(url):
    buf = io.BytesIO()
    r = requests.get(url, stream=True)
    for chunk in r.iter_content(chunk_size=1024):
        if chunk:
            buf.write(chunk)
    return buf.getvalue()
    
def namespaces(xml):
    return dict([node for _, node in ET.iterparse(io.StringIO(xml), events=['start-ns'])])

def get():
    xml = large_get(FEED).decode()
    return ET.fromstring(xml), namespaces(xml)
    
def parse(root, namespaces):
    parsed_items = []
    for item in root.find('channel').findall('item'):
        title = item.find('title')
        desc = item.find('description')
        pubDate = item.find('pubDate')
        content = item.find('media:content', namespaces=namespaces)
        d = dict(title=title.text, description=desc.text, date=pubDate.text)
        if content is not None and content.get('medium') == 'image':
            d['image'] = dict(url=content.get('url'), width=content.get('width'), height=content.get('height'))
        parsed_items.append(d)
    return parsed_items
    
def setimage(widget, views, index):
    try:
        img = views['flipper'].children[index]['img']
        img.imageURI=widgets.file_uri(widgets.download_resource(img.tag.url))
    except KeyError:
        pass #no image in item
    except OSError:
        print('error fetching image')
    
def flip(widget, views, amount):
    print(f'flipping {amount}')
    index = (views['flipper'].displayedChild + amount) % len(views['flipper'].children)
    views['flipper'].displayedChild = index
    widget.post(setimage, index=index)
    
def update(widget, views):
    try:
        items = parse(*get())
    except OSError:
        print('error fetching information')
        return
        
    views['flipper'].displayedChild = 0
    
    for item in items[:20]:
        bg = background(widget)
        img = None
        if 'image' in item:
            img = ImageView(name='img', width=widget.width / 3, height=widget.width / 3, adjustViewBounds=True, left=10, top=10)
            img.tag.url = item['image']['url']
        title = TextView(text=item['title'], textSize=15, lines=3, top=img.top if img is not None else 10, left=(img.iright + 20) if img is not None else 10, right=20)
        desc = TextView(text=item['description'], lines=30, top=title.ibottom + 10, left=title.left, right=20)
        date = TextView(text=item['date'], right=20, bottom=0)

        children = [bg, title, desc, date]
        
        if img is not None:
            children.insert(1, img)
        
        views['flipper'].children.append(children)
        
    widget.post(setimage, index=0)
    
def create(widget):
    refresh = refresh_button(update, widget=widget, initial_refresh=True, interval=4*3600)
    del refresh.left
    del refresh.bottom
    refresh.top = 0
    refresh.right = 0
    prev_btn = Button(style='secondary_btn_sml', text='<', left=0, bottom=0, click=(flip, dict(amount=-1)))
    next_btn = Button(style='secondary_btn_sml', text='>', left=prev_btn.iright + 10, bottom=0, click=(flip, dict(amount=1)))
    return [AdapterViewFlipper(name='flipper'), prev_btn, next_btn, refresh]
        
register_widget('rss', create, reset_refresh_buttons_if_needed)
