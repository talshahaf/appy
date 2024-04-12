package com.appy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Pair;

import androidx.appcompat.app.AlertDialog;

public class DialogActivity extends Activity {

    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_TEXT = "EXTRA_TEXT";
    public static final String EXTRA_BUTTONS = "EXTRA_BUTTONS";
    public static final String EXTRA_REQUEST_CODE = "EXTRA_REQUEST_CODE";
    public static final String EXTRA_ICON = "EXTRA_ICON";

    private Widget widgetService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            widgetService = ((Widget.LocalBinder) service).getService();
            makeDialog(getIntent().getIntExtra(EXTRA_REQUEST_CODE, -1),
                        getIntent().getIntExtra(EXTRA_ICON, android.R.drawable.ic_dialog_alert),
                        getIntent().getStringExtra(EXTRA_TITLE),
                        getIntent().getStringExtra(EXTRA_TEXT),
                        getIntent().getStringArrayExtra(EXTRA_BUTTONS));
        }

        public void onServiceDisconnected(ComponentName className) {
            widgetService = null;
        }
    };

    public interface DialogActivityButtonClick
    {
        void onClick(int which);
    };
    public void makeDialog(int request, int icon, String title, String text, String[] buttons)
    {
        if (request == -1 || title == null || text == null || buttons == null || buttons.length == 0)
        {
            return;
        }

        final DialogActivityButtonClick onClick = new DialogActivityButtonClick() {
            @Override
            public void onClick(int which) {
                widgetService.asyncReport(request, which);
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setIcon(icon)
                .setTitle(title)
                .setMessage(text);
        switch (buttons.length) {
            //fallthroughs
            default:
            case 3:
                builder.setNeutralButton(buttons[2], new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        onClick.onClick(2);
                        finish();
                    }
                });
            case 2:
                builder.setNegativeButton(buttons[1], new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        onClick.onClick(1);
                        finish();
                    }
                });
            case 1:
                builder.setPositiveButton(buttons[0], new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        onClick.onClick(0);
                        finish();
                    }
                });
        }
        builder.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        doBindService();
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
}
