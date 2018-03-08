package com.appy;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
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

import org.json.JSONException;
import org.json.JSONObject;

public class Widget extends RemoteViewsService {
    private static final String ITEM_ID_EXTRA = "ITEM_ID";
    public  static final String WIDGET_INTENT = "WIDGET_INTENT";
    private static final String COLLECTION_ITEM_ID_EXTRA = "COLLECTION_ITEM_ID_EXTRA";
    private static final String COLLECTION_POSITION_EXTRA = "COLLECTION_POSITION_EXTRA";
    private static final String LIST_SERIALIZED_EXTRA = "LIST_SERIALIZED_EXTRA";
    private static final String XML_ID_EXTRA = "XML_ID_EXTRA";
    private static final String VIEW_ID_EXTRA = "VIEW_ID_EXTRA";
    private static final String WIDGET_ID_EXTRA = "WIDGET_ID_EXTRA";
    private static final int SPECIAL_WIDGET_ID = -2;

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

    HashMap<Integer, String> widgets = new HashMap<>();
    WidgetUpdateListener updateListener = null;

    //only supports identity
    public Attributes.AttributeValue attributeParse(String attributeValue)
    {
        attributeValue = attributeValue.replace(" ", "").replace("\r","").replace("\t","").replace("\n","").replace("*","");
        attributeValue = attributeValue.replace("-", "+-");

        String[] args = attributeValue.split("\\+");

        double amount = 0;
        ArrayList<Attributes.AttributeValue.Reference> references = new ArrayList<>();
        for(String arg : args)
        {
            if(arg.isEmpty())
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

            if(idx.first == -1)
            {
                amount += Double.parseDouble(arg);
                continue;
            }

            if(arg.charAt(idx.first + 1) != '(')
            {
                throw new IllegalArgumentException("expected '('");
            }

            Attributes.AttributeValue.Reference reference = new Attributes.AttributeValue.Reference();
            int parEnd = arg.indexOf(")", idx.first);
            String refId = arg.substring(idx.first+2, parEnd);
            reference.id = refId.equalsIgnoreCase("r") ? -1 : Integer.parseInt(refId);
            reference.type = idx.second;
            reference.factor = 1;
            reference.factor *= Double.parseDouble(idx.first > 0 ? arg.substring(0, idx.first) : "1");
            reference.factor *= Double.parseDouble(parEnd + 1 < arg.length() ? arg.substring(parEnd + 1) : "1");
            references.add(reference);
        }

        Attributes.AttributeValue ret = new Attributes.AttributeValue();
        ret.arguments.add(new Pair<>(references, amount));
        ret.function = Attributes.AttributeValue.Function.IDENTITY;
        return ret;
    }

