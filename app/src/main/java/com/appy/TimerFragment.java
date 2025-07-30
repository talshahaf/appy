package com.appy;

import android.os.Bundle;
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

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Date;

public class TimerFragment extends FragmentParent
{
    DictObj.Dict timerSnapshot = null;

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
                "Clear all timers", "Clear timers for all widgets?", android.R.drawable.ic_dialog_alert,
                null, null, new Runnable()
                {
                    @Override
                    public void run()
                    {
                        getWidgetService().cancelAllTimers();
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

        updateDict();

        WidgetSelectFragment fragment = new WidgetSelectFragment();
        fragment.setParent(this);
        fragment.setWidget(null);
        switchTo(fragment, true);
    }

    @Override
    public DictObj.Dict getDict()
    {
        return timerSnapshot;
    }

    public void updateDict()
    {
        timerSnapshot = getWidgetService().getTimersSnapshot();
    }

    public static class WidgetSelectFragment extends ChildFragment implements AdapterView.OnItemClickListener
    {
        ListView list;
        String widget = null;

        public static String floatFormat(float f)
        {
            String s = String.format("%.1f", f);
            if (s.endsWith(".0"))
            {
                return s.substring(0, s.lastIndexOf("."));
            }
            return s;
        }

        public static String idFormat(String id)
        {
            if (id.length() >= 8)
            {
                return id.substring(0, id.charAt(0) == '-' ? 4 : 3) + "..." + id.substring(id.length() - 3);
            }
            return id;
        }

        public void refresh()
        {
            parent.updateDict();
            ArrayList<ListFragmentAdapter.Item> adapterList = new ArrayList<>();

            if (widget == null)
            {
                for (DictObj.Entry entry : parent.getDict().entries())
                {
                    DictObj.Dict val = (DictObj.Dict)entry.value;
                    String name = val.hasKey("name") ? val.getString("name") : "";
                    int timers = val.getList("timers").size();
                    adapterList.add(new ListFragmentAdapter.Item(entry.key, name + " (" + timers + " timers)", item -> ("widget #" + item.key), false));
                }
            }
            else
            {
                DictObj.List timers = parent.getDict().getDict(widget).getList("timers");
                for (int i = 0; i < timers.size(); i++)
                {
                    DictObj.Dict timer = timers.getDict(i);
                    boolean isInterval = timer.hasKey("interval");
                    final String prefix = isInterval ? "Interval timer #" : "Absolute timer #";
                    String subtext;
                    if (isInterval)
                    {
                        subtext = floatFormat(((float)timer.getLong("interval", 0)) / 1000) + " seconds\nNext: " + floatFormat(((float)timer.getLong("to_next", 0)) / 1000) + " seconds";
                    }
                    else
                    {
                        subtext = "At " + Constants.DATE_FORMAT.format(new Date(timer.getLong("time", 0)));
                    }
                    adapterList.add(new ListFragmentAdapter.Item(((Long)timer.getLong("id", 0)).toString(), subtext, item -> (prefix + idFormat(item.key)), true));
                }
            }
            list.setAdapter(new ListFragmentAdapter(getActivity(), adapterList));
        }

        public void setWidget(String widget)
        {
            this.widget = widget;
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
                fragment.setWidget(item.key);
                parent.switchTo(fragment, false);
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

            boolean leaf = (Boolean)item.arg;
            String display = item.keyFormat.format(item);
            Utils.showConfirmationDialog(getActivity(),
                    leaf ? "Delete timer" : "Delete widget timer",
                    leaf ? "Delete " + display + "?" : "Delete all " + display + " timers?",
                    android.R.drawable.ic_dialog_alert,
            null, null, new Runnable()
            {
                @Override
                public void run()
                {
                    if (leaf)
                    {
                        getWidgetService().cancelTimer(Long.parseLong(item.key));
                    }
                    else
                    {
                        getWidgetService().cancelWidgetTimers(Integer.parseInt(item.key));
                    }
                }
            });
            return true;
        }
    }
}
