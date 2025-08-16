import datetime
from .widgets import ListView, TextView, Button, ImageButton, Switch, CheckBox, RelativeLayout, background, show_dialog, call_general_function, register_widget, elist, R, DialogEditText, AttributeValue, Var
from . import java

##############refresh button##############################
def refresh_button_update_func(widget, views):
    widget.locals('__refresh_error_id')
    if '__refresh_error_id' in widget.state:
        try:
            btn = views.find_id(widget.state.__refresh_error_id)
            btn.visibility = java.clazz.android.view.View().VISIBLE
        except KeyError:
            pass
        del widget.state.__refresh_error_id

def refresh_button_action(widget, views, on_click, id, timer):
    try:
        call_general_function(on_click, widget=widget, views=views, timer=timer)
    except:
        widget.locals('__refresh_error_id')
        widget.state.__refresh_error_id = id
        raise
        
    try:
        btn = views.find_id(id)
        btn.visibility = java.clazz.android.view.View().VISIBLE
        widget.locals('__refresh_error_id')
        del widget.state.__refresh_error_id
    except KeyError:
        pass

def refresh_button_click(widget, views, on_click, id, timer_id=None):
    btn = None
    try:
        btn = views.find_id(id)
    except KeyError:
        #disable timer
        if timer_id is not None:
            widget.cancel_timer(timer_id)
    if btn is not None:
        btn.visibility = java.clazz.android.view.View().INVISIBLE
    widget.post(refresh_button_action, on_click=on_click, id=id, timer=timer_id is not None)
    
def RefreshButton(click, name=None, size=None, initial_refresh=None, widget=None, timeout=None, interval=None):
    try:
        w, h = size
        w, h = int(w), int(h)
    except:
        try:
            w, h = int(size), int(size)
        except:
            w, h = (80, 80)

    btn = ImageButton(style='dark_oval_sml', padding=(10, 10, 10, 10), adjustViewBounds=True, colorFilter=0xffffffff, width=w, height=h, left=0, bottom=0, imageResource=R.drawable.ic_action_refresh)
    btn.click = (refresh_button_click, dict(on_click=click, id=btn.id))
    if name is not None:
        btn.name = name

    if (initial_refresh or interval or timeout) and not widget:
        raise ValueError('must supply widget argument when using initial_refresh, interval or timeout')

    if initial_refresh:
        widget.invoke_click(btn)
    if interval:
        widget.set_interval(interval, widget.click_invoker, element_id=btn.id)
    if timeout:
        widget.set_timeout(timeout, widget.click_invoker, element_id=btn.id)
    return btn

def auto_check_click(widget, views, view, checked, checked_hook, state_name):
    if checked_hook:
        call_general_function(checked_hook, widget=widget, views=views, view=view, checked=checked)
        
    widget.state[state_name] = checked
    
def auto_check(Element, widget, state_name, checked_hook=None, initial_state=False, **kwargs):
    element = Element(**kwargs)
    element.checked = widget.state.get(state_name, initial_state)
    element.click = (auto_check_click, dict(checked_hook=checked_hook, state_name=state_name))
    return element
    
def AutoSwitch(widget, state_name, checked_hook=None, initial_state=False, **kwargs):
    return auto_check(Switch, widget, state_name, checked_hook=checked_hook, initial_state=initial_state, **kwargs)
    
def AutoCheckBox(widget, state_name, checked_hook=None, initial_state=False, **kwargs):
    return auto_check(CheckBox, widget, state_name, checked_hook=checked_hook, initial_state=initial_state, **kwargs)

def editable_click(widget, views, view, title, hint, options, dialog_format_hook, result_format_hook):
    dialog_text = view.text
    if dialog_format_hook:
        dialog_text = call_general_function(dialog_format_hook, text=dialog_text, widget=widget)
        if dialog_text is None:
            return
    
    btn, result_text = show_dialog(title, '', ('Ok', 'Cancel'), edittexts=(DialogEditText(dialog_text, hint, options)))
    if btn == 0:
        if result_format_hook:
            result_text = call_general_function(result_format_hook, text=result_text, widget=widget)
            
        if result_text is not None:
            view.text = result_text
        
