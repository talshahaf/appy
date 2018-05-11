from .widgets import ListView, TextView, ImageButton, RelativeLayout, call_general_function, register_widget
from .java import clazz

def list_adapter(widget, index, value, adapter):
    view = [TextView(textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 15))]
    if adapter is not None:
        call_general_function(adapter, widget=widget, view=view, index=index, value=value)
    else:
        view[0].text = str(value)
    return view

def updating_list_refresh_action(widget, views, on_refresh, adapter):
    values = call_general_function(on_refresh, widget=widget, views=views)
    views[views.index(views['list'])] = ListView(name='list', children=None if values is None else [list_adapter(widget, i, v, adapter) for i, v in enumerate(values)])
    views['refresh_btn'].visibility = clazz.android.view.View().VISIBLE
    print(views)

def updating_list_refresh(widget, views, on_refresh, adapter):
    views['refresh_btn'].visibility = clazz.android.view.View().INVISIBLE
    widget.post(updating_list_refresh_action, on_refresh=on_refresh, adapter=adapter)

def updating_list_create(widget, initial_values, on_refresh, background, adapter, initial_refresh):

    btn = ImageButton(name='refresh_btn', style='dark_btn_oval_nopad', click=(updating_list_refresh, dict(on_refresh=on_refresh, adapter=adapter)), colorFilter=0xffffffff, width=80, height=80, left=0, bottom=0, imageResource=clazz.android.R.drawable().ic_popup_sync)
    lst = ListView(name='list', children=None if initial_values is None else [list_adapter(widget, i, v, adapter) for i, v in enumerate(initial_values)])

    views = [lst, btn]
    if background is not None:
        if isinstance(background, dict):
            color = widget.color(**background)
        elif isinstance(background, (list, tuple)):
            color = widget.color(*background)
        elif isinstance(background, bool):
            #annoyingly, isinstance(True, int) == True
            color = widget.color(r=0, g=0, b=0, a=100)
        elif isinstance(background, int):
            color = background
        else:
            color = widget.color(r=0, g=0, b=0, a=100)

        views.insert(0, RelativeLayout(name='background', width=widget.width, height=widget.height, backgroundColor=color))

    if initial_refresh:
        widget.post(updating_list_refresh, on_refresh=on_refresh, adapter=adapter)
    return views

def updating_list(name, initial_values=None, on_refresh=None, background=None, adapter=None, initial_refresh=None):
    register_widget(name, (updating_list_create, dict(initial_values=initial_values, on_refresh=on_refresh, background=background, adapter=adapter, initial_refresh=initial_refresh)), None)
