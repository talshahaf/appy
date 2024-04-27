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

public class StateFragment extends FragmentParent
{
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

        WidgetSelectFragment fragment = new WidgetSelectFragment();
        fragment.setKeyPath();
        switchTo(fragment, true);
    }

    @Override
    public void onBound()
    {
        tryStart();
    }

    @Override
    public void onShow()
    {
        tryStart();
    }

    public static class WidgetSelectFragment extends ChildFragment implements AdapterView.OnItemClickListener
    {
        ListView list;
        ArrayList<String> keyPath;

        public void refresh()
        {
            ArrayList<ListFragmentAdapter.Item> adapterList = new ArrayList<>();
            if (keyPath.isEmpty())
            {
                for (String scope : StateLayout.listScopes())
                {
                    adapterList.add(new ListFragmentAdapter.Item(scope, "", false, false));
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
                    adapterList.add(new ListFragmentAdapter.Item(key, leaves ? stateLayout.getValue(keyPath, key) : "", leaves, inLocalScopeView));
                }
            }
            list.setAdapter(new ListFragmentAdapter(getActivity(), adapterList));
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
            ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) adapter.getItemAtPosition(position);
            if (!item.leaf)
            {
                //select that widget
                WidgetSelectFragment fragment = new WidgetSelectFragment();
                fragment.setKeyPath(keyPath, item.key);
                parent.switchTo(fragment, false);
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
                    ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) list.getItemAtPosition(info.position);
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
            if (menuItem.getItemId() != R.id.action_delete)
            {
                return super.onContextItemSelected(menuItem);
            }

            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();
            final ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) list.getItemAtPosition(info.position);

            ArrayList<String> fullPath = new ArrayList<>(keyPath);
            fullPath.add(item.key);

            Utils.showConfirmationDialog(getActivity(),
                    item.leaf ? "Delete state" : "Delete all",
                    (item.leaf ? "Delete " : "Delete all ") + String.join(".", fullPath) + " ?",
                    android.R.drawable.ic_dialog_alert,
                    null, null, new Runnable()
                    {
                        @Override
                        public void run()
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
                    });
            return true;
        }
    }
}
