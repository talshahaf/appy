import datetime, json, math, time, random, asyncio
from dateutil.relativedelta import relativedelta
import requests
from requests_futures.sessions import FuturesSession

from appy import templates
from appy.widgets import TextView, color, AttributeValue, Var
from appy import widgets

# Use an reasonable user-agent
user_agent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
main_currency = 'USD'

# convert tz aware datetime to utc seconds since epoch
epoch = datetime.datetime.fromtimestamp(0, datetime.UTC)
def gmt_epoch(t):
    return int((t - epoch).total_seconds())
    
def epoch_gmt(t):
    return epoch + datetime.timedelta(seconds=t)
    
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
    smaller = [(i, n) for i, n in enumerate(lst) if n is not None and n <= needle]
    if not smaller:
        return None
    return max(smaller, key=lambda x: x[1])[0]
    
def find_previous_nonnull(lst, ind):
    if ind is None:
        return None
    while lst[ind] is None:
        if ind == 0:
            return None
        ind -= 1
    return ind

def gains(v, ref):
    return 100 * ((v / ref) - 1)
    
async def symbol_data(symbol, adjusted):
    try:
        jitter = 12 * 3600
        day_jitter = 4
        
        today = datetime.datetime.now(datetime.UTC)
        
        #request last few days first to get last trading day
        day_data = await request_data(symbol, today - relativedelta(days=7 + day_jitter), today, '1d')
        today_ind = find_closest_smaller(day_data.get('timestamp', []), gmt_epoch(today) + jitter)
        if today_ind is not None:
            last_trading_day = epoch_gmt(day_data['timestamp'][today_ind])
        else:
            last_trading_day = today
        # gains are calculated against the close of day before
        day_before_last_trading_day = last_trading_day - relativedelta(days=1)
        
        # build datetimes to request
        weekago = day_before_last_trading_day - relativedelta(days=7)
        monthago = day_before_last_trading_day - relativedelta(months=1)
        threemonthsago = day_before_last_trading_day - relativedelta(months=3)
        jan1ago = today.replace(month=1, day=1)
        yearago = day_before_last_trading_day - relativedelta(years=1)
        
        # use a minimum number of requests (4) and do them concurrently
        month_data, three_months_data, ytd_data, year_data = await asyncio.gather(
            request_data(symbol, monthago - relativedelta(days=day_jitter), monthago, '1d'),
            request_data(symbol, threemonthsago - relativedelta(days=day_jitter), threemonthsago, '1d'),
            request_data(symbol, jan1ago - relativedelta(days=day_jitter), jan1ago, '1d'),
            request_data(symbol, yearago - relativedelta(days=day_jitter), yearago, '1d'),
        )

        # find the best data point to use
        week_ind = find_closest_smaller(day_data.get('timestamp', []), gmt_epoch(weekago) + jitter)
        month_ind = find_closest_smaller(month_data.get('timestamp', []), gmt_epoch(monthago) + jitter)
        three_months_ind = find_closest_smaller(three_months_data.get('timestamp', []), gmt_epoch(threemonthsago) + jitter)
        year_ind = find_closest_smaller(year_data.get('timestamp', []), gmt_epoch(yearago) + jitter)
        
        selector = (lambda d: d['indicators']['adjclose'][0].get('adjclose', [])) if adjusted else (lambda d: d['indicators']['quote'][0].get('close', []))
        
        week_ind = find_previous_nonnull(selector(day_data), week_ind)
        month_ind = find_previous_nonnull(selector(month_data), month_ind)
        three_months_ind = find_previous_nonnull(selector(three_months_data), three_months_ind)
        year_ind = find_previous_nonnull(selector(year_data), year_ind)
        
        current = day_data['meta']['regularMarketPrice']
        last_day_close = current
        
        if today_ind is not None and today_ind > 0:
            prev_day_ind = find_previous_nonnull(selector(day_data), today_ind - 1)
            if prev_day_ind is not None:
                last_day_close = selector(day_data)[prev_day_ind]
        
        week = selector(day_data)[week_ind] if week_ind is not None else last_day_close
        month = selector(month_data)[month_ind] if month_ind is not None else week
        three_months = selector(three_months_data)[three_months_ind] if three_months_ind is not None else month
        year = selector(year_data)[year_ind] if year_ind is not None else three_months
        ytd = [e for e in selector(ytd_data) if e][-1]
        
        currency = day_data['meta']['currency']

        # return parsed data as a dict
        return {
                'now': current,
                'symbol': symbol,
                'currency': currency,
                'history': {
                        'D': gains(current, last_day_close),
                        'W': gains(current, week),
                        'M': gains(current, month),
                        '3M': gains(current, three_months),
                        'YTD': gains(current, ytd),
                        'Y': gains(current, year),
                    }
                }
    except ValueError:
        return dict(symbol=symbol, error=True)

async def refresh(widget):
    try:
        return await asyncio.gather(*(symbol_data(symbol, widget.config.adjusted) for symbol in widget.config.symbols))
    except OSError as e:
        print('error fetching information', e)

def adapter(widget, view, value, index):
    # refresh should return a list of values, adapter is called on each one of them.
    # in our case `value` is the dict returned from symbol_data()
    
    has_error = value.get('error')
    if not has_error:
        # use the original textview to display the current value and maybe the currency as well
        view[0].text = f'{value['now']:.2f}{f' {value['currency']}' if value['currency'] != main_currency else ''}'
    else:
        view[0].text = 'Error'
        
    view[0].update(center=widget.center, textSize=17, textColor=color('white'), alignment='center')
    
    # add the symbol name to the left
    view.append(TextView(text=value['symbol'],
                         left=10, top=view[0].top, bottom=view[0].bottom, lines=1,
                         # invert manually so we can use AttributeValue.min with left and width
                         right=widget.width - AttributeValue.min(view[0].left - 5, widget.width / 4),
                         alignment='center',
                         autoTextSize=True, textColor=color('white')))
    
    if has_error:
        return
        
    # ignore small changes
    epsilon = 1e-4
    values = {k: v if abs(v) > epsilon else 0.0 for k,v in value['history'].items()}
    
    # intialize all texts first, position them later
    texts = [TextView(text=f'{k}\n{v:.2f}', 
                        textColor=color('white') if abs(v) < epsilon else (color(r=255) if v < 0 else color(g=255)),
                        textSize=17,
                        alignment='center',
                        shadowRadius='medium',
                        ) for k,v in values.items()]
    
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
                interval=24 * 3600)
