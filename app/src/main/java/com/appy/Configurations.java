package com.appy;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

public class Configurations
{
    private final Object lock = new Object();
    // widget -> key -> (current value, default value)
    //private HashMap<String, HashMap<String, Pair<String, String>>> widgetConfigurations = new HashMap<>();
    private DictObj.Dict configurations = new DictObj.Dict();

    private final Context context;

    interface ChangeListener
    {
        void onChange(String widget, String key);
    }

    private ChangeListener listener;

    public Configurations(Context context, ChangeListener listener)
    {
        this.context = context;
        this.listener = listener;
    }

    public HashMap<String, Integer> listWidgets()
    {
        synchronized (lock)
        {
            HashMap<String, Integer> ret = new HashMap<>();
            for (DictObj.Entry entry : configurations.entries())
            {
                ret.put(entry.key, ((DictObj.Dict)entry.value).size());
            }
            return ret;
        }
    }

    public HashMap<String, String> getValues(String widget)
    {
        synchronized (lock)
        {
            DictObj.Dict configs = configurations.getDict(widget);
            HashMap<String, String> values = new HashMap<>();
            if (configs != null)
            {
                for (DictObj.Entry entry : configs.entries())
                {
                    values.put(entry.key, ((DictObj.Dict)entry.value).getString("value"));
                }
            }
            return values;
        }
    }

    public void resetKey(String widget, String key)
    {
        boolean changed = false;
        synchronized (lock)
        {
            DictObj.Dict configs = configurations.getDict(widget);
            if (configs != null)
            {
                DictObj.Dict values = configs.getDict(key);
                if (values != null)
                {
                    String currentValue = values.getString("value");
                    String defaultValue = values.getString("default");
                    if (!currentValue.equals(defaultValue))
                    {
                        changed = true;
                    }
                    values.put("value", defaultValue);
                }
            }

            if (changed)
            {
                saveChanges(configurations, Collections.singleton(new Pair<>(widget, key)));
            }
        }

        if (changed)
        {
            notifyConfigurationUpdate(widget, key);
        }
    }

    public void resetWidget(String widget)
    {
        ArrayList<Pair<String, String>> changed = new ArrayList<>();
        synchronized (lock)
        {
            DictObj.Dict configs = configurations.getDict(widget);
            if (configs != null)
            {
                for (DictObj.Entry entry : configs.entries())
                {
                    DictObj.Dict values = (DictObj.Dict)entry.value;
                    if (values != null)
                    {
                        String currentValue = values.getString("value");
                        String defaultValue = values.getString("default");
                        if (!currentValue.equals(defaultValue))
                        {
                            changed.add(new Pair<>(widget, entry.key));
                        }
                        values.put("value", defaultValue);
                    }
                }
            }

            if (!changed.isEmpty())
            {
                saveChanges(configurations, changed);
            }
        }

        if (!changed.isEmpty())
        {
            notifyConfigurationUpdate(widget, null);
        }
    }

    public void deleteKey(String widget, String key)
    {
        boolean changed = false;
        synchronized (lock)
        {
            DictObj.Dict configs = configurations.getDict(widget);
            if (configs != null)
            {
                if (configs.hasKey(key))
                {
                    configs.remove(key);
                    changed = true;
                }
            }

            if (changed)
            {
                saveChanges(configurations, Collections.singleton(new Pair<>(widget, key)));
            }
        }

        if (changed)
        {
            notifyConfigurationUpdate(widget, key);
        }
    }

    public void deleteWidget(String widget)
    {
        ArrayList<Pair<String, String>> changed = new ArrayList<>();
        synchronized (lock)
        {
            if (configurations.hasKey(widget))
            {
                for (String key : configurations.getDict(widget).keyset())
                {
                    changed.add(new Pair<>(widget, key));
                }
                configurations.remove(widget);
                saveChanges(configurations, changed);
            }
        }

        if (!changed.isEmpty())
        {
            notifyConfigurationUpdate(widget, null);
        }
    }

    public void setDefaultConfig(String widget, DictObj.Dict defaults)
    {
        ArrayList<Pair<String, String>> changed = new ArrayList<>();
        for (DictObj.Entry entry : defaults.entries())
        {
            if (setConfigNoSave(widget, entry.key, (String) entry.value, true))
            {
                changed.add(new Pair<>(widget, entry.key));
            }
        }

        if (!changed.isEmpty())
        {
            synchronized (lock)
            {
                saveChanges(configurations, changed);
            }
        }
    }

    public void setConfig(String widget, String key, String value)
    {
        if (setConfigNoSave(widget, key, value, false))
        {
            synchronized (lock)
            {
                saveChanges(configurations, Collections.singleton(new Pair<>(widget, key)));
            }
        }
    }

