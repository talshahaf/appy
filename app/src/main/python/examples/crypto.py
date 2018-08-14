import json, requests, copy
import pickle
from appy import templates, java
from appy.widgets import ImageView, ImageButton, RelativeLayout
from appy import widgets

#preferences
COINS = ['BTC', 'ETH']
CURRENCY = 'USD'
############

COIN_LIST = 'https://api.coinmarketcap.com/v2/listings/'
SPECIFIC = 'https://api.coinmarketcap.com/v2/ticker/{id}/?convert={currency}'
IMAGE = 'https://s2.coinmarketcap.com/static/img/coins/32x32/{id}.png'

def coin_list():
    return {coin['symbol']: dict(id=coin['id'], name=coin['name'], symbol=coin['symbol']) for coin in json.loads(requests.get(COIN_LIST).text)['data']}
        
def coin_value(id, currency):
    data = json.loads(requests.get(SPECIFIC.format(id=id, currency=currency)).text)['data']['quotes'][currency]
    return dict(price=data['price'], market_cap=data['market_cap'], percent_change_24h=data['percent_change_24h'])

def coin_image(id):
    return IMAGE.format(id=id)
    
def refresh(widget):
    try:
        #cache coin list in a non-local state shared by all crypto widgets
        widget.nonlocals('coin_list')
        if 'coin_list' not in widget.state:
            widget.state.coin_list = coin_list()
        filtered = [copy.deepcopy(widget.state.coin_list[coin]) for coin in COINS]
        for info in filtered:
            info.update(coin_value(info['id'], CURRENCY))
        return filtered
    except OSError:
        print('error fetching information')

def adapter(widget, view, value, index):
    icon = ImageView(width='20dp', height='20dp', adjustViewBounds=True, top=20, left=20)
    try:
        icon.imageURI=widgets.file_uri(widgets.download_resource(coin_image(value['id'])))
    except OSError:
        print('error fetching image')
    view.append(icon)
    view.append(RelativeLayout(height=20, top=icon.ibottom))
    view[0].left = icon.iright + '20dp'
    view[0].text = format(value['price'], '.2f')
    view[0].vcenter = widget.vcenter
    view[0].textSize=15

def on_create(widget, views):
    del views['refresh_button'].left
    views['refresh_button'].right = 0
    del views['last_update'].right
    views['last_update'].left = 20
    
templates.updating_list('crypto', 
                on_refresh=refresh, 
                adapter=adapter,
                initial_refresh=True, 
                background=True,
                interval=4 * 3600,
                create_hook=on_create)