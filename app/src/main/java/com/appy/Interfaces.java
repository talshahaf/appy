package com.appy;

import android.content.Context;
import android.content.Intent;
import android.os.Message;

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

interface HandlerInterface
{
    void handleMessage(Message msg);
}

interface WidgetUpdateListener
{
    DictObj.Dict onUpdate(int widgetId, DictObj.List views, boolean isApp);

    void onDelete(int widgetId);

    DictObj.Dict onItemClick(int widgetId, DictObj.List views, long collectionId, int position, long id);

    DictObj.Dict onClick(int widgetId, DictObj.List views, long id, boolean checked);

    DictObj.Dict onTimer(long timerId, int widgetId, DictObj.List views, String data);

    DictObj.Dict onPost(int widgetId, DictObj.List views, String data);

    DictObj.Dict onConfig(int widgetId, DictObj.List views, String key);

    DictObj.Dict onShare(int widgetId, DictObj.List views, String mimeType, String text, DictObj.Dict datas);

    void wipeStateRequest();

    void importFile(String path, boolean skipRefresh);

    void deimportFile(String path, boolean skipRefresh);

    void recreateWidget(int widgetId);

    void refreshManagers();

    String onError(int widgetId, String error);

    DictObj.Dict getStateLayoutSnapshot();

    void cleanState(String scope, String widget, String key);

    void saveState();

    int[] findWidgetsByMame(String name);

    DictObj.Dict getAllWidgetNames();

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

interface AppPropsListener
{
    void onAppPropsChange(int widgetId, int androidWidgetId, DictObj.Dict data);
}

interface WidgetChosenListener
{
    void onWidgetChosen(int widgetId, int androidWidgetId, String name);
}
