package com.appy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class LinkService extends Activity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if(intent != null)
        {
            String customAction = intent.getStringExtra("action");
            String action = Constants.DEEP_LINK_BROADCAST + (customAction == null ? "" : "." + customAction);

            Log.d("APPY", "sending "+action);

            Intent broadcastIntent = new Intent(action);
            broadcastIntent.putExtras(intent);
            broadcastIntent.putExtra("uri", intent.getData());
            broadcastIntent.putExtra("mimetype", intent.getType());
            broadcastIntent.setPackage(getApplicationContext().getPackageName());
            sendBroadcast(broadcastIntent);
        }
        finish();
    }
}
