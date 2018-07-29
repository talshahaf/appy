from appy import java
from appy.widgets import java_context, register_widget, TextView, Widget

WIDGET_NAME = 'user present'

class Receiver:
    def __init__(self):
        self.iface = java.create_interface(self, java.clazz.com.appy.BroadcastInterface())
        
    @java.interface
    def onReceive(*args, **kwargs):
        print('user present')
        widgets = Widget.by_name(WIDGET_NAME)
        if widgets:
            widgets[0].nonlocals('count')
            widgets[0].state.setdefault('count', 0)
            widgets[0].state.count += 1
        for widget in widgets:
            widget.invalidate()
            
receiver = Receiver()
receiverBridge = java.new.com.appy.BroadcastInterfaceBridge(receiver.iface)
            
def __del__():
    java_context().unregisterReceiver(receiverBridge)
    
def register_receiver(intent):
    intentFilter = java.new.android.content.IntentFilter(getattr(java.clazz.android.content.Intent(), intent))
    java_context().registerReceiver(receiverBridge, intentFilter)
        
def update(widget, views):
    widget.nonlocals('count')
    views['counter'].text = f'User was present\n{widget.state.get("count", 0)}\ntimes'
    
def create(widget):
    widget.invalidate()
    return [TextView(name='counter', textSize=30, alignment='center')]
        
register_widget(WIDGET_NAME, create, update)
register_receiver('ACTION_USER_PRESENT')