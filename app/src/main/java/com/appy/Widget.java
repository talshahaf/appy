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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.GZIPInputStream;

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
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.system.ErrnoException;
import android.system.Os;
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
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarInputStream;

public class Widget extends RemoteViewsService
{
    private static final String ITEM_ID_EXTRA = "ITEM_ID";
    private static final String ITEM_TAG_EXTRA = "ITEM_TAG";
    public static final String WIDGET_INTENT = "WIDGET_INTENT";
    private static final String COLLECTION_ITEM_ID_EXTRA = "COLLECTION_ITEM_ID_EXTRA";
    private static final String COLLECTION_POSITION_EXTRA = "COLLECTION_POSITION_EXTRA";
    private static final String LIST_SERIALIZED_EXTRA = "LIST_SERIALIZED_EXTRA";
    private static final String XML_ID_EXTRA = "XML_ID_EXTRA";
    private static final String VIEW_ID_EXTRA = "VIEW_ID_EXTRA";
    private static final String WIDGET_ID_EXTRA = "WIDGET_ID_EXTRA";
    public static final String LOCAL_BIND_EXTRA = "LOCAL_BIND_EXTRA";
    private static final int SPECIAL_WIDGET_ID = 100;
    private static final int SPECIAL_WIDGET_RESTART = 1;
    private static final int SPECIAL_WIDGET_CLEAR = 2;
    private static final int SPECIAL_WIDGET_RELOAD = 3;
    public static final int TIMER_RELATIVE = 1;
    public static final int TIMER_ABSOLUTE = 2;
    public static final int TIMER_REPEATING = 3;
    public static final int IMPORT_TASK_QUEUE = -1;

    public enum StartupState
    {
        IDLE,
        RUNNING,
        ERROR,
        COMPLETED,
    }

    private final IBinder mBinder = new LocalBinder();

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

