package com.appy;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class ShareActivity extends WidgetSelectActivity
{
    Intent shareIntent;
    Handler handler;

    String shareText;
    String shareMime;
    ArrayList<Uri> shareUris;

    @Override
    public void onWidgetSelected(View view, int widgetId, String widgetName)
    {
        if (widgetService == null)
        {
            return;
        }

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
                    for (Uri uri : shareUris)
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
                    widgetService.shareWithWidget(widgetId, shareMime, shareText, datas);
                }
                catch (Exception e)
                {
                    Log.e("APPY", "Exception while reading shared data", e);
                }

                handler.post(() -> finish());
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

        shareMime = shareIntent.getType();

        shareText = shareIntent.getStringExtra(Intent.EXTRA_TEXT);
        Parcelable stream = shareIntent.getParcelableExtra(Intent.EXTRA_STREAM);

        Uri singleuri = null;
        ArrayList<Uri> multipleUris = null;

        if (stream instanceof Uri)
        {
            singleuri = (Uri)stream;
        }
        else if (stream instanceof ArrayList)
        {
            multipleUris = (ArrayList<Uri>)stream;
        }
        else
        {
            //unknown
            finish();
        }

        shareUris = new ArrayList<>();
        if (singleuri != null)
        {
            shareUris.add(singleuri);
        }
        if (multipleUris != null)
        {
            shareUris.addAll(multipleUris);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (widgetService == null)
        {
            return super.onOptionsItemSelected(item);
        }

        if (item.getItemId() == R.id.action_import)
        {
            Log.d("APPY", "preparing to share ");
            PythonFileImport.importPythonFromExternalUri(this, widgetService, shareUris.get(0), () -> {
                Log.d("APPY", "prepared to share ");
                finish();
            });
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu)
    {
        if (shareUris.size() != 1)
        {
            //TODO support sharing multiple python files?
            return false;
        }

        getMenuInflater().inflate(R.menu.share_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }
}
