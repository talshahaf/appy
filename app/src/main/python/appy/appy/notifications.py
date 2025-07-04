import random
from . import java
from .widget_manager import R, call_general_function, dump_general_function
from .widgets import request_permissions

intent_callables = {}
bridge = None

notification_intent_filter = 'com.appy.utils.simplenotificationfilter'
notification_intent_callable_extra = 'callable'

class PermissionError(RuntimeError):
    pass

class NotificationReceiver(java.implements(java.clazz.appy.BroadcastInterface())):
    @java.override
    def onReceive(self, ctx, intent):
        id = intent.getStringExtra(notification_intent_callable_extra)
        if not id:
            return

        id = int(id)
        if not id:
            return

        if id not in intent_callables:
            return

        call_general_function(intent_callables[id])
        _clean_intent_callables()

def _init():
    global bridge

    if bridge is not None:
        return

    bridge = java.new.appy.BroadcastInterfaceBridge(NotificationReceiver())
    context = java.get_java_arg()
    context.registerReceiver(bridge, java.new.android.content.IntentFilter(notification_intent_filter), context.RECEIVER_EXPORTED)

def _deinit():
    if bridge is None:
        return
    java.get_java_arg().unregisterReceiver(bridge)

def _clean_intent_callables(id=None):
    if id is not None:
        if id in intent_callables:
            return
        ids = [id]
    else:
        ids = list(intent_callables.keys())

    PendingIntent = java.clazz.android.app.PendingIntent()
    for id in ids:
        pending_intent = PendingIntent.getBroadcast(java.get_java_arg(),
                                                        id,
                                                        java.new.android.content.Intent(notification_intent_filter),
                                                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE)
        if not pending_intent:
            del intent_callables[id]

def simple(title, content, channel_name, channel_description, icon=None, click=None, notification_id=None, build_hook=None):
    if notification_id is None:
        notification_id = random.randint(1, 2 ** 30)

    if not isinstance(notification_id, int):
        raise ValueError(f'notification_id must be int or None, not {type(notification_id)}')
    if notification_id == 0:
        raise ValueError('notification_id cannot be 0.')

    context = java.get_java_arg()
    notificationManager = context.getSystemService(java.clazz.android.content.Context().NOTIFICATION_SERVICE)

    need_channels = java.clazz.android.os.Build.VERSION().SDK_INT >= 26
    if need_channels:
        channel = java.new.android.app.NotificationChannel(channel_name,
                                                            channel_name,
                                                            java.clazz.android.app.NotificationManager().IMPORTANCE_DEFAULT)
        channel.setDescription(channel_description)
        notificationManager.createNotificationChannel(channel)

    notificationIntent = java.new.android.content.Intent(notification_intent_filter)
    notificationIntent.putExtra(notification_intent_callable_extra, str(int(notification_id)))

    PendingIntent = java.clazz.android.app.PendingIntent()
    pending_intent = PendingIntent.getBroadcast(context,
                                                    notification_id, notificationIntent,
                                                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE)

    if need_channels:
        builder = java.new.android.app.Notification.Builder(context, channel_name)
    else:
        builder = java.new.android.app.Notification.Builder(context)

    if icon is None:
        #default icon
        icon = R.drawable.circle
    elif isinstance(icon, bytes):
        icon = java.clazz.android.graphics.drawable.Icon().createWithData(java.jbyte[()](icon), 0, len(icon))

    ( builder
      .setContentTitle(title)
      .setContentText(content)
      .setContentIntent(pending_intent)
      .setAutoCancel(True)
      .setSmallIcon(icon)
      .setDefaults(java.clazz.android.app.Notification().DEFAULT_SOUND | java.clazz.android.app.Notification().DEFAULT_VIBRATE)
    )

    if build_hook:
        call_general_function(build_hook, builder=builder)

    notification = builder.build()

    notification_permission = 'POST_NOTIFICATIONS'
    if notification_permission not in request_permissions(notification_permission)[0]:
        raise PermissionError('Notification permission not granted')

    if click is not None:
        #validate
        dump_general_function(click, {})
        intent_callables[int(notification_id)] = click

    # Display notification
    notificationManager.notify(notification_id, notification)
    return notification_id

def cancel(notification_id):
    notificationManager = java.get_java_arg().getSystemService(java.clazz.android.content.Context().NOTIFICATION_SERVICE)
    notificationManager.cancel(notification_id)
    _clean_intent_callables()
