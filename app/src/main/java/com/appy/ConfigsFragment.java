package com.appy;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;

import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import kotlin.Triple;

public class ConfigsFragment extends FragmentParent
{
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        super.onCreateView(inflater, container, savedInstanceState);
        return inflater.inflate(R.layout.fragment_parent, container, false);
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
            String content = Utils.readAndHashFileAsString(new File(files[0]), Constants.CONFIG_IMPORT_MAX_SIZE, false).first;

            DictObj.Dict newConfig = null;
            try
            {
                newConfig = (DictObj.Dict)DictObj.fromJson(content);
            }
            catch (Exception e)
            {
                Toast.makeText(requireActivity(), "File is not in a valid/expected JSON format", Toast.LENGTH_SHORT).show();
                Log.e("APPY", "deserialize failed", e);
            }

            if (newConfig != null)
            {
                final DictObj.Dict finalConfig = newConfig;
                Utils.showConfirmationDialog(requireActivity(),
                        "Import Configuration", "This will overwrite all existing configurations", android.R.drawable.ic_dialog_alert,
                        null, null, () -> {
                            configurations.replaceConfiguration(finalConfig);
                                Toast.makeText(requireActivity(), "Configurations imported from " + files[0], Toast.LENGTH_LONG).show();
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
        int requestCode = fragmentArg.getInt(Constants.FRAGMENT_ARG_REQUESTCODE, -1);
        if (fragmentArg.containsKey(Constants.FRAGMENT_ARG_CONFIG))
        {
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

            String config = fragmentArg.getString(Constants.FRAGMENT_ARG_CONFIG);
            if (configs.containsKey(config))
            {
                fragment.setConfig(config);
            }
            else
            {
                // die immediately if config is bad
                Toast.makeText(getActivity(), "Config not found: '" + config + "'", Toast.LENGTH_SHORT).show();
                fragment.setParent(this);
                fragment.handleAsyncRequestAndDie("");
            }
        }
        switchTo(fragment, true);
    }

    @Override
    public void onPause()
    {
        setArgument(null);
        super.onPause();
    }

    public static class ConfigSelectFragment extends ChildFragment implements AdapterView.OnItemClickListener, MenuProvider
    {
        ListView list;
        Spinner widgetPicker;
        String widget = null;
        int widgetId = Configurations.NONLOCAL_ID;
        String config = null;
        int requestCode_ = 0;
        WidgetListElement[] widgetPickerElements = null;

        String asyncResultAndDie = null;

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater)
        {
            inflater.inflate(R.menu.config_toolbar_actions, menu);
            if (widget == null)
            {
                menu.removeItem(R.id.action_copy);
            }
            if (widget == null || widgetId == Configurations.NONLOCAL_ID)
            {
                menu.removeItem(R.id.action_resetall);
            }
        }

        public File exportFilePath()
        {
            return new File(getWidgetService().getPreferredScriptDir(), "exported_configurations.json");
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem item)
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
                            Toast.makeText(requireActivity(), "Configurations exported to " + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
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
                Intent intent = new Intent(requireActivity(), FileBrowserActivity.class);
                intent.putExtra(FileBrowserActivity.REQUEST_ALLOW_RETURN_MULTIPLE, false);
                intent.putExtra(FileBrowserActivity.REQUEST_SPECIFIC_EXTENSION_CONFIRMATION, ".json");
                requestActivityResult(intent);
                return true;
            }
            else if (item.getItemId() == R.id.action_copy)
            {
                HashMap<String, Triple<String, String, Boolean>> values = getWidgetService().getConfigurations().getValues(widget, widgetId);
                if (widgetId != Configurations.NONLOCAL_ID)
                {
                    //filter out non instance values
                    values.entrySet().removeIf(e -> !e.getValue().component3());
                }
                copyToInstance(values);
            }
            else if (item.getItemId() == R.id.action_resetall)
            {
                Utils.showConfirmationDialog(requireContext(),
                                             "Reset configurations", "Reset all configurations of instance?",
                                                    android.R.drawable.ic_dialog_alert, null, null, () -> {
                    getWidgetService().getConfigurations().resetWidgetInstance(widget, widgetId);
                    refresh();
                });
            }
            return false;
        }

        static class WidgetListElement implements Comparable<WidgetListElement>
        {
            int widgetId;
            DictObj.Dict props;
            boolean deleted;
            boolean displayName;
            boolean hasInstanceConfigs;
            public WidgetListElement(int widgetId, DictObj.Dict props, boolean deleted, boolean displayName, boolean hasInstanceConfigs)
            {
                this.widgetId = widgetId;
                this.props = props;
                this.deleted = deleted;
                this.displayName = displayName;
                this.hasInstanceConfigs = hasInstanceConfigs;
            }

            @Override
            public String toString()
            {
                if (widgetId == Configurations.NONLOCAL_ID)
                {
                    return "All widgets";
                }
                return (hasInstanceConfigs ? "*" : "") + ((props != null && props.getBoolean("app", false)) ? "app #" : "widget #") + widgetId + (deleted ? " (deleted)" : "") + (displayName && props != null && props.getString("display_name") != null ? " (" + props.getString("display_name") + ")" : "");
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
            if (getWidgetService() == null)
            {
                return;
            }

            ArrayList<ListFragmentAdapter.Item> adapterList = new ArrayList<>();
            ListFragmentAdapter.Item selectedConfigItem = null;
            if (widget == null)
            {
                widgetPicker.setVisibility(View.GONE);
                widgetPicker.requestLayout();
                widgetPickerElements = null;

                if (getActivity() != null)
                {
                    getActivity().setTitle("Configurations");
                }

                HashMap<String, Integer> widgets = getWidgetService().getConfigurations().listWidgets();
                if (widgets.containsKey(Configurations.GLOBAL_CONFIG_NAME))
                {
                    // add global first
                    ListFragmentAdapter.Item item = new ListFragmentAdapter.Item(Configurations.GLOBAL_CONFIG_NAME,
                                                                                 Utils.enumerableFormat(widgets.get(Configurations.GLOBAL_CONFIG_NAME), "configuration", " configurations"),
                                                                                 i -> Utils.capitalize(i.key),
                                                                                 null);
                    item.setImportant(true);
                    adapterList.add(item);
                }
                for (Map.Entry<String, Integer> item : widgets.entrySet())
                {
                    if (item.getKey().equals(Configurations.GLOBAL_CONFIG_NAME))
                    {
                        continue;
                    }
                    adapterList.add(new ListFragmentAdapter.Item(item.getKey(), Utils.enumerableFormat(item.getValue(), "configuration", " configurations")));
                }
            }
            else
            {
                if (getActivity() != null)
                {
                    boolean isGlobal = widget.equals(Configurations.GLOBAL_CONFIG_NAME);
                    getActivity().setTitle(isGlobal ? "Global configurations" : "Configurations of " + widget);
                    widgetPicker.setVisibility(View.VISIBLE);
                    widgetPicker.requestLayout();

                    DictObj.Dict widgetInstances = getWidgetService().getAllWidgetAppProps(isGlobal ? null : widget, false, false);
                    HashMap<String, Set<Integer>> widgetConfigInstances = getWidgetService().getConfigurations().getInstanceValues(widget);

                    HashSet<Integer> haveInstanceConfigurations = new HashSet<>();
                    HashSet<Integer> deletedInstances = new HashSet<>();
                    for (Set<Integer> widgetIds : widgetConfigInstances.values())
                    {
                        for (int widgetId : widgetIds)
                        {
                            haveInstanceConfigurations.add(widgetId);
                            if (!widgetInstances.hasKey(widgetId+""))
                            {
                                deletedInstances.add(widgetId);
                            }
                        }
                    }

                    WidgetListElement[] elements = new WidgetListElement[widgetInstances.size() + deletedInstances.size() + 1];
                    int starti = 0;
                    //set selected first and NONLOCAL after
                    if (widgetId != Configurations.NONLOCAL_ID)
                    {
                        if (widgetInstances.hasKey(widgetId+""))
                        {
                            elements[starti++] = new WidgetListElement(widgetId, (DictObj.Dict) widgetInstances.get(widgetId+""), false, isGlobal, haveInstanceConfigurations.contains(widgetId));
                        }
                        else if (deletedInstances.contains(widgetId))
                        {
                            elements[starti++] = new WidgetListElement(widgetId, null, true, false, true);
                        }
                    }
                    elements[starti++] = new WidgetListElement(Configurations.NONLOCAL_ID, null, false, false, false);

                    int i = starti;
                    for (DictObj.Entry entry : widgetInstances.entries())
                    {
                        int id = Integer.parseInt(entry.key);
                        if (id == widgetId)
                        {
                            continue;
                        }
                        elements[i++] = new WidgetListElement(id, (DictObj.Dict) entry.value, false, isGlobal, haveInstanceConfigurations.contains(id));
                    }
                    for (int deletedInstance : deletedInstances)
                    {
                        if (deletedInstance == widgetId)
                        {
                            continue;
                        }
                        elements[i++] = new WidgetListElement(deletedInstance, null, true, false, true);
                    }
                    Arrays.sort(elements, starti, elements.length);
                    int current_position = -1;
                    for (i = 0; i < elements.length; i++)
                    {
                        if (elements[i].widgetId == widgetId)
                        {
                            current_position = i;
                            break;
                        }
                    }
                    widgetPickerElements = elements;
                    widgetPicker.setAdapter(new ArrayAdapter<>(getActivity(), R.layout.dropdown_text_item, widgetPickerElements));
                    widgetPicker.setSelection(current_position);
                }

                HashMap<String, Triple<String, String, Boolean>> values = getWidgetService().getConfigurations().getValues(widget, widgetId);
                for (Map.Entry<String, Triple<String, String, Boolean>> item : values.entrySet())
                {
                    String valueSummary = Utils.capWithEllipsis(Utils.collapseSpaces(item.getValue().component2().replaceAll("\r", "").replaceAll("\n", "\\\\n").replaceAll("\t", " ")), 100, true);
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
            if (getActivity() != null)
            {
                getActivity().invalidateMenu();
                list.setAdapter(new ListFragmentAdapter(getActivity(), adapterList));
            }
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
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState)
        {
            requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState)
        {
            super.onCreateView(inflater, container, savedInstanceState);

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

            widgetPicker.setVisibility(View.GONE);
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
                parent.getWidgetService().asyncReport(getRequestCode(), value);
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
            String title = widget + "." + item.key;
            builder.setTitle(title);
            View layout = LayoutInflater.from(context).inflate(R.layout.config_edit_view, null);
            builder.setView(layout);

            builder.setPositiveButton("Ok", null);

            final DialogInterface.OnClickListener onDismiss = (dialog, which) -> {
                if (dieAfter)
                {
                    handleAsyncRequestAndDie(((Triple<String, String, Boolean>)item.arg).component2());
                }
                else
                {
                    dialog.dismiss();
                }
            };
            builder.setNegativeButton("Cancel", onDismiss);
            builder.setNeutralButton("Copy", null);
            builder.setOnCancelListener(dialog -> onDismiss.onClick(dialog, 0));

            final EditText input = layout.findViewById(R.id.edit);
            final LinearLayout checkLayout = layout.findViewById(R.id.checklayout);
            final Switch check = layout.findViewById(R.id.check);
            final TextView checkText = layout.findViewById(R.id.checktext);
            check.setOnCheckedChangeListener((buttonView, isChecked) -> checkText.setText(isChecked ? "Set for widget instance" : "Set for all widgets"));
            check.setChecked(true);
            checkLayout.setVisibility(View.GONE);

            String value = "";
            if (item.arg != null)
            {
                Triple<String, String, Boolean> arg = (Triple<String, String, Boolean>) item.arg;
                value = arg.component2();
                if (!item.key.endsWith("_nojson"))
                {
                    try
                    {
                        value = new JSONObject(arg.component2()).toString(2);
                    }
                    catch (JSONException e)
                    {

                    }
                }

                if (dieAfter && widgetId != Configurations.NONLOCAL_ID)
                {
                    //add switch to choose where to set
                    checkLayout.setVisibility(View.VISIBLE);
                }
            }

            final String finalValue = value;
            input.setText(finalValue);

            final AlertDialog alert = builder.create();
            alert.setOnShowListener(dialogInterface -> {

                alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
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
                            alert.dismiss();
                        }
                    }
                    else
                    {
                        // dont dismiss
                        input.setBackgroundColor(0x50FF0000);
                    }
                });
                alert.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    Utils.copyToClipboard(context, title, finalValue, "Value copied to clipboard");
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

                requireActivity().getMenuInflater().inflate(R.menu.config_actions, menu);

                if (widget == null)
                {
                    menu.removeItem(R.id.action_copy);
                }
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
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();
            if (info != null)
            {
                final ListFragmentAdapter.Item item = (ListFragmentAdapter.Item) list.getItemAtPosition(info.position);

                if (menuItem.getItemId() == R.id.action_reset || menuItem.getItemId() == R.id.action_delete)
                {
                    boolean delete = menuItem.getItemId() == R.id.action_delete;

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
                            message = "Reset " + item.key + " for instance?";
                        }
                    }

                    Utils.showConfirmationDialog(requireActivity(),
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
                else if (menuItem.getItemId() == R.id.action_copy && widgetPickerElements != null)
                {
                    if (item.arg != null)
                    {
                        HashMap<String, Triple<String, String, Boolean>> values = new HashMap<>();
                        values.put(item.key, (Triple<String, String, Boolean>) item.arg);
                        copyToInstance(values);
                    }
                }
            }
            return super.onContextItemSelected(menuItem);
        }

        public void copyToInstance(HashMap<String, Triple<String, String, Boolean>> values)
        {
            WidgetListElement[] elementsExceptThis = new WidgetListElement[widgetPickerElements.length - 1];
            int c = 0;
            for (WidgetListElement e : widgetPickerElements)
            {
                if (e.widgetId != widgetId)
                {
                    elementsExceptThis[c++] = e;
                }
            }
            String[] widgets = new String[elementsExceptThis.length];
            for (int i = 0; i < widgets.length; i++)
            {
                widgets[i] = elementsExceptThis[i].toString();
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
            builder.setTitle(values.size() != 1 ? "Select instance to copy " + values.size() + " keys to:" : "Select an instance to copy " + values.keySet().iterator().next() + " to:");
            builder.setItems(widgets, (dialog, which) -> {
                int toInstance = elementsExceptThis[which].widgetId;
                for(Map.Entry<String, Triple<String, String, Boolean>> e : values.entrySet())
                {
                    String key = e.getKey();
                    String value = e.getValue().component2();
                    getWidgetService().getConfigurations().setConfig(widget, toInstance, key, value);
                }
                refresh();
            });
            builder.show();
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
