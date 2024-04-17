package com.appy;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AlertDialog;

import android.text.InputType;
import android.text.TextUtils;
import android.util.Pair;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.prefs.Preferences.MAX_VALUE_LENGTH;

public class StateFragment extends MyFragment
{
    public static final String FRAGMENT_TAG = "FRAGMENT";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View layout = inflater.inflate(R.layout.fragment_configs, container, false);

        WidgetSelectFragment fragment = new WidgetSelectFragment();
        fragment.setKeyPath();
        switchTo(fragment);
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
        if (getChildFragmentManager().findFragmentByTag(FRAGMENT_TAG) != null)
        {
            transaction.addToBackStack(null);
        }
        transaction.commitAllowingStateLoss();
    }

    public static class WidgetSelectFragment extends MyFragment implements AdapterView.OnItemClickListener
    {
        StateFragment parent;
        ListView list;
        ArrayList<String> keyPath;

        static class Item
        {
            String key;
            String value;
            boolean leaf;
            boolean inLocalScopeView;

            @Override
            public String toString()
            {
                return key;
            }

            public Item(String key, String value, boolean leaf, boolean inLocalScopeView)
            {
                this.key = key;
                this.value = value;
                this.leaf = leaf;
                this.inLocalScopeView = inLocalScopeView;
            }
        }

        public void refresh()
        {
            ArrayList<Item> adapterList = new ArrayList<>();
            if (keyPath.isEmpty())
            {
                for (String scope : StateLayout.listScopes())
                {
                    adapterList.add(new Item(scope, "", false, false));
                }
            }
            else
            {
                StateLayout stateLayout = getWidgetService().getStateLayout();

                int depth = StateLayout.getDepth(keyPath.get(0));
                boolean leaves = depth == keyPath.size();
                boolean inLocalScopeView = depth - 1 == keyPath.size() && depth == 3;

                for (String key : stateLayout.listDict(keyPath))
                {
                    adapterList.add(new Item(key, leaves ? stateLayout.getValue(keyPath, key) : "", leaves, inLocalScopeView));
                }
            }
            list.setAdapter(new ItemAdapter(getActivity(), adapterList));
        }

        public void setKeyPath()
        {
            keyPath = new ArrayList<>();
        }

        public void setKeyPath(List<String> prevKeyPath, String keyPathNext)
        {
            keyPath = new ArrayList<>(prevKeyPath);
            keyPath.add(keyPathNext);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState)
        {
            View layout = inflater.inflate(R.layout.fragment_configs_list, container, false);
            list = layout.findViewById(R.id.configs_list);
            list.setOnItemClickListener(this);
            list.setEmptyView(layout.findViewById(R.id.empty_view));

            registerForContextMenu(list);
            refresh();
            return layout;
        }

        @Override
        public void onItemClick(AdapterView<?> adapter, View view, int position, long id)
        {
            Item item = (Item) adapter.getItemAtPosition(position);
            if (!item.leaf)
            {
                //select that widget
                WidgetSelectFragment fragment = new WidgetSelectFragment();
                fragment.setKeyPath(keyPath, item.key);
                parent.switchTo(fragment);
            }
            else
            {
                //maybe pop viewer
                showViewer(item.key, item.value);
            }
        }

        public void showViewer(String title, String text)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            builder.setTitle(title);

            builder.setNeutralButton("OK", null);

            View layout = LayoutInflater.from(getActivity()).inflate(R.layout.alert_error_view_vertical, null);

            TextView message = layout.findViewById(R.id.message);
            message.setText(text + "\n\n");

            builder.setView(layout);

            AlertDialog alert = builder.create();
            alert.show();
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                                        ContextMenu.ContextMenuInfo menuInfo)
        {
            if (v == list)
            {
                boolean hasContextList = true;
                if (!keyPath.isEmpty())
                {
                    int depth = StateLayout.getDepth(keyPath.get(0));
                    if (depth != keyPath.size() && depth - 1 != keyPath.size())
                    {
                        //only two deepest levels has menu
                        hasContextList = false;
                    }
                }

                if (hasContextList)
                {
                    getActivity().getMenuInflater().inflate(R.menu.state_actions, menu);

                    AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
                    Item item = (Item) list.getItemAtPosition(info.position);
                    menu.setHeaderTitle(item.key);
                }
            }
            else
            {
                super.onCreateContextMenu(menu, v, menuInfo);
            }
        }

        @Override
        public boolean onContextItemSelected(MenuItem menuItem)
        {
            switch (menuItem.getItemId())
            {
                case R.id.action_delete:
                {
                    break;
                }
                default:
                {
                    return super.onContextItemSelected(menuItem);
                }
            }

            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();
            final Item item = (Item) list.getItemAtPosition(info.position);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int whichButton)
                        {
                            if (keyPath.isEmpty())
                            {
                                if (StateLayout.getDepth(item.key) != 1)
                                {
                                    Toast.makeText(getActivity(), "Too many inner levels to delete", Toast.LENGTH_SHORT).show();
                                }
                                else
                                {
                                    getWidgetService().cleanState(item.key, null, null);
                                }
                            }
                            else if (!item.leaf)
                            {
                                getWidgetService().cleanState(keyPath.get(0), item.key, null);
                            }
                            else
                            {
                                getWidgetService().cleanState(keyPath.get(0), keyPath.get(keyPath.size() - 1), item.key);
                            }
                            refresh();
                        }
                    })
                    .setNegativeButton(android.R.string.no, null);

            ArrayList<String> fullPath = new ArrayList<>(keyPath);
            fullPath.add(item.key);

            builder.setTitle(item.leaf ? "Delete state" : "Delete all");
            builder.setMessage((item.leaf ? "Delete " : "Delete all ") + String.join(".", fullPath) + " ?");

            builder.show();
            return true;
        }

        public void setParent(StateFragment parent)
        {
            this.parent = parent;
        }

        static class ItemAdapter extends BaseAdapter
        {
            public static int MAX_VALUE_LENGTH = 100;
            private Context context;
            private ArrayList<Item> items;

            public ItemAdapter(Context context, ArrayList<Item> items)
            {
                this.context = context;
                this.items = items;
            }

            @Override
            public int getCount()
            {
                return items.size();
            }

            @Override
            public Object getItem(int position)
            {
                return items.get(position);
            }

            @Override
            public long getItemId(int position)
            {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent)
            {

                View twoLineListItem;

                if (convertView == null)
                {
                    LayoutInflater inflater = (LayoutInflater) context
                            .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    twoLineListItem = inflater.inflate(R.layout.configs_list_item, null);
                }
                else
                {
                    twoLineListItem = convertView;
                }

                TextView text1 = twoLineListItem.findViewById(R.id.text1);
                TextView text2 = twoLineListItem.findViewById(R.id.text2);

                if (items.get(position).inLocalScopeView)
                {
                    text1.setText("widget #" + items.get(position).key);
                }
                else
                {
                    text1.setText(items.get(position).key);
                }

                String value = items.get(position).value;
                if (value.length() > MAX_VALUE_LENGTH)
                {
                    value = value.substring(0, MAX_VALUE_LENGTH - 3) + "...";
                }

                text2.setText(value);

                return twoLineListItem;
            }
        }
    }
}
