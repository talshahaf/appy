import math, os, random
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import axes3d
import numpy as np

from appy.widgets import register_widget, file_uri, cache_dir, ImageView, Button, AttributeFunction

# cool plots taken from matplotlib examples
def lorentz_plot():
    def lorenz(xyz, *, s=10, r=28, b=2.667):
        """
        Parameters
        ----------
        xyz : array-like, shape (3,)
           Point of interest in three-dimensional space.
        s, r, b : float
           Parameters defining the Lorenz attractor.

        Returns
        -------
        xyz_dot : array, shape (3,)
           Values of the Lorenz attractor's partial derivatives at *xyz*.
        """
        x, y, z = xyz
        x_dot = s*(y - x)
        y_dot = r*x - y - x*z
        z_dot = x*y - b*z
        return np.array([x_dot, y_dot, z_dot])


    dt = 0.01
    num_steps = 1000

    xyzs = np.empty((num_steps + 1, 3))  # Need one more for the initial values
    xyzs[0] = (0., 1., 1.05)  # Set initial values
    # Step through "time", calculating the partial derivatives at the current point
    # and using them to estimate the next point
    for i in range(num_steps):
        xyzs[i + 1] = xyzs[i] + lorenz(xyzs[i]) * dt

    # Plot
    fig = plt.figure()
    ax = fig.add_subplot(projection='3d')

    ax.plot(*xyzs.T, lw=0.5)
    ax.set_xlabel("X Axis")
    ax.set_ylabel("Y Axis")
    ax.set_zlabel("Z Axis")
    ax.set_title("Math")
    return fig
    
def predictions_plot():
    plt.style.use('fivethirtyeight')

    x = np.linspace(0, 10)

    # Fixing random state for reproducibility
    np.random.seed(19680801)

    fig, ax = plt.subplots()

    ax.plot(x, np.sin(x) + x + np.random.randn(50))
    ax.plot(x, np.sin(x) + 0.5 * x + np.random.randn(50))
    ax.plot(x, np.sin(x) + 2 * x + np.random.randn(50))
    ax.plot(x, np.sin(x) - 0.5 * x + np.random.randn(50))
    ax.plot(x, np.sin(x) - 2 * x + np.random.randn(50))
    ax.plot(x, np.sin(x) + np.random.randn(50))
    ax.set_title("Predictions")
    return fig
    
def polar_plot():
    # Fixing random state for reproducibility
    np.random.seed(19680801)

    # Compute areas and colors
    N = 150
    r = 2 * np.random.rand(N)
    theta = 2 * np.pi * np.random.rand(N)
    area = 200 * r**2
    colors = theta

    fig = plt.figure()
    ax = fig.add_subplot(projection='polar')
    c = ax.scatter(theta, r, c=colors, s=area, cmap='hsv', alpha=0.75)
    ax.set_title("Colors")
    return fig
    
def surface_plot():
    fig = plt.figure()
    ax = fig.add_subplot(projection='3d')

    X = np.arange(-5, 5, 0.25)
    Y = np.arange(-5, 5, 0.25)
    X, Y = np.meshgrid(X, Y)
    R = np.sqrt(X**2 + Y**2)
    Z = np.sin(R)

    surf = ax.plot_surface(X, Y, Z, rstride=1, cstride=1,
                           linewidth=0, antialiased=False)
    ax.set_zlim(-1, 1)
    ax.set_title('3D')
    return fig
    
def projection_plot():
    fig = plt.figure()
    ax = fig.add_subplot(projection='3d')
    X, Y, Z = axes3d.get_test_data(0.05)

    # Plot the 3D surface
    ax.plot_surface(X, Y, Z, edgecolor='royalblue', lw=0.5, rstride=8, cstride=8,
                    alpha=0.3)

    # Plot projections of the contours for each dimension.  By choosing offsets
    # that match the appropriate axes limits, the projected contours will sit on
    # the 'walls' of the graph
    ax.contourf(X, Y, Z, zdir='z', offset=-100, cmap='coolwarm')
    ax.contourf(X, Y, Z, zdir='x', offset=-40, cmap='coolwarm')
    ax.contourf(X, Y, Z, zdir='y', offset=40, cmap='coolwarm')

    ax.set(xlim=(-40, 40), ylim=(-40, 40), zlim=(-100, 100),
           xlabel='X', ylabel='Y', zlabel='Z')
    ax.set_title('Cool 3D')
    return fig

plots = [lorentz_plot, predictions_plot, polar_plot, surface_plot, projection_plot]

# make our png, with plot index as an argument
def make_image(which):
    # cache_dir() is the preferred directory for resources (used by ui elements or external apps)
    image_path = os.path.join(cache_dir(), 'plt.png')
    
    fig = plots[which]()
    fig.savefig(image_path, transparent=True)
    
    # Using widgets.file_uri to turn the filepath into a usable resource uri
    return file_uri(image_path)

def change_plot(widget, views, amount):
    widget.state.which = (widget.state.which + amount) % len(plots)
    views['image'].imageURI = make_image(widget.state.which)
 
def create(widget):
    btn_prev = Button(text='<', style='dark_sml', click=(change_plot, dict(amount=-1)), bottom=10, left=10)
    btn_next = Button(text='>', style='dark_sml', click=(change_plot, dict(amount=1)), bottom=10, right=10)
    
    # have the buttons grow until they are 40 pixels from the horizontal center, but only up to a maximum width of 100 pixels.
    btn_prev.width = AttributeFunction.min(100, widget.hcenter - 40 - btn_prev.left)
    btn_next.width = AttributeFunction.min(100, widget.hcenter - 40 - btn_next.right)
            
    # Initialize graph index
    widget.state.which = 0
    return [ImageView(name='image', imageURI=make_image(widget.state.which), adjustViewBounds=True, left=0, top=0, width=widget.width, height=widget.height),
            # Buttons anchored to the hcenter
            btn_prev, btn_next]
    
register_widget('plotting', create)
