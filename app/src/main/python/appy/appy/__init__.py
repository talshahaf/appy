from . import utils, bridge, java, state, widgets, configs
from . import widget_manager #must be after widgets
from .__version__ import __version__
from .widget_manager import register_widget

def do_init():
    state.init()
    widget_manager.init()
