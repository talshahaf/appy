package com.appy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import androidx.core.content.ContextCompat;
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

import org.json.JSONException;
import org.json.JSONObject;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarInputStream;


public class Widget extends RemoteViewsService
{
    private final IBinder mBinder = new LocalBinder();
    private static boolean mIsRunning = false;

    public static final int PYTHON_VERSION = 31210;
    public static final int NOTIFICATION_ID = 100;

    WidgetUpdateListener updateListener = null;
    StatusListener statusListener = null;
    Handler handler;
    Constants.StartupState startupState = Constants.StartupState.IDLE;

    final Object lock = new Object();
    HashMap<Integer, TaskQueue> widgetsTasks = new HashMap<>();

    HashMap<Integer, String> widgets = new HashMap<>();
    HashMap<Integer, Integer> androidToWidget = new HashMap<>();
    HashMap<Integer, Integer> widgetToAndroid = new HashMap<>();
    HashMap<Long, Timer> activeTimers = new HashMap<>();
    HashMap<Long, PendingIntent[]> activeTimersIntents = new HashMap<>();
    HashMap<Pair<Integer, Integer>, HashMap<Integer, ListFactory>> factories = new HashMap<>();
    ArrayList<PythonFile> pythonFiles = new ArrayList<>();
    PythonFile unknownPythonFile = new PythonFile("Unknown file");
    HashSet<Integer> needUpdateWidgets = new HashSet<>();
    float widthCorrectionFactor = 1.0f;
    float heightCorrectionFactor = 1.0f;
    Configurations configurations = new Configurations(this);
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
            if(mod < 0)
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
            if(mod < 0)
            {
                mod += storage.length;
            }
            if(storage_hash[mod] != null && storage_hash[mod] == hash)
            {
                //hit
                return new Pair<>(true, (Ret)storage[mod]);
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
            catch(WidgetDestroyedException e)
            {
                Log.w("APPY", "widget "+(int)objects[0]+" deleted");
            }
            finally
            {
                doneExecuting((int) objects[0]);
            }
            return null;
        }
    }

    class Task<T>
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

    public void addTask(int widgetId, Task<?> task)
    {
        synchronized (lock)
        {
            TaskQueue queue = widgetsTasks.get(widgetId);
            if (queue == null)
            {
                queue = new TaskQueue();
                widgetsTasks.put(widgetId, queue);
            }

            task.widgetId = widgetId;
            queue.queue.add(task);
        }
        executeReadyTasks();
    }

    public void executeReadyTasks()
    {
        ArrayList<Task> toExecute = new ArrayList<>();
        synchronized (lock)
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
        synchronized (lock)
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
                throw new IllegalArgumentException("expected '(' in "+arg+" got "+arg.charAt(idx.first + 1));
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

    public boolean isCollection(String type)
    {
        return "ListView".equals(type) ||
                "GridView".equals(type) ||
                "StackView".equals(type) ||
                "AdapterViewFlipper".equals(type);
    }

    public boolean clickShouldUseViewId(String type)
    {
        return "CheckBox".equals(type) ||
                "Switch".equals(type) ||
                "Button".equals(type) ||
                "ImageButton".equals(type);
    }

    public boolean isCheckableInsteadOfClickable(String type)
    {
        return "CheckBox".equals(type) ||
                "Switch".equals(type);
    }

    public boolean allowMethodCallInMeasurement(RemoteMethodCall methodCall)
    {
        // setCompoundButtonChecked triggers animation which we cannot do in our measurement thread
        if (methodCall.getIdentifier().equals("setCompoundButtonChecked"))
        {
            return false;
        }
        return true;
    }

    public ListFactory getFactory(Context context, int widgetId, int xml, int view, String list)
    {
        Pair<Integer, Integer> key = new Pair<>(widgetId, xml);
        HashMap<Integer, ListFactory> inWidgetXml = factories.get(key);
        if (inWidgetXml == null)
        {
            inWidgetXml = new HashMap<>();
            factories.put(key, inWidgetXml);
        }

        ListFactory foundFactory = inWidgetXml.get(view);
        if (foundFactory == null)
        {
            foundFactory = new ListFactory(context, widgetId, list);
            inWidgetXml.put(view, foundFactory);
        }
        else
        {
            foundFactory.reload(list);
        }

        return foundFactory;
    }

    class ListFactory implements RemoteViewsFactory
    {
        final Object lock = new Object();
        int widgetId;
        Context context;
        DynamicView list;

        public ListFactory(Context context, int widgetId, String list)
        {
            this.context = context;
            this.widgetId = widgetId;
            reload(list);
        }

        public void reload(String list)
        {
            synchronized (lock)
            {
                this.list = DynamicView.fromJSON(list);
            }
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
            return list.children.size();
        }

        @Override
        public RemoteViews getViewAt(int position)
        {
            try
            {
                synchronized (lock)
                {
                    if (position < list.children.size())
                    {
                        ArrayList<DynamicView> dynamicViewCopy = DynamicView.fromJSONArray(DynamicView.toJSONString(list.children.get(position)));
                        RemoteViews remoteView = resolveDimensions(context, widgetId, dynamicViewCopy, Constants.collection_layout_type.get(list.type), new Object[]{list.getId(), position}, list.actualWidth, list.actualHeight).first;
                        Intent fillIntent = new Intent(context, WidgetReceiver.class);
                        if (list.children.get(position).size() == 1)
                        {
                            fillIntent.putExtra(Constants.ITEM_ID_EXTRA, list.children.get(position).get(0).getId());
                        }
                        fillIntent.putExtra(Constants.COLLECTION_POSITION_EXTRA, position);

                        int elementId = clickShouldUseViewId(list.children.get(position).get(0).type) ? R.id.e0 : R.id.collection_root;

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        {
                            RemoteViews.RemoteResponse responseIntent = RemoteViews.RemoteResponse.fromFillInIntent(fillIntent);

                            if (isCheckableInsteadOfClickable(list.children.get(position).get(0).type))
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
                }
            }
            catch(Exception e)
            {
                Log.e("APPY", "Exception on ListFactory", e);
                try
                {
                    setSpecificErrorWidget(getAndroidWidget(widgetId), widgetId, e);
                }
                catch(WidgetDestroyedException e2)
                {
                    Log.e("APPY", "Exception on ListFactory", e2);
                }
            }
            return new RemoteViews(context.getPackageName(), R.layout.root);
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

        float width = convertUnit(bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), TypedValue.COMPLEX_UNIT_DIP, TypedValue.COMPLEX_UNIT_PX);
        float height = convertUnit(bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT), TypedValue.COMPLEX_UNIT_DIP, TypedValue.COMPLEX_UNIT_PX);

        //only works on portrait
        return new int[]{
                (int) (widthCorrectionFactor * width),
                (int) (heightCorrectionFactor * height)
        };
    }

    public Pair<Integer, HashMap<String, ArrayList<Integer>>> selectRootView(ArrayList<String> collections)
    {
        if (collections.size() > 2)
        {
            throw new IllegalArgumentException("more than 2 collections are not supported");
        }

        if (collections.isEmpty())
        {
            return new Pair<>(R.layout.root, new HashMap<String, ArrayList<Integer>>());
        }

        Collections.sort(collections, new Comparator<String>()
        {
            @Override
            public int compare(String s1, String s2)
            {
                return s1.compareToIgnoreCase(s2);
            }
        });

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

    public Pair<Integer, Integer> getCollectionElementIds(int n)
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

    public Integer advanceElementsId(int elements_id)
    {
        switch (elements_id)
        {
            case R.id.elements0:
                return R.id.elements1;
            case R.id.elements1:
                return R.id.elements2;
            case R.id.elements2:
                return null;
            default:
                return elements_id; //for collection_element_vertical and others
        }
    }

    public FunctionCache<Pair<String, HashMap<String, String>>, Integer> typeToLayoutCache = new FunctionCache<>(10);
    public int typeToLayout(String type, HashMap<String, String> selectors)
    {
        if(!Constants.element_map.containsKey(type))
        {
            throw new IllegalArgumentException("unknown type " + type);
        }

        Pair<Boolean, Integer> cache = typeToLayoutCache.get(new Pair<>(type, selectors));
        if(cache.first)
        {
            return cache.second;
        }

        int mostGeneralResource = 0;
        int mostGeneralFit = 0;
        for(Constants.SelectorElement selector : Constants.element_map.get(type))
        {
            int fit = selector.fit(selectors);
            if(fit != 0 && (mostGeneralFit == 0 || fit < mostGeneralFit))
            {
                mostGeneralFit = fit;
                mostGeneralResource = selector.res;
            }
        }
        if(mostGeneralFit == 0)
        {
            throw new IllegalArgumentException("unknown resource for selectors: " + selectors + " and type " + type);
        }

        typeToLayoutCache.set(new Pair<>(type, selectors), mostGeneralResource);
        return mostGeneralResource;
    }

    public Pair<RemoteViews, HashSet<Integer>> generate(Context context, int widgetId, ArrayList<DynamicView> dynamicList, boolean forMeasurement, Constants.CollectionLayout collectionLayout, Object[] collectionExtraData) throws InvocationTargetException, IllegalAccessException
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
            if(forMeasurement)
            {
                root_xml = R.layout.collection_element_measurements;
            }
            else
            {
                switch(collectionLayout)
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

        RemoteViews rootView = new RemoteViews(context.getPackageName(), root_xml);

        if(!inCollection)
        {
            Integer elements_id_it = R.id.elements0;
            while(elements_id_it != null)
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
                if(elements_id_opt == null)
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
                remoteView = new RemoteViews(context.getPackageName(), layout.xml_id);
            }

            for (RemoteMethodCall methodCall : layout.methodCalls)
            {
//                Log.d("APPY", "calling method "+methodCall.toString());
                if (!forMeasurement || allowMethodCallInMeasurement(methodCall))
                {
                    methodCall.call(remoteView, methodCall.isParentCall() ? layout.container_id : layout.view_id);
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
                clickIntent = new Intent(context, WidgetReceiver.class);
                clickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                clickIntent.putExtra(Constants.WIDGET_ID_EXTRA, widgetId);
            }

            if (isCollection(layout.type))
            {
                collection_views.add(layout.view_id);
                if (!forMeasurement)
                {
                    clickIntent.putExtra(Constants.COLLECTION_ITEM_ID_EXTRA, layout.getId());
                    //request code has to be unique at any given time
                    remoteView.setPendingIntentTemplate(layout.view_id, PendingIntent.getBroadcast(context, widgetId + ((int)layout.getId() << 10), clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));

                    //prepare factory
                    getFactory(context, widgetId, layout.xml_id, layout.view_id, layout.toJSON());

                    Intent listintent = new Intent(context, Widget.class);
                    listintent.putExtra(Constants.WIDGET_ID_EXTRA, widgetId);
                    listintent.putExtra(Constants.LIST_SERIALIZED_EXTRA, Gzip.compress(layout.toJSON()));
                    listintent.putExtra(Constants.XML_ID_EXTRA, layout.xml_id);
                    listintent.putExtra(Constants.VIEW_ID_EXTRA, layout.view_id);
                    listintent.setData(Uri.parse(listintent.toUri(Intent.URI_INTENT_SCHEME)));
                    remoteView.setRemoteAdapter(layout.view_id, listintent);

                    //Log.d("APPY", "set remote adapter on " + layout.view_id+", "+layout.xml_id+" in dynamic "+layout.getId());
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
                        if (layout.tag instanceof Integer) {
                            clickIntent.putExtra(Constants.ITEM_TAG_EXTRA, (Integer) layout.tag);
                        } else if (layout.tag instanceof Long) {
                            clickIntent.putExtra(Constants.ITEM_TAG_EXTRA, (Long) layout.tag);
                        }

                        if (inCollection) {
                            clickIntent.putExtra(Constants.COLLECTION_ITEM_ID_EXTRA, (long) collectionExtraData[0]);
                            clickIntent.putExtra(Constants.COLLECTION_POSITION_EXTRA, (int) collectionExtraData[1]);
                        }

                        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, widgetId + ((int) layout.getId() << 10), clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

                        if (isCheckableInsteadOfClickable(layout.type) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            remoteView.setOnCheckedChangeResponse(layout.view_id, RemoteViews.RemoteResponse.fromPendingIntent(pendingIntent));
                        } else {
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

    public void resolveAxis(DynamicView view, Attributes.Type startType, Attributes.Type sizeType, Attributes.Type endType, double defaultStart, double defaultSize)
    {
        Attributes.AttributeValue start = view.attributes.attributes.get(startType);
        Attributes.AttributeValue size = view.attributes.attributes.get(sizeType);
        Attributes.AttributeValue end = view.attributes.attributes.get(endType);

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
        else if(start.hasConstraints() &&
                size.hasConstraints() &&
                end.hasConstraints())
        {
            //ignore right
            third = end;
            others[0] = startType;
            others[1] = sizeType;
        }
        //if 1 constrained
        else if( start.hasConstraints() &&
                !size.hasConstraints() &&
                !end.hasConstraints())
        {
            //if only left, set width
            size.resolvedValue = defaultSize;

            third = end;
            others[0] = startType;
            others[1] = sizeType;
        }
        else if(!start.hasConstraints() &&
                 size.hasConstraints() &&
                !end.hasConstraints())
        {
            //if only width, set left
            start.resolvedValue = defaultStart;

            third = end;
            others[0] = startType;
            others[1] = sizeType;
        }
        else if(!start.hasConstraints() &&
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
        else if(!start.hasConstraints() &&
                 size.hasConstraints() &&
                 end.hasConstraints())
        {
            third = start;
            others[0] = endType;
            others[1] = sizeType;
        }
        else if( start.hasConstraints() &&
                !size.hasConstraints() &&
                 end.hasConstraints())
        {
            third = size;
            others[0] = startType;
            others[1] = endType;
        }
        else if( start.hasConstraints() &&
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

        //here we have 2 out of 3 set, create the reference for the third - third = widget.size - others
        third.function = Attributes.AttributeValue.Function.IDENTITY;
        third.arguments = new ArrayList<>();
        ArrayList<Attributes.AttributeValue.Reference> refs = new ArrayList<>();
        refs.add(new Attributes.AttributeValue.Reference(-1, sizeType, 1));
        refs.add(new Attributes.AttributeValue.Reference(view.getId(), others[0], -1));
        refs.add(new Attributes.AttributeValue.Reference(view.getId(), others[1], -1));
        third.arguments.add(new Pair<>(refs, (double)0));
    }

    public int applyIteration(ArrayList<DynamicView> dynamicList, Attributes rootAttributes)
    {
        int resolved = 0;
        for (DynamicView dynamicView : dynamicList)
        {
            for (Attributes.AttributeValue attributeValue : dynamicView.attributes.unresolved())
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
                    attributeValue.resolvedValue = applyFunction(attributeValue.function, results);
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
            for(Attributes.Type type : dimensionAttributes)
            {
                Attributes.AttributeValue attr = dynamicView.attributes.attributes.get(type);
                if(attr.isResolved())
                {
                    length += attr.resolvedValue;
                }
            }

            if(max < length)
            {
                max = length;
            }
        }
        return max;
    }

    public Pair<RemoteViews, HashSet<Integer>> resolveDimensions(Context context, int widgetId, ArrayList<DynamicView> dynamicList, Constants.CollectionLayout collectionLayout, Object[] collectionExtras, int widthLimit, int heightLimit) throws InvocationTargetException, IllegalAccessException
    {
        RemoteViews remote = generate(context, widgetId, dynamicList, true, collectionLayout, collectionExtras).first;
        RelativeLayout layout = new RelativeLayout(this);
        View inflated = remote.apply(context, layout);
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
                if(view == null)
                {
                    continue;
                }
                params = (RelativeLayout.LayoutParams) view.getLayoutParams();
                params.width = RelativeLayout.LayoutParams.WRAP_CONTENT;
                params.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                view.setLayoutParams(params);
            }
        }

        inflated.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        //set all back to MATCH_PARENT and resolve all trivials
        for (int k = 0; k < supergroup.getChildCount(); k++)
        {
            ViewGroup group = (ViewGroup) supergroup.getChildAt(k);
            for (int i = 0; i < group.getChildCount(); i++)
            {
                View view = ((ViewGroup) group.getChildAt(i)).getChildAt(0); //each leaf is inside RelativeLayout is inside elements or collections
                if(view == null || view.getContentDescription().toString() == null || view.getContentDescription().toString().isEmpty())
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

                resolveAxis(dynamicView, Attributes.Type.LEFT, Attributes.Type.WIDTH, Attributes.Type.RIGHT, 0, viewWidth);

                resolveAxis(dynamicView, Attributes.Type.TOP, Attributes.Type.HEIGHT, Attributes.Type.BOTTOM, 0, viewHeight);

                params = (RelativeLayout.LayoutParams) view.getLayoutParams();
                params.width = RelativeLayout.LayoutParams.MATCH_PARENT;
                params.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                view.setLayoutParams(params);
            }
        }

        // Log.d("APPY", "Attributes "+DynamicView.toJSONString(dynamicList));

        //resolve anything not depending on widget width/height
        while (applyIteration(dynamicList, rootAttributes) != 0);
        //resolve width and height
        double specialWidth = 0;
        double specialHeight = 0;
        rootAttributes.attributes.get(Attributes.Type.WIDTH).resolvedValue = (double)widthLimit;
        rootAttributes.attributes.get(Attributes.Type.HEIGHT).resolvedValue = (double)heightLimit;
        if(collectionLayout == Constants.CollectionLayout.HORIZONTAL || collectionLayout == Constants.CollectionLayout.BOTH)
        {
            specialWidth = getMaxDimension(dynamicList, new Attributes.Type[] {Attributes.Type.LEFT, Attributes.Type.WIDTH, Attributes.Type.RIGHT});
            rootAttributes.attributes.get(Attributes.Type.WIDTH).resolvedValue = specialWidth;
        }
        if (collectionLayout == Constants.CollectionLayout.VERTICAL || collectionLayout == Constants.CollectionLayout.BOTH)
        {
            specialHeight = getMaxDimension(dynamicList, new Attributes.Type[]{Attributes.Type.TOP, Attributes.Type.HEIGHT, Attributes.Type.BOTTOM});
            rootAttributes.attributes.get(Attributes.Type.HEIGHT).resolvedValue = specialHeight;
        }

        //resolve everything else
        while (applyIteration(dynamicList, rootAttributes) != 0);

        for (DynamicView dynamicView : dynamicList)
        {
            ArrayList<Attributes.AttributeValue> unresolved = dynamicView.attributes.unresolved();
            if (!unresolved.isEmpty())
            {
                throw new IllegalArgumentException("unresolved after iterations, maybe circular? example: " + unresolved.get(0) + " from: " + dynamicView.getId() + ", " + dynamicView.type + ": " + dynamicView.attributes);
            }
        }

        //apply
        for (DynamicView dynamicView : dynamicList)
        {
            Pair<Integer, Integer> hor = new Pair<>(dynamicView.attributes.attributes.get(Attributes.Type.LEFT).resolvedValue.intValue(),
                    dynamicView.attributes.attributes.get(Attributes.Type.RIGHT).resolvedValue.intValue());
            Pair<Integer, Integer> ver = new Pair<>(dynamicView.attributes.attributes.get(Attributes.Type.TOP).resolvedValue.intValue(),
                    dynamicView.attributes.attributes.get(Attributes.Type.BOTTOM).resolvedValue.intValue());
            Pair<Integer, Integer> dims = new Pair<>(dynamicView.attributes.attributes.get(Attributes.Type.WIDTH).resolvedValue.intValue(),
                    dynamicView.attributes.attributes.get(Attributes.Type.HEIGHT).resolvedValue.intValue());

            // saving each collection view size in it so that collection children will know their parent's size
            if(collectionLayout == Constants.CollectionLayout.NOT_COLLECTION)
            {
                dynamicView.actualWidth = dims.first;
                dynamicView.actualHeight = dims.second;
            }

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
                                            "setViewLayoutWidth", hor.first + dims.first, TypedValue.COMPLEX_UNIT_PX));
                dynamicView.methodCalls.add(new RemoteMethodCall("setViewLayoutHeight", true,
                                            "setViewLayoutHeight", ver.first + dims.second, TypedValue.COMPLEX_UNIT_PX));
                dynamicView.methodCalls.add(new RemoteMethodCall("setViewPadding", true, "setViewPadding",
                        hor.first,
                        ver.first,
                        0,
                        0));
            }
            else
            {
                //Old method sucks
                dynamicView.methodCalls.add(new RemoteMethodCall("setViewPadding", true, "setViewPadding",
                        hor.first,
                        ver.first,
                        hor.second,
                        ver.second));
            }
        }

        Pair<RemoteViews, HashSet<Integer>> views = generate(context, widgetId, dynamicList, false, collectionLayout, collectionExtras);
        //only collection elements has size_filler view
        if(collectionLayout != Constants.CollectionLayout.NOT_COLLECTION)
        {
//            Log.d("APPY", "special limits: "+specialWidth+" "+specialHeight);

            if(collectionLayout == Constants.CollectionLayout.HORIZONTAL || collectionLayout == Constants.CollectionLayout.BOTH)
            {
                //fill the size width filler (specialWidth must not be 0)
                views.first.setViewPadding(R.id.size_w_filler, (int) (specialWidth / 2), 0, (int) (specialWidth / 2), 0);
            }
            if(collectionLayout == Constants.CollectionLayout.VERTICAL || collectionLayout == Constants.CollectionLayout.BOTH)
            {
                //fill the size height filler (specialHeight must not be 0)
                views.first.setViewPadding(R.id.size_h_filler, 0, (int) (specialHeight / 2), 0, (int) (specialHeight / 2));
            }
        }
        return views;
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent)
    {
        int widgetId = intent.getIntExtra(Constants.WIDGET_ID_EXTRA, -1);
        int xmlId = intent.getIntExtra(Constants.XML_ID_EXTRA, 0);
        int viewId = intent.getIntExtra(Constants.VIEW_ID_EXTRA, 0);
        String list = Gzip.decompress(intent.getByteArrayExtra(Constants.LIST_SERIALIZED_EXTRA));
        return getFactory(this, widgetId, xmlId, viewId, list);
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
        if(androidWidget == null)
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
        synchronized (lock)
        {
            widgets.addAll(widgetToAndroid.keySet());
        }
        return widgets;
    }

    public Integer fromAndroidWidget(int androidWidget, boolean create)
    {
        synchronized (lock)
        {
            Integer widget = androidToWidget.get(androidWidget);
            if (widget == null && create)
            {
                int ret = addWidget(androidWidget);
                saveWidgetMapping();
                return ret;
            }
            return widget;
        }
    }

    public int addWidget(int androidWidget)
    {
        synchronized (lock)
        {
            if (androidToWidget.containsKey(androidWidget))
            {
                throw new IllegalArgumentException("already know this widget id");
            }

            int widget = newWidgetId();

            androidToWidget.put(androidWidget, widget);
            widgetToAndroid.put(widget, androidWidget);
            return widget;
        }
    }

    public void updateAndroidWidget(int oldAndroidWidget, int newAndroidWidget)
    {
        synchronized (lock)
        {
            Integer widget = fromAndroidWidget(oldAndroidWidget, false);
            if (widget == null)
            {
                //throw new IllegalArgumentException("widget does not exists");
                return;
            }
            androidToWidget.remove(oldAndroidWidget);
            widgetToAndroid.put(widget, newAndroidWidget);
            androidToWidget.put(newAndroidWidget, widget);
        }
    }

    public void deleteWidgetMappings(int widget)
    {
        synchronized (lock)
        {
            widgetToAndroid.remove(widget);
            HashSet<Integer> toremove = new HashSet<>();
            for(Map.Entry<Integer, Integer> entry : androidToWidget.entrySet())
            {
                if(entry.getValue() == widget)
                {
                    toremove.add(entry.getKey());
                }
            }
            for(Integer androidWidgetId : toremove)
            {
                androidToWidget.remove(androidWidgetId);
            }
        }
    }

    public void deleteAndroidWidget(int androidWidget)
    {
        synchronized (lock)
        {
            Integer widget = fromAndroidWidget(androidWidget, false);
            if (widget == null)
            {
                //throw new IllegalArgumentException("widget does not exists");
                return;
            }
            widgetToAndroid.remove(widget);
            androidToWidget.remove(androidWidget);
        }
    }

    public void putWidget(int widgetId, String json)
    {
        synchronized (lock)
        {
            widgets.put(widgetId, DynamicView.toJSONString(DynamicView.fromJSONArray(json))); //assign ids
        }
        saveWidgets();
    }

    public void loadWidgets()
    {
        try
        {
            SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
            String widgetsString = sharedPref.getString("widgets", null);
            if (widgetsString != null)
            {
                synchronized (lock)
                {
                    widgets = new MapSerialize<Integer, String>().deserialize(widgetsString, new MapSerialize.IntKey(), new MapSerialize.StringValue());
                }
            }
            String widgetToAndroidString = sharedPref.getString("widgetToAndroid", null);
            if (widgetToAndroidString != null)
            {
                synchronized (lock)
                {
                    widgetToAndroid = new MapSerialize<Integer, Integer>().deserialize(widgetToAndroidString, new MapSerialize.IntKey(), new MapSerialize.IntValue());
                    androidToWidget = new HashMap<>();
                    for (Map.Entry<Integer, Integer> entry : widgetToAndroid.entrySet())
                    {
                        androidToWidget.put(entry.getValue(), entry.getKey());
                    }
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
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        synchronized (lock)
        {
            editor.putString("widgetToAndroid", new MapSerialize<Integer, Integer>().serialize(widgetToAndroid, new MapSerialize.IntKey(), new MapSerialize.IntValue()));
        }
        editor.apply();
    }

    public void saveWidgets()
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        synchronized (lock)
        {
            editor.putString("widgets", new MapSerialize<Integer, String>().serialize(widgets, new MapSerialize.IntKey(), new MapSerialize.StringValue()));
        }
        editor.apply();
    }

    public float[] getCorrectionFactors()
    {
        return new float[]{widthCorrectionFactor, heightCorrectionFactor};
    }

    public void setCorrectionFactors(float widthCorrection, float heightCorrection)
    {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
        editor.putString("width_correction", widthCorrection+"");
        editor.putString("height_correction", heightCorrection+"");
        editor.apply();

        loadCorrectionFactors(false);
    }

    public void loadCorrectionFactors(boolean initing)
    {
        float widthCorrection = 1.0f;
        float heightCorrection = 1.0f;

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

            if (widthCorrection <= 0 || widthCorrection > 3)
            {
                widthCorrection = 1;
            }
            if (heightCorrection <= 0 || heightCorrection > 3)
            {
                heightCorrection = 1;
            }

        }
        catch(Exception e)
        {
            Log.e("APPY", "Exception on loadCorrectionFactors", e);
        }

        widthCorrectionFactor = widthCorrection;
        heightCorrectionFactor = heightCorrection;

        Log.d("APPY", "new correction factors: " + widthCorrectionFactor + ", " + heightCorrectionFactor);
        if(!initing)
        {
            updateAll();
        }
    }

    public static boolean needForeground()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static boolean getForeground(Context context)
    {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPref.getBoolean("foreground_service", needForeground());
    }

    public static void startService(Context context, Intent intent)
    {
        // this has nothing to do with the actual foregroundness of the service, but startService will fail if needForeground().
        if(needForeground() && getForeground(context))
        {
            context.startForegroundService(intent);
        }
        else
        {
            context.startService(intent);
        }
    }

    public void loadForeground()
    {
        boolean foreground = getForeground(this);
        if(foreground)
        {
            Log.d("APPY", "foreground is on");

            Notification.Builder builder;
            if(needForeground())
            {
                final String CHANNEL = "Service notification";
                NotificationChannel channel_none = new NotificationChannel(CHANNEL, CHANNEL, NotificationManager.IMPORTANCE_NONE);
                channel_none.setSound(null, null);
                channel_none.enableVibration(false);
                channel_none.setShowBadge(false);
                ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel_none);
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

            Notification notification = builder.setContentTitle ("Appy")
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
                    startForeground(NOTIFICATION_ID, notification,  ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
                }
                else
                {
                    startForeground(NOTIFICATION_ID, notification);
                }
            }
            catch (RuntimeException e)
            {
                Toast.makeText(this, "Could not start Appy because it is lacking permissions.", Toast.LENGTH_LONG);
            }
        }
        else
        {
            Log.d("APPY", "foreground is off");

            stopForeground(true);
        }
    }

    public long generateTimerId()
    {
        synchronized (lock)
        {
            // long newId = 1;
            // if (!activeTimers.isEmpty())
            // {
                // newId = Collections.max(activeTimers.keySet()) + 1;
            // }
            long newId = new Random().nextLong();
            activeTimers.put(newId, null); //save room
            return newId;
        }
    }

    static class Timer
    {
        public Timer(int widgetId, long since, long millis, int type, String data)
        {
            this.widgetId = widgetId;
            this.since = since;
            this.millis = millis;
            this.type = type;
            this.data = data;
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

    static class TimerValue implements MapSerialize.Converter<Timer, Object>
    {

        @Override
        public Object convert(Timer timer)
        {
            JSONObject obj = new JSONObject();
            try
            {
                obj.put("widgetId", timer.widgetId);
                obj.put("since", timer.since);
                obj.put("millis", timer.millis);
                obj.put("type", timer.type);
                obj.put("data", timer.data);
                return obj;
            }
            catch (JSONException e)
            {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public Timer invert(Object o)
        {
            JSONObject obj = (JSONObject) o;
            try
            {
                return new Timer(obj.getInt("widgetId"),
                        obj.has("since") ? obj.getInt("since") : 0,
                        obj.getInt("millis"),
                        obj.getInt("type"),
                        obj.getString("data"));
            }
            catch (JSONException e)
            {
                throw new IllegalArgumentException(e);
            }
        }
    }

    public void cancelTimer(long timerId)
    {
        cancelTimer(timerId, true);
    }

    public void cancelTimer(long timerId, boolean save)
    {
        PendingIntent[] pendingIntent;
        synchronized (lock)
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
        HashMap<Long, Timer> activeTimersCopy;
        synchronized (lock)
        {
            activeTimersCopy = new HashMap<>(activeTimers);
        }
        HashSet<Long> toCancel = new HashSet<>();
        for (Map.Entry<Long, Timer> timer : activeTimersCopy.entrySet())
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
        synchronized (lock)
        {
            toCancel.addAll(activeTimers.keySet());
        }
        for (long timer : toCancel)
        {
            cancelTimer(timer, false);
        }
        saveTimers();
    }

    public static abstract class ArgRunnable implements Runnable
    {
        Object[] args;

        ArgRunnable(Object... args)
        {
            this.args = args;
        }

        public abstract void run();
    }

    public void setPost(int widgetId, String data)
    {
        addTask(widgetId, new Task<>(new CallPostTask(), widgetId, data));
    }

    public void configurationUpdate(String widget, String key)
    {
        if (updateListener != null)
        {
            int[] widgetIds = updateListener.findWidgetsByMame(widget);
            for (int widgetId : widgetIds)
            {
                addTask(widgetId, new Task<>(new CallConfigTask(), widgetId, key));
            }
        }
    }

    public void deferredStateDump()
    {
        addTask(Constants.IMPORT_TASK_QUEUE, new Task<>(new StateDumpTask()));
    }

    public long setTimer(long millis, int type, int widgetId, String data)
    {
        return setTimer(System.currentTimeMillis(), millis, type, widgetId, data, -1);
    }

    long timeToNext(long since, long interval)
    {
        return interval - ((System.currentTimeMillis() - since) % interval);
    }

    public long setTimer(long since, long millis, int type, int widgetId, String data, long timerId)
    {
        long now = System.currentTimeMillis();
        if (type == Constants.TIMER_RELATIVE)
        {
            millis += now;
            type = Constants.TIMER_ABSOLUTE;
        }

        if (timerId == -1)
        {
            timerId = generateTimerId();
        }
        Intent timerIntent = new Intent(Widget.this, getClass());
        timerIntent.setAction("timer" + timerId); //make it unique for cancel
        timerIntent.putExtra("widgetId", widgetId);
        timerIntent.putExtra("timer", timerId);
        timerIntent.putExtra("timerData", Gzip.compress(data));

        //trick to insert a reference to the hashmap to be populated later
        PendingIntent[] pendingIntent = new PendingIntent[1];

        synchronized (lock)
        {
            activeTimers.put(timerId, new Timer(widgetId, since, millis, type, data));
            activeTimersIntents.put(timerId, pendingIntent);
        }
        saveTimers();

        if (type == Constants.TIMER_REPEATING && millis <= Constants.TIMER_MAX_HANDLER)
        {
            Log.d("APPY", "setting short time timer");
            handler.post(new ArgRunnable(timerIntent, since, millis, timerId)
            {
                boolean first = true;

                @Override
                public void run()
                {
                    if (!first)
                    {
                        Log.d("APPY", "short time timer fire");
                        Widget.startService(Widget.this, (Intent) args[0]);
                    }
                    first = false;
                    long timer = (long) args[3];
                    Timer obj;
                    synchronized (lock)
                    {
                        obj = activeTimers.get(timer);
                    }
                    if (obj != null)
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
            if(needForeground() && getForeground(getApplicationContext()))
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
                mgr.set(AlarmManager.RTC_WAKEUP, millis, pendingIntent[0]);
            }
        }

        return timerId;
    }

    public void loadTimers()
    {
        try
        {
            SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
            String timersString = sharedPref.getString("timers", null);
            if (timersString != null)
            {
                HashMap<Long, Timer> loaded = new MapSerialize<Long, Timer>().deserialize(timersString, new MapSerialize.LongKey(), new TimerValue());
                Log.d("APPY", "loaded " + loaded.size() + " timers");
                for (Map.Entry<Long, Timer> timer : loaded.entrySet())
                {
                    setTimer(timer.getValue().since, timer.getValue().millis, timer.getValue().type, timer.getValue().widgetId, timer.getValue().data, timer.getKey());
                }
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
            refreshPythonFile(pythonFile, true, false);
        }
        else
        {
            Log.d("APPY", "File " + pythonFile.path + " modified, NOT reloading");
        }
    }

    public interface MultipleFileObserverBase {
        void start();
        void stop();
    }

    public abstract class MultipleFileObserver extends FileObserver implements MultipleFileObserverBase
    {
        List<PythonFile> pythonFiles;
        @RequiresApi(api = Build.VERSION_CODES.Q)
        public MultipleFileObserver(List<PythonFile> pythonFiles, int mask)
        {
            super(pythonFiles.stream().map(pythonFile -> {
                return new File(pythonFile.path).getParentFile();
            }).collect(Collectors.toList()), mask);

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
        public void onEvent(final int event, @Nullable final String path)
        {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d("APPY", "got base inotify: "+path+" "+event);
                    for (PythonFile file : pythonFiles)
                    {
                        if (new File(file.path).getName().equals(path))
                        {
                            onEvent(event, file);
                        }
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

        public OldMultipleFileObserver(List<PythonFile> files, int mask) {
            for (PythonFile file : files) {
                observers.add(new SingleFileObserver(file, mask, this, observers.size()));
            }
        }

        public void start() {
            for (SingleFileObserver observer : observers) {
                observer.startWatching();
            }
        }

        public void stop() {
            for (SingleFileObserver observer : observers) {
                observer.stopWatching();
            }
        }

        public void onSingleEvent(int event, int index) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //update observer
                    SingleFileObserver ob = observers.get(index);
                    ob.stopWatching();
                    SingleFileObserver newob = new SingleFileObserver(ob.file, ob.mask, OldMultipleFileObserver.this, index);
                    observers.set(index, newob);
                    newob.startWatching();

                    Log.d("APPY", "new watch on " + index + " " + newob.file.path + " " + newob.mask);

                    onEvent(event, newob.file);
                }
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


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            pythonFilesObserver = new MultipleFileObserver(pythonFiles, FileObserver.CLOSE_WRITE)
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
            pythonFilesObserver = new OldMultipleFileObserver(pythonFiles, FileObserver.CLOSE_WRITE)
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
            SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
            String pythonfilesString = sharedPref.getString("pythonfiles", null);
            if (pythonfilesString != null)
            {
                synchronized (lock)
                {
                    pythonFiles = PythonFile.deserializeArray(pythonfilesString);
                }
            }
            String unknownPythonFileString = sharedPref.getString("unknownpythonfile", null);
            if (unknownPythonFileString != null)
            {
                synchronized (lock)
                {
                    unknownPythonFile = PythonFile.deserializeSingle(unknownPythonFileString);
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
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        synchronized (lock)
        {
            editor.putString("pythonfiles", PythonFile.serializeArray(pythonFiles));
            editor.putString("unknownpythonfile", unknownPythonFile.serializeSingle());
        }
        editor.apply();
    }

    public void refreshPythonFile(PythonFile file)
    {
        refreshPythonFile(file, true, false);
    }

    public void refreshPythonFile(PythonFile file, boolean usePool, boolean skipRefresh)
    {
        Task task = new Task<>(new CallImportTask(), file, skipRefresh);
        if (usePool)
        {
            addTask(Constants.IMPORT_TASK_QUEUE, task);
        }
        else
        {
            task.run();
        }
    }

    public void initAllPythonFiles()
    {
        ArrayList<PythonFile> files = getPythonFiles();
        for (PythonFile f : files)
        {
            refreshPythonFile(f, false, true);
        }
        if (updateListener != null) {
            updateListener.refreshManagers();
        }
    }

    public void addPythonFiles(ArrayList<PythonFile> files)
    {
        boolean exists = false;
        synchronized (lock)
        {
            for (PythonFile file : files)
            {
                for (PythonFile f : pythonFiles)
                {
                    if (f.path.equals(file.path))
                    {
                        exists = true;
                        break;
                    }
                }
                if (!exists)
                {
                    pythonFiles.add(file);
                }
            }
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
        synchronized (lock)
        {
            int idx = pythonFiles.indexOf(file);
            if (idx != -1) {
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
        synchronized (lock)
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
        synchronized (lock)
        {
            return new ArrayList<>(pythonFiles);
        }
    }

    public void saveTimers()
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        synchronized (lock)
        {
            editor.putString("timers", new MapSerialize<Long, Timer>().serialize(activeTimers, new MapSerialize.LongKey(), new TimerValue()));
        }
        editor.apply();
    }

    public void clearWidget(int widgetId)
    {
        cancelWidgetTimers(widgetId);
        if(updateListener != null)
        {
            updateListener.onDelete(widgetId);
        }
        update(widgetId);
    }

    public StateLayout getStateLayout()
    {
        if (updateListener == null)
        {
            return new StateLayout();
        }

        return StateLayout.deserialize(updateListener.getStateLayout());
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
        addTask(widgetId, new Task<>(new DeleteWidgetTask(), widgetId, androidWidgetDeleted));
    }

    public void update(int widgetId)
    {
        addTask(widgetId, new Task<>(new CallUpdateTask(), widgetId));
    }

    public void updateAll()
    {
        for(int widgetId : getAllWidgets())
        {
            update(widgetId);
        }
    }

    public void restart()
    {
        Log.d("APPY", "restarting process");
        setAllWidgets(false);
        saveWidgets();
        saveWidgetMapping();
        handler.post(new Runnable()
         {
             @Override
             public void run()
             {
                 Intent intent = new Intent(Widget.this, getClass());
                 PendingIntent pendingIntent;
                 if(needForeground() && getForeground(getApplicationContext()))
                 {
                     pendingIntent = PendingIntent.getForegroundService(getApplicationContext(), 1, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
                 }
                 else
                 {
                     pendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
                 }
                 AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
                 mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent);
                 System.exit(0);
             }
         });
    }

    public void saveState(String state)
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("state", state);
        editor.apply();
    }

    public String loadState()
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        return sharedPref.getString("state", null);
    }

    public Configurations getConfigurations()
    {
        return configurations;
    }

    public void setPythonUnpacked(int version)
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("unpacked_version", version);
        editor.apply();
    }

    public int getPythonUnpacked()
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        return sharedPref.getInt("unpacked_version", 0);
    }

    public Uri getUriForPath(String path)
    {
        return FileProvider.getUriForFile(this, "com.appy.fileprovider", new File(path));
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
            activeRequests.put(newId, null); //save room
            return newId;
        }
    }
    HashMap<Integer, Object> activeRequests = new HashMap<>();
    final Object notifier = new Object();

    public static Pair<int[], Boolean> getPermissionState(Context context, String[] permissions)
    {
        int[] granted = new int[permissions.length];
        boolean hasDenied = false;
        for(int i = 0; i < permissions.length; i++)
        {
            granted[i] = ContextCompat.checkSelfPermission(context, permissions[i]);
            if (granted[i] != PackageManager.PERMISSION_GRANTED)
            {
                hasDenied = true;
                break;
            }
        }
        return new Pair<>(granted, !hasDenied);
    }

    public Object waitForAsyncReport(int requestCode, int timeoutMilli)
    {
        Object result = null;
        synchronized (notifier)
        {
            boolean hasTimeout = timeoutMilli >= 0;
            long end = System.currentTimeMillis() + timeoutMilli;
            while (activeRequests.get(requestCode) == null)
            {
                try
                {
                    if(hasTimeout)
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
        Pair<int[], Boolean> state = getPermissionState(this, permissions);
        if(state.second || !request) //if all was granted or we should not request any, return the current state
        {
            return new Pair<>(permissions, state.first);
        }

        if(Looper.myLooper() != null)
        {
            throw new IllegalStateException("requestPermissions must be called on a Task thread");
        }

        int requestCode = generateRequestCode();
        Intent intent = new Intent(this, PermissionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.putExtra(PermissionActivity.EXTRA_REQUEST_CODE, requestCode);
        intent.putExtra(PermissionActivity.EXTRA_PERMISSIONS, permissions);
        startActivity(intent);

        return (Pair<String[], int[]>)waitForAsyncReport(requestCode, timeoutMilli);
    }

    public void asyncReport(int requestCode, @NonNull Object result)
    {
        synchronized (notifier)
        {
            if(activeRequests.containsKey(requestCode))
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
        if(Looper.myLooper() != null)
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

    public void reportSetConfig(int requestCode)
    {

    }

    public void setWidget(final int androidWidgetId, final int widgetId, final ArrayList<DynamicView> views, final boolean errorOnFailure)
    {
        needUpdateWidgets.remove(widgetId);
        handler.post(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(Widget.this);
                    int[] widgetDimensions = getWidgetDimensions(appWidgetManager, androidWidgetId);
                    int widthLimit = widgetDimensions[0];
                    int heightLimit = widgetDimensions[1];

                    Pair<RemoteViews, HashSet<Integer>> view = resolveDimensions(Widget.this, widgetId, views, Constants.CollectionLayout.NOT_COLLECTION, null, widthLimit, heightLimit);
                    appWidgetManager.updateAppWidget(androidWidgetId, view.first);
                    for(Integer collection_view : view.second)
                    {
                        appWidgetManager.notifyAppWidgetViewDataChanged(androidWidgetId, collection_view);
                    }
                }
                catch(Exception e)
                {
                    Log.e("APPY", "Exception on setWidget", e);
                    if(errorOnFailure)
                    {
                        setSpecificErrorWidget(androidWidgetId, widgetId, e);
                    }
                }
            }
        });
    }

    public void setLoadingWidget(int widgetId)
    {
        setSpecificLoadingWidget(getAndroidWidget(widgetId), widgetId);
    }

    public void setSpecificLoadingWidget(int androidWidgetId, int widgetId)
    {
        ArrayList<DynamicView> views = new ArrayList<>();

        DynamicView textView = new DynamicView("TextView");
        textView.methodCalls.add(new RemoteMethodCall("setText", false, Constants.getSetterMethod(textView.type, "setText"), "setText", "Loading..."));
        textView.methodCalls.add(new RemoteMethodCall("setTextColor", false, Constants.getSetterMethod(textView.type, "setTextColor"), "setTextColor", Constants.TEXT_COLOR));

        Attributes.AttributeValue wholeWidth = attributeParse("w(p)");
        Attributes.AttributeValue wholeHeight = attributeParse("h(p)");
        textView.attributes.attributes.put(Attributes.Type.WIDTH, wholeWidth);
        textView.attributes.attributes.put(Attributes.Type.HEIGHT, wholeHeight);

        views.add(textView);

        setWidget(androidWidgetId, Constants.SPECIAL_WIDGET_ID, views, true);

        if(widgetId != -1)
        {
            needUpdateWidgets.add(widgetId);
        }
    }

    public void setSpecificErrorWidget(int androidWidgetId, int widgetId, Throwable error)
    {
        Log.d("APPY", "setting error widget for "+widgetId+" android: "+androidWidgetId);

        if(widgetId > 0 && updateListener != null)
        {
            try
            {
                updateListener.onError(widgetId, Stacktrace.stackTraceString(error));
            }
            catch(Exception e)
            {
                Log.e("APPY", "Exception on onError", e);
            }
        }

        ArrayList<DynamicView> views = new ArrayList<>();

        DynamicView textView = new DynamicView("TextView");
        textView.methodCalls.add(new RemoteMethodCall("setText", false, Constants.getSetterMethod(textView.type, "setText"), "setText", "Error"));
        textView.methodCalls.add(new RemoteMethodCall("setTextColor", false, Constants.getSetterMethod(textView.type, "setTextColor"), "setTextColor", Constants.TEXT_COLOR));

        DynamicView restart = new DynamicView("ImageButton");
        restart.selectors.put("style", "danger_oval_pad");
        restart.methodCalls.add(new RemoteMethodCall("setColorFilter", false, Constants.getSetterMethod(restart.type, "setColorFilter"), "setColorFilter", 0xffffffff));
        restart.methodCalls.add(new RemoteMethodCall("setImageResource", false, Constants.getSetterMethod(restart.type, "setImageResource"), "setImageResource", android.R.drawable.ic_lock_power_off));
        restart.attributes.attributes.put(Attributes.Type.WIDTH, attributeParse("140"));
        restart.attributes.attributes.put(Attributes.Type.HEIGHT, attributeParse("140"));
        restart.attributes.attributes.put(Attributes.Type.RIGHT, attributeParse("0"));
        restart.attributes.attributes.put(Attributes.Type.BOTTOM, attributeParse("0"));
        restart.tag = Constants.SPECIAL_WIDGET_RESTART; //onclick

        views.add(textView);
        views.add(restart);

        if(widgetId > 0)
        {
            Attributes.AttributeValue afterText = attributeParse("h("+textView.getId()+")+10");

            DynamicView clear = new DynamicView("Button");
            clear.methodCalls.add(new RemoteMethodCall("setText", false, Constants.getSetterMethod(clear.type, "setText"), "setText", "Clear"));
            clear.selectors.put("style", "dark_sml");
            clear.attributes.attributes.put(Attributes.Type.TOP, afterText);
            clear.attributes.attributes.put(Attributes.Type.LEFT, attributeParse("l(p)"));
            clear.tag = widgetId + (Constants.SPECIAL_WIDGET_CLEAR << 16);

            DynamicView reload = new DynamicView("Button");
            reload.methodCalls.add(new RemoteMethodCall("setText", false, Constants.getSetterMethod(reload.type, "setText"), "setText", "Reload"));
            reload.selectors.put("style", "info_sml");
            reload.attributes.attributes.put(Attributes.Type.TOP, afterText);
            reload.attributes.attributes.put(Attributes.Type.RIGHT, attributeParse("r(p)"));
            reload.tag = widgetId + (Constants.SPECIAL_WIDGET_RELOAD << 16);

            views.add(clear);
            views.add(reload);
        }

        setWidget(androidWidgetId, Constants.SPECIAL_WIDGET_ID, views, false);

        needUpdateWidgets.add(widgetId);
    }

    private boolean isBundleOptionBad(Bundle bundle, String option)
    {
        return bundle.containsKey(option) && bundle.getInt(option) == 0;
    }

    public int[] filterBadAndroidWidgets(int[] ids)
    {
        ArrayList<Integer> filtered = new ArrayList<>();
        for (int id : ids)
        {
            Bundle bundle = AppWidgetManager.getInstance(this).getAppWidgetOptions(id);

            boolean hasSizes = Build.VERSION.SDK_INT < 31 || bundle.containsKey("appWidgetSizes");

            if (bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) != 0 &&
                bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) != 0 &&
                    hasSizes)
            {
                filtered.add(id);
            }
        }

        int[] out = new int[filtered.size()];
        for (int i = 0; i < out.length; i++)
        {
            out[i] = filtered.get(i);
        }
        return out;
    }

    public int[] requestAndroidWidgets()
    {
        ComponentName thisWidget = new ComponentName(this, WidgetReceiver.class);
        int[] ids = AppWidgetManager.getInstance(this).getAppWidgetIds(thisWidget);
        return filterBadAndroidWidgets(ids);
    }

    public void setAllWidgets(boolean error)
    {
        for(int id : requestAndroidWidgets())
        {
            if(error)
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
            String cacheDir = getCacheDir().getAbsolutePath();
            String exampleDir = new File(getFilesDir(), "examples").getAbsolutePath();
            try
            {
                if(getPythonUnpacked() != PYTHON_VERSION)
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
                if(!new File(exampleDir).exists())
                {
                    Log.d("APPY", "unpacking examples");
                    new File(exampleDir).mkdir();
                    untar(getAssets().open("examples.targz"), exampleDir);
                    Log.d("APPY", "done unpacking examples");
                }
                copyAsset(getAssets().open("main.py"), new File(cacheDir, "main.py"));
                copyAsset(getAssets().open("logcat.py"), new File(cacheDir, "logcat.py"));
                copyAsset(getAssets().open("appy.targz"), new File(cacheDir, "appy.tar.gz"));
                System.load(pythonLib);
                System.loadLibrary("native");
                pythonInit(pythonHome, cacheDir, pythonLib, new File(cacheDir, "main.py").getAbsolutePath(), getApplicationInfo().nativeLibraryDir, Widget.this);

                initAllPythonFiles();
            }
            catch(Exception e)
            {
                Log.e("APPY", "Exception on pythonSetup", e);
                error = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    Widget.startService(Widget.this, new Intent(Widget.this, Widget.class));
                }
            });
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
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
            callTimerWidget((long)args[0], (int)args[1], (String)args[2]);
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
        if(path == null)
        {
            setPythonFileLastError(unknownPythonFile, lastError);
        }
        else
        {
            for (PythonFile file : getPythonFiles())
            {
                if (file.path.equals(path))
                {
                    setPythonFileLastError(file, lastError);
                    break;
                }
            }
        }
    }

    private void setPythonFileLastError(PythonFile pythonFile, String lastError)
    {
        Date now = new Date();

        if(pythonFile.lastErrorDate != null && pythonFile.lastError != null && (now.getTime() - pythonFile.lastErrorDate.getTime()) <= Constants.ERROR_COALESCE_MILLI)
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

            String newhash = Utils.hashFile(file.path);
            if (newhash.equalsIgnoreCase(file.hash))
            {
                //file is exactly the same
                Log.d("APPY", file.path+" hash is the same (" +newhash+"), not reloading");
                return;
            }

            file.hash = newhash;

            Boolean skipRefresh = (Boolean)args[1];
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
            catch(Exception e)
            {
                setPythonFileLastError(file, Stacktrace.stackTraceString(e));
                Log.e("APPY", "Exception on import task", e);
            }

            if(file.state == PythonFile.State.RUNNING)
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
            Log.d("APPY", "deleting "+widget);
            synchronized (lock)
            {
                widgets.remove(widget);
            }
            cancelWidgetTimers(widget);
            if(updateListener != null)
            {
                updateListener.onDelete(widget);
            }

            if(args[1] != null)
            {
                deleteAndroidWidget(args[1]);
            }
            else
            {
                deleteWidgetMappings(widget);
            }
            saveWidgetMapping();
            saveWidgets();
        }
    }

    private class CallPostTask implements Runner<Object>
    {
        @Override
        public void run(Object... args)
        {
            callPostWidget((int)args[0], (String)args[1]);
        }
    }

    public void callPostWidget(int widgetId, final String data)
    {
        callWidgetChangingCallback(widgetId, new CallbackCaller()
        {
            @Override
            public String call(int widgetId, String current)
            {
                return updateListener.onPost(widgetId, current, data);
            }
        });
    }

    private class CallConfigTask implements Runner<Object>
    {
        @Override
        public void run(Object... args)
        {
            callConfigWidget((int)args[0], (String)args[1]);
        }
    }

    private class StateDumpTask implements Runner<Object>
    {
        @Override
        public void run(Object... args)
        {
            callStateDump();
        }
    }

    public void callStateDump()
    {
        if (updateListener != null)
        {
            String state = updateListener.dumpState();
            // Log.d("APPY", "dumping state: "+state.length()+" bytes");
            saveState(state);
        }
    }

    public void callConfigWidget(int widgetId, final String key)
    {
        callWidgetChangingCallback(widgetId, new CallbackCaller()
        {
            @Override
            public String call(int widgetId, String current)
            {
                return updateListener.onConfig(widgetId, current, key);
            }
        });
    }

    public void callTimerWidget(final long timerId, int widgetId, final String data)
    {
        Timer timer;
        synchronized (lock)
        {
            timer = activeTimers.get(timerId);
        }
        if(timer == null)
        {
            return;
        }
        if(timer.type != Constants.TIMER_REPEATING)
        {
            cancelTimer(timerId);
        }

        callWidgetChangingCallback(widgetId, new CallbackCaller()
        {
            @Override
            public String call(int widgetId, String current)
            {
                return updateListener.onTimer(timerId, widgetId, current, data);
            }
        });
    }

    interface CallbackCaller
    {
        String call(int widgetId, String current);
    }

    public boolean callWidgetChangingCallback(int widgetId, CallbackCaller caller)
    {
        if(updateListener == null)
        {
            return false;
        }

        int androidWidgetId = getAndroidWidget(widgetId);
        String widget;
        synchronized (lock)
        {
            widget = widgets.get(widgetId);
        }

        boolean updated = false;
        try
        {
            String newwidget = caller.call(widgetId, widget);
            if(newwidget != null)
            {
                putWidget(widgetId, newwidget);
                widget = newwidget;
                updated = true;
            }

            //if we were loading we refresh anyways
            if((updated || needUpdateWidgets.contains(widgetId)) && widget != null)
            {
                setWidget(androidWidgetId, widgetId, DynamicView.fromJSONArray(widget), true);
                return true;
            }
        }
        catch (Exception e)
        {
            Log.e("APPY", "Exception in python", e);
            setSpecificErrorWidget(androidWidgetId, widgetId, e);
            return true;
        }
        finally
        {
            deferredStateDump();
        }

        return false;
    }

    public void callEventWidget(int eventWidgetId, final long itemId, final long collectionItemId, final int collectionPosition, final boolean checked)
    {
        Log.d("APPY", "got event intent: " + eventWidgetId + " " + itemId);

        callWidgetChangingCallback(eventWidgetId, new CallbackCaller()
        {
            @Override
            public String call(int widgetId, String current)
            {
                Log.d("APPY", "handling "+itemId+" in collection "+collectionItemId);

                if(itemId == 0 && collectionItemId == 0)
                {
                    throw new IllegalArgumentException("handle without handle?");
                }

                String fromItemClick = null;

                if(collectionItemId != 0)
                {
                    Log.d("APPY", "calling listener onItemClick with "+collectionItemId+", "+collectionPosition+", "+itemId+", "+checked);
                    Object[] ret = updateListener.onItemClick(widgetId, current, collectionItemId, collectionPosition, itemId);
                    boolean handled = (boolean)ret[0];
                    fromItemClick = (String)ret[1];
                    if(handled || itemId == 0)
                    {
                        Log.d("APPY", "suppressing click on "+itemId);
                        return fromItemClick;
                    }
                }

                Log.d("APPY", "calling listener onClick");
                String newwidget = updateListener.onClick(widgetId, fromItemClick != null ? fromItemClick : current, itemId, checked);
                Log.d("APPY", "called listener onClick");
                if(newwidget != null)
                {
                    return newwidget;
                }
                else
                {
                    return fromItemClick; //use onItemClick
                }
            }
        });
    }

    public void callUpdateWidget(int widgetId)
    {
        int androidWidgetId = getAndroidWidget(widgetId);

        Log.d("APPY", "update: " + androidWidgetId + " ("+widgetId+")");

        if (updateListener == null)
        {
            setSpecificErrorWidget(androidWidgetId, widgetId, null);
            return;
        }

        boolean hasWidget;
        synchronized (lock)
        {
            hasWidget = widgets.containsKey(widgetId);
        }

        Log.d("APPY", "calling listener onUpdate");

        if(!hasWidget)
        {
            boolean updated = callWidgetChangingCallback(widgetId, new CallbackCaller()
            {
                @Override
                public String call(int widgetId, String current)
                {
                    return updateListener.onCreate(widgetId);
                }
            });
            if(!updated)
            {
                setSpecificErrorWidget(androidWidgetId, widgetId, null);
                return;
            }
        }

        callWidgetChangingCallback(widgetId, new CallbackCaller()
        {
            @Override
            public String call(int widgetId, String current)
            {
                return updateListener.onUpdate(widgetId, current);
            }
        });
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
        Widget getService() {
            return Widget.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if(intent.getBooleanExtra(Constants.LOCAL_BIND_EXTRA, false))
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

    public Constants.StartupState getStartupState()
    {
        return startupState;
    }

    public void callStatusChange(final boolean startup)
    {
        handler.post(new Runnable()
        {
            @Override
            public void run()
            {
                if(statusListener != null)
                {
                    if(startup)
                    {
                        statusListener.onStartupStatusChange();
                    }
                    else
                    {
                        statusListener.onPythonFileStatusChange();
                    }
                }
            }
        });
    }

    public void resetState()
    {
        if(updateListener != null)
        {
            updateListener.wipeStateRequest();
        }
    }

    public void resetWidgets()
    {
        Log.d("APPY", "clearing all widgets");
        for(int widget : getAllWidgets())
        {
            clearWidget(widget);
        }
    }

    private boolean pythonSetup()
    {
        Log.d("APPY", "dir: " + getApplicationInfo().nativeLibraryDir);
        if(!startedAfterSetup && pythonSetupTask.getStatus() == AsyncTask.Status.PENDING)
        {
            startupState = Constants.StartupState.IDLE;
            handler = new Handler();

            loadPythonFiles();
            loadCorrectionFactors(true);
            //loadForeground();
            loadWidgets();
            loadTimers();
            configurations.load();

            setAllWidgets(false);
            callStatusChange(true);

            pythonSetupTask.execute();

            return false;
        }

        if(pythonSetupTask.getStatus() == AsyncTask.Status.RUNNING)
        {
            setAllWidgets(false);
            return false;
        }

        if(pythonSetupTask.getStatus() == AsyncTask.Status.FINISHED && pythonSetupTask.hadError())
        {
            startupState = Constants.StartupState.ERROR;
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    setAllWidgets(true);
                }
            });
            callStatusChange(true);
            return false;
        }

        startupState = Constants.StartupState.COMPLETED;
        callStatusChange(true);
        return true;
    }

    class CrashHandler implements Thread.UncaughtExceptionHandler
    {
        String path;
        Thread.UncaughtExceptionHandler prev;
        public CrashHandler(String path, Thread.UncaughtExceptionHandler prev)
        {
            this.path = path;
            this.prev = prev;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e)
        {
            String trace = Stacktrace.stackTraceString(e);

            BufferedWriter bw = null;
            try
            {
                bw = new BufferedWriter(new FileWriter(path, true));
                bw.write(trace);
                bw.flush();
                bw.close();
            }
            catch (IOException e1)
            {
                Log.e("APPY", "Exception on uncaught exception", e1);
            }
            finally
            {
                if(bw != null)
                {
                    try
                    {
                        bw.close();
                    }
                    catch (IOException e1)
                    {

                    }
                }
            }

            if(prev != null)
            {
                prev.uncaughtException(t, e);
            }
        }
    }
    boolean startedAfterSetup = false;
    PythonSetupTask pythonSetupTask = new PythonSetupTask();
    public void handleStartCommand(Intent intent)
    {
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        if(handler == null || !(handler instanceof CrashHandler))
        {
            //not ours
            Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(new File(getCacheDir(), "javacrash.txt").getAbsolutePath(), handler));
        }

        loadForeground();

        if(!pythonSetup())
        {
            return;
        }

        boolean firstStart = false;
        if(!startedAfterSetup)
        {
            firstStart = true;
            startedAfterSetup = true;
        }

        Log.d("APPY", "startCommand");

        Intent widgetIntent = null;

        if(intent != null)
        {
            widgetIntent = intent.getParcelableExtra(Constants.WIDGET_INTENT);
        }

        if(firstStart)
        {
            int[] ids = requestAndroidWidgets();
            if(ids != null)
            {
                HashSet<Integer> activeWidgetIds = new HashSet<>();
                for (int id : ids)
                {
                    int widgetId = fromAndroidWidget(id, true);
                    activeWidgetIds.add(widgetId);
                    needUpdateWidgets.add(widgetId);

                    addTask(widgetId, new Task<>(new CallUpdateTask(), widgetId));
                }
                Set<Integer> allWidgetIds = getAllWidgets();
                allWidgetIds.removeAll(activeWidgetIds);
                Log.d("APPY", "deleting inactive "+allWidgetIds.size());
                for(int widgetId : allWidgetIds)
                {
                    delete(widgetId);
                }
            }
        }
        else if (intent != null && intent.getAction() != null && intent.getAction().startsWith("timer") && intent.hasExtra("widgetId") && intent.hasExtra("timer"))
        {
            Log.d("APPY", "timer fire");
            addTask(intent.getIntExtra("widgetId", -1), new Task<>(new CallTimerTask(), intent.getLongExtra("timer", -1), intent.getIntExtra("widgetId", -1), Gzip.decompress(intent.getByteArrayExtra("timerData"))));
        }
        else if(widgetIntent != null)
        {
            if (AppWidgetManager.ACTION_APPWIDGET_RESTORED.equals(widgetIntent.getAction()))
            {
                int[] oldWidgets = widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS);
                int[] newWidgets = widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                for(int i = 0; i < oldWidgets.length; i++)
                {
                    updateAndroidWidget(oldWidgets[i], newWidgets[i]);
                }
                saveWidgetMapping();
            }
            else if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(widgetIntent.getAction()) && widgetIntent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
            {
                int deletedWidget = widgetIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                Integer widget = fromAndroidWidget(deletedWidget, false);
                if(widget != null)
                {
                    delete(widget, deletedWidget);
                }
                else
                {
                    deleteAndroidWidget(deletedWidget);
                    saveWidgetMapping();
                    saveWidgets();
                }
            }
            else if(AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED.equals(widgetIntent.getAction()) && widgetIntent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
            {
                int widgetId = fromAndroidWidget(widgetIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1), true);
                needUpdateWidgets.add(widgetId);
                addTask(widgetId, new Task<>(new CallUpdateTask(), widgetId));
            }
            else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(widgetIntent.getAction()))
            {
                if(widgetIntent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS))
                {
                    for (int androidWidgetId : widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS))
                    {
//                        boolean newWidget = fromAndroidWidget(androidWidgetId, false) == null;
                        int widgetId = fromAndroidWidget(androidWidgetId, true);
//                        if(newWidget)
//                        {
//                            Log.d("APPY", "new widget!");
//                            setLoadingWidget(widgetId);
//                        }
                        addTask(widgetId, new Task<>(new CallUpdateTask(), widgetId));
                    }
                }
                else if(widgetIntent.hasExtra(Constants.WIDGET_ID_EXTRA))
                {
                    int eventWidgetId = widgetIntent.getIntExtra(Constants.WIDGET_ID_EXTRA, -1);

                    if(eventWidgetId == Constants.SPECIAL_WIDGET_ID)
                    {
                        int tag = widgetIntent.getIntExtra(Constants.ITEM_TAG_EXTRA, 0);
                        if(tag == Constants.SPECIAL_WIDGET_RESTART)
                        {
                            restart();
                        }
                        else if(((tag >> 16) & 0xffff) == Constants.SPECIAL_WIDGET_CLEAR)
                        {
                            int widgetId = tag & 0xffff;
                            if(widgetId > 0)
                            {
                                Log.d("APPY", "clearing " + widgetId);
                                clearWidget(widgetId);
                            }
                        }
                        else if(((tag >> 16) & 0xffff) == Constants.SPECIAL_WIDGET_RELOAD)
                        {
                            int widgetId = tag & 0xffff;
                            if(widgetId > 0)
                            {
                                Log.d("APPY", "reloading " + widgetId);
                                update(widgetId);
                            }
                        }
                    }
                    else
                    {
                        Log.d("APPY", "calling event task: item id: " + widgetIntent.hasExtra(Constants.ITEM_ID_EXTRA) + ", collection item id: " + widgetIntent.hasExtra(Constants.COLLECTION_ITEM_ID_EXTRA)+" , collection item position: "+widgetIntent.hasExtra(Constants.COLLECTION_POSITION_EXTRA)+" checked: "+widgetIntent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false));
                        addTask(eventWidgetId, new Task<>(new CallEventTask(), (long)eventWidgetId, widgetIntent.getLongExtra(Constants.ITEM_ID_EXTRA, 0), widgetIntent.getLongExtra(Constants.COLLECTION_ITEM_ID_EXTRA, 0), (long)widgetIntent.getIntExtra(Constants.COLLECTION_POSITION_EXTRA, -1), (long)(widgetIntent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false) ? 1 : 0)));
                    }
                }
            }
        }
    }

    //-----------------------------------python--------------------------------------------------------------

    public static void printFnames(File sDir){
        File[] faFiles = sDir.listFiles();
        for(File file: faFiles){
            Log.d("APPY", file.getAbsolutePath());
            if(file.isDirectory()){
                printFnames(file);
            }
        }
    }

    public static void printFile(File file)
    {
        try
        {
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            while ((line = bufferedReader.readLine()) != null)
            {
                Log.e("APPY", line);
            }
            fileReader.close();
        }
        catch(IOException e)
        {
            Log.e("APPY", "exception", e);
        }
    }

    public static void printAll(InputStream is)
    {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line;
        try
        {
            while ( (line = br.readLine()) != null)
            {
                Log.d("APPY", line);
            }
        }
        catch (IOException e)
        {
            Log.e("APPY", "Exception on printAll", e);
        }
    }

    public static void copyAsset(InputStream asset, File file) throws IOException
    {
        FileOutputStream fos = new FileOutputStream(file);
        BufferedOutputStream out = new BufferedOutputStream(fos);

        byte[] buffer = new byte[1024];
        int read;
        while((read = asset.read(buffer)) != -1)
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

        if(!file.getCanonicalPath().startsWith(canonicalDest))
        {
            throw new IOException("traversal");
        }

        if(entry.isDirectory())
        {
            file.mkdirs();
        }
        else
        {
            file.getParentFile().mkdirs();

            if(entry.getHeader().linkFlag == TarHeader.LF_SYMLINK || entry.getHeader().linkFlag == TarHeader.LF_LINK)
            {
                File target = new File(file.getParentFile(), entry.getHeader().linkName.toString());
                if(!target.getCanonicalPath().startsWith(canonicalDest))
                {
                    throw new IOException("traversal");
                }
                try
                {
                    Log.d("APPY", "creating link: "+entry.getHeader().linkName.toString()+" as "+ file.getAbsolutePath());
                    if(entry.getHeader().linkFlag == TarHeader.LF_SYMLINK)
                    {
                        Os.symlink(entry.getHeader().linkName.toString(), file.getAbsolutePath());
                    }
                    else
                    {
                        Os.link(entry.getHeader().linkName.toString(), file.getAbsolutePath());
                    }
                }
                catch(ErrnoException e)
                {
                    throw new IOException(e);
                }
            }
            else if(entry.getHeader().linkFlag == TarHeader.LF_NORMAL)
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
                Log.d("APPY", "cannot create type "+entry.getHeader().linkFlag);
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

            while((entry = tis.getNextEntry()) != null) {
                try
                {
                    process(tis, entry, dest);
                }
                catch(IOException e)
                {
                    Log.w("APPY", "exception in tar file: ", e);
                }
            }

            tis.close();
        }
        catch(IOException e) {
            Log.e("APPY", "tar exception", e);
        }
    }

    protected static native void pythonInit(String pythonHome, String tmpPath, String pythonLibPath, String script, String nativepath, Object arg);
    protected static native Object pythonCall(Object... args) throws Throwable;
}
