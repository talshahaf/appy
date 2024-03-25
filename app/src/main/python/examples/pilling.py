import math, os, random
from PIL import Image, ImageDraw, ImageFont
from appy.widgets import register_widget, file_uri, cache_dir, ImageView, Button

# make our png, with the number of sunrays as an argument
def make_image(rays):
    # Image generation parameters
    w,h = 600, 600
    sun_color = (255, 255, 0)
    sun_w, sun_h = 200, 200
    sun_x, sun_y = 0, -100

    ray_start = 130
    ray_end = 200
    text_color_options = ((255, 255, 255), (255, 255, 0), (255, 0, 255), (0, 255, 255), (255, 0, 0), (0, 255, 0), (0, 0, 255))
    text_color = random.choice(text_color_options)
    text_x, text_y = 0, 130
    
    # cache_dir() is the preferred directory for resources (used by ui elements or external apps)
    image_path = os.path.join(cache_dir(), 'happy.png')
    
    # Transparent image
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Sun
    draw.ellipse((w / 2 - sun_w / 2 + sun_x, h / 2 - sun_h / 2 + sun_y, w / 2 + sun_w / 2 + sun_x, h / 2 + sun_h / 2 + sun_y), fill=sun_color)
    
    # Rays
    for i in range(0, rays):
        angle = i * 2*math.pi/rays + math.pi / rays
        draw.line((round(w / 2 + ray_start * math.cos(angle) + sun_x),
                    round(h / 2 + ray_start * math.sin(angle) + sun_y),
                    round(w / 2 + ray_end * math.cos(angle) + sun_x),
                    round(h / 2 + ray_end * math.sin(angle) + sun_y)), 
                  fill=sun_color, width=5)
    
    # Text
    font = ImageFont.truetype('/system/fonts/DroidSans.ttf', 60)
    draw.text((w / 2 + text_x, h / 2 + text_y), 'Appy is great!', fill=text_color, anchor='mm', font=font)
    
    img.save(image_path)
    
    # Using widgets.file_uri to turn the filepath into a usable resource uri
    return file_uri(image_path)

def change_rays(widget, views, amount):
    widget.state.rays += amount
    if widget.state.rays < 0:
        widget.state.rays = 0

    views['image'].imageURI = make_image(widget.state.rays)
 
def create(widget):
    # Start with a reasonable amount of rays
    widget.state.rays = 6
    return [ImageView(name='image', imageURI=make_image(widget.state.rays), adjustViewBounds=True, left=0, top=0, width=widget.width, height=widget.height),
            # Ray buttons anchored to the hcenter
            Button(text='+', click=(change_rays, dict(amount=1)), bottom=10, right=10, left=widget.hcenter + 40),
            Button(text='-', click=(change_rays, dict(amount=-1)), bottom=10, left=10, right=widget.hcenter + 40)]
    
register_widget('pilling', create)
