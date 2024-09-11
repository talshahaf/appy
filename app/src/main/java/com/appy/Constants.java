package com.appy;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.AdapterViewFlipper;
import android.widget.AnalogClock;
import android.widget.Button;
import android.widget.CheckBox;
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
import android.widget.StackView;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.ViewFlipper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class Constants
{
    public static final String ITEM_ID_EXTRA = "ITEM_ID";
    public static final String ITEM_TAG_EXTRA = "ITEM_TAG";
    public static final String WIDGET_INTENT = "WIDGET_INTENT";
    public static final String COLLECTION_ID_EXTRA = "COLLECTION_ITEM_ID_EXTRA";
    public static final String COLLECTION_POSITION_EXTRA = "COLLECTION_POSITION_EXTRA";
    public static final String LIST_SERIALIZED_EXTRA = "LIST_SERIALIZED_EXTRA";
    public static final String XML_ID_EXTRA = "XML_ID_EXTRA";
    public static final String VIEW_ID_EXTRA = "VIEW_ID_EXTRA";
    public static final String WIDGET_ID_EXTRA = "WIDGET_ID_EXTRA";
    public static final String LOCAL_BIND_EXTRA = "LOCAL_BIND_EXTRA";
    public static final String FRAGMENT_NAME_EXTRA = "FRAGMENT_NAME_EXTRA";
    public static final String FRAGMENT_ARG_EXTRA = "FRAGMENT_ARG_EXTRA";
    public static final String FRAGMENT_ARG_WIDGET = "FRAGMENT_ARG_WIDGET";
    public static final String FRAGMENT_ARG_CONFIG = "FRAGMENT_ARG_CONFIG";
    public static final String FRAGMENT_ARG_FILEURI = "FRAGMENT_ARG_FILEURI";
    public static final String FRAGMENT_ARG_REQUESTCODE = "FRAGMENT_ARG_REQUESTCODE";
    public static final String APP_PACKAGE_NOCOM = "appy";
    public static final String APP_PACKAGE_NAME = "com." + APP_PACKAGE_NOCOM;
    public static final String DEEP_LINK_BROADCAST = APP_PACKAGE_NAME + ".DeepLink";
    public static final int SPECIAL_WIDGET_ID = 100;
    public static final int SPECIAL_WIDGET_RESTART = 1;
    public static final int SPECIAL_WIDGET_CLEAR = 2;
    public static final int SPECIAL_WIDGET_RELOAD = 3;
    public static final int SPECIAL_WIDGET_SHOWERROR = 4;
    public static final int SPECIAL_WIDGET_OPENAPP = 5;
    public static final int TIMER_RELATIVE = 1;
    public static final int TIMER_ABSOLUTE = 2;
    public static final int TIMER_REPEATING = 3;
    public static final int IMPORT_TASK_QUEUE = -1;
    public static final int TEXT_COLOR = 0xb3ffffff;
    public static final int TIMER_MAX_HANDLER = 60 * 60 * 1000; //1 hour
    public static final int ERROR_COALESCE_MILLI = 60 * 1000; //1 minute
    public static final int TASK_QUEUE_SUSPICIOUS_SIZE = 20;
    public static final int CONFIG_IMPORT_MAX_SIZE = 100 * 1024 * 1024;
    public static final int CRASH_FILE_MAX_SIZE = 10 * 1024 * 1024;
    public static final int PYTHON_FILE_MAX_SIZE = 100 * 1024 * 1024;
    public static final int STORE_CURSOR_SIZE = 100 * 1024 * 1024;
    public static final String[] CRASHES_FILENAMES = {
            "javacrash.txt",
            "pythoncrash.txt",
            "javatrace.txt",
            "pythontrace.txt",
    };
    public static final String CRASH_ZIP_PATH = "crash.zip";

    public enum CrashIndex
    {
        JAVA_CRASH_INDEX,
        PYTHON_CRASH_INDEX,
        JAVA_TRACE_INDEX,
        PYTHON_TRACE_INDEX,
    }

    public enum StartupState
    {
        IDLE,
        RUNNING,
        ERROR,
        COMPLETED,
    }

    public enum CollectionLayout
    {
        NOT_COLLECTION,
        UNCONSTRAINED,
        VERTICAL,
        HORIZONTAL,
        BOTH,
    }

    public static HashMap<String, Class<?>> typeToClass = new HashMap<>();
    public static HashMap<String, HashMap<String, String>> typeToRemotableMethod = new HashMap<>();
    public static HashMap<Class<?>, String> parameterToSetter = new HashMap<>();
    public static HashMap<String, String> preferredSetter = new HashMap<>();

    static
    {
        typeToClass.put("FrameLayout", FrameLayout.class);
        typeToClass.put("LinearLayout", LinearLayout.class);
        typeToClass.put("RelativeLayout", RelativeLayout.class);
        typeToClass.put("GridLayout", GridLayout.class);
        typeToClass.put("AnalogClock", AnalogClock.class);
        typeToClass.put("Button", Button.class);
        typeToClass.put("CheckBox", CheckBox.class);
        typeToClass.put("Chronometer", Chronometer.class);
        typeToClass.put("ImageButton", ImageButton.class);
        typeToClass.put("ImageView", ImageView.class);
        typeToClass.put("ProgressBar", ProgressBar.class);
        typeToClass.put("Switch", Switch.class);
        typeToClass.put("TextClock", TextClock.class);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            parameterToSetter.put(ColorStateList.class, "setColorStateList");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            parameterToSetter.put(BlendMode.class, "setBlendMode");
        }

        // TODO implement overloading?
        preferredSetter.put("setText", "setCharSequence");
        preferredSetter.put("setTextColor", "setInt");
        preferredSetter.put("setFocusable", "setBoolean");
        preferredSetter.put("setHint", "setCharSequence");
        preferredSetter.put("setAlpha", "setInt");

        for (String type : typeToClass.keySet())
        {
            typeToRemotableMethod.put(type, getRemotableMethods(type));
        }
    }

    static HashMap<String, CollectionLayout> collection_layout_type = new HashMap<>();

    static
    {
        collection_layout_type.put("ListView", CollectionLayout.VERTICAL);
        collection_layout_type.put("GridView", CollectionLayout.BOTH);
        collection_layout_type.put("StackView", CollectionLayout.UNCONSTRAINED);
        collection_layout_type.put("AdapterViewFlipper", CollectionLayout.UNCONSTRAINED);
    }

    static HashMap<List<String>, Integer> collection_map = new HashMap<>();

    static
    {
        collection_map.put(Collections.singletonList("ListView"), R.layout.root_listview);
        collection_map.put(Collections.singletonList("GridView"), R.layout.root_gridview);
        collection_map.put(Collections.singletonList("StackView"), R.layout.root_stackview);
        collection_map.put(Collections.singletonList("AdapterViewFlipper"), R.layout.root_adapterviewflipper);
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

    static class SelectorElement
    {
        int res;
        HashMap<String, String> selectors = new HashMap<>();

        SelectorElement(int res, String... selector_pairs)
        {
            this.res = res;
            if (selector_pairs.length % 2 != 0)
            {
                throw new IllegalArgumentException("selector pairs argument must be even");
            }
            for (int i = 0; i < selector_pairs.length; i += 2)
            {
                selectors.put(selector_pairs[i], selector_pairs[i + 1]);
            }
        }

        int fit(HashMap<String, String> required)
        {
            for (String key : required.keySet())
            {
                if (!required.get(key).equals(selectors.get(key)))
                {
                    return 0;
                }
            }
            return selectors.size();
        }

        int getResource()
        {
            return res;
        }
    }

    static HashMap<String, ArrayList<SelectorElement>> element_map = new HashMap<>();

    static
    {
        element_map.put("AnalogClock", new ArrayList<SelectorElement>());
        element_map.get("AnalogClock").add(new SelectorElement(R.layout.element_analogclock, "", ""));
        element_map.put("Button", new ArrayList<SelectorElement>());
        element_map.get("Button").add(new SelectorElement(R.layout.element_button, "", ""));
        element_map.put("CheckBox", new ArrayList<SelectorElement>());
        element_map.get("CheckBox").add(new SelectorElement(R.layout.element_checkbox, "", ""));
        element_map.put("Chronometer", new ArrayList<SelectorElement>());
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer, "", ""));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_left, "alignment", "left"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_right, "alignment", "right"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_center_horizontal, "alignment", "center_horizontal"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_top, "alignment", "top"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_bottom, "alignment", "bottom"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_center_vertical, "alignment", "center_vertical"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_start, "alignment", "start"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_end, "alignment", "end"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_top_left, "alignment", "top_left"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_top_right, "alignment", "top_right"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_top_center, "alignment", "top_center"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_top_start, "alignment", "top_start"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_top_end, "alignment", "top_end"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_bottom_left, "alignment", "bottom_left"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_bottom_right, "alignment", "bottom_right"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_bottom_center, "alignment", "bottom_center"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_bottom_start, "alignment", "bottom_start"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_bottom_end, "alignment", "bottom_end"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_center_left, "alignment", "center_left"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_center_right, "alignment", "center_right"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_center, "alignment", "center"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_center_start, "alignment", "center_start"));
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer_alignment_center_end, "alignment", "center_end"));
        element_map.put("ImageButton", new ArrayList<SelectorElement>());
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton, "", ""));
        element_map.put("ImageView", new ArrayList<SelectorElement>());
        element_map.get("ImageView").add(new SelectorElement(R.layout.element_imageview, "", ""));
        element_map.put("ProgressBar", new ArrayList<SelectorElement>());
        element_map.get("ProgressBar").add(new SelectorElement(R.layout.element_progressbar, "", ""));
        element_map.put("Switch", new ArrayList<SelectorElement>());
        element_map.get("Switch").add(new SelectorElement(R.layout.element_switch, "", ""));
        element_map.put("TextClock", new ArrayList<SelectorElement>());
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock, "", ""));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_left, "alignment", "left"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_right, "alignment", "right"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_center_horizontal, "alignment", "center_horizontal"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_top, "alignment", "top"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_bottom, "alignment", "bottom"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_center_vertical, "alignment", "center_vertical"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_start, "alignment", "start"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_end, "alignment", "end"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_top_left, "alignment", "top_left"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_top_right, "alignment", "top_right"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_top_center, "alignment", "top_center"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_top_start, "alignment", "top_start"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_top_end, "alignment", "top_end"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_bottom_left, "alignment", "bottom_left"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_bottom_right, "alignment", "bottom_right"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_bottom_center, "alignment", "bottom_center"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_bottom_start, "alignment", "bottom_start"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_bottom_end, "alignment", "bottom_end"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_center_left, "alignment", "center_left"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_center_right, "alignment", "center_right"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_center, "alignment", "center"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_center_start, "alignment", "center_start"));
        element_map.get("TextClock").add(new SelectorElement(R.layout.element_textclock_alignment_center_end, "alignment", "center_end"));
        element_map.put("TextView", new ArrayList<SelectorElement>());
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview, "", ""));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_left, "alignment", "left"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_right, "alignment", "right"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_center_horizontal, "alignment", "center_horizontal"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_top, "alignment", "top"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_bottom, "alignment", "bottom"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_center_vertical, "alignment", "center_vertical"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_start, "alignment", "start"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_end, "alignment", "end"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_top_left, "alignment", "top_left"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_top_right, "alignment", "top_right"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_top_center, "alignment", "top_center"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_top_start, "alignment", "top_start"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_top_end, "alignment", "top_end"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_bottom_left, "alignment", "bottom_left"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_bottom_right, "alignment", "bottom_right"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_bottom_center, "alignment", "bottom_center"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_bottom_start, "alignment", "bottom_start"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_bottom_end, "alignment", "bottom_end"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_center_left, "alignment", "center_left"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_center_right, "alignment", "center_right"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_center, "alignment", "center"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_center_start, "alignment", "center_start"));
        element_map.get("TextView").add(new SelectorElement(R.layout.element_textview_alignment_center_end, "alignment", "center_end"));
        element_map.put("RelativeLayout", new ArrayList<SelectorElement>());
        element_map.get("RelativeLayout").add(new SelectorElement(R.layout.element_relativelayout, "", ""));
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

    public static String getSetterMethod(String type, String method)
    {
        return typeToRemotableMethod.get(type).get(method);
    }

    public static HashMap<String, String> getRemotableMethods(String type)
    {
        Class<?> clazz = typeToClass.get(type);

        HashMap<String, String> methods = new HashMap<>();
        for (Method method : Reflection.getMethods(clazz))
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

                if (method.getParameterTypes().length != 1 || !parameterToSetter.containsKey(method.getParameterTypes()[0]))
                {
                    continue;
                }

                String name = method.getName();
                String setter = parameterToSetter.get(method.getParameterTypes()[0]);

                if (!methods.containsKey(name) || (preferredSetter.containsKey(name) && preferredSetter.get(name).equals(setter)))
                {
                    methods.put(name, setter);
                }
            }
        }
        return methods;
    }

    private static Boolean compiledWithManagerStorageResult = null;

    public static boolean compiledWithManagerStorage(Context context)
    {
        if (compiledWithManagerStorageResult != null)
        {
            return compiledWithManagerStorageResult;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
        {
            compiledWithManagerStorageResult = false;
            return false;
        }

        try
        {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] permissions = info.requestedPermissions;//This array contains the requested permissions.
            for (String permission : permissions)
            {
                if (permission.equals(Manifest.permission.MANAGE_EXTERNAL_STORAGE))
                {
                    compiledWithManagerStorageResult = true;
                    return true;
                }
            }
        }
        catch (PackageManager.NameNotFoundException e)
        {
            //Shouldn't ever reach here
        }

        compiledWithManagerStorageResult = false;
        return false;
    }
}
