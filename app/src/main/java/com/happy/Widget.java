package com.happy;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.TypedValue;
import android.widget.AdapterViewFlipper;
import android.widget.AnalogClock;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import android.widget.StackView;
import android.widget.TextView;
import android.widget.ViewFlipper;

public class Widget extends RemoteViewsService {
    private static final String ITEM_ID_EXTRA = "ITEM_ID";
    public static final String WIDGET_INTENT = "WIDGET_INTENT";
    private static final String COLLECTION_ITEM_ID_EXTRA = "COLLECTION_ITEM_ID_EXTRA";
    private static final String COLLECTION_POSITION_EXTRA = "COLLECTION_POSITION_EXTRA";
    private static final String LIST_SERIALIZED_EXTRA = "LIST_SERIALIZED_EXTRA";
    private static final String XML_ID_EXTRA = "XML_ID_EXTRA";
    private static final String VIEW_ID_EXTRA = "VIEW_ID_EXTRA";

    public static HashMap<String, Class<?>> typeToClass = new HashMap<>();
    public static HashMap<String, HashMap<String, String>> typeToRemotableMethod = new HashMap<>();
    public static HashMap<Class<?>, String> parameterToSetter = new HashMap<>();
    static
    {
        typeToClass.put("FrameLayout", FrameLayout.class);
        typeToClass.put("LinearLayout", LinearLayout.class);
        typeToClass.put("RelativeLayout", RelativeLayout.class);
        typeToClass.put("GridLayout", GridLayout.class);
        typeToClass.put("AnalogClock", AnalogClock.class);
        typeToClass.put("Button", Button.class);
        typeToClass.put("Chronometer", Chronometer.class);
        typeToClass.put("ImageButton", ImageButton.class);
        typeToClass.put("ImageView", ImageView.class);
        typeToClass.put("ProgressBar", ProgressBar.class);
        typeToClass.put("TextView", TextView.class);
        typeToClass.put("ViewFlipper", ViewFlipper.class);
        typeToClass.put("ListView", ListView.class);
        typeToClass.put("GridView", GridView.class);
        typeToClass.put("StackView", StackView.class);
        typeToClass.put("AdapterViewFlipper", AdapterViewFlipper.class);

        parameterToSetter.put(Boolean.TYPE, "setBoolean");
        parameterToSetter.put(Byte.TYPE, "setByte");
        parameterToSetter.put(Short.TYPE, "setShort");
        parameterToSetter.put(Integer.TYPE, "setInt");
        parameterToSetter.put(Long.TYPE, "setLong");
        parameterToSetter.put(Float.TYPE, "setFloat");
        parameterToSetter.put(Double.TYPE, "setDouble");
        parameterToSetter.put(Character.TYPE, "setChar");

        parameterToSetter.put(Boolean.class, "setBoolean");
        parameterToSetter.put(Byte.class, "setByte");
        parameterToSetter.put(Short.class, "setShort");
        parameterToSetter.put(Integer.class, "setInt");
        parameterToSetter.put(Long.class, "setLong");
        parameterToSetter.put(Float.class, "setFloat");
        parameterToSetter.put(Double.class, "setDouble");
        parameterToSetter.put(Character.class, "setChar");
        parameterToSetter.put(String.class, "setString");
        parameterToSetter.put(CharSequence.class, "setCharSequence");
        parameterToSetter.put(Uri.class, "setUri");
        parameterToSetter.put(Bitmap.class, "setBitmap");
        parameterToSetter.put(Bundle.class, "setBundle");
        parameterToSetter.put(Intent.class, "setIntent");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            parameterToSetter.put(Icon.class, "setIcon");
        }

