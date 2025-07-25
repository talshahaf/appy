package com.appy;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AlertDialog;

import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ConfigsFragment extends FragmentParent
{
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        super.onCreateView(inflater, container, savedInstanceState);

        setHasOptionsMenu(true);
        return inflater.inflate(R.layout.fragment_configs, container, false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.config_toolbar_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.action_export)
        {
            Log.d("APPY", "Export click");

            if (getWidgetService() != null)
            {
                Configurations configurations = getWidgetService().getConfigurations();
                if (configurations != null)
                {
                    File exportFile = exportFilePath();
                    try
                    {
                        Utils.writeFile(exportFile, DictObj.makeJson(configurations.getDict(), true));
                        Toast.makeText(getActivity(), "Configurations exported to " + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    }
                    catch (IOException e)
                    {
                        Log.e("APPY", "export config failed", e);
                    }

                }
            }

            return true;
        }
        else if (item.getItemId() == R.id.action_import)
        {
            Log.d("APPY", "Import click");
            Intent intent = new Intent(getActivity(), FileBrowserActivity.class);
            intent.putExtra(FileBrowserActivity.REQUEST_ALLOW_RETURN_MULTIPLE, false);
            intent.putExtra(FileBrowserActivity.REQUEST_SPECIFIC_EXTENSION_CONFIRMATION, ".json");
            requestActivityResult(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(Intent data)
    {
        String[] files = data.getStringArrayExtra(FileBrowserActivity.RESULT_FILES);
        if (files == null || files.length == 0)
        {
            return;
        }

        if (getWidgetService() == null)
        {
            return;
        }
        Configurations configurations = getWidgetService().getConfigurations();
        if (configurations == null)
        {
            return;
        }

        try
        {
            String content = Utils.readAndHashFileAsString(new File(files[0]), Constants.CONFIG_IMPORT_MAX_SIZE).first;

            DictObj.Dict newConfig = null;
            try
            {
                newConfig = (DictObj.Dict)DictObj.fromJson(content);
            }
            catch (Exception e)
            {
                Toast.makeText(getActivity(), "File is not in a valid/expected JSON format", Toast.LENGTH_SHORT).show();
                Log.e("APPY", "deserialize failed", e);
            }

            if (newConfig != null)
            {
                final DictObj.Dict finalConfig = newConfig;
                Utils.showConfirmationDialog(getActivity(),
                        "Import Configuration", "This will overwrite all existing configurations", android.R.drawable.ic_dialog_alert,
                        null, null, new Runnable() {
                            @Override
                            public void run() {
                                configurations.replaceConfiguration(finalConfig);
                                    Toast.makeText(getActivity(), "Configurations imported from " + files[0], Toast.LENGTH_LONG).show();
                                    start();
                                }
                            });
                }
            }
            catch (IOException e)
            {
                Log.e("APPY", "import config failed", e);
            }
    }

    private boolean attachedAndBound = false;

    @Override
    public void onAttachedAndBound()
    {
        attachedAndBound = true;
        start();
    }

    @Override
    public void onArgument()
    {
        if (attachedAndBound)
        {
            start();
        }
    }

    public void start()
    {
        if (getActivity() == null)
        {
            return;
        }
        if (getWidgetService() == null)
        {
            return;
        }
        if (fragmentArg == null)
        {
            switchTo(new WidgetSelectFragment(), false);
            return;
        }

        String widget = fragmentArg.getString(Constants.FRAGMENT_ARG_WIDGET);
        HashMap<String, Pair<String, String>> configs = getWidgetService().getConfigurations().getValues(widget);
        if (configs.isEmpty())
        {
            switchTo(new WidgetSelectFragment(), true);
            return;
        }

        WidgetSelectFragment fragment = new WidgetSelectFragment();
        fragment.setWidget(widget);
        String config = fragmentArg.getString(Constants.FRAGMENT_ARG_CONFIG);
        if (configs.containsKey(config))
        {
            fragment.setConfig(config);

            int requestCode = fragmentArg.getInt(Constants.FRAGMENT_ARG_REQUESTCODE, -1);
            if (requestCode != -1)
            {
                int doneRequestCode = getWidgetService().generateRequestCode();
                getWidgetService().asyncReport(requestCode, doneRequestCode);
                fragment.setRequestCode(doneRequestCode);
            }
            else
            {
                fragment.setRequestCode(-1);
            }
        }
        switchTo(fragment, true);
    }

    public File exportFilePath()
    {
        return new File(getWidgetService().getPreferredScriptDir(), "exported_configurations.json");
    }

    @Override
    public void onPause()
    {
        setArgument(null);
        super.onPause();
    }

    public static class WidgetSelectFragment extends ChildFragment implements AdapterView.OnItemClickListener
    {
        ListView list;
        String widget = null;
        String config = null;
        int requestCode_ = 0;

        String asyncResultAndDie = null;

        @Override
        public void onResumedAndBound()
        {
            refresh();
        }

        @Override
        public void onStop()
        {
            if (asyncResultAndDie != null)
            {
                handleAsyncRequestAndDie(asyncResultAndDie);
            }
            super.onStop();
        }

        public void refresh()
        {
            ArrayList<ListFragmentAdapter.Item> adapterList = new ArrayList<>();
            ListFragmentAdapter.Item selectedConfigItem = null;
            if (widget == null)
            {
                HashMap<String, Integer> widgets = getWidgetService().getConfigurations().listWidgets();
                for (Map.Entry<String, Integer> item : widgets.entrySet())
                {
                    adapterList.add(new ListFragmentAdapter.Item(item.getKey(), item.getValue() + " configurations"));
                }
            }
            else
            {
                HashMap<String, Pair<String, String>> values = getWidgetService().getConfigurations().getValues(widget);
                for (Map.Entry<String, Pair<String, String>> item : values.entrySet())
                {
                    String subtitle = item.getValue().first != null ? (item.getValue().first + "\n" + item.getValue().second) : item.getValue().second;
                    ListFragmentAdapter.Item listitem = new ListFragmentAdapter.Item(item.getKey(), subtitle, item.getValue().second);
                    if (config != null && item.getKey().equals(config))
                    {
                        selectedConfigItem = listitem;
                    }
                    adapterList.add(listitem);
                }
            }
            list.setAdapter(new ListFragmentAdapter(getActivity(), adapterList));
            if (selectedConfigItem != null)
            {
                asyncResultAndDie = (String)selectedConfigItem.arg;
                showEditor(selectedConfigItem, true);
            }
            else
            {
                asyncResultAndDie = null;
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState)
        {
            View layout = inflater.inflate(R.layout.fragment_configs_list, container, false);
            list = layout.findViewById(R.id.configs_list);
            list.setOnItemClickListener(this);
            registerForContextMenu(list);
            refresh();
            return layout;
        }

        @Override
        public void onItemClick(AdapterView<?> adapter, View view, int position, long id)
        {
            ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) adapter.getItemAtPosition(position);
            if (widget == null)
            {
                //select that widget
                WidgetSelectFragment fragment = new WidgetSelectFragment();
                fragment.setWidget(item.key);
                parent.switchTo(fragment, false);
            }
            else
            {
                //pop value editor
                showEditor(item, false);
            }
        }

        public static boolean isValidJSON(String value)
        {
            try
            {
                // turns everything to an array (array of array is ok)
                new JSONArray("[" + value + "]");
            }
            catch (JSONException e)
            {
                return false;
            }
            return true;
        }

        public void handleAsyncRequestAndDie(String value)
        {
            if (getRequestCode() != -1)
            {
                getWidgetService().asyncReport(getRequestCode(), value);
                setRequestCode(-1);
            }
            parent.finishActivity();
        }

        public void showEditor(final ListFragmentAdapter.Item item, final boolean dieAfter)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(widget + "." + item.key);

            final EditText input = new EditText(getActivity());

            if (item.arg != null)
            {
                try
                {
                    input.setText(new JSONObject((String) item.arg).toString(2));
                }
                catch (JSONException e)
                {
                    input.setText((String) item.arg);
                }
            }
            else
            {
                input.setText("");
            }

            input.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setSingleLine(false);
            input.setGravity(Gravity.START | Gravity.TOP);
            builder.setView(input);

            builder.setPositiveButton("Ok", null);

            final DialogInterface.OnClickListener onDismiss = new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    if (dieAfter)
                    {
                        handleAsyncRequestAndDie((String)item.arg);
                    }
                    dialog.dismiss();
                }
            };
            builder.setNegativeButton("Cancel", onDismiss);
            builder.setOnCancelListener(new DialogInterface.OnCancelListener()
            {
                @Override
                public void onCancel(DialogInterface dialog)
                {
                    onDismiss.onClick(dialog, 0);
                }
            });

            final AlertDialog alert = builder.create();

            alert.setOnShowListener(new DialogInterface.OnShowListener()
            {

                @Override
                public void onShow(DialogInterface dialogInterface)
                {

                    Button button = alert.getButton(AlertDialog.BUTTON_POSITIVE);
                    button.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View view)
                        {
                            String newValue = input.getText().toString();
                            if (item.key.endsWith("_nojson") || isValidJSON(newValue))
                            {
                                getWidgetService().getConfigurations().setConfig(widget, item.key, newValue);
                                if (dieAfter)
                                {
                                    handleAsyncRequestAndDie(newValue);
                                }
                                else
                                {
                                    refresh();
                                }
                                alert.dismiss();
                            }
                            else
                            {
                                // dont dismiss
                                input.setBackgroundColor(0x50FF0000);
                            }
                        }
                    });
                }
            });

            alert.show();
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                                        ContextMenu.ContextMenuInfo menuInfo)
        {
            if (v == list)
            {
                getActivity().getMenuInflater().inflate(R.menu.config_actions, menu);

                AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
                ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) list.getItemAtPosition(info.position);
                menu.setHeaderTitle(item.key);
            }
            else
            {
                super.onCreateContextMenu(menu, v, menuInfo);
            }
        }

        @Override
        public boolean onContextItemSelected(MenuItem menuItem)
        {
            boolean delete_;
            if (menuItem.getItemId() == R.id.action_reset)
            {
                delete_ = false;
            }
            else if (menuItem.getItemId() == R.id.action_delete)
            {
                delete_ = true;
            }
            else
            {
                return super.onContextItemSelected(menuItem);
            }

            final boolean delete = delete_;

            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();
            final ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) list.getItemAtPosition(info.position);

            String title;
            String message;

            if (widget == null)
            {
                if (delete)
                {
                    title = "Delete all";
                    message = "Delete all " + item.key + " configurations?";
                }
                else
                {
                    title = "Reset all";
                    message = "Reset all " + item.key + " configurations?";
                }
            }
            else
            {
                if (delete)
                {
                    title = "Delete configuration";
                    message = "Delete " + item.key + "?";
                }
                else
                {
                    title = "Reset configuration";
                    message = "Reset " + item.key + "?";
                }
            }

            Utils.showConfirmationDialog(getActivity(),
                    title, message, android.R.drawable.ic_dialog_alert,
                    null, null, new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            if (widget == null)
                            {
                                if (delete)
                                {
                                    getWidgetService().getConfigurations().deleteWidget(item.key);
                                }
                                else
                                {
                                    getWidgetService().getConfigurations().resetWidget(item.key);
                                }
                            }
                            else
                            {
                                if (delete)
                                {
                                    getWidgetService().getConfigurations().deleteKey(widget, item.key);
                                }
                                else
                                {
                                    getWidgetService().getConfigurations().resetKey(widget, item.key);
                                }
                            }
                            refresh();
                        }
                    });
            return true;
        }

        public void setWidget(String widget)
        {
            this.widget = widget;
        }

        public void setConfig(String config)
        {
            this.config = config;
        }

        public void setRequestCode(int requestCode)
        {
            this.requestCode_ = requestCode;
        }
        public int getRequestCode()
        {
            return requestCode_;
        }
    }
}
