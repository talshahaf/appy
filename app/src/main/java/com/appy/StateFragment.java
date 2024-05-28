package com.appy;

import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.view.ContextMenu;
import android.view.LayoutInflater;
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

        updateDict();

        WidgetSelectFragment fragment = new WidgetSelectFragment();
        fragment.setParent(this);
        fragment.setKeyPath();
        switchTo(fragment, true);
    }

    @Override
    public DictObj.Dict getDict()
    {
        return stateSnapshot;
    }

    public void updateDict()
    {
        stateSnapshot = getWidgetService().getStateLayoutSnapshot();
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

        public DictObj.Dict traverse()
        {
            DictObj.Dict current = parent.getDict();
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
            parent.updateDict();

            ArrayList<ListFragmentAdapter.Item> adapterList = new ArrayList<>();
            if (keyPath.isEmpty())
            {
                for (String scope : scopes)
                {
                    adapterList.add(new ListFragmentAdapter.Item(scope, "", "", false));
                }
            }
            else
            {
                int depth = getScopeDepth(keyPath.get(0));
                boolean areLeaves = depth == keyPath.size();
                boolean inLocalScopeView = depth - 1 == keyPath.size() && depth == 3;
                String keyPrefix = inLocalScopeView ? "widget #" : "";

                DictObj.Dict current = traverse();
                if (current != null)
                {
                    for (String key : current.keys())
                    {
                        adapterList.add(new ListFragmentAdapter.Item(key, areLeaves ? current.get(key).toString() : "", keyPrefix, areLeaves));
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
