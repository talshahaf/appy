package com.appy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Created by Tal on 19/03/2018.
 */

public class FilesFragment extends MyFragment implements FileGridAdapter.ItemActionListener
{
    Button browse;
    GridView filegrid;
    FileGridAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View layout = inflater.inflate(R.layout.files_fragment, container, false);

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
        adapter.setItems(((MainActivity)getActivity()).widgetService.getPythonFiles());

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
                pythonFiles.add(new PythonFile(file, file, PythonFile.State.IDLE));
            }
            ((MainActivity)getActivity()).widgetService.addPythonFiles(pythonFiles);
            adapter.setItems(((MainActivity)getActivity()).widgetService.getPythonFiles());
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDelete(PythonFile file)
    {
        Log.d("APPY", "on delete");
        ((MainActivity)getActivity()).widgetService.removePythonFile(file);
        adapter.setItems(((MainActivity)getActivity()).widgetService.getPythonFiles());
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onRefresh(PythonFile file)
    {
        Log.d("APPY", "on refresh");
        ((MainActivity)getActivity()).widgetService.refreshPythonFile(file);
        adapter.setItems(((MainActivity)getActivity()).widgetService.getPythonFiles());
        adapter.notifyDataSetChanged();
    }
}
