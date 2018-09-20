from appy import java
from appy.widgets import java_context, register_widget, TextView, Widget, Button

WIDGET_NAME = 'user present'

# using java.implements to create a java object implementing BroadcastInterface
class Receiver(java.implements(java.clazz.appy.BroadcastInterface())):   
    @java.override
    def onReceive(*args, **kwargs):
        print('user present')
        # not a widget callback - no widget object.
        # we just need one by name
        widgets = Widget.by_name(WIDGET_NAME)
        if widgets:
            # either one is ok because of the nonlocal scope.
            widgets[0].nonlocals('count')
            widgets[0].state.setdefault('count', 0)
            widgets[0].state.count += 1
        # refresh all
        for widget in widgets:
            widget.post(update_counter)

# wrap Receiver instance with BroadcastInterfaceBridge to pass to registerReceiver because BroadcastReceiver is an abstract class instead of an interface
receiverBridge = java.new.appy.BroadcastInterfaceBridge(Receiver())

# will be called when this module is unloaded
def __del__():
    # registerReceiver is not part of appy and must be cleaned up manually
    java_context().unregisterReceiver(receiverBridge)
    
def register_receiver(intent):
    # get field by name
    intentFilter = java.new.android.content.IntentFilter(getattr(java.clazz.android.content.Intent(), intent))
    java_context().registerReceiver(receiverBridge, intentFilter)
        
def update_counter(widget, views):
    # access count from nonlocal (shared) scope
    widget.nonlocals('count')
    views['counter'].text = str(widget.state.get("count", 0))

def reset(widget, views):
    widget.nonlocals('count')
    widget.state.count = 0
    update_counter(widget, views)
    
def create(widget):
    btn = Button(style='secondary_sml', name='counter', click=reset, textSize=30, hcenter=widget.hcenter, vcenter=widget.vcenter)
    widget.post(update_counter)
    return [TextView(text='User was present', textSize=30, alignment='center', hcenter=widget.hcenter, bottom=btn.itop + 5), 
            TextView(text='times', textSize=30, alignment='center', hcenter=widget.hcenter, top=btn.ibottom + 5), 
            btn]
        
# no need for update
register_widget(WIDGET_NAME, create)
register_receiver('ACTION_USER_PRESENT')