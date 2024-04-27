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
    public static final int REQUEST_IMPORT_PATH = 304;

    public Bundle fragmentArg = null;

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
                        FileWriter writer = new FileWriter(exportFile, false);
                        writer.write(configurations.serialize());
                        writer.close();
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
            startActivityForResult(intent, REQUEST_IMPORT_PATH);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_IMPORT_PATH && resultCode == Activity.RESULT_OK)
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
                String content = Utils.readFile(new File(files[0]));
                HashMap<String, HashMap<String, Pair<String, String>>> newConfig = Configurations.deserialize(content);

                Utils.showConfirmationDialog(getActivity(),
                        "Import Configuration", "This will overwrite all existing configurations", android.R.drawable.ic_dialog_alert,
                        null, null, new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                configurations.replaceConfiguration(newConfig);
                                Toast.makeText(getActivity(), "Configurations imported from " + files[0], Toast.LENGTH_LONG).show();
                                tryStart();
                            }
                        });
            }
            catch (IllegalStateException e)
            {
                Toast.makeText(getActivity(), "File is not in a valid JSON format", Toast.LENGTH_SHORT).show();
                Log.e("APPY", "deserialize failed", e);
            }
            catch (IOException e)
            {
                Log.e("APPY", "import config failed", e);
            }
        }
    }

    public void tryStart()
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
        HashMap<String, String> configs = getWidgetService().getConfigurations().getValues(widget);
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
            fragment.setRequestCode(fragmentArg.getInt(Constants.FRAGMENT_ARG_REQUESTCODE, 0));
        }
        switchTo(fragment, true);
    }

    public File exportFilePath()
    {
        return new File(getWidgetService().getPreferredScriptDir(), "exported_configurations.json");
    }

    public void onBound()
    {
        tryStart();
    }

    public void onShow()
    {
        tryStart();
    }

    public void onHide()
    {
        setArgument(null);
    }

    @Override
    public void setArgument(Bundle fragmentArg)
    {
        this.fragmentArg = fragmentArg;
    }

    public static class WidgetSelectFragment extends ChildFragment implements AdapterView.OnItemClickListener
    {
        ListView list;
        String widget = null;
        String config = null;
        int requestCode = 0;

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
                HashMap<String, String> values = getWidgetService().getConfigurations().getValues(widget);
                for (Map.Entry<String, String> item : values.entrySet())
                {
                    ListFragmentAdapter.Item listitem = new ListFragmentAdapter.Item(item.getKey(), item.getValue());
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
                showEditor(selectedConfigItem, true);
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

        public void showEditor(final ListFragmentAdapter.Item item, final boolean dieAfter)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(widget + "." + item.key);

            final EditText input = new EditText(getActivity());

            try
            {
                input.setText(new JSONObject(item.value).toString(2));
            }
            catch (JSONException e)
            {
                input.setText(item.value);
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
                        if (requestCode != 0)
                        {
                            getWidgetService().asyncReport(requestCode, item.value);
                        }
                        parent.finishActivity();
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
                                    if (requestCode != 0)
                                    {
                                        getWidgetService().asyncReport(requestCode, newValue);
                                    }
                                    parent.finishActivity();
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
            this.requestCode = requestCode;
        }
    }
}
