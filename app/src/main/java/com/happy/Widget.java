package com.happy;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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


    enum ChildrenType
    {
        NO_CHILDS,
        GROUP,
        COLLECTION,
    }

    public static HashMap<String, Pair<Integer, ChildrenType>> typeToLayout = new HashMap<>();
    public static HashMap<String, Class<?>> typeToClass = new HashMap<>();
    public static HashMap<String, HashMap<String, String>> typeToRemotableMethod = new HashMap<>();
    public static HashMap<Class<?>, String> parameterToSetter = new HashMap<>();
    static
    {

        typeToLayout.put("FrameLayout", new Pair<>(R.layout.framelayout_layout, ChildrenType.GROUP));
        typeToLayout.put("LinearLayout", new Pair<>(R.layout.linearlayout_layout, ChildrenType.GROUP));
        typeToLayout.put("RelativeLayout", new Pair<>(R.layout.relativelayout_layout, ChildrenType.GROUP));
        typeToLayout.put("GridLayout", new Pair<>(R.layout.gridlayout_layout, ChildrenType.GROUP));
        typeToLayout.put("AnalogClock", new Pair<>(R.layout.analogclock_layout, ChildrenType.NO_CHILDS));
        typeToLayout.put("Button", new Pair<>(R.layout.button_layout, ChildrenType.NO_CHILDS));
        typeToLayout.put("Chronometer", new Pair<>(R.layout.chronometer_layout, ChildrenType.NO_CHILDS));
        typeToLayout.put("ImageButton", new Pair<>(R.layout.imagebutton_layout, ChildrenType.NO_CHILDS));
        typeToLayout.put("ImageView", new Pair<>(R.layout.imageview_layout, ChildrenType.NO_CHILDS));
        typeToLayout.put("ProgressBar", new Pair<>(R.layout.progressbar_layout, ChildrenType.NO_CHILDS));
        typeToLayout.put("TextView", new Pair<>(R.layout.textview_layout, ChildrenType.NO_CHILDS));
        typeToLayout.put("ViewFlipper", new Pair<>(R.layout.viewflipper_layout, ChildrenType.GROUP));
        typeToLayout.put("ListView", new Pair<>(R.layout.listview_layout, ChildrenType.COLLECTION));
        typeToLayout.put("GridView", new Pair<>(R.layout.gridview_layout, ChildrenType.COLLECTION));
        typeToLayout.put("StackView", new Pair<>(R.layout.stackview_layout, ChildrenType.COLLECTION));
        typeToLayout.put("AdapterViewFlipper", new Pair<>(R.layout.adapterviewflipper_layout, ChildrenType.COLLECTION));

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

        for(String type: typeToLayout.keySet())
        {
            typeToRemotableMethod.put(type, getRemotableMethods(type));
        }
    }

    SparseArray<DynamicView> widgets = new SparseArray<>();
    WidgetUpdateListener updateListener = null;

    public DynamicView initWidget(int widgetId) throws Exception
    {
        return new DynamicView("ListView");
    }

    public DynamicView updateWidget(int widgetId, DynamicView root) throws Exception
    {
        root.children.clear();
        for(int i = 0; i < 100; i++)
        {
            DynamicView text1 = new DynamicView("TextView");
            text1.methodCalls.add(new RemoteMethodCall("setText", getSetterMethod(text1.type, "setText"), "setText", ""+new Random().nextInt(1000)));
            text1.methodCalls.add(new RemoteMethodCall("setTextViewTextSize", "setTextViewTextSize", TypedValue.COMPLEX_UNIT_SP, 30));
//            text1.onClick = new DynamicView.OnClick()
//            {
//                @Override
//                public void onClick(DynamicView view)
//                {
//                }
//            };
            root.children.add(text1);
        }
        return null;
    }

    public static int getId(Class<?> cls, String name)
    {
        try
        {
            return (Integer)cls.getField(name).get(null);
        }
        catch (IllegalAccessException|NoSuchFieldException e)
        {
            //shouldn't happen
            e.printStackTrace();
        }
        return -1;
    }

    public RemoteViews generate(Context context, DynamicView view, int widgetId, boolean collectionParent, boolean isRoot) throws InvocationTargetException, IllegalAccessException
    {
        Pair<Integer, ChildrenType> layout = typeToLayout.get(view.type);
        RemoteViews remoteView = new RemoteViews(context.getPackageName(), layout.first);

        for(RemoteMethodCall methodCall : view.methodCalls)
        {
            methodCall.call(remoteView, R.id.element);
        }

        if(!collectionParent && layout.second != ChildrenType.COLLECTION) //TODO check if you can never setOnClick on collection
        {
            Intent intent = new Intent(context, WidgetReceiver.class);

            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
            intent.putExtra(ITEM_ID_EXTRA, view.getId());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                    widgetId + (view.getId() << 8), intent, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteView.setOnClickPendingIntent(R.id.element, pendingIntent);
        }

        if(layout.second != ChildrenType.NO_CHILDS)
        {
            if(layout.second == ChildrenType.COLLECTION)
            {
                Log.d("HAPY", "adding list "+view.getId());
                Intent clickintent = new Intent(context, WidgetReceiver.class);
                clickintent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                clickintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
                clickintent.putExtra(COLLECTION_ITEM_ID_EXTRA, view.getId());

                PendingIntent clickPendingIntent = PendingIntent.getBroadcast(context,
                        widgetId + (view.getId() << 8), clickintent, PendingIntent.FLAG_UPDATE_CURRENT);
                remoteView.setPendingIntentTemplate(R.id.element, clickPendingIntent);

                Intent intent = new Intent(context, Widget.class);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                intent.putExtra(ITEM_ID_EXTRA, view.getId());
                intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
                remoteView.setRemoteAdapter(R.id.element, intent);
            }
            else
            {
                remoteView.removeAllViews(R.id.element);
                for (DynamicView child : view.children)
                {
                    RemoteViews remoteChild = generate(context, child, widgetId, false, false);
                    Log.d("HAPY", "adding child "+child.getId()+" "+child.type);
                    remoteView.addView(R.id.element, remoteChild);
                }
            }
        }
        return remoteView;
    }

    public DynamicView find(DynamicView root, int dynamicId)
    {
        if(dynamicId == -1 || root == null)
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

    public DynamicView handle(int widgetId, DynamicView widget, int collectionId, int dynamicId, int collectionPosition)
    {
        Log.d("HAPY", "handling "+dynamicId+" in collection "+collectionId);
        DynamicView view = find(widget, dynamicId);
        if(view == null)
        {
            Log.d("HAPY", "no view?");
            return null;
        }

        boolean callOnClick = true;

        if(updateListener == null)
        {
            return null;
        }

        if(collectionId != -1)
        {
            DynamicView ret = updateListener.onItemClick(widgetId, widget, collectionId, dynamicId, collectionPosition);
            //TODO cannot change layout and not suppress click
            if(ret != null)
            {
                Log.d("HAPY", "suppressing click on "+view.getId());
                return ret;
            }
        }

        return updateListener.onClick(widgetId, widget, dynamicId);
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

    class ListFactory implements RemoteViewsFactory
    {
        DynamicView item;
        Context context;
        int widgetId;
        int itemId;

        public ListFactory(Context ctx, int widgetId, int itemId)
        {
            this.widgetId = widgetId;
            this.itemId = itemId;
            context = ctx;

            DynamicView widget = widgets.get(widgetId);
            if(widget != null)
            {
                item = find(widget, itemId);
            }
            Log.d("HAPY", "list factory item is "+item);
        }

        @Override
        public void onCreate()
        {

        }

        @Override
        public void onDataSetChanged()
        {
            DynamicView widget = widgets.get(widgetId);
            if (widget != null)
            {
                item = find(widget, itemId);
            }
            if(item != null)
            {
                Log.d("HAPY", "update list factory item is " + item.children.size() + " " + item);
            }
        }

        @Override
        public void onDestroy()
        {

        }

        @Override
        public int getCount()
        {
            if (item == null)
            {
                return 0;
            }
            int count = item.children.size();
            Log.d("HAPY", "get count called: " + count);
            return count;
        }

        @Override
        public RemoteViews getViewAt(int position)
        {
            Log.d("HAPY", "get view at " + position);

            try
            {
                if (item != null)
                {
                    DynamicView view = item.children.get(position);
                    RemoteViews remote = generate(context, view, widgetId, true, false);

                    Intent intent = new Intent(context, WidgetReceiver.class);
                    intent.putExtra(ITEM_ID_EXTRA, view.getId());
                    intent.putExtra(COLLECTION_POSITION_EXTRA, position);
                    remote.setOnClickFillInIntent(R.id.element, intent);
                    return remote;
                }
            }
            catch (InvocationTargetException | IllegalAccessException e)
            {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public RemoteViews getLoadingView()
        {
            return null;
        }

        @Override
        public int getViewTypeCount()
        {
            Log.d("HAPY", "get view type count called");
            if (item == null)
            {
                return 0;
            }
            return item.children.size();
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

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent)
    {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        int dynamicId = intent.getIntExtra(ITEM_ID_EXTRA, -1);
        Log.d("HAPY", "onGetViewFactory: "+widgetId + " "+dynamicId);
        return new ListFactory(this, widgetId, dynamicId);
    }

    public Widget()
    {
        super();
    }

    public void registerOnWidgetUpdate(WidgetUpdateListener listener)
    {
        updateListener = listener;
    }

    boolean started = false;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d("HAPY", "call");
        if(!started)
        {
            started = true;

            makePython();

            System.load(new File(getFilesDir(), "/lib/libpython3.6m.so.1.0").getAbsolutePath());
            System.loadLibrary("native");

            pythonInit(getFilesDir().getAbsolutePath());

            pythonRun("/sdcard/main.py", Widget.this);

//            registerOnWidgetUpdate(new WidgetUpdateListener()
//            {
//                @Override
//                public DynamicView onCreate(int widgetId)
//                {
//                    try
//                    {
//                        return initWidget(widgetId);
//                    }
//                    catch (Exception e)
//                    {
//                        e.printStackTrace();
//                    }
//                    return null;
//                }
//
//                @Override
//                public DynamicView onUpdate(int widgetId, DynamicView currentView)
//                {
//                    try
//                    {
//                        return updateWidget(widgetId, currentView);
//                    }
//                    catch (Exception e)
//                    {
//                        e.printStackTrace();
//                    }
//                    return null;
//                }
//            });
        }

        Intent widgetIntent = intent.getParcelableExtra(WIDGET_INTENT);
        if(AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(widgetIntent.getAction()))
        {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            // Get all ids
            ComponentName thisWidget = new ComponentName(this, WidgetReceiver.class);
            int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

            int eventWidgetId = widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)[0];
            int dynamicId = widgetIntent.getIntExtra(ITEM_ID_EXTRA, -1);
            Log.d("HAPY", "got intent: " + eventWidgetId + " " + dynamicId + " ("+widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS).length+")");
            DynamicView eventWidget = widgets.get(eventWidgetId);
            if(eventWidget != null)
            {
                DynamicView newwidget = handle(eventWidgetId, eventWidget, widgetIntent.getIntExtra(COLLECTION_ITEM_ID_EXTRA, -1), dynamicId, widgetIntent.getIntExtra(COLLECTION_POSITION_EXTRA, -1));
                if(newwidget != null)
                {
                    widgets.put(eventWidgetId, newwidget);
                }
            }

            for (int widgetId : allWidgetIds) {
                Log.d("HAPY", "update: "+widgetId);

                DynamicView widget = widgets.get(widgetId);
                if(widget == null)
                {
                    if(updateListener != null)
                    {
                        Log.d("HAPY", "calling listener onCreate");
                        widget = updateListener.onCreate(widgetId);
                    }
                    if(widget == null)
                    {
                        Log.d("HAPY", "doing default onCreate");
                        try
                        {
                            widget = new DynamicView("LinearLayout");
                        }
                        catch (Exception e)
                        {
                            //shouldn't happen
                        }
                    }
                    widgets.put(widgetId, widget);
                }

                if(updateListener != null)
                {
                    DynamicView newwidget = updateListener.onUpdate(widgetId, widget);
                    if(newwidget != null)
                    {
                        widget = newwidget;
                        widgets.put(widgetId, widget);
                    }
                }
                Log.d("HAPY", widget.toString());

                try
                {
                    RemoteViews view = generate(this, widgets.get(widgetId), widgetId, false, true);
                    appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.element);
                    appWidgetManager.updateAppWidget(widgetId, view);
                }
                catch (InvocationTargetException|IllegalAccessException e)
                {
                    e.printStackTrace();
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
