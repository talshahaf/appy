from appy import java
from appy.widgets import java_context, register_widget, TextView, Widget, Button

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
            widget.post(update)
            
receiver = Receiver()
receiverBridge = java.new.com.appy.BroadcastInterfaceBridge(receiver.iface)
            
def __del__():
    java_context().unregisterReceiver(receiverBridge)
    
def register_receiver(intent):
    intentFilter = java.new.android.content.IntentFilter(getattr(java.clazz.android.content.Intent(), intent))
    java_context().registerReceiver(receiverBridge, intentFilter)
        
def update(widget, views):
    widget.nonlocals('count')
    views['counter'].text = str(widget.state.get("count", 0))

def reset(widget, views):
    widget.nonlocals('count')
    widget.state.count = 0
    update(widget, views)
    
def create(widget):
    btn = Button(style='secondary_btn_sml', name='counter', click=reset, textSize=30, hcenter=widget.hcenter, vcenter=widget.vcenter)
    widget.post(update)
    return [TextView(text='User was present', textSize=30, alignment='center', hcenter=widget.hcenter, bottom=btn.itop + 5), 
            TextView(text='times', textSize=30, alignment='center', hcenter=widget.hcenter, top=btn.ibottom + 5), 
            btn]
        
register_widget(WIDGET_NAME, create)
register_receiver('ACTION_USER_PRESENT')