import random

from widgets import Button, TextView, ListView, register_widget, pip_install, simplydefined, widget_dims
import widgets
from java import clazz
from state import print_state

#=========================================================================

@simplydefined
def void(*args, **kwargs):
    return None

@simplydefined
def example_on_create(widget_id, state):
    txt = TextView(text='zxc', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30), click=lambda e: setattr(e, 'text', str(random.randint(50, 60))))
    lst = ListView(children=[
        txt,
        txt.duplicate()
    ])
    return lst

@simplydefined
def example2_on_create(widget_id, state):
    return [
            Button(text='ref', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30), click=void),
            TextView(text='bbb', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30))
        ]

@simplydefined
def logcat_on_create(widget_id, state):
    print(f'logcat on create {widget_id}')
    return ListView(children=[TextView(text='ready...', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 15), click=void)])

@simplydefined
def logcat_on_update(widget_id, views, state):
    state.locals('i')
    state.setdefault('i', 0)
    state.i += 1
    print_state()
    print(f'logcat on update {widget_id}')
    btn = Button(text='ref', textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 30), click=void)
    lst = ListView(children=[TextView(text=str(random.randint(300, 400)), textViewTextSize=(clazz.android.util.TypedValue().COMPLEX_UNIT_SP, 15), click=void) for _ in range(state.i)])

    btn.top = 400
    lst.top = btn.top / 2

    return [btn, lst]

@simplydefined
def newwidget_on_create(widget_id, state):
    return None

@simplydefined
def newwidget_on_update(widget_id, views, state):
    print(views)

#register_widget('example', example_on_create, None)
#register_widget('example2', example2_on_create, None)
register_widget('logcat', logcat_on_create, logcat_on_update)

#TODO fix bytes serialization
if __name__ == '__main__':
    widgets.init()