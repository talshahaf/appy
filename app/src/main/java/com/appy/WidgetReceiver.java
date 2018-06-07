package com.appy;

import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by Tal on 25/11/2017.
 */

public class WidgetReceiver extends AppWidgetProvider
{
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("APPY", "onReceive intent: "+intent.getAction());
        Intent serviceIntent = new Intent(context, Widget.class);
        serviceIntent.putExtra(Constants.WIDGET_INTENT, intent);
        context.startService(serviceIntent);
        super.onReceive(context, intent);
    }
}
