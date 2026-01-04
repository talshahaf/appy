package com.appy;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Tal on 23/03/2018.
 */

public class FileBrowserActivity extends AppCompatActivity implements FileBrowserAdapter.OnCheckedChanged
{
    public static final String RESULT_FILES = "RESULT_FILES";
    public static final String REQUEST_ALLOW_RETURN_MULTIPLE = "REQUEST_ALLOW_RETURN_MULTIPLE";
    public static final String REQUEST_SPECIFIC_EXTENSION_CONFIRMATION = "REQUEST_SPECIFIC_EXTENSION_CONFIRMATION";
    public static final int REQUEST_PERMISSION_STORAGE = 101;
    public static final int REQUEST_ALL_STORAGE = 102;
    public static final int MEDIA_STORAGE_INDEX = 1;
    public static final int SHARED_STORAGE_INDEX = 5;

    FileBrowserAdapter adapter;
    ListView list;
    LinkedList<String> history = new LinkedList<>();
    Toolbar toolbar;
    TextView bottomtext;
    TutorialOverlayView tutorialOverlay;
    Tutorial tutorial;
    boolean copying = false;
    boolean cutting = false;
    HashMap<String, File> selected = new HashMap<>();
    boolean selectingEnabled = true;

    boolean cantViewSharedStorage;
    boolean allowReturnMultipleFiles;
    String specificExtensionConfirmation;