    public boolean setConfigNoSave(String widget, String key, String value, boolean isDefaultValue)
    {
        boolean changed = false;
        synchronized (lock)
        {
            DictObj.Dict widgetConfig = configurations.getDict(widget);
            if (widgetConfig == null)
            {
                widgetConfig = new DictObj.Dict();
                configurations.put(widget, widgetConfig);
            }

            DictObj.Dict values = widgetConfig.getDict(key);
            if (values == null)
            {
                values = new DictObj.Dict();
                values.put("value", value);
                values.put("default", value);
                widgetConfig.put(key, values);
                changed = true;
            }
            else
            {
                // override previous pair changing only one of its values
                if (!isDefaultValue && !values.get("value").equals(value))
                {
                    values.put("value", value);
                    changed = true;
                }
                if (isDefaultValue && !values.get("default").equals(value))
                {
                    values.put("default", value);
                    changed = true;
                }
            }
        }

        if (changed)
        {
            notifyConfigurationUpdate(widget, key);
        }
        return changed;
    }

    public void load()
    {
        StoreData store = StoreData.Factory.create(context, "configurations");
        Set<String> storeKeys = store.getAll();

        DictObj.Dict newconfig = new DictObj.Dict();

        for (String storeKey : storeKeys)
        {
            DictObj.Dict obj = store.getDict(storeKey);
            if (obj == null)
            {
                continue;
            }

            String widget = obj.getString("widget");
            String key = obj.getString("key");
            String value = obj.getString("value");
            String default_ = obj.getString("default");

            if (widget == null || key == null)
            {
                continue;
            }

            DictObj.Dict widgetConfig = newconfig.getDict(widget);
            if (widgetConfig == null)
            {
                widgetConfig = new DictObj.Dict();
                newconfig.put(widget, widgetConfig);
            }

            DictObj.Dict configDict = new DictObj.Dict();
            configDict.put("value", value);
            configDict.put("default", default_);

            widgetConfig.put(key, configDict);
        }

        synchronized (lock)
        {
            configurations = newconfig;
        }

        notifyConfigurationUpdate(null, null);
    }

    public void replaceConfiguration(DictObj.Dict newConfig)
    {
        ArrayList<Pair<String, String>> changed = new ArrayList<>();
        ArrayList<Pair<String, String>> changedExplicit = new ArrayList<>();
        synchronized (lock)
        {
            Pair<Set<String>, Set<String>> intersectionAndXor = Utils.intersectionAndXor(configurations.keyset(), newConfig.keyset());

            //all new widgets and removed widgets
            for (String widget : intersectionAndXor.second)
            {
                changed.add(new Pair<>(widget, null));
            }

            for (String widget : intersectionAndXor.first)
            {
                Pair<Set<String>, Set<String>> intersectionAndXorWidget = Utils.intersectionAndXor(configurations.getDict(widget).keyset(), newConfig.getDict(widget).keyset());

                //all new keys and removed keys
                for (String key : intersectionAndXorWidget.second)
                {
                    changed.add(new Pair<>(widget, key));
                }

                //all changed values or changed defaults
                for (String key : intersectionAndXorWidget.first)
                {
                    //only check changed value
                    if (!configurations.getDict(widget).getDict(key).getString("value").equals(newConfig.getDict(widget).getDict(key).getString("value")))
                    {
                        changed.add(new Pair<>(widget, key));
                    }

                    if (!configurations.getDict(widget).getDict(key).getString("default").equals(newConfig.getDict(widget).getDict(key).get("default")))
                    {
                        //don't override default
                        newConfig.getDict(widget).getDict(key).put("default", configurations.getDict(widget).getDict(key).getString("value"));
                    }
                }
            }

            for (Pair<String, String> change : changed)
            {
                if (change.second != null)
                {
                    changedExplicit.add(new Pair<>(change.first, change.second));
                }
                else
                {
                    // get deleted keys explicitly
                    for (String key : configurations.getDict(change.first).keyset())
                    {
                        changedExplicit.add(new Pair<>(change.first, key));
                    }
                }
            }

            configurations = newConfig;
        }

        synchronized (lock)
        {
            saveChanges(configurations, changedExplicit);
        }

        for (Pair<String, String> change : changed)
        {
            notifyConfigurationUpdate(change.first, change.second);
        }
    }

    private void notifyConfigurationUpdate(String widget, String key)
    {
        if (listener != null)
        {
            listener.onChange(widget, key);
        }
    }

    public DictObj.Dict getDict()
    {
        return configurations;
    }

    public static String buildKey(String widget, String key)
    {
        // has to be unique
        return widget.replace(":", ":_") + "::" + key.replace(":", ":_");
    }

    private void saveChanges(DictObj.Dict configDict, Collection<Pair<String, String>> changes)
    {
        StoreData store = StoreData.Factory.create(context, "configurations");
        for (Pair<String, String> change : changes)
        {
            String widget = change.first;
            String key = change.second;

            DictObj.Dict widgetConfig = configDict.getDict(widget);
            if (widgetConfig == null)
            {
                // deleted
                store.remove(buildKey(widget, key));
            }
            else
            {
                DictObj.Dict configObj = widgetConfig.getDict(key);
                if (configObj == null)
                {
                    // deleted
                    store.remove(buildKey(widget, key));
                }
                else
                {
                    DictObj.Dict obj = new DictObj.Dict();
                    obj.put("widget", widget);
                    obj.put("key", key);
                    obj.put("value", configObj.getString("value"));
                    obj.put("default", configObj.getString("default"));
                    store.put(buildKey(widget, key), obj);
                }
            }
        }
        store.apply();
    }
}
