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
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;

public class DialogActivity extends Activity
{

    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_TEXT = "EXTRA_TEXT";
    public static final String EXTRA_BUTTONS = "EXTRA_BUTTONS";
    public static final String EXTRA_REQUEST_CODE = "EXTRA_REQUEST_CODE";
    public static final String EXTRA_ICON = "EXTRA_ICON";
    public static final String EXTRA_EDITTEXT_TEXT = "EXTRA_EDITTEXT_TEXT";
    public static final String EXTRA_EDITTEXT_HINT = "EXTRA_EDITTEXT_HINT";

    private Widget widgetService;

    private ServiceConnection mConnection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            widgetService = ((Widget.LocalBinder) service).getService();
            makeDialog(getIntent().getIntExtra(EXTRA_REQUEST_CODE, -1),
                    getIntent().getIntExtra(EXTRA_ICON, -1),
                    getIntent().getStringExtra(EXTRA_TITLE),
                    getIntent().getStringExtra(EXTRA_TEXT),
                    getIntent().getStringArrayExtra(EXTRA_BUTTONS),
                    getIntent().getStringArrayExtra(EXTRA_EDITTEXT_TEXT),
                    getIntent().getStringArrayExtra(EXTRA_EDITTEXT_HINT));
        }

        public void onServiceDisconnected(ComponentName className)
        {
            widgetService = null;
        }
    };

    public interface DialogActivityButtonClick
    {
        void onClick(int which, String[] editTexts);
    }

    public static String[] getEditTexts(EditText[] editTextViews)
    {
        String[] texts = new String[editTextViews.length];
        for (int i = 0; i < texts.length; i++)
        {
            texts[i] = editTextViews[i].getText().toString();
        }
        return texts;
    }

    public void makeDialog(int request, int icon, String title, String text, String[] buttons, String[] editTexts, String[] editHints)
    {
        if (request == -1 || title == null || text == null || buttons == null || buttons.length == 0 || editTexts == null || editHints == null)
        {
            return;
        }

        final DialogActivityButtonClick onClick = new DialogActivityButtonClick()
        {
            @Override
            public void onClick(int which, String[] editTexts)
            {
                widgetService.asyncReport(request, new Pair<>(which, editTexts));
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(text);
        if (icon != -1)
        {
            builder.setIcon(icon);
        }
        final EditText[] editTextViews = new EditText[editTexts.length];
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < editTextViews.length; i++)
        {
            editTextViews[i] = new EditText(this);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            float margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
            params.leftMargin = (int) margin;
            params.rightMargin = (int) margin;

            editTextViews[i].setLayoutParams(params);
            editTextViews[i].setText(editTexts[i]);
            if (i < editHints.length)
            {
                editTextViews[i].setHint(editHints[i]);
            }
            container.addView(editTextViews[i]);
        }
        builder.setView(container);

        builder.setOnCancelListener(new DialogInterface.OnCancelListener()
        {
            @Override
            public void onCancel(DialogInterface dialog)
            {
                onClick.onClick(-1, getEditTexts(editTextViews));
                finish();
            }
        });

        DialogInterface.OnClickListener dialogClick = new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                int which = -1;
                switch (whichButton)
                {
                    case AlertDialog.BUTTON_NEUTRAL:
                        which = 2;
                        break;
                    case AlertDialog.BUTTON_NEGATIVE:
                        which = 1;
                        break;
                    case AlertDialog.BUTTON_POSITIVE:
                        which = 0;
                        break;
                }
                onClick.onClick(which, getEditTexts(editTextViews));
                finish();
            }
        };

        switch (buttons.length)
        {
            //fallthroughs
            default:
            case 3:
                builder.setNeutralButton(buttons[2], dialogClick);
            case 2:
                builder.setNegativeButton(buttons[1], dialogClick);
            case 1:
                builder.setPositiveButton(buttons[0], dialogClick);
        }

        builder.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        doBindService();
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
            widgetService.setStatusListener(null);
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
}
