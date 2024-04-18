package com.appy;

import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by Tal on 25/11/2017.
 */

public class WidgetReceiverBase extends AppWidgetProvider
{
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("APPY", "onReceive intent: "+intent.getAction());

        if (Widget.isRunning())
        {
            Log.d("APPY", "service is running");

            Intent serviceIntent = new Intent(context, Widget.class);
            serviceIntent.putExtra(Constants.WIDGET_INTENT, intent);
            Widget.startService(context, serviceIntent);
        }
        else
        {
            Log.d("APPY", "service is not running");

            Intent activityIntent = new Intent(context, ForegroundActivity.class);
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            activityIntent.putExtra(Constants.WIDGET_INTENT, intent);
            context.startActivity(activityIntent);
        }
        super.onReceive(context, intent);
    }
}
