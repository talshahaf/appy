package com.appy;

import android.content.Context;
import android.content.DialogInterface;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.JsonReader;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TwoLineListItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConfigsFragment extends MyFragment
{
    public static final String FRAGMENT_TAG = "FRAGMENT";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View layout = inflater.inflate(R.layout.fragment_configs, container, false);
        switchTo(new WidgetSelectFragment());
        return layout;
    }

    public void switchTo(WidgetSelectFragment fragment)
    {
        fragment.setParent(this);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.setCustomAnimations(
                R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                R.animator.slide_in_from_left, R.animator.slide_out_to_right);
        transaction.replace(R.id.configs_container, fragment, FRAGMENT_TAG);
        if(getChildFragmentManager().findFragmentByTag(FRAGMENT_TAG) != null)
        {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    public static class WidgetSelectFragment extends MyFragment implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener
    {
        ConfigsFragment parent;
        ListView list;
        String widget = null;

        static class Item
        {
            String key;
            String value;

            @Override
            public String toString()
            {
                return key;
            }

            public Item(String key, String value)
            {
                this.key = key;
                this.value = value;
            }
        }

        public void refresh()
        {
            ArrayList<Item> adapterList = new ArrayList<>();
            if(widget == null)
            {
                HashMap<String, Integer> widgets = getWidgetService().getConfigurations().listWidgets();
                for(Map.Entry<String, Integer> item : widgets.entrySet())
                {
                    adapterList.add(new Item(item.getKey(), item.getValue()+" configurations"));
                }
            }
            else
            {
                HashMap<String, String> values = getWidgetService().getConfigurations().getValues(widget);
                for(Map.Entry<String, String> item : values.entrySet())
                {
                    adapterList.add(new Item(item.getKey(), item.getValue()));
                }
            }
            list.setAdapter(new ItemAdapter(getActivity(), adapterList));
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState)
        {
            View layout = inflater.inflate(R.layout.fragment_configs_list, container, false);
            list = layout.findViewById(R.id.configs_list);
            list.setOnItemClickListener(this);
            list.setOnItemLongClickListener(this);
            registerForContextMenu(list);
            refresh();
            return layout;
        }

        @Override
        public void onItemClick(AdapterView<?> adapter, View view, int position, long id)
        {
            Item item = (Item)adapter.getItemAtPosition(position);
            if(widget == null)
            {
                //select that widget
                WidgetSelectFragment fragment = new WidgetSelectFragment();
                fragment.setWidget(item.key);
                parent.switchTo(fragment);
            }
            else
            {
                //pop value editor
                showEditor(item);
            }
        }

        public static boolean isValidJSON(String value)
        {
            try
            {
                // turns everything to an array (array of array is ok)
                new JSONArray("["+value+"]");
            }
            catch (JSONException e)
            {
                return false;
            }
            return true;
        }

        public void showEditor(final Item item)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(widget + "." + item.key);

            final EditText input = new EditText(getActivity());

            try
            {
                input.setText(new JSONObject(item.value).toString(2));
            }
            catch(JSONException e)
            {
                input.setText(item.value);
            }
            input.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setSingleLine(false);
            input.setGravity(Gravity.START | Gravity.TOP);
            builder.setView(input);

            builder.setPositiveButton("Ok", null);

            builder.setNegativeButton("Cancel",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

            final AlertDialog alert = builder.create();

            alert.setOnShowListener(new DialogInterface.OnShowListener() {

                @Override
                public void onShow(DialogInterface dialogInterface) {

                    Button button = alert.getButton(AlertDialog.BUTTON_POSITIVE);
                    button.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View view) {
                            String newValue = input.getText().toString();
                            if(isValidJSON(newValue))
                            {
                                getWidgetService().getConfigurations().setConfig(widget, item.key, newValue);
                                refresh();
                                alert.dismiss();
                            }
                            else
                            {
                                // dont dismiss
                                input.setBackgroundColor(0x50FF0000);
                            }
                        }
                    });
                }
            });


            alert.show();
        }

        @Override
        public boolean onItemLongClick(AdapterView<?> adapter, View view, int position, long id)
        {
            final Item item = (Item)adapter.getItemAtPosition(position);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int whichButton)
                        {
                            if(widget == null)
                            {
                                getWidgetService().getConfigurations().resetWidget(item.key);
                            }
                            else
                            {
                                getWidgetService().getConfigurations().resetKey(widget, item.key);
                            }
                            refresh();
                        }
                    }
                        )
                    .setNegativeButton(android.R.string.no, null);

            if(widget == null)
            {
                builder.setTitle("Reset all");
                builder.setMessage("Reset all " + item.key + " configurations?");
            }
            else
            {
                builder.setTitle("Reset configuration");
                builder.setMessage("Reset " + item.key + "?");
            }
            builder.show();
            return true;
        }

        public void setParent(ConfigsFragment parent)
        {
            this.parent = parent;
        }

        public void setWidget(String widget)
        {
            this.widget = widget;
        }

        static class ItemAdapter extends BaseAdapter
        {
            private Context context;
            private ArrayList<Item> items;

            public ItemAdapter(Context context, ArrayList<Item> items) {
                this.context = context;
                this.items = items;
            }

            @Override
            public int getCount() {
                return items.size();
            }

            @Override
            public Object getItem(int position) {
                return items.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {

                View twoLineListItem;

                if (convertView == null) {
                    LayoutInflater inflater = (LayoutInflater) context
                            .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    twoLineListItem = inflater.inflate(R.layout.configs_list_item, null);
                } else {
                    twoLineListItem = convertView;
                }

                TextView text1 = twoLineListItem.findViewById(R.id.text1);
                TextView text2 = twoLineListItem.findViewById(R.id.text2);

                text1.setText(items.get(position).key);
                text2.setText(items.get(position).value);

                return twoLineListItem;
            }
        }
    }
}