def Editable(title='', hint='', options=None, dialog_format_hook=None, result_format_hook=None, **kwargs):
    text = TextView(**kwargs)
    text.click = (editable_click, dict(title=title, hint=hint, options=options, dialog_format_hook=dialog_format_hook, result_format_hook=result_format_hook))
    return text

##############list template###############################
def call_list_adapter(widget, adapter, value, **kwargs):
    view = elist([TextView(textSize=15, textColor=0xb3ffffff)])
    if adapter is not None:
        call_general_function(adapter, widget=widget, view=view, value=value, **kwargs)
    else:
        view[0].text = str(value)
    return view

def updating_list_refresh_action(widget, views, timer, on_refresh, adapter, update_hook):
    values = call_general_function(on_refresh, widget=widget, views=views, timer=timer)
    if values is not None:
        views['list'].children = None if not values else [call_list_adapter(widget, adapter, value=v, index=i) for i, v in enumerate(values)]
        try:
            views['last_update'].text = datetime.datetime.now().strftime('%x %X')
        except KeyError:
            pass
        
    if update_hook is not None:
        call_general_function(update_hook, widget=widget, views=views)

def updating_list_create(widget, initial_values, on_refresh, background_params, adapter, initial_refresh, timeout, interval, last_update, create_hook, update_hook):
    btn = RefreshButton((updating_list_refresh_action, dict(on_refresh=on_refresh, adapter=adapter, update_hook=update_hook)), initial_refresh=initial_refresh, widget=widget, timeout=timeout, interval=interval, name='refresh_button')
    lst = ListView(name='list', top=0, bottom=0, left=0, right=0, children=None if not initial_values else [call_list_adapter(widget, adapter, value=v, index=i) for i, v in enumerate(initial_values)])
    
    views = elist()
    if background_params is True:
        views.append(background())
    elif isinstance(background_params, dict):
        views.append(background(**background_params))

    views.append(lst)
    if last_update:
        last = TextView(name='last_update', textSize=14, textColor=0xb3ffffff, bottom=0, right=20)
        lst.bottom = AttributeValue.max(last.itop.if_(last.itop < 100).else_(0), btn.itop.if_(btn.itop < 100).else_(0))
        views.append(last)
    else:
        lst.bottom = btn.itop.if_(btn.itop < 100).else_(0)
    views.append(btn)
    
    if create_hook is not None:
        call_general_function(create_hook, widget=widget, views=views)
    
    return views
    
def on_config_change(widget, views):
    if 'refresh_button' in views:
        widget.invoke_click(views['refresh_button'])

def updating_list(name, initial_values=None, on_refresh=None, background=True, adapter=None, initial_refresh=None, timeout=None, interval=None, last_update=True, config=None, config_description=None, create_hook=None, update_hook=None, debug=None):
    register_widget(name, (updating_list_create, dict(initial_values=initial_values, on_refresh=on_refresh, background_params=background, adapter=adapter, initial_refresh=initial_refresh, timeout=timeout, interval=interval, last_update=last_update, create_hook=create_hook, update_hook=update_hook)), update=refresh_button_update_func, config=config, config_description=config_description, on_config=on_config_change, debug=debug)

##############text template############################
def call_text_adapter(widget, adapter, value, view, **kwargs):
    if adapter is not None:
        call_general_function(adapter, widget=widget, view=view, value=value, **kwargs)
    else:
        view.text = str(value)

def updating_text_refresh_action(widget, views, timer, on_refresh, adapter, update_hook):
    value = call_general_function(on_refresh, widget=widget, views=views, timer=timer)
    if value is not None:
        call_text_adapter(widget, adapter, value=value, view=views['content'])
        try:
            views['last_update'].text = datetime.datetime.now().strftime('%x %X')
        except KeyError:
            pass
        
    if update_hook is not None:
        call_general_function(update_hook, widget=widget, views=views)

def updating_text_create(widget, initial_value, on_refresh, background_params, adapter, initial_refresh, timeout, interval, last_update, create_hook, update_hook):
    text = TextView(name='content', text='', textSize=30, textColor=0xb3ffffff)
    text.hcenter = widget.hcenter
    text.vcenter  = widget.vcenter
    if initial_value is not None:
        call_text_adapter(widget, adapter, value=initial_value, view=text)

    btn = RefreshButton((updating_text_refresh_action, dict(on_refresh=on_refresh, adapter=adapter, update_hook=update_hook)), initial_refresh=initial_refresh, widget=widget, timeout=timeout, interval=interval, name='refresh_button')

    views = elist()
    if background_params is True:
        views.append(background())
    elif isinstance(background_params, dict):
        views.append(background(**background_params))

    views.append(text)
    if last_update:
        views.append(TextView(name='last_update', textSize=14, textColor=0xb3ffffff, bottom=0, right=20))
    views.append(btn)
    
    if create_hook is not None:
        call_general_function(create_hook, widget=widget, views=views)
        
    return views

