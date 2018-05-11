from .widgets import ListView, TextView, ImageButton, RelativeLayout, call_general_function, register_widget
from .java import clazz


##############refresh button##############################
def refresh_button_action(widget, views, on_click, id):
    call_general_function(on_click, widget=widget, views=views)
    views.find_id(id).visibility = clazz.android.view.View().VISIBLE

def refresh_button_click(widget, views, on_click, id):
    print(id, views)
    views.find_id(id).visibility = clazz.android.view.View().INVISIBLE
    widget.post(refresh_button_action, on_click=on_click, id=id)

def refresh_button(on_click, name=None, initial_refresh=None, widget=None):
    btn = ImageButton(style='dark_btn_oval_nopad', colorFilter=0xffffffff, width=80, height=80, left=0, bottom=0, imageResource=clazz.android.R.drawable().ic_popup_sync)
    btn.click = (refresh_button_click, dict(on_click=on_click, id=btn.id))
    print('id', btn.id)
    if name is not None:
        btn.name = name
    if initial_refresh:
        if not widget:
            raise ValueError('must supply widget argument when initial_refresh')
        widget.post(refresh_button_click, on_click=on_click, id=btn.id)
    return btn

##################background####################################
def background(widget, name=None, color=None, drawable=None):
    if isinstance(color, dict):
        color = widget.color(**color)
    elif isinstance(color, (list, tuple)):
        color = widget.color(*color)
    elif isinstance(color, int):
        color = color
    else:
        color = widget.color(r=0, g=0, b=0, a=100)

    if drawable is None:
        drawable = clazz.com.appy.R.drawable().rounded_rect

    bg = RelativeLayout(width=widget.width, height=widget.height, backgroundResource=drawable)
    bg.drawableParameters = (True, (color >> 24) & 0xff, color | 0xff000000, clazz.android.graphics.PorterDuff.Mode().SRC_ATOP, -1)
    if name is not None:
        bg.name = name
    return bg

##############adapter#####################################
def call_adapter(widget, adapter, value, pass_list, base_adapter, name=None, **kwargs):
    view = base_adapter(widget)
    if name is not None:
        view[0].name = name
    if adapter is not None:
        call_general_function(adapter, widget=widget, view=view if pass_list else view[0], value=value, **kwargs)
    else:
        view[0].text = str(value)
    return view

##############list template###############################
def base_list_adapter(widget):
    return [TextView(textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 15))]

def updating_list_refresh_action(widget, views, on_refresh, adapter):
    values = call_general_function(on_refresh, widget=widget, views=views)
    views[views.index(views['list'])] = ListView(name='list', children=None if values is None else [call_adapter(widget, adapter, pass_list=True, base_adapter=base_list_adapter, value=v, index=i) for i, v in enumerate(values)])

def updating_list_create(widget, initial_values, on_refresh, background_param, adapter, initial_refresh):
    btn = refresh_button((updating_list_refresh_action, dict(on_refresh=on_refresh, adapter=adapter)), name='refresh_btn', initial_refresh=initial_refresh, widget=widget)
    lst = ListView(name='list', children=None if initial_values is None else [call_adapter(widget, adapter, value=v, pass_list=True, base_adapter=base_list_adapter, index=i) for i, v in enumerate(initial_values)])

    views = []
    if background_param is not None and background_param is not False:
        views.append(background(widget, name='background', color=None if background_param is True else background_param))

    views.append(lst)
    views.append(btn)
    return views

def updating_list(name, initial_values=None, on_refresh=None, background=None, adapter=None, initial_refresh=None):
    register_widget(name, (updating_list_create, dict(initial_values=initial_values, on_refresh=on_refresh, background_param=background, adapter=adapter, initial_refresh=initial_refresh)), None)

##############text template############################
def base_text_adapter(widget):
    text = TextView(textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30))
    text.left = (widget.width  / 2) - (text.width  / 2)
    text.top  = (widget.height / 2) - (text.height / 2)
    return [text]

def updating_text_refresh_action(widget, views, on_refresh, adapter):
    value = call_general_function(on_refresh, widget=widget, views=views)
    views[views.index(views['content'])] = call_adapter(widget, adapter, value=value, pass_list=False, base_adapter=base_text_adapter, name='content')[0]

def updating_text_create(widget, initial_value, on_refresh, background_param, adapter, initial_refresh):
    if initial_value is not None:
        text = call_adapter(widget, adapter, value=initial_value, pass_list=False, base_adapter=base_text_adapter, name='content')[0]
    else:
        text = TextView(name='content', text='')

    btn = refresh_button((updating_text_refresh_action, dict(on_refresh=on_refresh, adapter=adapter)), name='refresh_btn', initial_refresh=initial_refresh, widget=widget)
    del btn.bottom
    del btn.left
    btn.top = 0
    btn.right = 0

    views = []
    if background_param is not None and background_param is not False:
        views.append(background(widget, name='background', color=None if background_param is True else background_param))

    views.append(text)
    views.append(btn)
    return views

def updating_text(name, initial_value=None, on_refresh=None, background=None, adapter=None, initial_refresh=None):
    register_widget(name, (updating_text_create, dict(initial_value=initial_value, on_refresh=on_refresh, background_param=background, adapter=adapter, initial_refresh=initial_refresh)), None)