    private String[] preset_names;
    private String[] preset_paths;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.filebrowser);

        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true)
        {
            @Override
            public void handleOnBackPressed()
            {
                backPressed();
            }
        });

        allowReturnMultipleFiles = getIntent().getBooleanExtra(REQUEST_ALLOW_RETURN_MULTIPLE, true);
        specificExtensionConfirmation = getIntent().getStringExtra(REQUEST_SPECIFIC_EXTENSION_CONFIRMATION);

        list = findViewById(R.id.filelist);
        toolbar = findViewById(R.id.toolbar);
        bottomtext = findViewById(R.id.bottomtext);
        bottomtext.setVisibility(View.INVISIBLE);
        tutorialOverlay = findViewById(R.id.tutorial_overlay_view);
        setSupportActionBar(toolbar);

        tutorial = new Tutorial();
        tutorial.fillFileBrowserComponents(this, tutorialOverlay, toolbar, list);

        File[] mediaDirs = getExternalMediaDirs();
        String mediaDir = null;
        if (mediaDirs.length > 0 && mediaDirs[0] != null)
        {
            mediaDir = mediaDirs[0].getAbsolutePath();
        }

        String externalFilesDir = null;
        File externalFiles = getExternalFilesDir(null);
        if (externalFiles != null)
        {
            externalFilesDir = externalFiles.getAbsolutePath();
        }

        List<Pair<String, String>> presets = new ArrayList<>();
        presets.add(new Pair<>("examples", new File(getFilesDir(), "examples").getAbsolutePath()));
        presets.add(new Pair<>("app media storage (preferred for script files)", mediaDir));
        presets.add(new Pair<>("app scoped storage", externalFilesDir));
        presets.add(new Pair<>("app files dir", getFilesDir().getAbsolutePath()));
        presets.add(new Pair<>("app cache dir (for shared resources)", getCacheDir().getAbsolutePath()));
        presets.add(new Pair<>("shared storage", Environment.getExternalStorageDirectory().getAbsolutePath()));

        int filteredSize = 0;
        for (Pair<String, String> preset : presets)
        {
            if (preset.second != null)
            {
                filteredSize++;
            }
        }

        preset_names = new String[filteredSize];
        preset_paths = new String[filteredSize];

        int filteredIndex = 0;
        for (Pair<String, String> preset : presets)
        {
            if (preset.second != null)
            {
                preset_names[filteredIndex] = preset.first;
                preset_paths[filteredIndex] = preset.second;
                filteredIndex++;
            }
        }

        cantViewSharedStorage = !Constants.compiledWithManagerStorage(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;

        String[] permissions;
        if (Build.VERSION.SDK_INT <= 32)
        {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }
        else
        {
            permissions = new String[]{};
            //Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES};
        }

        boolean all = true;
        for (String permission : permissions)
        {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
            {
                all = false;
                break;
            }
        }

        if (!all)
        {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_STORAGE);
        }
        else
        {
            requestAllStorage();
        }
    }

    public void requestAllStorage()
    {
        if (Constants.compiledWithManagerStorage(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager())
        {
            Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
            startActivityForResult(intent, REQUEST_ALL_STORAGE);
        }
        else
        {
            tutorial.startFileBrowser();
            getStartDir();
        }
    }

    public boolean getStartDir()
    {
        String startDir = preset_paths[cantViewSharedStorage ? MEDIA_STORAGE_INDEX : SHARED_STORAGE_INDEX];
        if (!getDirFromRoot(startDir, false))
        {
            startDir = preset_paths[MEDIA_STORAGE_INDEX];
            return getDirFromRoot(startDir, false);
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ALL_STORAGE)
        {
            //either way, start everything
            getStartDir();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_STORAGE)
        {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                requestAllStorage();
            }
            else
            {
                Toast.makeText(this, "Cannot open file browser", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    public void userNavigate(String path)
    {
        if (!getDirFromRoot(path, false))
        {
            Toast.makeText(FileBrowserActivity.this, "Cannot navigate to " + path, Toast.LENGTH_SHORT).show();
        }
    }

    //get directories and files from selected path
    public boolean getDirFromRoot(String path, boolean noHistory)
    {
        if (path == null)
        {
            return getStartDir();
        }
        boolean isRoot = path.equals("/");

        FileBrowserAdapter.FileItem current = new FileBrowserAdapter.FileItem();
        current.file = new File(path);
        File[] filesArray = current.file.listFiles();
        if (filesArray == null)
        {
            Log.d("APPY", "Cannot navigate to " + path);
            return false;
        }

        if (!noHistory)
        {
            history.addFirst(path);
        }

        //sorting file list in alphabetical order
        Arrays.sort(filesArray, (o1, o2) -> {
            if (o1.isDirectory() && !o2.isDirectory())
            {
                return -1;
            }
            if (!o1.isDirectory() && o2.isDirectory())
            {
                return 1;
            }
            return o1.getAbsolutePath().compareToIgnoreCase(o2.getAbsolutePath());
        });

        FileBrowserAdapter.FileItem[] items = new FileBrowserAdapter.FileItem[filesArray.length];
        for (int i = 0; i < filesArray.length; i++)
        {
            items[i] = new FileBrowserAdapter.FileItem();
            items[i].file = filesArray[i];
            items[i].checked = selected.containsKey(canonicalPath(filesArray[i]));
        }
        adapter = new FileBrowserAdapter(this, items, current, isRoot);
        adapter.setCheckedListener(this);
        adapter.setSelectingEnabled(selectingEnabled);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (adapter.isParent(position))
            {
                userNavigate(adapter.getCurrent().file.getParentFile().getAbsolutePath());
            }
            else
            {
                FileBrowserAdapter.FileItem item = (FileBrowserAdapter.FileItem) adapter.getItem(position);
                if (item.file.isDirectory())
                {
                    userNavigate(item.file.getAbsolutePath());
                }
                else
                {
                    returnFiles(new String[]{item.file.getAbsolutePath()});
                }
            }
        });
        return true;
    }

    public void cancelFileOp()
    {
        copying = false;
        cutting = false;
        selectingEnabled = true;
        adapter.setSelectingEnabled(true);
        updateMenu();
    }

    public void backPressed()
    {
        if (!tutorial.allowBackPress())
        {
            return;
        }

        if (copying || cutting)
        {
            cancelFileOp();
            return;
        }

        Log.d("APPY", "history: "+history.size());

        history.removeFirst(); //pop current
        if (history.isEmpty())
        {
            finish();
            return;
        }
        getDirFromRoot(history.get(0), true);
    }

    public void returnFiles(String[] files)
    {
        if (!allowReturnMultipleFiles && files.length > 1)
        {
            Toast.makeText(this, "Please select only one file.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean needConfirmation = false;

        if (specificExtensionConfirmation != null)
        {
            for (String file : files)
            {
                if (!file.toLowerCase().endsWith(specificExtensionConfirmation))
                {
                    needConfirmation = true;
                    break;
                }
            }
        }

        if (needConfirmation)
        {
            //just to confirm
            Utils.showConfirmationDialog(this,
                    "Import " + files.length + " files?",
                    "At least one file does not end with " + specificExtensionConfirmation + ", continue?",
                    android.R.drawable.ic_dialog_alert,
                    "Import", "Cancel", () -> {
                        Intent intent = new Intent();
                        intent.putExtra(RESULT_FILES, files);
                        setResult(RESULT_OK, intent);
                        tutorial.onFileBrowserImportDone();
                        finish();
                    });
        }
        else
        {
            Intent intent = new Intent();
            intent.putExtra(RESULT_FILES, files);
            setResult(RESULT_OK, intent);
            tutorial.onFileBrowserImportDone();
            finish();
        }
    }

    public String[] getSelectedFiles()
    {
        String[] files = new String[selected.size()];
        int i = 0;
        for (File file : selected.values())
        {
            files[i] = file.getAbsolutePath();
            i++;
        }
        return files;
    }

    public String currentDir()
    {
        if (adapter == null)
        {
            return null;
        }
        return adapter.getCurrent().file.getAbsolutePath();
    }

    private static boolean copy(File source, File dest)
    {
        try (FileInputStream is = new FileInputStream(source); FileOutputStream os = new FileOutputStream(dest))
        {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0)
            {
                os.write(buffer, 0, length);
            }
        }
        catch (IOException e)
        {
            return false;
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        if (adapter != null)
        {
            if (item.getItemId() == R.id.action_select)
            {
                returnFiles(getSelectedFiles());
                return true;
            }
            else if (item.getItemId() == R.id.action_delete)
            {
                final String[] files = getSelectedFiles();

                //TODO are you sure
                new AlertDialog.Builder(this)
                        .setTitle("Delete")
                        .setMessage(files.length == 1 ? "Delete " + new File(files[0]).getName() + "?" : "Delete " + files.length + " files?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                            for (String path : files)
                            {
                                Widget.deleteDir(new File(path));
                            }
                            selected.clear();
                            updateMenu();
                            getDirFromRoot(currentDir(), true);
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .setOnDismissListener(dialog -> tutorial.onFileBrowserDialogDone()).show();
                return true;
            }
            else if (item.getItemId() == R.id.action_copy)
            {
                copying = true;
                selectingEnabled = false;
                adapter.setSelectingEnabled(false);
                updateMenu();
                return true;
            }
            else if (item.getItemId() == R.id.action_cut)
            {
                cutting = true;
                selectingEnabled = false;
                adapter.setSelectingEnabled(false);
                updateMenu();
                return true;
            }
            else if (item.getItemId() == R.id.action_paste)
            {
                String newDir = currentDir();
                for (File file : selected.values())
                {
                    if (copying)
                    {
                        if (!copy(file, new File(newDir, file.getName())))
                        {
                            //TODO notify
                        }
                    }
                    if (cutting)
                    {
                        if (!file.renameTo(new File(newDir, file.getName())))
                        {
                            //TODO notify
                        }
                    }
                }
                cancelFileOp();
                selected.clear();
                getDirFromRoot(currentDir(), true);
                return true;
            }
            else if (item.getItemId() == R.id.action_cancel)
            {
                cancelFileOp();
                return true;
            }
            else if (item.getItemId() == R.id.action_clear)
            {
                selected.clear();
                cancelFileOp();
                updateMenu();
                adapter.updateSelection(selected.values());
                return true;
            }
            else if (item.getItemId() == R.id.action_rename)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Rename");

                final File file = selected.values().iterator().next();

                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
                input.setText(file.getName());
                builder.setView(input);

                builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (!file.renameTo(new File(file.getParentFile(), input.getText().toString())))
                    {
                        Toast.makeText(FileBrowserActivity.this, "Failed to rename", Toast.LENGTH_SHORT).show();
                    }
                    selected.clear();
                    updateMenu();
                    getDirFromRoot(currentDir(), true);
                });
                builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

                builder.setOnDismissListener(dialog -> tutorial.onFileBrowserDialogDone());

                builder.show();
                return true;
            }
            else if (item.getItemId() == R.id.action_goto)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Go to");

                FrameLayout container = new FrameLayout(this);

                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
                input.setText(currentDir());

                double margin = Utils.convertUnit(this, 40, TypedValue.COMPLEX_UNIT_DIP, TypedValue.COMPLEX_UNIT_PX);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.leftMargin = (int) margin;
                params.rightMargin = (int) margin;
                input.setLayoutParams(params);
                container.addView(input);

                builder.setView(container);

                builder.setSingleChoiceItems(preset_names, -1,
                        (dialog, which) -> {
                            if (cantViewSharedStorage && which == SHARED_STORAGE_INDEX)
                            {
                                bottomtext.setVisibility(View.VISIBLE);
                            }
                            else
                            {
                                bottomtext.setVisibility(View.INVISIBLE);
                            }
                            userNavigate(preset_paths[which]);
                            dialog.dismiss();
                        });

                builder.setPositiveButton("Go", (dialog, which) -> {
                    //overriding later
                });

                builder.setOnDismissListener(dialog -> tutorial.onFileBrowserDialogDone());

                final AlertDialog dialog = builder.show();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    // only if we succeeded
                    if (getDirFromRoot(input.getText().toString(), false))
                    {
                        dialog.dismiss();
                    }
                });
                return true;
            }
            else if (item.getItemId() == R.id.action_edit)
            {
                final File file = selected.values().iterator().next();

                FileEditorActivity.launch(this, file.getPath());

                selected.clear();
                updateMenu();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.filebrowser_actions, menu);
        updateMenu(menu);
        return super.onCreateOptionsMenu(menu);
    }

    public void updateMenu()
    {
        updateMenu(toolbar.getMenu());
    }

    public void updateMenu(Menu menu)
    {
        if (menu != null)
        {
            menu.findItem(R.id.action_clear).setVisible(!selected.isEmpty() && !copying && !cutting);
            menu.findItem(R.id.action_select).setVisible(!selected.isEmpty() && !copying && !cutting);
            menu.findItem(R.id.action_copy).setVisible(!selected.isEmpty() && !copying && !cutting);
            menu.findItem(R.id.action_cut).setVisible(!selected.isEmpty() && !copying && !cutting);
            menu.findItem(R.id.action_delete).setVisible(!selected.isEmpty() && !copying && !cutting);
            menu.findItem(R.id.action_rename).setVisible(selected.size() == 1 && !copying && !cutting);
            menu.findItem(R.id.action_edit).setVisible(selected.size() == 1 && !copying && !cutting);

            menu.findItem(R.id.action_cancel).setVisible(copying || cutting);
            menu.findItem(R.id.action_paste).setVisible(copying || cutting);
        }
    }

    public String canonicalPath(File file)
    {
        try
        {
            return file.getCanonicalPath();
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void onCheckedChanged(FileBrowserAdapter adapter, File file, boolean checked)
    {
        if (checked)
        {
            selected.put(canonicalPath(file), file);
        }
        else
        {
            selected.remove(canonicalPath(file));
        }
        updateMenu();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if (tutorial != null)
        {
            tutorial.onActivityDestroyed();
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();

        if (tutorial != null)
        {
            tutorial.onActivityPaused();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        if (tutorial != null)
        {
            tutorial.onConfigurationChanged();
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        getDirFromRoot(currentDir(), true);

        if (tutorial != null)
        {
            tutorial.onActivityResumed();
        }
    }
}
