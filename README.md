# Appy  
Appy is an app that allows you to code android widgets in Python. (3.12)  

- A simple intuitive UI coding library.  
- Full access to java methods and objects.  
- Common python libraries such as: requests, dateutil, numpy, matplotlib  
- pip (for pure python packages)  

Write dashboards and mini apps with few lines of code, allowing you to customize your phone and have important information at a glance.  

This is especially useful when you only need to present some updated information from the web with minimal parsing:  
```py  
import requests  
from appy.templates import updating_text  

def on_refresh():  
    return requests.get('https://www.example.com/api/random-number').json()['number']  

updating_text('info', on_refresh=on_refresh, interval=3600)  
```  

Result:  
![information widget preview](readme-res/random.gif)  

Or when you want to perform a quick action from your homescreen:  
```py  
import requests  
from appy.widgets import register_widget, Button  

def click():  
    requests.post('https://www.example.com/api/turn/on/lights', data={'color': 'red'})  
    
def create():  
    return [Button(text='CLICK ME!', click=click)]  

register_widget('button', create)  
```  

Result:  
![button widget preview](readme-res/btn.gif)  
(Imagine a light turned on somewhere.)  

With full java support:  
```py  
from appy.widgets import register_widget, ListView, TextView, java_context  
from appy.java import clazz  

def create():  
    sensorService = java_context().getSystemService(java_context().SENSOR_SERVICE)  
    sensors = sensorService.getSensorList(clazz.android.hardware.Sensor().TYPE_ALL).toArray()  
    names = [sensor.getName() for sensor in sensors]  
    return [ListView(children=[TextView(text=name) for name in names])]  

register_widget('java', create)  
```  

Result:  
![java widget preview](readme-res/sensors.gif)  


And more:  
![plot widget preview](readme-res/plot.gif) ![pil widget preview](readme-res/pil.gif)
![countdown widget preview](readme-res/count.gif) ![crypto widget preview](readme-res/crypto.gif)  


See [wiki](https://github.com/talshahaf/appy/wiki).  

[Latest APK](https://github.com/talshahaf/appy/releases)  
[Google Play](https://play.google.com/store/apps/details?id=com.appy.widget)  
