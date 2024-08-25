package com.appy;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class ShareActivity extends WidgetSelectActivity
{
    Intent shareIntent;
    Handler handler;

    @Override
    public void onWidgetSelected(View view, int widgetId, String widgetName)
    {
        if (widgetService == null)
        {
            return;
        }

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
                    widgetService.shareWithWidget(widgetId, mimetype, text, datas);
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

    @Override
    public String getToolbarHeader()
    {
        return "Share with";
    }

    @Override
    public boolean hasContextMenu()
    {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        handler = new Handler();

        shareIntent = getIntent();

        if (shareIntent.getType() == null || shareIntent.getExtras() == null || !Intent.ACTION_SEND.equals(shareIntent.getAction()) && !Intent.ACTION_SEND_MULTIPLE.equals(shareIntent.getAction()))
        {
            finish();
        }
    }
}
