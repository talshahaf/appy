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
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.Serializable;

public class DialogActivity extends Activity
{
    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_TEXT = "EXTRA_TEXT";
    public static final String EXTRA_BUTTONS = "EXTRA_BUTTONS";
    public static final String EXTRA_REQUEST_CODE = "EXTRA_REQUEST_CODE";
    public static final String EXTRA_ICON = "EXTRA_ICON";
    public static final String EXTRA_EDITTEXT_TEXT = "EXTRA_EDITTEXT_TEXT";
    public static final String EXTRA_EDITTEXT_HINT = "EXTRA_EDITTEXT_HINT";
    public static final String EXTRA_EDITTEXT_OPTIONS = "EXTRA_EDITTEXT_OPTIONS";

    private Widget widgetService;
    private int doneRequestCode = -1;

    private final ServiceConnection mConnection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            widgetService = ((Widget.LocalBinder) service).getService();

            //confirm started
            int request = getIntent().getIntExtra(EXTRA_REQUEST_CODE, -1);
            if (request != -1)
            {
                doneRequestCode = widgetService.generateRequestCode();
                widgetService.asyncReport(request, doneRequestCode);
            }

            Serializable optionsObject = getIntent().getSerializableExtra(EXTRA_EDITTEXT_OPTIONS);
            String[][] options = (optionsObject instanceof String[][]) ? (String[][])optionsObject : null;
            makeDialog(getIntent().getIntExtra(EXTRA_ICON, -1),
                    getIntent().getStringExtra(EXTRA_TITLE),
                    getIntent().getStringExtra(EXTRA_TEXT),
                    getIntent().getStringArrayExtra(EXTRA_BUTTONS),
                    getIntent().getStringArrayExtra(EXTRA_EDITTEXT_TEXT),
                    getIntent().getStringArrayExtra(EXTRA_EDITTEXT_HINT),
                    options);
        }

        public void onServiceDisconnected(ComponentName className)
        {
            widgetService = null;
        }
    };

    private boolean resultReported = false;

    public void dialogActivityResult(int which, String[] editTexts)
    {
        if (widgetService == null)
        {
            return;
        }

        if (resultReported)
        {
            return;
        }

        if (doneRequestCode == -1)
        {
            return;
        }
        widgetService.asyncReport(doneRequestCode, new Pair<>(which, editTexts));
        resultReported = true;
    }

    public static String[] getEditTexts(View[] editTextViews)
    {
        String[] texts = new String[editTextViews.length];
        for (int i = 0; i < texts.length; i++)
        {
            TextView textView = null;
            if (editTextViews[i] instanceof EditText)
            {
                textView = (TextView)editTextViews[i];

            }
            else if (editTextViews[i] instanceof TextInputLayout)
            {
                TextInputLayout layout = (TextInputLayout) editTextViews[i];
                textView = layout.getEditText();
            }

            if (textView != null)
            {
                texts[i] = textView.getText().toString();
            }
        }
        return texts;
    }

    public static View makeDropdown(Context context, String text, String hint, String[] options)
    {
        TextInputLayout layout = new TextInputLayout(context, null, R.style.ExposedDropdownMenu);
        AutoCompleteTextView textview = new AutoCompleteTextView(context);
        if (text != null)
        {
            textview.setText(text);
        }
        if (hint != null)
        {
            layout.setHint(hint);
        }
        textview.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        textview.setInputType(EditorInfo.TYPE_NULL);
        textview.setAdapter(new ArrayAdapter<>(context, R.layout.dropdown_text_item, options));
        textview.setThreshold(0);
        textview.setOnClickListener(v -> ((AutoCompleteTextView)v).showDropDown());
        textview.setOnFocusChangeListener((v, hasFocus) -> ((AutoCompleteTextView)v).showDropDown());
        layout.addView(textview);

        return layout;
    }

    public static View makeEditText(Context context, String text, String hint)
    {
        TextInputLayout layout = new TextInputLayout(context);
        TextInputEditText textview = new TextInputEditText(context);
        if (text != null)
        {
            textview.setText(text);
        }
        if (hint != null)
        {
            layout.setHint(hint);
        }
        textview.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        layout.addView(textview);
        return layout;
    }

    public void makeDialog(int icon, String title, String text, String[] buttons, String[] editTexts, String[] editHints, String[][] editOptions)
    {
        if (title == null || text == null || buttons == null || buttons.length == 0 || editTexts == null || editHints == null)
        {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(text);
        if (icon != -1)
        {
            builder.setIcon(icon);
        }
        final View[] editTextViews = new View[editTexts.length];
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < editTextViews.length; i++)
        {
            if (editOptions != null && i < editOptions.length && editOptions[i] != null)
            {
                editTextViews[i] = makeDropdown(this, editTexts[i], i < editHints.length ? editHints[i] : null, editOptions[i]);
            }
            else
            {
                editTextViews[i] = makeEditText(this, editTexts[i], i < editHints.length ? editHints[i] : null);
            }

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            double margin = Utils.convertUnit(this, 20, TypedValue.COMPLEX_UNIT_DIP, TypedValue.COMPLEX_UNIT_PX);
            params.leftMargin = (int) margin;
            params.rightMargin = (int) margin;

            editTextViews[i].setLayoutParams(params);
            container.addView(editTextViews[i]);
        }
        builder.setView(container);

        builder.setOnCancelListener(dialog -> {
            dialogActivityResult(-1, getEditTexts(editTextViews));
            finish();
        });

        DialogInterface.OnClickListener dialogClick = (dialog, whichButton) -> {
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
            dialogActivityResult(which, getEditTexts(editTextViews));
            finish();
        };

        switch (buttons.length)
        {
            //fallthrough
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
            unbindService(mConnection);
            widgetService = null;
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        dialogActivityResult(-1, getIntent().getStringArrayExtra(EXTRA_EDITTEXT_TEXT));
        doUnbindService();
    }

    @Override
    protected void onStop()
    {
        finish();
        super.onStop();
    }
}
