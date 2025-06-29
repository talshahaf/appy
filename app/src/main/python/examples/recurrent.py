import datetime
from dateutil.parser import parse as dateutil_parse
from dateutil.relativedelta import relativedelta as relativedelta
from appy.widgets import register_widget, show_dialog, DialogEditText, background, Button, TextView, ImageView, color, androidR

def config_dialog(widget, views):
    # show configuration dialog
    # this is done here rather than using the proper configuration mechanism to allow for per widget configuration
    btn, interval, reset_on, not_done_text, done_text = show_dialog('Config', 'Configure recurrent widget', ('Ok', 'Cancel'),
                                                                                   # interval is limited to specific options
                                                                        edittexts=(DialogEditText(widget.state.interval, 'interval', ['minutely', 'hourly', 'daily', 'weekly', 'monthly']),
                                                                                   DialogEditText(widget.state.reset_on, 'Reset on'),
                                                                                   DialogEditText(widget.state.not_done_text, 'Not done text'),
                                                                                   DialogEditText(widget.state.done_text, 'Done text')))
    if btn != 0:
        # don't change configuration if dialog was cancelled
        return
    
    try:
        # validate date (not specific to selected interval, excess date data will be ignored)
        dateutil_parse(reset_on)
    except dateutil.parser.ParserError:
        # leave unchanged if couldn't parse value
        reset_on = widget.state.reset_on
        
    widget.state.interval = interval
    widget.state.reset_on = reset_on
    widget.state.done_text = done_text
    widget.state.not_done_text = not_done_text
    
    # fire the timer handler to deal with the new state
    timer_action(widget, views)

# Called to update UI according to the current state
def update_mark_text(widget, views):
    # change button text and color according to the state
    is_done = int(widget.state.mark is not None)
    views['mark'].text = [widget.state.not_done_text, widget.state.done_text][is_done]
    views['mark'].textColor = [color(r=255), color(g=255)][is_done]
    
    # calculate current streak
    td = relativedelta(datetime.datetime.now(), max(widget.state.missed + [widget.state.created_time]))
    if widget.state.interval == 'hourly':
        streak = td.hours
    elif widget.state.interval == 'daily':
        streak = td.days
    elif widget.state.interval == 'weekly':
        streak = td.days // 7
    elif widget.state.interval == 'monthly':
        streak = td.months
    elif widget.state.interval == 'minutely':
        streak = td.minutes
            
    views['streak'].text = f'Streak: {streak + is_done}'

def mark(widget, views):
    if widget.state.mark is None:
        widget.state.mark = datetime.datetime.now()
        update_mark_text(widget, views)

# Called when the configuration has changed or by a timer
def timer_action(widget, views):
    dt = dateutil_parse(widget.state.reset_on)
    now = datetime.datetime.now()
    
    # build the previous and next reset_on dates according to interval and reset_on
    # additional data in reset_on (for example: month data on a daily interval) will be ignored
    if widget.state.interval == 'hourly':
        combined = now.replace(minute=dt.minute, second=dt.second)
        td = relativedelta(hours=1)
    elif widget.state.interval == 'daily':
        combined = now.replace(hour=dt.hour, minute=dt.minute, second=dt.second)
        td = relativedelta(days=1)
    elif widget.state.interval == 'weekly':
        combined = now.replace(hour=dt.hour, minute=dt.minute, second=dt.second)
        combined = combined + relativedelta(days=(dt.weekday() - combined.weekday()) % 7)
        td = relativedelta(days=7)
    elif widget.state.interval == 'monthly':
        combined = now.replace(day=dt.day, hour=dt.hour, minute=dt.minute, second=dt.second)
        td = relativedelta(months=1)
    elif widget.state.interval == 'minutely':
        combined = now.replace(second=dt.second)
        td = relativedelta(minutes=1)
    
    if combined < now:
        before = combined
        after = combined + td
    else:
        before = combined - td
        after = combined
        
    if widget.state.mark is None:
        if widget.state.mark_reset < before:
            # update missed periods if we didn't mark in time
            widget.state.missed.append(before)
            # notify state.missed change to force flushing
            widget.state.missed = widget.state.missed
    else:
        if widget.state.mark <= before:
            # reset mark if we are in a new period
            widget.state.mark = None
            widget.state.mark_reset = now
    
    # reschedule timer to the next `reset_on` time
    if widget.state.timer_id is not None:
        widget.cancel_timer(widget.state.timer_id)
    widget.state.timer_id = widget.set_absolute_timer(after + datetime.timedelta(seconds=10), timer_action)
    
    # update UI
    update_mark_text(widget, views)

def create(widget):
    #initialize everything to be in local (default) state, to allow for multiple widgets with different configurations
    widget.state.interval = 'daily'
    widget.state.reset_on = '00:00'
    widget.state.done_text = 'Done'
    widget.state.not_done_text = 'Not Done'
    widget.state.mark = None
    widget.state.mark_reset = datetime.datetime.now()
    widget.state.created_time = datetime.datetime.now()
    widget.state.timer_id = None
    widget.state.missed = []
    
    mark_btn = Button(name='mark', textSize=20, click=mark, hcenter=widget.hcenter, vcenter=widget.vcenter)
    streak = TextView(name='streak', textSize=12, textColor=color('white', a=180), hcenter=widget.hcenter, top=mark_btn.ibottom + 5)
    config = ImageView(imageResource=androidR.drawable.ic_menu_preferences, adjustViewBounds=True, colorFilter=color('white'), click=config_dialog, width='20dp', height='20dp', right=20, bottom=20)
    
    # start the timer handler to reschedule itself
    # this is done via widget.post so that it will be called with the `widget` and `views` parameters
    widget.post(timer_action)
    return [background(), mark_btn, streak, config]

register_widget('recurrent', create)