def updating_text(name, initial_value=None, on_refresh=None, background=True, adapter=None, initial_refresh=None, timeout=None, interval=None, last_update=True, config=None, config_description=None, create_hook=None, update_hook=None, debug=None):
    register_widget(name, (updating_text_create, dict(initial_value=initial_value, on_refresh=on_refresh, background_params=background, adapter=adapter, initial_refresh=initial_refresh, timeout=timeout, interval=interval, last_update=last_update, create_hook=create_hook, update_hook=update_hook)), refresh_button_update_func, config=config, config_description=config_description, on_config=on_config_change, debug=debug)

def grid_of(elements, orientation='horizontal', alignment='center', padding_top=0, padding_left=0, padding_right=0, padding_bottom=0, min_element_width=None, max_element_width=None, min_element_height=None, max_element_height=None, **grid_attributes):
    allowed = set(['top', 'bottom', 'left', 'right', 'width', 'height', 'hcenter', 'vcenter', 'center'])
    if (allowed | grid_attributes.keys()) != allowed:
        raise ValueError('only layout attributes are supported as `grid_attributes`')

    if orientation == 'vertical':
        if alignment == 'top':
            alignment = 'start'
        elif alignment == 'bottom':
            alignment = 'end'

        if alignment not in ('start', 'end', 'center'):
            raise ValueError('Only start, end, top, bottom and center alignments are supported in vertical orientation')

        primary_start_attr = 'top'
        primary_end_attr = 'bottom'
        primary_size_attr = 'height'
        primary_center_attr = 'vcenter'
        primary_start_padding = padding_top
        primary_end_padding = padding_bottom
        primary_max_element_size = max_element_height
        primary_min_element_size = min_element_height

        secondary_start_attr = 'left'
        secondary_end_attr = 'right'
        secondary_size_attr = 'width'
        secondary_start_padding = padding_left
        secondary_end_padding = padding_right
        secondary_max_element_size = max_element_width
        secondary_min_element_size = min_element_width
    else:
        if alignment == 'left':
            alignment = 'start'
        elif alignment == 'right':
            alignment = 'end'

        if alignment not in ('start', 'end', 'center'):
            raise ValueError('Only start, end, left, right and center alignments are supported in horizontal orientation')

        primary_start_attr = 'left'
        primary_end_attr = 'right'
        primary_size_attr = 'width'
        primary_center_attr = 'hcenter'
        primary_start_padding = padding_left
        primary_end_padding = padding_right
        primary_max_element_size = max_element_width
        primary_min_element_size = min_element_width

        secondary_start_attr = 'top'
        secondary_end_attr = 'bottom'
        secondary_size_attr = 'height'
        secondary_start_padding = padding_top
        secondary_end_padding = padding_bottom
        secondary_max_element_size = max_element_height
        secondary_min_element_size = min_element_height

    INVISIBLE = 4
    # by default, take all available space
    default_grid_attributes = dict(top=0, bottom=0, left=0, right=0)
    if 'width' in grid_attributes:
        del default_grid_attributes['right']
    if 'height' in grid_attributes:
        del default_grid_attributes['bottom']
    default_grid_attributes.update(grid_attributes)
    region = RelativeLayout(**default_grid_attributes, visibility=INVISIBLE)

    primary_element_size = AttributeValue.max(*[getattr(e, primary_size_attr) for e in elements])
    if primary_max_element_size is not None:
        primary_element_size = AttributeValue.min(primary_element_size, primary_max_element_size)
    if primary_min_element_size is not None:
        primary_element_size = AttributeValue.max(primary_element_size, primary_min_element_size)
    primary_element_size = Var(primary_element_size)

      = AttributeValue.max(*[getattr(e, secondary_size_attr) for e in elements])
    if secondary_max_element_size is not None:
        secondary_element_size = AttributeValue.min(secondary_element_size, secondary_max_element_size)
    if secondary_min_element_size is not None:
        secondary_element_size = AttributeValue.max(secondary_element_size, secondary_min_element_size)
    secondary_element_size = Var(secondary_element_size)

    line_size = Var(secondary_element_size + secondary_start_padding + secondary_end_padding)
    max_in_line = Var(AttributeValue.max(1, getattr(region, primary_size_attr) // (primary_element_size + primary_start_padding + primary_end_padding)))
    num_lines = Var((len(elements) / max_in_line).ceil())
    line_remainder = Var(len(elements) % max_in_line)
    elements_in_last_line = Var(line_remainder.if_(line_remainder != 0).else_(max_in_line))

    these_lines = []
    for i, element in enumerate(elements):
        num_thisline = Var(max_in_line.if_((i // max_in_line) != (num_lines - 1)).else_(elements_in_last_line))
        pos_thisline = Var(i % max_in_line)
        these_lines.append(num_thisline)
        these_lines.append(pos_thisline)

        if alignment == 'start':
            setattr(element, primary_start_attr, (primary_element_size + primary_start_padding + primary_end_padding) * pos_thisline + getattr(region, primary_start_attr) + primary_start_padding)
        elif alignment == 'end':
            setattr(element, primary_end_attr, (primary_element_size + primary_start_padding + primary_end_padding) * (num_thisline - 1 - pos_thisline) + getattr(region, primary_end_attr) + primary_end_padding)
        else: # alignment == 'center'
            setattr(element, primary_center_attr, getattr(region, primary_start_attr) + (((i % num_thisline) + 1) * getattr(region, primary_size_attr) / (num_thisline + 1)))

        setattr(element, secondary_start_attr, (getattr(region, secondary_start_attr) + secondary_start_padding + (line_size * (i // max_in_line))))

    return [region, primary_element_size, secondary_element_size, line_size, max_in_line, num_lines, line_remainder, elements_in_last_line, *these_lines, *elements]

#################keyboard###############################
def key_backspace_click(output):
    output.text = output.text[:-1]

def key_click(widget, views, output_id, key=None, handler=None):
    output = views.find_id(output_id)
    if handler is not None:
        call_general_function(handler, widget=widget, output=output)
    else:
        output.text = output.text + key

keyboard_english = ['qwertyuiop',
                    'asdfghjkl',
                    list('zxcvbnm') + [dict(label='←', handler=key_backspace_click)],
                    [dict(label='space', key=' ', width=600, height=80)]]

def keyboard(widget, layout=None):
    if layout is None:
        layout = keyboard_english
    resolved_layout = []
    for line in layout:
        resolved_line = []
        for key_dict in line:
            if isinstance(key_dict, dict):
                key_dict.setdefault('width', 80)
                key_dict.setdefault('height', 80)
            else:
                key_dict = dict(label=key_dict, width=80, height=80)
            resolved_line.append((key_dict, resolved_line[-1][1] + resolved_line[-1][0]['width'] if resolved_line else 0))

        resolved_layout.append((resolved_line, resolved_line[-1][1] + resolved_line[-1][0]['width'], max(x[0]['height'] for x in resolved_line), resolved_layout[-1][3] + resolved_layout[-1][2] if resolved_layout else 0))

    layout_height = resolved_layout[-1][3] + resolved_layout[-1][2]
    btns = []
    edit = TextView(name='output', text='', textSize=30, textColor=0xb3ffffff)
    for resolved_line in resolved_layout:
        btn_line = []
        line, line_width, line_height, top = resolved_line
        for e in line:
            key_dict, left = e
            btn = Button(style='secondary_sml',
                         padding=(0, 0, 0, 0),
                         text=key_dict['label'],
                         width=key_dict['width'],
                         height=key_dict['height'],
                         left=((widget.width - line_width) / 2) + left,
                         top=(widget.height - layout_height) + top,
                         )
            if 'handler' in key_dict:
                btn.click = (key_click, dict(handler=key_dict['handler'], output_id=edit.id))
            else:
                btn.click = (key_click, dict(key=key_dict.get('key', key_dict['label']), output_id=edit.id))

            btn_line.append(btn)
        btns.append(btn_line)

    edit.top = btns[0][0].top - edit.height - 10
    return [edit] + [e for l in btns for e in l]
