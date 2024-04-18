package com.appy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BroadcastInterfaceBridge extends BroadcastReceiver
{
    private BroadcastInterface iface;

    public BroadcastInterfaceBridge(BroadcastInterface iface)
    {
        super();
        this.iface = iface;
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        try
        {
            iface.onReceive(context, intent);
        }
        catch (Exception e)
        {
            Log.e("APPY", "Exception on onReceive", e);
        }
    }
}
