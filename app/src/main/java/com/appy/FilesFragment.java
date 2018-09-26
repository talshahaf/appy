package com.appy;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
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
    GridView filegrid;
    FileGridAdapter adapter;
    Handler handler;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View layout = inflater.inflate(R.layout.fragment_files, container, false);

        handler = new Handler();

        browse = layout.findViewById(R.id.browse);
        browse.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startActivityForResult(new Intent(getActivity(), FileBrowserActivity.class), 0);
            }
        });

        filegrid = layout.findViewById(R.id.filegrid);

        adapter = new FileGridAdapter(getActivity(), this);
        onPythonFileStatusChange();

        filegrid.setAdapter(adapter);
        return layout;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if(resultCode == Activity.RESULT_OK)
        {
            Log.d("APPY", "file activity result");
            String[] files = data.getStringArrayExtra(FileBrowserActivity.RESULT_FILES);
            ArrayList<PythonFile> pythonFiles = new ArrayList<>();
            for(String file : files)
            {
                pythonFiles.add(new PythonFile(file, "", ""));
            }
            getWidgetService().addPythonFiles(pythonFiles);
            adapter.setItems(getWidgetService().getPythonFiles());
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDelete(final PythonFile file)
    {
        new AlertDialog.Builder(getActivity())
                .setTitle("Confirm Delete")
                .setMessage("Are you sure?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int whichButton)
                    {
                        Log.d("APPY", "on delete");
                        getWidgetService().removePythonFile(file);
                        adapter.setItems(getWidgetService().getPythonFiles());
                        adapter.notifyDataSetChanged();
                    }
                }).setNegativeButton(android.R.string.no, null).show();
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

        builder.setTitle(new File(file.path).getName() + (file.lastErrorDate != null ? " from " + file.lastErrorDate : ""));

        builder.setNeutralButton("OK", null);

        View layout = LayoutInflater.from(getActivity()).inflate(R.layout.alert_error_view, null);

        if(file.lastError != null)
        {
            TextView message = layout.findViewById(R.id.message);
            message.setText(file.lastError+"\n\n");

            ScrollView vertical = layout.findViewById(R.id.verticalscroll);
            vertical.fullScroll(View.FOCUS_DOWN);
        }

        builder.setView(layout);

        AlertDialog alert = builder.create();
        alert.show();
    }

    public void onPythonFileStatusChange()
    {
        if(getWidgetService() == null)
        {
            return;
        }
        adapter.setItems(getWidgetService().getPythonFiles());
        adapter.notifyDataSetChanged();
    }
}
