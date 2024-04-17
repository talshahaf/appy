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
import android.util.Log;
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
import android.widget.RemoteViews;
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
    public static final String COLLECTION_ITEM_ID_EXTRA = "COLLECTION_ITEM_ID_EXTRA";
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            if(selector_pairs.length % 2 != 0)
            {
                throw new IllegalArgumentException("selector pairs argument must be even");
            }
            for(int i = 0; i < selector_pairs.length; i += 2)
            {
                selectors.put(selector_pairs[i], selector_pairs[i + 1]);
            }
        }

        int fit(HashMap<String, String> required)
        {
            for(String key : required.keySet())
            {
                if(!required.get(key).equals(selectors.get(key)))
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
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary, "style", "primary"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary, "style", "outline_primary"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_sml, "style", "primary_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_sml, "style", "outline_primary_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_lg, "style", "primary_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_lg, "style", "outline_primary_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_pad, "style", "primary_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_pad, "style", "outline_primary_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_nopad, "style", "primary_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_nopad, "style", "outline_primary_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_oval, "style", "primary_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_oval, "style", "outline_primary_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_oval_sml, "style", "primary_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_oval_sml, "style", "outline_primary_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_oval_lg, "style", "primary_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_oval_lg, "style", "outline_primary_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_oval_pad, "style", "primary_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_oval_pad, "style", "outline_primary_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_oval_nopad, "style", "primary_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_oval_nopad, "style", "outline_primary_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary, "style", "secondary"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary, "style", "outline_secondary"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_sml, "style", "secondary_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_sml, "style", "outline_secondary_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_lg, "style", "secondary_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_lg, "style", "outline_secondary_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_pad, "style", "secondary_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_pad, "style", "outline_secondary_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_nopad, "style", "secondary_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_nopad, "style", "outline_secondary_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_oval, "style", "secondary_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_oval, "style", "outline_secondary_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_oval_sml, "style", "secondary_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_oval_sml, "style", "outline_secondary_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_oval_lg, "style", "secondary_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_oval_lg, "style", "outline_secondary_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_oval_pad, "style", "secondary_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_oval_pad, "style", "outline_secondary_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_oval_nopad, "style", "secondary_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_oval_nopad, "style", "outline_secondary_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success, "style", "success"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success, "style", "outline_success"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_sml, "style", "success_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_sml, "style", "outline_success_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_lg, "style", "success_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_lg, "style", "outline_success_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_pad, "style", "success_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_pad, "style", "outline_success_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_nopad, "style", "success_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_nopad, "style", "outline_success_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_oval, "style", "success_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_oval, "style", "outline_success_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_oval_sml, "style", "success_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_oval_sml, "style", "outline_success_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_oval_lg, "style", "success_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_oval_lg, "style", "outline_success_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_oval_pad, "style", "success_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_oval_pad, "style", "outline_success_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_oval_nopad, "style", "success_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_oval_nopad, "style", "outline_success_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger, "style", "danger"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger, "style", "outline_danger"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_sml, "style", "danger_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_sml, "style", "outline_danger_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_lg, "style", "danger_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_lg, "style", "outline_danger_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_pad, "style", "danger_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_pad, "style", "outline_danger_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_nopad, "style", "danger_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_nopad, "style", "outline_danger_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_oval, "style", "danger_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_oval, "style", "outline_danger_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_oval_sml, "style", "danger_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_oval_sml, "style", "outline_danger_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_oval_lg, "style", "danger_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_oval_lg, "style", "outline_danger_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_oval_pad, "style", "danger_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_oval_pad, "style", "outline_danger_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_oval_nopad, "style", "danger_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_oval_nopad, "style", "outline_danger_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning, "style", "warning"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning, "style", "outline_warning"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_sml, "style", "warning_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_sml, "style", "outline_warning_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_lg, "style", "warning_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_lg, "style", "outline_warning_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_pad, "style", "warning_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_pad, "style", "outline_warning_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_nopad, "style", "warning_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_nopad, "style", "outline_warning_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_oval, "style", "warning_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_oval, "style", "outline_warning_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_oval_sml, "style", "warning_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_oval_sml, "style", "outline_warning_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_oval_lg, "style", "warning_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_oval_lg, "style", "outline_warning_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_oval_pad, "style", "warning_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_oval_pad, "style", "outline_warning_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_oval_nopad, "style", "warning_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_oval_nopad, "style", "outline_warning_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info, "style", "info"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info, "style", "outline_info"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_sml, "style", "info_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_sml, "style", "outline_info_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_lg, "style", "info_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_lg, "style", "outline_info_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_pad, "style", "info_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_pad, "style", "outline_info_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_nopad, "style", "info_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_nopad, "style", "outline_info_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_oval, "style", "info_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_oval, "style", "outline_info_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_oval_sml, "style", "info_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_oval_sml, "style", "outline_info_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_oval_lg, "style", "info_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_oval_lg, "style", "outline_info_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_oval_pad, "style", "info_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_oval_pad, "style", "outline_info_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_oval_nopad, "style", "info_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_oval_nopad, "style", "outline_info_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light, "style", "light"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light, "style", "outline_light"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_sml, "style", "light_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_sml, "style", "outline_light_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_lg, "style", "light_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_lg, "style", "outline_light_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_pad, "style", "light_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_pad, "style", "outline_light_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_nopad, "style", "light_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_nopad, "style", "outline_light_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_oval, "style", "light_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_oval, "style", "outline_light_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_oval_sml, "style", "light_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_oval_sml, "style", "outline_light_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_oval_lg, "style", "light_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_oval_lg, "style", "outline_light_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_oval_pad, "style", "light_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_oval_pad, "style", "outline_light_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_oval_nopad, "style", "light_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_oval_nopad, "style", "outline_light_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark, "style", "dark"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark, "style", "outline_dark"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_sml, "style", "dark_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_sml, "style", "outline_dark_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_lg, "style", "dark_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_lg, "style", "outline_dark_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_pad, "style", "dark_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_pad, "style", "outline_dark_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_nopad, "style", "dark_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_nopad, "style", "outline_dark_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_oval, "style", "dark_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_oval, "style", "outline_dark_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_oval_sml, "style", "dark_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_oval_sml, "style", "outline_dark_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_oval_lg, "style", "dark_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_oval_lg, "style", "outline_dark_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_oval_pad, "style", "dark_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_oval_pad, "style", "outline_dark_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_oval_nopad, "style", "dark_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_oval_nopad, "style", "outline_dark_oval_nopad"));
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
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary, "style", "primary"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary, "style", "outline_primary"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_sml, "style", "primary_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_sml, "style", "outline_primary_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_lg, "style", "primary_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_lg, "style", "outline_primary_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_pad, "style", "primary_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_pad, "style", "outline_primary_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_nopad, "style", "primary_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_nopad, "style", "outline_primary_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_oval, "style", "primary_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_oval, "style", "outline_primary_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_oval_sml, "style", "primary_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_oval_sml, "style", "outline_primary_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_oval_lg, "style", "primary_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_oval_lg, "style", "outline_primary_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_oval_pad, "style", "primary_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_oval_pad, "style", "outline_primary_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_oval_nopad, "style", "primary_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_oval_nopad, "style", "outline_primary_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary, "style", "secondary"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary, "style", "outline_secondary"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_sml, "style", "secondary_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_sml, "style", "outline_secondary_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_lg, "style", "secondary_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_lg, "style", "outline_secondary_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_pad, "style", "secondary_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_pad, "style", "outline_secondary_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_nopad, "style", "secondary_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_nopad, "style", "outline_secondary_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_oval, "style", "secondary_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_oval, "style", "outline_secondary_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_oval_sml, "style", "secondary_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_oval_sml, "style", "outline_secondary_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_oval_lg, "style", "secondary_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_oval_lg, "style", "outline_secondary_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_oval_pad, "style", "secondary_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_oval_pad, "style", "outline_secondary_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_oval_nopad, "style", "secondary_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_oval_nopad, "style", "outline_secondary_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success, "style", "success"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success, "style", "outline_success"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_sml, "style", "success_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_sml, "style", "outline_success_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_lg, "style", "success_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_lg, "style", "outline_success_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_pad, "style", "success_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_pad, "style", "outline_success_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_nopad, "style", "success_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_nopad, "style", "outline_success_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_oval, "style", "success_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_oval, "style", "outline_success_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_oval_sml, "style", "success_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_oval_sml, "style", "outline_success_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_oval_lg, "style", "success_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_oval_lg, "style", "outline_success_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_oval_pad, "style", "success_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_oval_pad, "style", "outline_success_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_oval_nopad, "style", "success_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_oval_nopad, "style", "outline_success_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger, "style", "danger"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger, "style", "outline_danger"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_sml, "style", "danger_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_sml, "style", "outline_danger_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_lg, "style", "danger_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_lg, "style", "outline_danger_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_pad, "style", "danger_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_pad, "style", "outline_danger_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_nopad, "style", "danger_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_nopad, "style", "outline_danger_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_oval, "style", "danger_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_oval, "style", "outline_danger_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_oval_sml, "style", "danger_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_oval_sml, "style", "outline_danger_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_oval_lg, "style", "danger_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_oval_lg, "style", "outline_danger_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_oval_pad, "style", "danger_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_oval_pad, "style", "outline_danger_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_oval_nopad, "style", "danger_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_oval_nopad, "style", "outline_danger_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning, "style", "warning"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning, "style", "outline_warning"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_sml, "style", "warning_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_sml, "style", "outline_warning_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_lg, "style", "warning_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_lg, "style", "outline_warning_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_pad, "style", "warning_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_pad, "style", "outline_warning_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_nopad, "style", "warning_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_nopad, "style", "outline_warning_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_oval, "style", "warning_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_oval, "style", "outline_warning_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_oval_sml, "style", "warning_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_oval_sml, "style", "outline_warning_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_oval_lg, "style", "warning_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_oval_lg, "style", "outline_warning_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_oval_pad, "style", "warning_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_oval_pad, "style", "outline_warning_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_oval_nopad, "style", "warning_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_oval_nopad, "style", "outline_warning_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info, "style", "info"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info, "style", "outline_info"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_sml, "style", "info_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_sml, "style", "outline_info_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_lg, "style", "info_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_lg, "style", "outline_info_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_pad, "style", "info_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_pad, "style", "outline_info_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_nopad, "style", "info_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_nopad, "style", "outline_info_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_oval, "style", "info_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_oval, "style", "outline_info_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_oval_sml, "style", "info_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_oval_sml, "style", "outline_info_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_oval_lg, "style", "info_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_oval_lg, "style", "outline_info_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_oval_pad, "style", "info_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_oval_pad, "style", "outline_info_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_oval_nopad, "style", "info_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_oval_nopad, "style", "outline_info_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light, "style", "light"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light, "style", "outline_light"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_sml, "style", "light_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_sml, "style", "outline_light_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_lg, "style", "light_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_lg, "style", "outline_light_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_pad, "style", "light_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_pad, "style", "outline_light_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_nopad, "style", "light_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_nopad, "style", "outline_light_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_oval, "style", "light_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_oval, "style", "outline_light_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_oval_sml, "style", "light_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_oval_sml, "style", "outline_light_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_oval_lg, "style", "light_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_oval_lg, "style", "outline_light_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_oval_pad, "style", "light_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_oval_pad, "style", "outline_light_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_oval_nopad, "style", "light_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_oval_nopad, "style", "outline_light_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark, "style", "dark"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark, "style", "outline_dark"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_sml, "style", "dark_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_sml, "style", "outline_dark_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_lg, "style", "dark_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_lg, "style", "outline_dark_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_pad, "style", "dark_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_pad, "style", "outline_dark_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_nopad, "style", "dark_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_nopad, "style", "outline_dark_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_oval, "style", "dark_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_oval, "style", "outline_dark_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_oval_sml, "style", "dark_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_oval_sml, "style", "outline_dark_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_oval_lg, "style", "dark_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_oval_lg, "style", "outline_dark_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_oval_pad, "style", "dark_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_oval_pad, "style", "outline_dark_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_oval_nopad, "style", "dark_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_oval_nopad, "style", "outline_dark_oval_nopad"));
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

        try {
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
        } catch (PackageManager.NameNotFoundException e) {
            //Shouldn't ever reach here
        }

        compiledWithManagerStorageResult = false;
        return false;
    }
}
