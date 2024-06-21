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

    private ServiceConnection mConnection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            widgetService = ((Widget.LocalBinder) service).getService();
            Serializable optionsObject = getIntent().getSerializableExtra(EXTRA_EDITTEXT_OPTIONS);
            String[][] options = (optionsObject instanceof String[][]) ? (String[][])optionsObject : null;
            makeDialog(getIntent().getIntExtra(EXTRA_REQUEST_CODE, -1),
                    getIntent().getIntExtra(EXTRA_ICON, -1),
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

    public interface DialogActivityButtonClick
    {
        void onClick(int which, String[] editTexts);
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
        textview.setText(text);
        layout.setHint(hint);
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
        EditText edit = new EditText(context);
        if (text != null)
        {
            edit.setText(text);
        }
        if (hint != null)
        {
            edit.setHint(hint);
        }
        return edit;
    }

    public void makeDialog(int request, int icon, String title, String text, String[] buttons, String[] editTexts, String[] editHints, String[][] editOptions)
    {
        if (request == -1 || title == null || text == null || buttons == null || buttons.length == 0 || editTexts == null || editHints == null)
        {
            return;
        }

        final DialogActivityButtonClick onClick = (which, editTexts1) -> widgetService.asyncReport(request, new Pair<>(which, editTexts1));

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
            float margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
            params.leftMargin = (int) margin;
            params.rightMargin = (int) margin;

            editTextViews[i].setLayoutParams(params);
            container.addView(editTextViews[i]);
        }
        builder.setView(container);

        builder.setOnCancelListener(dialog -> {
            onClick.onClick(-1, getEditTexts(editTextViews));
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
            onClick.onClick(which, getEditTexts(editTextViews));
            finish();
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
