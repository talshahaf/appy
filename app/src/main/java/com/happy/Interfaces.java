package com.happy;

import android.content.Context;
import android.content.Intent;

/**
 * Created by Tal on 30/12/2017.
 */

interface Interfaces
{
    Object action(Object i) throws Throwable;
}

interface BroadcastInterface
{
    void onReceive(Context context, Intent intent);
}
