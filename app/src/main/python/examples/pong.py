import os
from appy.widgets import register_widget, TextView, Button, java_context, file_uri, cache_dir, Widget, request_permissions, androidR
from appy.templates import background
from appy import java

widget_name = 'pong'

deeplink_intent_filter = 'com.appy.DeepLink.HTMLPong.Pong'
notification_intent_filter = 'com.appy.NotificationPong.Pong'

notification_channel_name = 'Pong notification'
notification_channel_description = 'Notification used by the Pong widget'
notification_channel_id = 'pong_notification_id'
notification_id = 200

############# html generating code #############

# deeplinking (https://developer.android.com/training/app-links/deep-linking) allowing for intent sending using html.
# multiline for readability
deeplink_template = '''intent://#Intent;
            action=android.intent.action.VIEW;
            scheme=appy;
            S.action=HTMLPong.Pong;
            S.method=add;
            S.amount=2;
            S.widgetId={widget_id};
            end'''.replace('\n', '').replace('\r', '').replace('\t', '').replace(' ', '')

# cache_dir() is the preferred directory for resources (used by ui elements or external apps)
html_path_template = os.path.join(cache_dir(), 'pong_{widget_id}.html')
html_template = '''<!doctype html>
<html>
<head>
<title>HTML pong</title>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
.counter
{{
    font-size: 80pt;
    margin-left: auto;
    margin-right: auto;
    text-align: center;
}}
.button
{{
    padding: 5px 20px;
    font-size: 30pt;
    border: 1px outset buttonborder;
    border-radius: 3px;
    color: buttontext;
    background-color: buttonface;
    text-decoration: none;
    margin-left: auto;
    margin-right: auto;
    text-align: center;
}}
</style>
</head>
<body>
<div class="counter">{counter}</div>
<div style="text-align: center;"><a class="button" href="{deeplink}" onclick="window.close();">PONG</a></div>
</body>
</html>
'''

def display_page(widget_id, counter):
    # Prepare HTML page and write it to cache dir
    html_path = html_path_template.format(widget_id=widget_id)
    deeplink = deeplink_template.format(widget_id=widget_id)
    with open(html_path, 'w') as fh:
        fh.write(html_template.format(counter=counter, deeplink=deeplink))
    
    intent = java.new.android.content.Intent(java.clazz.android.content.Intent().ACTION_VIEW)
    # Use widgets.file_uri to obtain a publicly accessible link
    intent.setDataAndType(file_uri(html_path), 'text/html')
    intent.setFlags(java.clazz.android.content.Intent().FLAG_ACTIVITY_NEW_TASK | \
                    java.clazz.android.content.Intent().FLAG_ACTIVITY_CLEAR_TASK | \
                    java.clazz.android.content.Intent().FLAG_GRANT_READ_URI_PERMISSION)

    try:
        # Start Chrome using intent
        intent.setClassName("com.android.chrome", "com.google.android.apps.chrome.Main")
        java_context().startActivity(intent)
    except:
        intent.setPackage(None)
        java_context().startActivity(intent)
        
############# notification generating code #############

# Android requires all notifications to have a channel, this channel would appear at App info and can be customized specifically
def create_notification_channel(channel_id, name, description):
    importance = java.clazz.android.app.NotificationManager().IMPORTANCE_DEFAULT
    channel = java.new.android.app.NotificationChannel(channel_id, name, importance)
    channel.setDescription(description)
    notificationManager = java_context().getSystemService(java.clazz.android.content.Context().NOTIFICATION_SERVICE)
    notificationManager.createNotificationChannel(channel)
    
def make_notification(intent_filter, widget_id, title, content, ticker, icon, extras):
    need_channels = java.clazz.android.os.Build.VERSION().SDK_INT >= 26
    
    # Only new androids require using a channel
    if need_channels:
        create_notification_channel(notification_channel_id, notification_channel_name, notification_channel_description)
    
    notificationIntent = java.new.android.content.Intent(intent_filter)
    for key, value in extras.items():
        # All extras are added as strings so that we know which putExtra overload was chosen
        notificationIntent.putExtra(key, str(value))
    
    pending_intent = java.clazz.android.app.PendingIntent().getBroadcast(java_context(),
                            0, notificationIntent,
                            java.clazz.android.app.PendingIntent().FLAG_CANCEL_CURRENT | java.clazz.android.app.PendingIntent().FLAG_IMMUTABLE)
    
    if need_channels:
        builder = java.new.android.app.Notification.Builder(java_context(), notification_channel_id)
    else:
        builder = java.new.android.app.Notification.Builder(java_context())

    builder \
      .setTicker(ticker) \
      .setContentTitle(title) \
      .setContentText(content) \
      .setContentIntent(pending_intent) \
      .setAutoCancel(True) \
      .setDefaults(java.clazz.android.app.Notification().DEFAULT_SOUND | java.clazz.android.app.Notification().DEFAULT_VIBRATE)
    
    # New androids require a notification icon
    builder.setSmallIcon(icon)
        
    notification = builder.build()

    notificationManager = java_context().getSystemService(java.clazz.android.content.Context().NOTIFICATION_SERVICE)
    # Display notification
    notificationManager.notify(notification_id, notification)
        
