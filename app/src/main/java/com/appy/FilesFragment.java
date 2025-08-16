package com.appy;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AlertDialog;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by Tal on 19/03/2018.
 */

public class FilesFragment extends MyFragment implements FileGridAdapter.ItemActionListener
{
    FloatingActionButton browse;
    FloatingActionButton unknownInfo;
    GridView filegrid;
    FileGridAdapter adapter;
    Handler handler;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        super.onCreateView(inflater, container, savedInstanceState);

        View layout = inflater.inflate(R.layout.fragment_files, container, false);

        handler = new Handler();

        browse = layout.findViewById(R.id.browse);
        browse.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent intent = new Intent(getActivity(), FileBrowserActivity.class);
                intent.putExtra(FileBrowserActivity.REQUEST_ALLOW_RETURN_MULTIPLE, true);
                intent.putExtra(FileBrowserActivity.REQUEST_SPECIFIC_EXTENSION_CONFIRMATION, ".py");
                requestActivityResult(intent);
            }
        });

        unknownInfo = layout.findViewById(R.id.unknown_info);
        unknownInfo.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onInfo(getWidgetService().unknownPythonFile);
            }
        });

        filegrid = layout.findViewById(R.id.filegrid);
        adapter = new FileGridAdapter(getActivity(), this);
        filegrid.setAdapter(adapter);
        return layout;
    }

    @Override
    public void onActivityResult(Intent data)
    {
        Log.d("APPY", "file activity result");
        String[] files = data.getStringArrayExtra(FileBrowserActivity.RESULT_FILES);
        ArrayList<PythonFile> pythonFiles = new ArrayList<>();
        if (files != null)
        {
            for (String file : files)
            {
                pythonFiles.add(new PythonFile(file));
            }
        }
        getWidgetService().addPythonFiles(pythonFiles);
        adapter.setItems(getWidgetService().getPythonFiles());
        adapter.notifyDataSetChanged();

        //give it time to load
        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                adapter.setItems(getWidgetService().getPythonFiles());
                adapter.notifyDataSetChanged();
            }
        }, 500);
    }

    @Override
    public void onDelete(final PythonFile file)
    {
        Utils.showConfirmationDialog(getActivity(),
                "Confirm Delete", "Are you sure?", android.R.drawable.ic_dialog_alert,
                null, null, new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Log.d("APPY", "on delete");
                        getWidgetService().removePythonFile(file);
                        adapter.setItems(getWidgetService().getPythonFiles());
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    @Override
    public void onRefresh(final PythonFile file)
    {
        Log.d("APPY", "on refresh");
        getWidgetService().refreshPythonFile(file);
        adapter.setStateOverride(file, PythonFile.State.RUNNING);
        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                adapter.clearStateOverride(file);
                adapter.notifyDataSetChanged();
            }
        }, 500);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onInfo(PythonFile file)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle(new File(file.path).getName() + (file.lastErrorDate != null ? " from " + file.lastErrorDate.toString() : ""));
        builder.setNeutralButton("OK", null);
        builder.setNegativeButton("Clear", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                getWidgetService().clearFileError(file);
            }
        });

        View layout = LayoutInflater.from(getActivity()).inflate(R.layout.alert_error_view, null);
        TextView message = layout.findViewById(R.id.message);
        String error = "No errors\n\n";
        if (!file.lastError.isEmpty())
        {
            error = file.lastError + "\n\n";
        }

        message.setText(file.path + "\n\n" + error);

        builder.setView(layout);

        AlertDialog alert = builder.create();
        alert.setOnShowListener(new DialogInterface.OnShowListener()
        {
            @Override
            public void onShow(DialogInterface dialog)
            {
                ScrollView vertical = layout.findViewById(R.id.verticalscroll);
                vertical.fullScroll(View.FOCUS_DOWN);
            }
        });
        alert.show();
    }

    private boolean resumedAndBound = false;
    private boolean handledArgument = false;
    @Override
    public void onResumedAndBound()
    {
        resumedAndBound = true;
        onPythonFileStatusChange();
        checkFileRequest();
    }

    @Override
    public void onArgument()
    {
        if (resumedAndBound)
        {
            handledArgument = false;
            checkFileRequest();
        }
    }

    public void checkFileRequest()
    {
        if (fragmentArg == null)
        {
            return;
        }

        if (handledArgument)
        {
            return;
        }

        handledArgument = true;

        int op = fragmentArg.getInt(Constants.FRAGMENT_ARG_FILEOP, -1);
        Uri file = fragmentArg.getParcelable(Constants.FRAGMENT_ARG_FILEURI);
        if (file == null)
        {
            return;
        }

        if (op == Constants.FRAGMENT_ARG_FILEOP_IMPORT)
        {
            PythonFileImport.importPythonFromExternalUri(getActivity(), getWidgetService(), file, null);
        }
        else if (op == Constants.FRAGMENT_ARG_FILEOP_EDIT)
        {
            FileEditorActivity.launch(getActivity(), file.getPath());
        }
        else
        {
            Log.d("APPY", "unknown file op: " + op);
        }
    }

    public void onPythonFileStatusChange()
    {
        if (getActivity() == null)
        {
            return;
        }
        if (getWidgetService() == null)
        {
            return;
        }
        adapter.setItems(getWidgetService().getPythonFiles());
        adapter.notifyDataSetChanged();
    }
}
