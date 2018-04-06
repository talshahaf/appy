import random

from appy.widgets import Button, TextView, ListView, register_widget, widget_dims
from appy.java import clazz
from appy.state import print_state

#=========================================================================

def void(*args, **kwargs):
    return None

def example_on_create(widget):
    txt = TextView(text='zxc', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30), click=lambda e: setattr(e, 'text', str(random.randint(50, 60))))
    lst = ListView(children=[
        txt,
        txt.duplicate()
    ])
    return lst

def example2_on_create(widget):
    return [
        Button(text='ref', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30), click=void),
        TextView(text='bbb', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30))
    ]

def logcat_on_create(widget):
    print(f'logcat on create {widget.widget_id}')
    return ListView(children=[TextView(text='ready...', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 15), click=void)])

def logcat_on_update(widget, views):
    widget.local_token(1)

    widget.state.locals('i')
    widget.state.setdefault('i', 0)
    widget.state.i += 1
    print_state()
    print(f'logcat on update {widget.widget_id}')
    btn = Button(text='ref', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30), click=void)
    lst = ListView(children=[TextView(text=str(random.randint(300, 400)), textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 15), click=void) for _ in range(widget.state.i)])

    btn.top = 400
    lst.top = btn.top / 2

    return [btn, lst]

def inc(widget):
    widget.state.locals('i')
    widget.state.setdefault('i', 0)
    widget.state.i += 1
    print(widget.state.i)
    widget.invalidate()

def timer_on_create(widget):
    widget.state.locals('i')
    widget.state.setdefault('i', 0)
    widget.set_interval(1, inc)
    return TextView(text='ready...', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 15))

def timer_on_update(widget, views):
    widget.local_token(1)
    widget.state.locals('i')
    widget.state.setdefault('i', 0)
    views[0].text = str(widget.state.i)
    return views

#register_widget('example', example_on_create, None)
#register_widget('example2', example2_on_create, None)
#register_widget('logcat', logcat_on_create, logcat_on_update)
register_widget('timer', timer_on_create, timer_on_update)
