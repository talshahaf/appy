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

import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;

import org.jspecify.annotations.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class TimerFragment extends FragmentParent
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

        TimerSelectFragment fragment = new TimerSelectFragment();
        fragment.setParent(this);
        fragment.setWidget(null);
        switchTo(fragment, true);
    }

    public static class TimerSelectFragment extends ChildFragment implements AdapterView.OnItemClickListener, MenuProvider
    {
        ListView list;
        String widget = null;
        boolean isApp = false;

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
            if (getWidgetService() == null)
            {
                return;
            }

            DictObj.Dict allTimers = getWidgetService().getTimersSnapshot();

            ArrayList<ListFragmentAdapter.Item> adapterList = new ArrayList<>();

            if (widget == null)
            {
                if (getActivity() != null)
                {
                    getActivity().setTitle("Timers");
                }

                for (String key : allTimers.keys())
                {
                    DictObj.Dict val = allTimers.getDict(key);
                    String name = val.getString("display_name");
                    int timers = val.getList("timers").size();
                    boolean isApp = val.getBoolean("app", false);
                    adapterList.add(new ListFragmentAdapter.Item(key, name + " (" + Utils.enumerableFormat(timers, "timer", "timers") + ")", item -> ((isApp ? "app #" : "widget #") + item.key), false));
                }
            }
            else
            {
                DictObj.Dict widgetTimers = allTimers.getDict(widget);
                isApp = widgetTimers != null && widgetTimers.getBoolean("app", false);
                String entity = (isApp ? "app #" : "widget #") + widget;

                if (getActivity() != null)
                {
                    getActivity().setTitle("Timers of " + entity);
                }

                if (widgetTimers != null)
                {
                    DictObj.List timers = widgetTimers.getList("timers");
                    for (int i = 0; i < timers.size(); i++)
                    {
                        DictObj.Dict timer = timers.getDict(i);
                        boolean isInterval = timer.hasKey("interval");
                        final String prefix = isInterval ? "Interval timer #" : "Absolute timer #";
                        String subtext;
                        if (isInterval)
                        {
                            subtext = floatFormat(((float) timer.getLong("interval", 0)) / 1000) + " seconds\nNext: " + floatFormat(((float) timer.getLong("to_next", 0)) / 1000) + " seconds";
                        }
                        else
                        {
                            subtext = "At " + new SimpleDateFormat(Constants.DATE_FORMAT).format(new Date(timer.getLong("time", 0)));
                        }
                        adapterList.add(new ListFragmentAdapter.Item(((Long) timer.getLong("id", 0)).toString(), subtext, item -> (prefix + idFormat(item.key)), true));
                    }
                }
            }
            list.setAdapter(new ListFragmentAdapter(getActivity(), adapterList));
        }

        public void setWidget(String widget)
        {
            this.widget = widget;
            this.isApp = false;
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
            if (!(Boolean)item.arg)
            {
                //select that widget
                TimerSelectFragment fragment = new TimerSelectFragment();
                fragment.setWidget(item.key);
                parent.switchTo(fragment, false);
            }
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
            null, null, () -> {
                if (leaf)
                {
                    getWidgetService().cancelTimer(Long.parseLong(item.key));
                }
                else
                {
                    getWidgetService().cancelWidgetTimers(Integer.parseInt(item.key));
                }
                refresh();
            });
            return true;
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
                Utils.showConfirmationDialog(getActivity(),
                        widget == null ? "Clear all timers" : "Clear " + (isApp ? "app" : "widget") + " timers",
                        widget == null ? "Clear timers for all widgets?" : "Clear all timers for " + (isApp ? "app #" : "widget #") + widget + "?",
                        android.R.drawable.ic_dialog_alert, null, null, () -> {
                            if (widget == null)
                            {
                                getWidgetService().cancelAllTimers();
                            }
                            else
                            {
                                getWidgetService().cancelWidgetTimers(Integer.parseInt(widget));
                            }
                            refresh();
                        });
                return true;
            }
            return false;
        }
    }
}
