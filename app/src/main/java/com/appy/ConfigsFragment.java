package com.appy;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import android.text.InputType;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import kotlin.Triple;

public class ConfigsFragment extends FragmentParent
{
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        super.onCreateView(inflater, container, savedInstanceState);

        setHasOptionsMenu(true);
        return inflater.inflate(R.layout.fragment_parent, container, false);
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
                        null, null, () -> {
                            configurations.replaceConfiguration(finalConfig);
                                Toast.makeText(getActivity(), "Configurations imported from " + files[0], Toast.LENGTH_LONG).show();
                                start();
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
            switchTo(new ConfigSelectFragment(), true);
            return;
        }

        String widget = fragmentArg.getString(Constants.FRAGMENT_ARG_WIDGET);
        int widgetId = fragmentArg.getInt(Constants.FRAGMENT_ARG_WIDGET_ID, Configurations.NONLOCAL_ID);
        HashMap<String, Triple<String, String, Boolean>> configs = getWidgetService().getConfigurations().getValues(widget, widgetId);
        if (configs.isEmpty())
        {
            switchTo(new ConfigSelectFragment(), true);
            return;
        }

        ConfigSelectFragment fragment = new ConfigSelectFragment();
        fragment.setWidget(widget, widgetId);
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

    public static class ConfigSelectFragment extends ChildFragment implements AdapterView.OnItemClickListener
    {
        ListView list;
        Spinner widgetPicker;
        String widget = null;
        int widgetId = Configurations.NONLOCAL_ID;
        String config = null;
        int requestCode_ = 0;

        String asyncResultAndDie = null;

        static class WidgetListElement implements Comparable<WidgetListElement>
        {
            int widgetId;
            DictObj.Dict props;
            public WidgetListElement(int widgetId, DictObj.Dict props)
            {
                this.widgetId = widgetId;
                this.props = props;
            }

            @Override
            public String toString()
            {
                if (widgetId == Configurations.NONLOCAL_ID)
                {
                    return "All widgets";
                }
                return (props.getBoolean("app", false) ? "app #" : "widget #") + widgetId;
            }

            @Override
            public int compareTo(WidgetListElement other)
            {
                if (widgetId == other.widgetId)
                {
                    return 0;
                }
                if (widgetId == Configurations.NONLOCAL_ID)
                {
                    return -1;
                }
                if (other.widgetId == Configurations.NONLOCAL_ID)
                {
                    return 1;
                }
                return Integer.compare(widgetId, other.widgetId);
            }
        }

        @Override
        public void onResumedAndBound()
        {
            refresh();
        }

        @Override
        public void onStartedAndBound()
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
                widgetPicker.getLayoutParams().height = 0;
                widgetPicker.setVisibility(View.INVISIBLE);
                widgetPicker.requestLayout();

                if (getActivity() != null)
                {
                    getActivity().setTitle("Configurations");
                }