    public ArrayList<DynamicView> initWidget(int widgetId)
    {
        Log.d("APPY", "initWidget "+widgetId);

        ArrayList<DynamicView> views = new ArrayList<>();

        DynamicView btn = new DynamicView("Button");
        btn.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(btn.type, "setText"), "setText", "initial button"));

        Attributes.AttributeValue _100px = attributeParse("100");
        Attributes.AttributeValue halfWidth = attributeParse("w(r)*0.5");
        Attributes.AttributeValue halfHeight = attributeParse("h(r)*0.5");

        Log.d("APPY", "100: "+_100px+"     btnWidth: "+halfWidth+"      btnHeight: "+halfHeight);

        btn.attributes.attributes.put(Attributes.Type.WIDTH, halfWidth);
        //btn.attributes.attributes.put(Attributes.Type.HEIGHT, halfHeight);

        DynamicView btn2 = new DynamicView("Button");
        btn2.attributes.attributes.put(Attributes.Type.TOP, halfHeight);
        btn2.attributes.attributes.put(Attributes.Type.WIDTH, attributeParse("w("+btn.getId()+")*0.5"));

        views.add(btn2);
        views.add(btn);



        DynamicView lst = new DynamicView("ListView");
        for(int i = 0; i < 10; i++)
        {
            DynamicView txt = new DynamicView("TextView");
            txt.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(btn.type, "setText"), "setText", "text"+i));
            ArrayList<DynamicView> listItem = new ArrayList<>();
            listItem.add(txt);
            lst.children.add(listItem);
        }

        lst.attributes.attributes.put(Attributes.Type.LEFT, halfWidth);
        lst.attributes.attributes.put(Attributes.Type.HEIGHT, _100px);

        views.add(lst);

        return views;
    }

    public ArrayList<DynamicView> updateWidget(int widgetId, ArrayList<DynamicView> root)
    {
        Log.d("APPY", "updateWidget "+widgetId);

        DynamicView view = root.get(0);
        view.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(view.type, "setText"), "setText", ""+new Random().nextInt(1000)));
        view.methodCalls.add(new RemoteMethodCall("setTextViewTextSize", false, "setTextViewTextSize", TypedValue.COMPLEX_UNIT_SP, 30));
        return root;
    }

    public boolean isCollection(String type)
    {
        return "ListView".equals(type) ||
                "GridView".equals(type) ||
                "StackView".equals(type) ||
                "AdapterViewFlipper".equals(type);
    }

    HashMap<Pair<Integer, Integer>, HashMap<Integer, ListFactory>> factories = new HashMap<>();
    public ListFactory getFactory(Context context, int widgetId, int xml, int view, String list)
    {
        Pair<Integer, Integer> key = new Pair<>(widgetId, xml);
        HashMap<Integer, ListFactory> inWidgetXml = factories.get(key);
        if(inWidgetXml == null)
        {
            inWidgetXml = new HashMap<>();
            factories.put(key, inWidgetXml);
        }

        ListFactory foundFactory = inWidgetXml.get(view);
        if(foundFactory == null)
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
            DynamicView view = DynamicView.fromJSON(list);
            //Log.d("APPY", "reloadFactory: "+widgetId + " " + view.view_id + ", " + view.xml_id + " dynamic: " + view.getId());
            this.list = view;
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
                if(position < list.children.size())
                {
                    ArrayList<DynamicView> dynamicViewCopy = DynamicView.fromJSONArray(DynamicView.toJSONString(list.children.get(position)));
                    RemoteViews remoteView = resolveDimensions(context, widgetId, dynamicViewCopy, true, list.actualWidth, list.actualHeight);
                    Intent fillIntent = new Intent(context, WidgetReceiver.class);
                    if(list.children.get(position).size() == 1)
                    {
                        fillIntent.putExtra(ITEM_ID_EXTRA, list.children.get(position).get(0).getId());
                    }
                    fillIntent.putExtra(COLLECTION_POSITION_EXTRA, position);
                    remoteView.setOnClickFillInIntent(R.id.listitem_root, fillIntent);
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

    public static float dipToPixels(Context context, float dipValue) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    }

    public int[] getWidgetDimensions(AppWidgetManager appWidgetManager, int androidWidgetId) //TODO optimize
    {
        Bundle bundle = appWidgetManager.getAppWidgetOptions(androidWidgetId);
        return new int[]{(int)dipToPixels(this, bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)),
                         (int)dipToPixels(this, bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT))};
    }

    public int selectRootView(int collections)
    {
        switch(collections)
        {
            case 0:
                return R.layout.simple_0_root;
            case 1:
                return R.layout.simple_1_root;
        }
        throw new IllegalArgumentException(collections + " collections are not supported");
    }

    public Pair<Integer, Integer> getListViewId(int n)
    {
        switch(n)
        {
            case 0:
                return new Pair<>(R.id.e0, R.id.l0);
//            case 1:
//                return new Pair<>(R.id.e1, R.id.l1);
        }
        throw new IllegalArgumentException((n + 1) + " collections are not supported");
    }

    public int typeToLayout(String type)
    {
        switch(type)
        {
            case "Button":
                return R.layout.simple_button;
            case "TextView":
                return R.layout.simple_textview;
        }
        throw new IllegalArgumentException("unknown type " + type);
    }

    public RemoteViews generate(Context context, int widgetId, ArrayList<DynamicView> dynamicList, boolean keepDescription, boolean inCollection) throws InvocationTargetException, IllegalAccessException
    {
        int collections = 0;
        for(DynamicView layout : dynamicList)
        {
            if(isCollection(layout.type))
            {
                collections++;
            }
        }

        if(collections > 0 && inCollection)
        {
            throw new IllegalArgumentException("cannot have collections in collection");
        }

        int root_xml = selectRootView(collections);
        int elements_id = R.id.elements;

        if(inCollection)
        {
            //can only be collections == 0
            //this fixes a bug when using the same id? i think it's in removeAllViews or in addView
            root_xml = R.layout.simple_0_item;
            elements_id = R.id.listitem_elements;
        }

        RemoteViews rootView = new RemoteViews(context.getPackageName(), root_xml);
        rootView.removeAllViews(elements_id);

        int collectionCounter = 0;

        for(DynamicView layout : dynamicList)
        {
            RemoteViews remoteView;
            if(isCollection(layout.type))
            {
                remoteView = rootView;
                layout.xml_id = root_xml;
                Pair<Integer, Integer> ids = getListViewId(collectionCounter);
                layout.view_id = ids.first;
                layout.container_id = ids.second;
                collectionCounter++;
            }
            else
            {
                if(!layout.children.isEmpty())
                {
                    throw new IllegalArgumentException("only collections can have children, not "+layout.type);
                }
                layout.xml_id = typeToLayout(layout.type);
                layout.container_id = R.id.l0;
                layout.view_id = R.id.e0;
                remoteView = new RemoteViews(context.getPackageName(), layout.xml_id);
            }

            for(RemoteMethodCall methodCall : layout.methodCalls)
            {
                methodCall.call(remoteView, methodCall.isParentCall() ? layout.container_id : layout.view_id);
            }

            if(!keepDescription)
            {
                remoteView.setCharSequence(layout.view_id, "setContentDescription", layout.getId()+"");
            }

            Intent clickIntent = new Intent(context, WidgetReceiver.class);
            clickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            clickIntent.putExtra(WIDGET_ID_EXTRA, widgetId);

            if(isCollection(layout.type))
            {
                clickIntent.putExtra(COLLECTION_ITEM_ID_EXTRA, layout.getId());
                remoteView.setPendingIntentTemplate(layout.view_id, PendingIntent.getBroadcast(context, widgetId + (layout.getId() << 8), clickIntent, PendingIntent.FLAG_UPDATE_CURRENT));

                //prepare factory
                getFactory(context, widgetId, layout.xml_id, layout.view_id, layout.toJSON());

                Intent listintent = new Intent(context, Widget.class);
                listintent.putExtra(WIDGET_ID_EXTRA, widgetId);
                listintent.putExtra(LIST_SERIALIZED_EXTRA, layout.toJSON());
                listintent.putExtra(XML_ID_EXTRA, layout.xml_id);
                listintent.putExtra(VIEW_ID_EXTRA, layout.view_id);
                listintent.setData(Uri.parse(listintent.toUri(Intent.URI_INTENT_SCHEME)));
                remoteView.setRemoteAdapter(layout.view_id, listintent);

                //Log.d("APPY", "set remote adapter on " + layout.view_id+", "+layout.xml_id+" in dynamic "+layout.getId());
            }
            else if(!inCollection)
            {
                clickIntent.putExtra(ITEM_ID_EXTRA, layout.getId());
                remoteView.setOnClickPendingIntent(layout.view_id, PendingIntent.getBroadcast(context, widgetId + (layout.getId() << 8), clickIntent, PendingIntent.FLAG_UPDATE_CURRENT));
            }

            if(remoteView != rootView)
            {
                rootView.addView(elements_id, remoteView);
            }
        }
        return rootView;
    }

    //only one level
    public DynamicView find(ArrayList<DynamicView> dynamicList, int id)
    {
        for(DynamicView view : dynamicList)
        {
            if(view.getId() == id)
            {
                return view;
            }
        }
        return null;
    }

    public double applyFunction(Attributes.AttributeValue.Function function, ArrayList<Double> arguments)
    {
        switch(function)
        {
            case IDENTITY:
                if(arguments.size() != 1)
                {
                    throw new IllegalArgumentException("tried to apply identity with "+arguments.size()+" arguments, probably a misuse");
                }
                return arguments.get(0);
            case MAX:
                return Collections.max(arguments);
            case MIN:
                return Collections.min(arguments);
        }
        throw new IllegalArgumentException("unknown function "+function);
    }

    public Pair<Integer, Integer> resolveAxis(int lenLimit, Attributes.AttributeValue start, Attributes.AttributeValue end, Attributes.AttributeValue len)
    {
        int padStart;
        int padEnd;

        if(len.triviallyResolved && !start.triviallyResolved && !end.triviallyResolved)
        {
            padStart = start.resolvedValue.intValue();
            padEnd = end.resolvedValue.intValue();
        }
        else if(start.triviallyResolved && !end.triviallyResolved)
        {
            padEnd = end.resolvedValue.intValue();
            padStart = lenLimit - len.resolvedValue.intValue() - padEnd;
        }
        else
        {
            padStart = start.resolvedValue.intValue();
            padEnd = lenLimit - len.resolvedValue.intValue() - padStart;
        }

        return new Pair<>(padStart, padEnd);
    }

    public RemoteViews resolveDimensions(Context context, int widgetId, ArrayList<DynamicView> dynamicList, boolean inCollection, int widthLimit, int heightLimit) throws InvocationTargetException, IllegalAccessException
    {
        RemoteViews remote = generate(context, widgetId, dynamicList, false, inCollection);
        RelativeLayout layout = new RelativeLayout(this);
        View inflated = remote.apply(context, layout);
        layout.addView(inflated);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)inflated.getLayoutParams();
        params.width = widthLimit;
        params.height = heightLimit;
        inflated.setLayoutParams(params);

        Attributes rootAttributes = new Attributes();
        rootAttributes.attributes.get(Attributes.Type.LEFT).tryTrivialResolve(0);
        rootAttributes.attributes.get(Attributes.Type.TOP).tryTrivialResolve(0);
        rootAttributes.attributes.get(Attributes.Type.RIGHT).tryTrivialResolve(0);
        rootAttributes.attributes.get(Attributes.Type.BOTTOM).tryTrivialResolve(0);
        rootAttributes.attributes.get(Attributes.Type.WIDTH).tryTrivialResolve(widthLimit);
        rootAttributes.attributes.get(Attributes.Type.HEIGHT).tryTrivialResolve(heightLimit);

        ViewGroup supergroup = (ViewGroup)inflated;

