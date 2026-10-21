package com.appy;

import android.os.Build;
import android.os.StrictMode;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

public class Application extends android.app.Application
{
    @Override
    public void onCreate()
    {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    //.penaltyDeath()
                    .build());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            try
            {
                HiddenApiBypass.setHiddenApiExemptions("");
            }
            catch (Exception e)
            {
                // best effort
            }
        }
    }
}
