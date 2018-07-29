from appy.widgets import GridView, RelativeLayout, Button, TextView, register_widget
from appy import widgets

WIDTH = 400
HEIGHT = 400

def set(widget, views):
    widgets.java_context().setCorrectionFactors(widget.state.ratio[0], widget.state.ratio[1])

def act(widget, views, type=0, value=0):
    widget.state.ratio[type] += value
    views['text'].text = 'x'.join(format(ratio, '.3f') for ratio in widget.state.ratio)
    reported_size = widget.size()
    current_factors = list(widgets.java_context().getCorrectionFactors())
    #WIDTH apparent size with widget.state.ratio after reported_size (reported with current correction factors)
    views['rel'].width = WIDTH + reported_size[0] * (1 - (widget.state.ratio[0] / current_factors[0]))
    views['rel'].height = HEIGHT + reported_size[1] * (1 - (widget.state.ratio[1] / current_factors[1]))

def on_create(widget):
    grid = GridView(children=[RelativeLayout(width=WIDTH, height=HEIGHT, backgroundColor=widgets.color(b=255))])
    rel = RelativeLayout(name='rel', width=WIDTH, height=HEIGHT, backgroundColor=widgets.color(r=255, a=128))
    dec_w = Button(style='secondary_btn', click=(act, dict(type=0, value=0.005)), width=200, height=200, text='-', bottom=0, left=0)
    inc_w = Button(style='secondary_btn', click=(act, dict(type=0, value=-0.005)),  width=200, height=200, text='+', bottom=0, left=dec_w.iright + 10)
    dec_h = Button(style='secondary_btn', click=(act, dict(type=1, value=0.005)), width=200, height=200, text='-', right=0, top=0)
    inc_h = Button(style='secondary_btn', click=(act, dict(type=1, value=-0.005)),  width=200, height=200, text='+', right=0, top=dec_h.ibottom + 10)
    text  = Button(style='secondary_btn', click=set, name='text', right=0, bottom=0)
    widget.state.ratio = list(widgets.java_context().getCorrectionFactors())
    widget.post(act)
    return [grid, rel, dec_w, inc_w, dec_h, inc_h, text]
    
register_widget('correction', on_create, act)