package com.appy;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by Tal on 25/11/2017.
 */

public class WidgetReceiver extends AppWidgetProvider
{
    Intent lastIntent;

    @Override
    public void onReceive(Context context, Intent intent) {

        lastIntent = intent;
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {

        Log.d("APPY", "onUpdate");
        Intent serviceIntent = new Intent(context, Widget.class);
        serviceIntent.putExtra(Widget.WIDGET_INTENT, lastIntent);
        context.startService(serviceIntent);
    }
}
