package com.appy;

import android.content.Context;
import android.util.Pair;

import java.util.ArrayList;
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
        DictObj.Dict copy = null;
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
                copy = (DictObj.Dict) configurations.copy(false);
            }
        }

        if (changed)
        {
            configurationUpdate(widget, key);
            save(copy);
        }
    }

    public void resetWidget(String widget)
    {
        boolean changed = false;
        DictObj.Dict copy = null;
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
                            changed = true;
                        }
                        values.put("value", defaultValue);
                    }
                }
            }

            if (changed)
            {
                copy = (DictObj.Dict) configurations.copy(false);
            }
        }

        if (changed)
        {
            configurationUpdate(widget, null);
            save(copy);
        }
    }

    public void deleteKey(String widget, String key)
    {
        boolean changed = false;
        DictObj.Dict copy = null;
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
                copy = (DictObj.Dict) configurations.copy(false);
            }
        }

        if (changed)
        {
            configurationUpdate(widget, key);
            save(copy);
        }
    }

    public void deleteWidget(String widget)
    {
        boolean changed = false;
        DictObj.Dict copy = null;
        synchronized (lock)
        {
            if (configurations.hasKey(widget))
            {
                configurations.remove(widget);
                copy = (DictObj.Dict) configurations.copy(false);
                changed = true;
            }
        }

        if (changed)
        {
            configurationUpdate(widget, null);
            save(copy);
        }
    }

    public void setDefaultConfig(String widget, DictObj.Dict defaults)
    {
        boolean changed = false;
        for (DictObj.Entry entry : defaults.entries())
        {
            if (setConfigNoSave(widget, entry.key, (String) entry.value, true))
            {
                changed = true;
            }
        }

        if (changed)
        {
            DictObj.Dict copy;
            synchronized (lock)
            {
                copy = (DictObj.Dict) configurations.copy(false);
            }
            save(copy);
        }
    }

    public void setConfig(String widget, String key, String value)
    {
        if (setConfigNoSave(widget, key, value, false))
        {
            DictObj.Dict copy;
            synchronized (lock)
            {
                copy = (DictObj.Dict) configurations.copy(false);
            }
            save(copy);
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
            configurationUpdate(widget, key);
        }
        return changed;
    }

    public void load()
    {
        StoreData store = StoreData.Factory.create(context, "configuration");
        DictObj.Dict config = store.getDict("configurations");
        if (config == null)
        {
            return;
        }

        synchronized (lock)
        {
            configurations = config;
        }

        configurationUpdate(null, null);
    }

    public void replaceConfiguration(DictObj.Dict newConfig)
    {
        ArrayList<Pair<String, String>> changed = new ArrayList<>();
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

            configurations = newConfig;
        }

        for (Pair<String, String> change : changed)
        {
            configurationUpdate(change.first, change.second);
        }

        DictObj.Dict copy;
        synchronized (lock)
        {
            copy = (DictObj.Dict) configurations.copy(false);
        }
        save(copy);
    }

    private void configurationUpdate(String widget, String key)
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

    private void save(DictObj.Dict dict)
    {
        StoreData store = StoreData.Factory.create(context, "configuration");
        store.put("configurations", dict);
        store.apply();
    }
}
