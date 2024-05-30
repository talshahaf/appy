package com.appy;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class WidgetClearActivity extends WidgetSelectActivity
{
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
