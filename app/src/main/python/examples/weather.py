import json, requests, datetime, io
from appy.widgets import register_widget, ImageView, TextView, AttributeFunction
from appy.templates import background, refresh_button, reset_refresh_buttons_if_needed
from appy import widgets, java

# inaccurate location is enough
PERMISSION = 'ACCESS_COARSE_LOCATION'

# get location using the android providers (currently unused by this widget)
def get_location():
    # request_permissions blocks until user action
    # request_permissions returns a tuple: (granted, denied)
    if PERMISSION not in widgets.request_permissions(PERMISSION)[0]:
        print('no perms')
        return None
    # getLastKnownLocation instead of active location request
    locationManager = widgets.java_context().getSystemService(java.clazz.android.content.Context().LOCATION_SERVICE);
    lastKnownLocation = locationManager.getLastKnownLocation(java.clazz.android.location.LocationManager().NETWORK_PROVIDER);
    if lastKnownLocation == java.Null:
        print('no loc')
        return None
    return dict(lat=float(lastKnownLocation.getLatitude()),
                lon=float(lastKnownLocation.getLongitude()),
                acc=float(lastKnownLocation.getAccuracy()))

FORECAST = 'https://api.open-meteo.com/v1/forecast?latitude={lat:.4f}&longitude={lon:.4f}&current=temperature_2m,is_day,weather_code&forecast_days=1'
WMO_TO_ICON_URL = 'https://gist.github.com/stellasphere/9490c195ed2b53c707087c8c2db4ec0c/raw/76b0cb0ef0bfd8a2ec988aa54e30ecd1b483495d/descriptions.json'

def parse_date(datestr):
    return datetime.datetime.strptime(datestr.translate({ord(':'): None, ord('-'): None}), "%Y%m%dT%H%M%SZ").replace(tzinfo=datetime.timezone.utc)

def wmo_code_to_icon(widget, code, is_day):
    widget.state.nonlocals('wmo_json')
    if 'wmo_json' not in widget.state:
        widget.state.wmo_json = requests.get(WMO_TO_ICON_URL).json()
    
    icon = widget.state.wmo_json.get(str(code), dict(day=None, night=None))['day' if is_day else 'night']
    if icon:
        return icon['description'], icon['image']
    else:
        return '', None
    
def forecast(widget, lat, lon):
    response = json.loads(requests.get(FORECAST.format(lat=lat, lon=lon), timeout=60).text)

    desc, icon = wmo_code_to_icon(widget, response['current']['weather_code'], bool(response['current']['is_day']))
    return desc, response['current']['temperature_2m'], response['current_units']['temperature_2m'], icon

def on_refresh(widget, views):
    try:
        lat, lon = widget.config.lat, widget.config.lon
        desc, temp, temp_units, icon_url = forecast(widget, lat=lat, lon=lon)

        views['location'].text = desc
        views['temp'].text = f'{round(temp)}{temp_units}'
        views['img'].imageURI = widgets.file_uri(widgets.download_resource(icon_url))
    except OSError:
        print('error fetching information')

# Refresh on lat/lon change        
def on_config(widget, views):
    on_refresh(widget, views)
    
def create(widget):
    bg = background()
    refresh = refresh_button(on_refresh, widget=widget, initial_refresh=True, interval=4*3600)
    # moving refresh button
    del refresh.left
    refresh.right = 0
    #                               width and height are 60% of the widget's height but no more than 200 pixels 
    img = ImageView(name='img', width=AttributeFunction.min(200, widget.height * 0.6), height=AttributeFunction.min(200, widget.height * 0.6), adjustViewBounds=True, hcenter=widget.hcenter, top=10)
    temp_text = TextView(name='temp', top=img.ibottom, hcenter=widget.hcenter, textColor=0xb3ffffff, textSize=30)
    location_text = TextView(name='location', top=temp_text.ibottom, hcenter=widget.hcenter, textColor=0xb3ffffff, textSize=20)
    # bg is first
    return [bg, img, temp_text, location_text, refresh]
    
# no custom update callback is needed, just recover refresh button on error,    location is configurable in configurations tab
register_widget('weather', create, reset_refresh_buttons_if_needed, config=dict(lat=32.08, lon=34.78), on_config=on_config)
