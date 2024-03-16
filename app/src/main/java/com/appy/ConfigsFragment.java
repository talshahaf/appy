package com.appy;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AlertDialog;
import android.text.InputType;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ConfigsFragment extends MyFragment
{
    public static final String FRAGMENT_TAG = "FRAGMENT";

    public Bundle fragmentArg = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View layout = inflater.inflate(R.layout.fragment_configs, container, false);
        onShow();
        return layout;
    }

    public void tryStart()
    {
        if (getActivity() == null)
        {
            return;
        }
        if (getWidgetService() == null)
        {
            return;
        }
        if (fragmentArg == null)
        {
            switchTo(new WidgetSelectFragment(), false);
            return;
        }

        String widget = fragmentArg.getString(Constants.FRAGMENT_ARG_WIDGET);
        HashMap<String, String> configs = getWidgetService().getConfigurations().getValues(widget);
        if (configs.isEmpty())
        {
            switchTo(new WidgetSelectFragment(), true);
            return;
        }

        WidgetSelectFragment fragment = new WidgetSelectFragment();
        fragment.setWidget(widget);
        String config = fragmentArg.getString(Constants.FRAGMENT_ARG_CONFIG);
        if (configs.containsKey(config)) {
            fragment.setConfig(config);
            fragment.setRequestCode(fragmentArg.getInt(Constants.FRAGMENT_ARG_REQUESTCODE, 0));
        }
        switchTo(fragment, true);
    }

    public void onBound()
    {
        tryStart();
    }
    public void onShow()
    {
        tryStart();
    }

    public void onHide()
    {
        setArgument(null);
    }

    @Override
    public void setArgument(Bundle fragmentArg)
    {
        this.fragmentArg = fragmentArg;
    }

    public void switchTo(WidgetSelectFragment fragment, boolean noBackStack)
    {
        fragment.setParent(this);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.setCustomAnimations(
                R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                R.animator.slide_in_from_left, R.animator.slide_out_to_right);
        transaction.replace(R.id.configs_container, fragment, FRAGMENT_TAG);
        if(getChildFragmentManager().findFragmentByTag(FRAGMENT_TAG) != null && noBackStack)
        {
            transaction.addToBackStack(null);
        }
        transaction.commitAllowingStateLoss();
    }

    public void finishActivity()
    {
        if (getActivity() != null)
        {
            getActivity().finish();
        }
    }

    public static class WidgetSelectFragment extends MyFragment implements AdapterView.OnItemClickListener
    {
        ConfigsFragment parent;
        ListView list;
        String widget = null;
        String config = null;
        int requestCode = 0;

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
            Item selectedConfigItem = null;
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
                    Item listitem = new Item(item.getKey(), item.getValue());
                    if (config != null && item.getKey().equals(config))
                    {
                        selectedConfigItem = listitem;
                    }
                    adapterList.add(listitem);
                }
            }
            list.setAdapter(new ItemAdapter(getActivity(), adapterList));
            if (selectedConfigItem != null)
            {
                showEditor(selectedConfigItem, true);
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState)
        {
            View layout = inflater.inflate(R.layout.fragment_configs_list, container, false);
            list = layout.findViewById(R.id.configs_list);
            list.setOnItemClickListener(this);
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
                parent.switchTo(fragment, false);
            }
            else
            {
                //pop value editor
                showEditor(item, false);
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

        public void showEditor(final Item item, final boolean dieAfter)
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

            final DialogInterface.OnClickListener onDismiss = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (dieAfter)
                    {
                        if (requestCode != 0)
                        {
                            getWidgetService().asyncReport(requestCode, item.value);
                        }
                        parent.finishActivity();
                    }
                    dialog.dismiss();
                }
            };
            builder.setNegativeButton("Cancel", onDismiss);
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    onDismiss.onClick(dialog, 0);
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
                                getWidgetService().configurationUpdate(widget, item.key);
                                if (dieAfter)
                                {
                                    if (requestCode != 0)
                                    {
                                        getWidgetService().asyncReport(requestCode, newValue);
                                    }
                                    parent.finishActivity();
                                }
                                else
                                {
                                    refresh();
                                }
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
        public void onCreateContextMenu(ContextMenu menu, View v,
                                        ContextMenu.ContextMenuInfo menuInfo)
        {
            if(v == list)
            {
                getActivity().getMenuInflater().inflate(R.menu.config_actions, menu);

                AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
                Item item = (Item)list.getItemAtPosition(info.position);
                menu.setHeaderTitle(item.key);
            }
            else
            {
                super.onCreateContextMenu(menu, v, menuInfo);
            }
        }

        @Override
        public boolean onContextItemSelected(MenuItem menuItem)
        {
            boolean delete_;
            switch (menuItem.getItemId())
            {
                case R.id.action_reset:
                {
                    delete_ = false;
                    break;
                }
                case R.id.action_delete:
                {
                    delete_ = true;
                    break;
                }
                default:
                {
                    return super.onContextItemSelected(menuItem);
                }
            }

            final boolean delete = delete_;

            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();
            final Item item = (Item)list.getItemAtPosition(info.position);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                            {
                                public void onClick(DialogInterface dialog, int whichButton)
                                {
                                    if(widget == null)
                                    {
                                        if(delete)
                                        {
                                            getWidgetService().getConfigurations().deleteWidget(item.key);
                                        }
                                        else
                                        {
                                            getWidgetService().getConfigurations().resetWidget(item.key);
                                        }
                                        getWidgetService().configurationUpdate(item.key, null);
                                    }
                                    else
                                    {
                                        if(delete)
                                        {
                                            getWidgetService().getConfigurations().deleteKey(widget, item.key);
                                        }
                                        else
                                        {
                                            getWidgetService().getConfigurations().resetKey(widget, item.key);
                                        }
                                        getWidgetService().configurationUpdate(widget, item.key);
                                    }

                                    refresh();
                                }
                            }
                    )
                    .setNegativeButton(android.R.string.no, null);

            if(widget == null)
            {
                if (delete)
                {
                    builder.setTitle("Delete all");
                    builder.setMessage("Delete all " + item.key + " configurations?");
                }
                else
                {
                    builder.setTitle("Reset all");
                    builder.setMessage("Reset all " + item.key + " configurations?");
                }
            }
            else
            {
                if(delete)
                {
                    builder.setTitle("Delete configuration");
                    builder.setMessage("Delete " + item.key + "?");
                }
                else
                {
                    builder.setTitle("Reset configuration");
                    builder.setMessage("Reset " + item.key + "?");
                }
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
        public void setConfig(String config)
        {
            this.config = config;
        }
        public void setRequestCode(int requestCode)
        {
            this.requestCode = requestCode;
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
