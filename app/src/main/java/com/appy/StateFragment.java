package com.appy;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;

import android.os.Handler;
import android.util.Log;
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

import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StateFragment extends FragmentParent
{
    public static final int EXPORT_CODE = 1;
    private Handler handler;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        super.onCreateView(inflater, container, savedInstanceState);
        handler = new Handler();
        return inflater.inflate(R.layout.fragment_parent, container, false);
    }

    public DictObj.Dict getAllState()
    {
        DictObj.Dict result = new DictObj.Dict();

        for (String scope : StateSelectFragment.scopes)
        {
            int depth = StateSelectFragment.getScopeDepth(scope);
            DictObj.Dict snapshot = getWidgetService().getStateLayoutSnapshot(scope, null);
            if (snapshot == null)
            {
                return result;
            }
            if (depth == 1)
            {
                result.put(scope, snapshot);
            }
            else if (depth == 2)
            {
                DictObj.Dict scopeDict = new DictObj.Dict();
                for (String key : snapshot.keyset())
                {
                    scopeDict.put(key, getWidgetService().getStateLayoutSnapshot(scope, key));
                }
            }
            else if (depth == 3)
            {
                DictObj.Dict scopeDict = new DictObj.Dict();
                for (String widgetName : snapshot.keyset())
                {
                    DictObj.Dict widgetDict = new DictObj.Dict();
                    for (String widgetId : snapshot.getDict(widgetName).keyset())
                    {
                        widgetDict.put(widgetId, getWidgetService().getStateLayoutSnapshot(scope, widgetId));
                    }
                    scopeDict.put(widgetName, widgetDict);
                }
                result.put(scope, scopeDict);
            }
        }
        return result;
    }

    @Override
    public void onActivityResult(Intent data)
    {
        String[] files = data.getStringArrayExtra(FileBrowserActivity.RESULT_FILES);
        if (files == null || files.length == 0)
        {
            return;
        }

        if (getWidgetService() == null)
        {
            return;
        }

        int requestCode = data.getIntExtra(FileBrowserActivity.REQUEST_CODE, -1);
        if (requestCode == EXPORT_CODE)
        {
            File file = new File(files[0], "exported_state.json");
            Runnable export = () -> {
                Toast.makeText(requireActivity(), "Exporting state...", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    try
                    {
                        DictObj.Dict stateSnapshot = getAllState();
                        Utils.writeFile(file, DictObj.makeJson(stateSnapshot, true));
                        handler.post(() -> Toast.makeText(requireActivity(), "State exported to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
                    }
                    catch (IOException e)
                    {
                        Log.e("APPY", "export state failed", e);
                        handler.post(() -> Toast.makeText(requireActivity(), "Failed to write file", Toast.LENGTH_SHORT).show());
                    }
                }).start();
            };

            if (file.exists())
            {
                Utils.showConfirmationDialog(requireActivity(),
                        "Export State", "File already exists, overwrite?", android.R.drawable.ic_dialog_alert,
                        null, null, export);
            }
            else
            {
                export.run();
            }
        }
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
                if (stateSnapshot == null)
                {
                    return;
                }
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
                if (stateSnapshot == null)
                {
                    return;
                }
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
            super.onCreateView(inflater, container, savedInstanceState);

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
        public void onStartupCompleted()
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
            return switch (scope)
            {
                case "globals" -> 1;
                case "nonlocals" -> 2;
                case "locals" -> 3;
                default -> throw new IllegalArgumentException("unknown scope");
            };
        }

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState)
        {
            requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        }

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater)
        {
            inflater.inflate(R.menu.state_toolbar_action, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem item)
        {
            if (item.getItemId() == R.id.action_clear)
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
            else if (item.getItemId() == R.id.action_export)
            {
                if (getWidgetService() != null)
                {
                    parent.launchFileBrowser(EXPORT_CODE, getWidgetService().getPreferredScriptDir(), false, false, true, null);
                }
            }
            return false;
        }
    }
}
