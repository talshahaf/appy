package com.appy;

import android.content.Context;
import android.content.Intent;

/**
 * Created by Tal on 30/12/2017.
 */

interface TestInterface
{
    Object action(Object i) throws Throwable;
}

interface BroadcastInterface
{
    void onReceive(Context context, Intent intent);
}

interface WidgetUpdateListener
{
    String onCreate(int widgetId);
    String onUpdate(int widgetId, String currentViews);
    void onDelete(int widgetId);
    String onItemClick(int widgetId, String views, int collectionId, int position);
    String onClick(int widgetId, String views, int id);
    void onTimer(int timerId, int widgetId, String data);
}

