package com.appy;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Pair;
import android.view.Window;
import android.view.WindowManager;

public class PermissionActivity extends Activity
{
    public static final String EXTRA_REQUEST_CODE = "EXTRA_REQUEST_CODE";
    public static final String EXTRA_PERMISSIONS = "EXTRA_PERMISSIONS";
    private Widget widgetService;

    private String[] permissions;

    private ServiceConnection mConnection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            widgetService = ((Widget.LocalBinder) service).getService();
            permissions = getIntent().getStringArrayExtra(EXTRA_PERMISSIONS);
            requestPermissions(getIntent().getIntExtra(EXTRA_REQUEST_CODE, 1), permissions);
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

        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        doBindService();
    }

    public static boolean isSpecialPermission(String permission)
    {
        if (Manifest.permission.ACCESS_NOTIFICATION_POLICY.equals(permission))
        {
            return true;
        }
        //...
        return false;
    }

    public static int checkSpecialPermission(Context context, String permission)
    {
        if (Manifest.permission.ACCESS_NOTIFICATION_POLICY.equals(permission))
        {
            boolean granted = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            {
                granted = ((NotificationManager)context.getSystemService(NOTIFICATION_SERVICE)).isNotificationPolicyAccessGranted();
            }
            return granted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
        }
        //...
        return PackageManager.PERMISSION_DENIED;
    }

    public void requestSpecialPermission(int requestCode, String permission)
    {
        if (Manifest.permission.ACCESS_NOTIFICATION_POLICY.equals(permission))
        {
            Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            startActivityForResult(intent, requestCode);
        }
        //...
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (permissions != null && permissions.length == 1)
        {
            if (Manifest.permission.ACCESS_NOTIFICATION_POLICY.equals(permissions[0]))
            {
                onRequestPermissionsResult(requestCode, permissions, new int[] {checkSpecialPermission(this, permissions[0])});
                return;
            }
            //...
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public static Pair<int[], Boolean> getPermissionState(Context context, String[] permissions)
    {
        int[] granted = new int[permissions.length];
        boolean hasDenied = false;
        for (int i = 0; i < permissions.length; i++)
        {
            if (isSpecialPermission(permissions[i]))
            {
                granted[i] = checkSpecialPermission(context, permissions[i]);
            }
            else
            {
                granted[i] = ContextCompat.checkSelfPermission(context, permissions[i]);
            }
            if (granted[i] != PackageManager.PERMISSION_GRANTED)
            {
                hasDenied = true;
            }
        }

        return new Pair<>(granted, !hasDenied);
    }

    public static boolean canRequestPermissionsTogether(String[] permissions)
    {
        for (String permission : permissions)
        {
            if (isSpecialPermission(permission) && permissions.length != 1)
            {
                return false;
            }
        }
        return true;
    }

    public void requestPermissions(int requestCode, String[] permissions)
    {
        Pair<int[], Boolean> state = getPermissionState(this, permissions);
        if (state.second)
        {
            onRequestPermissionsResult(requestCode, permissions, state.first);
        }
        else
        {
            if (permissions.length == 1 && isSpecialPermission(permissions[0]))
            {
                requestSpecialPermission(requestCode, permissions[0]);
            }
            else
            {
                ActivityCompat.requestPermissions(this, permissions, requestCode);
            }
        }
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

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        doUnbindService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (widgetService != null)
        {
            widgetService.asyncReport(requestCode, new Pair<>(permissions, grantResults));
        }
        finish();
    }
}