//        Log.d("APPY", "child count: ");
//        printChildCount(supergroup, "  ");

        if(supergroup.getChildCount() > 2)
        {
            throw new IllegalArgumentException("supergroup children count is larger than 2");
        }

        //set all to WRAP_CONTENT to measure it's default size (all children are leaves of collections)
        for(int k = 0; k < supergroup.getChildCount(); k++)
        {
            ViewGroup group = (ViewGroup)supergroup.getChildAt(k);
            for (int i = 0; i < group.getChildCount(); i++)
            {
                View view = ((ViewGroup) group.getChildAt(i)).getChildAt(0); //each leaf is inside RelativeLayout is inside elements or collections
                params = (RelativeLayout.LayoutParams) view.getLayoutParams();
                params.width = RelativeLayout.LayoutParams.WRAP_CONTENT;
                params.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                view.setLayoutParams(params);
            }
        }

        inflated.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        //set all back to MATCH_PARENT and resolve all trivials
        for(int k = 0; k < supergroup.getChildCount(); k++)
        {
            ViewGroup group = (ViewGroup) supergroup.getChildAt(k);
            for (int i = 0; i < group.getChildCount(); i++)
            {
                View view = ((ViewGroup) group.getChildAt(i)).getChildAt(0); //each leaf is inside RelativeLayout is inside elements or collections
                DynamicView dynamicView = find(dynamicList, Integer.parseInt(view.getContentDescription().toString()));

                dynamicView.attributes.attributes.get(Attributes.Type.LEFT).tryTrivialResolve(0);
                dynamicView.attributes.attributes.get(Attributes.Type.TOP).tryTrivialResolve(0);
                dynamicView.attributes.attributes.get(Attributes.Type.RIGHT).tryTrivialResolve(0);
                dynamicView.attributes.attributes.get(Attributes.Type.BOTTOM).tryTrivialResolve(0);

                if (isCollection(dynamicView.type))
                {
                    dynamicView.attributes.attributes.get(Attributes.Type.WIDTH).tryTrivialResolve(widthLimit);
                    dynamicView.attributes.attributes.get(Attributes.Type.HEIGHT).tryTrivialResolve(heightLimit);
                }
                else
                {
                    dynamicView.attributes.attributes.get(Attributes.Type.WIDTH).tryTrivialResolve(view.getMeasuredWidth());
                    dynamicView.attributes.attributes.get(Attributes.Type.HEIGHT).tryTrivialResolve(view.getMeasuredHeight());
                }

                params = (RelativeLayout.LayoutParams) view.getLayoutParams();
                params.width = RelativeLayout.LayoutParams.MATCH_PARENT;
                params.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                view.setLayoutParams(params);
            }
        }

        //one iteration
        while(true)
        {
            int iterationResolved = 0;
            for(int k = 0; k < supergroup.getChildCount(); k++)
            {
                ViewGroup group = (ViewGroup) supergroup.getChildAt(k);
                for (int i = 0; i < group.getChildCount(); i++)
                {
                    View view = ((ViewGroup) group.getChildAt(i)).getChildAt(0); //each leaf is inside RelativeLayout is inside elements or collections
                    DynamicView dynamicView = find(dynamicList, Integer.parseInt(view.getContentDescription().toString()));

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
                            iterationResolved++;
                        }
                    }
                }
            }

            if(iterationResolved == 0) //no way to advance from here
            {
                break;
            }
        }

        for(DynamicView dynamicView : dynamicList)
        {
            ArrayList<Attributes.AttributeValue> unresolved = dynamicView.attributes.unresolved();
            if(!unresolved.isEmpty())
            {
                throw new IllegalArgumentException("unresolved after iterations, maybe circular? example: "+unresolved.get(0) + " from: "+dynamicView.getId()+", "+dynamicView.type+": "+dynamicView.attributes);
            }
        }

        //apply
        for(DynamicView dynamicView : dynamicList)
        {
            //pick 2 out of 3 with this priority
            Attributes.AttributeValue width = dynamicView.attributes.attributes.get(Attributes.Type.WIDTH);
            Attributes.AttributeValue left = dynamicView.attributes.attributes.get(Attributes.Type.LEFT);
            Attributes.AttributeValue right = dynamicView.attributes.attributes.get(Attributes.Type.RIGHT);

            //pick 2 out of 3 with this priority
            Attributes.AttributeValue top = dynamicView.attributes.attributes.get(Attributes.Type.TOP);
            Attributes.AttributeValue bottom = dynamicView.attributes.attributes.get(Attributes.Type.BOTTOM);
            Attributes.AttributeValue height = dynamicView.attributes.attributes.get(Attributes.Type.HEIGHT);

            Pair<Integer, Integer> hor;
            Pair<Integer, Integer> ver;
            if(inCollection)
            {
                //in list, we have no size limit, so no real width, height, right or bottom
                hor = new Pair<>(left.resolvedValue.intValue(), 0);
                ver = new Pair<>(top.resolvedValue.intValue(), 0);
            }
            else
            {
                hor = resolveAxis(widthLimit, left, right, width);
                ver = resolveAxis(heightLimit, top, bottom, height);

                //for collection children
                dynamicView.actualWidth = widthLimit - hor.second - hor.first;
                dynamicView.actualHeight = heightLimit - ver.second - ver.first;
            }

//            Log.d("APPY", "resolved attributes: ");
//            for(Map.Entry<Attributes.Type, Attributes.AttributeValue> entry : dynamicView.attributes.attributes.entrySet())
//            {
//                Log.d("APPY", entry.getKey().name()+": "+entry.getValue().resolvedValue);
//            }
//            Log.d("APPY", "selected pad for "+dynamicView.getId()+" "+dynamicView.type+": "+hor.first+", "+hor.second+", "+ver.first+", "+ver.second);

            dynamicView.methodCalls.add(new RemoteMethodCall("setViewPadding", true, "setViewPadding",
                    hor.first,
                    ver.first,
                    hor.second,
                    ver.second));
        }

        return generate(context, widgetId, dynamicList, true, inCollection);
    }

    public String handle(int widgetId, String widgetJson, int collectionId, int itemId, int collectionPosition)
    {
        Log.d("APPY", "handling "+itemId+" in collection "+collectionId);

        //boolean callOnClick = true;

        if(updateListener == null)
        {
            return null;
        }

        if(itemId == 0 && collectionId == 0)
        {
            throw new IllegalArgumentException("handle without handle?");
        }

        if(collectionId != 0)
        {
            Log.d("APPY", "calling listener onItemClick");
            String ret = updateListener.onItemClick(widgetId, widgetJson, collectionId, collectionPosition);
            //TODO cannot change layout and not suppress click
            if(ret != null || itemId == 0)
            {
                Log.d("APPY", "suppressing click on "+itemId);
                return ret;
            }
        }

        Log.d("APPY", "calling listener onClick");
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
                    continue;
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
                    ArrayList<DynamicView> out = initWidget(widgetId);
                    if(out != null)
                    {
                        return DynamicView.toJSONString(out);
                    }
                    return null;
                }

                @Override
                public String onUpdate(int widgetId, String currentViews)
                {
                    ArrayList<DynamicView> out = updateWidget(widgetId, DynamicView.fromJSONArray(currentViews));
                    if(out != null)
                    {
                        return DynamicView.toJSONString(out);
                    }
                    return null;
                }

                @Override
                public void onDelete(int widgetId)
                {

                }

                @Override
                public String onItemClick(int widgetId, String views, int collectionId, int position)
                {
                    Log.d("APPY", "on item click: "+collectionId+" "+position);
                    return null;
                }

                @Override
                public String onClick(int widgetId, String views, int id)
                {
                    Log.d("APPY", "on click: "+id);
                    return null;
                }

                @Override
                public void onTimer(int timerId, int widgetId, String data)
                {
                }
            });
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent)
    {
        int widgetId = intent.getIntExtra(WIDGET_ID_EXTRA, -1);
        int xmlId = intent.getIntExtra(XML_ID_EXTRA, 0);
        int viewId = intent.getIntExtra(VIEW_ID_EXTRA, 0);
        String list = intent.getStringExtra(LIST_SERIALIZED_EXTRA);
        return getFactory(this, widgetId, xmlId, viewId, list);
    }

    public void registerOnWidgetUpdate(WidgetUpdateListener listener)
    {
        updateListener = listener;
    }

    boolean started = false;
    Handler handler;

    HashMap<Integer, Integer> androidToWidget = new HashMap<>();
    HashMap<Integer, Integer> widgetToAndroid = new HashMap<>();

    public int newWidgetId()
    {
        int counter = 1;
        while(true)
        {
            if(!widgetToAndroid.containsKey(counter))
            {
                break;
            }
            counter++;
        }
        return counter;
    }

    public Integer getAndroidWidget(int widget)
    {
        return widgetToAndroid.get(widget);
    }

    public Integer fromAndroidWidget(int androidWidget, boolean create)
    {
        Integer widget = androidToWidget.get(androidWidget);
        if(widget == null && create)
        {
            return addWidget(androidWidget);
        }
        return widget;
    }

    public int addWidget(int androidWidget)
    {
        if(androidToWidget.containsKey(androidWidget))
        {
            throw new IllegalArgumentException("already know this widget id");
        }

        int widget = newWidgetId();

        androidToWidget.put(androidWidget, widget);
        widgetToAndroid.put(widget, androidWidget);
        return widget;
    }

    public void updateAndroidWidget(int oldAndroidWidget, int newAndroidWidget)
    {
        Integer widget = fromAndroidWidget(oldAndroidWidget, false);
        if(widget == null)
        {
            throw new IllegalArgumentException("widget does not exists");
        }
        androidToWidget.remove(oldAndroidWidget);
        widgetToAndroid.put(widget, newAndroidWidget);
        androidToWidget.put(newAndroidWidget, widget);
    }

    public void deleteAndroidWidget(int androidWidget)
    {
        Integer widget = fromAndroidWidget(androidWidget, false);
        if(widget == null)
        {
            throw new IllegalArgumentException("widget does not exists");
        }
        widgetToAndroid.remove(widget);
        androidToWidget.remove(androidWidget);
    }

    public void putWidget(int widgetId, String json)
    {
        widgets.put(widgetId, DynamicView.toJSONString(DynamicView.fromJSONArray(json))); //assign ids
    }

    public void loadWidgets()
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        String widgetsString = sharedPref.getString("widgets", null);
        if(widgetsString != null)
        {
            widgets = new MapSerialize<Integer, String>().deserialize(widgetsString, new MapSerialize.IntConverter(), new MapSerialize.StringConverter());
        }
        String widgetToAndroidString = sharedPref.getString("widgetToAndroid", null);
        if(widgetToAndroidString != null)
        {
            widgetToAndroid = new MapSerialize<Integer, Integer>().deserialize(widgetToAndroidString, new MapSerialize.IntConverter(), new MapSerialize.IntConverter());
            androidToWidget = new HashMap<>();
            for(Map.Entry<Integer, Integer> entry : widgetToAndroid.entrySet())
            {
                androidToWidget.put(entry.getValue(), entry.getKey());
            }
        }
        //TODO maybe clean
    }

    public void saveWidgets()
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("widgets", new MapSerialize<Integer, String>().serialize(widgets));
        editor.putString("widgetToAndroid", new MapSerialize<Integer, Integer>().serialize(widgetToAndroid));
        editor.apply();
    }

    public int generateTimerId()
    {
        if(activeTimers.isEmpty())
        {
            return 1;
        }
        int newId = Collections.max(activeTimers.keySet()) + 1;
        activeTimers.put(newId, null); //save room
        return newId;
    }

    HashMap<Integer, ArrayList<Integer>> widgetTimers = new HashMap<>();
    HashMap<Integer, Pair<Integer, PendingIntent>> activeTimers = new HashMap<>();

    public void timerCall(int timerId, int widgetId, String data)
    {
        Pair<Integer, PendingIntent> timer = activeTimers.get(timerId);
        if(timer == null)
        {
            return;
        }
        if(timer.first != TIMER_REPEATING)
        {
            cancelTimer(timerId);
        }

        if(updateListener != null)
        {
            updateListener.onTimer(timerId, widgetId, data);
        }
    }

    public boolean cancelTimer(int timerId)
    {
        PendingIntent pendingIntent = activeTimers.remove(timerId).second;
        if(pendingIntent != null)
        {
            AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
            mgr.cancel(pendingIntent);
            return true;
        }
        return false;
    }

    public void cancelWidgetTimers(int widgetId)
    {
        if(!widgetTimers.containsKey(widgetId))
        {
            return;
        }
        ArrayList<Integer> timers = widgetTimers.get(widgetId);
        widgetTimers.remove(widgetId);
        for(int timer : timers)
        {
            cancelTimer(timer);
        }
    }

    public static final int TIMER_RELATIVE = 1;
    public static final int TIMER_ABSOLUTE = 2;
    public static final int TIMER_REPEATING = 3;

    public abstract class ArgRunnable implements Runnable
    {
        Object[] args;
        ArgRunnable(Object ... args)
        {
            this.args = args;
        }
        public abstract void run();
    }

    public int setTimer(long millis, int type, boolean persistant, int widgetId, String data)
    {
        if(type == TIMER_RELATIVE)
        {
            millis += System.currentTimeMillis();
        }

        int timer = generateTimerId();
        Intent timerIntent = new Intent(Widget.this, getClass());
        timerIntent.putExtra("widgetId", widgetId);
        timerIntent.putExtra("timer", timer);
        timerIntent.putExtra("timerData", data);

        AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getService(Widget.this, 1, timerIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        if(type == TIMER_REPEATING)
        {
            if(millis <= 10 * 60 * 1000)
            {
                Log.d("APPY", "setting short time timer");
                handler.post(new ArgRunnable(timerIntent, millis, timer)
                {
                    boolean first = true;

                    @Override
                    public void run()
                    {
                        if(!first)
                        {
                            Log.d("APPY", "short time timer fire");
                            Widget.this.startService((Intent) args[0]);
                        }
                        first = false;
                        int timer = (int)args[2];
                        if(activeTimers.get(timer) != null)
                        {
                            //timer still active
                            handler.postDelayed(this, (long)args[1]);
                        }
                    }
                });
            }
            else
            {
                Log.d("APPY", "setting long time timer: "+millis);
                mgr.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + millis, millis, pendingIntent);
            }
        }
        else
        {
            Log.d("APPY", "setting one time timer");
            mgr.set(AlarmManager.RTC, millis, pendingIntent);
        }

        if(widgetTimers.get(widgetId) == null)
        {
            widgetTimers.put(widgetId, new ArrayList<Integer>());
        }
        widgetTimers.get(widgetId).add(timer);
        activeTimers.put(timer, new Pair<>(type, pendingIntent));
        return timer;
    }

    public void update(int widgetId)
    {
        Intent updateIntent = new Intent(Widget.this, getClass());
        updateIntent.putExtra("widgetId", widgetId);
        startService(updateIntent);
    }

    public void restart()
    {
        handler.post(new Runnable()
                     {
                         @Override
                         public void run()
                         {
                             setAllWidgets(false);

                             Intent intent = new Intent(Widget.this, getClass());
                             PendingIntent pendingIntent = PendingIntent.getService(Widget.this, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT);
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

    public void setWidget(AppWidgetManager appWidgetManager, int androidWidgetId, ArrayList<DynamicView> views)
    {
        try
        {
            int[] widgetDimensions = getWidgetDimensions(appWidgetManager, androidWidgetId);
            int widthLimit = (int) (widgetDimensions[0] * 1.1); //found empirically
            int heightLimit = (int) (widgetDimensions[1] * 1.5); //found empirically

            RemoteViews view = resolveDimensions(this, SPECIAL_WIDGET_ID, views, false, widthLimit, heightLimit);
            //appWidgetManager.notifyAppWidgetViewDataChanged(androidWidgetId, R.id.root);
            appWidgetManager.updateAppWidget(androidWidgetId, view);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw new IllegalStateException("exception while generating error widget, cannot continue");
        }
    }

    public void setSpecificLoadingWidget(AppWidgetManager appWidgetManager, int androidWidgetId)
    {
        ArrayList<DynamicView> views = new ArrayList<>();

        DynamicView textView = new DynamicView("TextView");
        textView.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(textView.type, "setText"), "setText", "Loading..."));

        Attributes.AttributeValue wholeWidth = attributeParse("w(r)");
        Attributes.AttributeValue wholeHeight = attributeParse("h(r)");
        textView.attributes.attributes.put(Attributes.Type.WIDTH, wholeWidth);
        textView.attributes.attributes.put(Attributes.Type.HEIGHT, wholeHeight);

        views.add(textView);

        setWidget(appWidgetManager, androidWidgetId, views);
    }

    public void setSpecificErrorWidget(AppWidgetManager appWidgetManager, int androidWidgetId)
    {
        ArrayList<DynamicView> views = new ArrayList<>();

        DynamicView textView = new DynamicView("TextView");
        DynamicView button = new DynamicView("Button");
        textView.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(textView.type, "setText"), "setText", "Error"));
        button.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(button.type, "setText"), "setText", "Restart"));

        Attributes.AttributeValue wholeWidth = attributeParse("w(r)");
        Attributes.AttributeValue halfHeight = attributeParse("h(r)*0.5");

        textView.attributes.attributes.put(Attributes.Type.WIDTH, wholeWidth);
        textView.attributes.attributes.put(Attributes.Type.HEIGHT, halfHeight);

        button.attributes.attributes.put(Attributes.Type.WIDTH, wholeWidth);
        button.attributes.attributes.put(Attributes.Type.HEIGHT, halfHeight);
        button.attributes.attributes.put(Attributes.Type.TOP, halfHeight);

        views.add(textView);
        views.add(button);

        setWidget(appWidgetManager, androidWidgetId, views);
    }

    public int[] getAllAndroidWidgetIds(AppWidgetManager appWidgetManager)
    {
        ComponentName thisWidget = new ComponentName(this, WidgetReceiver.class);
        return appWidgetManager.getAppWidgetIds(thisWidget);
    }

    public void setAllWidgets(boolean error)
    {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        for(int id : getAllAndroidWidgetIds(appWidgetManager))
        {
            if(error)
            {
                setSpecificErrorWidget(appWidgetManager, id);
            }
            else
            {
                setSpecificLoadingWidget(appWidgetManager, id);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        handleStartCommand(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    public void handleStartCommand(Intent intent)
    {
        boolean firstStart = false;
        if(!started)
        {
            started = true;
            firstStart = true;
            loadWidgets();
            handler = new Handler();

            String pythonHome = getFilesDir().getAbsolutePath();
            String pythonLib = new File(pythonHome, "/lib/libpython3.6m.so").getAbsolutePath(); //must be without
            String cacheDir = getCacheDir().getAbsolutePath();

            try
            {
                unpackPython(pythonHome);
                System.load(pythonLib);
                System.loadLibrary("native");
                pythonInit(pythonHome, cacheDir, pythonLib);
                pythonRun("/sdcard/appy/main.py", Widget.this);
                //java_widget();
            }
            catch(Exception e)
            {
                e.printStackTrace();
                Log.d("APPY", "cannot init python");
                handler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        setAllWidgets(true);
                    }
                });
                return;
            }
        }

        Log.d("APPY", "startCommand");
        Intent widgetIntent = null;
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        LinkedHashSet<Integer> updatedAndroidWidgets = new LinkedHashSet<>();
        Integer eventWidgetId = null;
        Integer updateWidgetId = null;
        if(intent != null)
        {
            if (intent.getBooleanExtra("restart", false))
            {
                restart();
                return;
            }

            if(intent.hasExtra("widgetId"))
            {
                updateWidgetId = intent.getIntExtra("widgetId", -1);
            }

            if(intent.hasExtra("timer") && updateWidgetId != null)
            {
                Log.d("APPY", "timer fire");
                timerCall(intent.getIntExtra("timer", -1), updateWidgetId, intent.getStringExtra("timerData"));
            }

            widgetIntent = intent.getParcelableExtra(WIDGET_INTENT);
        }

        if (widgetIntent != null && AppWidgetManager.ACTION_APPWIDGET_RESTORED.equals(widgetIntent.getAction()))
        {
            int[] oldWidgets = widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS);
            int[] newWidgets = widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            for(int i = 0; i < oldWidgets.length; i++)
            {
                updateAndroidWidget(oldWidgets[i], newWidgets[i]);
            }
        }
        if (widgetIntent != null && AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(widgetIntent.getAction()))
        {
            if(widgetIntent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
            {
                int deletedWidget = widgetIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                Integer widget = fromAndroidWidget(deletedWidget, false);
                if(widget != null)
                {
                    cancelWidgetTimers(widget);
                    widgets.remove(widget);
                    if(updateListener != null)
                    {
                        updateListener.onDelete(widget);
                    }
                }
                deleteAndroidWidget(deletedWidget);
            }
        }

        if(firstStart)
        {
            int[] ids = getAllAndroidWidgetIds(appWidgetManager);
            if(ids != null)
            {
                for (int id : ids)
                {
                    updatedAndroidWidgets.add(id);
                }
            }
        }

        if(widgetIntent != null && AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED.equals(widgetIntent.getAction()))
        {
            if(widgetIntent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
            {
                updatedAndroidWidgets.add(widgetIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1));
            }
        }

        if (widgetIntent != null && AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(widgetIntent.getAction()))
        {
            int[] ids = widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            if(ids != null)
            {
                for (int id : ids)
                {
                    updatedAndroidWidgets.add(id);
                }
            }

            if(widgetIntent.hasExtra(WIDGET_ID_EXTRA))
            {
                eventWidgetId = widgetIntent.getIntExtra(WIDGET_ID_EXTRA, -1);

                if(eventWidgetId == SPECIAL_WIDGET_ID)
                {
                    restart();
                    return;
                }

                //move it to end of set
                Integer androidEventWidgetId = getAndroidWidget(eventWidgetId);
                if(androidEventWidgetId != null)
                {
                    updatedAndroidWidgets.remove(androidEventWidgetId);
                    updatedAndroidWidgets.add(androidEventWidgetId);
                }
            }
        }

        if(updateWidgetId != null)
        {
            //move it to end of set
            Integer androidUpdateWidgetId = getAndroidWidget(updateWidgetId);
            if(androidUpdateWidgetId != null)
            {
                updatedAndroidWidgets.remove(androidUpdateWidgetId);
                updatedAndroidWidgets.add(androidUpdateWidgetId);
            }
        }

        if(!updatedAndroidWidgets.isEmpty())
        {
            if(widgetIntent != null && eventWidgetId != null)
            {
                int dynamicId = widgetIntent.getIntExtra(ITEM_ID_EXTRA, 0);
                Log.d("APPY", "got intent: " + eventWidgetId + " " + dynamicId);

                String eventWidget = widgets.get(eventWidgetId);
                if (eventWidget != null)
                {
                    try
                    {
                        String newwidget = handle(eventWidgetId, eventWidget, widgetIntent.getIntExtra(COLLECTION_ITEM_ID_EXTRA, 0), dynamicId, widgetIntent.getIntExtra(COLLECTION_POSITION_EXTRA, -1));
                        putWidget(eventWidgetId, newwidget);
                    }
                    catch (Exception e)
                    {
                        Log.d("APPY", "error in handle");
                    }
                }
            }

            for(int androidWidgetId : updatedAndroidWidgets)
            {
                int widgetId = fromAndroidWidget(androidWidgetId, true);
                Log.d("APPY", "update: " + androidWidgetId + " ("+widgetId+")");

                String widget = widgets.get(widgetId);
                if (widget == null)
                {
                    if (updateListener != null)
                    {
                        Log.d("APPY", "calling listener onCreate");
                        try
                        {
                            widget = updateListener.onCreate(widgetId);
                        }
                        catch (Exception e)
                        {
                            Log.e("APPY", "error in listener onCreate", e);
                        }
                        if(widget != null)
                        {
                            putWidget(widgetId, widget);
                        }
                        else
                        {
                            setSpecificErrorWidget(appWidgetManager, androidWidgetId);
                            continue;
                        }
                    }
                }

                if (updateListener != null)
                {
                    Log.d("APPY", "calling listener onUpdate");
                    try
                    {
                        String newwidget = updateListener.onUpdate(widgetId, widget);
                        if (newwidget != null)
                        {
                            widget = newwidget;
                            putWidget(widgetId, widget);
                        }
                    }
                    catch (Exception e)
                    {
                        Log.e("APPY", "error in listener onUpdate", e);
                    }
                }

                try
                {
                    int[] widgetDimensions = getWidgetDimensions(appWidgetManager, androidWidgetId);
                    int widthLimit = (int)(widgetDimensions[0] * 1.1); //found empirically
                    int heightLimit = (int)(widgetDimensions[1] * 1.5); //found empirically

                    RemoteViews view = resolveDimensions(this, widgetId, DynamicView.fromJSONArray(widgets.get(widgetId)), false, widthLimit, heightLimit);
                    //appWidgetManager.notifyAppWidgetViewDataChanged(androidWidgetId, R.id.root);
                    appWidgetManager.updateAppWidget(androidWidgetId, view);
                }
                catch (InvocationTargetException | IllegalAccessException e)
                {
                    e.printStackTrace();
                }
            }
        }

        saveWidgets();
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
            Log.e("APPY", "exception", e);
        }
    }

    public void unpackPython(String pythonHome)
    {
        if(!new File(pythonHome, "lib/libpython3.so").exists())
        {
            Log.d("APPY", "unpacking python");

            runProcess(new String[]{"sh", "-c", "tar -xf /sdcard/python.tar -C " + pythonHome + " 2>&1"});

            //printFnames(pythonHome);
            Log.d("APPY", "done unpacking python");
        }
        else
        {
            Log.d("APPY", "not unpacking python");
        }
    }

    protected static native void pythonInit(String pythonHome, String tmpPath, String pythonLibPath);
    protected static native void pythonRun(String script, Object obj);
    protected static native Object pythonCall(Object... args) throws Throwable;
}
