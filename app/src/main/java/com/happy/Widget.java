package com.happy;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

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
    static
    {
        typeToLayout.put("TextView", new Pair<>(R.layout.text_layout, ChildrenType.NO_CHILDS));
        typeToLayout.put("Layout", new Pair<>(R.layout.widget_layout, ChildrenType.GROUP));
        typeToLayout.put("ListView", new Pair<>(R.layout.listview_layout, ChildrenType.COLLECTION));
    }
    public static SparseArray<HashSet<String>> remotableMethods = new SparseArray<>();

    interface Variable
    {
        void Call(Context context, RemoteViews view, String remoteMethod, int id) throws InvocationTargetException, IllegalAccessException;
    }

    class SetVariable implements Variable
    {
        String type;
        Object var;

        public SetVariable(String type, Object o)
        {
            type = "set" + type.substring(0, 1).toUpperCase() + type.substring(1);
            var = o;
        }
        public SetVariable(boolean bool)
        {
            type = "setBoolean";
            var = bool;
        }
        public SetVariable(byte b)
        {
            type = "setByte";
            var = b;
        }
        public SetVariable(short s)
        {
            type = "setShort";
            var = s;
        }
        public SetVariable(int i)
        {
            type = "setInt";
            var = i;
        }
        public SetVariable(long l)
        {
            type = "setLong";
            var = l;
        }
        public SetVariable(float f)
        {
            type = "setFloat";
            var = f;
        }
        public SetVariable(double d)
        {
            type = "setDouble";
            var = d;
        }
        public SetVariable(char c)
        {
            type = "setChar";
            var = c;
        }
        public SetVariable(String string)
        {
            type = "setString";
            var = string;
        }
        public SetVariable(CharSequence charSequence)
        {
            type = "setCharSequence";
            var = charSequence;
        }
        public SetVariable(Uri uri)
        {
            type = "setUri";
            var = uri;
        }
        public SetVariable(Bitmap bitmap)
        {
            type = "setBitmap";
            var = bitmap;
        }
        public SetVariable(Bundle bundle)
        {
            type = "setBundle";
            var = bundle;
        }
        public SetVariable(Intent intent)
        {
            type = "setIntent";
            var = intent;
        }
        public SetVariable(Icon icon)
        {
            type = "setIcon";
            var = icon;
        }

        public void Call(Context context, RemoteViews view, String remoteMethod, int id) throws InvocationTargetException, IllegalAccessException
        {
            Method[] methods = RemoteViews.class.getMethods();
            Method method = null;
            for(Method _method : methods)
            {
                if(_method.getName().equalsIgnoreCase(type))
                {
                    method = _method;
                    break;
                }
            }
            if(method == null)
            {
                throw new RuntimeException("no such type");
            }

            if(remotableMethods.get(view.getLayoutId()) == null)
            {
                remotableMethods.put(view.getLayoutId(), getRemotableMethods(context, view.getLayoutId()));
            }
            if(!remotableMethods.get(view.getLayoutId()).contains(remoteMethod))
            {
                throw new RuntimeException("method "+remoteMethod+" not remotable");
            }

            Log.d("HAPY", "calling "+id+" "+remoteMethod+" "+method.getName()+" "+var);
            method.invoke(view, id, remoteMethod, var);
        }
    }

    class SimpleVariables implements Variable
    {
        Object[] arguments;
        public SimpleVariables(Object... args)
        {
            arguments = args;
        }

        public void Call(Context context, RemoteViews view, String remoteMethod, int id) throws InvocationTargetException, IllegalAccessException
        {
            Method[] methods = RemoteViews.class.getMethods();
            Method method = null;
            for(Method _method : methods)
            {
                if(_method.getName().equalsIgnoreCase(remoteMethod))
                {
                    method = _method;
                    break;
                }
            }
            if(method == null)
            {
                throw new RuntimeException("no such type");
            }

            Log.d("HAPY", "calling "+id+" "+remoteMethod+" "+method.getName());
            switch (arguments.length)
            {
                case 0:
                {
                    method.invoke(view, id);
                    break;
                }
                case 1:
                {
                    method.invoke(view, id, arguments[0]);
                    break;
                }
                case 2:
                {
                    method.invoke(view, id, arguments[0], arguments[1]);
                    break;
                }
                case 3:
                {
                    method.invoke(view, id, arguments[0], arguments[1], arguments[2]);
                    break;
                }
                case 4:
                {
                    method.invoke(view, id, arguments[0], arguments[1], arguments[2], arguments[3]);
                    break;
                }
            }

        }
    }

    interface OnClick
    {
        void onClick(DynamicView view);
    }

    interface OnItemClick
    {
        boolean onClick(DynamicView collection, DynamicView item, int position);
    }

    class DynamicView
    {
        public ArrayList<DynamicView> children = new ArrayList<>();
        public String type;
        public HashMap<String, Variable> attrs = new HashMap<>();
        public int id;
        public OnClick onClick;
        public OnItemClick onItemClick;
    }

    SparseArray<DynamicView> widgets = new SparseArray<>();

    public DynamicView initWidget(int widgetId)
    {
        DynamicView widget;
        widget = new DynamicView();
        widget.type = "ListView";
        widget.id = 0;

        widget.onItemClick = new OnItemClick()
        {
            @Override
            public boolean onClick(DynamicView collection, DynamicView item, int position)
            {
                Log.d("HAPY", "on item click!!  "+position);
                return false;
            }
        };

        DynamicView text1 = new DynamicView();
        text1.type = "TextView";
        text1.attrs.put("setText", new SetVariable((CharSequence)"dfg"));
        text1.attrs.put("setTextViewTextSize", new SimpleVariables(TypedValue.COMPLEX_UNIT_SP, 30));
        text1.id = 1;
        text1.onClick = new OnClick()
        {
            @Override
            public void onClick(DynamicView view)
            {
                Log.d("HAPY", "onclick1!!");
                String ran = new Random().nextInt(100)+"";
                view.attrs.put("setText", new SetVariable((CharSequence)ran));
            }
        };

        DynamicView text2 = new DynamicView();
        text2.type = "TextView";
        text2.attrs.put("setText", new SetVariable((CharSequence)"xcv"));
        text2.attrs.put("setTextViewTextSize", new SimpleVariables(TypedValue.COMPLEX_UNIT_SP, 30));
        text2.id = 2;
        text2.onClick = new OnClick()
        {
            @Override
            public void onClick(DynamicView view)
            {
                Log.d("HAPY", "onclick2!!");
                String ran = "" + (char)('A' + new Random().nextInt(26));
                view.attrs.put("setText", new SetVariable((CharSequence)ran));
            }
        };

//        DynamicView layout1 = new DynamicView();
//        layout1.type = "Layout";
//        layout1.id = 3;
//
//        DynamicView layout2 = new DynamicView();
//        layout2.type = "Layout";
//        layout2.id = 4;
//
//        layout1.children.add(text1);
//        layout2.children.add(text2);
//
//        widget.children.add(layout1);
//        widget.children.add(layout2);

        widget.children.add(text1);
        widget.children.add(text2);


        widgets.put(widgetId, widget);
        return widget;
    }

    public RemoteViews generate(Context context, DynamicView view, int widgetId, boolean collectionParent) throws InvocationTargetException, IllegalAccessException
    {
        Pair<Integer, ChildrenType> layout = typeToLayout.get(view.type);
        RemoteViews remoteView = new RemoteViews(context.getPackageName(), layout.first);

        for(Map.Entry<String,Variable> attr : view.attrs.entrySet())
        {
            attr.getValue().Call(context, remoteView, attr.getKey(), R.id.element);
        }

        if(view.onClick != null && !collectionParent)
        {
            Intent intent = new Intent(context, WidgetReceiver.class);

            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
            intent.putExtra(ITEM_ID_EXTRA, view.id);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                    widgetId + (view.id << 8), intent, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteView.setOnClickPendingIntent(R.id.element, pendingIntent);
        }

        if(layout.second != ChildrenType.NO_CHILDS)
        {
            if(layout.second == ChildrenType.COLLECTION)
            {
                Intent clickintent = new Intent(context, WidgetReceiver.class);
                clickintent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                clickintent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
                clickintent.putExtra(COLLECTION_ITEM_ID_EXTRA, view.id);

                PendingIntent clickPendingIntent = PendingIntent.getBroadcast(context,
                        widgetId + (view.id << 8), clickintent, PendingIntent.FLAG_UPDATE_CURRENT);
                remoteView.setPendingIntentTemplate(R.id.element, clickPendingIntent);

                Intent intent = new Intent(context, Widget.class);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                intent.putExtra(ITEM_ID_EXTRA, view.id);
                intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
                remoteView.setRemoteAdapter(R.id.element, intent);
            }
            else
            {
                remoteView.removeAllViews(R.id.element);
                for (DynamicView child : view.children)
                {
                    RemoteViews remoteChild = generate(context, child, widgetId, false);
                    Log.d("HAPY", "adding child");
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
        if(root.id == dynamicId)
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

    public void handle(DynamicView widget, int collectionId, int dynamicId, int collectionPosition)
    {
        Log.d("HAPY", "handling "+dynamicId+" of "+collectionId);
        DynamicView view = find(widget, dynamicId);
        if(view == null)
        {
            Log.d("HAPY", "no view?");
            return;
        }

        boolean callOnClick = true;

        DynamicView collectionView = find(widget, collectionId);
        if(collectionView != null && collectionView.onItemClick != null)
        {
            callOnClick = !collectionView.onItemClick.onClick(collectionView, view, collectionPosition);
        }
        if(!callOnClick || view.onClick == null)
        {
            Log.d("HAPY", "no onclick... !!"+callOnClick+" "+view.id);
            return;
        }

        view.onClick.onClick(view);
    }

    public HashSet<String> getRemotableMethods(Context context, int layoutid)
    {
        View layout = LayoutInflater.from(context).inflate(layoutid, null);
        View view = layout.findViewById(R.id.element);

        HashSet<String> methods = new HashSet<>();
        for(Method method : view.getClass().getMethods())
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
                methods.add(method.getName());
            }
        }
        return methods;
    }

    class ListFactory implements RemoteViewsFactory
    {
        DynamicView item;
        Context context;
        int widgetId;

        public ListFactory(Context ctx, int widget, DynamicView i)
        {
            item = i;
            widgetId = widget;
            context = ctx;
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
            return item.children.size();
        }

        @Override
        public RemoteViews getViewAt(int position)
        {
            Log.d("HAPY", "get view at "+position);
            try
            {
                DynamicView view = item.children.get(position);
                RemoteViews remote = generate(context, view, widgetId, true);

                Intent intent = new Intent(context, WidgetReceiver.class);
                intent.putExtra(ITEM_ID_EXTRA, view.id);
                intent.putExtra(COLLECTION_POSITION_EXTRA, position);
                remote.setOnClickFillInIntent(R.id.element, intent);
                return remote;
            }
            catch (InvocationTargetException|IllegalAccessException e)
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
        DynamicView widget = widgets.get(widgetId);
        if(widget == null)
        {
            Log.d("HAPY", "NO widget");
        }
        DynamicView view = find(widget, dynamicId);
        if(view == null)
        {
            Log.d("HAPY", "HERE");
            return null;
        }
        return new ListFactory(this, widgetId, view);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
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
                handle(eventWidget, widgetIntent.getIntExtra(COLLECTION_ITEM_ID_EXTRA, -1), dynamicId, widgetIntent.getIntExtra(COLLECTION_POSITION_EXTRA, -1));
            }

            for (int widgetId : allWidgetIds) {
                Log.d("HAPY", "update: "+widgetId);

                DynamicView widget = widgets.get(widgetId);
                if(widget == null)
                {
                    widget = initWidget(widgetId);
                }

                try
                {
                    RemoteViews view = generate(this, widget, widgetId, false);
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
}
