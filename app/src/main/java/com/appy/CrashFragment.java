package com.appy;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentTransaction;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by Tal on 19/03/2018.
 */

public class CrashFragment extends FragmentParent
{
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View layout = inflater.inflate(R.layout.fragment_configs, container, false);

        onShow();

        setHasOptionsMenu(true);
        return layout;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.crash_toolbar_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.action_export)
        {
            if (getActivity() == null)
            {
                return false;
            }

            String zipPath = new File(Widget.getPreferredScriptDirStatic(getActivity()), Constants.CRASH_ZIP_PATH).getAbsolutePath();
            String[] crashFiles = new String[Constants.CrashIndex.values().length];
            for (Constants.CrashIndex i : Constants.CrashIndex.values())
            {
                crashFiles[i.ordinal()] = Utils.getCrashPath(getActivity(), i);
            }
            try
            {
                Utils.zipWithoutPath(crashFiles, zipPath, true);
                Toast.makeText(getActivity(), "Crashes saved to "+zipPath, Toast.LENGTH_LONG).show();
            }
            catch (IOException e)
            {
                Log.e("APPY", "Failed to write crash zip", e);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onShow()
    {
        if (getActivity() == null)
        {
            return;
        }

        switchTo(new CrashListFragment(), true);
    }

    public static class CrashListFragment extends ChildFragment implements AdapterView.OnItemClickListener
    {
        ListView list;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState)
        {
            View layout = inflater.inflate(R.layout.fragment_configs_list, container, false);
            list = layout.findViewById(R.id.configs_list);
            list.setOnItemClickListener(this);

            refresh();

            return layout;
        }

        @Override
        public void onItemClick(AdapterView<?> adapter, View view, int position, long id)
        {
            ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) adapter.getItemAtPosition(position);
            CrashViewerFragment fragment = new CrashViewerFragment();
            fragment.setParent(parent);

            fragment.setTitle(item.key);
            fragment.setFile((File)item.arg);

            parent.switchTo(fragment, false);
        }

        public void refresh()
        {
            if (getActivity() == null)
            {
                return;
            }

            ArrayList<ListFragmentAdapter.Item> adapterList = new ArrayList<>();
            for (Constants.CrashIndex index : Constants.CrashIndex.values())
            {
                String path = Utils.getCrashPath(getActivity(), index);
                File file = new File(path);
                String value = file.exists() ? (file.length() + " bytes, from " + new Date(file.lastModified())) : "No file";
                adapterList.add(new ListFragmentAdapter.Item(Constants.CRASHES_FILENAMES[index.ordinal()], value, file));
            }
            list.setAdapter(new ListFragmentAdapter(getActivity(), adapterList));
        }
    }

    public static class CrashViewerFragment extends ChildFragment
    {
        String title;
        File file;
        TextView crashText;
        Button clearButton;
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState)
        {
            View layout = inflater.inflate(R.layout.fragment_crashviewer, container, false);

            crashText = layout.findViewById(R.id.crash_view);

            clearButton = layout.findViewById(R.id.crash_clear);
            clearButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    file.delete();
                    refresh();
                }
            });

            refresh();
            return layout;
        }

        public void refresh()
        {
            try
            {
                crashText.setText(file.exists() ? Utils.readFile(file) : "");
            }
            catch (IOException e)
            {
                Log.e("APPY", "Cannot read crash file: " + file.getName(), e);
            }
        }

        public void setTitle(String title)
        {
            this.title = title;
        }

        public void setFile(File file)
        {
            this.file = file;
        }
    }
}
