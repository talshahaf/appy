package com.appy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        Log.d("APPY", "foreground activity!");

        Intent widgetIntent = getIntent().getParcelableExtra(Constants.WIDGET_INTENT);
        if (widgetIntent == null)
        {
            widgetIntent = getIntent();
        }

        Intent serviceIntent = new Intent(this, Widget.class);
        serviceIntent.putExtra(Constants.WIDGET_INTENT, widgetIntent);
        Widget.startService(this, serviceIntent);

        finish();
    }
}
