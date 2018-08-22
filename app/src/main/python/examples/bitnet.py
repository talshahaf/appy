# -*- coding: utf-8 -*-

import json, requests
from appy.templates import updating_text
from appy.widgets import color

BIT_TO_CURRENCY = 'https://api.coinmarketcap.com/v2/ticker/1/?convert={currency}'

def bit_on_refresh(widget):
    print(f'refreshing {widget.config}')
    try:
        # getting config values directly from widget.config each time to allow for quick config updates
        ratio = float(json.loads(requests.get(BIT_TO_CURRENCY.format(currency=widget.config.currency)).text)['data']['quotes'][widget.config.currency]['price'])
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

updating_text('bit net', 
    config=dict(currency='USD', btc_amount=1, currency_amount=1000), 
    on_refresh=bit_on_refresh,
    # default colored background
    background=True, 
    adapter=bit_adapter,
    # refresh right when widget is created
    initial_refresh=True,
    # refresh every 4 hours
    interval=4 * 3600)
