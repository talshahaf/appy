from appy.widgets import GridView, RelativeLayout, Button, register_widget
from appy import widgets

WIDTH = 400
HEIGHT = 400

def set(widget, views):
    # java_context() is the appy service exporting setCorrectionFactors for us
    widgets.java_context().setCorrectionFactors(widget.state.ratio[0], widget.state.ratio[1])

# using captures for type and value
def act(widget, views, type=0, value=0):
    widget.state.ratio[type] += value
    views['text'].text = 'x'.join(format(ratio, '.3f') for ratio in widget.state.ratio)
    # reported widget dimensions (assumed wrong by a factor)
    reported_size = widget.size()
    current_factors = list(widgets.java_context().getCorrectionFactors())
    # WIDTH apparent size with widget.state.ratio after reported_size (reported with current correction factors)
    views['rel'].width = WIDTH + reported_size[0] * (1 - (widget.state.ratio[0] / current_factors[0]))
    views['rel'].height = HEIGHT + reported_size[1] * (1 - (widget.state.ratio[1] / current_factors[1]))

def on_create(widget):
    # gridview items are not drawn relative to the widget's dimensions, which gives us a second point of reference to compare to.
    grid = GridView(children=[RelativeLayout(width=WIDTH, height=HEIGHT, backgroundColor=widgets.color(b=255))])
    # RelativeLayout is drawn relative to the widget's dimensions.
    rel = RelativeLayout(name='rel', width=WIDTH, height=HEIGHT, backgroundColor=widgets.color(r=255, a=128))
    # adjustment buttons, using the same act function but with different captures.
    dec_w = Button(style='secondary', click=(act, dict(type=0, value=0.005)), width=200, height=200, text='-', bottom=0, left=0)
    # --->                                                                                                  using inverted attributes when setting e1.left to e2.right
    inc_w = Button(style='secondary', click=(act, dict(type=0, value=-0.005)),  width=200, height=200, text='+', bottom=0, left=dec_w.iright + 10)
    dec_h = Button(style='secondary', click=(act, dict(type=1, value=0.005)), width=200, height=200, text='-', right=0, top=0)
    inc_h = Button(style='secondary', click=(act, dict(type=1, value=-0.005)),  width=200, height=200, text='+', right=0, top=dec_h.ibottom + 10)
    text  = Button(style='secondary', click=set, name='text', right=0, bottom=0)
    # get current correction values to de-correct them (and make this widget agnostic to the current values)
    widget.state.ratio = list(widgets.java_context().getCorrectionFactors())
    # calling act with post so it will be called after returning the element list for initial refresh
    widget.post(act)
    return [grid, rel, dec_w, inc_w, dec_h, inc_h, text]
    
# setting act as the update callback to refresh the correction layout when resizing and changing the correction factors
register_widget('correction', on_create, act)