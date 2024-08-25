package com.appy;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class PythonFileImport
{
    interface DialogRunnable
    {
        void run(Context context, String name);
    }

    public static void showRenamableDialog(Context context, String name, DialogRunnable yesAction, Runnable otherAction)
    {
        String ext = ".py";

        int lastdot = name.lastIndexOf('.');
        String noext = lastdot == -1 ? name : name.substring(0, lastdot);

        EditText editText = new EditText(context);
        EditText extview = new EditText(context);
        TextView textView = new TextView(context);
        editText.setText(noext);
        extview.setText(ext);
        textView.setText("will be copied to script dir.");

        extview.setInputType(EditorInfo.TYPE_NULL);
        extview.setBackground(null);

        editText.setPadding(editText.getPaddingLeft(), extview.getPaddingTop(), 0, extview.getPaddingBottom());
        extview.setPadding(0, extview.getPaddingTop(), 20, extview.getPaddingBottom());

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);
        container.addView(editText);
        container.addView(extview);
        container.addView(textView);

        DialogInterface.OnClickListener yesClick = new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                yesAction.run(context, editText.getText().toString().strip() + ext);
            }
        };


        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle("Import File?")
                .setPositiveButton("Import", yesClick)
                .setNegativeButton("Cancel", otherAction == null ? null : new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        otherAction.run();
                    }
                })
                .setOnCancelListener(otherAction == null ? null : new DialogInterface.OnCancelListener()
                {
                    @Override
                    public void onCancel(DialogInterface dialog)
                    {
                        otherAction.run();
                    }
                })
                .setView(container);

        builder.show();
    }

    public static void importPythonFromExternalUri(Context context, Widget widgetService, Uri uri, Runnable onDone)
    {
        if (context == null || widgetService == null || uri == null)
        {
            return;
        }

        String defaultname = Utils.getFilenameFromUri(context, uri, "unnamed.py");

        showRenamableDialog(context, defaultname, new DialogRunnable()
        {
            @Override
            public void run(Context context, String name)
            {
                File destPath = new File(widgetService.getPreferredScriptDir(), name);
                try
                {
                    InputStream is = context.getContentResolver().openInputStream(uri);
                    if (is != null)
                    {
                        Pair<byte[], String> fileData = Utils.readAndHashFile(is, Constants.PYTHON_FILE_MAX_SIZE);
                        if (destPath.exists())
                        {
                            Pair<byte[], String> existing = Utils.readAndHashFile(destPath, Constants.PYTHON_FILE_MAX_SIZE);
                            if (existing.second.equalsIgnoreCase(fileData.second))
                            {
                                //same file, just refresh
                                widgetService.addPythonFileByPath(destPath.getAbsolutePath());

                                if (onDone != null)
                                {
                                    onDone.run();
                                }
                            }
                            else
                            {
                                //different files, ask again.
                                Utils.showConfirmationDialog(context, "Overwrite '" + name + "'?", "File in script dir will be overwritten", -1, "Overwrite", "Cancel", new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        //overwrite
                                        try
                                        {
                                            Utils.writeFile(destPath, fileData.first);
                                            widgetService.addPythonFileByPath(destPath.getAbsolutePath());
                                        }
                                        catch (IOException e)
                                        {
                                            Log.e("APPY", "Could not process file: " + destPath.getPath(), e);
                                            Toast.makeText(context, "Could not process file", Toast.LENGTH_SHORT).show();
                                        }

                                        if (onDone != null)
                                        {
                                            onDone.run();
                                        }
                                    }
                                }, onDone);
                            }
                        }
                        else
                        {
                            // file does not exist, copy and import
                            Utils.writeFile(destPath, fileData.first);
                            widgetService.addPythonFileByPath(destPath.getAbsolutePath());

                            if (onDone != null)
                            {
                                onDone.run();
                            }
                        }
                    }
                }
                catch (IOException e)
                {
                    Log.e("APPY", "Could not process file: " + destPath.getPath(), e);
                    Toast.makeText(context, "Could not process file", Toast.LENGTH_SHORT).show();
                    if (onDone != null)
                    {
                        onDone.run();
                    }
                }
            }
        }, onDone);
    }
}
