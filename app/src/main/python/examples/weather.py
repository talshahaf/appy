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

# see https://api.openweathermap.org/
API_KEY = 'c2771f55d71091fab071cb6af174e5a0'
UNITS = ('metric', 'C')
FORECAST = 'https://api.openweathermap.org/data/2.5/weather?lat={lat:.2f}&lon={lon:.2f}&units={units}&appid={api_key}'
WEATHER_ICONS = 'https://openweathermap.org/img/wn/{code}@2x.png'

def parse_date(datestr):
    return datetime.datetime.strptime(datestr.translate({ord(':'): None, ord('-'): None}), "%Y%m%dT%H%M%SZ").replace(tzinfo=datetime.timezone.utc)

def forecast(lat, lon, units):
    response = json.loads(requests.get(FORECAST.format(lat=lat, lon=lon, units=units, api_key=API_KEY), timeout=60).text)
    return response['name'], response['main']['temp'], response['weather'][0]['icon']

def on_refresh(widget, views):
    try:
        api_units, unit_symbol = UNITS
        lat, lon = widget.config.lat, widget.config.lon
        name, temp, icon_code = forecast(lat=lat, lon=lon, units=api_units)

        views['location'].text = name
        views['temp'].text = f'{temp:.2f}°{unit_symbol}'
        views['img'].imageURI = widgets.file_uri(widgets.download_resource(WEATHER_ICONS.format(code=icon_code)))
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