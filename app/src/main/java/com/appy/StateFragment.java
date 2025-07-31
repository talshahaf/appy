package com.appy;

import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;

import android.util.Pair;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class StateFragment extends FragmentParent
{
    DictObj.Dict stateSnapshot = null;
    DictObj.Dict widgetProps = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        setHasOptionsMenu(true);
        return inflater.inflate(R.layout.fragment_configs, container, false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.clearall_toolbar_action, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.action_clearall)
        {
            Utils.showConfirmationDialog(getActivity(),
                "Clear all state", "Clear state for all widgets?", android.R.drawable.ic_dialog_alert,
                null, null, new Runnable()
                {
                    @Override
                    public void run()
                    {
                        getWidgetService().resetState();
                    }
                });
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAttachedAndBound()
    {
        start();
    }

    public void start()
    {
        if (getActivity() == null)
        {
            return;
        }
        if (getWidgetService() == null)
        {
            return;
        }

        updateDataSource();

        WidgetSelectFragment fragment = new WidgetSelectFragment();
        fragment.setParent(this);
        fragment.setKeyPath();
        switchTo(fragment, true);
    }

    @Override
    public Object getDataSource()
    {
        return new Pair<>(stateSnapshot, widgetProps);
    }

    public void updateDataSource()
    {
        stateSnapshot = getWidgetService().getStateLayoutSnapshot();
        widgetProps = getWidgetService().getAllWidgetAppProps(false, false);
    }

    public static class WidgetSelectFragment extends ChildFragment implements AdapterView.OnItemClickListener
    {
        ListView list;
        ArrayList<String> keyPath;

        public Pair<DictObj.Dict, DictObj.Dict> traverse()
        {
            Pair<DictObj.Dict, DictObj.Dict> current_props = (Pair<DictObj.Dict, DictObj.Dict>) parent.getDataSource();
            DictObj.Dict current = current_props.first;
            for (String key : keyPath)
            {
                if (current == null)
                {
                    return null;
                }
                current = current.getDict(key);
            }
            return new Pair<>(current, current_props.second);
        }

        public static String numEntries(DictObj.Dict d, String key)
        {
            String entries = "?";
            Object val = d.get(key);
            if (val instanceof DictObj.Dict)
            {
                entries = ((DictObj.Dict) val).size() + "";
            }
            return entries + " entries";
        }

        public void refresh()
        {
            parent.updateDataSource();

            ArrayList<ListFragmentAdapter.Item> adapterList = new ArrayList<>();
            if (keyPath.isEmpty())
            {
                if (getActivity() != null)
                {
                    getActivity().setTitle("State");
                }

                for (String scope : scopes)
                {
                    adapterList.add(new ListFragmentAdapter.Item(scope, numEntries(((Pair<DictObj.Dict, DictObj.Dict>)parent.getDataSource()).first, scope), false));
                }
            }
            else
            {
                if (getActivity() != null)
                {
                    getActivity().setTitle("State: " + String.join(".", keyPath));
                }

                int depth = getScopeDepth(keyPath.get(0));
                boolean areLeaves = depth == keyPath.size();
                boolean containStates = depth - 1 == keyPath.size();
                boolean inLocalScopeView = containStates && depth == 3;

                Pair<DictObj.Dict, DictObj.Dict> current_props = traverse();
                if (current_props.first != null)
                {
                    for (String key : current_props.first.keys())
                    {
                        String entries = numEntries(current_props.first, key);

                        if (areLeaves)
                        {
                            adapterList.add(new ListFragmentAdapter.Item(key, current_props.first.get(key).toString(), item -> item.key, true));
                        }
                        else if (inLocalScopeView)
                        {
                            DictObj.Dict props = current_props.second.getDict(key);
                            final boolean isApp = props != null && props.getBoolean("app", false);

                            adapterList.add(new ListFragmentAdapter.Item(key, entries, item -> (isApp ? "app #" : "widget #") + item.key, false));
                        }
                        else
                        {
                            adapterList.add(new ListFragmentAdapter.Item(key, entries, item -> item.key, false));
                        }
                    }
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
        public void onResumedAndBound()
        {
            refresh();
        }

        @Override
        public void onItemClick(AdapterView<?> adapter, View view, int position, long id)
        {
            ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) adapter.getItemAtPosition(position);
            if (!(Boolean)item.arg)
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
                    int depth = getScopeDepth(keyPath.get(0));
                    if (depth != keyPath.size() && depth - 1 != keyPath.size())
                    {
                        //only two deepest levels has menu
                        hasContextList = false;
                    }
                }

                if (hasContextList)
                {
                    getActivity().getMenuInflater().inflate(R.menu.delete_action, menu);

                    AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
                    ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) list.getItemAtPosition(info.position);
                    menu.setHeaderTitle(item.keyFormat.format(item));
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

            boolean leaf = (Boolean)item.arg;

            Utils.showConfirmationDialog(getActivity(),
                    leaf ? "Delete state" : "Delete all",
                    (leaf ? "Delete " : "Delete all ") + String.join(".", fullPath) + " ?",
                    android.R.drawable.ic_dialog_alert,
                    null, null, new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            if (keyPath.isEmpty())
                            {
                                if (getScopeDepth(item.key) != 1)
                                {
                                    Toast.makeText(getActivity(), "Too many inner levels to delete", Toast.LENGTH_SHORT).show();
                                }
                                else
                                {
                                    getWidgetService().cleanState(item.key, null, null);
                                }
                            }
                            else if (!leaf)
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

        public static final String[] scopes = new String[] {"globals", "nonlocals", "locals"};

        public static int getScopeDepth(String scope)
        {
            if (scope.equals("globals"))
            {
                return 1;
            }

            if (scope.equals("nonlocals"))
            {
                return 2;
            }

            if (scope.equals("locals"))
            {
                return 3;
            }

            throw new IllegalArgumentException("unknown scope");
        }
    }
}
