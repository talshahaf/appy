package com.appy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

import android.system.ErrnoException;
import android.system.Os;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarInputStream;

import kotlin.Triple;

public class Widget extends RemoteViewsService
{
    private final IBinder mBinder = new LocalBinder();
    private static boolean mIsRunning = false;

    public static final int PYTHON_VERSION = 31212;
    public static final int NOTIFICATION_ID = 100;

    WidgetUpdateListener updateListener = null;
    StatusListener statusListener = null;
    AppPropsListener appPropsListener = null;
    WidgetChosenListener widgetChosenListener = null;
    Handler handler;
    Constants.StartupState startupState = Constants.StartupState.IDLE;

    final ConcurrentHashMap<Integer, TaskQueue> widgetsTasks = new ConcurrentHashMap<>();

    final Object widgetLock = new Object();
    ConcurrentHashMap<Integer, ArrayList<DynamicView>> widgets = new ConcurrentHashMap<>();

    final Object activeTimersLock = new Object();
    ConcurrentHashMap<Long, Timer> activeTimers = new ConcurrentHashMap<>();
    ConcurrentHashMap<Long, PendingIntent[]> activeTimersIntents = new ConcurrentHashMap<>();

    final Object widgetPropsLock = new Object();
    ConcurrentHashMap<Integer, DictObj.Dict> widgetProps = new ConcurrentHashMap<>();

    final Object widgetAppIconsLock = new Object();
    ConcurrentHashMap<Integer, DictObj.Dict> widgetAppIcons = new ConcurrentHashMap<>();

    final ConcurrentHashMap<Long, ListFactory> factories = new ConcurrentHashMap<>();

    final Object widgetToAndroidLock = new Object();
    ConcurrentHashMap<Integer, Integer> androidToWidget = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer, Integer> widgetToAndroid = new ConcurrentHashMap<>();

    static final Object INVALID_RESULT = new Object();
    final ConcurrentHashMap<Integer, Object> activeRequests = new ConcurrentHashMap<>();

    final ConcurrentHashMap<Integer, Bundle> androidWidgetToOptions = new ConcurrentHashMap<>();

    final Object pythonFilesLock = new Object();
    ArrayList<PythonFile> pythonFiles = new ArrayList<>();
    PythonFile unknownPythonFile = new PythonFile("Unknown file");

    final HashSet<Integer> needUpdateWidgets = new HashSet<>();

    float widthCorrectionFactor = 1.0f;
    float heightCorrectionFactor = 1.0f;
    float globalSizeFactor = 1.0f;
    final Configurations configurations = new Configurations(this, this::configurationUpdate);
    MultipleFileObserverBase pythonFilesObserver = null;

    static class WidgetDestroyedException extends RuntimeException
    {

    }

    static class FunctionCache<Arg, Ret>
    {
        Object[] storage;
        Integer[] storage_hash;

        FunctionCache(int capacity)
        {
            storage = new Object[capacity];
            storage_hash = new Integer[capacity];
        }

        void set(Arg arg, Ret ret)
        {
            int hash = arg.hashCode();
            int mod = hash % storage.length;
            if (mod < 0)
            {
                mod += storage.length;
            }
            storage[mod] = ret;
            storage_hash[mod] = hash;
        }

        Pair<Boolean, Ret> get(Arg arg)
        {
            int hash = arg.hashCode();
            int mod = hash % storage.length;
            if (mod < 0)
            {
                mod += storage.length;
            }
            if (storage_hash[mod] != null && storage_hash[mod] == hash)
            {
                //hit
                return new Pair<>(true, (Ret) storage[mod]);
            }
            else
            {
                //miss
                return new Pair<>(false, null);
            }
        }
    }

    interface Runner<T>
    {
        void run(T... args);
    }

    class AsyncWrapper<T> extends AsyncTask<Object, Void, Void>
    {
        @Override
        protected Void doInBackground(Object... objects)
        {
            try
            {
                ((Runner<T>) objects[1]).run((T[]) objects[2]);
            }
            catch (WidgetDestroyedException e)
            {
                Log.w("APPY", "widget " + (int) objects[0] + " deleted");
            }
            finally
            {
                doneExecuting((int) objects[0]);
            }
            return null;
        }
    }

    class Task<T> implements Runnable
    {
        public Task(Runner<T> torun, T... args)
        {
            this.torun = torun;
            params = args;
        }

        public void execute()
        {
            new AsyncWrapper<T>().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, widgetId, torun, params);
        }

        public void run()
        {
            torun.run(params);
        }

