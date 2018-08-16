# -*- coding: utf-8 -*-

import json, requests
from appy.templates import updating_text, updating_list, keyboard
from appy.widgets import register_widget, Button, TextView, call_general_function, color
from appy.java import clazz

#=======================================================================

BIT_TO_CURRENCY = 'https://api.coinmarketcap.com/v2/ticker/1/?convert={currency}'

def bit_on_refresh(widget):
    print(f'refreshing {widget.config}')
    try:
        ratio = float(json.loads(requests.get(BIT_TO_CURRENCY.format(currency=widget.config.currency)).text)['data']['quotes'][widget.config.currency]['price'])
        total = (widget.config.btc_amount * ratio) - widget.config.currency_amount
        return total
    except OSError:
        print('error fetching information')

def bit_adapter(widget, view, value):
    view.textColor = color(g=255) if value >= 0 else color(r=255)
    view.text = f'{value:.2f}'

updating_text('bit net', config=dict(currency='USD', btc_amount=1, currency_amount=1000), on_refresh=bit_on_refresh, background=True, adapter=bit_adapter, initial_refresh=True, interval=4 * 3600)
