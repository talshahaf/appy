# -*- coding: utf-8 -*-

import requests
from appy.templates import updating_text
from appy.widgets import color

# see https://coinmarketcap.com/api/
API_KEY = '398a40da-e7c2-4e58-bbb3-84b244b8a05e'
BITCOIN_ID = 1
SPECIFIC = 'https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest?id={id}&convert={currency}'

def api_request(url):
    return requests.get(url, timeout=60, headers={'X-CMC_PRO_API_KEY': API_KEY}).json()

def bit_on_refresh(widget):
    print(f'refreshing {widget.config}')
    try:
        # getting config values directly from widget.config each time to allow for quick config updates
        id = BITCOIN_ID
        ratio = float(api_request(SPECIFIC.format(id=id, currency=widget.config.currency))['data'][str(id)]['quote'][widget.config.currency]['price'])
        total = (widget.config.btc_amount * ratio) - widget.config.currency_amount
        # returning int to adapter
        return total
    except OSError:
        print('error fetching information')
    # returning None to indicate the refreshing failed

def bit_adapter(widget, view, value):
    # further customizations depending on value (int)
    view.textColor = color(g=255) if value >= 0 else color(r=255)
    view.text = f'{value:.2f}'

updating_text('bit payoff', 
    config=dict(currency='USD', btc_amount=1, currency_amount=1000), 
    on_refresh=bit_on_refresh,
    # default colored background
    background=True, 
    adapter=bit_adapter,
    # refresh right when widget is created
    initial_refresh=True,
    # refresh every 4 hours
    interval=4 * 3600)