        for(String type: typeToClass.keySet())
        {
            typeToRemotableMethod.put(type, getRemotableMethods(type));
        }
    }

    public static ArrayList<DynamicView> templates = new ArrayList<>();
    static
    {
        try
        {
            String list =   "{" +
                            "    \"id\": 0," +
                            "    \"type\": \"*\"," +
                            "    \"children\":" +
                            "    [" +
                            "        {" +
                            "            \"id\": 1," +
                            "            \"type\": \"RelativeLayout\"," +
                            "            \"xmlId\": "+ R.layout.bestfit_layout +"," +
                            "            \"viewId\": "+ R.id.e1 +"," +
                            "            \"children\":" +
                            "            [" +
                            "                {" +
                            "                    \"id\": 2," +
                            "                    \"type\": \"ListView\"," +
                            "                    \"xmlId\": "+ R.layout.bestfit_layout +"," +
                            "                    \"viewId\": "+ R.id.e2 +"," +
                            "                    \"children\":" +
                            "                    [" +
                            "                        {" +
                            "                            \"id\": 0," +
                            "                            \"type\": \"*\"" +
                            "                        }" +
                            "                    ]" +
                            "                }," +
                            "                {" +
                            "                    \"id\": 3," +
                            "                    \"type\": \"RelativeLayout\"," +
                            "                    \"xmlId\": "+ R.layout.bestfit_layout +"," +
                            "                    \"viewId\": "+ R.id.e3 +"," +
                            "                    \"children\":" +
                            "                    [" +
                            "                        {" +
                            "                            \"id\": 0," +
                            "                            \"type\": \"*\"" +
                            "                        }" +
                            "                    ]" +
                            "                }" +
                            "            ]" +
                            "        }" +
                            "    ]" +
                            "}";

            String text =   "{" +
                    "    \"id\": 0," +
                    "    \"type\": \"*\"," +
                    "    \"children\":" +
                    "    [" +
                    "        {" +
                    "            \"id\": 1," +
                    "            \"type\": \"TextView\"," +
                    "            \"xmlId\": "+ R.layout.textview_layout +"," +
                    "            \"viewId\": "+ R.id.element +
                    "        }" +
                    "    ]" +
                    "}";

            String button =   "{" +
                    "    \"id\": 0," +
                    "    \"type\": \"*\"," +
                    "    \"children\":" +
                    "    [" +
                    "        {" +
                    "            \"id\": 1," +
                    "            \"type\": \"Button\"," +
                    "            \"xmlId\": "+ R.layout.button_layout +"," +
                    "            \"viewId\": "+ R.id.element +
                    "        }" +
                    "    ]" +
                    "}";

            templates.add(DynamicView.fromJSON(list, true));
            templates.add(DynamicView.fromJSON(text, true));
            templates.add(DynamicView.fromJSON(button, true));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    BestFit bestFit = new BestFit(templates);

    HashMap<Integer, String> widgets = new HashMap<>();
    WidgetUpdateListener updateListener = null;

    public String initWidget(int widgetId)
    {
        String widget = "{" +
                        "    \"id\": 1," +
                        "    \"type\": \"RelativeLayout\"," +
                        "    \"children\":" +
                        "    [" +
                        "       {" +
                        "           \"id\": 2," +
                        "           \"type\": \"ListView\"," +
                        "           \"children\":" +
                        "           [" +
                        "               {" +
                        "                   \"id\": 3," +
                        "                   \"type\": \"TextView\"," +
                        "                   \"methodCalls\":" +
                        "                   [" +
                        "                       {" +
                        "                           \"identifier\": \"setText\"," +
                        "                           \"method\": \"setCharSequence\"," +
                        "                           \"arguments\":" +
                        "                           [" +
                        "                               \"setText\"," +
                        "                               \"abcde\"" +
                        "                           ]" +
                        "                       }" +
                        "                   ]" +
                        "               }," +
                        "               {" +
                        "                   \"id\": 4," +
                        "                   \"type\": \"TextView\"," +
                        "                   \"methodCalls\":" +
                        "                   [" +
                        "                       {" +
                        "                           \"identifier\": \"setText\"," +
                        "                           \"method\": \"setCharSequence\"," +
                        "                           \"arguments\":" +
                        "                           [" +
                        "                               \"setText\"," +
                        "                               \"fghij\"" +
                        "                           ]" +
                        "                       }" +
                        "                   ]" +
                        "               }" +
                        "           ]" +
                        "       }" +
                        "    ]" +
                        "}";
        return widget;
    }

    public DynamicView updateWidget(int widgetId, DynamicView root)
    {
        root.children.clear();
        for(int i = 0; i < 100; i++)
        {
            DynamicView text1 = new DynamicView("TextView");
            text1.methodCalls.add(new RemoteMethodCall("setText", getSetterMethod(text1.type, "setText"), "setText", ""+new Random().nextInt(1000)));
            text1.methodCalls.add(new RemoteMethodCall("setTextViewTextSize", "setTextViewTextSize", TypedValue.COMPLEX_UNIT_SP, 30));
            root.children.add(text1);
        }
        return null;
    }

    public RemoteViews newGenerate(Context context, int widgetId, DynamicView layout, boolean inCollection, boolean root) throws InvocationTargetException, IllegalAccessException
    {
        ArrayList<RemoteViews> storage = new ArrayList<>();
        newGenerate(context, widgetId, storage, null, layout, 0, null, null, inCollection, root);
        return storage.get(0);
    }

    public boolean isCollection(String type)
    {
        return "ListView".equals(type) ||
                "GridView".equals(type) ||
                "StackView".equals(type) ||
                "AdapterViewFlipper".equals(type);
    }

    public boolean isGroup(String type)
    {
        return "FrameLayout".equals(type) ||
                "LinearLayout".equals(type) ||
                "RelativeLayout".equals(type) ||
                "GridLayout".equals(type) ||
                "ViewFlipper".equals(type);
    }


    HashMap<Pair<Integer, Integer>, HashMap<Integer, NewListFactory>> factories = new HashMap<>();
    public NewListFactory getFactory(Context context, int widgetId, int xml, int view, String list)
    {
        Pair<Integer, Integer> key = new Pair<>(widgetId, xml);
        HashMap<Integer, NewListFactory> inWidgetXml = factories.get(key);
        if(inWidgetXml == null)
        {
            inWidgetXml = new HashMap<>();
            factories.put(key, inWidgetXml);
        }

        NewListFactory foundFactory = inWidgetXml.get(view);
        if(foundFactory == null)
        {
            foundFactory = new NewListFactory(context, widgetId, list);
            inWidgetXml.put(view, foundFactory);
        }
        else
        {
            foundFactory.reload(list);
        }

        return foundFactory;
    }

    class NewListFactory implements RemoteViewsFactory
    {
        int widgetId;
        Context context;
        DynamicView list;

        public NewListFactory(Context context, int widgetId, String list)
        {
            this.context = context;
            this.widgetId = widgetId;
            reload(list);
        }

        public void reload(String list)
        {
            DynamicView view = DynamicView.fromJSON(list, true);
            Log.d("HAPY", "reloadFactory: "+widgetId + " "+list+" "+view.view_id+", "+view.xml_id+" dynamic: "+view.getId());
            this.list = view;
        }

        @Override
        public void onCreate()
        {

        }

        @Override
        public void onDataSetChanged()
        {
            Log.d("HAPY", "onDataSet");
        }

        @Override
        public void onDestroy()
        {

        }

        @Override
        public int getCount()
        {
            Log.d("HAPY", "count: "+list.children.size());
            return list.children.size();
        }

        @Override
        public RemoteViews getViewAt(int position)
        {
            Log.d("HAPY", "get view at: "+position +"/"+list.children.size());
            try
            {
                if(position < list.children.size())
                {
                    DynamicView view = list.children.get(position);
                    RemoteViews remoteView = newGenerate(context, widgetId, view, true, false);
                    Log.d("HAPY", "inflating child "+view.toJSON());
                    Log.d("HAPY", remoteView.toString());
                    Intent fillIntent = new Intent(context, WidgetReceiver.class);
                    fillIntent.putExtra(ITEM_ID_EXTRA, view.getId());
                    fillIntent.putExtra(COLLECTION_POSITION_EXTRA, position);
                    remoteView.setOnClickFillInIntent(view.view_id, fillIntent);
                    return remoteView;
                }
            }
            catch (InvocationTargetException|IllegalAccessException e)
            {
                e.printStackTrace();
            }
            return null; //maybe TODO?
        }

        @Override
        public RemoteViews getLoadingView()
        {
            return null;
        }

        @Override
        public int getViewTypeCount()
        {
            return getCount();
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

    //the arraylist acts as a pointer
    public void newGenerate(Context context, int widgetId, ArrayList<RemoteViews> storage, RemoteViews current, DynamicView layout, int parentViewId, DynamicView currentTemplate, HashMap<Integer, Integer> currentMap, boolean inCollection, boolean root) throws InvocationTargetException, IllegalAccessException
    {
        if(currentMap != null && currentMap.containsKey(layout.getId()))
        {
            DynamicView templateMap = find(currentTemplate, currentMap.get(layout.getId()));
            layout.xml_id = templateMap.xml_id;
            layout.view_id = templateMap.view_id;
        }
        else
        {
            Log.d("HAPY", "forking on "+layout.getId()+" as child of "+parentViewId);
            Pair<DynamicView, HashMap<Integer, Integer>> fit = bestFit.bestFit(layout);
            if(fit == null)
            {
                throw new IllegalArgumentException("cannot inflate: " + layout.toJSON());
            }

            currentTemplate = fit.first;
            currentMap = fit.second;

            Log.d("HAPY", "best fit: "+currentMap);

            if(!currentMap.containsKey(layout.getId()))
            {
                throw new IllegalArgumentException("no key " + layout.getId() + " in " + currentMap);
            }

            DynamicView templateMap = find(currentTemplate, currentMap.get(layout.getId()));

            if(!templateMap.type.equals(layout.type))
            {
                throw new IllegalArgumentException(templateMap.type+ " != "+layout.type+" in "+templateMap+" , "+layout);
            }

            layout.xml_id = templateMap.xml_id;
            layout.view_id = templateMap.view_id;
            RemoteViews newView = new RemoteViews(context.getPackageName(), layout.xml_id);
            if(current != null)
            {
                if(parentViewId == 0)
                {
                    throw new IllegalArgumentException("parentViewId == 0");
                }

                current.addView(parentViewId, newView);
                root = false;
            }
            else
            {
                if(storage.size() != 0)
                {
                    throw new IllegalArgumentException("WHAT");
                }
                storage.add(newView);
            }
            current = newView;
        }

        if(layout.xml_id == 0)
        {
            throw new IllegalArgumentException("layout.xml_id == 0");
        }

        if(layout.view_id == 0)
        {
            throw new IllegalArgumentException("layout.view_id == 0");
        }


        for(RemoteMethodCall methodCall : layout.methodCalls)
        {
            methodCall.call(current, layout.view_id);
        }

        Intent clickIntent = new Intent(context, WidgetReceiver.class);
        clickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});

        if(isCollection(layout.type))
        {
            if(!root)
            {
                throw new IllegalArgumentException("listview not in root!: "+layout.toJSON());
            }
            clickIntent.putExtra(COLLECTION_ITEM_ID_EXTRA, layout.getId());
            current.setPendingIntentTemplate(layout.view_id, PendingIntent.getBroadcast(context, widgetId + (layout.getId() << 8), clickIntent, PendingIntent.FLAG_UPDATE_CURRENT));

            //prepare factory
            getFactory(context, widgetId, layout.xml_id, layout.view_id, layout.toJSON());

            Intent listintent = new Intent(context, Widget.class);
            listintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            listintent.putExtra(LIST_SERIALIZED_EXTRA, layout.toJSON());
            listintent.putExtra(XML_ID_EXTRA, layout.xml_id);
            listintent.putExtra(VIEW_ID_EXTRA, layout.view_id);
            listintent.setData(Uri.parse(listintent.toUri(Intent.URI_INTENT_SCHEME)));
            current.setRemoteAdapter(layout.view_id, listintent);

            Log.d("HAPY", "set remote adapter on " + layout.view_id+", "+layout.xml_id+" in dynamic "+layout.getId());
        }
        else
        {
            if(!inCollection)
            {
                clickIntent.putExtra(ITEM_ID_EXTRA, layout.getId());
                current.setOnClickPendingIntent(layout.view_id, PendingIntent.getBroadcast(context, widgetId + (layout.getId() << 8), clickIntent, PendingIntent.FLAG_UPDATE_CURRENT));
            }

            if(isGroup(layout.type))
            {
                for (DynamicView child : layout.children)
                {
                    newGenerate(context, widgetId, storage, current, child, layout.view_id, currentTemplate, currentMap, false, root);
                }
            }
        }
    }

    public DynamicView find(DynamicView root, int dynamicId)
    {
        if(dynamicId <= 0 || root == null)
        {
            return null;
        }
        if(root.getId() == dynamicId)
        {
            return root;
        }

        for(DynamicView child : root.children)
        {
            DynamicView view = find(child, dynamicId);
            if(view != null)
            {
                return view;
            }
        }
        return null;
    }

    public String handle(int widgetId, String widgetJson, int collectionId, int itemId, int collectionPosition)
    {
        Log.d("HAPY", "handling "+itemId+" in collection "+collectionId);

        //boolean callOnClick = true;

        if(updateListener == null)
        {
            return null;
        }

        if(collectionId != 0)
        {
            Log.d("HAPY", "calling listener onItemClick");
            String ret = updateListener.onItemClick(widgetId, widgetJson, collectionId, itemId, collectionPosition);
            //TODO cannot change layout and not suppress click
            if(ret != null)
            {
                Log.d("HAPY", "suppressing click on "+itemId);
                return ret;
            }
        }

        Log.d("HAPY", "calling listener onClick");
        return updateListener.onClick(widgetId, widgetJson, itemId);
    }

    public static String getSetterMethod(String type, String method)
    {
        return typeToRemotableMethod.get(type).get(method);
    }

    public static HashMap<String, String> getRemotableMethods(String type)
    {
        Class<?> clazz = typeToClass.get(type);

        HashMap<String, String> methods = new HashMap<>();
        for(Method method : clazz.getMethods())
        {
            Annotation[] annotations = method.getAnnotations();
            boolean remotable = false;
            for(Annotation annotation : annotations)
            {
                if(annotation.annotationType().getName().equals("android.view.RemotableViewMethod"))
                {
                    remotable = true;
                    break;
                }
            }

            if(remotable)
            {
                if(method.getParameterTypes().length != 1)
                {
                    continue; //TODO?
                }
                methods.put(method.getName(), parameterToSetter.get(method.getParameterTypes()[0]));
            }
        }
        return methods;
    }

    private void java_widget()
    {
        registerOnWidgetUpdate(new WidgetUpdateListener()
            {
                @Override
                public String onCreate(int widgetId)
                {
                    return initWidget(widgetId);
                }

                @Override
                public String onUpdate(int widgetId, String currentView)
                {
                    DynamicView out = updateWidget(widgetId, DynamicView.fromJSON(currentView, true));
                    if(out != null)
                    {
                        return out.toJSON();
                    }
                    return null;
                }

                @Override
                public String onItemClick(int widgetId, String root, int collectionId, int id, int position)
                {
                    Log.d("HAPY", "on item click: "+collectionId+" "+id+" "+position);
                    return null;
                }

                @Override
                public String onClick(int widgetId, String root, int id)
                {
                    Log.d("HAPY", "on click: "+id);
                    return null;
                }
            });
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent)
    {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        int xmlId = intent.getIntExtra(XML_ID_EXTRA, 0);
        int viewId = intent.getIntExtra(VIEW_ID_EXTRA, 0);
        Log.d("HAPY", "onGetViewFactory: "+xmlId+", "+ viewId);
        String list = intent.getStringExtra(LIST_SERIALIZED_EXTRA);
        return getFactory(this, widgetId, xmlId, viewId, list);
    }

    public void registerOnWidgetUpdate(WidgetUpdateListener listener)
    {
        updateListener = listener;
    }

    boolean started = false;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if(!started)
        {
            started = true;

            makePython();

            System.load(new File(getFilesDir(), "/lib/libpython3.6m.so.1.0").getAbsolutePath());
            System.loadLibrary("native");

            pythonInit(getFilesDir().getAbsolutePath());

            pythonRun("/sdcard/main.py", Widget.this);

            //java_widget();
        }

        if(intent != null)
        {
            Intent widgetIntent = intent.getParcelableExtra(WIDGET_INTENT);
            if (widgetIntent != null && AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(widgetIntent.getAction()))
            {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
                // Get all ids
                ComponentName thisWidget = new ComponentName(this, WidgetReceiver.class);
                int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

                int eventWidgetId = widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)[0];
                int dynamicId = widgetIntent.getIntExtra(ITEM_ID_EXTRA, 0);
                Log.d("HAPY", "got intent: " + eventWidgetId + " " + dynamicId + " (" + widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS).length + ")");

                String eventWidget = widgets.get(eventWidgetId);
                if (eventWidget != null)
                {
                    try
                    {
                        String newwidget = handle(eventWidgetId, eventWidget, widgetIntent.getIntExtra(COLLECTION_ITEM_ID_EXTRA, 0), dynamicId, widgetIntent.getIntExtra(COLLECTION_POSITION_EXTRA, -1));
                        widgets.put(eventWidgetId, newwidget);
                    }
                    catch (Exception e)
                    {
                        Log.d("HAPY", "error in handle");
                    }
                }

                for (int widgetId : allWidgetIds)
                {
                    Log.d("HAPY", "update: " + widgetId);

                    String widget = widgets.get(widgetId);
                    if (widget == null)
                    {
                        if (updateListener != null)
                        {
                            Log.d("HAPY", "calling listener onCreate");
                            try
                            {
                                widget = updateListener.onCreate(widgetId);
                            }
                            catch (Exception e)
                            {
                                Log.d("HAPY", "error in listener onCreate");
                            }
                        }
                        if (widget == null)
                        {
                            Log.d("HAPY", "doing default onCreate");
                            widget = "        {" +
                                    "            \"id\": 1," +
                                    "            \"type\": \"TextView\"," +
                                    "            \"methodCalls\":" +
                                    "            [" +
                                    "                {" +
                                    "                    \"identifier\": \"setText\"," +
                                    "                    \"method\": \"setCharSequence\"," +
                                    "                    \"arguments\":" +
                                    "                    [" +
                                    "                        \"setText\"," +
                                    "                        \"Error\"" +
                                    "                    ]" +
                                    "                }" +
                                    "            ]" +
                                    "        }";
                        }
                        widgets.put(widgetId, widget);
                    }

                    if (updateListener != null)
                    {
                        Log.d("HAPY", "calling listener onUpdate");
                        try
                        {
                            String newwidget = updateListener.onUpdate(widgetId, widget);
                            if (newwidget != null)
                            {
                                widget = newwidget;
                                widgets.put(widgetId, widget);
                            }
                        }
                        catch (Exception e)
                        {
                            Log.d("HAPY", "error in listener onUpdate");
                        }
                    }
                    Log.d("HAPY", widget);

                    try
                    {
                        RemoteViews view = newGenerate(this, widgetId, DynamicView.fromJSON(widgets.get(widgetId), true), false, true); //TODO extractAll temporary true
                        appWidgetManager.updateAppWidget(widgetId, view);
                        appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.e2);
                    }
                    catch (InvocationTargetException | IllegalAccessException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    //-----------------------------------python--------------------------------------------------------------

    public static void printFnames(File sDir){
        File[] faFiles = sDir.listFiles();
        for(File file: faFiles){
            Log.d("HAPY", file.getAbsolutePath());
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
                Log.e("HAPY", line);
            }
            fileReader.close();
        }
        catch(IOException e)
        {
            Log.e("HAPY", "exception", e);
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
                Log.d("HAPY", line);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void runProcess(String[] command)
    {
        try
        {
            Process process = Runtime.getRuntime().exec(command);
            printAll(process.getInputStream());
            process.waitFor();
        }
        catch (IOException|InterruptedException e)
        {
            Log.e("HAPY", "exception", e);
        }
    }

    public void makePython()
    {
        if(!new File(getFilesDir(), "lib/libpython3.so").exists())
        {
            Log.d("HAPY", "unpacking python");

            File newfile = new File(getFilesDir(), "test");
            //TODO without creating?

            runProcess(new String[]{"sh", "-c", "tar -xf /sdcard/python.tar -C " + getFilesDir().getAbsolutePath()+" 2>&1"});

            //printFnames(getFilesDir());
            Log.d("HAPY", "done unpacking python");
        }
        else
        {
            Log.d("HAPY", "not unpacking python");
        }
    }

    protected static native int pythonInit(String pythonpath);
    protected static native int pythonRun(String script, Object obj);
    protected static native Object pythonCall(Object... args) throws Throwable;
}
