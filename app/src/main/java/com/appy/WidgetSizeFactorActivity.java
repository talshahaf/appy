package com.appy;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

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

        Float sizeFactor = widgetService.getWidgetSizeFactor(widgetId);
        String sizeFactorText = sizeFactor == null ? "1" : Utils.formatFloat(sizeFactor);

        EditText editText = new EditText(this);
        editText.setText(sizeFactorText);
        editText.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.addView(editText);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Widget size factor")
                .setMessage("Set size factor for widget #" + widgetId + " (" + widgetName + ")")
                .setView(container)
                .setPositiveButton("Set", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        try
                        {
                            String factorText = editText.getText().toString();
                            Float newFactor = factorText.isEmpty() ? null : Float.parseFloat(factorText);
                            widgetService.setWidgetSizeFactor(widgetId, newFactor);
                            updateWidgetList();
                        }
                        catch (NumberFormatException e)
                        {

                        }
                    }
                })
                .setNeutralButton("Unset", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        widgetService.setWidgetSizeFactor(widgetId, null);
                        updateWidgetList();
                    }
                })
                .setNegativeButton("Cancel", null);

        builder.show();
    }

    @Override
    protected String elementValueFormat(int widgetId, DictObj.Dict widgetProps)
    {
        String value = super.elementValueFormat(widgetId, widgetProps);
        Float sizeFactor = widgetService.getWidgetSizeFactor(widgetId);
        if (sizeFactor != null)
        {
            return value + ": " + Utils.formatFloat(sizeFactor);
        }
        return value;
    }

    @Override
    public String getToolbarHeader()
    {
        return "Widget Size Factors";
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
            "Reset size factors", "Reset all widget size factors?", android.R.drawable.ic_dialog_alert,
            null, null, new Runnable()
            {
                @Override
                public void run()
                {
                    widgetService.resetWidgetSizeFactors();
                    updateWidgetList();
                }
            });
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
