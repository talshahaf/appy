package com.appy;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

public class WidgetManagerActivity extends WidgetSelectActivity
{
    public static final int CONTEXT_MENU_CLEAR = 50;
    public static final int CONTEXT_MENU_RECREATE = 51;
    public static final int CONTEXT_MENU_SIZE_FACTOR = 52;

    private float lastTouchX = Float.NaN;
    private float lastTouchY = Float.NaN;

    @Override
    public void onWidgetSelected(View view, int widgetId, String widgetName)
    {
        if (widgetService == null || view == null)
        {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        {
            listview.showContextMenuForChild(view, !Float.isNaN(lastTouchX) ? lastTouchX - view.getX() : 0, !Float.isNaN(lastTouchY) ? lastTouchY - view.getY() : 0);
        }
        else
        {
            listview.showContextMenuForChild(view);
        }
    }

    @Override
    public String getToolbarHeader()
    {
        return "Manage Widgets";
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        listview.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                if (event.getAction() == MotionEvent.ACTION_DOWN)
                {
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                }
                return false;
            }
        });

        registerForContextMenu(listview);
    }

    @Override
    public boolean hasContextMenu()
    {
        return true;
    }

    @Override
    public void onWidgetCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo, int widgetId, String widgetName)
    {
        menu.add(0, CONTEXT_MENU_CLEAR, 0, "Clear");
        menu.add(0, CONTEXT_MENU_RECREATE, 0, "Recreate");
        menu.add(0, CONTEXT_MENU_SIZE_FACTOR, 0, "Change Size Factor");
    }

    @Override
    public boolean onWidgetContextSelected(int itemid, int widgetId, String widgetName)
    {
        if (itemid == CONTEXT_MENU_CLEAR)
        {
            Utils.showConfirmationDialog(this,
            "Clear widget", "Clear widget #" + widgetId + " (" + widgetName + ")?", android.R.drawable.ic_dialog_alert,
            null, null, new Runnable()
            {
                @Override
                public void run()
                {
                    widgetService.clearWidget(widgetId);
                    updateWidgetList();
                }
            });
        }
        else if (itemid == CONTEXT_MENU_RECREATE)
        {
            widgetService.recreateWidget(widgetId);
            updateWidgetList();
        }
        else if (itemid == CONTEXT_MENU_SIZE_FACTOR)
        {
            Widget.startSizeFactorActivity(this, null, widgetId);
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu)
    {
        getMenuInflater().inflate(R.menu.widgetmanager_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (widgetService == null)
        {
            return super.onOptionsItemSelected(item);
        }

        if (item.getItemId() == R.id.action_recreateall)
        {
            Utils.showConfirmationDialog(this,
                    "Recreate all widgets", "Recreate all widgets?", android.R.drawable.ic_dialog_alert,
                    null, null, new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            widgetService.recreateWidgets();
                        }
                    });
            return true;
        }
        else if (item.getItemId() == R.id.action_clearall)
        {
            Utils.showConfirmationDialog(this,
            "Clear all widgets", "Clear all widgets?", android.R.drawable.ic_dialog_alert,
            null, null, new Runnable()
            {
                @Override
                public void run()
                {
                    widgetService.resetWidgets();
                    finish();
                }
            });
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
