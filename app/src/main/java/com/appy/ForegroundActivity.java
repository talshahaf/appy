package com.appy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

public class ForegroundActivity extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        Log.d("APPY", "foreground activity!");

        Intent widgetIntent = getIntent().getParcelableExtra(Constants.WIDGET_INTENT);

        Intent serviceIntent = new Intent(this, Widget.class);
        serviceIntent.putExtra(Constants.WIDGET_INTENT, widgetIntent);
        Widget.startService(this, serviceIntent);

        finish();
    }
}
