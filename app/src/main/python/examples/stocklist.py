import datetime, json, math, time, random, asyncio
from dateutil.relativedelta import relativedelta
import requests
from requests_futures.sessions import FuturesSession

from appy import templates
from appy.widgets import TextView, color, AttributeValue, Var
from appy import widgets

# Use an reasonable user-agent
user_agent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'

# convert tz aware datetime to utc seconds since epoch
epoch = datetime.datetime.fromtimestamp(0, datetime.UTC)
def gmt_epoch(t):
    return int((t - epoch).total_seconds())
    
async def request_data(symbol, start, end, interval, retries=5):
    starttime = gmt_epoch(start)
    endtime = gmt_epoch(end)
    available_intervals = ['1m', '2m', '5m', '15m', '30m', '60m', '90m', '1h', '4h', '1d', '5d', '1wk', '1mo', '3mo']
    if interval not in available_intervals:
        raise ValueError('unsupported interval')
        
    # build the API query url
    url = f'https://query2.finance.yahoo.com/v8/finance/chart/{symbol.lower()}?period1={starttime}&period2={endtime}&interval={interval}&events=history&includeAdjustedClose=true'
    
    # request with retries
    while retries > 0:
        try:
            # use FuturesSession in requests_futures to request everything in parallel
            # only works for small requests though
            session = FuturesSession()
            resp = await asyncio.wrap_future(session.get(url, headers={'User-Agent': user_agent}))
            if resp.status_code == 200:
                break
        except OSError as e:
            pass
        retries -= 1
        if retries > 0:
            # throw in some non deterministic behaviour
            await asyncio.sleep(random.uniform(1.0, 4.5))
            print(f'Error fetching data, retrying: {retries}')
    else:
        raise ValueError(f'error fetching history: {symbol}')
        
    # json parse ourselves to specify encoding
    data = json.loads(resp.content.decode('utf8'))
    return data['chart']['result'][0]
    
def find_closest_smaller(lst, needle):
    smaller = [(i, n) for i, n in enumerate(lst) if n <= needle]
    if not smaller:
        return None
    return max(smaller, key=lambda x: x[1])[0]
    
def gains(v, ref):
    return 100 * ((v / ref) - 1)
    
async def symbol_data(symbol, adjusted):
    # build datetimes to request
    today = datetime.datetime.now(datetime.UTC).replace(hour=0, minute=0, second=0, microsecond=0)
    weekago = today - relativedelta(days=7)
    monthago = today - relativedelta(months=1)
    threemonthsago = today - relativedelta(months=3)
    jan1ago = today.replace(month=1, day=1)
    yearago = today - relativedelta(years=1)
    
    # use a minimum number of requests (3) and do them concurrently
    data, ytd_data, year_data = await asyncio.gather(request_data(symbol, threemonthsago, today, '1wk'), 
                                                request_data(symbol, jan1ago - relativedelta(days=1), jan1ago, '1d'),
                                                request_data(symbol, yearago, today, '3mo'))

    # find the best data point to use
    jitter = 12 * 3600
    today_ind = find_closest_smaller(data['timestamp'], gmt_epoch(today) + jitter)
    week_ind = find_closest_smaller(data['timestamp'], gmt_epoch(weekago) + jitter)
    month_ind = find_closest_smaller(data['timestamp'], gmt_epoch(monthago) + jitter)
    three_month_ind = find_closest_smaller(data['timestamp'], gmt_epoch(threemonthsago) + jitter)
    year_ind = find_closest_smaller(year_data['timestamp'], gmt_epoch(yearago) + jitter)

    if adjusted:
        close = data['indicators']['adjclose'][0]['adjclose']
        year_close = year_data['indicators']['adjclose'][0]['adjclose']
        ytd = ytd_data['indicators']['adjclose'][0]['adjclose'][0]
    else:
        close =  data['indicators']['quote'][0]['close']
        year_close = year_data['indicators']['quote'][0]['close']
        ytd = ytd_data['indicators']['quote'][0]['close'][0]
    
    current = data['meta']['regularMarketPrice']
    day_open = data['indicators']['quote'][0]['open'][today_ind] if today_ind is not None else current
    week = close[week_ind] if week_ind is not None else day_open
    month = close[month_ind] if month_ind is not None else week
    three_month = close[three_month_ind] if three_month_ind is not None else month
    year = year_close[year_ind] if year_ind is not None else three_month
    
    # return parsed data as a dict
    return {
            'now': current,
            'symbol': symbol,
            'history': {
                    'D': gains(current, day_open),
                    'W': gains(current, week),
                    'M': gains(current, month),
                    '3M': gains(current, three_month),
                    'YTD': gains(current, ytd),
                    'Y': gains(current, year),
                }
            }