        for (String type : typeToClass.keySet())
        {
            typeToRemotableMethod.put(type, getRemotableMethods(type));
        }
    }

    static HashMap<List<String>, Integer> collection_map = new HashMap<>();
    static
    {
        collection_map.put(Arrays.asList("ListView"), R.layout.root_listview);
        collection_map.put(Arrays.asList("GridView"), R.layout.root_gridview);
        collection_map.put(Arrays.asList("StackView"), R.layout.root_stackview);
        collection_map.put(Arrays.asList("AdapterViewFlipper"), R.layout.root_adapterviewflipper);
        collection_map.put(Arrays.asList("AdapterViewFlipper", "AdapterViewFlipper"), R.layout.root_adapterviewflipper_adapterviewflipper);
        collection_map.put(Arrays.asList("AdapterViewFlipper", "GridView"), R.layout.root_adapterviewflipper_gridview);
        collection_map.put(Arrays.asList("AdapterViewFlipper", "ListView"), R.layout.root_adapterviewflipper_listview);
        collection_map.put(Arrays.asList("AdapterViewFlipper", "StackView"), R.layout.root_adapterviewflipper_stackview);
        collection_map.put(Arrays.asList("GridView", "GridView"), R.layout.root_gridview_gridview);
        collection_map.put(Arrays.asList("GridView", "ListView"), R.layout.root_gridview_listview);
        collection_map.put(Arrays.asList("GridView", "StackView"), R.layout.root_gridview_stackview);
        collection_map.put(Arrays.asList("ListView", "ListView"), R.layout.root_listview_listview);
        collection_map.put(Arrays.asList("ListView", "StackView"), R.layout.root_listview_stackview);
        collection_map.put(Arrays.asList("StackView", "StackView"), R.layout.root_stackview_stackview);
    }

    static HashMap<String, HashMap<String, Integer>> element_map = new HashMap<>();
    static
    {
        element_map.put("RelativeLayout", new HashMap<String, Integer>());
        element_map.get("RelativeLayout").put("", R.layout.element_relativelayout);
        element_map.put("AnalogClock", new HashMap<String, Integer>());
        element_map.get("AnalogClock").put("", R.layout.element_analogclock);
        element_map.put("Button", new HashMap<String, Integer>());
        element_map.get("Button").put("", R.layout.element_button);
        element_map.get("Button").put("primary_btn", R.layout.element_button_primary_btn);
        element_map.get("Button").put("outline_primary_btn", R.layout.element_button_outline_primary_btn);
        element_map.get("Button").put("primary_btn_sml", R.layout.element_button_primary_btn_sml);
        element_map.get("Button").put("outline_primary_btn_sml", R.layout.element_button_outline_primary_btn_sml);
        element_map.get("Button").put("primary_btn_lg", R.layout.element_button_primary_btn_lg);
        element_map.get("Button").put("outline_primary_btn_lg", R.layout.element_button_outline_primary_btn_lg);
        element_map.get("Button").put("primary_btn_nopad", R.layout.element_button_primary_btn_nopad);
        element_map.get("Button").put("outline_primary_btn_nopad", R.layout.element_button_outline_primary_btn_nopad);
        element_map.get("Button").put("primary_btn_oval", R.layout.element_button_primary_btn_oval);
        element_map.get("Button").put("outline_primary_btn_oval", R.layout.element_button_outline_primary_btn_oval);
        element_map.get("Button").put("primary_btn_oval_sml", R.layout.element_button_primary_btn_oval_sml);
        element_map.get("Button").put("outline_primary_btn_oval_sml", R.layout.element_button_outline_primary_btn_oval_sml);
        element_map.get("Button").put("primary_btn_oval_lg", R.layout.element_button_primary_btn_oval_lg);
        element_map.get("Button").put("outline_primary_btn_oval_lg", R.layout.element_button_outline_primary_btn_oval_lg);
        element_map.get("Button").put("primary_btn_oval_nopad", R.layout.element_button_primary_btn_oval_nopad);
        element_map.get("Button").put("outline_primary_btn_oval_nopad", R.layout.element_button_outline_primary_btn_oval_nopad);
        element_map.get("Button").put("secondary_btn", R.layout.element_button_secondary_btn);
        element_map.get("Button").put("outline_secondary_btn", R.layout.element_button_outline_secondary_btn);
        element_map.get("Button").put("secondary_btn_sml", R.layout.element_button_secondary_btn_sml);
        element_map.get("Button").put("outline_secondary_btn_sml", R.layout.element_button_outline_secondary_btn_sml);
        element_map.get("Button").put("secondary_btn_lg", R.layout.element_button_secondary_btn_lg);
        element_map.get("Button").put("outline_secondary_btn_lg", R.layout.element_button_outline_secondary_btn_lg);
        element_map.get("Button").put("secondary_btn_nopad", R.layout.element_button_secondary_btn_nopad);
        element_map.get("Button").put("outline_secondary_btn_nopad", R.layout.element_button_outline_secondary_btn_nopad);
        element_map.get("Button").put("secondary_btn_oval", R.layout.element_button_secondary_btn_oval);
        element_map.get("Button").put("outline_secondary_btn_oval", R.layout.element_button_outline_secondary_btn_oval);
        element_map.get("Button").put("secondary_btn_oval_sml", R.layout.element_button_secondary_btn_oval_sml);
        element_map.get("Button").put("outline_secondary_btn_oval_sml", R.layout.element_button_outline_secondary_btn_oval_sml);
        element_map.get("Button").put("secondary_btn_oval_lg", R.layout.element_button_secondary_btn_oval_lg);
        element_map.get("Button").put("outline_secondary_btn_oval_lg", R.layout.element_button_outline_secondary_btn_oval_lg);
        element_map.get("Button").put("secondary_btn_oval_nopad", R.layout.element_button_secondary_btn_oval_nopad);
        element_map.get("Button").put("outline_secondary_btn_oval_nopad", R.layout.element_button_outline_secondary_btn_oval_nopad);
        element_map.get("Button").put("success_btn", R.layout.element_button_success_btn);
        element_map.get("Button").put("outline_success_btn", R.layout.element_button_outline_success_btn);
        element_map.get("Button").put("success_btn_sml", R.layout.element_button_success_btn_sml);
        element_map.get("Button").put("outline_success_btn_sml", R.layout.element_button_outline_success_btn_sml);
        element_map.get("Button").put("success_btn_lg", R.layout.element_button_success_btn_lg);
        element_map.get("Button").put("outline_success_btn_lg", R.layout.element_button_outline_success_btn_lg);
        element_map.get("Button").put("success_btn_nopad", R.layout.element_button_success_btn_nopad);
        element_map.get("Button").put("outline_success_btn_nopad", R.layout.element_button_outline_success_btn_nopad);
        element_map.get("Button").put("success_btn_oval", R.layout.element_button_success_btn_oval);
        element_map.get("Button").put("outline_success_btn_oval", R.layout.element_button_outline_success_btn_oval);
        element_map.get("Button").put("success_btn_oval_sml", R.layout.element_button_success_btn_oval_sml);
        element_map.get("Button").put("outline_success_btn_oval_sml", R.layout.element_button_outline_success_btn_oval_sml);
        element_map.get("Button").put("success_btn_oval_lg", R.layout.element_button_success_btn_oval_lg);
        element_map.get("Button").put("outline_success_btn_oval_lg", R.layout.element_button_outline_success_btn_oval_lg);
        element_map.get("Button").put("success_btn_oval_nopad", R.layout.element_button_success_btn_oval_nopad);
        element_map.get("Button").put("outline_success_btn_oval_nopad", R.layout.element_button_outline_success_btn_oval_nopad);
        element_map.get("Button").put("danger_btn", R.layout.element_button_danger_btn);
        element_map.get("Button").put("outline_danger_btn", R.layout.element_button_outline_danger_btn);
        element_map.get("Button").put("danger_btn_sml", R.layout.element_button_danger_btn_sml);
        element_map.get("Button").put("outline_danger_btn_sml", R.layout.element_button_outline_danger_btn_sml);
        element_map.get("Button").put("danger_btn_lg", R.layout.element_button_danger_btn_lg);
        element_map.get("Button").put("outline_danger_btn_lg", R.layout.element_button_outline_danger_btn_lg);
        element_map.get("Button").put("danger_btn_nopad", R.layout.element_button_danger_btn_nopad);
        element_map.get("Button").put("outline_danger_btn_nopad", R.layout.element_button_outline_danger_btn_nopad);
        element_map.get("Button").put("danger_btn_oval", R.layout.element_button_danger_btn_oval);
        element_map.get("Button").put("outline_danger_btn_oval", R.layout.element_button_outline_danger_btn_oval);
        element_map.get("Button").put("danger_btn_oval_sml", R.layout.element_button_danger_btn_oval_sml);
        element_map.get("Button").put("outline_danger_btn_oval_sml", R.layout.element_button_outline_danger_btn_oval_sml);
        element_map.get("Button").put("danger_btn_oval_lg", R.layout.element_button_danger_btn_oval_lg);
        element_map.get("Button").put("outline_danger_btn_oval_lg", R.layout.element_button_outline_danger_btn_oval_lg);
        element_map.get("Button").put("danger_btn_oval_nopad", R.layout.element_button_danger_btn_oval_nopad);
        element_map.get("Button").put("outline_danger_btn_oval_nopad", R.layout.element_button_outline_danger_btn_oval_nopad);
        element_map.get("Button").put("warning_btn", R.layout.element_button_warning_btn);
        element_map.get("Button").put("outline_warning_btn", R.layout.element_button_outline_warning_btn);
        element_map.get("Button").put("warning_btn_sml", R.layout.element_button_warning_btn_sml);
        element_map.get("Button").put("outline_warning_btn_sml", R.layout.element_button_outline_warning_btn_sml);
        element_map.get("Button").put("warning_btn_lg", R.layout.element_button_warning_btn_lg);
        element_map.get("Button").put("outline_warning_btn_lg", R.layout.element_button_outline_warning_btn_lg);
        element_map.get("Button").put("warning_btn_nopad", R.layout.element_button_warning_btn_nopad);
        element_map.get("Button").put("outline_warning_btn_nopad", R.layout.element_button_outline_warning_btn_nopad);
        element_map.get("Button").put("warning_btn_oval", R.layout.element_button_warning_btn_oval);
        element_map.get("Button").put("outline_warning_btn_oval", R.layout.element_button_outline_warning_btn_oval);
        element_map.get("Button").put("warning_btn_oval_sml", R.layout.element_button_warning_btn_oval_sml);
        element_map.get("Button").put("outline_warning_btn_oval_sml", R.layout.element_button_outline_warning_btn_oval_sml);
        element_map.get("Button").put("warning_btn_oval_lg", R.layout.element_button_warning_btn_oval_lg);
        element_map.get("Button").put("outline_warning_btn_oval_lg", R.layout.element_button_outline_warning_btn_oval_lg);
        element_map.get("Button").put("warning_btn_oval_nopad", R.layout.element_button_warning_btn_oval_nopad);
        element_map.get("Button").put("outline_warning_btn_oval_nopad", R.layout.element_button_outline_warning_btn_oval_nopad);
        element_map.get("Button").put("info_btn", R.layout.element_button_info_btn);
        element_map.get("Button").put("outline_info_btn", R.layout.element_button_outline_info_btn);
        element_map.get("Button").put("info_btn_sml", R.layout.element_button_info_btn_sml);
        element_map.get("Button").put("outline_info_btn_sml", R.layout.element_button_outline_info_btn_sml);
        element_map.get("Button").put("info_btn_lg", R.layout.element_button_info_btn_lg);
        element_map.get("Button").put("outline_info_btn_lg", R.layout.element_button_outline_info_btn_lg);
        element_map.get("Button").put("info_btn_nopad", R.layout.element_button_info_btn_nopad);
        element_map.get("Button").put("outline_info_btn_nopad", R.layout.element_button_outline_info_btn_nopad);
        element_map.get("Button").put("info_btn_oval", R.layout.element_button_info_btn_oval);
        element_map.get("Button").put("outline_info_btn_oval", R.layout.element_button_outline_info_btn_oval);
        element_map.get("Button").put("info_btn_oval_sml", R.layout.element_button_info_btn_oval_sml);
        element_map.get("Button").put("outline_info_btn_oval_sml", R.layout.element_button_outline_info_btn_oval_sml);
        element_map.get("Button").put("info_btn_oval_lg", R.layout.element_button_info_btn_oval_lg);
        element_map.get("Button").put("outline_info_btn_oval_lg", R.layout.element_button_outline_info_btn_oval_lg);
        element_map.get("Button").put("info_btn_oval_nopad", R.layout.element_button_info_btn_oval_nopad);
        element_map.get("Button").put("outline_info_btn_oval_nopad", R.layout.element_button_outline_info_btn_oval_nopad);
        element_map.get("Button").put("light_btn", R.layout.element_button_light_btn);
        element_map.get("Button").put("outline_light_btn", R.layout.element_button_outline_light_btn);
        element_map.get("Button").put("light_btn_sml", R.layout.element_button_light_btn_sml);
        element_map.get("Button").put("outline_light_btn_sml", R.layout.element_button_outline_light_btn_sml);
        element_map.get("Button").put("light_btn_lg", R.layout.element_button_light_btn_lg);
        element_map.get("Button").put("outline_light_btn_lg", R.layout.element_button_outline_light_btn_lg);
        element_map.get("Button").put("light_btn_nopad", R.layout.element_button_light_btn_nopad);
        element_map.get("Button").put("outline_light_btn_nopad", R.layout.element_button_outline_light_btn_nopad);
        element_map.get("Button").put("light_btn_oval", R.layout.element_button_light_btn_oval);
        element_map.get("Button").put("outline_light_btn_oval", R.layout.element_button_outline_light_btn_oval);
        element_map.get("Button").put("light_btn_oval_sml", R.layout.element_button_light_btn_oval_sml);
        element_map.get("Button").put("outline_light_btn_oval_sml", R.layout.element_button_outline_light_btn_oval_sml);
        element_map.get("Button").put("light_btn_oval_lg", R.layout.element_button_light_btn_oval_lg);
        element_map.get("Button").put("outline_light_btn_oval_lg", R.layout.element_button_outline_light_btn_oval_lg);
        element_map.get("Button").put("light_btn_oval_nopad", R.layout.element_button_light_btn_oval_nopad);
        element_map.get("Button").put("outline_light_btn_oval_nopad", R.layout.element_button_outline_light_btn_oval_nopad);
        element_map.get("Button").put("dark_btn", R.layout.element_button_dark_btn);
        element_map.get("Button").put("outline_dark_btn", R.layout.element_button_outline_dark_btn);
        element_map.get("Button").put("dark_btn_sml", R.layout.element_button_dark_btn_sml);
        element_map.get("Button").put("outline_dark_btn_sml", R.layout.element_button_outline_dark_btn_sml);
        element_map.get("Button").put("dark_btn_lg", R.layout.element_button_dark_btn_lg);
        element_map.get("Button").put("outline_dark_btn_lg", R.layout.element_button_outline_dark_btn_lg);
        element_map.get("Button").put("dark_btn_nopad", R.layout.element_button_dark_btn_nopad);
        element_map.get("Button").put("outline_dark_btn_nopad", R.layout.element_button_outline_dark_btn_nopad);
        element_map.get("Button").put("dark_btn_oval", R.layout.element_button_dark_btn_oval);
        element_map.get("Button").put("outline_dark_btn_oval", R.layout.element_button_outline_dark_btn_oval);
        element_map.get("Button").put("dark_btn_oval_sml", R.layout.element_button_dark_btn_oval_sml);
        element_map.get("Button").put("outline_dark_btn_oval_sml", R.layout.element_button_outline_dark_btn_oval_sml);
        element_map.get("Button").put("dark_btn_oval_lg", R.layout.element_button_dark_btn_oval_lg);
        element_map.get("Button").put("outline_dark_btn_oval_lg", R.layout.element_button_outline_dark_btn_oval_lg);
        element_map.get("Button").put("dark_btn_oval_nopad", R.layout.element_button_dark_btn_oval_nopad);
        element_map.get("Button").put("outline_dark_btn_oval_nopad", R.layout.element_button_outline_dark_btn_oval_nopad);
        element_map.put("Chronometer", new HashMap<String, Integer>());
        element_map.get("Chronometer").put("", R.layout.element_chronometer);
        element_map.put("ImageButton", new HashMap<String, Integer>());
        element_map.get("ImageButton").put("", R.layout.element_imagebutton);
        element_map.get("ImageButton").put("primary_btn", R.layout.element_imagebutton_primary_btn);
        element_map.get("ImageButton").put("outline_primary_btn", R.layout.element_imagebutton_outline_primary_btn);
        element_map.get("ImageButton").put("primary_btn_sml", R.layout.element_imagebutton_primary_btn_sml);
        element_map.get("ImageButton").put("outline_primary_btn_sml", R.layout.element_imagebutton_outline_primary_btn_sml);
        element_map.get("ImageButton").put("primary_btn_lg", R.layout.element_imagebutton_primary_btn_lg);
        element_map.get("ImageButton").put("outline_primary_btn_lg", R.layout.element_imagebutton_outline_primary_btn_lg);
        element_map.get("ImageButton").put("primary_btn_nopad", R.layout.element_imagebutton_primary_btn_nopad);
        element_map.get("ImageButton").put("outline_primary_btn_nopad", R.layout.element_imagebutton_outline_primary_btn_nopad);
        element_map.get("ImageButton").put("primary_btn_oval", R.layout.element_imagebutton_primary_btn_oval);
        element_map.get("ImageButton").put("outline_primary_btn_oval", R.layout.element_imagebutton_outline_primary_btn_oval);
        element_map.get("ImageButton").put("primary_btn_oval_sml", R.layout.element_imagebutton_primary_btn_oval_sml);
        element_map.get("ImageButton").put("outline_primary_btn_oval_sml", R.layout.element_imagebutton_outline_primary_btn_oval_sml);
        element_map.get("ImageButton").put("primary_btn_oval_lg", R.layout.element_imagebutton_primary_btn_oval_lg);
        element_map.get("ImageButton").put("outline_primary_btn_oval_lg", R.layout.element_imagebutton_outline_primary_btn_oval_lg);
        element_map.get("ImageButton").put("primary_btn_oval_nopad", R.layout.element_imagebutton_primary_btn_oval_nopad);
        element_map.get("ImageButton").put("outline_primary_btn_oval_nopad", R.layout.element_imagebutton_outline_primary_btn_oval_nopad);
        element_map.get("ImageButton").put("secondary_btn", R.layout.element_imagebutton_secondary_btn);
        element_map.get("ImageButton").put("outline_secondary_btn", R.layout.element_imagebutton_outline_secondary_btn);
        element_map.get("ImageButton").put("secondary_btn_sml", R.layout.element_imagebutton_secondary_btn_sml);
        element_map.get("ImageButton").put("outline_secondary_btn_sml", R.layout.element_imagebutton_outline_secondary_btn_sml);
        element_map.get("ImageButton").put("secondary_btn_lg", R.layout.element_imagebutton_secondary_btn_lg);
        element_map.get("ImageButton").put("outline_secondary_btn_lg", R.layout.element_imagebutton_outline_secondary_btn_lg);
        element_map.get("ImageButton").put("secondary_btn_nopad", R.layout.element_imagebutton_secondary_btn_nopad);
        element_map.get("ImageButton").put("outline_secondary_btn_nopad", R.layout.element_imagebutton_outline_secondary_btn_nopad);
        element_map.get("ImageButton").put("secondary_btn_oval", R.layout.element_imagebutton_secondary_btn_oval);
        element_map.get("ImageButton").put("outline_secondary_btn_oval", R.layout.element_imagebutton_outline_secondary_btn_oval);
        element_map.get("ImageButton").put("secondary_btn_oval_sml", R.layout.element_imagebutton_secondary_btn_oval_sml);
        element_map.get("ImageButton").put("outline_secondary_btn_oval_sml", R.layout.element_imagebutton_outline_secondary_btn_oval_sml);
        element_map.get("ImageButton").put("secondary_btn_oval_lg", R.layout.element_imagebutton_secondary_btn_oval_lg);
        element_map.get("ImageButton").put("outline_secondary_btn_oval_lg", R.layout.element_imagebutton_outline_secondary_btn_oval_lg);
        element_map.get("ImageButton").put("secondary_btn_oval_nopad", R.layout.element_imagebutton_secondary_btn_oval_nopad);
        element_map.get("ImageButton").put("outline_secondary_btn_oval_nopad", R.layout.element_imagebutton_outline_secondary_btn_oval_nopad);
        element_map.get("ImageButton").put("success_btn", R.layout.element_imagebutton_success_btn);
        element_map.get("ImageButton").put("outline_success_btn", R.layout.element_imagebutton_outline_success_btn);
        element_map.get("ImageButton").put("success_btn_sml", R.layout.element_imagebutton_success_btn_sml);
        element_map.get("ImageButton").put("outline_success_btn_sml", R.layout.element_imagebutton_outline_success_btn_sml);
        element_map.get("ImageButton").put("success_btn_lg", R.layout.element_imagebutton_success_btn_lg);
        element_map.get("ImageButton").put("outline_success_btn_lg", R.layout.element_imagebutton_outline_success_btn_lg);
        element_map.get("ImageButton").put("success_btn_nopad", R.layout.element_imagebutton_success_btn_nopad);
        element_map.get("ImageButton").put("outline_success_btn_nopad", R.layout.element_imagebutton_outline_success_btn_nopad);
        element_map.get("ImageButton").put("success_btn_oval", R.layout.element_imagebutton_success_btn_oval);
        element_map.get("ImageButton").put("outline_success_btn_oval", R.layout.element_imagebutton_outline_success_btn_oval);
        element_map.get("ImageButton").put("success_btn_oval_sml", R.layout.element_imagebutton_success_btn_oval_sml);
        element_map.get("ImageButton").put("outline_success_btn_oval_sml", R.layout.element_imagebutton_outline_success_btn_oval_sml);
        element_map.get("ImageButton").put("success_btn_oval_lg", R.layout.element_imagebutton_success_btn_oval_lg);
        element_map.get("ImageButton").put("outline_success_btn_oval_lg", R.layout.element_imagebutton_outline_success_btn_oval_lg);
        element_map.get("ImageButton").put("success_btn_oval_nopad", R.layout.element_imagebutton_success_btn_oval_nopad);
        element_map.get("ImageButton").put("outline_success_btn_oval_nopad", R.layout.element_imagebutton_outline_success_btn_oval_nopad);
        element_map.get("ImageButton").put("danger_btn", R.layout.element_imagebutton_danger_btn);
        element_map.get("ImageButton").put("outline_danger_btn", R.layout.element_imagebutton_outline_danger_btn);
        element_map.get("ImageButton").put("danger_btn_sml", R.layout.element_imagebutton_danger_btn_sml);
        element_map.get("ImageButton").put("outline_danger_btn_sml", R.layout.element_imagebutton_outline_danger_btn_sml);
        element_map.get("ImageButton").put("danger_btn_lg", R.layout.element_imagebutton_danger_btn_lg);
        element_map.get("ImageButton").put("outline_danger_btn_lg", R.layout.element_imagebutton_outline_danger_btn_lg);
        element_map.get("ImageButton").put("danger_btn_nopad", R.layout.element_imagebutton_danger_btn_nopad);
        element_map.get("ImageButton").put("outline_danger_btn_nopad", R.layout.element_imagebutton_outline_danger_btn_nopad);
        element_map.get("ImageButton").put("danger_btn_oval", R.layout.element_imagebutton_danger_btn_oval);
        element_map.get("ImageButton").put("outline_danger_btn_oval", R.layout.element_imagebutton_outline_danger_btn_oval);
        element_map.get("ImageButton").put("danger_btn_oval_sml", R.layout.element_imagebutton_danger_btn_oval_sml);
        element_map.get("ImageButton").put("outline_danger_btn_oval_sml", R.layout.element_imagebutton_outline_danger_btn_oval_sml);
        element_map.get("ImageButton").put("danger_btn_oval_lg", R.layout.element_imagebutton_danger_btn_oval_lg);
        element_map.get("ImageButton").put("outline_danger_btn_oval_lg", R.layout.element_imagebutton_outline_danger_btn_oval_lg);
        element_map.get("ImageButton").put("danger_btn_oval_nopad", R.layout.element_imagebutton_danger_btn_oval_nopad);
        element_map.get("ImageButton").put("outline_danger_btn_oval_nopad", R.layout.element_imagebutton_outline_danger_btn_oval_nopad);
        element_map.get("ImageButton").put("warning_btn", R.layout.element_imagebutton_warning_btn);
        element_map.get("ImageButton").put("outline_warning_btn", R.layout.element_imagebutton_outline_warning_btn);
        element_map.get("ImageButton").put("warning_btn_sml", R.layout.element_imagebutton_warning_btn_sml);
        element_map.get("ImageButton").put("outline_warning_btn_sml", R.layout.element_imagebutton_outline_warning_btn_sml);
        element_map.get("ImageButton").put("warning_btn_lg", R.layout.element_imagebutton_warning_btn_lg);
        element_map.get("ImageButton").put("outline_warning_btn_lg", R.layout.element_imagebutton_outline_warning_btn_lg);
        element_map.get("ImageButton").put("warning_btn_nopad", R.layout.element_imagebutton_warning_btn_nopad);
        element_map.get("ImageButton").put("outline_warning_btn_nopad", R.layout.element_imagebutton_outline_warning_btn_nopad);
        element_map.get("ImageButton").put("warning_btn_oval", R.layout.element_imagebutton_warning_btn_oval);
        element_map.get("ImageButton").put("outline_warning_btn_oval", R.layout.element_imagebutton_outline_warning_btn_oval);
        element_map.get("ImageButton").put("warning_btn_oval_sml", R.layout.element_imagebutton_warning_btn_oval_sml);
        element_map.get("ImageButton").put("outline_warning_btn_oval_sml", R.layout.element_imagebutton_outline_warning_btn_oval_sml);
        element_map.get("ImageButton").put("warning_btn_oval_lg", R.layout.element_imagebutton_warning_btn_oval_lg);
        element_map.get("ImageButton").put("outline_warning_btn_oval_lg", R.layout.element_imagebutton_outline_warning_btn_oval_lg);
        element_map.get("ImageButton").put("warning_btn_oval_nopad", R.layout.element_imagebutton_warning_btn_oval_nopad);
        element_map.get("ImageButton").put("outline_warning_btn_oval_nopad", R.layout.element_imagebutton_outline_warning_btn_oval_nopad);
        element_map.get("ImageButton").put("info_btn", R.layout.element_imagebutton_info_btn);
        element_map.get("ImageButton").put("outline_info_btn", R.layout.element_imagebutton_outline_info_btn);
        element_map.get("ImageButton").put("info_btn_sml", R.layout.element_imagebutton_info_btn_sml);
        element_map.get("ImageButton").put("outline_info_btn_sml", R.layout.element_imagebutton_outline_info_btn_sml);
        element_map.get("ImageButton").put("info_btn_lg", R.layout.element_imagebutton_info_btn_lg);
        element_map.get("ImageButton").put("outline_info_btn_lg", R.layout.element_imagebutton_outline_info_btn_lg);
        element_map.get("ImageButton").put("info_btn_nopad", R.layout.element_imagebutton_info_btn_nopad);
        element_map.get("ImageButton").put("outline_info_btn_nopad", R.layout.element_imagebutton_outline_info_btn_nopad);
        element_map.get("ImageButton").put("info_btn_oval", R.layout.element_imagebutton_info_btn_oval);
        element_map.get("ImageButton").put("outline_info_btn_oval", R.layout.element_imagebutton_outline_info_btn_oval);
        element_map.get("ImageButton").put("info_btn_oval_sml", R.layout.element_imagebutton_info_btn_oval_sml);
        element_map.get("ImageButton").put("outline_info_btn_oval_sml", R.layout.element_imagebutton_outline_info_btn_oval_sml);
        element_map.get("ImageButton").put("info_btn_oval_lg", R.layout.element_imagebutton_info_btn_oval_lg);
        element_map.get("ImageButton").put("outline_info_btn_oval_lg", R.layout.element_imagebutton_outline_info_btn_oval_lg);
        element_map.get("ImageButton").put("info_btn_oval_nopad", R.layout.element_imagebutton_info_btn_oval_nopad);
        element_map.get("ImageButton").put("outline_info_btn_oval_nopad", R.layout.element_imagebutton_outline_info_btn_oval_nopad);
        element_map.get("ImageButton").put("light_btn", R.layout.element_imagebutton_light_btn);
        element_map.get("ImageButton").put("outline_light_btn", R.layout.element_imagebutton_outline_light_btn);
        element_map.get("ImageButton").put("light_btn_sml", R.layout.element_imagebutton_light_btn_sml);
        element_map.get("ImageButton").put("outline_light_btn_sml", R.layout.element_imagebutton_outline_light_btn_sml);
        element_map.get("ImageButton").put("light_btn_lg", R.layout.element_imagebutton_light_btn_lg);
        element_map.get("ImageButton").put("outline_light_btn_lg", R.layout.element_imagebutton_outline_light_btn_lg);
        element_map.get("ImageButton").put("light_btn_nopad", R.layout.element_imagebutton_light_btn_nopad);
        element_map.get("ImageButton").put("outline_light_btn_nopad", R.layout.element_imagebutton_outline_light_btn_nopad);
        element_map.get("ImageButton").put("light_btn_oval", R.layout.element_imagebutton_light_btn_oval);
        element_map.get("ImageButton").put("outline_light_btn_oval", R.layout.element_imagebutton_outline_light_btn_oval);
        element_map.get("ImageButton").put("light_btn_oval_sml", R.layout.element_imagebutton_light_btn_oval_sml);
        element_map.get("ImageButton").put("outline_light_btn_oval_sml", R.layout.element_imagebutton_outline_light_btn_oval_sml);
        element_map.get("ImageButton").put("light_btn_oval_lg", R.layout.element_imagebutton_light_btn_oval_lg);
        element_map.get("ImageButton").put("outline_light_btn_oval_lg", R.layout.element_imagebutton_outline_light_btn_oval_lg);
        element_map.get("ImageButton").put("light_btn_oval_nopad", R.layout.element_imagebutton_light_btn_oval_nopad);
        element_map.get("ImageButton").put("outline_light_btn_oval_nopad", R.layout.element_imagebutton_outline_light_btn_oval_nopad);
        element_map.get("ImageButton").put("dark_btn", R.layout.element_imagebutton_dark_btn);
        element_map.get("ImageButton").put("outline_dark_btn", R.layout.element_imagebutton_outline_dark_btn);
        element_map.get("ImageButton").put("dark_btn_sml", R.layout.element_imagebutton_dark_btn_sml);
        element_map.get("ImageButton").put("outline_dark_btn_sml", R.layout.element_imagebutton_outline_dark_btn_sml);
        element_map.get("ImageButton").put("dark_btn_lg", R.layout.element_imagebutton_dark_btn_lg);
        element_map.get("ImageButton").put("outline_dark_btn_lg", R.layout.element_imagebutton_outline_dark_btn_lg);
        element_map.get("ImageButton").put("dark_btn_nopad", R.layout.element_imagebutton_dark_btn_nopad);
        element_map.get("ImageButton").put("outline_dark_btn_nopad", R.layout.element_imagebutton_outline_dark_btn_nopad);
        element_map.get("ImageButton").put("dark_btn_oval", R.layout.element_imagebutton_dark_btn_oval);
        element_map.get("ImageButton").put("outline_dark_btn_oval", R.layout.element_imagebutton_outline_dark_btn_oval);
        element_map.get("ImageButton").put("dark_btn_oval_sml", R.layout.element_imagebutton_dark_btn_oval_sml);
        element_map.get("ImageButton").put("outline_dark_btn_oval_sml", R.layout.element_imagebutton_outline_dark_btn_oval_sml);
        element_map.get("ImageButton").put("dark_btn_oval_lg", R.layout.element_imagebutton_dark_btn_oval_lg);
        element_map.get("ImageButton").put("outline_dark_btn_oval_lg", R.layout.element_imagebutton_outline_dark_btn_oval_lg);
        element_map.get("ImageButton").put("dark_btn_oval_nopad", R.layout.element_imagebutton_dark_btn_oval_nopad);
        element_map.get("ImageButton").put("outline_dark_btn_oval_nopad", R.layout.element_imagebutton_outline_dark_btn_oval_nopad);
        element_map.put("ImageView", new HashMap<String, Integer>());
        element_map.get("ImageView").put("", R.layout.element_imageview);
        element_map.put("ProgressBar", new HashMap<String, Integer>());
        element_map.get("ProgressBar").put("", R.layout.element_progressbar);
        element_map.put("TextView", new HashMap<String, Integer>());
        element_map.get("TextView").put("", R.layout.element_textview);
    }


    WidgetUpdateListener updateListener = null;
    StatusListener statusListener = null;
    Handler handler;
    StartupState startupState = StartupState.IDLE;

    final Object lock = new Object();
    HashMap<Integer, TaskQueue> widgetsTasks = new HashMap<>();

    HashMap<Integer, String> widgets = new HashMap<>();
    HashMap<Integer, Integer> androidToWidget = new HashMap<>();
    HashMap<Integer, Integer> widgetToAndroid = new HashMap<>();
    HashMap<Integer, Timer> activeTimers = new HashMap<>();
    HashMap<Integer, PendingIntent> activeTimersIntents = new HashMap<>();
    HashMap<Pair<Integer, Integer>, HashMap<Integer, ListFactory>> factories = new HashMap<>();
    ArrayList<PythonFile> pythonFiles = new ArrayList<>();
    HashSet<Integer> needUpdateWidgets = new HashSet<>();

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
        attributeValue = attributeValue.replace("-", "+-");

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

            Attributes.AttributeValue.Reference reference = new Attributes.AttributeValue.Reference();
            int parEnd = arg.indexOf(")", idx.first);
            String refId = arg.substring(idx.first + 2, parEnd);
            reference.id = refId.equalsIgnoreCase("p") ? -1 : Integer.parseInt(refId);
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

    public ArrayList<DynamicView> initTestWidget(int widgetId)
    {
        Log.d("APPY", "initWidget " + widgetId);

        ArrayList<DynamicView> views = new ArrayList<>();

        DynamicView btn = new DynamicView("Button");
        btn.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(btn.type, "setText"), "setText", "initial button"));

        Attributes.AttributeValue _100px = attributeParse("100");
        Attributes.AttributeValue halfWidth = attributeParse("w(p)*0.5");
        Attributes.AttributeValue halfHeight = attributeParse("h(p)*0.5");

        //btn.attributes.attributes.put(Attributes.Type.WIDTH, halfWidth);
        //btn.attributes.attributes.put(Attributes.Type.HEIGHT, attributeParse("151"));

        views.add(btn);

        //DynamicView btn2 = new DynamicView("Button");
        //btn2.attributes.attributes.put(Attributes.Type.TOP, halfHeight);
        //btn2.attributes.attributes.put(Attributes.Type.WIDTH, attributeParse("w("+btn.getId()+")*0.5"));

        //views.add(btn2);


