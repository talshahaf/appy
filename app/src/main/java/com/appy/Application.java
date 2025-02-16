package com.appy;

import android.os.Build;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

public class Application extends android.app.Application
{
    @Override
    public void onCreate()
    {
        super.onCreate();

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
