package com.appy;

import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;

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

import java.util.ArrayList;
import java.util.List;

public class StateFragment extends FragmentParent
{
    String clearAllTitle = "";
    String clearAllMessage = "";
    Runnable clearAllAction = null;

    public void setClearAll(String title, String message, Runnable action)
    {
        clearAllTitle = title;
        clearAllMessage = message;
        clearAllAction = action;
    }

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
            if (clearAllAction != null)
            {
                Utils.showConfirmationDialog(getActivity(),
                        clearAllTitle, clearAllMessage, android.R.drawable.ic_dialog_alert,
                        null, null, clearAllAction);
            }
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

        WidgetSelectFragment fragment = new WidgetSelectFragment();
        fragment.setParent(this);
        fragment.setKeyPath();
        switchTo(fragment, true);
    }

    public static class WidgetSelectFragment extends ChildFragment implements AdapterView.OnItemClickListener
    {
        ListView list;
        ArrayList<String> keyPath;

        public DictObj.Dict traverse(DictObj.Dict stateSnapshot)
        {
            DictObj.Dict current = stateSnapshot;
            for (String key : keyPath)
            {
                if (current == null)
                {
                    return null;
                }
                current = current.getDict(key);
            }
            return current;
        }

        public void refresh()
        {
            DictObj.Dict widgetProps = getWidgetService().getAllWidgetAppProps(false, false);

            ArrayList<ListFragmentAdapter.Item> adapterList = new ArrayList<>();
            if (keyPath.isEmpty())
            {
                DictObj.Dict stateSnapshot = getWidgetService().getStateLayoutSnapshot(null, null);
                if (getActivity() != null)
                {
                    getActivity().setTitle("State");
                }

                ((StateFragment)parent).setClearAll("Clear all state",
                        "Clear state for all widgets?",
                        () -> {
                            getWidgetService().resetState();
                            refresh();
                        });

                for (String scope : scopes)
                {
                    boolean containStates = getScopeDepth(scope) == 1;
                    adapterList.add(new ListFragmentAdapter.Item(scope, Utils.enumerableFormat(stateSnapshot.getInt(scope, 0), containStates ? "state" : "widget", containStates ? "states" : "widgets"), null));
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

                ((StateFragment)parent).setClearAll("Clear states",
                        "Clear all states in " + String.join(".", keyPath) + "?",
                        () -> {
                            int widgetIndex = depth == 3 ? 2 : 1;
                            if (depth == 3 && keyPath.size() == 2)
                            {
                                //special handling if in locals/widget/
                                getWidgetService().cleanLocalStateByName(keyPath.get(1));
                            }
                            else
                            {
                                getWidgetService().cleanState(keyPath.get(0), keyPath.size() > widgetIndex ? keyPath.get(widgetIndex) : null, null);
                            }
                            refresh();
                        });

                DictObj.Dict stateSnapshot = getWidgetService().getStateLayoutSnapshot(keyPath.get(0), areLeaves ? keyPath.get(keyPath.size() - 1) : null);
                for (String key : stateSnapshot.keys())
                {
                    if (areLeaves)
                    {
                        adapterList.add(new ListFragmentAdapter.Item(key, Utils.capWithEllipsis(stateSnapshot.get(key).toString(), 100), item -> item.key, stateSnapshot.get(key).toString()));
                    }
                    else if (inLocalScopeView)
                    {
                        if (key.equals(keyPath.get(1)))
                        {
                            DictObj.Dict nameState = stateSnapshot.getDict(key);
                            for (String widgetId : nameState.keys())
                            {
                                DictObj.Dict props = widgetProps.getDict(widgetId);
                                final boolean isApp = props != null && props.getBoolean("app", false);

                                adapterList.add(new ListFragmentAdapter.Item(widgetId, Utils.enumerableFormat(nameState.getInt(widgetId, 0), "state", "states"), item -> (isApp ? "app #" : "widget #") + item.key, null));
                            }
                        }
                    }
                    else
                    {
                        String numEntries;
                        if (depth == 3)
                        {
                            numEntries = Utils.enumerableFormat(stateSnapshot.getDict(key).size(), "instance", "instances");
                        }
                        else
                        {
                            numEntries = Utils.enumerableFormat(stateSnapshot.getInt(key, 0), containStates ? "state" : "widget", containStates ? "states" : "widgets");
                        }
                        adapterList.add(new ListFragmentAdapter.Item(key, numEntries, item -> item.key, null));
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
        public void onStartedAndBound()
        {
            refresh();
        }

        @Override
        public void onItemClick(AdapterView<?> adapter, View view, int position, long id)
        {
            ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) adapter.getItemAtPosition(position);
            String value = (String)item.arg;
            if (value == null)
            {
                WidgetSelectFragment fragment = new WidgetSelectFragment();
                fragment.setKeyPath(keyPath, item.key);
                parent.switchTo(fragment, false);
            }
            else
            {
                showViewer(item.key, value);
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
                getActivity().getMenuInflater().inflate(R.menu.delete_action, menu);

                AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
                ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) list.getItemAtPosition(info.position);
                menu.setHeaderTitle(item.keyFormat.format(item));
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

            boolean leaf = item.arg != null;

            Utils.showConfirmationDialog(getActivity(),
                    leaf ? "Delete state" : "Delete all",
                    (leaf ? "Delete " : "Delete all ") + String.join(".", fullPath) + " ?",
                    android.R.drawable.ic_dialog_alert,
                    null, null, () -> {
                        if (keyPath.isEmpty())
                        {
                            getWidgetService().cleanState(item.key, null, null);
                        }
                        else if (!leaf)
                        {
                            if (getScopeDepth(keyPath.get(0)) == 3 && keyPath.size() == 1)
                            {
                                //clean locals by widget name
                                getWidgetService().cleanLocalStateByName(item.key);
                            }
                            else
                            {
                                getWidgetService().cleanState(keyPath.get(0), item.key, null);
                            }
                        }
                        else
                        {
                            getWidgetService().cleanState(keyPath.get(0), keyPath.get(keyPath.size() - 1), item.key);
                        }
                        refresh();
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