//        DynamicView lst = new DynamicView("ListView");
//        for(int i = 0; i < 10; i++)
//        {
//            DynamicView txt = new DynamicView("TextView");
//            txt.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(btn.type, "setText"), "setText", "text"+i));
//            ArrayList<DynamicView> listItem = new ArrayList<>();
//            listItem.add(txt);
//            lst.children.add(listItem);
//        }
//
//        lst.attributes.attributes.put(Attributes.Type.LEFT, halfWidth);
//        lst.attributes.attributes.put(Attributes.Type.HEIGHT, _100px);
//
//        views.add(lst);

        return views;
    }

    public ArrayList<DynamicView> updateTestWidget(int widgetId, ArrayList<DynamicView> root)
    {
        Log.d("APPY", "updateWidget " + widgetId);

        DynamicView view = root.get(0);
        view.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(view.type, "setText"), "setText", "" + new Random().nextInt(1000)));
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
            this.list = DynamicView.fromJSON(list);
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
                if (position < list.children.size())
                {
                    ArrayList<DynamicView> dynamicViewCopy = DynamicView.fromJSONArray(DynamicView.toJSONString(list.children.get(position)));
                    RemoteViews remoteView = resolveDimensions(context, widgetId, dynamicViewCopy, true, list.actualWidth, list.actualHeight);
                    Intent fillIntent = new Intent(context, WidgetReceiver.class);
                    if (list.children.get(position).size() == 1)
                    {
                        fillIntent.putExtra(ITEM_ID_EXTRA, list.children.get(position).get(0).getId());
                    }
                    fillIntent.putExtra(COLLECTION_POSITION_EXTRA, position);
                    remoteView.setOnClickFillInIntent(R.id.collection_root, fillIntent);
                    return remoteView;
                }
            }
            catch (InvocationTargetException | IllegalAccessException e)
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

    public static float dipToPixels(Context context, float dipValue)
    {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    }

    public int[] getWidgetDimensions(AppWidgetManager appWidgetManager, int androidWidgetId)
    {
        Bundle bundle = appWidgetManager.getAppWidgetOptions(androidWidgetId);
        //only works on portrait
        return new int[]{(int) dipToPixels(this, bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)),
                (int) dipToPixels(this, bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT))};
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

        Integer res = collection_map.get(collections);
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

    public Pair<Integer, Integer> getElementIds(int n)
    {
        switch (n)
        {
            case 0:
                return new Pair<>(R.id.e0, R.id.l0);
            case 1:
                return new Pair<>(R.id.e1, R.id.l1);
        }
        throw new IllegalArgumentException((n + 1) + " collections are not supported");
    }

    public int typeToLayout(String type, String style)
    {
        if(style == null)
        {
            style = "";
        }

        if(!element_map.containsKey(type))
        {
            throw new IllegalArgumentException("unknown type " + type);
        }

        if(!element_map.get(type).containsKey(style))
        {
            throw new IllegalArgumentException("unknown style " + style + " for type " + type);
        }

        return element_map.get(type).get(style);
    }

    public RemoteViews generate(Context context, int widgetId, ArrayList<DynamicView> dynamicList, boolean keepDescription, boolean inCollection) throws InvocationTargetException, IllegalAccessException
    {
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
        int elements_id = R.id.elements;

        if (inCollection)
        {
            //can only be collections == 0
            //this fixes a bug when using the same id? i think it's in removeAllViews or in addView
            root_xml = R.layout.collection_element;
            elements_id = R.id.collection_elements;
        }

        RemoteViews rootView = new RemoteViews(context.getPackageName(), root_xml);
        rootView.removeAllViews(elements_id);

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
                Pair<Integer, Integer> ids = getElementIds(index);
                layout.view_id = ids.first;
                layout.container_id = ids.second;
            }
            else
            {
                if (!layout.children.isEmpty())
                {
                    throw new IllegalArgumentException("only collections can have children, not " + layout.type);
                }
                layout.xml_id = typeToLayout(layout.type, layout.style);
                layout.container_id = R.id.l0;
                layout.view_id = R.id.e0;
                remoteView = new RemoteViews(context.getPackageName(), layout.xml_id);
            }

            for (RemoteMethodCall methodCall : layout.methodCalls)
            {
//                Log.d("APPY", "calling method "+methodCall.toString());
                methodCall.call(remoteView, methodCall.isParentCall() ? layout.container_id : layout.view_id);
            }

            if (!keepDescription)
            {
                remoteView.setCharSequence(layout.view_id, "setContentDescription", layout.getId() + "");
            }

            Intent clickIntent = new Intent(context, WidgetReceiver.class);
            clickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            clickIntent.putExtra(WIDGET_ID_EXTRA, widgetId);

            if (isCollection(layout.type))
            {
                if (keepDescription)
                {
                    clickIntent.putExtra(COLLECTION_ITEM_ID_EXTRA, layout.getId());
                    remoteView.setPendingIntentTemplate(layout.view_id, PendingIntent.getBroadcast(context, widgetId + (layout.getId() << 16), clickIntent, PendingIntent.FLAG_UPDATE_CURRENT));

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
            }
            else if (!inCollection)
            {
                clickIntent.putExtra(ITEM_ID_EXTRA, layout.getId());
                if (layout.tag instanceof Integer)
                {
                    clickIntent.putExtra(ITEM_TAG_EXTRA, (Integer) layout.tag);
                }
                remoteView.setOnClickPendingIntent(layout.view_id, PendingIntent.getBroadcast(context, widgetId + (layout.getId() << 16), clickIntent, PendingIntent.FLAG_UPDATE_CURRENT));
            }

            if (remoteView != rootView)
            {
                rootView.addView(elements_id, remoteView);
            }
        }
        return rootView;
    }

    //only one level
    public DynamicView find(ArrayList<DynamicView> dynamicList, int id)
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

    public Pair<Integer, Integer> resolveAxis(int lenLimit, Attributes.AttributeValue start, Attributes.AttributeValue end, Attributes.AttributeValue len)
    {
        int padStart;
        int padEnd;

        if (len.triviallyResolved && !start.triviallyResolved && !end.triviallyResolved)
        {
            padStart = start.resolvedValue.intValue();
            padEnd = end.resolvedValue.intValue();
        }
        else if (start.triviallyResolved && !end.triviallyResolved)
        {
            padEnd = end.resolvedValue.intValue();
            padStart = lenLimit - len.resolvedValue.intValue() - padEnd;
        }
        else
        {
            padStart = start.resolvedValue.intValue();
            padEnd = lenLimit - len.resolvedValue.intValue() - padStart;
        }

        return new Pair<>(padStart >= 0 ? padStart : 0, padEnd >= 0 ? padEnd : 0);
    }

    public RemoteViews resolveDimensions(Context context, int widgetId, ArrayList<DynamicView> dynamicList, boolean inCollection, int widthLimit, int heightLimit) throws InvocationTargetException, IllegalAccessException
    {
        RemoteViews remote = generate(context, widgetId, dynamicList, false, inCollection);
        RelativeLayout layout = new RelativeLayout(this);
        View inflated = remote.apply(context, layout);
        layout.addView(inflated);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) inflated.getLayoutParams();
        params.width = widthLimit;
        params.height = heightLimit;
        inflated.setLayoutParams(params);

        Log.d("APPY", "limits: " + widthLimit + ", " + heightLimit);

        Attributes rootAttributes = new Attributes();
        rootAttributes.attributes.get(Attributes.Type.LEFT).tryTrivialResolve(0);
        rootAttributes.attributes.get(Attributes.Type.TOP).tryTrivialResolve(0);
        rootAttributes.attributes.get(Attributes.Type.RIGHT).tryTrivialResolve(0);
        rootAttributes.attributes.get(Attributes.Type.BOTTOM).tryTrivialResolve(0);
        rootAttributes.attributes.get(Attributes.Type.WIDTH).tryTrivialResolve(widthLimit);
        rootAttributes.attributes.get(Attributes.Type.HEIGHT).tryTrivialResolve(heightLimit);

        ViewGroup supergroup = (ViewGroup) inflated;

