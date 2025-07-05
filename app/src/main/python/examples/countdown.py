import time, datetime
import dateutil
from appy.widgets import register_widget, Chronometer, ImageView, R, androidR
from appy.templates import background
from appy import java

datetime_format = '%Y/%m/%d %H:%M:%S'
    
# Called when changing timepoint
def on_config(widget, views):
    # Update chronometer time reference
    views['timer'].base = convert_time(widget.config.timepoint_nojson)

# Chronometer works with SystemClock.elapsedRealtime() which is milliseconds since boot
# t is a datetime string, using dateutil to parse
def convert_time(t):
    python_time_to_chronometer_time = java.clazz.android.os.SystemClock().elapsedRealtime() - (time.time() * 1000)
    
    return (dateutil.parser.parse(t).timestamp() * 1000) + python_time_to_chronometer_time

def edit_btn_click(widget):
    # Make the user change 'timepoint_nojson' configuration
    widget.request_config_change('timepoint_nojson')
    
def create(widget):
    timer = Chronometer(name="timer",
                        countDown=True, base=convert_time(widget.config.timepoint_nojson), started=True,
                        textColor=0xb3ffffff, textSize=30,
                        # align view center with widget center
                        vcenter=widget.vcenter, hcenter=widget.hcenter, alignment='center')
                        
    edit_btn = ImageView(adjustViewBounds=True,
                           colorFilter=0xffffffff, width=80, height=80,
                           right=10, bottom=10,
                           click=edit_btn_click,
                           imageResource=androidR.drawable.ic_menu_edit)
    
    # Set up a semi-transparent black rectangle as background
    return [background(drawable=R.drawable.rect), timer, edit_btn]

register_widget('countdown',
                create,
                # Allowing the user to configure the timepoint
                config=dict(timepoint_nojson=datetime.datetime.now().strftime(datetime_format)),
                # Listen for timepoint updates
                on_config=on_config)
