package com.appy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by Tal on 09/03/2018.
 */

public class BootReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
        {
            Log.d("APPY", "boot onReceive intent");
            context.startService(new Intent(context, Widget.class));
        }
    }
}
