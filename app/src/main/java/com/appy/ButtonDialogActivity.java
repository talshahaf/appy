package com.appy;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

public class ButtonDialogActivity extends Activity
{
    //SPECIAL ACTIONS ONLY
    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_CANCEL_ACTION = "EXTRA_CANCEL_ACTION";
    public static final String EXTRA_BUTTON_TEXTS = "EXTRA_BUTTON_TEXTS";
    public static final String EXTRA_BUTTON_ACTIONS = "EXTRA_BUTTON_ACTIONS";
    public static final String EXTRA_BUTTON_CONFIRM = "EXTRA_BUTTON_CONFIRM";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String[] buttons = getIntent().getStringArrayExtra(EXTRA_BUTTON_TEXTS);
        Parcelable[] actions_ = getIntent().getParcelableArrayExtra(EXTRA_BUTTON_ACTIONS);
        Intent cancelAction = getIntent().getParcelableExtra(EXTRA_CANCEL_ACTION);
        String[] confirm = getIntent().getStringArrayExtra(EXTRA_BUTTON_CONFIRM);

        if (title == null || buttons == null || actions_ == null || buttons.length != actions_.length || (confirm != null && confirm.length != buttons.length))
        {
            finish();
            return;
        }

        Intent[] actions = new Intent[actions_.length];
        for (int i = 0; i < actions.length; i++)
        {
            actions[i] = (Intent)actions_[i];
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setOnCancelListener(dialog -> dialogResult(cancelAction, null));
        builder.setTitle(title);
        builder.setItems(buttons, (dialog, which) -> dialogResult(actions[which], confirm == null ? null : confirm[which]));
        builder.show();
    }

    public void dialogResult(Intent action, String confirm)
    {
        if (action == null)
        {
            finish();
            return;
        }

        Runnable act = () -> {
            Widget.startService(this, action);
            finish();
        };
        if (confirm != null)
        {
            Utils.showConfirmationDialog(this, confirm, "", android.R.drawable.ic_dialog_alert,
                null, null, act, this::finish);
        }
        else
        {
            act.run();
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    protected void onStop()
    {
        finish();
        super.onStop();
    }
}
