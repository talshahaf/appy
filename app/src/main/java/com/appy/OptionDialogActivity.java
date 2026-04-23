package com.appy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

public class OptionDialogActivity extends Activity
{
    //SPECIAL ACTIONS ONLY
    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_CANCEL_ACTION = "EXTRA_CANCEL_ACTION";
    public static final String EXTRA_OPTION_TEXTS = "EXTRA_BUTTON_TEXTS";
    public static final String EXTRA_OPTION_ACTIONS = "EXTRA_BUTTON_ACTIONS";
    public static final String EXTRA_BUTTON_CONFIRM = "EXTRA_BUTTON_CONFIRM";
    public static final String EXTRA_REQUEST_CODE = "EXTRA_REQUEST_CODE";

    private Widget widgetService;
    int doneRequestCode = -1;

    public static class ShowTextAction implements Parcelable
    {
        String title;
        String text;
        boolean scrollDown;

        public ShowTextAction(String title, String text, boolean scrollDown)
        {
            this.title = title;
            this.text = text;
            this.scrollDown = scrollDown;
        }

        protected ShowTextAction(Parcel in)
        {
            title = in.readString();
            text = in.readString();
            scrollDown = in.readInt() == 1;
        }

        public static final Creator<ShowTextAction> CREATOR = new Creator<>()
        {
            @Override
            public ShowTextAction createFromParcel(Parcel in)
            {
                return new ShowTextAction(in);
            }

            @Override
            public ShowTextAction[] newArray(int size)
            {
                return new ShowTextAction[size];
            }
        };

        @Override
        public int describeContents()
        {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags)
        {
            dest.writeString(title);
            dest.writeString(text);
            dest.writeInt(scrollDown ? 1 : 0);
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            widgetService = ((Widget.LocalBinder) service).getService();

            int request = getIntent().getIntExtra(EXTRA_REQUEST_CODE, -1);
            if (request != -1)
            {
                doneRequestCode = widgetService.generateRequestCode();
                widgetService.asyncReport(request, doneRequestCode);
                show();
            }
        }

        public void onServiceDisconnected(ComponentName className)
        {
            widgetService = null;
        }
    };

    private void show()
    {
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String[] options = getIntent().getStringArrayExtra(EXTRA_OPTION_TEXTS);
        String[] confirm = getIntent().getStringArrayExtra(EXTRA_BUTTON_CONFIRM);

        if (title == null || options == null || (confirm != null && confirm.length != options.length))
        {
            finish();
            return;
        }

        Parcelable[] actions = null;
        Parcelable cancelAction = null;

        if (doneRequestCode == -1)
        {
            actions = getIntent().getParcelableArrayExtra(EXTRA_OPTION_ACTIONS);
            cancelAction = getIntent().getParcelableExtra(EXTRA_CANCEL_ACTION);

            if (actions == null || options.length != actions.length)
            {
                finish();
                return;
            }
        }

        Parcelable[] actions_ = actions;
        Parcelable cancelAction_ = cancelAction;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setOnCancelListener(dialog -> dialogResult(dialog, actions_, cancelAction_, -1, null));
        builder.setTitle(title);
        builder.setItems(options, null);

        AlertDialog dialog = builder.create();
        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> dialogResult(dialog, actions_, cancelAction_, position, confirm));
        dialog.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        doneRequestCode = -1;
        if (!getIntent().hasExtra(EXTRA_REQUEST_CODE))
        {
            show();
        }
        else
        {
            doBindService();
        }
    }

    public void dialogResult(DialogInterface dialog, Parcelable[] actions, Parcelable cancelAction, int selected, String[] confirm)
    {
        Runnable act = () -> {
            if (doneRequestCode != -1)
            {
                report(selected);
                finish();
            }
            else
            {
                Parcelable action = selected != -1 ? actions[selected] : cancelAction;
                switch (action)
                {
                    case Intent intent ->
                    {
                        Widget.startService(this, intent);
                        finish();
                    }
                    case ShowTextAction showTextAction ->
                    {
                        Utils.showTextDialog(this, showTextAction.title, showTextAction.text, "Close", null, null, this::finish, showTextAction.scrollDown);
                    }
                    case null, default -> finish();
                }
            }
        };
        if (confirm != null && confirm[selected] != null)
        {
            Utils.showConfirmationDialog(this, confirm[selected], "", android.R.drawable.ic_dialog_alert,
                null, null, act);
        }
        else
        {
            act.run();
            dialog.dismiss();
        }
    }

    private void report(int result)
    {
        if (widgetService != null && doneRequestCode != -1)
        {
            widgetService.asyncReport(doneRequestCode, result);
            doneRequestCode = -1;
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        report(-1);
        doUnbindService();
    }

    @Override
    protected void onStop()
    {
        finish();
        super.onStop();
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
}
