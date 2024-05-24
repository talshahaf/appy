import mimetypes, os
from appy.widgets import register_widget, file_uri, cache_dir, ImageView, color

def on_share(widget, views, mimetype, data):
    # filter non-images
    if not mimetype.startswith('image/') or len(data) == 0:
        views['image'].imageURI = None
        return
    
    # get a single image
    uri, data = data.popitem()
    
    # save locally
    ext = mimetypes.guess_extension(mimetype) or ''
    image_path = os.path.join(cache_dir(), f'sharing_image{ext}')
    
    with open(image_path, 'wb') as fh:
        fh.write(data)
    
    # point to local file
    views['image'].imageURI = file_uri(image_path)
    
def create(widget):
    # create a single ImageView with a semi-transparent background
    return [ImageView(name='image', imageURI=None, backgroundColor=color('black', a=100), adjustViewBounds=True, left=0, top=0, width=widget.width, height=widget.height)]

# on_share callback will be called with data shared with this widget
register_widget('sharing', create, on_share=on_share)
