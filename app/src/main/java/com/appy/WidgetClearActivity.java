package com.appy;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class WidgetClearActivity extends WidgetSelectActivity
{
    public static final int CONTEXT_MENU_CLEAR = 50;
    public static final int CONTEXT_MENU_RECREATE = 51;

    @Override
    public void onWidgetSelected(int widgetId, String widgetName)
    {
        if (widgetService == null)
        {
            return;
        }

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

    @Override
    public String getToolbarHeader()
    {
        return "Clear Widgets";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setSupportActionBar(toolbar);
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
    }

    @Override
    public boolean onWidgetContextSelected(int itemid, int widgetId, String widgetName)
    {
        if (itemid == CONTEXT_MENU_CLEAR)
        {
            widgetService.clearWidget(widgetId);
            updateWidgetList();
        }
        else if (itemid == CONTEXT_MENU_RECREATE)
        {
            widgetService.recreateWidget(widgetId);
            updateWidgetList();
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu)
    {
        getMenuInflater().inflate(R.menu.widgetclear_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (widgetService == null)
        {
            return super.onOptionsItemSelected(item);
        }

        if (item.getItemId() == R.id.action_clearall)
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
