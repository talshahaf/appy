package com.appy;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputLayout;

public class WidgetDrawingSettingsActivity extends WidgetSelectActivity
{
    public static View makeRow(Context context, String text, String hint, int inputType, Utils.RunnableArg<TextView> onSet, Utils.RunnableArg<TextView> onUnset)
    {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2);
        LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        LinearLayout.LayoutParams unsetParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);

        View editText = DialogActivity.makeEditText(context, text, hint, inputType);
        editText.setLayoutParams(editParams);

        int buttonStyle = R.style.secondary_btn_sml;
        Button setButton = new Button(new ContextThemeWrapper(context, buttonStyle), null, buttonStyle);
        setButton.setText("Apply");
        setButton.setLayoutParams(setParams);
        Button unsetButton = new Button(new ContextThemeWrapper(context, buttonStyle), null, buttonStyle);
        unsetButton.setText("Clear");
        unsetButton.setLayoutParams(unsetParams);

        setButton.setOnClickListener(v -> onSet.run(DialogActivity.getEditTexts(new View[] {editText})[0]));
        unsetButton.setOnClickListener(v -> onUnset.run(DialogActivity.getEditTexts(new View[] {editText})[0]));

        container.addView(editText);
        container.addView(setButton);
        container.addView(unsetButton);
        return container;
    }

    public static View makeSwitch(Context context, Boolean checked, String text, Utils.RunnableArg<Boolean> onCheckedChange)
    {
        String[] options = new String[] {"unset", "yes", "no"};
        Boolean[] values = new Boolean[] {null, true, false};

        String prevText = options[Utils.indexOf(values, checked)];

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        TextView title = new TextView(context);
        title.setGravity(Gravity.CENTER);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2));
        title.setText(text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);

        TextInputLayout dropDown = new TextInputLayout(context, null, R.style.ExposedDropdownMenu);
        AutoCompleteTextView textview = new AutoCompleteTextView(context);
        textview.setText(prevText);
        textview.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        textview.setInputType(EditorInfo.TYPE_NULL);
        textview.setAdapter(new ArrayAdapter<>(context, R.layout.dropdown_text_item, options));
        textview.setThreshold(0);
        textview.setOnClickListener(v -> ((AutoCompleteTextView)v).showDropDown());
        textview.setOnFocusChangeListener((v, hasFocus) -> ((AutoCompleteTextView)v).showDropDown());
        textview.setGravity(Gravity.CENTER);
        dropDown.setHintEnabled(false);
        dropDown.addView(textview);
        dropDown.setGravity(Gravity.CENTER);
        dropDown.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        TextView empty = new TextView(context);
        empty.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        layout.addView(title);
        layout.addView(dropDown);
        layout.addView(empty);

        textview.setOnItemClickListener((adapterView, view, i, l) -> {
            TextView textView = (TextView)view;
            String chosen = textView.getText().toString();
            onCheckedChange.run(values[Utils.indexOf(options, chosen)]);
        });
        return layout;
    }

    @Override
    public void onWidgetSelected(View view, int widgetId, String widgetName)
    {
        if (widgetService == null)
        {
            return;
        }

        Widget.DrawingSettings drawingSettings = widgetService.getWidgetDrawingSettings(widgetId);
        String sizeFactorText = drawingSettings.sizeFactor != null ? Utils.formatFloat(drawingSettings.sizeFactor) : "";
        String widthCorrectionFactorText = drawingSettings.widthCorrectionFactor != null ? Utils.formatFloat(drawingSettings.widthCorrectionFactor) : "";
        String heightCorrectionFactorText = drawingSettings.heightCorrectionFactor != null ? Utils.formatFloat(drawingSettings.heightCorrectionFactor) : "";
        Boolean debugBoundingBoxes = drawingSettings.debugBoundingBoxes;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        container.addView(makeRow(this, sizeFactorText, "Size factor", InputType.TYPE_NUMBER_FLAG_DECIMAL,
                editText -> {
                    Float v = Utils.parseFloatOrNull(editText.getText().toString());
                    if (v != null)
                    {
                        Widget.DrawingSettings settings = new Widget.DrawingSettings();
                        settings.sizeFactor = v;
                        widgetService.setWidgetDrawingSettings(widgetId, true, false, false, false, settings);
                        updateWidgetList();
                    }
                },
                (editText) -> {
                    widgetService.setWidgetDrawingSettings(widgetId, true, false, false, false, new Widget.DrawingSettings());
                    editText.setText("");
                    updateWidgetList();
                }
        ));

        container.addView(makeRow(this, widthCorrectionFactorText, "Width correction factor", InputType.TYPE_NUMBER_FLAG_DECIMAL,
                editText -> {
                    Float v = Utils.parseFloatOrNull(editText.getText().toString());
                    if (v != null)
                    {
                        Widget.DrawingSettings settings = new Widget.DrawingSettings();
                        settings.widthCorrectionFactor = v;
                        widgetService.setWidgetDrawingSettings(widgetId, false, true, false, false, settings);
                        updateWidgetList();
                    }
                },
                (editText) -> {
                    widgetService.setWidgetDrawingSettings(widgetId, false, true, false, false, new Widget.DrawingSettings());
                    editText.setText("");
                    updateWidgetList();
                }
        ));

        container.addView(makeRow(this, heightCorrectionFactorText, "Height correction factor", InputType.TYPE_NUMBER_FLAG_DECIMAL,
                editText -> {
                    Float v = Utils.parseFloatOrNull(editText.getText().toString());
                    if (v != null)
                    {
                        Widget.DrawingSettings settings = new Widget.DrawingSettings();
                        settings.heightCorrectionFactor = v;
                        widgetService.setWidgetDrawingSettings(widgetId, false, false, true, false, settings);
                        updateWidgetList();
                    }
                },
                (editText) -> {
                    widgetService.setWidgetDrawingSettings(widgetId, false, false, true, false, new Widget.DrawingSettings());
                    editText.setText("");
                    updateWidgetList();
                }));

        container.addView(makeSwitch(this, debugBoundingBoxes, "Debug bounding boxes: ", (checked) -> {
            if (checked != null)
            {
                Widget.DrawingSettings settings = new Widget.DrawingSettings();
                settings.debugBoundingBoxes = checked;
                widgetService.setWidgetDrawingSettings(widgetId, false, false, false, true, settings);
                updateWidgetList();
            }
            else
            {
                widgetService.setWidgetDrawingSettings(widgetId, false, false, false, true, new Widget.DrawingSettings());
                updateWidgetList();
            }
        }));

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Widget Drawing Settings")
                .setMessage("Set drawing settings for widget #" + widgetId + " (" + widgetName + ")")
                .setView(container)
                .setNeutralButton("Close", null)
                .setOnDismissListener(dialog -> {
                    if (view == null)
                    {
                        //selected from intent
                        finish();
                    }
                });

        builder.show();
    }

    @Override
    public String getToolbarHeader()
    {
        return "Widget Drawing Settings";
    }

    @Override
    public boolean hasContextMenu()
    {
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu)
    {
        getMenuInflater().inflate(R.menu.drawingsettings_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (widgetService == null)
        {
            return super.onOptionsItemSelected(item);
        }

        if (item.getItemId() == R.id.action_resetall)
        {
            Utils.showConfirmationDialog(this,
            "Reset drawing settings", "Reset drawing settings for all widgets?", android.R.drawable.ic_dialog_alert,
            null, null, () -> {
                widgetService.resetAllWidgetDrawingSettings();
                updateWidgetList();
            });
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
