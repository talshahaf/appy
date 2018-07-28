package com.appy;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Pair;
import android.view.WindowManager;

public class PermissionActivity extends Activity
{
    public static final String EXTRA_REQUEST_CODE = "EXTRA_REQUEST_CODE";
    public static final String EXTRA_PERMISSIONS  = "EXTRA_PERMISSIONS";
    private Widget widgetService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            widgetService = ((Widget.LocalBinder) service).getService();
            requestPermissions(getIntent().getIntExtra(EXTRA_REQUEST_CODE, 1), getIntent().getStringArrayExtra(EXTRA_PERMISSIONS));
        }

        public void onServiceDisconnected(ComponentName className) {
            widgetService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        doBindService();
    }

    public void requestPermissions(int requestCode, String[] permissions) {
        Pair<int[], Boolean> state = Widget.getPermissionState(this, permissions);
        if(state.second)
        {
            onRequestPermissionsResult(requestCode, permissions, state.first);
        }
        else
        {
            ActivityCompat.requestPermissions(this, permissions, requestCode);
        }
    }

    void doBindService() {
        Intent bindIntent = new Intent(this, Widget.class);
        bindIntent.putExtra(Constants.LOCAL_BIND_EXTRA, true);
        bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    void doUnbindService() {
        if (widgetService != null) {
            widgetService.setStatusListener(null);
            unbindService(mConnection);
            widgetService = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(widgetService != null)
        {
            widgetService.reportRequestPermission(requestCode, permissions, grantResults);
        }
        finish();
    }
}
