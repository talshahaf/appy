package com.appy;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Tal on 23/03/2018.
 */

public class FileBrowserActivity extends AppCompatActivity
{
    public static final String RESULT_FILES = "RESULT_FILES";
    public static final int REQUEST_PERMISSION_STORAGE = 101;

    FileBrowserAdapter adapter;
    ListView list;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.filebrowser);

        list = findViewById(R.id.filelist);
        setSupportActionBar((Toolbar)findViewById(R.id.toolbar));

        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_STORAGE);
        }
        else
        {
            getDirFromRoot(root);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    getDirFromRoot(root);
                }
                else
                {
                    Toast.makeText(this, "Cannot open file browser", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            }
        }
    }

    private String root = Environment.getExternalStorageDirectory().getPath();

    //get directories and files from selected path
    public void getDirFromRoot(String path)
    {
        boolean isRoot = path.equalsIgnoreCase(root);

        File current = new File(path);
        File[] filesArray = current.listFiles();

        //sorting file list in alphabetical order
        Arrays.sort(filesArray);
        adapter = new FileBrowserAdapter(this, filesArray, current, isRoot);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                if(adapter.isParent(position))
                {
                    getDirFromRoot(adapter.getCurrent().getParentFile().getAbsolutePath());
                }
                else
                {
                    File item = (File) adapter.getItem(position);
                    if (item.isDirectory())
                    {
                        getDirFromRoot(item.getAbsolutePath());
                    }
                    else
                    {
                        returnFiles(new String[]{item.getAbsolutePath()});
                    }
                }
            }
        });
    }

    public void returnFiles(String[] files)
    {
        Intent intent = new Intent();
        intent.putExtra(RESULT_FILES, files);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.action_select:
            {
                ArrayList<File> selected = adapter.getSelected();
                String[] files = new String[selected.size()];
                for(int i = 0; i < files.length; i++)
                {
                    files[i] = selected.get(i).getAbsolutePath();
                }
                returnFiles(files);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.filebrowser_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }
}
