package com.appy;

import android.content.Context;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;

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

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class StateFragment extends FragmentParent
{
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_parent, container, false);
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

        StateSelectFragment fragment = new StateSelectFragment();
        fragment.setParent(this);
        fragment.setKeyPath();
        switchTo(fragment, true);
    }

    public static class StateSelectFragment extends ChildFragment implements AdapterView.OnItemClickListener, MenuProvider
    {
        ListView list;
        ArrayList<String> keyPath;

        public void refresh()
        {
            if (getWidgetService() == null)
            {
                return;
            }

            DictObj.Dict widgetProps = getWidgetService().getAllWidgetAppProps(false, false);

            ArrayList<ListFragmentAdapter.Item> adapterList = new ArrayList<>();
            if (keyPath.isEmpty())
            {
                DictObj.Dict stateSnapshot = getWidgetService().getStateLayoutSnapshot(null, null);
                if (getActivity() != null)
                {
                    getActivity().setTitle("State");
                }

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

                DictObj.Dict stateSnapshot = getWidgetService().getStateLayoutSnapshot(keyPath.get(0), areLeaves ? keyPath.get(keyPath.size() - 1) : null);
                for (String key : stateSnapshot.keys())
                {
                    if (areLeaves)
                    {
                        adapterList.add(new ListFragmentAdapter.Item(key, Utils.capWithEllipsis(stateSnapshot.get(key).toString(), 100, true), item -> item.key, stateSnapshot.get(key).toString()));
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
            View layout = inflater.inflate(R.layout.fragment_list, container, false);
            list = layout.findViewById(R.id.list_view);
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
                StateSelectFragment fragment = new StateSelectFragment();
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
            Context context = getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(title);
            builder.setPositiveButton("OK", null);
            builder.setNeutralButton("Copy", null);

            View layout = LayoutInflater.from(getActivity()).inflate(R.layout.alert_error_view, null);
            TextView message = layout.findViewById(R.id.message);

            if (text.length() > 10240)
            {
                //Too slow
                message.setTextIsSelectable(false);
            }

            message.setText("Loading...");
            message.post(() -> message.setText(text + "\n\n"));

            builder.setView(layout);
            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(d -> ((AlertDialog)d).getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> Utils.copyToClipboard(context, title, text, "State copied to clipboard")));
            dialog.show();
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

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState)
        {
            requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        }

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater)
        {
            inflater.inflate(R.menu.clearall_toolbar_action, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem item)
        {
            if (item.getItemId() == R.id.action_clearall)
            {
                String title = keyPath.isEmpty() ? "Clear all state" : "Clear states";
                String message = keyPath.isEmpty() ? "Clear state for all widgets?" : "Clear all states in " + String.join(".", keyPath) + "?";

                Utils.showConfirmationDialog(getActivity(),
                        title, message, android.R.drawable.ic_dialog_alert,
                        null, null, () -> {
                            if (keyPath.isEmpty())
                            {
                                getWidgetService().resetState();
                            }
                            else
                            {
                                int depth = getScopeDepth(keyPath.get(0));
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
                            }
                            refresh();
                        });
                return true;
            }
            return false;
        }
    }
}
