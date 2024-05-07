package com.appy;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AlertDialog;

import android.provider.OpenableColumns;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Created by Tal on 19/03/2018.
 */

public class FilesFragment extends MyFragment implements FileGridAdapter.ItemActionListener
{
    public static final int REQUEST_FILES = 405;

    FloatingActionButton browse;
    FloatingActionButton unknownInfo;
    GridView filegrid;
    FileGridAdapter adapter;
    Handler handler;
    public Bundle fragmentArg = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {

        View layout = inflater.inflate(R.layout.fragment_files, container, false);

        handler = new Handler();

        browse = layout.findViewById(R.id.browse);
        browse.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startActivityForResult(new Intent(getActivity(), FileBrowserActivity.class), REQUEST_FILES);
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
        onPythonFileStatusChange();

        filegrid.setAdapter(adapter);
        return layout;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_FILES && resultCode == Activity.RESULT_OK)
        {
            Log.d("APPY", "file activity result");
            String[] files = data.getStringArrayExtra(FileBrowserActivity.RESULT_FILES);
            ArrayList<PythonFile> pythonFiles = new ArrayList<>();
            for (String file : files)
            {
                pythonFiles.add(new PythonFile(file));
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

    @Override
    public void onBound()
    {
        onPythonFileStatusChange();
        checkFileRequest();
    }

    @Override
    public void setArgument(Bundle fragmentArg)
    {
        this.fragmentArg = fragmentArg;
        checkFileRequest();
    }

    public void checkFileRequest()
    {
        if (fragmentArg == null)
        {
            return;
        }
        if (getActivity() == null)
        {
            return;
        }
        if (getWidgetService() == null)
        {
            return;
        }

        Uri file = fragmentArg.getParcelable(Constants.FRAGMENT_ARG_FILEURI);
        if (file != null)
        {
            String name = Utils.getFilenameFromUri(getActivity(), file, "unnamed.py");
            File destPath = new File(getWidgetService().getPreferredScriptDir(), name);
            try
            {
                InputStream is = getActivity().getContentResolver().openInputStream(file);
                if (is != null)
                {
                    Pair<byte[], String> fileData = Utils.readAndHashFile(is, Constants.PYTHON_FILE_MAX_SIZE);
                    boolean sameData = false;
                    boolean fileExists = destPath.exists();

                    if (fileExists)
                    {
                        Pair<byte[], String> existing = Utils.readAndHashFile(destPath, Constants.PYTHON_FILE_MAX_SIZE);
                        if (existing.second.equalsIgnoreCase(fileData.second))
                        {
                            //same data
                            sameData = true;
                        }
                    }

                    Runnable copyAndImport = new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            try
                            {
                                Utils.writeFile(destPath, fileData.first);
                                getWidgetService().addPythonFileByPath(destPath.getAbsolutePath());
                            }
                            catch (IOException e)
                            {
                                Log.e("APPY", "Could not process file: " + file.getPath(), e);
                                Toast.makeText(getActivity(), "Could not process file", Toast.LENGTH_SHORT).show();
                            }
                        }
                    };

                    if (!fileExists)
                    {
                        Utils.showConfirmationDialog(getActivity(), "Import '" + name + "'?", "File will be copied to script dir", -1, "Import", "Cancel", copyAndImport);
                    }
                    else
                    {
                        if (sameData)
                        {
                            Utils.showConfirmationDialog(getActivity(), "Import '" + name + "'?", "", -1, "Import", "Cancel", new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    getWidgetService().addPythonFileByPath(destPath.getAbsolutePath());
                                }
                            });
                        }
                        else
                        {
                            Utils.showConfirmationDialog(getActivity(), "Overwrite '" + name + "'?", "File in script dir will be overwritten", -1, "Overwrite", "Cancel", copyAndImport);
                        }
                    }
                }
            }
            catch (IOException e)
            {
                Log.e("APPY", "Could not process file: " + file.getPath(), e);
                Toast.makeText(getActivity(), "Could not process file", Toast.LENGTH_SHORT).show();
            }
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