############# broadcast receivers code #############

# using java.implements to create a java object implementing BroadcastInterface
class PongReceiver(java.implements(java.clazz.appy.BroadcastInterface())):
    @java.override
    def onReceive(self, ctx, intent):
        print('received Pong!')
        
        # Extract extras, using string only so we can have null
        method = intent.getStringExtra('method')
        amount = intent.getStringExtra('amount')
        widget_id = intent.getStringExtra('widgetId')
        
        # Sanity check
        if not widget_id or not amount:
            return
            
        widget_id, amount = int(widget_id), int(amount)
        
        try:
            # Find our specific widget that made the intent
            widget = Widget.by_id(widget_id)
        except KeyError:
            return
            
        if widget.name != widget_name:
            # This shouldn't happen, but we don't want to mess with another widget
            return

        if method == 'add':
            widget.state.counter += amount
            # Using post to notify the widget
            widget.post(update_counter)
            
class NotificationReceiver(java.implements(java.clazz.appy.BroadcastInterface())):
    @java.override
    def onReceive(self, ctx, intent):
        print('apartments notification click')
        
        # Extract extras, using string only so we can have null
        widget_id = intent.getStringExtra('widget_id_extra')
        method = intent.getStringExtra('method_extra')
        amount = intent.getStringExtra('amount_extra')

        # Sanity check
        if not widget_id or not amount:
            return
            
        widget_id, amount = int(widget_id), int(amount)
            
        try:
            # Find our specific widget that made the intent
            widget = Widget.by_id(widget_id)
        except KeyError:
            return
            
        if widget.name != widget_name:
            # This shouldn't happen, but we don't want to mess with another widget
            return
            
        if method == 'add':
            widget.state.counter += amount
            # Using post to notify the widget
            widget.post(update_counter)

# wrap Receiver instance with BroadcastInterfaceBridge to pass to registerReceiver because BroadcastReceiver is an abstract class instead of an interface
pongReceiverBridge = java.new.appy.BroadcastInterfaceBridge(PongReceiver())
notificationReceiverBridge = java.new.appy.BroadcastInterfaceBridge(NotificationReceiver())

# will be called when this module is unloaded
def __del__():
    # registerReceiver is not part of appy and must be cleaned up manually
    java_context().unregisterReceiver(pongReceiverBridge)
    java_context().unregisterReceiver(notificationReceiverBridge)
     
def register_receivers():
    print('registering receivers')
    deeplinkIntentFilter = java.new.android.content.IntentFilter(deeplink_intent_filter)
    notificationIntentFilter = java.new.android.content.IntentFilter(notification_intent_filter)
    
    # Using java_context() to obtain a valid Android context object
    java_context().registerReceiver(pongReceiverBridge, deeplinkIntentFilter, java_context().RECEIVER_EXPORTED)
    java_context().registerReceiver(notificationReceiverBridge, notificationIntentFilter, java_context().RECEIVER_EXPORTED)

############# widget code #############

def update_counter(widget, views):
    views['counter'].text = str(widget.state.counter)
    
def ping_click(widget):
    # Choose whether to open an html page or to display a notification according to config
    if widget.config.method == 'html':
        display_page(widget.widget_id, str(widget.state.counter + 1))
    else:
        notification_permission = 'POST_NOTIFICATIONS'
        if notification_permission not in request_permissions(notification_permission)[0]:
            print('no perms')
            return None
        make_notification(notification_intent_filter, widget.widget_id, 
                          'Click to Pong', str(widget.state.counter + 1),
                          #            because it kinda looks like a paddle
                          'Pong', androidR.drawable.ic_menu_search,
                          dict(widget_id_extra=str(widget.widget_id), method_extra='add', amount_extra='2'))

def create(widget):
    # Align counter text center to widget center
    counter = TextView(name='counter', textSize=30, textColor=0xb3ffffff, alignment='center', bottom=widget.vcenter, hcenter=widget.hcenter)
    # Ping button below it
    ping = Button(text="PING", click=ping_click, top=counter.ibottom + 10, hcenter=widget.hcenter)
    
    widget.state.counter = 0
    widget.post(update_counter)
    return [background(), counter, ping]
    
register_widget(widget_name, create, config=dict(method='notification'), config_description=dict(method="Can be either 'html' or 'notification'"))
register_receivers()
