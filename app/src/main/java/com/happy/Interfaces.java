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
    DynamicView onCreate(int widgetId);
    DynamicView onUpdate(int widgetId, DynamicView currentView);
    DynamicView onItemClick(int widgetId, DynamicView root, int collectionId, int id, int position);
    DynamicView onClick(int widgetId, DynamicView root, int id);
}

