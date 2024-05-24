package com.appy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class ShareActivity extends Activity implements ListView.OnItemClickListener
{
    private Widget widgetService;
    ListView listview;

    Intent shareIntent;
    Handler handler;

    private ServiceConnection mConnection = new ServiceConnection(){
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            widgetService = ((Widget.LocalBinder) service).getService();

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
            listview.setAdapter(new ListFragmentAdapter(ShareActivity.this, adapterList));
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

        setContentView(R.layout.share_activity);

        handler = new Handler();

        shareIntent = getIntent();

        listview = findViewById(R.id.list);
        listview.setOnItemClickListener(this);

        if (shareIntent.getType() == null || shareIntent.getExtras() == null || !Intent.ACTION_SEND.equals(shareIntent.getAction()) && !Intent.ACTION_SEND_MULTIPLE.equals(shareIntent.getAction()))
        {
            finish();
        }
        else
        {
            doBindService();
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        doUnbindService();
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
            widgetService.setStatusListener(null);
            unbindService(mConnection);
            widgetService = null;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapter, View view, int position, long id)
    {
        if (widgetService == null)
        {
            return;
        }

        ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) adapter.getItemAtPosition(position);

        String mimetype = shareIntent.getType();

        String text = shareIntent.getStringExtra(Intent.EXTRA_TEXT);
        Uri singleuri = shareIntent.getParcelableExtra(Intent.EXTRA_STREAM);
        ArrayList<Uri> multipleUris = shareIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);

        ArrayList<Uri> allUris = new ArrayList<>();
        if (singleuri != null)
        {
            allUris.add(singleuri);
        }
        if (multipleUris != null)
        {
            allUris.addAll(multipleUris);
        }

        final ArrayList<Uri> finalAllUris = allUris;

        Thread reader = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    final int bufLen = 4096;
                    byte[] buf = new byte[bufLen];
                    int readed;

                    DictObj.Dict datas = new DictObj.Dict();
                    for (Uri uri : finalAllUris)
                    {
                        byte[] data = new byte[0];
                        try (InputStream inputStream = getContentResolver().openInputStream(uri))
                        {
                            if (inputStream != null)
                            {
                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                while ((readed = inputStream.read(buf, 0, bufLen)) != -1)
                                {
                                    outputStream.write(buf, 0, readed);
                                }

                                data = outputStream.toByteArray();
                            }
                        }
                        catch (Exception e)
                        {
                            Log.e("APPY", "Exception while reading shared data", e);
                        }
                        datas.put(uri.toString(), data, false);
                    }
                    widgetService.shareWithWidget((Integer)item.arg, mimetype, text, datas);
                }
                catch (Exception e)
                {
                    Log.e("APPY", "Exception while reading shared data", e);
                }

                handler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        finish();
                    }
                });
            }
        };

        reader.start();


    }
}
