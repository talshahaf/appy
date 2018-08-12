package com.appy;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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
import android.widget.StackView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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
    public static final int SPECIAL_WIDGET_ID = 100;
    public static final int SPECIAL_WIDGET_RESTART = 1;
    public static final int SPECIAL_WIDGET_CLEAR = 2;
    public static final int SPECIAL_WIDGET_RELOAD = 3;
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
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_btn, "style", "primary_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_btn, "style", "outline_primary_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_btn_sml, "style", "primary_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_btn_sml, "style", "outline_primary_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_btn_lg, "style", "primary_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_btn_lg, "style", "outline_primary_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_btn_pad, "style", "primary_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_btn_pad, "style", "outline_primary_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_btn_nopad, "style", "primary_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_btn_nopad, "style", "outline_primary_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_btn_oval, "style", "primary_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_btn_oval, "style", "outline_primary_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_btn_oval_sml, "style", "primary_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_btn_oval_sml, "style", "outline_primary_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_btn_oval_lg, "style", "primary_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_btn_oval_lg, "style", "outline_primary_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_btn_oval_pad, "style", "primary_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_btn_oval_pad, "style", "outline_primary_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_primary_btn_oval_nopad, "style", "primary_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_primary_btn_oval_nopad, "style", "outline_primary_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_btn, "style", "secondary_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_btn, "style", "outline_secondary_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_btn_sml, "style", "secondary_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_btn_sml, "style", "outline_secondary_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_btn_lg, "style", "secondary_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_btn_lg, "style", "outline_secondary_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_btn_pad, "style", "secondary_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_btn_pad, "style", "outline_secondary_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_btn_nopad, "style", "secondary_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_btn_nopad, "style", "outline_secondary_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_btn_oval, "style", "secondary_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_btn_oval, "style", "outline_secondary_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_btn_oval_sml, "style", "secondary_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_btn_oval_sml, "style", "outline_secondary_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_btn_oval_lg, "style", "secondary_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_btn_oval_lg, "style", "outline_secondary_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_btn_oval_pad, "style", "secondary_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_btn_oval_pad, "style", "outline_secondary_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_secondary_btn_oval_nopad, "style", "secondary_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_secondary_btn_oval_nopad, "style", "outline_secondary_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_btn, "style", "success_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_btn, "style", "outline_success_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_btn_sml, "style", "success_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_btn_sml, "style", "outline_success_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_btn_lg, "style", "success_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_btn_lg, "style", "outline_success_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_btn_pad, "style", "success_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_btn_pad, "style", "outline_success_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_btn_nopad, "style", "success_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_btn_nopad, "style", "outline_success_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_btn_oval, "style", "success_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_btn_oval, "style", "outline_success_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_btn_oval_sml, "style", "success_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_btn_oval_sml, "style", "outline_success_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_btn_oval_lg, "style", "success_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_btn_oval_lg, "style", "outline_success_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_btn_oval_pad, "style", "success_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_btn_oval_pad, "style", "outline_success_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_success_btn_oval_nopad, "style", "success_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_success_btn_oval_nopad, "style", "outline_success_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_btn, "style", "danger_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_btn, "style", "outline_danger_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_btn_sml, "style", "danger_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_btn_sml, "style", "outline_danger_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_btn_lg, "style", "danger_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_btn_lg, "style", "outline_danger_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_btn_pad, "style", "danger_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_btn_pad, "style", "outline_danger_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_btn_nopad, "style", "danger_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_btn_nopad, "style", "outline_danger_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_btn_oval, "style", "danger_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_btn_oval, "style", "outline_danger_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_btn_oval_sml, "style", "danger_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_btn_oval_sml, "style", "outline_danger_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_btn_oval_lg, "style", "danger_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_btn_oval_lg, "style", "outline_danger_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_btn_oval_pad, "style", "danger_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_btn_oval_pad, "style", "outline_danger_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_danger_btn_oval_nopad, "style", "danger_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_danger_btn_oval_nopad, "style", "outline_danger_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_btn, "style", "warning_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_btn, "style", "outline_warning_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_btn_sml, "style", "warning_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_btn_sml, "style", "outline_warning_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_btn_lg, "style", "warning_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_btn_lg, "style", "outline_warning_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_btn_pad, "style", "warning_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_btn_pad, "style", "outline_warning_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_btn_nopad, "style", "warning_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_btn_nopad, "style", "outline_warning_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_btn_oval, "style", "warning_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_btn_oval, "style", "outline_warning_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_btn_oval_sml, "style", "warning_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_btn_oval_sml, "style", "outline_warning_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_btn_oval_lg, "style", "warning_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_btn_oval_lg, "style", "outline_warning_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_btn_oval_pad, "style", "warning_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_btn_oval_pad, "style", "outline_warning_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_warning_btn_oval_nopad, "style", "warning_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_warning_btn_oval_nopad, "style", "outline_warning_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_btn, "style", "info_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_btn, "style", "outline_info_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_btn_sml, "style", "info_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_btn_sml, "style", "outline_info_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_btn_lg, "style", "info_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_btn_lg, "style", "outline_info_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_btn_pad, "style", "info_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_btn_pad, "style", "outline_info_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_btn_nopad, "style", "info_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_btn_nopad, "style", "outline_info_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_btn_oval, "style", "info_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_btn_oval, "style", "outline_info_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_btn_oval_sml, "style", "info_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_btn_oval_sml, "style", "outline_info_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_btn_oval_lg, "style", "info_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_btn_oval_lg, "style", "outline_info_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_btn_oval_pad, "style", "info_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_btn_oval_pad, "style", "outline_info_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_info_btn_oval_nopad, "style", "info_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_info_btn_oval_nopad, "style", "outline_info_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_btn, "style", "light_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_btn, "style", "outline_light_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_btn_sml, "style", "light_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_btn_sml, "style", "outline_light_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_btn_lg, "style", "light_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_btn_lg, "style", "outline_light_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_btn_pad, "style", "light_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_btn_pad, "style", "outline_light_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_btn_nopad, "style", "light_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_btn_nopad, "style", "outline_light_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_btn_oval, "style", "light_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_btn_oval, "style", "outline_light_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_btn_oval_sml, "style", "light_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_btn_oval_sml, "style", "outline_light_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_btn_oval_lg, "style", "light_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_btn_oval_lg, "style", "outline_light_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_btn_oval_pad, "style", "light_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_btn_oval_pad, "style", "outline_light_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_light_btn_oval_nopad, "style", "light_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_light_btn_oval_nopad, "style", "outline_light_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_btn, "style", "dark_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_btn, "style", "outline_dark_btn"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_btn_sml, "style", "dark_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_btn_sml, "style", "outline_dark_btn_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_btn_lg, "style", "dark_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_btn_lg, "style", "outline_dark_btn_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_btn_pad, "style", "dark_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_btn_pad, "style", "outline_dark_btn_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_btn_nopad, "style", "dark_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_btn_nopad, "style", "outline_dark_btn_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_btn_oval, "style", "dark_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_btn_oval, "style", "outline_dark_btn_oval"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_btn_oval_sml, "style", "dark_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_btn_oval_sml, "style", "outline_dark_btn_oval_sml"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_btn_oval_lg, "style", "dark_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_btn_oval_lg, "style", "outline_dark_btn_oval_lg"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_btn_oval_pad, "style", "dark_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_btn_oval_pad, "style", "outline_dark_btn_oval_pad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_dark_btn_oval_nopad, "style", "dark_btn_oval_nopad"));
        element_map.get("Button").add(new SelectorElement(R.layout.element_button_style_outline_dark_btn_oval_nopad, "style", "outline_dark_btn_oval_nopad"));
        element_map.put("Chronometer", new ArrayList<SelectorElement>());
        element_map.get("Chronometer").add(new SelectorElement(R.layout.element_chronometer, "", ""));
        element_map.put("ImageButton", new ArrayList<SelectorElement>());
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton, "", ""));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_btn, "style", "primary_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_btn, "style", "outline_primary_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_btn_sml, "style", "primary_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_btn_sml, "style", "outline_primary_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_btn_lg, "style", "primary_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_btn_lg, "style", "outline_primary_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_btn_pad, "style", "primary_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_btn_pad, "style", "outline_primary_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_btn_nopad, "style", "primary_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_btn_nopad, "style", "outline_primary_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_btn_oval, "style", "primary_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_btn_oval, "style", "outline_primary_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_btn_oval_sml, "style", "primary_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_btn_oval_sml, "style", "outline_primary_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_btn_oval_lg, "style", "primary_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_btn_oval_lg, "style", "outline_primary_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_btn_oval_pad, "style", "primary_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_btn_oval_pad, "style", "outline_primary_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_primary_btn_oval_nopad, "style", "primary_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_primary_btn_oval_nopad, "style", "outline_primary_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_btn, "style", "secondary_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_btn, "style", "outline_secondary_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_btn_sml, "style", "secondary_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_btn_sml, "style", "outline_secondary_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_btn_lg, "style", "secondary_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_btn_lg, "style", "outline_secondary_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_btn_pad, "style", "secondary_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_btn_pad, "style", "outline_secondary_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_btn_nopad, "style", "secondary_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_btn_nopad, "style", "outline_secondary_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_btn_oval, "style", "secondary_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_btn_oval, "style", "outline_secondary_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_btn_oval_sml, "style", "secondary_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_btn_oval_sml, "style", "outline_secondary_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_btn_oval_lg, "style", "secondary_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_btn_oval_lg, "style", "outline_secondary_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_btn_oval_pad, "style", "secondary_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_btn_oval_pad, "style", "outline_secondary_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_secondary_btn_oval_nopad, "style", "secondary_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_secondary_btn_oval_nopad, "style", "outline_secondary_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_btn, "style", "success_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_btn, "style", "outline_success_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_btn_sml, "style", "success_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_btn_sml, "style", "outline_success_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_btn_lg, "style", "success_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_btn_lg, "style", "outline_success_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_btn_pad, "style", "success_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_btn_pad, "style", "outline_success_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_btn_nopad, "style", "success_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_btn_nopad, "style", "outline_success_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_btn_oval, "style", "success_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_btn_oval, "style", "outline_success_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_btn_oval_sml, "style", "success_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_btn_oval_sml, "style", "outline_success_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_btn_oval_lg, "style", "success_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_btn_oval_lg, "style", "outline_success_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_btn_oval_pad, "style", "success_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_btn_oval_pad, "style", "outline_success_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_success_btn_oval_nopad, "style", "success_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_success_btn_oval_nopad, "style", "outline_success_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_btn, "style", "danger_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_btn, "style", "outline_danger_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_btn_sml, "style", "danger_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_btn_sml, "style", "outline_danger_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_btn_lg, "style", "danger_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_btn_lg, "style", "outline_danger_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_btn_pad, "style", "danger_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_btn_pad, "style", "outline_danger_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_btn_nopad, "style", "danger_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_btn_nopad, "style", "outline_danger_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_btn_oval, "style", "danger_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_btn_oval, "style", "outline_danger_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_btn_oval_sml, "style", "danger_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_btn_oval_sml, "style", "outline_danger_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_btn_oval_lg, "style", "danger_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_btn_oval_lg, "style", "outline_danger_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_btn_oval_pad, "style", "danger_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_btn_oval_pad, "style", "outline_danger_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_danger_btn_oval_nopad, "style", "danger_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_danger_btn_oval_nopad, "style", "outline_danger_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_btn, "style", "warning_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_btn, "style", "outline_warning_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_btn_sml, "style", "warning_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_btn_sml, "style", "outline_warning_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_btn_lg, "style", "warning_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_btn_lg, "style", "outline_warning_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_btn_pad, "style", "warning_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_btn_pad, "style", "outline_warning_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_btn_nopad, "style", "warning_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_btn_nopad, "style", "outline_warning_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_btn_oval, "style", "warning_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_btn_oval, "style", "outline_warning_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_btn_oval_sml, "style", "warning_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_btn_oval_sml, "style", "outline_warning_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_btn_oval_lg, "style", "warning_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_btn_oval_lg, "style", "outline_warning_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_btn_oval_pad, "style", "warning_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_btn_oval_pad, "style", "outline_warning_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_warning_btn_oval_nopad, "style", "warning_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_warning_btn_oval_nopad, "style", "outline_warning_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_btn, "style", "info_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_btn, "style", "outline_info_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_btn_sml, "style", "info_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_btn_sml, "style", "outline_info_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_btn_lg, "style", "info_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_btn_lg, "style", "outline_info_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_btn_pad, "style", "info_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_btn_pad, "style", "outline_info_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_btn_nopad, "style", "info_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_btn_nopad, "style", "outline_info_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_btn_oval, "style", "info_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_btn_oval, "style", "outline_info_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_btn_oval_sml, "style", "info_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_btn_oval_sml, "style", "outline_info_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_btn_oval_lg, "style", "info_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_btn_oval_lg, "style", "outline_info_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_btn_oval_pad, "style", "info_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_btn_oval_pad, "style", "outline_info_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_info_btn_oval_nopad, "style", "info_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_info_btn_oval_nopad, "style", "outline_info_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_btn, "style", "light_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_btn, "style", "outline_light_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_btn_sml, "style", "light_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_btn_sml, "style", "outline_light_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_btn_lg, "style", "light_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_btn_lg, "style", "outline_light_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_btn_pad, "style", "light_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_btn_pad, "style", "outline_light_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_btn_nopad, "style", "light_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_btn_nopad, "style", "outline_light_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_btn_oval, "style", "light_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_btn_oval, "style", "outline_light_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_btn_oval_sml, "style", "light_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_btn_oval_sml, "style", "outline_light_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_btn_oval_lg, "style", "light_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_btn_oval_lg, "style", "outline_light_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_btn_oval_pad, "style", "light_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_btn_oval_pad, "style", "outline_light_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_light_btn_oval_nopad, "style", "light_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_light_btn_oval_nopad, "style", "outline_light_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_btn, "style", "dark_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_btn, "style", "outline_dark_btn"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_btn_sml, "style", "dark_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_btn_sml, "style", "outline_dark_btn_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_btn_lg, "style", "dark_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_btn_lg, "style", "outline_dark_btn_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_btn_pad, "style", "dark_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_btn_pad, "style", "outline_dark_btn_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_btn_nopad, "style", "dark_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_btn_nopad, "style", "outline_dark_btn_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_btn_oval, "style", "dark_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_btn_oval, "style", "outline_dark_btn_oval"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_btn_oval_sml, "style", "dark_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_btn_oval_sml, "style", "outline_dark_btn_oval_sml"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_btn_oval_lg, "style", "dark_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_btn_oval_lg, "style", "outline_dark_btn_oval_lg"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_btn_oval_pad, "style", "dark_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_btn_oval_pad, "style", "outline_dark_btn_oval_pad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_dark_btn_oval_nopad, "style", "dark_btn_oval_nopad"));
        element_map.get("ImageButton").add(new SelectorElement(R.layout.element_imagebutton_style_outline_dark_btn_oval_nopad, "style", "outline_dark_btn_oval_nopad"));
        element_map.put("ImageView", new ArrayList<SelectorElement>());
        element_map.get("ImageView").add(new SelectorElement(R.layout.element_imageview, "", ""));
        element_map.put("ProgressBar", new ArrayList<SelectorElement>());
        element_map.get("ProgressBar").add(new SelectorElement(R.layout.element_progressbar, "", ""));
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
                if (method.getParameterTypes().length != 1 || !parameterToSetter.containsKey(method.getParameterTypes()[0]))
                {
                    continue;
                }
                methods.put(method.getName(), parameterToSetter.get(method.getParameterTypes()[0]));
            }
        }
        return methods;
    }
}
