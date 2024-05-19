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
    DictObj.List onCreate(int widgetId);

    DictObj.List onUpdate(int widgetId, DictObj.List views);

    void onDelete(int widgetId);

    Object[] onItemClick(int widgetId, DictObj.List views, long collectionId, int position, long id);

    DictObj.List onClick(int widgetId, DictObj.List views, long id, boolean checked);

    DictObj.List onTimer(long timerId, int widgetId, DictObj.List views, String data);

    DictObj.List onPost(int widgetId, DictObj.List views, String data);

    DictObj.List onConfig(int widgetId, DictObj.List views, String key);

    void wipeStateRequest();

    void importFile(String path, boolean skipRefresh);

    void deimportFile(String path, boolean skipRefresh);

    void refreshManagers();

    String onError(int widgetId, String error);

    DictObj.Dict getStateLayoutSnapshot();

    void cleanState(String scope, String widget, String key);

    void saveState();

    int[] findWidgetsByMame(String name);

    void syncConfig(DictObj.Dict config);

    void dumpStacktrace(String path);

    String getVersion();
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
