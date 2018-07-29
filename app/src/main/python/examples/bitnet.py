# -*- coding: utf-8 -*-

import json, requests
from appy.templates import updating_text, updating_list, keyboard
from appy.widgets import register_widget, Button, TextView, call_general_function, color
from appy.java import clazz

#=======================================================================
CURRENCY = 'USD'
BIT_TO_CURRENCY = f'https://api.coinmarketcap.com/v2/ticker/1/?convert={CURRENCY}'

AMOUNT_BTC = 1.0
AMOUNT_CURRENCY = 1000

def bit_on_refresh():
    print('refreshing')
    ratio = float(json.loads(requests.get(BIT_TO_CURRENCY).text)['data']['quotes'][CURRENCY]['price'])
    total = (AMOUNT_BTC * ratio) - AMOUNT_CURRENCY
    return total

def bit_adapter(widget, view, value):
    view.textColor = color(g=255) if value >= 0 else color(r=255)
    view.text = f'{value:.2f}'

updating_text('bit net', on_refresh=bit_on_refresh, background=True, adapter=bit_adapter, initial_refresh=True, interval=4 * 3600)
