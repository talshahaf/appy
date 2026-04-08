package com.appy;

import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

public class WidgetSizeFactorActivity extends WidgetSelectActivity
{
    @Override
    public void onWidgetSelected(View view, int widgetId, String widgetName)
    {
        if (widgetService == null)
        {
            return;
        }

        Float[] factors = widgetService.getWidgetSizeAndCorrectionFactors(widgetId);
        String sizeFactorText = factors[0] != null ? Utils.formatFloat(factors[0]) : "1";
        String widthCorrectionFactorText = factors[1] != null ? Utils.formatFloat(factors[1]) : "1";
        String heightCorrectionFactorText = factors[2] != null ? Utils.formatFloat(factors[2]) : "1";

        View sizeEditText = DialogActivity.makeEditText(this, sizeFactorText, "Size factor", InputType.TYPE_NUMBER_FLAG_DECIMAL);
        View widthEditText = DialogActivity.makeEditText(this, widthCorrectionFactorText, "Width correction factor", InputType.TYPE_NUMBER_FLAG_DECIMAL);
        View heightEditText = DialogActivity.makeEditText(this, heightCorrectionFactorText, "Height correction factor", InputType.TYPE_NUMBER_FLAG_DECIMAL);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.addView(sizeEditText);
        container.addView(widthEditText);
        container.addView(heightEditText);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Widget size and correction factors")
                .setMessage("Set size and correction factors for widget #" + widgetId + " (" + widgetName + ")")
                .setView(container)
                .setPositiveButton("Set", (dialog, which) -> {
                    try
                    {
                        String[] texts = DialogActivity.getEditTexts(new View[] { sizeEditText, widthEditText, heightEditText });
                        String sizeText = texts[0];
                        String widthText = texts[1];
                        String heightText = texts[2];
                        Float newSizeFactor = sizeText.isEmpty() ? null : Float.parseFloat(sizeText);
                        Float newWidthFactor = widthText.isEmpty() ? null : Float.parseFloat(widthText);
                        Float newHeightFactor = heightText.isEmpty() ? null : Float.parseFloat(heightText);
                        widgetService.setWidgetSizeFactorAndCorrections(widgetId, newSizeFactor, newWidthFactor, newHeightFactor);
                        updateWidgetList();
                    }
                    catch (NumberFormatException ignored)
                    {

                    }
                })
                .setNeutralButton("Unset", (dialog, which) -> {
                    widgetService.setWidgetSizeFactorAndCorrections(widgetId, null, null, null);
                    updateWidgetList();
                })
                .setNegativeButton("Cancel", null)
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
