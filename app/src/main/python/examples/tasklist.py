from appy.widgets import register_widget, ImageView, ListView, CheckBox, R
from appy.templates import background
from appy import java

strikethrough_flags = java.clazz.android.graphics.Paint().STRIKE_THRU_TEXT_FLAG | java.clazz.android.graphics.Paint().ANTI_ALIAS_FLAG
normal_flags = java.clazz.android.graphics.Paint().ANTI_ALIAS_FLAG

def edit_btn_click(widget):
    # Make the user change 'list' configuration
    widget.request_config_change('list_nojson')
    
def on_check(widget, view, checked):
    # Update checked in state so remember through reboots and list changes
    widget.nonlocals(view.text)
    widget.state[view.text] = checked
    # Strikethrough on checked
    view.paintFlags = strikethrough_flags if checked else normal_flags
    
    #invalidate other tasklists
    for w in widget.by_name(widget.name):
        if w != widget:
            w.invalidate()
 
def update_list(widget, views):
    children = []
    # Parse list configuration and make it CheckBoxes
    for item in widget.config.list_nojson.split('\n' if widget.config.newline_delimiter else widget.config.delimiter):
        text = item.strip()
        if not text:
            continue
        checked = widget.state.get(text, False)
        children.append(CheckBox(text=text, textColor=0xb3ffffff,
                                buttonTintList=java.clazz.android.content.res.ColorStateList().valueOf(0xb3ffffff),
                                textSize=20,
                                paintFlags=strikethrough_flags if checked else normal_flags,
                                checked=checked,
                                click=on_check))
    views['list'].children = children

def create(widget):
    lst = ListView(name='list')
    edit_btn = ImageView(adjustViewBounds=True,
                           colorFilter=0xffffffff, width=80, height=80,
                           right=10, bottom=10,
                           click=edit_btn_click,
                           imageResource=R.drawable.ic_menu_edit)
    
    widget.invalidate()
    return [background(drawable=R.drawable.rect), lst, edit_btn]
    
register_widget('tasklist', create, update_list,
                    # no json so it would be easier to read and to change
                    config=dict(list_nojson='Task 1, Task 2, Task 3', newline_delimiter=False, delimiter=', '),
                    # Refresh list every config change
                    on_config=update_list)
