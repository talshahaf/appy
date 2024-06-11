package com.appy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;

public abstract class WidgetSelectActivity extends AppCompatActivity implements ListView.OnItemClickListener
{
    protected Widget widgetService;
    ListView listview;
    Toolbar toolbar;

    private ServiceConnection mConnection = new ServiceConnection(){
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            widgetService = ((Widget.LocalBinder) service).getService();
            updateWidgetList();
        }

        public void onServiceDisconnected(ComponentName className)
        {
            widgetService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.widget_select_activity);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(getToolbarHeader());

        listview = findViewById(R.id.list);
        listview.setOnItemClickListener(this);

        doBindService();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        doUnbindService();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        updateWidgetList();
    }

    public void updateWidgetList()
    {
        if (widgetService == null)
        {
            return;
        }

        DictObj.Dict widgets = widgetService.getAllWidgetNames();

        ArrayList<ListFragmentAdapter.Item> adapterList = new ArrayList<>();
        for (DictObj.Entry widget : widgets.entries())
        {
            // ignore widget managers
            if (widget.value != null)
            {
                adapterList.add(new ListFragmentAdapter.Item(widget.key, (String) widget.value, "widget #", Integer.parseInt(widget.key)));
            }
        }
        listview.setAdapter(new ListFragmentAdapter(this, adapterList));
    }

    void doBindService()
    {
        Intent bindIntent = new Intent(this, Widget.class);
        bindIntent.putExtra(Constants.LOCAL_BIND_EXTRA, true);
        bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    void doUnbindService()
    {
        if (widgetService != null)
        {
            unbindService(mConnection);
            widgetService = null;
        }
    }

    protected abstract void onWidgetSelected(int widgetId, String widgetName);
    protected abstract String getToolbarHeader();
    protected abstract boolean hasContextMenu();
    protected void onWidgetCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo, int widgetId, String widgetName)
    {

    }
    protected boolean onWidgetContextSelected(int itemid, int widgetId, String widgetName)
    {
        return false;
    }

    @Override
    public void onItemClick(AdapterView<?> adapter, View view, int position, long id)
    {
        if (widgetService == null)
        {
            return;
        }

        ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) adapter.getItemAtPosition(position);
        onWidgetSelected((Integer)item.arg, item.value);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);

        ListView list = (ListView)v;
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        ListFragmentAdapter.Item item = (ListFragmentAdapter.Item)list.getAdapter().getItem(info.position);

        onWidgetCreateContextMenu(menu, v, menuInfo, (Integer)item.arg, item.value);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (info == null)
        {
            return super.onContextItemSelected(item);
        }
        ListFragmentAdapter.Item listitem = (ListFragmentAdapter.Item) listview.getAdapter().getItem(info.position);

        if (onWidgetContextSelected(item.getItemId(), (Integer)listitem.arg, listitem.value))
        {
            return true;
        }

        return super.onContextItemSelected(item);
    }
}