async def refresh(widget):
    try:
        return await asyncio.gather(*(symbol_data(symbol, widget.config.adjusted) for symbol in widget.config.symbols))
    except OSError as e:
        print('error fetching information', e)

def adapter(widget, view, value, index):
    # refresh should return a list of values, adapter is called on each one of them.
    # in our case `value` is the dict returned from symbol_data()
    
    # use the original textview to display the current value 
    view[0].text = f'{value['now']:.2f}'
    view[0].hcenter = widget.hcenter
    view[0].vcenter = widget.vcenter
    view[0].textSize = 15
    view[0].textColor = color('white')
    
    # add the symbol name to the left
    view.append(TextView(text=value['symbol'], left=10, textSize=15, vcenter=view[0].vcenter, textColor=color('white')))
    
    # ignore small changes
    epsilon = 1e-4
    values = {k: v if abs(v) > epsilon else 0.0 for k,v in value['history'].items()}
    
    # intialize all texts first, position them later
    texts = [TextView(text=f'{k}\n{v:.2f}', 
                        textColor=color('white') if abs(v) < epsilon else (color(r=255) if v < 0 else color(g=255)), 
                        alignment='center') for k,v in values.items()]
    
    use_template = False
    
    if use_template:
        texts = templates.grid_of(texts, padding_top=5, padding_bottom=5, padding_left=25, padding_right=25, top=view[0].ibottom)
    else:
        # this section mimics what grid_of does above, kept here as an example of using Vars
        
        # position the first text below the current value
        texts[0].top = view[0].ibottom + 10
        
        # set up a system of constraints to have the texts arranged in a grid according to the widget's dimensions
        
        # line height from the first text with some padding
        line_height = texts[0].height + 10
        # calculate the number of texts that fit in a line with some padding between them
        # using Var to save the result and speed up rendering
        max_in_line = Var(AttributeValue.max(1, widget.width // (texts[0].width + 50)))
        # determine total lines
        num_lines = Var((len(texts) / max_in_line).ceil())
        # determine how many texts fit in the last line
        texts_in_last_line = Var((len(texts) % max_in_line).if_((len(texts) % max_in_line) != 0).else_(max_in_line))
        
        # Var are added like views
        view.extend([max_in_line, num_lines, texts_in_last_line])

        # for each text, determine its line and its position in that line
        for i, text in enumerate(texts):
            # all lines except the last should `contain max_in_line` texts
            # the last line contains `texts_in_last_line` texts
            num_thisline = Var(max_in_line.if_((i // max_in_line) != (num_lines - 1)).else_(texts_in_last_line))
            view.append(num_thisline)
            
            # position texts horizontally
            text.hcenter = (((i % num_thisline) + 1) * widget.width / (num_thisline + 1))
            if i != 0:
                # position all texts but the first vertically
                text.top = (texts[0].top + (line_height * (i // max_in_line)))
    
    view.extend(texts)
        
def on_create(widget, views):
    # switch refresh button and last update text places
    del views['refresh_button'].left
    views['refresh_button'].right = 0
    del views['last_update'].right
    views['last_update'].left = 20

templates.updating_list('stocklist',
                config=dict(symbols=['SPY', 'QQQ', 'EUR=X'], adjusted=True),
                config_description=dict(adjusted='whether to adjust currency value'),
                # refresh is a coroutine
                on_refresh=refresh,
                adapter=adapter,
                # refresh when created
                initial_refresh=True, 
                # has background
                background=True,
                # refresh every 4 hours
                interval=24 * 3600,
                # for rearranging the layout
                create_hook=on_create)