        int widgetId;
        T[] params;
        Runner<T> torun;
    }

    class TaskQueue
    {
        LinkedList<Task> queue = new LinkedList<>();
        boolean executing = false;
    }

    public void addTask(int widgetId, Task<?> task, boolean onlyOnce)
    {
        boolean doDump = false;
        synchronized (widgetsTasks)
        {
            TaskQueue queue = widgetsTasks.get(widgetId);
            if (queue == null)
            {
                queue = new TaskQueue();
                widgetsTasks.put(widgetId, queue);
            }

            task.widgetId = widgetId;

            boolean alreadyHas = false;
            if (onlyOnce)
            {
                for (Task qTask : queue.queue)
                {
                    if (qTask.torun.getClass() == task.torun.getClass())
                    {
                        alreadyHas = true;
                        break;
                    }
                }
            }

            if (!onlyOnce || !alreadyHas)
            {
                queue.queue.add(task);

                if (queue.queue.size() >= Constants.TASK_QUEUE_SUSPICIOUS_SIZE)
                {
                    Log.w("APPY", "addTask: dumping stacktrace due to suspicious queue size: " + queue.queue.size() + " for widget " +widgetId  + ", latest task waiting: " + task.torun.getClass().getSimpleName());
                    doDump = true;
                }
            }
        }

        if (doDump)
        {
            dumpStacktrace();
        }
        executeReadyTasks();
    }

    public void executeReadyTasks()
    {
        ArrayList<Task> toExecute = new ArrayList<>();
        synchronized (widgetsTasks)
        {
            for (Map.Entry<Integer, TaskQueue> queue : widgetsTasks.entrySet())
            {
                if (!queue.getValue().executing && !queue.getValue().queue.isEmpty())
                {
                    toExecute.add(queue.getValue().queue.pop());
                    queue.getValue().executing = true;
                }
            }
        }

        for (Task task : toExecute)
        {
            task.execute();
        }
    }

    public void doneExecuting(int widgetId)
    {
        synchronized (widgetsTasks)
        {
            TaskQueue queue = widgetsTasks.get(widgetId);
            if (queue != null)
            {
                queue.executing = false;
            }
        }
        executeReadyTasks();
    }

    //only supports identity
    public Attributes.AttributeValue attributeParse(String attributeValue)
    {
        attributeValue = attributeValue.replace(" ", "").replace("\r", "").replace("\t", "").replace("\n", "").replace("*", "");

        String[] args = attributeValue.split("\\+");

        double amount = 0;
        ArrayList<Attributes.AttributeValue.Reference> references = new ArrayList<>();
        for (String arg : args)
        {
            if (arg.isEmpty())
            {
                continue;
            }

            Pair<Integer, Attributes.Type> idx = new Pair<>(-1, Attributes.Type.BOTTOM);

            idx = idx.first == -1 ? new Pair<>(arg.indexOf("w"), Attributes.Type.WIDTH) : idx;
            idx = idx.first == -1 ? new Pair<>(arg.indexOf("h"), Attributes.Type.HEIGHT) : idx;
            idx = idx.first == -1 ? new Pair<>(arg.indexOf("t"), Attributes.Type.TOP) : idx;
            idx = idx.first == -1 ? new Pair<>(arg.indexOf("b"), Attributes.Type.BOTTOM) : idx;
            idx = idx.first == -1 ? new Pair<>(arg.indexOf("r"), Attributes.Type.RIGHT) : idx;
            idx = idx.first == -1 ? new Pair<>(arg.indexOf("l"), Attributes.Type.LEFT) : idx;

            if (idx.first == -1)
            {
                amount += Double.parseDouble(arg);
                continue;
            }

            if (arg.charAt(idx.first + 1) != '(')
            {
                throw new IllegalArgumentException("expected '(' in " + arg + " got " + arg.charAt(idx.first + 1));
            }

            int parEnd = arg.indexOf(")", idx.first);
            String refId = arg.substring(idx.first + 2, parEnd);
            Attributes.AttributeValue.Reference reference = new Attributes.AttributeValue.Reference(refId.equalsIgnoreCase("p") ? -1 : Long.parseLong(refId), idx.second, 1);
            reference.factor *= Double.parseDouble(idx.first > 0 ? arg.substring(0, idx.first) : "1");
            reference.factor *= Double.parseDouble(parEnd + 1 < arg.length() ? arg.substring(parEnd + 1) : "1");
            references.add(reference);
        }

        Attributes.AttributeValue ret = new Attributes.AttributeValue();
        ret.arguments.add(new Pair<>(references, amount));
        ret.function = Attributes.AttributeValue.Function.IDENTITY;
        return ret;
    }

    public static boolean isCollection(String type)
    {
        return "ListView".equals(type) ||
                "GridView".equals(type) ||
                "StackView".equals(type) ||
                "AdapterViewFlipper".equals(type);
    }

    public static boolean clickShouldUseViewId(String type)
    {
        return "CheckBox".equals(type) ||
                "Switch".equals(type) ||
                "Button".equals(type) ||
                "ImageButton".equals(type);
    }

    public static boolean isCheckableInsteadOfClickable(String type)
    {
        return "CheckBox".equals(type) ||
                "Switch".equals(type);
    }

    public static boolean allowMethodCallInMeasurement(RemoteMethodCall methodCall)
    {
        // setCompoundButtonChecked triggers animation which we cannot do in our measurement thread
        if (methodCall.getIdentifierIgnorePrefix().equals("setCompoundButtonChecked"))
        {
            return false;
        }
        return true;
    }

    public static Object duckMul(Object source, double b)
    {
        if (source == null)
        {
            return null;
        }

        if (source instanceof Integer)
        {
            return (int)(((Integer)source) * b);
        }
        if (source instanceof Short)
        {
            return (short)(((Short)source) * b);
        }
        if (source instanceof Long)
        {
            return (long)(((Long)source) * b);
        }
        if (source instanceof Float)
        {
            return (float)(((Float)source) * b);
        }
        if (source instanceof Double)
        {
            return ((Double)source) * b;
        }

        throw new RuntimeException("cannot multiply " + source.getClass().getSimpleName());
    }

    // this function wraps the remote method calls to account for the size factor in specific calls
    public static void callRemoteMethodWithSizeFactor(RemoteMethodCall methodCall, double sizeFactor, RemoteViews remoteView, int container_id, int view_id) throws InvocationTargetException, IllegalAccessException
    {
        if (!methodCall.isParentCall())
        {
            if (methodCall.getIdentifierIgnorePrefix().equals("setLineHeight") || methodCall.getIdentifierIgnorePrefix().equals("setTextSize"))
            {
                //single argument factor
                Object originalArg = methodCall.getArgument(1);
                Object factoredArg = duckMul(originalArg, sizeFactor);
                methodCall.setArgument(1, factoredArg);

                methodCall.call(remoteView, view_id);

                //change back
                methodCall.setArgument(1, originalArg);
                return;
            }
            else if (methodCall.getIdentifierIgnorePrefix().equals("setViewPadding"))
            {
                //multiple arguments to factor
                Object[] originalArgs = new Object[4];
                Object[] factoredArgs = new Object[4];
                for (int i = 0; i < originalArgs.length; i++)
                {
                    originalArgs[i] = methodCall.getArgument(i);
                    factoredArgs[i] = duckMul(originalArgs[i], sizeFactor);
                    methodCall.setArgument(i, factoredArgs[i]);
                }

                methodCall.call(remoteView, view_id);

                //change back
                for (int i = 0; i < originalArgs.length; i++)
                {
                    methodCall.setArgument(i, originalArgs[i]);
                }

                return;
            }
        }

        // just call
        methodCall.call(remoteView, methodCall.isParentCall() ? container_id : view_id);
    }

    public static void prepareFactory(Widget service, int widgetId, int xml, int view, DynamicView listview)
    {
        long key = widgetId + ((long)view << 10) + ((long)xml << 36); //good enough

        DynamicView listViewCopy = listview.copy();

        ListFactory factory;
        synchronized (service.factories)
        {
            factory = service.factories.get(key);
            if (factory == null)
            {
                factory = new ListFactory(service, widgetId, listViewCopy);
                service.factories.put(key, factory);
            }
            else
            {
                factory.reload(listViewCopy);
            }
        }
    }

    public ListFactory getFactory(Widget service, int widgetId, int xml, int view)
    {
        long key = widgetId + ((long)view << 10) + ((long)xml << 36); //good enough

        ListFactory factory;
        synchronized (factories)
        {
            factory = factories.get(key);

            if (factory == null)
            {
                Log.w("APPY", "null factory for " + widgetId + " " + xml + " " + view);

                factory = new ListFactory(service, widgetId, null);
                factories.put(key, factory);
            }
        }
        return factory;
    }

    public static class ListFactory implements RemoteViewsFactory
    {
        final Object lock = new Object();
        int widgetId;
        Widget service;
        DynamicView listview = null;
        boolean defaultListView;

        public DynamicView buildDefaultList()
        {
            DynamicView defaultList = new DynamicView("ListView");
            DynamicView defaultElement = new DynamicView("TextView");
            addMethodCall(defaultElement, "setText", "List error");
            addMethodCall(defaultElement, "setTextColor", Constants.TEXT_COLOR);
            ArrayList<DynamicView> child = new ArrayList<>();
            child.add(defaultElement);
            defaultList.children.add(child);
            return defaultList;
        }

        public ListFactory(Widget service, int widgetId, DynamicView listview)
        {
            this.service = service;
            this.widgetId = widgetId;

            if (listview != null)
            {
                reload(listview);
            }
            else
            {
                reload(buildDefaultList());
                defaultListView = true;
            }
        }

        public void reload(DynamicView listview)
        {
            synchronized (lock)
            {
                this.listview = listview;
                defaultListView = false;
            }
        }

        public boolean isDefaultListView()
        {
            return defaultListView;
        }

        @Override
        public void onCreate()
        {

        }

        @Override
        public void onDataSetChanged()
        {

        }

        @Override
        public void onDestroy()
        {

        }

        @Override
        public int getCount()
        {
            synchronized (lock)
            {
                if (listview == null || service.startupState != Constants.StartupState.COMPLETED)
                {
                    return 0;
                }
                return listview.children.size();
            }
        }

        public static RemoteViews inflateChild(Widget service, int widgetId, DynamicView listview, int position, Object[] listViewLock) throws InvocationTargetException, IllegalAccessException
        {
            ArrayList<DynamicView> child;
            String type;
            long id;
            int actualWidth;
            int actualHeight;

            if (listViewLock == null)
            {
                if (position >= listview.children.size())
                {
                    return null;
                }

                child = listview.children.get(position);
                type = listview.type;
                id = listview.getId();
                actualWidth = listview.actualWidth;
                actualHeight = listview.actualHeight;
            }
            else
            {
                synchronized (listViewLock[0])
                {
                    if (position >= listview.children.size())
                    {
                        return null;
                    }

                    child = listview.children.get(position);
                    type = listview.type;
                    id = listview.getId();
                    actualWidth = listview.actualWidth;
                    actualHeight = listview.actualHeight;
                }
            }

            Triple<RemoteViews, HashSet<Integer>, ArrayList<DynamicView>> view = service.resolveDimensions(service, widgetId, child, Constants.collection_layout_type.get(type), new Object[]{id, position}, actualWidth, actualHeight);
            RemoteViews remoteView = view.component1();
            Intent fillIntent = new Intent(service, WidgetReceiver2x2.class);
            if (child.size() == 1)
            {
                fillIntent.putExtra(Constants.ITEM_ID_EXTRA, child.get(0).getId());
            }
            fillIntent.putExtra(Constants.COLLECTION_POSITION_EXTRA, position);

            int elementId = clickShouldUseViewId(child.get(0).type) ? R.id.e0 : R.id.collection_root;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            {
                RemoteViews.RemoteResponse responseIntent = RemoteViews.RemoteResponse.fromFillInIntent(fillIntent);

                if (isCheckableInsteadOfClickable(child.get(0).type))
                {
                    remoteView.setOnCheckedChangeResponse(elementId, responseIntent);
                }
                else
                {
                    remoteView.setOnClickResponse(elementId, responseIntent);
                }
            }
            else
            {
                remoteView.setOnClickFillInIntent(elementId, fillIntent);
            }
            return remoteView;
        }

        @Override
        public RemoteViews getViewAt(int position)
        {
            if (listview == null || service.startupState != Constants.StartupState.COMPLETED)
            {
                return null;
            }

            RemoteViews ret = null;
            try
            {
                ret = inflateChild(service, widgetId, listview, position, new Object[] {lock});
            }
            catch (Exception e)
            {
                Log.e("APPY", "Exception on ListFactory", e);
                try
                {
                    service.setSpecificErrorWidget(service.getAndroidWidget(widgetId), widgetId, e);
                }
                catch (WidgetDestroyedException e2)
                {
                    Log.e("APPY", "Exception on ListFactory", e2);
                }
            }
            return ret != null ? ret : new RemoteViews(service.getPackageName(), R.layout.root);
        }

        @Override
        public RemoteViews getLoadingView()
        {
            return null;
        }

        @Override
        public int getViewTypeCount()
        {
            return getCount() * 3;
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        @Override
        public boolean hasStableIds()
        {
            return false;
        }
    }

    public static float convertUnit(Context context, float value, int from, int to)
    {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        //applyDimension takes the "from" unit
        return value * TypedValue.applyDimension(from, 1.0f, metrics) / TypedValue.applyDimension(to, 1.0f, metrics);
    }

    public float convertUnit(float value, int from, int to)
    {
        return convertUnit(this, value, from, to);
    }

    public int[] getWidgetDimensions(int widgetId)
    {
        return getWidgetDimensions(AppWidgetManager.getInstance(this), getAndroidWidget(widgetId));
    }

    public int[] getWidgetDimensions(AppWidgetManager appWidgetManager, int androidWidgetId)
    {
        Bundle bundle = appWidgetManager.getAppWidgetOptions(androidWidgetId);

        float widthDp = bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        float heightDp = bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);

        float widthPx = convertUnit(widthDp, TypedValue.COMPLEX_UNIT_DIP, TypedValue.COMPLEX_UNIT_PX);
        float heightPx = convertUnit(heightDp, TypedValue.COMPLEX_UNIT_DIP, TypedValue.COMPLEX_UNIT_PX);

        //only works on portrait
        return new int[]{
                (int) (widthCorrectionFactor * widthPx),
                (int) (heightCorrectionFactor * heightPx),
                (int) widthDp,
                (int) heightDp,
        };
    }

    public boolean getWidgetIsApp(AppWidgetManager appWidgetManager, int androidWidgetId)
    {
        Bundle bundle = appWidgetManager.getAppWidgetOptions(androidWidgetId);
        return bundle.getBoolean(AppsFragmentKt.OPTION_APPWIDGET_APPY_APP);
    }

    public static Pair<Integer, HashMap<String, ArrayList<Integer>>> selectRootView(ArrayList<String> collections)
    {
        if (collections.size() > 2)
        {
            throw new IllegalArgumentException("more than 2 collections are not supported");
        }

        if (collections.isEmpty())
        {
            return new Pair<>(R.layout.root, new HashMap<>());
        }

        Collections.sort(collections, String::compareToIgnoreCase);

        Integer res = Constants.collection_map.get(collections);
        if (res == null)
        {
            throw new IllegalArgumentException("collection types not supported");
        }

        HashMap<String, ArrayList<Integer>> ret = new HashMap<>();
        for (int i = 0; i < collections.size(); i++)
        {
            if (!ret.containsKey(collections.get(i)))
            {
                ret.put(collections.get(i), new ArrayList<Integer>());
            }
            ret.get(collections.get(i)).add(i);
        }

        return new Pair<>(res, ret);
    }

    public static Pair<Integer, Integer> getCollectionElementIds(int n)
    {
        switch (n)
        {
            case 0:
                return new Pair<>(R.id.ce0, R.id.cl0);
            case 1:
                return new Pair<>(R.id.ce1, R.id.cl1);
        }
        throw new IllegalArgumentException((n + 1) + " collections are not supported");
    }

    public static Integer advanceElementsId(int elements_id)
    {
        if (elements_id == R.id.elements0)
        {
            return R.id.elements1;
        }
        else if (elements_id == R.id.elements1)
        {
            return R.id.elements2;
        }
        else if (elements_id == R.id.elements2)
        {
            return null;
        }
        else
        {
            return elements_id; //for collection_element_vertical and others
        }
    }

    public static FunctionCache<Pair<String, HashMap<String, String>>, Integer> typeToLayoutCache = new FunctionCache<>(10);

    public static int typeToLayout(String type, HashMap<String, String> selectors)
    {
        if (!Constants.element_map.containsKey(type))
        {
            throw new IllegalArgumentException("unknown type " + type);
        }

        Pair<Boolean, Integer> cache = typeToLayoutCache.get(new Pair<>(type, selectors));
        if (cache.first)
        {
            return cache.second;
        }

        int mostGeneralResource = 0;
        int mostGeneralFit = 0;
        for (Constants.SelectorElement selector : Constants.element_map.get(type))
        {
            int fit = selector.fit(selectors);
            if (fit != 0 && (mostGeneralFit == 0 || fit < mostGeneralFit))
            {
                mostGeneralFit = fit;
                mostGeneralResource = selector.res;
            }
        }
        if (mostGeneralFit == 0)
        {
            throw new IllegalArgumentException("unknown resource for selectors: " + selectors + " and type " + type);
        }

        typeToLayoutCache.set(new Pair<>(type, selectors), mostGeneralResource);
        return mostGeneralResource;
    }

    public static Pair<RemoteViews, HashSet<Integer>> generate(Widget service, int widgetId, ArrayList<DynamicView> dynamicList, boolean forMeasurement, Constants.CollectionLayout collectionLayout, Object[] collectionExtraData, double sizeFactor) throws InvocationTargetException, IllegalAccessException
    {
        boolean inCollection = collectionLayout != Constants.CollectionLayout.NOT_COLLECTION;
        HashSet<Integer> collection_views = new HashSet<>();
        ArrayList<String> collections = new ArrayList<>();
        for (DynamicView layout : dynamicList)
        {
            if (isCollection(layout.type))
            {
                collections.add(layout.type);
            }
        }

        if (collections.size() > 0 && inCollection)
        {
            throw new IllegalArgumentException("cannot have collections in collection");
        }

        Pair<Integer, HashMap<String, ArrayList<Integer>>> root = selectRootView(collections);
        int root_xml = root.first;
        int elements_id = -1;

        if (inCollection)
        {
            elements_id = R.id.collection_elements;
            if (forMeasurement)
            {
                root_xml = R.layout.collection_element_measurements;
            }
            else
            {
                switch (collectionLayout)
                {
                    case VERTICAL:
                    {
                        root_xml = R.layout.collection_element_vertical;
                        break;
                    }
                    case HORIZONTAL:
                    {
                        root_xml = R.layout.collection_element_horizontal;
                        break;
                    }
                    case BOTH:
                    {
                        root_xml = R.layout.collection_element_both;
                        break;
                    }
                    case UNCONSTRAINED:
                    {
                        //can also be collection_element_horizontal but not collection_element_both
                        root_xml = R.layout.collection_element_vertical;
                        break;
                    }
                }
            }
        }

        RemoteViews rootView = new RemoteViews(service.getPackageName(), root_xml);

        if (!inCollection)
        {
            Integer elements_id_it = R.id.elements0;
            while (elements_id_it != null)
            {
                rootView.removeAllViews(elements_id_it);
                elements_id_it = advanceElementsId(elements_id_it);
            }

            elements_id = R.id.elements0;
        }
        else
        {
            rootView.removeAllViews(elements_id);
        }

        for (DynamicView layout : dynamicList)
        {
            RemoteViews remoteView;
            ArrayList<Integer> indices = root.second.get(layout.type);
            if (indices != null)
            {
                if (indices.isEmpty())
                {
                    throw new IllegalArgumentException("out of collection indices");
                }
                int index = indices.remove(0);
                remoteView = rootView;
                layout.xml_id = root_xml;
                Pair<Integer, Integer> ids = getCollectionElementIds(index);
                layout.view_id = ids.first;
                layout.container_id = ids.second;

                Integer elements_id_opt = advanceElementsId(elements_id);
                if (elements_id_opt == null)
                {
                    throw new IllegalArgumentException("out of element containers");
                }
                elements_id = elements_id_opt;
            }
            else
            {
                if (!layout.children.isEmpty())
                {
                    throw new IllegalArgumentException("only collections can have children, not " + layout.type);
                }
                layout.xml_id = typeToLayout(layout.type, layout.selectors);
                layout.container_id = R.id.l0;
                layout.view_id = R.id.e0;
                remoteView = new RemoteViews(service.getPackageName(), layout.xml_id);
            }

            for (RemoteMethodCall methodCall : layout.methodCalls)
            {
//              Log.d("APPY", "calling method "+methodCall.toString());
                if (!forMeasurement || allowMethodCallInMeasurement(methodCall))
                {
                    callRemoteMethodWithSizeFactor(methodCall, sizeFactor, remoteView, layout.container_id, layout.view_id);
                }
            }

            if (forMeasurement)
            {
                remoteView.setCharSequence(layout.view_id, "setContentDescription", layout.getId() + "");
                if (layout.type.equals("Chronometer") || layout.type.equals("TextClock"))
                {
                    remoteView.setCharSequence(layout.view_id, "setHint", layout.getId() + "");
                }
            }

            Intent clickIntent = null;
            if (!forMeasurement)
            {
                clickIntent = new Intent(service, WidgetReceiver2x2.class);
                clickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                clickIntent.putExtra(Constants.WIDGET_ID_EXTRA, widgetId);
            }

            if (isCollection(layout.type))
            {
                collection_views.add(layout.view_id);
                if (!forMeasurement)
                {
                    clickIntent.putExtra(Constants.COLLECTION_ID_EXTRA, layout.getId());

                    int key = widgetId + ((int) (layout.xml_id ^ layout.view_id) << 10);
                    //request code has to be unique at any given time
                    remoteView.setPendingIntentTemplate(layout.view_id, PendingIntent.getBroadcast(service, key, clickIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE));

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    {
                        // This enables proper onclick handing to children as well
                        RemoteViews.RemoteCollectionItems.Builder collection = new RemoteViews.RemoteCollectionItems.Builder();
                        for (int i = 0; i < layout.children.size(); i++)
                        {
                            collection.addItem(i, ListFactory.inflateChild(service, widgetId, layout, i, null));
                        }
                        collection.setHasStableIds(false);

                        remoteView.setRemoteAdapter(layout.view_id, collection.build());
                    }
                    else
                    {
                        prepareFactory(service, widgetId, layout.xml_id, layout.view_id, layout);

                        Intent listintent = new Intent(service, Widget.class);
                        listintent.putExtra(Constants.WIDGET_ID_EXTRA, widgetId);
                        listintent.putExtra(Constants.XML_ID_EXTRA, layout.xml_id);
                        listintent.putExtra(Constants.VIEW_ID_EXTRA, layout.view_id);
                        listintent.setData(Uri.parse(listintent.toUri(Intent.URI_INTENT_SCHEME)));
                        remoteView.setRemoteAdapter(layout.view_id, listintent);
                    }
                }
            }
            else
            {
                if (!forMeasurement)
                {
                    // Should we check this?
                    // Doing this gives a warning in logcat, but not doing this might cause some clicks to not register
                    //if (!inCollection)
                    {
                        clickIntent.putExtra(Constants.ITEM_ID_EXTRA, layout.getId());
                        if (layout.tag instanceof Integer)
                        {
                            clickIntent.putExtra(Constants.ITEM_TAG_EXTRA, (Integer) layout.tag);
                        }
                        else if (layout.tag instanceof Long)
                        {
                            clickIntent.putExtra(Constants.ITEM_TAG_EXTRA, (Long) layout.tag);
                        }
                        else if (layout.tag instanceof String)
                        {
                            clickIntent.putExtra(Constants.ITEM_TAG_EXTRA, (String) layout.tag);
                        }

                        if (inCollection)
                        {
                            clickIntent.putExtra(Constants.COLLECTION_ID_EXTRA, (long) collectionExtraData[0]);
                            clickIntent.putExtra(Constants.COLLECTION_POSITION_EXTRA, (int) collectionExtraData[1]);
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        {
                            RemoteViews.RemoteResponse response;
                            if (inCollection)
                            {
                                response = RemoteViews.RemoteResponse.fromFillInIntent(clickIntent);
                            }
                            else
                            {
                                PendingIntent pendingIntent = PendingIntent.getBroadcast(service, widgetId + ((int) layout.getId() << 10), clickIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
                                response = RemoteViews.RemoteResponse.fromPendingIntent(pendingIntent);
                            }

                            if (isCheckableInsteadOfClickable(layout.type) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            {
                                remoteView.setOnCheckedChangeResponse(layout.view_id, response);
                            }
                            else
                            {
                                remoteView.setOnClickResponse(layout.view_id, response);
                            }
                        }
                        else
                        {
                            PendingIntent pendingIntent = PendingIntent.getBroadcast(service, widgetId + ((int) layout.getId() << 10), clickIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
                            remoteView.setOnClickPendingIntent(layout.view_id, pendingIntent);
                        }
                    }
                }
            }

            if (remoteView != rootView)
            {
                rootView.addView(elements_id, remoteView);
            }
        }

        return new Pair<>(rootView, collection_views);
    }

    //only one level
    public DynamicView find(ArrayList<DynamicView> dynamicList, long id)
    {
        for (DynamicView view : dynamicList)
        {
            if (view.getId() == id)
            {
                return view;
            }
        }
        return null;
    }

    public double applyFunction(Attributes.AttributeValue.Function function, ArrayList<Double> arguments)
    {
        switch (function)
        {
            case IDENTITY:
                if (arguments.size() != 1)
                {
                    throw new IllegalArgumentException("tried to apply identity with " + arguments.size() + " arguments, probably a misuse");
                }
                return arguments.get(0);
            case MAX:
                return Collections.max(arguments);
            case MIN:
                return Collections.min(arguments);
        }
        throw new IllegalArgumentException("unknown function " + function);
    }

    public void resolveAxis(DynamicView view, Attributes.Type startType, Attributes.Type sizeType, Attributes.Type endType, double defaultStart, double defaultSize, double sizeFactor)
    {
        Attributes.AttributeValue start = view.attributes.attributes.get(startType);
        Attributes.AttributeValue size = view.attributes.attributes.get(sizeType);
        Attributes.AttributeValue end = view.attributes.attributes.get(endType);

        //only fudge if set
        if (size.hasConstraints())
        {
            size.finalFactor = sizeFactor;
        }

        Attributes.AttributeValue third;
        Attributes.Type[] others = new Attributes.Type[2];

        //if 0 constrained
        if (!start.hasConstraints() &&
                !size.hasConstraints() &&
                !end.hasConstraints())
        {
            //no constaints at all, set left and width to measured,
            start.resolvedValue = defaultStart;
            size.resolvedValue = defaultSize;

            third = end;
            others[0] = startType;
            others[1] = sizeType;
        }
        //if 3 constrained
        else if (start.hasConstraints() &&
                size.hasConstraints() &&
                end.hasConstraints())
        {
            // To avoid an issue where ignoring one constraint would cause a circular reference, throw an error instead.
            throw new RuntimeException("Too many constraints on the same axis: " + startType.toString().toLowerCase() + ", " + sizeType.toString().toLowerCase() + ", " + endType.toString().toLowerCase());
//            //ignore right
//            third = end;
//            others[0] = startType;
//            others[1] = sizeType;
        }
        //if 1 constrained
        else if (start.hasConstraints() &&
                !size.hasConstraints() &&
                !end.hasConstraints())
        {
            //if only left, set width
            size.resolvedValue = defaultSize;

            third = end;
            others[0] = startType;
            others[1] = sizeType;
        }
        else if (!start.hasConstraints() &&
                size.hasConstraints() &&
                !end.hasConstraints())
        {
            //if only width, set left
            start.resolvedValue = defaultStart;

            third = end;
            others[0] = startType;
            others[1] = sizeType;
        }
        else if (!start.hasConstraints() &&
                !size.hasConstraints() &&
                end.hasConstraints())
        {
            //if only right, set width
            size.resolvedValue = defaultSize;

            third = start;
            others[0] = endType;
            others[1] = sizeType;
        }
        //if 2 constrained
        else if (!start.hasConstraints() &&
                size.hasConstraints() &&
                end.hasConstraints())
        {
            third = start;
            others[0] = endType;
            others[1] = sizeType;
        }
        else if (start.hasConstraints() &&
                !size.hasConstraints() &&
                end.hasConstraints())
        {
            third = size;
            others[0] = startType;
            others[1] = endType;
        }
        else if (start.hasConstraints() &&
                size.hasConstraints() &&
                !end.hasConstraints())
        {
            third = end;
            others[0] = startType;
            others[1] = sizeType;
        }
        else
        {
            throw new IllegalStateException("shouldn't happen");
        }

        //here we have 2 out of 3 set, create the reference for the third: third = widget.size - others
        third.function = Attributes.AttributeValue.Function.IDENTITY;
        third.arguments = new ArrayList<>();
        ArrayList<Attributes.AttributeValue.Reference> refs = new ArrayList<>();
        refs.add(new Attributes.AttributeValue.Reference(-1, sizeType, 1));
        refs.add(new Attributes.AttributeValue.Reference(view.getId(), others[0], -1));
        refs.add(new Attributes.AttributeValue.Reference(view.getId(), others[1], -1));
        third.arguments.add(new Pair<>(refs, (double) 0));
    }

    public int applyIteration(ArrayList<DynamicView> dynamicList, Attributes rootAttributes)
    {
        int resolved = 0;
        for (DynamicView dynamicView : dynamicList)
        {
            for (Attributes.AttributeValue attributeValue : dynamicView.attributes.unresolved().values())
            {
                boolean canBeResolved = true;

                ArrayList<Double> results = new ArrayList<>();
                for (Pair<ArrayList<Attributes.AttributeValue.Reference>, Double> argument : attributeValue.arguments)
                {
                    double result = argument.second;
                    for (Attributes.AttributeValue.Reference reference : argument.first)
                    {
                        Attributes referenceAttributes;
                        if (reference.id != -1)
                        {
                            referenceAttributes = find(dynamicList, reference.id).attributes;
                        }
                        else
                        {
                            referenceAttributes = rootAttributes;
                        }

                        if (!referenceAttributes.attributes.get(reference.type).isResolved())
                        {
                            canBeResolved = false;
                        }
                        else
                        {
                            result += referenceAttributes.attributes.get(reference.type).resolvedValue * reference.factor;
                        }
                    }
                    results.add(result);
                }

                if (canBeResolved)
                {
                    attributeValue.resolvedValue = applyFunction(attributeValue.function, results) * attributeValue.finalFactor;
                    resolved++;
                }
            }
        }
        return resolved;
    }

    public double getMaxDimension(ArrayList<DynamicView> dynamicList, Attributes.Type[] dimensionAttributes)
    {
        double max = 0;
        for (DynamicView dynamicView : dynamicList)
        {
            double length = 0;
            for (Attributes.Type type : dimensionAttributes)
            {
                Attributes.AttributeValue attr = dynamicView.attributes.attributes.get(type);
                if (attr.isResolved())
                {
                    length += attr.resolvedValue;
                }
            }

            if (max < length)
            {
                max = length;
            }
        }
        return max;
    }

    public Triple<RemoteViews, HashSet<Integer>, ArrayList<DynamicView>> resolveDimensions(Widget service, int widgetId, ArrayList<DynamicView> dynamicList, Constants.CollectionLayout collectionLayout, Object[] collectionExtras, int widthLimit, int heightLimit) throws InvocationTargetException, IllegalAccessException
    {
        //we must copy as we're changing the views
        dynamicList = DynamicView.copyArray(dynamicList);

        Float widgetSizeFactor = getWidgetSizeFactor(widgetId);
        float sizeFactor = widgetSizeFactor == null ? globalSizeFactor : widgetSizeFactor;

        RemoteViews remote = generate(service, widgetId, dynamicList, true, collectionLayout, collectionExtras, sizeFactor).first;
        RelativeLayout layout = new RelativeLayout(this);
        View inflated = remote.apply(service, layout);
        layout.addView(inflated);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) inflated.getLayoutParams();
        params.width = widthLimit;
        params.height = heightLimit;
        inflated.setLayoutParams(params);

//        Log.d("APPY", "limits: " + widthLimit + ", " + heightLimit);

        Attributes rootAttributes = new Attributes();
        rootAttributes.attributes.get(Attributes.Type.LEFT).resolvedValue = 0.0;
        rootAttributes.attributes.get(Attributes.Type.TOP).resolvedValue = 0.0;
        rootAttributes.attributes.get(Attributes.Type.RIGHT).resolvedValue = 0.0;
        rootAttributes.attributes.get(Attributes.Type.BOTTOM).resolvedValue = 0.0;
        //don't resolve width height yet

        ViewGroup supergroup = (ViewGroup) inflated;

//        Log.d("APPY", "child count: ");
//        printChildCount(supergroup, "  ");

        //set all to WRAP_CONTENT to measure it's default size (all children are leaves of collections)
        for (int k = 0; k < supergroup.getChildCount(); k++)
        {
            ViewGroup group = (ViewGroup) supergroup.getChildAt(k);
            for (int i = 0; i < group.getChildCount(); i++)
            {
                View view = ((ViewGroup) group.getChildAt(i)).getChildAt(0); //each leaf is inside RelativeLayout is inside elements or collections
                if (view == null)
                {
                    continue;
                }
                params = (RelativeLayout.LayoutParams) view.getLayoutParams();
                params.width = RelativeLayout.LayoutParams.WRAP_CONTENT;
                params.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                view.setLayoutParams(params);
            }
        }

        inflated.measure(View.MeasureSpec.makeMeasureSpec(widthLimit, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(heightLimit, View.MeasureSpec.AT_MOST));

        //set all back to MATCH_PARENT and resolve all trivials
        for (int k = 0; k < supergroup.getChildCount(); k++)
        {
            ViewGroup group = (ViewGroup) supergroup.getChildAt(k);
            for (int i = 0; i < group.getChildCount(); i++)
            {
                View view = ((ViewGroup) group.getChildAt(i)).getChildAt(0); //each leaf is inside RelativeLayout is inside elements or collections
                if (view == null || view.getContentDescription().toString() == null || view.getContentDescription().toString().isEmpty())
                {
                    continue;
                }

                long dynamicViewId = -1;
                //Chronometer and TextClock override getContentDescription, we can't freely set it, we use hint instead (which should never be visible?)
                if (view.getClass() == Chronometer.class || view.getClass() == TextClock.class)
                {
                    dynamicViewId = Long.parseLong(((TextView) view).getHint().toString());
                }
                else
                {
                    dynamicViewId = Long.parseLong(view.getContentDescription().toString());
                }

                DynamicView dynamicView = find(dynamicList, dynamicViewId);

                double viewWidth = view.getMeasuredWidth();
                double viewHeight = view.getMeasuredHeight();

                if (isCollection(dynamicView.type))
                {
                    viewWidth = widthLimit;
                    viewHeight = heightLimit;
                }

                resolveAxis(dynamicView, Attributes.Type.LEFT, Attributes.Type.WIDTH, Attributes.Type.RIGHT, 0, viewWidth, sizeFactor);

                resolveAxis(dynamicView, Attributes.Type.TOP, Attributes.Type.HEIGHT, Attributes.Type.BOTTOM, 0, viewHeight, sizeFactor);

                params = (RelativeLayout.LayoutParams) view.getLayoutParams();
                params.width = RelativeLayout.LayoutParams.MATCH_PARENT;
                params.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                view.setLayoutParams(params);
            }
        }

        // Log.d("APPY", "Attributes "+DynamicView.toJSONString(dynamicList));

        //resolve anything not depending on widget width/height
        while (applyIteration(dynamicList, rootAttributes) != 0) ;

        //resolve width and height
        rootAttributes.attributes.get(Attributes.Type.WIDTH).resolvedValue = (double) widthLimit;
        rootAttributes.attributes.get(Attributes.Type.HEIGHT).resolvedValue = (double) heightLimit;
        //in collection items, there's no width and height limits, they are calculated from the items within
        if (collectionLayout == Constants.CollectionLayout.HORIZONTAL || collectionLayout == Constants.CollectionLayout.BOTH)
        {
            rootAttributes.attributes.get(Attributes.Type.WIDTH).resolvedValue = getMaxDimension(dynamicList, new Attributes.Type[]{Attributes.Type.LEFT, Attributes.Type.WIDTH, Attributes.Type.RIGHT});
        }
        if (collectionLayout == Constants.CollectionLayout.VERTICAL || collectionLayout == Constants.CollectionLayout.BOTH)
        {
            rootAttributes.attributes.get(Attributes.Type.HEIGHT).resolvedValue = getMaxDimension(dynamicList, new Attributes.Type[]{Attributes.Type.TOP, Attributes.Type.HEIGHT, Attributes.Type.BOTTOM});
        }

        //resolve everything else
        while (applyIteration(dynamicList, rootAttributes) != 0) ;

        for (DynamicView dynamicView : dynamicList)
        {
            HashMap<Attributes.Type, Attributes.AttributeValue> unresolved = dynamicView.attributes.unresolved();
            if (!unresolved.isEmpty())
            {
                Map.Entry<Attributes.Type, Attributes.AttributeValue> example = unresolved.entrySet().iterator().next();
                String name = null;
                if (dynamicView.tag instanceof DictObj.Dict)
                {
                    name = ((DictObj.Dict)dynamicView.tag).getString("name");
                }

                StringBuilder unresolvedStr = new StringBuilder();
                for (Map.Entry<Attributes.Type, Attributes.AttributeValue> entry : unresolved.entrySet())
                {
                    unresolvedStr.append("\n").append(entry.getKey()).append(": ").append(DictObj.makeJson(entry.getValue().toDict(), true));
                }

                throw new IllegalArgumentException("Unresolved attributes (" + unresolved.size() + ") for " + dynamicView.type + "(name: '" + name + "', id: " + dynamicView.getId() + "):" + unresolvedStr);
            }
        }

        double totalWidth = 0;
        double totalHeight = 0;
        //apply
        for (DynamicView dynamicView : dynamicList)
        {
            Pair<Integer, Integer> hor = new Pair<>(dynamicView.attributes.attributes.get(Attributes.Type.LEFT).resolvedValue.intValue(),
                    dynamicView.attributes.attributes.get(Attributes.Type.RIGHT).resolvedValue.intValue());
            Pair<Integer, Integer> ver = new Pair<>(dynamicView.attributes.attributes.get(Attributes.Type.TOP).resolvedValue.intValue(),
                    dynamicView.attributes.attributes.get(Attributes.Type.BOTTOM).resolvedValue.intValue());
            Pair<Integer, Integer> dims = new Pair<>(dynamicView.attributes.attributes.get(Attributes.Type.WIDTH).resolvedValue.intValue(),
                    dynamicView.attributes.attributes.get(Attributes.Type.HEIGHT).resolvedValue.intValue());

            if (totalWidth < hor.first + dims.first)
            {
                totalWidth = hor.first + dims.first;
            }
            if (totalHeight < ver.first + dims.second)
            {
                totalHeight = ver.first + dims.second;
            }

            // saving each view size in it so that collection children will know their parent's size
            dynamicView.actualWidth = dims.first;
            dynamicView.actualHeight = dims.second;

//            Log.d("APPY", "resolved attributes: ");
//            for(Map.Entry<Attributes.Type, Attributes.AttributeValue> entry : dynamicView.attributes.attributes.entrySet())
//            {
//                Log.d("APPY", entry.getKey().name()+": "+entry.getValue().resolvedValue);
//            }
//            Log.d("APPY", "selected pad for "+dynamicView.getId()+" "+dynamicView.type+": left:"+hor.first+", right:"+hor.second+", top:"+ver.first+", bottom:"+ver.second);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            {
                //We can finally set width/height!

                dynamicView.methodCalls.add(new RemoteMethodCall("setViewLayoutWidth", true,
                        "setViewLayoutWidth", ((float) dims.first), TypedValue.COMPLEX_UNIT_PX));
                dynamicView.methodCalls.add(new RemoteMethodCall("setViewLayoutHeight", true,
                        "setViewLayoutHeight", ((float) dims.second), TypedValue.COMPLEX_UNIT_PX));

                dynamicView.methodCalls.add(new RemoteMethodCall("setViewLayoutMargin", true,
                        "setViewLayoutMargin", RemoteViews.MARGIN_LEFT, ((float) hor.first), TypedValue.COMPLEX_UNIT_PX));
                dynamicView.methodCalls.add(new RemoteMethodCall("setViewLayoutMargin", true,
                        "setViewLayoutMargin", RemoteViews.MARGIN_TOP, ((float) ver.first), TypedValue.COMPLEX_UNIT_PX));
            }
            else
            {
                //Old method sucks
                dynamicView.methodCalls.add(new RemoteMethodCall("parent_setViewPadding", true, "setViewPadding",
                        hor.first,
                        ver.first,
                        hor.second,
                        ver.second));
            }
        }

        Pair<RemoteViews, HashSet<Integer>> views = generate(service, widgetId, dynamicList, false, collectionLayout, collectionExtras, sizeFactor);
        //only collection elements has size_filler view
        if (collectionLayout != Constants.CollectionLayout.NOT_COLLECTION)
        {
            if (collectionLayout == Constants.CollectionLayout.HORIZONTAL || collectionLayout == Constants.CollectionLayout.BOTH)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                {
                    views.first.setViewLayoutWidth(R.id.size_w_filler, (float) totalWidth, TypedValue.COMPLEX_UNIT_PX);
                }
                else
                {
                    //fill the size width filler (totalWidth must not be 0)
                    views.first.setViewPadding(R.id.size_w_filler, (int) (totalWidth / 2), 0, (int) (totalWidth / 2), 0);
                }
            }
            if (collectionLayout == Constants.CollectionLayout.VERTICAL || collectionLayout == Constants.CollectionLayout.BOTH)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                {
                    views.first.setViewLayoutHeight(R.id.size_h_filler, (float)totalHeight, TypedValue.COMPLEX_UNIT_PX);
                }
                else
                {
                    //fill the size height filler (totalHeight must not be 0)
                    views.first.setViewPadding(R.id.size_h_filler, 0, (int) (totalHeight / 2), 0, (int) (totalHeight / 2));
                }
            }
        }

        return new Triple<> (views.first, views.second, dynamicList);
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent)
    {
        int widgetId = intent.getIntExtra(Constants.WIDGET_ID_EXTRA, -1);
        int xmlId = intent.getIntExtra(Constants.XML_ID_EXTRA, 0);
        int viewId = intent.getIntExtra(Constants.VIEW_ID_EXTRA, 0);
        return getFactory(this, widgetId, xmlId, viewId);
    }

    public void registerOnWidgetUpdate(WidgetUpdateListener listener)
    {
        updateListener = listener;
    }

    public int newWidgetId()
    {
        int counter = 1;
        while (true)
        {
            if (!widgetToAndroid.containsKey(counter))
            {
                break;
            }
            counter++;
        }
        return counter;
    }

    public int getAndroidWidget(int widget)
    {
        Integer androidWidget = widgetToAndroid.get(widget);
        if (androidWidget == null)
        {
            //maybe we could "call destructors"
            delete(widget);
            throw new WidgetDestroyedException();
        }
        return androidWidget;
    }

    public Set<Integer> getAllWidgets()
    {
        Set<Integer> widgets = new HashSet<>();
        widgets.addAll(widgetToAndroid.keySet());
        return widgets;
    }

    public Integer fromAndroidWidget(int androidWidget, boolean create)
    {
        boolean created = false;
        Integer widget = null;
        synchronized (widgetToAndroidLock)
        {
            widget = androidToWidget.get(androidWidget);
            if (widget == null && create)
            {
                widget = addWidget(androidWidget);
                created = true;
            }
        }

        if (created)
        {
            saveWidgetMapping();
        }

        return widget;
    }

    public int addWidget(int androidWidget)
    {
        synchronized (widgetToAndroidLock)
        {
            if (androidToWidget.containsKey(androidWidget))
            {
                throw new IllegalArgumentException("already know this widget id");
            }

            int widget = newWidgetId();

            Log.d("APPY", "add widget " + androidWidget + " -> " + widget);

            androidToWidget.put(androidWidget, widget);
            widgetToAndroid.put(widget, androidWidget);
            return widget;
        }
    }

    public void updateAndroidWidget(int oldAndroidWidget, int newAndroidWidget)
    {
        Integer widget = fromAndroidWidget(oldAndroidWidget, false);
        if (widget == null)
        {
            return;
        }

        synchronized (widgetToAndroidLock)
        {
            androidToWidget.remove(oldAndroidWidget);
            widgetToAndroid.put(widget, newAndroidWidget);
            androidToWidget.put(newAndroidWidget, widget);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            Bundle options = new Bundle();
            options.putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true);
            manager.updateAppWidgetOptions(newAndroidWidget, options);
        }
    }

    public void deleteWidgetMappings(int widget)
    {
        synchronized (widgetToAndroidLock)
        {
            widgetToAndroid.remove(widget);
            HashSet<Integer> toremove = new HashSet<>();
            for (Map.Entry<Integer, Integer> entry : androidToWidget.entrySet())
            {
                if (entry.getValue() == widget)
                {
                    toremove.add(entry.getKey());
                }
            }
            for (Integer androidWidgetId : toremove)
            {
                androidToWidget.remove(androidWidgetId);
            }
        }
    }

    public void deleteAndroidWidget(int androidWidget)
    {
        Integer widget = fromAndroidWidget(androidWidget, false);
        if (widget == null)
        {
            return;
        }

        synchronized (widgetToAndroidLock)
        {
            widgetToAndroid.remove(widget);
            androidToWidget.remove(androidWidget);
        }
    }

    public void loadWidgets()
    {
        try {
            StoreData store = StoreData.Factory.create(this, "widgets");
            Set<String> keys = store.getAll();

            ConcurrentHashMap<Integer, ArrayList<DynamicView>> newwidgets = new ConcurrentHashMap<>();

            for (String widget : keys)
            {
                DictObj.List list = store.getList(widget);
                if (list != null)
                {
                    int widgetId = Integer.parseInt(widget);
                    newwidgets.put(widgetId, DynamicView.fromDictList(list));
                }
            }

            synchronized (widgetLock)
            {
                widgets = newwidgets;
            }

            store = StoreData.Factory.create(this, "etc");
            DictObj.Dict widgetToAndroidDict = store.getDict("widgetToAndroid");
            if (widgetToAndroidDict != null)
            {
                ConcurrentHashMap<Integer, Integer> newWidgetToAndroid = new ConcurrentHashMap<>();

                for (DictObj.Entry entry : widgetToAndroidDict.entries())
                {
                    newWidgetToAndroid.put(Integer.parseInt(entry.key), ((Long)entry.value).intValue());
                }

                ConcurrentHashMap<Integer, Integer> newAndroidToWidget = new ConcurrentHashMap<>();
                for (Map.Entry<Integer, Integer> entry : newWidgetToAndroid.entrySet())
                {
                    newAndroidToWidget.put(entry.getValue(), entry.getKey());
                }

                synchronized (widgetToAndroidLock)
                {
                    widgetToAndroid = newWidgetToAndroid;
                    androidToWidget = newAndroidToWidget;
                }
            }
        }
        catch (Exception e)
        {
            Log.e("APPY", "Exception on loadWidgets", e);
        }
    }

    public void saveWidgetMapping()
    {
        StoreData store = StoreData.Factory.create(this, "etc");

        DictObj.Dict widgetToAndroidDict = new DictObj.Dict();
        for (Map.Entry<Integer, Integer> entry : widgetToAndroid.entrySet())
        {
            widgetToAndroidDict.put(entry.getKey().toString(), entry.getValue());
        }

        store.put("widgetToAndroid", widgetToAndroidDict);
        store.apply();
    }

    public HashMap<Integer, Bundle> filterBadAndroidWidgets(HashMap<Integer, Bundle> ids)
    {
        HashMap<Integer, Bundle> filtered = new HashMap<>();
        for (Map.Entry<Integer, Bundle> entry : ids.entrySet())
        {
            Bundle bundle = entry.getValue();

            boolean hasSizes = Build.VERSION.SDK_INT < 31 || bundle.containsKey("appWidgetSizes");

            if (bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) != 0 &&
                    bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) != 0 &&
                    hasSizes)
            {
                filtered.put(entry.getKey(), bundle);
            }
            else
            {
                Log.d("APPY", "filtering widget id " + entry.getKey());
            }
        }
        return filtered;
    }

    public HashMap<Integer, Bundle> requestAndroidWidgets()
    {
        Class<?>[] widgetReceivers = new Class[]{WidgetReceiver1x1.class, WidgetReceiver2x1.class,
                WidgetReceiver2x2.class, WidgetReceiver3x2.class,
                WidgetReceiver3x3.class, WidgetReceiver4x2.class,
                WidgetReceiver4x3.class, WidgetReceiver4x4.class};
        ArrayList<int[]> allIds = new ArrayList<>();

        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        for (Class<?> clazz : widgetReceivers)
        {
            ComponentName thisWidget = new ComponentName(this, clazz);
            int[] receiverIds = manager.getAppWidgetIds(thisWidget);
            allIds.add(receiverIds);
        }

        HashMap<Integer, Bundle> ids = new HashMap<>();
        for (int[] arr : allIds)
        {
            for (int i : arr)
            {
                ids.put(i, manager.getAppWidgetOptions(i));
            }
        }

        return filterBadAndroidWidgets(ids);
    }

    public void removeWidgetFromStorage(int widgetId)
    {
        StoreData store = StoreData.Factory.create(this, "widgets");
        store.remove(widgetId+"");
        store.apply();
    }

    public void saveWidget(int widgetId, boolean noSave)
    {
        StoreData store = StoreData.Factory.create(this, "widgets");

        DictObj.List widgetCopy;
        synchronized (widgetLock)
        {
            ArrayList<DynamicView> widget = widgets.get(widgetId);
            if (widget == null)
            {
                return;
                //throw new RuntimeException("widget entry is null?");
            }
            widgetCopy = DynamicView.toDictList(widget);
        }
        store.put(widgetId + "", widgetCopy);

        if (!noSave)
        {
            store.apply();
        }
    }

    public void saveAllWidgets()
    {
        HashSet<Integer> widgetIds = new HashSet<>(widgets.keySet());
        synchronized (widgetLock)
        {
            for (Integer widgetId : widgetIds)
            {
                saveWidget(widgetId, true);
            }
        }
        StoreData store = StoreData.Factory.create(this, "widgets");
        store.apply();
    }

    public interface PropSetter
    {
        void set(DictObj.Dict dict);
    }

    public ConcurrentHashMap<Integer, DictObj.Dict> loadProps(String domain)
    {
        try
        {
            StoreData store = StoreData.Factory.create(this, domain);
            Set<String> keys = store.getAll();

            ConcurrentHashMap<Integer, DictObj.Dict> newprops = new ConcurrentHashMap<>();
            for (String widget : keys)
            {
                DictObj.Dict props = store.getDict(widget);
                if (props != null)
                {
                    int widgetId = Integer.parseInt(widget);
                    newprops.put(widgetId, props);
                }
            }
            return newprops;
        }
        catch (Exception e)
        {
            Log.e("APPY", "Exception on loadProps", e);
        }

        return null;
    }

    public void loadWidgetProps()
    {
        ConcurrentHashMap<Integer, DictObj.Dict> newprops = loadProps("widget_props");
        if (newprops == null)
        {
            return;
        }

        synchronized (widgetPropsLock)
        {
            widgetProps = newprops;
        }
    }

    public void loadWidgetAppIcons()
    {
        ConcurrentHashMap<Integer, DictObj.Dict> newprops = loadProps("widget_app_icons");
        if (newprops == null)
        {
            return;
        }

        synchronized (widgetAppIconsLock)
        {
            widgetAppIcons = newprops;
        }
    }

    public void removeProps(ConcurrentHashMap<Integer, DictObj.Dict> map, String domain, int widgetId, boolean flush)
    {
        map.remove(widgetId);

        StoreData store = StoreData.Factory.create(this, domain);
        store.remove(widgetId + "");
        if (flush)
        {
            store.apply();
        }
    }

    //pass lock by ref through array of size 1
    public void saveProps(ConcurrentHashMap<Integer, DictObj.Dict> map, Object[] mapLock, String domain, int widgetId, boolean flush)
    {
        StoreData store = StoreData.Factory.create(this, domain);
        DictObj.Dict props = map.get(widgetId);

        if (props == null)
        {
            return;
        }

        synchronized (mapLock[0])
        {
            props = (DictObj.Dict)props.copy(false);
        }

        store.put(widgetId + "", props);

        if (flush)
        {
            store.apply();
        }
    }

    public void saveWidgetProps(int widgetId, boolean flush)
    {
        saveProps(widgetProps, new Object[]{widgetPropsLock}, "widget_props", widgetId, flush);
    }

    public void setWidgetSizeFactor(int widgetId, Float sizeFactor)
    {
        setProps(widgetProps, new Object[]{widgetPropsLock}, widgetId, "sizeFactor", sizeFactor, (DictObj.Dict dict) -> dict.put("sizeFactor", sizeFactor));
        saveWidgetProps(widgetId, true);
    }

    public void setWidgetAppTitle(int widgetId, String title)
    {
        setProps(widgetProps, new Object[]{widgetPropsLock}, widgetId, "app_title", title, (DictObj.Dict dict) -> dict.put("app_title", title));
        saveWidgetProps(widgetId, true);
    }

    public void setProps(ConcurrentHashMap<Integer, DictObj.Dict> map, Object[] mapLock, int widgetId, String key, Object data, PropSetter setter)
    {
        if (map.get(widgetId) == null)
        {
            if (data == null)
            {
                return;
            }
            map.put(widgetId, new DictObj.Dict());
        }

        DictObj.Dict props = map.get(widgetId);

        synchronized (mapLock[0])
        {
            if (data == null)
            {
                if (props.hasKey(key))
                {
                    props.remove(key);
                }
            }
            else
            {
                setter.set(props);
            }
        }
    }

    public Float getWidgetSizeFactor(int widgetId)
    {
        DictObj.Dict props = widgetProps.get(widgetId);
        if (props == null)
        {
            return null;
        }

        synchronized (widgetPropsLock)
        {
            if (!props.hasKey("sizeFactor"))
            {
                return null;
            }
            return props.getFloat("sizeFactor", 1.0f);
        }
    }

    public DictObj.Dict getAllWidgetAppProps(boolean useAndroidWidgetId, boolean withIcons)
    {
        DictObj.Dict names = getAllWidgetNames();
        DictObj.Dict result = new DictObj.Dict();

        for (String key : names.keys())
        {
            DictObj.Dict data = new DictObj.Dict();
            data.put("name", names.getString(key));
            result.put(key, data);
        }

        synchronized (widgetPropsLock)
        {
            for (Map.Entry<Integer, DictObj.Dict> props : widgetProps.entrySet())
            {
                if (!props.getValue().hasKey("app_title"))
                {
                    continue;
                }

                String key = props.getKey().toString();
                if (!result.hasKey(key))
                {
                    continue;
                }

                result.getDict(key).put("title", props.getValue().getString("app_title"));
            }
        }

        if (withIcons)
        {
            for (Map.Entry<Integer, DictObj.Dict> icons : widgetAppIcons.entrySet())
            {
                String key = icons.getKey().toString();
                if (!result.hasKey(key))
                {
                    continue;
                }

                result.getDict(key).put("icons", icons.getValue());
            }
        }

        AppWidgetManager manager = AppWidgetManager.getInstance(this);

        if (useAndroidWidgetId)
        {
            DictObj.Dict androidResult = new DictObj.Dict();
            for (String key : result.keys())
            {
                Integer androidWidgetId = widgetToAndroid.get(Integer.parseInt(key));
                if (androidWidgetId != null)
                {
                    int[] dimensions = getWidgetDimensions(manager, androidWidgetId);
                    result.getDict(key).put("app", getWidgetIsApp(manager, androidWidgetId));
                    result.getDict(key).put("width", dimensions[0]);
                    result.getDict(key).put("height", dimensions[1]);
                    result.getDict(key).put("width_dp", dimensions[2]);
                    result.getDict(key).put("height_dp", dimensions[3]);
                    androidResult.put(androidWidgetId.toString(), result.getDict(key));
                }
            }

            result = androidResult;
        }
        else
        {
            for (String key : result.keys())
            {
                Integer androidWidgetId = widgetToAndroid.get(Integer.parseInt(key));
                if (androidWidgetId != null)
                {
                    int[] dimensions = getWidgetDimensions(manager, androidWidgetId);
                    result.getDict(key).put("androidWidgetId", androidWidgetId);
                    result.getDict(key).put("app", getWidgetIsApp(manager, androidWidgetId));
                    result.getDict(key).put("width", dimensions[0]);
                    result.getDict(key).put("height", dimensions[1]);
                    result.getDict(key).put("width_dp", dimensions[2]);
                    result.getDict(key).put("height_dp", dimensions[3]);
                }
            }
        }

        return result;
    }

    public void resetWidgetSizeFactors()
    {
        HashSet<Integer> widgetIds = new HashSet<>(widgets.keySet());
        for (int widgetId : widgetIds)
        {
            DictObj.Dict props = widgetProps.get(widgetId);
            if (props != null)
            {
                synchronized (widgetPropsLock)
                {
                    props.remove("sizeFactor");
                    saveWidgetProps(widgetId, false);
                }
            }
        }

        StoreData.Factory.create(this, "widget_props").apply();
    }

    public void setWidgetAppIcons(int widgetId, DictObj.Dict pngs)
    {
        if (pngs != null)
        {
            for (DictObj.Entry entry : pngs.entries())
            {
                if (!(entry.value instanceof byte[]))
                {
                    throw new IllegalArgumentException("pngs dict values must be byte arrays, not " + entry.value.getClass().getSimpleName());
                }
                if (!entry.key.matches(Constants.APP_ICON_REGEX))
                {
                    throw new IllegalArgumentException("pngs dict keys must be of the form '{selector}_{w}x{h}'");
                }
            }

            widgetAppIcons.put(widgetId, pngs);
        }
        else
        {
            widgetAppIcons.remove(widgetId);
        }

        saveProps(widgetAppIcons, new Object[] {widgetAppIconsLock}, "widget_app_icons", widgetId, true);
    }

    public float[] getCorrectionFactors()
    {
        return new float[]{widthCorrectionFactor, heightCorrectionFactor};
    }

    public float getGlobalSizeFactor()
    {
        return globalSizeFactor;
    }

    public void setCorrectionFactors(float widthCorrection, float heightCorrection)
    {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
        editor.putString("width_correction", widthCorrection + "");
        editor.putString("height_correction", heightCorrection + "");
        editor.apply();

        loadCorrectionFactors(false);
    }

    public void loadCorrectionFactors(boolean initing)
    {
        float widthCorrection = 1.0f;
        float heightCorrection = 1.0f;
        float sizeFactor = 1.0f;

        try
        {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            try
            {
                widthCorrection = Float.parseFloat(sharedPref.getString("width_correction", "1"));
            }
            catch (NumberFormatException e)
            {
                Log.w("APPY", "wrong number format for width");
            }

            try
            {
                heightCorrection = Float.parseFloat(sharedPref.getString("height_correction", "1"));
            }
            catch (NumberFormatException e)
            {
                Log.w("APPY", "wrong number format for width");
            }

            try
            {
                sizeFactor = Float.parseFloat(sharedPref.getString("global_size_factor", "1"));
            }
            catch (NumberFormatException e)
            {
                Log.w("APPY", "wrong number for global size factor");
            }

            if (widthCorrection <= 0 || widthCorrection > 3)
            {
                widthCorrection = 1;
            }
            if (heightCorrection <= 0 || heightCorrection > 3)
            {
                heightCorrection = 1;
            }

            if (sizeFactor <= 0 || sizeFactor > 3)
            {
                sizeFactor = 1;
            }

        }
        catch (Exception e)
        {
            Log.e("APPY", "Exception on loadCorrectionFactors", e);
        }

        widthCorrectionFactor = widthCorrection;
        heightCorrectionFactor = heightCorrection;
        globalSizeFactor = sizeFactor;

        Log.d("APPY", "new correction factors: " + widthCorrectionFactor + ", " + heightCorrectionFactor);
        if (!initing)
        {
            updateAll();
        }
    }

    public static boolean needForeground()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static void startService(Context context, Intent intent)
    {
        // this has nothing to do with the actual foregroundness of the service, but startService will fail if needForeground().
        if (needForeground())
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            {
                try
                {
                    context.startForegroundService(intent);
                }
                catch (ForegroundServiceStartNotAllowedException e)
                {
                    Log.e("APPY", "startForeground not allowed", e);
                }
            }
            else
            {
                context.startForegroundService(intent);
            }
        }
        else
        {
            context.startService(intent);
        }
    }

    private static boolean foregroundWarningShown = false;

    public void loadForeground()
    {
        Log.d("APPY", "foreground is on");

        Notification.Builder builder;
        if (needForeground())
        {
            final String CHANNEL = "Service notification";
            NotificationChannel channel_none = new NotificationChannel(CHANNEL, CHANNEL, NotificationManager.IMPORTANCE_NONE);
            channel_none.setSound(null, null);
            channel_none.enableVibration(false);
            channel_none.setShowBadge(false);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel_none);
            builder = new Notification.Builder(this, CHANNEL);
            builder.setChannelId(CHANNEL);
        }
        else
        {
            builder = new Notification.Builder(this);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        Notification notification = builder.setContentTitle("Appy")
                .setContentText("Appy is running")
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setPriority(Notification.PRIORITY_MIN)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setWhen(0)
                .build();
        try
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
            }
            else
            {
                startForeground(NOTIFICATION_ID, notification);
            }
        }
        catch (RuntimeException e)
        {
            if (!foregroundWarningShown)
            {
                Toast.makeText(this, "Could not start Appy because it is lacking permissions.", Toast.LENGTH_LONG).show();
                foregroundWarningShown = true;
            }
        }
    }

    public long generateTimerId()
    {
        synchronized (activeTimersLock)
        {
            long newId = new Random().nextLong();
            activeTimers.put(newId, Timer.INVALID); //save room
            return newId;
        }
    }

    static class Timer
    {
        public static final Timer INVALID = new Timer(-1, -1, -1, -1, null);
        public Timer(int widgetId, long since, long millis, int type, String data)
        {
            this.widgetId = widgetId;
            this.since = since;
            this.millis = millis;
            this.type = type;
            this.data = data;
        }

        public DictObj.Dict toDict(long timerId)
        {
            DictObj.Dict obj = new DictObj.Dict();
            obj.put("timerId", timerId);
            obj.put("widgetId", widgetId);
            obj.put("since", since);
            obj.put("millis", millis);
            obj.put("type", type);
            obj.put("data", data);
            return obj;
        }

        @Override
        public String toString()
        {
            return widgetId + ", " + since + ", " + millis + ", " + type + ", " + data;
        }

        int widgetId;
        long since;
        long millis;
        int type;
        String data;
    }

    public void cancelTimer(long timerId)
    {
        cancelTimer(timerId, true);
    }

    public void cancelTimer(long timerId, boolean save)
    {
        PendingIntent[] pendingIntent;
        synchronized (activeTimersLock)
        {
            activeTimers.remove(timerId);
            pendingIntent = activeTimersIntents.remove(timerId);
        }
        if (pendingIntent != null && pendingIntent[0] != null)
        {
            AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            mgr.cancel(pendingIntent[0]);
        }

        if (save)
        {
            saveTimers();
        }
    }

    public void cancelWidgetTimers(int widgetId)
    {
        HashSet<Long> toCancel = new HashSet<>();
        for (Map.Entry<Long, Timer> timer : activeTimers.entrySet())
        {
            if (timer.getValue().widgetId == widgetId)
            {
                toCancel.add(timer.getKey());
            }
        }

        for (long timer : toCancel)
        {
            cancelTimer(timer, false);
        }
        saveTimers();
    }

    public void cancelAllTimers()
    {
        Log.d("APPY", "cancelling all timers");
        HashSet<Long> toCancel = new HashSet<>();
        toCancel.addAll(activeTimers.keySet());
        for (long timer : toCancel)
        {
            cancelTimer(timer, false);
        }
        saveTimers();
    }

    public void setPost(int widgetId, String data)
    {
        addTask(widgetId, new Task<>(new CallPostTask(), widgetId, data), false);
    }

    public void configurationUpdate(String widget, String key)
    {
        if (updateListener != null)
        {
            try
            {
                updateListener.syncConfig(getConfigurations().getDict());
            }
            catch(Exception e)
            {
                Log.e("APPY", "Exception in python", e);
            }

            if (widget != null)
            {
                int[] widgetIds = updateListener.findWidgetsByMame(widget);
                for (int widgetId : widgetIds)
                {
                    addTask(widgetId, new Task<>(new CallConfigTask(), widgetId, key), false);
                }
            }
        }
    }

    public void shareWithWidget(int widgetId, String mimetype, String text, DictObj.Dict datas)
    {
        addTask(widgetId, new Task<>(new CallShareTask(), widgetId, mimetype, text, datas), false);
    }

    public DictObj.Dict getAllWidgetNames()
    {
        if (updateListener == null)
        {
            return new DictObj.Dict();
        }

        return updateListener.getAllWidgetNames();
    }

    public long setTimer(long millis, int type, int widgetId, String data)
    {
        return setTimer(System.currentTimeMillis(), millis, type, widgetId, data, -1);
    }

    long timeToNext(long since, long interval)
    {
        return interval - ((System.currentTimeMillis() - since) % interval);
    }

    @SuppressLint("MissingPermission")
    public long setTimer(long since, long millis, int type, int widgetId, String data, long timerId)
    {
        long now = System.currentTimeMillis();
        if (type == Constants.TIMER_RELATIVE)
        {
            millis += now;
            type = Constants.TIMER_ABSOLUTE;
        }

        Intent timerIntent;
        PendingIntent[] pendingIntent;

        if (timerId == -1)
        {
            timerId = generateTimerId();
        }
        timerIntent = new Intent(Widget.this, getClass());
        timerIntent.setAction("timer" + timerId); //make it unique for cancel
        timerIntent.putExtra("widgetId", widgetId);
        timerIntent.putExtra("timer", timerId);
        timerIntent.putExtra("timerData", data);

        //trick to insert a reference to the hashmap to be populated later
        pendingIntent = new PendingIntent[1];

        Timer timer = new Timer(widgetId, since, millis, type, data);
        synchronized (activeTimersLock)
        {
            activeTimers.put(timerId, timer);
            activeTimersIntents.put(timerId, pendingIntent);
        }
        saveTimers();

        if (type == Constants.TIMER_REPEATING && millis <= Constants.TIMER_MAX_HANDLER)
        {
            Log.d("APPY", "setting short time timer");
            handler.post(new Utils.ArgRunnable(timerIntent, since, millis, timerId)
            {
                boolean first = true;

                @Override
                public void run()
                {
                    long timer = (long) args[3];

                    if (!first)
                    {
                        Log.d("APPY", "short time timer fire "+timer);
                        Widget.startService(Widget.this, (Intent) args[0]);
                    }
                    first = false;

                    Timer obj = activeTimers.get(timer);
                    if (obj != null && !obj.equals(Timer.INVALID))
                    {
                        //timer still active
                        long interval = (long) args[2];
                        long since = (long) args[1];
                        handler.postDelayed(this, first ? timeToNext(since, interval) : interval);
                    }
                }
            });
        }
        else
        {
            AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (needForeground())
            {
                pendingIntent[0] = PendingIntent.getForegroundService(getApplicationContext(), 1, timerIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
            }
            else
            {
                pendingIntent[0] = PendingIntent.getService(getApplicationContext(), 1, timerIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
            }
            //clear previous alarm (if we crashed but no reboot)
            mgr.cancel(pendingIntent[0]);

            if (type == Constants.TIMER_REPEATING)
            {
                long toNext = timeToNext(since, millis);
                Log.d("APPY", "setting long time timer: " + millis + ", next in " + toNext);
                mgr.setRepeating(AlarmManager.RTC_WAKEUP, toNext + now, millis, pendingIntent[0]);
            }
            else
            {
                Log.d("APPY", "setting one time timer");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                {
                    mgr.setExact(AlarmManager.RTC_WAKEUP, millis, pendingIntent[0]);
                }
                else
                {
                    mgr.set(AlarmManager.RTC_WAKEUP, millis, pendingIntent[0]);
                }
            }
        }

        return timerId;
    }

    public void loadTimers()
    {
        try
        {
            StoreData store = StoreData.Factory.create(this, "timers");
            DictObj.List timersList = store.getList("timers");
            if (timersList != null)
            {
                for (int i = 0; i < timersList.size(); i++)
                {
                    DictObj.Dict timer = timersList.getDict(i);

                    setTimer(timer.getLong("since", 0),
                            timer.getLong("millis", 0),
                            timer.getInt("type", 0),
                            timer.getInt("widgetId", -1),
                            timer.getString("data"),
                            timer.getLong("timerId", -1));
                }
                Log.d("APPY", "loaded " + timersList.size() + " timers");
            }
        }
        catch (Exception e)
        {
            Log.e("APPY", "Exception on loadTimers", e);
        }
    }

    public static boolean getRefreshOnModify(Context context)
    {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPref.getBoolean("refresh_on_modify", true);
    }

    public void onPythonFileModified(PythonFile pythonFile)
    {
        boolean refresh = getRefreshOnModify(this);
        if (refresh)
        {
            Log.d("APPY", "File " + pythonFile.path + " modified, reloading");
            refreshPythonFile(pythonFile);
        }
        else
        {
            Log.d("APPY", "File " + pythonFile.path + " modified, NOT reloading");
        }
    }

    public interface MultipleFileObserverBase
    {
        void start();

        void stop();
    }

    public abstract class MultipleFileObserver extends FileObserver implements MultipleFileObserverBase
    {
        List<PythonFile> pythonFiles;

        @RequiresApi(api = Build.VERSION_CODES.Q)
        public MultipleFileObserver(List<PythonFile> pythonFiles, int mask)
        {
            super(pythonFiles.stream().map(pythonFile -> new File(pythonFile.path).getParentFile()).filter(Objects::nonNull).collect(Collectors.toList()), mask);

            this.pythonFiles = new ArrayList<>(pythonFiles);
        }

        public void start()
        {
            startWatching();
        }

        public void stop()
        {
            stopWatching();
        }

        @Override
        public void onEvent(final int event, @Nullable final String filename)
        {
            handler.postDelayed(() -> {
                Log.d("APPY", "got base inotify: " + filename + " " + event);
                // we have no way of knowing where it came from, firing for all pythonfiles with the same filename
                for (PythonFile pythonFile : getPythonFiles())
                {
                    if (new File(pythonFile.path).getName().equals(filename))
                    {
                        onEvent(event, pythonFile);
                    }
                }
            }, 100);
        }

        public abstract void onEvent(int event, PythonFile file);
    }

    public abstract class OldMultipleFileObserver implements MultipleFileObserverBase
    {
        class SingleFileObserver extends FileObserver
        {
            PythonFile file;
            int mask;
            OldMultipleFileObserver parent;
            int index;

            public SingleFileObserver(@NonNull PythonFile file, int mask, OldMultipleFileObserver parent, int index)
            {
                super(file.path, mask);
                this.file = file;
                this.mask = mask;
                this.parent = parent;
                this.index = index;
            }


            @Override
            public void onEvent(int event, @Nullable String path)
            {
                this.parent.onSingleEvent(event, index);
            }
        }

        ArrayList<SingleFileObserver> observers = new ArrayList<>();

        public OldMultipleFileObserver(List<PythonFile> files, int mask)
        {
            for (PythonFile file : files)
            {
                observers.add(new SingleFileObserver(file, mask, this, observers.size()));
            }
        }

        public void start()
        {
            for (SingleFileObserver observer : observers)
            {
                observer.startWatching();
            }
        }

        public void stop()
        {
            for (SingleFileObserver observer : observers)
            {
                observer.stopWatching();
            }
        }

        public void onSingleEvent(int event, int index)
        {
            handler.postDelayed(() -> {
                //update observer
                SingleFileObserver ob = observers.get(index);
                ob.stopWatching();
                SingleFileObserver newob = new SingleFileObserver(ob.file, ob.mask, OldMultipleFileObserver.this, index);
                observers.set(index, newob);
                newob.startWatching();

                Log.d("APPY", "new watch on " + index + " " + newob.file.path + " " + newob.mask);

                onEvent(event, newob.file);
            }, 300);
        }

        public abstract void onEvent(int event, PythonFile file);
    }

    public void updateObserver()
    {
        Log.d("APPY", "updating observers");
        if (pythonFilesObserver != null)
        {
            pythonFilesObserver.stop();
        }

        ArrayList<PythonFile> pythonFilesCopy;
        synchronized (pythonFilesLock)
        {
            pythonFilesCopy = new ArrayList<>(pythonFiles);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            pythonFilesObserver = new MultipleFileObserver(pythonFilesCopy, FileObserver.CLOSE_WRITE)
            {
                @Override
                public void onEvent(int event, PythonFile file)
                {
                    if (file != null)
                    {
                        Log.d("APPY", "inotify event: " + file.path);
                        onPythonFileModified(file);
                    }
                }
            };
        }
        else
        {
            pythonFilesObserver = new OldMultipleFileObserver(pythonFilesCopy, FileObserver.CLOSE_WRITE)
            {
                @Override
                public void onEvent(int event, PythonFile file)
                {
                    if (file != null)
                    {
                        Log.d("APPY", "inotify event: " + file.path);
                        onPythonFileModified(file);
                    }
                }
            };
        }

        pythonFilesObserver.start();
    }

    public void loadPythonFiles()
    {
        try
        {
            StoreData store = StoreData.Factory.create(this, "pythonfiles");
            DictObj.List pythonfilesList = store.getList("pythonfiles");
            if (pythonfilesList != null)
            {
                ArrayList<PythonFile> list = PythonFile.fromList(pythonfilesList);
                synchronized (pythonFilesLock)
                {
                    pythonFiles = list;
                }
            }
            DictObj.Dict unknownPythonFileDict = store.getDict("unknownpythonfile");
            if (unknownPythonFileDict != null)
            {
                PythonFile file = PythonFile.fromDict(unknownPythonFileDict);
                synchronized (pythonFilesLock)
                {
                    unknownPythonFile = file;
                }
            }
        }
        catch (Exception e)
        {
            Log.e("APPY", "Exception on loadPythonFiles", e);
        }

        updateObserver();
    }

    public void savePythonFiles()
    {
        StoreData store = StoreData.Factory.create(this, "pythonfiles");
        store.put("pythonfiles", PythonFile.toList(getPythonFiles()));
        DictObj.Dict dict;
        synchronized (pythonFilesLock)
        {
            dict = unknownPythonFile.toDict();
        }
        store.put("unknownpythonfile", dict);
        store.apply();
    }

    public boolean addPythonFileByPath(String path)
    {
        if (!new File(path).isFile())
        {
            return false;
        }
        ArrayList<PythonFile> lst = new ArrayList<>();
        lst.add(new PythonFile(path));
        addPythonFiles(lst);

        return true;
    }

    public boolean addPythonFileByPathWithDialog(String path)
    {
        // Fail early
        if (!new File(path).isFile())
        {
            return false;
        }

        Pair<Integer, String[]> pair = showAndWaitForDialog(null, "Add Python File", "Allow widget to add the python file \"" + new File(path).getName() + "\"?", new String[]{"OK", "CANCEL"}, new String[0], new String[0], new String[0][],-1);
        if (pair.first == 0)
        {
            return addPythonFileByPath(path);
        }
        return false;
    }

    public PythonFile findPythonFile(String path)
    {
        ArrayList<PythonFile> files = getPythonFiles();
        for (PythonFile f : files)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                try
                {
                    if (Files.isSameFile(Paths.get(f.path), Paths.get(path)))
                    {
                        return f;
                    }
                    continue;
                }
                catch (IOException e)
                {
                    Log.e("APPY", "problem comparing paths", e);
                }
            }

            // fallback check
            if (f.path.equals(path))
            {
                return f;
            }
        }

        return null;
    }

    public boolean refreshPythonFileByPath(String path)
    {
        PythonFile pythonFile = findPythonFile(path);
        if (pythonFile == null)
        {
            return false;
        }

        refreshPythonFile(pythonFile);
        return true;
    }

    public void refreshPythonFile(PythonFile file)
    {
        Task<?> task = new Task<>(new CallImportTask(), file, false);
        addTask(Constants.IMPORT_TASK_QUEUE, task, false);
    }

    public static Class<?> getUtils()
    {
        return Utils.class;
    }

    public static String getPreferredScriptDirStatic(Context context)
    {
        File[] mediaDirs = context.getExternalMediaDirs();
        if (mediaDirs.length > 0 && mediaDirs[0] != null)
        {
            return mediaDirs[0].getAbsolutePath();
        }

        //fallback
        return context.getFilesDir().getAbsolutePath();
    }

    public String getPreferredScriptDir()
    {
        return getPreferredScriptDirStatic(this);
    }

    public static String getPreferredCacheDirStatic(Context context)
    {
        return context.getCacheDir().getAbsolutePath();
    }

    public String getPreferredCacheDir()
    {
        return getPreferredCacheDirStatic(this);
    }

    public void initAllPythonFiles()
    {
        ArrayList<PythonFile> files = getPythonFiles();
        for (PythonFile f : files)
        {
            // python cannot do multithreaded imports
            Task task = new Task<>(new CallImportTask(), f, true);
            task.run();
        }

        if (updateListener != null)
        {
            updateListener.refreshManagers();
        }
    }

    public void addPythonFiles(ArrayList<PythonFile> files)
    {
        synchronized (pythonFilesLock)
        {
            ArrayList<PythonFile> toadd = new ArrayList<>();
            for (PythonFile file : files)
            {
                if (findPythonFile(file.path) == null)
                {
                    toadd.add(file);
                }
            }
            pythonFiles.addAll(toadd);
        }

        updateObserver();
        savePythonFiles();
        for (PythonFile file : files)
        {
            refreshPythonFile(file);
        }
    }

    public void clearFileError(PythonFile file)
    {
        synchronized (pythonFilesLock)
        {
            int idx = pythonFiles.indexOf(file);
            if (idx != -1)
            {
                pythonFiles.get(idx).lastError = "";
                pythonFiles.get(idx).lastErrorDate = null;
            }
            else if (file.equals(unknownPythonFile))
            {
                unknownPythonFile.lastError = "";
                unknownPythonFile.lastErrorDate = null;
            }
        }
        savePythonFiles();
    }

    public void removePythonFile(PythonFile file)
    {
        synchronized (pythonFilesLock)
        {
            pythonFiles.remove(file);
        }
        if (updateListener != null)
        {
            //ok to be called on main thread
            updateListener.deimportFile(file.path, false);
        }
        updateObserver();
        savePythonFiles();
    }

    public ArrayList<PythonFile> getPythonFiles()
    {
        synchronized (pythonFilesLock)
        {
            return new ArrayList<>(pythonFiles);
        }
    }

    public void saveTimers()
    {
        StoreData store = StoreData.Factory.create(this, "timers");

        DictObj.List timersList = new DictObj.List();

        for (Map.Entry<Long, Timer> timer : activeTimers.entrySet())
        {
            if (timer.getValue() == null || timer.getValue().equals(Timer.INVALID))
            {
                continue;
            }

            timersList.add(timer.getValue().toDict(timer.getKey()));
        }

        store.put("timers", timersList);
        store.apply();
    }

    public void clearWidget(int widgetId)
    {
        cancelWidgetTimers(widgetId);
        if (updateListener != null)
        {
            updateListener.onDelete(widgetId);
        }
        update(widgetId);
    }

    public void recreateWidget(int widgetId)
    {
        if (updateListener != null)
        {
            updateListener.recreateWidget(widgetId);
        }
    }

    public void recreateWidgets()
    {
        for (int widgetId : getAllWidgets())
        {
            recreateWidget(widgetId);
        }
    }

    public DictObj.Dict getStateLayoutSnapshot()
    {
        if (updateListener == null)
        {
            return null;
        }

        return updateListener.getStateLayoutSnapshot();
    }

    public void cleanState(String scope, String widget, String key)
    {
        if (updateListener != null)
        {
            updateListener.cleanState(scope, widget, key);
        }
    }

    public void delete(int widgetId)
    {
        delete(widgetId, null);
    }

    public void delete(int widgetId, Integer androidWidgetDeleted)
    {
        addTask(widgetId, new Task<>(new DeleteWidgetTask(), widgetId, androidWidgetDeleted), false);
    }

    public void update(int widgetId)
    {
        addTask(widgetId, new Task<>(new CallUpdateTask(), widgetId), false);
    }

    public void updateAll()
    {
        for (int widgetId : getAllWidgets())
        {
            update(widgetId);
        }
    }

    public void restart()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                saveTimers();
                saveAllWidgets();
                saveWidgetMapping();
                savePythonFiles();
                new Task<>(new SaveStateTask()).run();

                StoreData.Factory.commitAll();
                handler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Log.d("APPY", "restarting process");
                        setAllWidgets(false);

                        Intent intent = new Intent(Widget.this, getClass());
                        PendingIntent pendingIntent;
                        if (needForeground())
                        {
                            pendingIntent = PendingIntent.getForegroundService(getApplicationContext(), 1, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
                        }
                        else
                        {
                            pendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
                        }
                        AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent);
                        System.exit(0);
                    }
                });
            }
        }).start();
    }

    public String pythonVersion()
    {
        if (updateListener != null)
        {
            return updateListener.getVersion();
        }
        return "Not available";
    }

    public void dumpPythonStacktrace()
    {
        if (updateListener != null)
        {
            try
            {
                updateListener.dumpStacktrace(Utils.getCrashPath(this, Constants.CrashIndex.PYTHON_TRACE_INDEX));
            }
            catch (Exception e)
            {
                Log.e("APPY", "Exception on dumpPythonStacktrace", e);
            }
        }
    }
    public void dumpJavaStacktrace()
    {
        try
        {
            Utils.dumpStacktrace(Utils.getCrashPath(this, Constants.CrashIndex.JAVA_TRACE_INDEX));
        }
        catch (Exception e)
        {
            Log.e("APPY", "Exception on dumpJavaStacktrace", e);
        }
    }

    public void dumpStacktrace()
    {
        dumpJavaStacktrace();
        dumpPythonStacktrace();
    }

    public void deleteSavedState()
    {
        StoreData store = StoreData.Factory.create(this, "state");
        store.removeAll();
        store.apply();
    }

    private class SaveWidgetTask implements Runner<Integer>
    {
        @Override
        public void run(Integer... args)
        {
            int widgetId = args[0];
            saveWidget(widgetId, false);
        }
    }

    private class SaveStateTask implements Runner<Void>
    {
        @Override
        public void run(Void... args)
        {
            if (updateListener != null)
            {
                updateListener.saveState();
            }
        }
    }

    public void deferredStateSave(int widgetId)
    {
        addTask(widgetId, new Task<>(new SaveStateTask()), true);
    }

    public void deferredWidgetSave(int widgetId)
    {
        addTask(widgetId, new Task<>(new SaveWidgetTask(), widgetId), true);
    }

    public void saveSpecificState(DictObj.Dict statesModified)
    {
        StoreData store = StoreData.Factory.create(this, "state");
        for (DictObj.Entry entry : statesModified.entries())
        {
            DictObj.Dict value = (DictObj.Dict) entry.value;
            if (value.getBoolean("deleted", false))
            {
                store.remove(entry.key);
            }
            else
            {
                store.put(entry.key, value);
            }
        }
        store.apply();
    }

    public DictObj.Dict loadAllState()
    {
        try
        {
            StoreData store = StoreData.Factory.create(this, "state");
            Set<String> keys = store.getAll();
            DictObj.Dict state = new DictObj.Dict();
            for (String key : keys)
            {
                state.put(key, store.getDict(key));
            }

            return state;
        }
        catch (Exception e)
        {
            Log.e("APPY", "loading state failed", e);
        }
        return null;
    }

    public Configurations getConfigurations()
    {
        return configurations;
    }

    public void setPythonUnpacked(int version)
    {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
        editor.putInt("unpacked_version", version);
        editor.apply();
    }

    public int getPythonUnpacked()
    {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPref.getInt("unpacked_version", 0);
    }

    public Uri getUriForPath(String path)
    {
        return FileProvider.getUriForFile(this, "com.appy.appyfileprovider", new File(path));
    }

    public int generateRequestCode()
    {
        synchronized (notifier)
        {
            int newId = 1;
            if (!activeRequests.isEmpty())
            {
                newId = Collections.max(activeRequests.keySet()) + 1;
            }
            activeRequests.put(newId, INVALID_RESULT); //save room
            return newId;
        }
    }

    public void toast(String text, boolean longDuration)
    {
        handler.post(() -> Toast.makeText(Widget.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show());
    }


    final Object notifier = new Object();

    public Object waitForAsyncReport(int requestCode, int timeoutMilli)
    {
        Object result = null;
        synchronized (notifier)
        {
            boolean hasTimeout = timeoutMilli >= 0;
            long end = System.currentTimeMillis() + timeoutMilli;
            while (activeRequests.get(requestCode) == null || activeRequests.get(requestCode) == INVALID_RESULT)
            {
                try
                {
                    if (hasTimeout)
                    {
                        long left = end - System.currentTimeMillis();
                        if (left <= 0)
                        {
                            break;
                        }

                        notifier.wait(left);
                    }
                    else
                    {
                        notifier.wait();
                    }
                }
                catch (InterruptedException e)
                {

                }
            }
            result = activeRequests.remove(requestCode);
        }
        return result;
    }

    public Pair<String[], int[]> requestPermissions(String[] permissions, boolean request, int timeoutMilli)
    {
        Pair<int[], Boolean> state = PermissionActivity.getPermissionState(this, permissions);
        if (state.second || !request) //if all was granted or we should not request any, return the current state
        {
            return new Pair<>(permissions, state.first);
        }

        if (Looper.myLooper() != null)
        {
            throw new IllegalStateException("requestPermissions must be called on a Task thread");
        }

        if (!PermissionActivity.canRequestPermissionsTogether(permissions))
        {
            throw new IllegalArgumentException("Given permissions cannot be requested with a single request");
        }

        int requestCode = generateRequestCode();
        Intent intent = new Intent(this, PermissionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.putExtra(PermissionActivity.EXTRA_REQUEST_CODE, requestCode);
        intent.putExtra(PermissionActivity.EXTRA_PERMISSIONS, permissions);
        startActivity(intent);

        return (Pair<String[], int[]>) waitForAsyncReport(requestCode, timeoutMilli);
    }

    public int showDialogNoWait(Integer icon, String title, String text, String[] buttons, String[] editText, String[] editHints, String[][] editOptions)
    {
        int requestCode = generateRequestCode();
        Intent intent = new Intent(this, DialogActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(DialogActivity.EXTRA_REQUEST_CODE, requestCode);
        intent.putExtra(DialogActivity.EXTRA_TITLE, title);
        intent.putExtra(DialogActivity.EXTRA_TEXT, text);
        intent.putExtra(DialogActivity.EXTRA_BUTTONS, buttons);
        intent.putExtra(DialogActivity.EXTRA_EDITTEXT_TEXT, editText);
        intent.putExtra(DialogActivity.EXTRA_EDITTEXT_HINT, editHints);
        intent.putExtra(DialogActivity.EXTRA_EDITTEXT_OPTIONS, editOptions);
        if (icon != null)
        {
            intent.putExtra(DialogActivity.EXTRA_ICON, icon.intValue());
        }
        startActivity(intent);
        return requestCode;
    }

    public Pair<Integer, String[]> showAndWaitForDialog(Integer icon, String title, String text, String[] buttons, String[] editTexts, String[] editHints, String[][] editOptions, int timeoutMilli)
    {
        if (Looper.myLooper() != null)
        {
            throw new IllegalStateException("showAndWaitForDialog must be called on a Task thread");
        }

        int requestCode = showDialogNoWait(icon, title, text, buttons, editTexts, editHints, editOptions);

        return (Pair<Integer, String[]>) waitForAsyncReport(requestCode, timeoutMilli);
    }

    //result must be immutable
    public void asyncReport(int requestCode, @NonNull Object result)
    {
        synchronized (notifier)
        {
            if (activeRequests.containsKey(requestCode))
            {
                activeRequests.put(requestCode, result);
                notifier.notify();
            }
        }
    }

    public void startMainActivity(String fragment, Bundle arg)
    {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        if (fragment != null)
        {
            intent.putExtra(Constants.FRAGMENT_NAME_EXTRA, fragment);
        }
        if (arg != null)
        {
            intent.putExtra(Constants.FRAGMENT_ARG_EXTRA, arg);
        }
        startActivity(intent);
    }

    public void startConfigFragment(String widget)
    {
        Bundle bundle = new Bundle();
        bundle.putString(Constants.FRAGMENT_ARG_WIDGET, widget);
        startMainActivity("Configurations", bundle);
    }

    public boolean requestConfigChange(String widget, String config, int timeoutMilli)
    {
        if (Looper.myLooper() != null)
        {
            throw new IllegalStateException("requestConfigChange must be called on a Task thread");
        }

        int requestCode = generateRequestCode();
        Bundle bundle = new Bundle();
        bundle.putString(Constants.FRAGMENT_ARG_WIDGET, widget);
        bundle.putString(Constants.FRAGMENT_ARG_CONFIG, config);
        bundle.putInt(Constants.FRAGMENT_ARG_REQUESTCODE, requestCode);

        startMainActivity("Configurations", bundle);

        return waitForAsyncReport(requestCode, timeoutMilli) != null;
    }

    public void setWidget(final int androidWidgetId, final int widgetId, final ArrayList<DynamicView> views, final boolean errorOnFailure)
    {
        synchronized (needUpdateWidgets)
        {
            needUpdateWidgets.remove(widgetId);
        }

        try
        {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(Widget.this);
            int[] widgetDimensions = getWidgetDimensions(appWidgetManager, androidWidgetId);
            int widthLimit = widgetDimensions[0];
            int heightLimit = widgetDimensions[1];

            Triple<RemoteViews, HashSet<Integer>, ArrayList<DynamicView>> view = resolveDimensions(Widget.this, widgetId, views, Constants.CollectionLayout.NOT_COLLECTION, null, widthLimit, heightLimit);
            appWidgetManager.updateAppWidget(androidWidgetId, view.component1());

            for (Integer collection_view : view.component2())
            {
                appWidgetManager.notifyAppWidgetViewDataChanged(androidWidgetId, collection_view);
            }
        }
        catch (Exception e)
        {
            Log.e("APPY", "Exception on setWidget", e);
            if (errorOnFailure)
            {
                setSpecificErrorWidget(androidWidgetId, widgetId, e);
            }
        }
    }

    public void setLoadingWidget(int widgetId)
    {
        setSpecificLoadingWidget(getAndroidWidget(widgetId), widgetId);
    }

    public void setSpecificLoadingWidget(int androidWidgetId, int widgetId)
    {
        ArrayList<DynamicView> views = new ArrayList<>();

        DynamicView textView = new DynamicView("TextView");
        addMethodCall(textView, "setText", "Loading...");
        addMethodCall(textView, "setTextColor", Constants.TEXT_COLOR);
        addMethodCall(textView, "setTextSize", "12sp");

        textView.attributes.attributes.put(Attributes.Type.TOP, attributeParse("15"));
        textView.attributes.attributes.put(Attributes.Type.LEFT, attributeParse("15"));

        views.add(textView);

        setWidget(androidWidgetId, Constants.SPECIAL_WIDGET_ID, views, true);

        if (widgetId != -1)
        {
            synchronized (needUpdateWidgets)
            {
                needUpdateWidgets.add(widgetId);
            }
        }
    }

    public static void addMethodCall(DynamicView view, Object... args)
    {
        String method = (String)args[0];
        view.methodCalls.add(new RemoteMethodCall(method, false, Constants.getSetterMethod(view.type, method), args));
    }

    public void setSpecificErrorWidget(int androidWidgetId, int widgetId, Throwable error)
    {
        Log.d("APPY", "setting error widget for " + widgetId + " android: " + androidWidgetId);

        String path = null;
        if (widgetId > 0 && updateListener != null)
        {
            try
            {
                path = updateListener.onError(widgetId, Stacktrace.stackTraceString(error));
            }
            catch (Exception e)
            {
                Log.e("APPY", "Exception on onError", e);
            }
        }

        ArrayList<DynamicView> views = new ArrayList<>();

        DynamicView errorText = new DynamicView("TextView");
        addMethodCall(errorText, "setText", "Error occurred");
        addMethodCall(errorText, "setTextColor", Constants.TEXT_COLOR);
        errorText.attributes.attributes.put(Attributes.Type.TOP, attributeParse("5"));
        errorText.attributes.attributes.put(Attributes.Type.LEFT, attributeParse("5"));

        DynamicView openApp = new DynamicView("ImageView");
        addMethodCall(openApp, "setImageResource", R.mipmap.ic_launcher_foreground);
        openApp.attributes.attributes.put(Attributes.Type.BOTTOM, attributeParse("5"));
        openApp.attributes.attributes.put(Attributes.Type.LEFT, attributeParse("5"));
        openApp.attributes.attributes.put(Attributes.Type.WIDTH, attributeParse("140"));
        openApp.attributes.attributes.put(Attributes.Type.HEIGHT, attributeParse("140"));
        openApp.tag = Constants.SPECIAL_WIDGET_OPENAPP + "";

        views.add(errorText);
        views.add(openApp);

        if (widgetId > 0)
        {
            Attributes.AttributeValue afterText = attributeParse("h(" + errorText.getId() + ")+10");

            DynamicView clear = new DynamicView("Button");
            addMethodCall(clear, "setText", "Clear");
            addMethodCall(clear, "setBackgroundResource", R.drawable.drawable_dark_btn);
            addMethodCall(clear, "setTextColor", 0xffffffff);
            addMethodCall(clear, "setTextSize", "10sp");
            clear.methodCalls.add(new RemoteMethodCall("setViewPadding", false, "setViewPadding", "16sp", "12sp", "16sp", "12sp"));
            clear.attributes.attributes.put(Attributes.Type.TOP, afterText);
            clear.attributes.attributes.put(Attributes.Type.LEFT, attributeParse("l(p)"));
            clear.tag = Constants.SPECIAL_WIDGET_CLEAR + "," + widgetId;

            DynamicView reload = new DynamicView("Button");
            addMethodCall(reload, "setText", "Reload");
            addMethodCall(reload, "setBackgroundResource", R.drawable.drawable_info_btn);
            addMethodCall(reload, "setTextColor", 0xffffffff);
            addMethodCall(reload, "setTextSize", "10sp");
            reload.methodCalls.add(new RemoteMethodCall("setViewPadding", false, "setViewPadding", "16sp", "12sp", "16sp", "12sp"));
            reload.attributes.attributes.put(Attributes.Type.TOP, afterText);
            reload.attributes.attributes.put(Attributes.Type.RIGHT, attributeParse("0"));
            reload.tag = Constants.SPECIAL_WIDGET_RELOAD + "," + widgetId;

            views.add(clear);
            views.add(reload);
        }

        DynamicView showError = new DynamicView("ImageView");
        addMethodCall(showError, "setImageResource", R.drawable.ic_action_info);
        showError.attributes.attributes.put(Attributes.Type.BOTTOM, attributeParse("5"));
        showError.attributes.attributes.put(Attributes.Type.RIGHT, attributeParse("5"));
        showError.attributes.attributes.put(Attributes.Type.WIDTH, attributeParse("140"));
        showError.attributes.attributes.put(Attributes.Type.HEIGHT, attributeParse("140"));
        showError.tag = Constants.SPECIAL_WIDGET_SHOWERROR + "," + (path == null ? "0," : (path.length() + "," + path)) + "," + Stacktrace.stackTraceString(error);
        views.add(showError);

        setWidget(androidWidgetId, Constants.SPECIAL_WIDGET_ID, views, false);

        synchronized (needUpdateWidgets)
        {
            needUpdateWidgets.add(widgetId);
        }
    }

    public void setAllWidgets(boolean error)
    {
        for (int id : requestAndroidWidgets().keySet())
        {
            if (error)
            {
                setSpecificErrorWidget(id, 0, null);
            }
            else
            {
                setSpecificLoadingWidget(id, -1);
            }
        }
    }

    public static void deleteDir(File file)
    {
        File[] contents = file.listFiles();
        if (contents != null)
        {
            for (File f : contents)
            {
                deleteDir(f);
            }
        }
        file.delete();
    }

    public void unpackExamples(boolean force)
    {
        String exampleDir = new File(getFilesDir(), "examples").getAbsolutePath();
        if (force || !new File(exampleDir).exists())
        {
            try
            {
                Log.d("APPY", "unpacking examples");
                new File(exampleDir).mkdir();
                untar(getAssets().open("examples.targz"), exampleDir);
                Log.d("APPY", "done unpacking examples");
            }
            catch (IOException e)
            {
                Log.e("APPY", "Error on unpackExamples", e);
            }
        }
    }

    private class PythonSetupTask extends AsyncTask<Void, Void, Void>
    {
        private boolean error = false;

        public boolean hadError()
        {
            return error;
        }

        @Override
        protected Void doInBackground(Void... param)
        {
            startupState = Constants.StartupState.RUNNING;
            callStatusChange(true);

            error = false;
            String pythonHome = new File(getFilesDir(), "python").getAbsolutePath();
            String pythonLib = new File(pythonHome, "/lib/libpython3.12.so").getAbsolutePath(); //must be without
            String cacheDir = getPreferredCacheDir();

            try
            {
                if (getPythonUnpacked() != PYTHON_VERSION)
                {
                    Log.d("APPY", "python version mismatch: " + getPythonUnpacked() + ", " + PYTHON_VERSION);
                    deleteDir(new File(pythonHome));
                    Log.d("APPY", "unpacking python");
                    untar(getAssets().open("python.targz"), pythonHome);
                    Log.d("APPY", "done unpacking python");
                    setPythonUnpacked(PYTHON_VERSION);
                }
                else
                {
                    Log.d("APPY", "python already unpacked");
                }

                unpackExamples(false);

                copyAsset(getAssets().open("main.py"), new File(cacheDir, "main.py"));
                copyAsset(getAssets().open("logcat.py"), new File(cacheDir, "logcat.py"));
                copyAsset(getAssets().open("appy.targz"), new File(cacheDir, "appy.tar.gz"));
                System.load(pythonLib);
                System.loadLibrary("native");
                pythonInit(pythonHome, cacheDir, pythonLib, new File(cacheDir, "main.py").getAbsolutePath(), getApplicationInfo().nativeLibraryDir, Widget.this);

                initAllPythonFiles();
            }
            catch (Exception e)
            {
                Log.e("APPY", "Exception on pythonSetup", e);
                error = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
            handler.post(() -> Widget.startService(Widget.this, new Intent(Widget.this, Widget.class)));
        }

        @Override
        protected void onPreExecute()
        {
        }

        @Override
        protected void onProgressUpdate(Void... values)
        {
        }
    }

    private class CallEventTask implements Runner<Long>
    {
        @Override
        public void run(Long... args)
        {
            callEventWidget(args[0].intValue(), args[1], args[2], args[3].intValue(), args[4] != 0);
        }
    }

    private class CallTimerTask implements Runner<Object>
    {
        @Override
        public void run(Object... args)
        {
            callTimerWidget((long) args[0], (int) args[1], (String) args[2]);
        }
    }

    private class CallUpdateTask implements Runner<Integer>
    {
        @Override
        public void run(Integer... args)
        {
            callUpdateWidget(args[0]);
        }
    }

    public void setFileLastError(String path, String lastError)
    {
        if (path == null)
        {
            synchronized (pythonFilesLock)
            {
                setPythonFileLastError(unknownPythonFile, lastError);
            }
        }
        else
        {
            PythonFile pythonFile = findPythonFile(path);
            if (pythonFile != null)
            {
                setPythonFileLastError(pythonFile, lastError);
            }
        }
    }

    private void setPythonFileLastError(PythonFile pythonFile, String lastError)
    {
        Date now = new Date();

        if (pythonFile.lastErrorDate != null && pythonFile.lastError != null && (now.getTime() - pythonFile.lastErrorDate.getTime()) <= Constants.ERROR_COALESCE_MILLI)
        {
            pythonFile.lastError += "\n\n" + lastError;
        }
        else
        {
            pythonFile.lastError = lastError;
        }

        pythonFile.lastErrorDate = new Date();
        savePythonFiles();
    }

    private class CallImportTask implements Runner<Object>
    {
        @Override
        public void run(Object... args)
        {
            PythonFile file = (PythonFile) args[0];

            String newhash = "";
            try
            {
                newhash = Utils.readAndHashFileAsString(new File(file.path), Constants.PYTHON_FILE_MAX_SIZE).second;
            }
            catch (IOException e)
            {
                Log.e("APPY", "Could not hash file: "+file.path, e);
                //let python try to import anyway
            }

            if (file.state == PythonFile.State.ACTIVE && newhash.equalsIgnoreCase(file.hash))
            {
                //file is exactly the same
                Log.d("APPY", file.path + " hash is the same (" + newhash + "), not reloading");
                return;
            }

            file.hash = newhash;

            Boolean skipRefresh = (Boolean) args[1];
            file.state = PythonFile.State.RUNNING;
            callStatusChange(false);

            try
            {
                if (updateListener != null)
                {
                    updateListener.importFile(file.path, skipRefresh);
                    file.state = PythonFile.State.ACTIVE;
                }
            }
            catch (Exception e)
            {
                setPythonFileLastError(file, Stacktrace.stackTraceString(e));
                Log.e("APPY", "Exception on import task", e);
            }

            if (file.state == PythonFile.State.RUNNING)
            {
                file.state = PythonFile.State.FAILED;
            }
            callStatusChange(false);
            savePythonFiles();
        }
    }

    private class DeleteWidgetTask implements Runner<Integer>
    {
        @Override
        public void run(Integer... args)
        {
            int widget = args[0];
            Log.d("APPY", "deleting " + widget);
            widgets.remove(widget);

            cancelWidgetTimers(widget);
            if (updateListener != null)
            {
                updateListener.onDelete(widget);
            }

            if (args[1] != null)
            {
                deleteAndroidWidget(args[1]);
            }
            else
            {
                deleteWidgetMappings(widget);
            }
            saveWidgetMapping();
            removeWidgetFromStorage(widget);
            removeProps(widgetProps, "widget_props", widget, true);
            removeProps(widgetAppIcons, "widget_app_icons", widget, true);
        }
    }

    private class CallPostTask implements Runner<Object>
    {
        @Override
        public void run(Object... args)
        {
            callPostWidget((int) args[0], (String) args[1]);
        }
    }

    public void callPostWidget(int widgetId, final String data)
    {
        callWidgetChangingCallback(widgetId, (widgetId1, current, unused) -> updateListener.onPost(widgetId1, current, data));
    }

    private class CallConfigTask implements Runner<Object>
    {
        @Override
        public void run(Object... args)
        {
            callConfigWidget((int) args[0], (String) args[1]);
        }
    }

    public void callConfigWidget(int widgetId, final String key)
    {
        callWidgetChangingCallback(widgetId, (widgetId1, current, unused) -> updateListener.onConfig(widgetId1, current, key));
    }

    private class CallShareTask implements Runner<Object>
    {
        @Override
        public void run(Object... args)
        {
            callShareWidget((int) args[0], (String) args[1], (String) args[2], (DictObj.Dict) args[3]);
        }
    }

    public void callShareWidget(int widgetId, final String mimeType, final String text, DictObj.Dict datas)
    {
        callWidgetChangingCallback(widgetId, (widgetId1, current, unused) -> updateListener.onShare(widgetId1, current, mimeType, text, datas));
    }

    public void callTimerWidget(final long timerId, int widgetId, final String data)
    {
        Log.d("APPY", "callTimerWidget: " + widgetId + " " + timerId);

        Timer timer = activeTimers.get(timerId);
        if (timer == null || timer.equals(Timer.INVALID))
        {
            return;
        }
        if (timer.type != Constants.TIMER_REPEATING)
        {
            cancelTimer(timerId);
        }

        callWidgetChangingCallback(widgetId, (widgetId1, current, unused) -> updateListener.onTimer(timerId, widgetId1, current, data));
    }

    public interface CallbackCaller
    {
        //currentView must be readonly
        DictObj.Dict call(int widgetId, DictObj.List current, ArrayList<DynamicView> currentView);
    }

    public boolean callWidgetChangingCallback(int widgetId, CallbackCaller caller)
    {
        if (updateListener == null)
        {
            return false;
        }

        //Log.d("APPY", "widgetChangingCallback Start");

        int androidWidgetId = getAndroidWidget(widgetId);

        DictObj.List widgetDictList = null;
        ArrayList<DynamicView> widget;

        synchronized (widgetLock)
        {
            widget = widgets.get(widgetId);
            if (widget != null)
            {
                widgetDictList = DynamicView.toDictList(widget);
            }
        }

        boolean updated = false;
        DictObj.Dict result;
        try
        {
            result = caller.call(widgetId, widgetDictList, widget);
        }
        catch (Exception e)
        {
            Log.e("APPY", "Exception in python", e);
            setSpecificErrorWidget(androidWidgetId, widgetId, e);
            return true;
        }
        finally
        {
            deferredStateSave(widgetId);
        }

        DictObj.List newWidget = result.getList("views");
        if (newWidget != null)
        {
            widget = DynamicView.fromDictList(newWidget);
            widgets.put(widgetId, widget);

            updated = true;
            deferredWidgetSave(widgetId);
        }

        boolean needUpdate;
        synchronized (needUpdateWidgets)
        {
            needUpdate = needUpdateWidgets.contains(widgetId);
        }

        handler.post(() -> {
            DictObj.Dict data = new DictObj.Dict();
            if (result.hasKey("title"))
            {
                String title = result.getString("title");
                setWidgetAppTitle(widgetId, title);
                data.put("title", title);
            }
            if (result.hasKey("icons"))
            {
                DictObj.Dict pngs = result.getDict("icons");
                DictObj.Dict resizedPngs = pngs == null ? null : Utils.prepareAppIcons(pngs);
                setWidgetAppIcons(widgetId, resizedPngs);
                data.put("icons", resizedPngs);
            }

            if (data.size() > 0)
            {
                callAppPropsListener(widgetId, data);
            }

            if (result.hasKey("name"))
            {
                callWidgetChosenListener(widgetId, result.getString("name"));
            }
        });

        //if we were loading we refresh anyways
        if ((updated || needUpdate) && widget != null)
        {
            setWidget(androidWidgetId, widgetId, widget, true);
            return true;
        }

        return false;
    }

    public void callEventWidget(int eventWidgetId, final long itemId, final long collectionId, final int collectionPosition, final boolean checked)
    {
        Log.d("APPY", "got event intent: " + eventWidgetId + " " + itemId);

        callWidgetChangingCallback(eventWidgetId, (widgetId, current, currentView) -> {
            Log.d("APPY", "handling " + itemId + " in collection " + collectionId);

            if (itemId == 0 && collectionId == 0)
            {
                throw new IllegalArgumentException("handle without handle?");
            }

            DictObj.Dict itemClickResult = null;

            if (collectionId != 0)
            {
                //optimization: don't call itemclick if it isn't set, useful if everything is inside a collection (such as AdapterViewFlipper)
                DynamicView collectionView = DynamicView.findById(currentView, collectionId);
                if (collectionView != null && collectionView.tag instanceof DictObj.Dict && ((DictObj.Dict)collectionView.tag).hasKey("itemclick"))
                {
                    Log.d("APPY", "calling listener onItemClick with " + collectionId + ", " + collectionPosition + ", " + itemId + ", " + checked);

                    itemClickResult = updateListener.onItemClick(widgetId, current, collectionId, collectionPosition, itemId);
                    boolean handled = itemClickResult.getBoolean("handled", false);
                    if (handled || itemId == 0)
                    {
                        Log.d("APPY", "suppressing click on " + itemId);
                        return itemClickResult;
                    }
                }
                else
                {
                    Log.d("APPY", "skipping listener onItemClick with " + collectionId + ", " + collectionPosition + ", " + itemId + ", " + checked);
                }
            }

            DictObj.List itemClickViews = itemClickResult == null ? null : itemClickResult.getList("views");

            Log.d("APPY", "calling listener onClick");
            DictObj.Dict clickResult = updateListener.onClick(widgetId, itemClickViews != null ? itemClickViews : current, itemId, checked);
            Log.d("APPY", "called listener onClick");
            if (clickResult.get("views") != null)
            {
                return clickResult;
            }
            else
            {
                return itemClickResult; //use onItemClick
            }
        });
    }

    public void callUpdateWidget(int widgetId)
    {
        int androidWidgetId = getAndroidWidget(widgetId);

        Log.d("APPY", "update: " + androidWidgetId + " (" + widgetId + ")");

        if (updateListener == null)
        {
            setSpecificErrorWidget(androidWidgetId, widgetId, null);
            return;
        }

        boolean hasWidget = widgets.containsKey(widgetId);
        boolean isApp = getWidgetIsApp(AppWidgetManager.getInstance(this), androidWidgetId);

        Log.d("APPY", "calling listener onUpdate");

        boolean updated = callWidgetChangingCallback(widgetId, (widgetId_, current, unused) -> updateListener.onUpdate(widgetId_, current, isApp));
        if (!updated && !hasWidget)
        {
            setSpecificErrorWidget(androidWidgetId, widgetId, null);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        mIsRunning = true;
        handleStartCommand(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy()
    {
        mIsRunning = false;
        super.onDestroy();
    }

    public static boolean isRunning()
    {
        return mIsRunning;
    }

    public class LocalBinder extends Binder
    {
        Widget getService()
        {
            return Widget.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        if (intent.getBooleanExtra(Constants.LOCAL_BIND_EXTRA, false))
        {
            return mBinder;
        }
        else
        {
            return super.onBind(intent);
        }
    }

    public void setStatusListener(StatusListener listener)
    {
        statusListener = listener;
    }

    public void setAppPropsListener(AppPropsListener listener)
    {
        appPropsListener = listener;
    }

    public void setWidgetChosenListener(WidgetChosenListener listener)
    {
        widgetChosenListener = listener;
    }

    public Constants.StartupState getStartupState()
    {
        return startupState;
    }

    public void callStatusChange(final boolean startup)
    {
        handler.post(() -> {
            if (statusListener != null)
            {
                if (startup)
                {
                    statusListener.onStartupStatusChange();
                }
                else
                {
                    statusListener.onPythonFileStatusChange();
                }
            }
        });
    }

    public void callAppPropsListener(int widgetId, DictObj.Dict data)
    {
        handler.post(() -> {
            if (appPropsListener != null)
            {
                Integer androidWidgetId = widgetToAndroid.get(widgetId);
                if (androidWidgetId != null)
                {
                    appPropsListener.onAppPropsChange(widgetId, androidWidgetId, data);
                }
            }
        });
    }

    public void callWidgetChosenListener(int widgetId, String name)
    {
        handler.post(() -> {
            if (widgetChosenListener != null)
            {
                Integer androidWidgetId = widgetToAndroid.get(widgetId);
                if (androidWidgetId != null)
                {
                    widgetChosenListener.onWidgetChosen(widgetId, androidWidgetId, name);
                }
            }
        });
    }

    public void resetState()
    {
        if (updateListener != null)
        {
            updateListener.wipeStateRequest();
        }
    }

    public void resetWidgets()
    {
        Log.d("APPY", "clearing all widgets");
        for (int widget : getAllWidgets())
        {
            clearWidget(widget);
        }
    }

    private boolean pythonSetup()
    {
        Log.d("APPY", "dir: " + getApplicationInfo().nativeLibraryDir);
        if (!startedAfterSetup && pythonSetupTask.getStatus() == AsyncTask.Status.PENDING)
        {
            startupState = Constants.StartupState.IDLE;
            handler = new Handler();

            //Force android to create dirs for us
            getExternalFilesDir(null);
            getExternalMediaDirs();

            loadPythonFiles();
            loadCorrectionFactors(true);
            loadWidgets();
            loadTimers();
            loadWidgetProps();
            loadWidgetAppIcons();
            configurations.load();

            setAllWidgets(false);
            callStatusChange(true);

            pythonSetupTask.execute();

            return false;
        }

        if (pythonSetupTask.getStatus() == AsyncTask.Status.RUNNING)
        {
            setAllWidgets(false);
            return false;
        }

        if (pythonSetupTask.getStatus() == AsyncTask.Status.FINISHED && pythonSetupTask.hadError())
        {
            startupState = Constants.StartupState.ERROR;
            handler.post(() -> setAllWidgets(true));
            callStatusChange(true);
            return false;
        }

        startupState = Constants.StartupState.COMPLETED;
        callStatusChange(true);
        return true;
    }

    boolean startedAfterSetup = false;
    PythonSetupTask pythonSetupTask = new PythonSetupTask();

    public void handleStartCommand(Intent intent)
    {
        Utils.setCrashHandlerIfNeeded(Utils.getCrashPath(this, Constants.CrashIndex.JAVA_CRASH_INDEX));

        loadForeground();

        Utils.updateGlobalResources(this);

        if (!pythonSetup())
        {
            return;
        }

        boolean firstStart = false;
        if (!startedAfterSetup)
        {
            firstStart = true;
            startedAfterSetup = true;
        }

        Log.d("APPY", "startCommand");

        Intent widgetIntent = null;

        if (intent != null)
        {
            widgetIntent = intent.getParcelableExtra(Constants.WIDGET_INTENT);
        }

        if (firstStart)
        {
            HashMap<Integer, Bundle> ids = requestAndroidWidgets();
            HashSet<Integer> activeWidgetIds = new HashSet<>();
            for (Map.Entry<Integer, Bundle> id : ids.entrySet())
            {
                androidWidgetToOptions.put(id.getKey(), id.getValue());

                int widgetId = fromAndroidWidget(id.getKey(), true);
                activeWidgetIds.add(widgetId);
                synchronized (needUpdateWidgets)
                {
                    needUpdateWidgets.add(widgetId);
                }

                addTask(widgetId, new Task<>(new CallUpdateTask(), widgetId), false);
            }
            Set<Integer> allWidgetIds = getAllWidgets();
            allWidgetIds.removeAll(activeWidgetIds);
            Log.d("APPY", "deleting inactive " + allWidgetIds.size());
            for (int widgetId : allWidgetIds)
            {
                delete(widgetId);
            }
        }
        else if (intent != null && intent.getAction() != null && intent.getAction().startsWith("timer") && intent.hasExtra("widgetId") && intent.hasExtra("timer"))
        {
            long timer = intent.getLongExtra("timer", -1);
            int widgetId = intent.getIntExtra("widgetId", -1);
            Log.d("APPY", "timer fire: " + widgetId + " " + timer);
            addTask(widgetId, new Task<>(new CallTimerTask(), timer, widgetId, intent.getStringExtra("timerData")), false);
        }
        else if (widgetIntent != null)
        {
            if (AppWidgetManager.ACTION_APPWIDGET_RESTORED.equals(widgetIntent.getAction()))
            {
                int[] oldWidgets = widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS);
                int[] newWidgets = widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                for (int i = 0; i < oldWidgets.length; i++)
                {
                    updateAndroidWidget(oldWidgets[i], newWidgets[i]);
                }
                saveWidgetMapping();
            }
            else if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(widgetIntent.getAction()) && widgetIntent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
            {
                int deletedWidget = widgetIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                Integer widget = fromAndroidWidget(deletedWidget, false);
                if (widget != null)
                {
                    delete(widget, deletedWidget);
                }
                else
                {
                    deleteAndroidWidget(deletedWidget);
                    saveWidgetMapping();
                }
            }
            else if (AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED.equals(widgetIntent.getAction()) && widgetIntent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
            {
                Log.d("APPY", "options changed " + widgetIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1));

                int androidWidgetId = widgetIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);

                ArrayList<String> changedKeys;

                synchronized (androidWidgetToOptions)
                {
                    Bundle newOptions = widgetIntent.getBundleExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS);
                    changedKeys = Utils.bundleDiff(androidWidgetToOptions.get(androidWidgetId), newOptions);
                    androidWidgetToOptions.put(androidWidgetId, newOptions);
                }

                boolean knownChanges = true;
                for (String changed : changedKeys)
                {
                    //add exceptions here
                    knownChanges = false;
                }

                if (!knownChanges)
                {
                    int widgetId = fromAndroidWidget(androidWidgetId, true);
                    synchronized (needUpdateWidgets)
                    {
                        needUpdateWidgets.add(widgetId);
                    }
                    addTask(widgetId, new Task<>(new CallUpdateTask(), widgetId), false);
                }
                else
                {
                    Log.d("APPY", "skipping option changed update on " + androidWidgetId);
                }
            }
            else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(widgetIntent.getAction()))
            {
                if (widgetIntent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS))
                {
                    //cache
                    Set<Integer> allAndroidWidgets = null;

                    for (int androidWidgetId : widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS))
                    {
                        Integer widgetId = fromAndroidWidget(androidWidgetId, false);
                        if (widgetId == null)
                        {
                            //request all android widget ids to make sure it's real before creating a new mapping
                            if (allAndroidWidgets == null)
                            {
                                allAndroidWidgets = requestAndroidWidgets().keySet();
                            }
                            if (allAndroidWidgets.contains(androidWidgetId))
                            {
                                //real
                                widgetId = fromAndroidWidget(androidWidgetId, true);
                            }
                            else
                            {
                                Log.d("APPY", "skipping new widget " + androidWidgetId);
                            }
                        }

                        if (widgetId != null)
                        {
                            Log.d("APPY", "got update " + androidWidgetId);
                            addTask(widgetId, new Task<>(new CallUpdateTask(), widgetId), false);
                        }
                    }
                }
                else if (widgetIntent.hasExtra(Constants.WIDGET_ID_EXTRA))
                {
                    int eventWidgetId = widgetIntent.getIntExtra(Constants.WIDGET_ID_EXTRA, -1);

                    if (eventWidgetId == Constants.SPECIAL_WIDGET_ID)
                    {
                        String tag = widgetIntent.getStringExtra(Constants.ITEM_TAG_EXTRA);
                        if (tag != null)
                        {
                            int index = tag.indexOf(",");
                            int command = -1;
                            String arg = null;
                            try
                            {
                                if (index == -1)
                                {
                                    command = Integer.parseInt(tag);
                                }
                                else
                                {
                                    command = Integer.parseInt(tag.substring(0, index));
                                    arg = tag.substring(index + 1);
                                }
                            }
                            catch (NumberFormatException ignored)
                            {

                            }

                            if (command != -1)
                            {
                                switch (command)
                                {
                                    case Constants.SPECIAL_WIDGET_RESTART:
                                        restart();
                                        break;
                                    case Constants.SPECIAL_WIDGET_OPENAPP:
                                        startMainActivity("Files", null);
                                        break;
                                    case Constants.SPECIAL_WIDGET_CLEAR:
                                        if (arg != null)
                                        {
                                            try
                                            {
                                                int widgetId = Integer.parseInt(arg);
                                                Log.d("APPY", "clearing " + widgetId);
                                                clearWidget(widgetId);
                                            }
                                            catch (NumberFormatException ignored)
                                            {

                                            }
                                        }
                                        break;
                                    case Constants.SPECIAL_WIDGET_RELOAD:
                                        if (arg != null)
                                        {
                                            try
                                            {
                                                int widgetId = Integer.parseInt(arg);
                                                Log.d("APPY", "reloading " + widgetId);
                                                update(widgetId);
                                            }
                                            catch (NumberFormatException ignored)
                                            {

                                            }
                                        }
                                        break;
                                    case Constants.SPECIAL_WIDGET_SHOWERROR:
                                        if (arg == null || arg.isEmpty())
                                        {
                                            Log.d("APPY", "cannot showerror, arg is empty");
                                            break;
                                        }
                                        String[] args = arg.split(",", 2);
                                        int arg1len = 0;
                                        try
                                        {
                                            arg1len = Integer.parseInt(args[0]);
                                        }
                                        catch (NumberFormatException ignored)
                                        {
                                            Log.d("APPY", "cannot showerror, " + args[0] + " is not a number");
                                            break;
                                        }

                                        if (arg1len > args[1].length() || args[1].charAt(arg1len) != ',')
                                        {
                                            Log.d("APPY", "cannot showerror, arglen (" + arg1len + ") is bad: " + args[1].length() + ", '" + args[1].substring(0, arg1len + 1) + "'");
                                            break;
                                        }

                                        String path = args[1].substring(0, arg1len);
                                        String error = args[1].substring(arg1len + 1);

                                        String title = path.isEmpty() ? "" : new File(path).getName();

                                        Log.d("APPY", "showing error of " + path);

                                        showDialogNoWait(null, title, path + "\n\n" + error, new String[]{"Close"}, new String[0], new String[0], new String[0][]);
                                        break;
                                }
                            }
                        }
                    }
                    else
                    {
                        Log.d("APPY", "calling event task: item id: " + widgetIntent.hasExtra(Constants.ITEM_ID_EXTRA) + ", collection id: " + widgetIntent.hasExtra(Constants.COLLECTION_ID_EXTRA) + " , collection item position: " + widgetIntent.hasExtra(Constants.COLLECTION_POSITION_EXTRA) + " checked: " + widgetIntent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false));
                        addTask(eventWidgetId, new Task<>(new CallEventTask(), (long) eventWidgetId, widgetIntent.getLongExtra(Constants.ITEM_ID_EXTRA, 0), widgetIntent.getLongExtra(Constants.COLLECTION_ID_EXTRA, 0), (long) widgetIntent.getIntExtra(Constants.COLLECTION_POSITION_EXTRA, -1), (long) (widgetIntent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false) ? 1 : 0)), false);
                    }
                }
            }
        }
    }

    //-----------------------------------python--------------------------------------------------------------

    public static void copyAsset(InputStream asset, File file) throws IOException
    {
        FileOutputStream fos = new FileOutputStream(file);
        BufferedOutputStream out = new BufferedOutputStream(fos);

        byte[] buffer = new byte[1024];
        int read;
        while ((read = asset.read(buffer)) != -1)
        {
            out.write(buffer, 0, read);
        }
        out.flush();
        out.close();
    }

    public static int READ_PERM = 0444;
    public static int WRITE_PERM = 0222;
    public static int EXEC_PERM = 0111;

    public static void process(TarInputStream tis, TarEntry entry, String dest) throws IOException
    {
        Log.d("APPY", "Extracting: " + entry.getName());
        int count;
        byte data[] = new byte[2048];
        File file = new File(dest, entry.getName());

        String canonicalDest = new File(dest).getCanonicalPath();

        if (!file.getCanonicalPath().startsWith(canonicalDest))
        {
            throw new IOException("traversal");
        }

        if (entry.isDirectory())
        {
            file.mkdirs();
        }
        else
        {
            file.getParentFile().mkdirs();
            file.delete();

            if (entry.getHeader().linkFlag == TarHeader.LF_SYMLINK || entry.getHeader().linkFlag == TarHeader.LF_LINK)
            {
                File target = new File(file.getParentFile(), entry.getHeader().linkName.toString());
                if (!target.getCanonicalPath().startsWith(canonicalDest))
                {
                    throw new IOException("traversal");
                }
                try
                {
                    Log.d("APPY", "creating link: " + entry.getHeader().linkName.toString() + " as " + file.getAbsolutePath());
                    if (entry.getHeader().linkFlag == TarHeader.LF_SYMLINK)
                    {
                        Os.symlink(entry.getHeader().linkName.toString(), file.getAbsolutePath());
                    }
                    else
                    {
                        Os.link(entry.getHeader().linkName.toString(), file.getAbsolutePath());
                    }
                }
                catch (ErrnoException e)
                {
                    throw new IOException(e);
                }
            }
            else if (entry.getHeader().linkFlag == TarHeader.LF_NORMAL)
            {
                FileOutputStream fos = new FileOutputStream(file);
                BufferedOutputStream out = new BufferedOutputStream(fos);
                while ((count = tis.read(data)) != -1)
                {
                    out.write(data, 0, count);
                }

                out.flush();
                out.close();
            }
            else
            {
                Log.d("APPY", "cannot create type " + entry.getHeader().linkFlag);
            }
        }

        file.setReadable((entry.getHeader().mode & READ_PERM) != 0, false);
        file.setWritable((entry.getHeader().mode & WRITE_PERM) != 0, false);
        file.setExecutable((entry.getHeader().mode & EXEC_PERM) != 0, false);
        file.setLastModified(entry.getModTime().getTime());
    }

    public static void untar(InputStream tar, String dest)
    {
        try
        {
            // Create a TarInputStream
            TarInputStream tis = new TarInputStream(new GZIPInputStream(new BufferedInputStream(tar)));
            TarEntry entry;

            while ((entry = tis.getNextEntry()) != null)
            {
                try
                {
                    process(tis, entry, dest);
                }
                catch (IOException e)
                {
                    Log.w("APPY", "exception in tar file: ", e);
                }
            }

            tis.close();
        }
        catch (IOException e)
        {
            Log.e("APPY", "tar exception", e);
        }
    }

    protected static native void pythonInit(String pythonHome, String tmpPath, String pythonLibPath, String script, String nativepath, Object arg);

    protected static native Object pythonCall(Object... args) throws Throwable;
}
