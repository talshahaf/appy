package com.happy;
import java.util.Random;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class Widget extends AppWidgetProvider {

    private static final String ACTION_CLICK = "ACTION_CLICK";
    public static final int TEXT_ID = 0x10101010;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {

        // Get all ids
        ComponentName thisWidget = new ComponentName(context,
                Widget.class);
        int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
        for (int widgetId : allWidgetIds) {
            // create some random data
            int number = (new Random().nextInt(100));

            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            RemoteViews textView1 = new RemoteViews(context.getPackageName(), R.layout.text_layout);
            RemoteViews textView2 = new RemoteViews(context.getPackageName(), R.layout.text_layout);

            // Set the text
            textView1.setTextViewText(R.id.textelement, "wat: "+String.valueOf(number));
            textView2.setTextViewText(R.id.textelement, "sat: "+String.valueOf(number * 5));

            // Register an onClickListener
            Intent intent = new Intent(context, Widget.class);

            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            remoteViews.removeAllViews(R.id.layout);
            remoteViews.addView(R.id.layout, textView1);
            remoteViews.addView(R.id.layout, textView2);

            textView2.setOnClickPendingIntent(R.id.textelement, pendingIntent);

            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }
    }
}
