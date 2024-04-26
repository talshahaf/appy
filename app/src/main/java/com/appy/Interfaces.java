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

    String onUpdate(int widgetId, String views);

    void onDelete(int widgetId);

    Object[] onItemClick(int widgetId, String views, long collectionId, int position, long id);

    String onClick(int widgetId, String views, long id, boolean checked);

    String onTimer(long timerId, int widgetId, String views, String data);

    String onPost(int widgetId, String views, String data);

    String onConfig(int widgetId, String views, String key);

    void wipeStateRequest();

    void importFile(String path, boolean skipRefresh);

    void deimportFile(String path, boolean skipRefresh);

    void refreshManagers();

    String onError(int widgetId, String error);

    String getStateLayout();

    void cleanState(String scope, String widget, String key);

    int[] findWidgetsByMame(String name);

    String dumpState();

    void syncConfig(String serializedConfig);
}

interface RunnerListener
{
    void onLine(String line);

    void onExited(Integer code);
}

interface StatusListener
{
    void onStartupStatusChange();

    void onPythonFileStatusChange();
}