//        Log.d("APPY", "child count: ");
//        printChildCount(supergroup, "  ");

        if (supergroup.getChildCount() > 2)
        {
            throw new IllegalArgumentException("supergroup children count is larger than 2");
        }

        //set all to WRAP_CONTENT to measure it's default size (all children are leaves of collections)
        for (int k = 0; k < supergroup.getChildCount(); k++)
        {
            ViewGroup group = (ViewGroup) supergroup.getChildAt(k);
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
        for (int k = 0; k < supergroup.getChildCount(); k++)
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
        while (true)
        {
            int iterationResolved = 0;
            for (int k = 0; k < supergroup.getChildCount(); k++)
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

            if (iterationResolved == 0) //no way to advance from here
            {
                break;
            }
        }

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
            if (inCollection)
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

    public static String getSetterMethod(String type, String method)
    {
        return typeToRemotableMethod.get(type).get(method);
    }

    public static HashMap<String, String> getRemotableMethods(String type)
    {
        Class<?> clazz = typeToClass.get(type);

        HashMap<String, String> methods = new HashMap<>();
        for (Method method : clazz.getMethods())
        {
            Annotation[] annotations = method.getAnnotations();
            boolean remotable = false;
            for (Annotation annotation : annotations)
            {
                if (annotation.annotationType().getName().equals("android.view.RemotableViewMethod"))
                {
                    remotable = true;
                    break;
                }
            }

            if (remotable)
            {
                if (method.getParameterTypes().length != 1)
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
                ArrayList<DynamicView> out = initTestWidget(widgetId);
                if (out != null)
                {
                    return DynamicView.toJSONString(out);
                }
                return null;
            }

            @Override
            public String onUpdate(int widgetId, String views)
            {
                ArrayList<DynamicView> out = updateTestWidget(widgetId, DynamicView.fromJSONArray(views));
                if (out != null)
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
            public Object[] onItemClick(int widgetId, String views, int collectionId, int position)
            {
                Log.d("APPY", "on item click: " + collectionId + " " + position);
                return new Object[]{false, null};
            }

            @Override
            public String onClick(int widgetId, String views, int id)
            {
                Log.d("APPY", "on click: " + id);
                return null;
            }

            @Override
            public String onTimer(int timerId, int widgetId, String views, String data)
            {
                return null;
            }

            @Override
            public String onPost(int widgetId, String views, String data)
            {
                return null;
            }

            @Override
            public void wipeStateRequest()
            {

            }

            @Override
            public void importFile(String path)
            {

            }

            @Override
            public void deimportFile(String path)
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

    public Integer getAndroidWidget(int widget)
    {
        return widgetToAndroid.get(widget);
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
            e.printStackTrace();
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

    public int generateTimerId()
    {
        synchronized (lock)
        {
            if (activeTimers.isEmpty())
            {
                return 1;
            }
            int newId = Collections.max(activeTimers.keySet()) + 1;
            activeTimers.put(newId, null); //save room
            return newId;
        }
    }

    static class Timer
    {
        public Timer(int widgetId, long millis, int type, String data)
        {
            this.widgetId = widgetId;
            this.millis = millis;
            this.type = type;
            this.data = data;
        }

        @Override
        public String toString()
        {
            return widgetId + ", " + millis + ", " + type + ", " + data;
        }

        int widgetId;
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
                obj.put("millis", timer.millis);
                obj.put("type", timer.type);
                obj.put("data", timer.data);
                return obj;
            }
            catch (JSONException e)
            {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public Timer invert(Object o)
        {
            JSONObject obj = (JSONObject) o;
            try
            {
                return new Timer(obj.getInt("widgetId"),
                        obj.getInt("millis"),
                        obj.getInt("type"),
                        obj.getString("data"));
            }
            catch (JSONException e)
            {
                throw new IllegalArgumentException();
            }
        }
    }

    public void cancelTimer(int timerId)
    {
        cancelTimer(timerId, true);
    }

    public void cancelTimer(int timerId, boolean save)
    {
        PendingIntent pendingIntent;
        synchronized (lock)
        {
            activeTimers.remove(timerId);
            pendingIntent = activeTimersIntents.remove(timerId);
        }
        if (pendingIntent != null)
        {
            AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            mgr.cancel(pendingIntent);
        }

        if (save)
        {
            saveTimers();
        }
    }

    public void cancelWidgetTimers(int widgetId)
    {
        HashMap<Integer, Timer> activeTimersCopy;
        synchronized (lock)
        {
            activeTimersCopy = new HashMap<>(activeTimers);
        }
        HashSet<Integer> toCancel = new HashSet<>();
        for (Map.Entry<Integer, Timer> timer : activeTimersCopy.entrySet())
        {
            if (timer.getValue().widgetId == widgetId)
            {
                toCancel.add(timer.getKey());
            }
        }

        for (int timer : toCancel)
        {
            cancelTimer(timer, false);
        }
        saveTimers();
    }

    public void cancelAllTimers()
    {
        Log.d("APPY", "cancelling all timers");
        HashSet<Integer> toCancel = new HashSet<>();
        synchronized (lock)
        {
            toCancel.addAll(activeTimers.keySet());
        }
        for (int timer : toCancel)
        {
            cancelTimer(timer, false);
        }
        saveTimers();
    }

    public abstract class ArgRunnable implements Runnable
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

    public int setTimer(long millis, int type, int widgetId, String data)
    {
        return setTimer(millis, type, widgetId, data, -1);
    }

    public int setTimer(long millis, int type, int widgetId, String data, int timerId)
    {
        if (type == TIMER_RELATIVE)
        {
            millis += System.currentTimeMillis();
            type = TIMER_ABSOLUTE;
        }

        if (timerId == -1)
        {
            timerId = generateTimerId();
        }
        Intent timerIntent = new Intent(Widget.this, getClass());
        timerIntent.setAction("timer" + timerId); //make it unique for cancel
        timerIntent.putExtra("widgetId", widgetId);
        timerIntent.putExtra("timer", timerId);
        timerIntent.putExtra("timerData", data);

        PendingIntent pendingIntent = null;

        synchronized (lock)
        {
            activeTimers.put(timerId, new Timer(widgetId, millis, type, data));
            activeTimersIntents.put(timerId, pendingIntent);
        }
        saveTimers();

        if (type == TIMER_REPEATING && millis <= 10 * 60 * 1000)
        {
            Log.d("APPY", "setting short time timer");
            handler.post(new ArgRunnable(timerIntent, millis, timerId)
            {
                boolean first = true;

                @Override
                public void run()
                {
                    if (!first)
                    {
                        Log.d("APPY", "short time timer fire");
                        Widget.this.startService((Intent) args[0]);
                    }
                    first = false;
                    int timer = (int) args[2];
                    Timer obj;
                    synchronized (lock)
                    {
                        obj = activeTimers.get(timer);
                    }
                    if (obj != null)
                    {
                        //timer still active
                        handler.postDelayed(this, (long) args[1]);
                    }
                }
            });
        }
        else
        {
            AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            pendingIntent = PendingIntent.getService(Widget.this, 1, timerIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            //clear previous alarm (if we crashed but no reboot)
            mgr.cancel(pendingIntent);

            if (type == TIMER_REPEATING)
            {
                Log.d("APPY", "setting long time timer: " + millis);
                mgr.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + millis, millis, pendingIntent);
            }
            else
            {
                Log.d("APPY", "setting one time timer");
                mgr.set(AlarmManager.RTC, millis, pendingIntent);
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
                HashMap<Integer, Timer> loaded = new MapSerialize<Integer, Timer>().deserialize(timersString, new MapSerialize.IntKey(), new TimerValue());
                Log.d("APPY", "loaded " + loaded.size() + " timers");
                for (Map.Entry<Integer, Timer> timer : loaded.entrySet())
                {
                    setTimer(timer.getValue().millis, timer.getValue().type, timer.getValue().widgetId, timer.getValue().data, timer.getKey());
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
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
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void savePythonFiles()
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        synchronized (lock)
        {
            editor.putString("pythonfiles", PythonFile.serializeArray(pythonFiles));
        }
        editor.apply();
    }

    public void refreshPythonFile(PythonFile file)
    {
        refreshPythonFile(file, true);
    }

    public void refreshPythonFile(PythonFile file, boolean usePool)
    {
        Task task = new Task<>(new CallImportTask(), file);
        if (usePool)
        {
            addTask(IMPORT_TASK_QUEUE, task);
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
            refreshPythonFile(f, false);
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
        savePythonFiles();
        for (PythonFile file : files)
        {
            refreshPythonFile(file);
        }
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
            updateListener.deimportFile(file.path);
        }
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
            editor.putString("timers", new MapSerialize<Integer, Timer>().serialize(activeTimers, new MapSerialize.IntKey(), new TimerValue()));
        }
        editor.apply();
    }

    public void clearWidget(int widgetId)
    {
        delete(widgetId);
        update(widgetId);
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

    public void restart()
    {
        Log.d("APPY", "restarting process");
        saveWidgets();
        saveWidgetMapping();
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

    public void setPythonUnpacked(boolean unpacked)
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("unpacked", unpacked);
        editor.apply();
    }

    public boolean getPythonUnpacked()
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        return sharedPref.getBoolean("unpacked", false);
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

                    RemoteViews view = resolveDimensions(Widget.this, widgetId, views, false, widthLimit, heightLimit);
                    //appWidgetManager.notifyAppWidgetViewDataChanged(androidWidgetId, R.id.root);
                    appWidgetManager.updateAppWidget(androidWidgetId, view);
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                    if(errorOnFailure)
                    {
                        setSpecificErrorWidget(androidWidgetId, widgetId);
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
        textView.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(textView.type, "setText"), "setText", "Loading..."));

        Attributes.AttributeValue wholeWidth = attributeParse("w(p)");
        Attributes.AttributeValue wholeHeight = attributeParse("h(p)");
        textView.attributes.attributes.put(Attributes.Type.WIDTH, wholeWidth);
        textView.attributes.attributes.put(Attributes.Type.HEIGHT, wholeHeight);

        views.add(textView);

        setWidget(androidWidgetId, SPECIAL_WIDGET_ID, views, true);

        needUpdateWidgets.add(widgetId);
    }

    public void setSpecificErrorWidget(int androidWidgetId, int widgetId)
    {
        ArrayList<DynamicView> views = new ArrayList<>();

        DynamicView textView = new DynamicView("TextView");
        textView.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(textView.type, "setText"), "setText", "Error"));

        DynamicView restart = new DynamicView("ImageButton");
        restart.style = "success_btn_oval_nopad";
        restart.methodCalls.add(new RemoteMethodCall("setColorFilter", false, getSetterMethod(restart.type, "setColorFilter"), "setColorFilter", 0xffffffff));
        restart.methodCalls.add(new RemoteMethodCall("setImageResource", false, getSetterMethod(restart.type, "setImageResource"), "setImageResource", android.R.drawable.ic_lock_power_off));
        restart.methodCalls.add(new RemoteMethodCall("setDrawableParameters", false, "setDrawableParameters", true, -1, 0x7f000000, android.graphics.PorterDuff.Mode.SRC_OVER, -1));
        restart.attributes.attributes.put(Attributes.Type.WIDTH, attributeParse("80"));
        restart.attributes.attributes.put(Attributes.Type.HEIGHT, attributeParse("80"));
        restart.attributes.attributes.put(Attributes.Type.RIGHT, attributeParse("0"));
        restart.attributes.attributes.put(Attributes.Type.BOTTOM, attributeParse("0"));
        restart.tag = SPECIAL_WIDGET_RESTART; //onclick

        views.add(textView);
        views.add(restart);

        if(widgetId > 0)
        {
            Attributes.AttributeValue afterText = attributeParse("h("+textView.getId()+")+10");

            DynamicView clear = new DynamicView("Button");
            clear.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(clear.type, "setText"), "setText", "Clear"));
            clear.style = "dark_btn_sml";
            clear.attributes.attributes.put(Attributes.Type.TOP, afterText);
            clear.attributes.attributes.put(Attributes.Type.LEFT, attributeParse("l(p)"));
            clear.tag = widgetId + (SPECIAL_WIDGET_CLEAR << 16);

            DynamicView reload = new DynamicView("Button");
            reload.methodCalls.add(new RemoteMethodCall("setText", false, getSetterMethod(reload.type, "setText"), "setText", "Reload"));
            reload.style = "info_btn_sml";
            reload.attributes.attributes.put(Attributes.Type.TOP, afterText);
            reload.attributes.attributes.put(Attributes.Type.RIGHT, attributeParse("r(p)"));
            reload.tag = widgetId + (SPECIAL_WIDGET_RELOAD << 16);

            views.add(clear);
            views.add(reload);
        }

        setWidget(androidWidgetId, SPECIAL_WIDGET_ID, views, false);

        needUpdateWidgets.add(widgetId);
    }

    public int[] requestAndroidWidgets()
    {
        ComponentName thisWidget = new ComponentName(this, WidgetReceiver.class);
        return AppWidgetManager.getInstance(this).getAppWidgetIds(thisWidget);
    }

    public void setAllWidgets(boolean error)
    {
        for(int id : requestAndroidWidgets())
        {
            if(error)
            {
                setSpecificErrorWidget(id, 0);
            }
            else
            {
                setSpecificLoadingWidget(id, -1);
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
            startupState = StartupState.RUNNING;
            callStatusChange(true);

            error = false;
            String pythonHome = new File(getFilesDir(), "python").getAbsolutePath();
            String pythonLib = new File(pythonHome, "/lib/libpython3.6m.so").getAbsolutePath(); //must be without
            String cacheDir = getCacheDir().getAbsolutePath();
            try
            {
                if(!getPythonUnpacked())
                {
                    unpackPython(getAssets().open("python.targz"), pythonHome);
                    setPythonUnpacked(true);
                }
                else
                {
                    Log.d("APPY", "python already unpacked");
                }
                copyAsset(getAssets().open("main.py"), new File(cacheDir, "main.py"));
                copyAsset(getAssets().open("logcat.py"), new File(cacheDir, "logcat.py"));
                copyAsset(getAssets().open("appy.targz"), new File(cacheDir, "appy.tar.gz"));
                System.load(pythonLib);
                System.loadLibrary("native");
                pythonInit(pythonHome, cacheDir, pythonLib, new File(cacheDir, "main.py").getAbsolutePath(), Widget.this);
                //java_widget();
                initAllPythonFiles();
            }
            catch(Exception e)
            {
                Log.e("APPY", "exception", e);
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
                    startService(new Intent(Widget.this, Widget.class));
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

    private class CallEventTask implements Runner<Integer>
    {
        @Override
        public void run(Integer... args)
        {
            callEventWidget(args[0], args[1], args[2], args[3]);
        }
    }

    private class CallTimerTask implements Runner<Object>
    {
        @Override
        public void run(Object... args)
        {
            callTimerWidget((int)args[0], (int)args[1], (String)args[2]);
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

    public void setFileInfo(String path, String info)
    {
        for(PythonFile file : getPythonFiles())
        {
            if(file.path.equals(path))
            {
                file.info = info;
            }
        }
        savePythonFiles();
    }

    private class CallImportTask implements Runner<PythonFile>
    {
        @Override
        public void run(PythonFile... args)
        {
            PythonFile file = args[0];
            file.state = PythonFile.State.RUNNING;
            callStatusChange(false);

            try
            {
                if (updateListener != null)
                {
                    updateListener.importFile(file.path);
                    file.state = PythonFile.State.ACTIVE;
                }
            }
            catch(Exception e)
            {
                file.info = getStacktrace(e);
                e.printStackTrace();
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
                saveWidgetMapping();
                saveWidgets();
            }
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

    public void callTimerWidget(final int timerId, int widgetId, final String data)
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
        if(timer.type != TIMER_REPEATING)
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
            e.printStackTrace();
            Log.d("APPY", "error in caller");
            setSpecificErrorWidget(androidWidgetId, widgetId);
            return true;
        }

        return false;
    }

    public void callEventWidget(int eventWidgetId, final int itemId, final int collectionItemId, final int collectionPosition)
    {
        Log.d("APPY", "got intent: " + eventWidgetId + " " + itemId);

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
                    Log.d("APPY", "calling listener onItemClick");
                    Object[] ret = updateListener.onItemClick(widgetId, current, collectionItemId, collectionPosition);
                    boolean handled = (boolean)ret[0];
                    fromItemClick = (String)ret[1];
                    if(handled || itemId == 0)
                    {
                        Log.d("APPY", "suppressing click on "+itemId);
                        return fromItemClick;
                    }
                }

                Log.d("APPY", "calling listener onClick");
                String newwidget = updateListener.onClick(widgetId, fromItemClick != null ? fromItemClick : current, itemId);
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
            setSpecificErrorWidget(androidWidgetId, widgetId);
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
                setSpecificErrorWidget(androidWidgetId, widgetId);
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
        handleStartCommand(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    public class LocalBinder extends Binder
    {
        Widget getService() {
            return Widget.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if(intent.getBooleanExtra(LOCAL_BIND_EXTRA, false))
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

    public StartupState getStartupState()
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
        if(!startedAfterSetup && pythonSetupTask.getStatus() == AsyncTask.Status.PENDING)
        {
            startupState = StartupState.IDLE;
            handler = new Handler();

            loadPythonFiles();
            loadWidgets();
            loadTimers();

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
            startupState = StartupState.ERROR;
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

        startupState = StartupState.COMPLETED;
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
            String trace = getStacktrace(e);

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
                e1.printStackTrace();
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
            widgetIntent = intent.getParcelableExtra(WIDGET_INTENT);
        }

        if(firstStart)
        {

            int[] ids = requestAndroidWidgets();
            if(ids != null)
            {
                for (int id : ids)
                {
                    int widgetId = fromAndroidWidget(id, true);
                    needUpdateWidgets.add(widgetId);
                    addTask(widgetId, new Task<>(new CallUpdateTask(), widgetId));
                }
            }
        }
        else if (intent != null && intent.hasExtra("widgetId") && intent.hasExtra("timer"))
        {
            Log.d("APPY", "timer fire");
            addTask(intent.getIntExtra("widgetId", -1), new Task<>(new CallTimerTask(), intent.getIntExtra("timer", -1), intent.getIntExtra("widgetId", -1), intent.getStringExtra("timerData")));
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
                addTask(widgetId, new Task<>(new CallUpdateTask(), widgetId));
            }
            else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(widgetIntent.getAction()))
            {
                if(widgetIntent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS))
                {
                    for (int androidWidgetId : widgetIntent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS))
                    {
                        int widgetId = fromAndroidWidget(androidWidgetId, true);
                        addTask(widgetId, new Task<>(new CallUpdateTask(), widgetId));
                    }
                }
                else if(widgetIntent.hasExtra(WIDGET_ID_EXTRA))
                {
                    int eventWidgetId = widgetIntent.getIntExtra(WIDGET_ID_EXTRA, -1);

                    if(eventWidgetId == SPECIAL_WIDGET_ID)
                    {
                        int tag = widgetIntent.getIntExtra(ITEM_TAG_EXTRA, 0);
                        if(tag == SPECIAL_WIDGET_RESTART)
                        {
                            restart();
                        }
                        else if(((tag >> 16) & 0xffff) == SPECIAL_WIDGET_CLEAR)
                        {
                            int widgetId = tag & 0xffff;
                            if(widgetId > 0)
                            {
                                Log.d("APPY", "clearing " + widgetId);
                                clearWidget(widgetId);
                            }
                        }
                        else if(((tag >> 16) & 0xffff) == SPECIAL_WIDGET_RELOAD)
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
                        addTask(eventWidgetId, new Task<>(new CallEventTask(), eventWidgetId, widgetIntent.getIntExtra(ITEM_ID_EXTRA, 0), widgetIntent.getIntExtra(COLLECTION_ITEM_ID_EXTRA, 0), widgetIntent.getIntExtra(COLLECTION_POSITION_EXTRA, -1)));
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
            e.printStackTrace();
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

    public static void unpackPython(InputStream pythontar, String pythonHome)
    {
        Log.d("APPY", "unpacking python");
        try
        {
            untar(pythontar, pythonHome);
        }
        catch(IOException e){
            e.printStackTrace();
            Log.e("APPY", "tar exception: "+e);
        }
        Log.d("APPY", "done unpacking python");
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
                    throw new IOException(e.getMessage());
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

    public static void untar(InputStream tar, String dest) throws IOException
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

    public static String getStacktrace(Throwable e)
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    protected static native void pythonInit(String pythonHome, String tmpPath, String pythonLibPath, String script, Object arg);
    protected static native Object pythonCall(Object... args) throws Throwable;
}
