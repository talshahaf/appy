package com.happy;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.lang.reflect.InvocationTargetException;

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
    String onUpdate(int widgetId, String currentView);
    String onItemClick(int widgetId, String root, int collectionId, int id, int position);
    String onClick(int widgetId, String root, int id);
}