                HashMap<String, Integer> widgets = getWidgetService().getConfigurations().listWidgets();
                for (Map.Entry<String, Integer> item : widgets.entrySet())
                {
                    adapterList.add(new ListFragmentAdapter.Item(item.getKey(), Utils.enumerableFormat(item.getValue(), "configuration", " configurations")));
                }
            }
            else
            {
                if (getActivity() != null)
                {
                    getActivity().setTitle("Configurations of " + widget);
                    widgetPicker.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    widgetPicker.setVisibility(View.VISIBLE);
                    widgetPicker.requestLayout();

                    DictObj.Dict widgetInstances = getWidgetService().getAllWidgetAppProps(widget, false, false);
                    WidgetListElement[] elements = new WidgetListElement[widgetInstances.size() + 1];
                    elements[0] = new WidgetListElement(Configurations.NONLOCAL_ID, null);
                    int i = 1;
                    for (DictObj.Entry entry : widgetInstances.entries())
                    {
                        elements[i] = new WidgetListElement(Integer.parseInt(entry.key), (DictObj.Dict) entry.value);
                        i++;
                    }
                    Arrays.sort(elements);
                    int current_position = -1;
                    for (i = 0; i < elements.length; i++)
                    {
                        if (elements[i].widgetId == widgetId)
                        {
                            current_position = i;
                            break;
                        }
                    }
                    widgetPicker.setAdapter(new ArrayAdapter<>(getActivity(), R.layout.dropdown_text_item, elements));
                    widgetPicker.setSelection(current_position);
                }

                HashMap<String, Triple<String, String, Boolean>> values = getWidgetService().getConfigurations().getValues(widget, widgetId);
                for (Map.Entry<String, Triple<String, String, Boolean>> item : values.entrySet())
                {
                    String valueSummary = Utils.capWithEllipsis(Utils.collapseSpaces(item.getValue().component2().replaceAll("\r", "").replaceAll("\n", "\\\\n").replaceAll("\t", " ")), 100);
                    String subtitle = (item.getValue().component1() != null ? (item.getValue().component1() + "\n") : "") + valueSummary;
                    ListFragmentAdapter.Item listitem = new ListFragmentAdapter.Item(item.getKey(), subtitle, item1 -> item1.key + (widgetId == Configurations.NONLOCAL_ID || ((Triple<String, String, Boolean>)item1.arg).component3() ? "" : " (all widgets)"), item.getValue());
                    listitem.setEnabled(widgetId == Configurations.NONLOCAL_ID || item.getValue().component3());
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
                asyncResultAndDie = ((Triple<String, String, Boolean>)selectedConfigItem.arg).component2();
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
            View layout = inflater.inflate(R.layout.fragment_config_list, container, false);
            list = layout.findViewById(R.id.config_list);
            list.setOnItemClickListener(this);
            list.setEmptyView(layout.findViewById(R.id.empty_view));
            registerForContextMenu(list);

            widgetPicker = layout.findViewById(R.id.config_picker);
            widgetPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
            {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
                {
                    int newWidgetId = ((WidgetListElement)parent.getItemAtPosition(position)).widgetId;
                    if (widgetId != newWidgetId)
                    {
                        widgetId = newWidgetId;
                        refresh();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent)
                {
                    if (widgetId != Configurations.NONLOCAL_ID)
                    {
                        widgetId = Configurations.NONLOCAL_ID;
                        refresh();
                    }
                }
            });

            widgetPicker.getLayoutParams().height = 0;
            widgetPicker.setVisibility(View.INVISIBLE);
            widgetPicker.requestLayout();

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
                ConfigSelectFragment fragment = new ConfigSelectFragment();
                fragment.setWidget(item.key, Configurations.NONLOCAL_ID);
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
            Context context = getActivity();
            if (context == null)
            {
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(widget + "." + item.key);

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);

            EditText input = new EditText(context);
            LinearLayout switchContainer = new LinearLayout(context);
            switchContainer.setOrientation(LinearLayout.HORIZONTAL);

            SwitchMaterial check = new SwitchMaterial(context);
            TextView checkText = new TextView(context);
            String textOn = "Set for widget instance";
            String textOff = "Set for all widgets";

            checkText.setText(textOn);
            check.setChecked(true);
            check.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            checkText.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            check.setOnCheckedChangeListener((buttonView, isChecked) -> checkText.setText(isChecked ? textOn : textOff));
            switchContainer.addView(check);
            switchContainer.addView(checkText);
            switchContainer.setVisibility(View.GONE);

            container.addView(input);
            container.addView(switchContainer);

            if (item.arg != null)
            {
                Triple<String, String, Boolean> arg = (Triple<String, String, Boolean>) item.arg;
                try
                {
                    input.setText(new JSONObject(arg.component2()).toString(2));
                }
                catch (JSONException e)
                {
                    input.setText(arg.component2());
                }

                if (dieAfter && widgetId != Configurations.NONLOCAL_ID)
                {
                    //add switch to choose where to set
                    switchContainer.setVisibility(View.VISIBLE);
                }
            }
            else
            {
                input.setText("");
            }

            input.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setSingleLine(false);
            input.setGravity(Gravity.START | Gravity.TOP);

            builder.setView(container);

            builder.setPositiveButton("Ok", null);

            final DialogInterface.OnClickListener onDismiss = (dialog, which) -> {
                if (dieAfter)
                {
                    handleAsyncRequestAndDie(((Triple<String, String, Boolean>)item.arg).component2());
                }
                dialog.dismiss();
            };
            builder.setNegativeButton("Cancel", onDismiss);
            builder.setOnCancelListener(dialog -> onDismiss.onClick(dialog, 0));

            final AlertDialog alert = builder.create();

            alert.setOnShowListener(dialogInterface -> {

                Button button = alert.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(view -> {
                    String newValue = input.getText().toString();
                    if (item.key.endsWith("_nojson") || isValidJSON(newValue))
                    {
                        getWidgetService().getConfigurations().setConfig(widget, check.isChecked() ? widgetId : Configurations.NONLOCAL_ID, item.key, newValue);
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
                });
            });

            alert.show();
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                                        ContextMenu.ContextMenuInfo menuInfo)
        {
            if (v == list)
            {
                AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
                ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) list.getItemAtPosition(info.position);

                Triple<String, String, Boolean> arg = (Triple<String, String, Boolean>)item.arg;
                if (widgetId != Configurations.NONLOCAL_ID && !arg.component3())
                {
                    // no menu if in widget instance and config does not have override
                    super.onCreateContextMenu(menu, v, menuInfo);
                    return;
                }

                getActivity().getMenuInflater().inflate(R.menu.config_actions, menu);

                if (widgetId != Configurations.NONLOCAL_ID)
                {
                    menu.removeItem(R.id.action_delete);
                }
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
                if (widgetId == Configurations.NONLOCAL_ID)
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
                else
                {
                    title = "Reset configuration";
                    message = "Reset " + item.key + " for widget?";
                }
            }

            Utils.showConfirmationDialog(getActivity(),
                    title, message, android.R.drawable.ic_dialog_alert,
                    null, null, () -> {
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
                            if (delete && widgetId == Configurations.NONLOCAL_ID)
                            {
                                getWidgetService().getConfigurations().deleteKey(widget, item.key);
                            }
                            else
                            {
                                getWidgetService().getConfigurations().resetKey(widget, item.key, widgetId);
                            }
                        }
                        refresh();
                    });
            return true;
        }

        public void setWidget(String widget, int widgetId)
        {
            this.widget = widget;
            this.widgetId = widgetId;
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
