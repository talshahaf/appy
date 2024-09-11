package com.appy;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

public class FileEditorActivity extends AppCompatActivity
{
    public static final String FILE_EDITOR_PATH_EXTRA = "FILE_EDITOR_PATH_EXTRA";

    Toolbar toolbar;
    EditText content;
    TextView numbers;
    File file;
    String originalContent = "";
    boolean unsaved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fileeditor);

        toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        content = findViewById(R.id.content);
        numbers = findViewById(R.id.linenumbers);

        content.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {

            }

            @Override
            public void afterTextChanged(Editable s)
            {
                String text = s.toString();
                int lines = 1 + text.length() - text.replace("\n", "").length();
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= lines; i++)
                {
                    sb.append(i);
                    sb.append('\n');
                }
                numbers.setText(sb.toString());

                unsaved = true;
                updateTitle();
            }
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();

        String path = getIntent().getStringExtra(FILE_EDITOR_PATH_EXTRA);
        if (path == null)
        {
            Log.e("APPY", "null file path");
            Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (file != null && new File(path).getPath().equals(file.getPath()))
        {
            //actual resume
            return;
        }

        // load new file
        file = new File(path);

        try
        {
            Pair<String, String> result = Utils.readAndHashFileAsString(file, Constants.PYTHON_FILE_MAX_SIZE);
            originalContent = result.first;

            toolbar.setTitle(file.getName());
            content.setText(originalContent);
            unsaved = false;
            updateTitle();
        }
        catch (IOException e)
        {
            Log.e("APPY", "Could not open file " + path, e);
            Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu)
    {
        getMenuInflater().inflate(R.menu.fileeditor_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.action_save)
        {
            save();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateTitle()
    {
        toolbar.setTitle(file.getName() + (unsaved ? "*" : ""));
    }

    private void save()
    {
        String newcontent = content.getText().toString();
        try
        {
            Utils.writeFile(file, newcontent);
            originalContent = newcontent;
            unsaved = false;
            updateTitle();
        }
        catch (IOException e)
        {
            Log.e("APPY", "Failed to write to " + file.getPath(), e);
            Toast.makeText(this, "Failed to write", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if(originalContent.equals(content.getText().toString()))
        {
            super.onBackPressed();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Unsaved changes")
                .setMessage("Discard unsaved changes?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int id)
                    {
                        FileEditorActivity.super.onBackPressed();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
