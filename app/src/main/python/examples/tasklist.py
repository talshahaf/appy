from appy.widgets import register_widget, RelativeLayout, ImageButton, ImageView, ListView, CheckBox
from appy.templates import background
from appy import java

strikethrough_flags = java.clazz.android.graphics.Paint().STRIKE_THRU_TEXT_FLAG | java.clazz.android.graphics.Paint().ANTI_ALIAS_FLAG
normal_flags = java.clazz.android.graphics.Paint().ANTI_ALIAS_FLAG

def edit_btn_click(widget):
    # Make the user change 'list' configuration
    widget.request_config_change('list')
    
def on_check(widget, view, checked):
    # Update checked in state so remember through reboots and list changes
    widget.state[view.text] = checked
    # Strikethrough on checked
    view.paintFlags = strikethrough_flags if checked else normal_flags
 
def update_list(widget, views):
    children = []
    # Parse list configuration and make it CheckBoxes
    for item in widget.config.list.split(','):
        text = item.strip()
        children.append(CheckBox(text=text, textColor=0xb3ffffff, 
                                buttonTintList=java.clazz.android.content.res.ColorStateList().valueOf(0xb3ffffff), 
                                textSize=20, 
                                checked=widget.state.get(text, False), 
                                click=on_check))
    views['list'].children = children

def create(widget):
    lst = ListView(name='list')
    edit_btn = ImageView(adjustViewBounds=True,
                           colorFilter=0xffffffff, width=80, height=80,
                           right=10, bottom=10,
                           click=edit_btn_click,
                           imageResource=java.clazz.android.R.drawable().ic_menu_edit)
    
    widget.post(update_list)
    return [background(drawable=java.clazz.appy.R.drawable().rect), lst, edit_btn]
    
register_widget('tasklist', create,
                    config=dict(list='Task 1, Task 2, Task 3'),
                    # Refresh list every config change
                    on_config=update_list)
