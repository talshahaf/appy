package com.happy;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
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
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RemoteViews;

public class Widget extends AppWidgetProvider {

    private static final String ACTION_CLICK = "ACTION_CLICK";

    public static HashMap<String, Pair<Integer, Boolean>> typeToLayout = new HashMap<>();
    static
    {
        typeToLayout.put("TextView", new Pair<>(R.layout.text_layout, false));
        typeToLayout.put("Layout", new Pair<>(R.layout.widget_layout, true));
    }
    public static HashMap<Integer, HashSet<String>> remotableMethods = new HashMap<>();

    class Variable
    {
        String type;
        Object var;

        public Variable(String type, Object o)
        {
            type = "set" + type.substring(0, 1).toUpperCase() + type.substring(1);
            var = o;
        }
        public Variable(boolean bool)
        {
            type = "setBoolean";
            var = bool;
        }
        public Variable(byte b)
        {
            type = "setByte";
            var = b;
        }
        public Variable(short s)
        {
            type = "setShort";
            var = s;
        }
        public Variable(int i)
        {
            type = "setInt";
            var = i;
        }
        public Variable(long l)
        {
            type = "setLong";
            var = l;
        }
        public Variable(float f)
        {
            type = "setFloat";
            var = f;
        }
        public Variable(double d)
        {
            type = "setDouble";
            var = d;
        }
        public Variable(char c)
        {
            type = "setChar";
            var = c;
        }
        public Variable(String string)
        {
            type = "setString";
            var = string;
        }
        public Variable(CharSequence charSequence)
        {
            type = "setCharSequence";
            var = charSequence;
        }
        public Variable(Uri uri)
        {
            type = "setUri";
            var = uri;
        }
        public Variable(Bitmap bitmap)
        {
            type = "setBitmap";
            var = bitmap;
        }
        public Variable(Bundle bundle)
        {
            type = "setBundle";
            var = bundle;
        }
        public Variable(Intent intent)
        {
            type = "setIntent";
            var = intent;
        }
        public Variable(Icon icon)
        {
            type = "setIcon";
            var = icon;
        }

        void Call(RemoteViews view, String remoteMethod, int id) throws InvocationTargetException, IllegalAccessException
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

            Log.d("HAPY", "calling "+id+" "+remoteMethod+" "+method.getName()+" "+var);
            method.invoke(view, id, remoteMethod, var);
        }
    }

    interface OnClick
    {
        void onClick(DynamicView view);
    }

    class DynamicView
    {
        public ArrayList<DynamicView> children = new ArrayList<>();
        public String type;
        public ArrayList<Pair<String, Variable>> attrs = new ArrayList<>();
        public int id;
        public OnClick onClick;
    }

    //SparseArray<DynamicView> widgets = new SparseArray<>();

    DynamicView widget;
    {
        widget = new DynamicView();
        widget.type = "Layout";
        widget.id = 0;

        DynamicView text1 = new DynamicView();
        text1.type = "TextView";
        text1.attrs.add(new Pair<>("setText", new Variable((CharSequence)"dfg")));
        text1.id = 1;
        text1.onClick = new OnClick()
        {
            @Override
            public void onClick(DynamicView view)
            {
                Log.d("HAPY", "onclick!!");
                view.attrs.clear();
                String ran = new Random().nextInt(100)+"";
                view.attrs.add(new Pair<>("setText", new Variable((CharSequence)ran)));
            }
        };

        DynamicView text2 = new DynamicView();
        text2.type = "TextView";
        text2.attrs.add(new Pair<>("setText", new Variable((CharSequence)"xcv")));
        text1.id = 2;

        widget.children.add(text1);
        widget.children.add(text2);
    }

    public RemoteViews generate(Context context, DynamicView view, int widgetId) throws InvocationTargetException, IllegalAccessException
    {
        Pair<Integer, Boolean> layout = typeToLayout.get(view.type);
        RemoteViews remoteView = new RemoteViews(context.getPackageName(), layout.first);

        if(!remotableMethods.containsKey(layout.first))
        {
            remotableMethods.put(layout.first, getRemotableMethods(context, layout.first));
        }

        for(Pair<String,Variable> attr : view.attrs)
        {
            if(!remotableMethods.get(layout.first).contains(attr.first))
            {
                throw new RuntimeException("method "+attr.first+" not remotable");
            }
            attr.second.Call(remoteView, attr.first, R.id.element);
        }

        if(view.onClick != null)
        {
            Intent intent = new Intent(context, Widget.class);

            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
            intent.putExtra(ACTION_CLICK, view.id);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteView.setOnClickPendingIntent(R.id.element, pendingIntent);
        }

        if(layout.second)
        {
            remoteView.removeAllViews(R.id.element);
            for (DynamicView child : view.children)
            {
                RemoteViews remoteChild = generate(context, child, widgetId);
                Log.d("HAPY", "adding child");
                remoteView.addView(R.id.element, remoteChild);
            }
        }
        return remoteView;
    }

    public DynamicView find(DynamicView root, int dynamicId)
    {
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

    public void handle(int widgetId, int dynamicId)
    {
        DynamicView view = find(widget, dynamicId);
        if(view == null)
        {
            return;
        }
        if(view.onClick == null)
        {
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

    @Override
    public void onReceive(Context context, Intent intent) {
        if(AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction()))
        {
            int[] widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            if(widgetIds.length > 0)
            {
                int dynamicId = intent.getIntExtra(ACTION_CLICK, -1);
                Log.d("HAPY", "got intent: " + widgetIds[0] + " " + dynamicId);
                handle(widgetIds[0], dynamicId);
            }
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {

        // Get all ids
        ComponentName thisWidget = new ComponentName(context,
                Widget.class);
        int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
        for (int widgetId : allWidgetIds) {
            Log.d("HAPY", "update");
//            RemoteViews l = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
//            RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.text_layout);
//            view.setCharSequence(R.id.element, "setText", "cfvb");
//            l.addView(R.layout.);
//            appWidgetManager.updateAppWidget(widgetId, view);


            try
            {
                RemoteViews view = generate(context, widget, widgetId);
                appWidgetManager.updateAppWidget(widgetId, view);
            }
            catch (InvocationTargetException|IllegalAccessException e)
            {
                e.printStackTrace();
            }
        }
    }
}
