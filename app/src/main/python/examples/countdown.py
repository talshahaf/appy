import time, datetime
from appy.widgets import register_widget, Chronometer, R
from appy.templates import background
from appy import java

datetime_format = '%Y/%m/%d %H:%M:%S'
    
# Called when changing timepoint
def on_config(widget, views):
    # Update chronometer time reference
    views['timer'].base = convert_time(widget.config.timepoint)

# Chronometer works with SystemClock.elapsedRealtime() which is milliseconds since boot
# t is a string in datetime_format
def convert_time(t):
    python_time_to_chronometer_time = java.clazz.android.os.SystemClock().elapsedRealtime() - (time.time() * 1000)
    
    return (datetime.datetime.strptime(t, datetime_format).timestamp() * 1000) + python_time_to_chronometer_time
    
def create(widget):
    timer = Chronometer(name="timer",
                        countDown=True, base=convert_time(widget.config.timepoint), started=True,
                        textColor=0xb3ffffff, textSize=30,
                        # align view center with widget center
                        vcenter=widget.vcenter, hcenter=widget.hcenter, alignment='center')
    
    # Set up a semi-transparent black rectangle as background
    return [background(drawable=R.drawable.rect), timer]

register_widget('countdown',
                create,
                # Allowing the user to configure the timepoint
                config=dict(timepoint=datetime.datetime.now().strftime(datetime_format)),
                # Listen for timepoint updates
                on_config=on_config)
