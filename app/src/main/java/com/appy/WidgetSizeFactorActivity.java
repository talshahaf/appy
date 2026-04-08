package com.appy;

import android.content.Context;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

public class WidgetSizeFactorActivity extends WidgetSelectActivity
{
    public static View makeRow(Context context, String text, String hint, int inputType, Utils.RunnableArg<TextView> onSet, Utils.RunnableArg<TextView> onUnset)
    {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 4);
        LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        LinearLayout.LayoutParams unsetParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);

        View editText = DialogActivity.makeEditText(context, text, hint, inputType);
        editText.setLayoutParams(editParams);
        Button setButton = new Button(context);
        setButton.setText("Apply");
        setButton.setLayoutParams(setParams);
        Button unsetButton = new Button(context);
        unsetButton.setText("Clear");
        unsetButton.setLayoutParams(unsetParams);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            setButton.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
            unsetButton.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        }

        setButton.setOnClickListener(v -> onSet.run(DialogActivity.getEditTexts(new View[] {editText})[0]));
        unsetButton.setOnClickListener(v -> onUnset.run(DialogActivity.getEditTexts(new View[] {editText})[0]));

        container.addView(editText);
        container.addView(setButton);
        container.addView(unsetButton);
        return container;
    }
    @Override
    public void onWidgetSelected(View view, int widgetId, String widgetName)
    {
        if (widgetService == null)
        {
            return;
        }

        Float[] factors = widgetService.getWidgetSizeAndCorrectionFactors(widgetId);
        String sizeFactorText = factors[0] != null ? Utils.formatFloat(factors[0]) : "";
        String widthCorrectionFactorText = factors[1] != null ? Utils.formatFloat(factors[1]) : "";
        String heightCorrectionFactorText = factors[2] != null ? Utils.formatFloat(factors[2]) : "";

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        container.addView(makeRow(this, sizeFactorText, "Size factor", InputType.TYPE_NUMBER_FLAG_DECIMAL,
                editText -> {
                    Float v = Utils.parseFloatOrNull(editText.getText().toString());
                    if (v != null)
                    {
                        widgetService.setWidgetSizeFactorAndCorrections(widgetId, true, false, false, v, null, null);
                        updateWidgetList();
                    }
                },
                (editText) -> {
                    widgetService.setWidgetSizeFactorAndCorrections(widgetId, true, false, false, null, null, null);
                    editText.setText("");
                    updateWidgetList();
                }
        ));

        container.addView(makeRow(this, widthCorrectionFactorText, "Width correction factor", InputType.TYPE_NUMBER_FLAG_DECIMAL,
                editText -> {
                    Float v = Utils.parseFloatOrNull(editText.getText().toString());
                    if (v != null)
                    {
                        widgetService.setWidgetSizeFactorAndCorrections(widgetId, false, true, false, null, v, null);
                        updateWidgetList();
                    }
                },
                (editText) -> {
                    widgetService.setWidgetSizeFactorAndCorrections(widgetId, false, true, false, null, null, null);
                    editText.setText("");
                    updateWidgetList();
                }
        ));

        container.addView(makeRow(this, heightCorrectionFactorText, "Height correction factor", InputType.TYPE_NUMBER_FLAG_DECIMAL,
                editText -> {
                    Float v = Utils.parseFloatOrNull(editText.getText().toString());
                    if (v != null)
                    {
                        widgetService.setWidgetSizeFactorAndCorrections(widgetId, false, false, true, null, null, v);
                        updateWidgetList();
                    }
                },
                (editText) -> {
                    widgetService.setWidgetSizeFactorAndCorrections(widgetId, false, false, true, null, null, null);
                    editText.setText("");
                    updateWidgetList();
                }));

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Widget size and correction factors")
                .setMessage("Set size and correction factors for widget #" + widgetId + " (" + widgetName + ")")
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
        return "Widget Size And Correction Factors";
    }

    @Override
    public boolean hasContextMenu()
    {
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu)
    {
        getMenuInflater().inflate(R.menu.sizefactor_actions, menu);
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
            "Reset size and correction factors", "Reset widget size and correction factors for all widgets?", android.R.drawable.ic_dialog_alert,
            null, null, () -> {
                widgetService.resetWidgetSizeAndCorrectionFactors();
                updateWidgetList();
            });
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
