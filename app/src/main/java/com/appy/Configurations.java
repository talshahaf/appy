package com.appy;

import android.content.Context;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import kotlin.Triple;

public class Configurations
{
    private final Object lock = new Object();
    // widget -> (key -> {value: nonlocal value, instance_values: id -> value, default: default value, description))
    //private HashMap<String, HashMap<String, Pair<String, String>>> widgetConfigurations = new HashMap<>();
    private DictObj.Dict configurations = new DictObj.Dict();

    public static final int NONLOCAL_ID = -1;

    private final Context context;

    interface ChangeListener
    {
        void onChange(String widget, String key, int widgetid);
    }

    private final ChangeListener listener;

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

    public HashMap<String, Triple<String, String, Boolean>> getValues(String widget, int widgetId)
    {
        synchronized (lock)
        {
            DictObj.Dict configs = configurations.getDict(widget);
            HashMap<String, Triple<String, String, Boolean>> values = new HashMap<>();
            if (configs != null)
            {
                for (DictObj.Entry entry : configs.entries())
                {
                    DictObj.Dict config = (DictObj.Dict)entry.value;
                    String description = config.getString("description");
                    String value = config.getString("value");
                    boolean instanceValue = false;
                    //use instance value if applicable
                    if (widgetId != NONLOCAL_ID)
                    {
                        DictObj.Dict instanceValues = config.getDict("instance_values");
                        if (instanceValues != null && instanceValues.hasKey(widgetId+""))
                        {
                            value = instanceValues.getString(widgetId+"");
                            instanceValue = true;
                        }
                    }
                    values.put(entry.key, new Triple<>(description, value, instanceValue));
                }
            }
            return values;
        }
    }

    public void resetKey(String widget, String key, int widgetId)
    {
        boolean changed = false;
        synchronized (lock)
        {
            DictObj.Dict configs = configurations.getDict(widget);
            if (configs != null)
            {
                DictObj.Dict config = configs.getDict(key);
                if (config != null)
                {
                    if (widgetId == NONLOCAL_ID)
                    {
                        String currentValue = config.getString("value");
                        String defaultValue = config.getString("default");
                        if (!currentValue.equals(defaultValue))
                        {
                            config.put("value", defaultValue);
                            changed = true;
                        }
                    }
                    else
                    {
                        DictObj.Dict instanceValues = config.getDict("instance_values");
                        if (instanceValues != null && instanceValues.hasKey(widgetId+""))
                        {
                            instanceValues.remove(widgetId+"");
                            changed = true;
                        }
                    }
                }
            }

            if (changed)
            {
                saveChanges(configurations, Collections.singleton(new Triple<>(widget, key, widgetId)));
            }
        }

        if (changed)
        {
            notifyConfigurationUpdate(widget, key, widgetId);
        }
    }

    public void resetWidgetInstance(String widget, int widgetId)
    {
        if (widgetId == NONLOCAL_ID)
        {
            throw new RuntimeException("cannot reset widget instance without widgetId");
        }

        ArrayList<Triple<String, String, Integer>> changed = new ArrayList<>();
        synchronized (lock)
        {
            DictObj.Dict configs = configurations.getDict(widget);
            if (configs != null)
            {
                for (DictObj.Entry entry : configs.entries())
                {
                    DictObj.Dict config = (DictObj.Dict)entry.value;
                    DictObj.Dict instanceValues = config.getDict("instance_values");
                    if (instanceValues != null && instanceValues.hasKey(widgetId+""))
                    {
                        instanceValues.remove(widgetId+"");
                        changed.add(new Triple<>(widget, entry.key, widgetId));
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
            notifyConfigurationUpdate(widget, null, widgetId);
        }
    }

    public void resetWidget(String widget)
    {
        ArrayList<Triple<String, String, Integer>> changed = new ArrayList<>();
        synchronized (lock)
        {
            DictObj.Dict configs = configurations.getDict(widget);
            if (configs != null)
            {
                for (DictObj.Entry entry : configs.entries())
                {
                    DictObj.Dict config = (DictObj.Dict)entry.value;
                    if (config != null)
                    {
                        String currentValue = config.getString("value");
                        String defaultValue = config.getString("default");
                        boolean addChanged = false;
                        if (!currentValue.equals(defaultValue))
                        {
                            config.put("value", defaultValue);
                            addChanged = true;
                        }
                        DictObj.Dict instanceValues = config.getDict("instance_values");
                        if (instanceValues != null && instanceValues.size() > 0)
                        {
                            instanceValues.removeAll();
                            addChanged = true;
                        }
                        if (addChanged)
                        {
                            changed.add(new Triple<>(widget, entry.key, NONLOCAL_ID));
                        }
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
            notifyConfigurationUpdate(widget, null, NONLOCAL_ID);
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
                saveChanges(configurations, Collections.singleton(new Triple<>(widget, key, NONLOCAL_ID)));
            }
        }

        if (changed)
        {
            notifyConfigurationUpdate(widget, key, NONLOCAL_ID);
        }
    }

    public void deleteWidget(String widget)
    {
        ArrayList<Triple<String, String, Integer>> changed = new ArrayList<>();
        synchronized (lock)
        {
            if (configurations.hasKey(widget))
            {
                for (String key : configurations.getDict(widget).keyset())
                {
                    changed.add(new Triple<>(widget, key, NONLOCAL_ID));
                }
                configurations.remove(widget);
                saveChanges(configurations, changed);
            }
        }

        if (!changed.isEmpty())
        {
            notifyConfigurationUpdate(widget, null, NONLOCAL_ID);
        }
    }

    public void setDefaultConfig(String widget, DictObj.Dict defaults)
    {
        ArrayList<Triple<String, String, Integer>> changed = new ArrayList<>();
        for (DictObj.Entry entry : defaults.entries())
        {
            String def = ((DictObj.Dict)entry.value).getString("default");
            String desc = ((DictObj.Dict)entry.value).getString("description");
            if (setConfigOrDefaultNoSave(widget, entry.key, def, desc, true))
            {
                changed.add(new Triple<>(widget, entry.key, NONLOCAL_ID));
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

    public void setConfig(String widget, int widgetId, String key, String value)
    {
        boolean changed;
        if (widgetId == NONLOCAL_ID)
        {
            changed = setConfigOrDefaultNoSave(widget, key, value, null, false);
        }
        else
        {
            changed = setConfigInstanceValue(widget, widgetId, key, value);
        }
        if (changed)
        {
            synchronized (lock)
            {
                saveChanges(configurations, Collections.singleton(new Triple<>(widget, key, widgetId)));
            }
            notifyConfigurationUpdate(widget, key, widgetId);
        }
    }

    public boolean setConfigInstanceValue(String widget, int widgetId, String key, String value)
    {
        boolean changed = false;
        synchronized (lock)
        {
            DictObj.Dict widgetConfig = configurations.getDict(widget);
            if (widgetConfig != null)
            {
                DictObj.Dict config = widgetConfig.getDict(key);
                if (config != null)
                {
                    DictObj.Dict instanceValues = config.getDict("instance_values");
                    if (instanceValues == null)
                    {
                        config.put("instance_values", new DictObj.Dict());
                        instanceValues = config.getDict("instance_values");
                    }

                    if (!instanceValues.hasKey(widgetId+"") || !instanceValues.getString(widgetId+"").equals(value))
                    {
                        instanceValues.put(widgetId+"", value);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private boolean setConfigOrDefaultNoSave(String widget, String key, String value, String description, boolean isDefaultValue)
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

            DictObj.Dict config = widgetConfig.getDict(key);
            if (config == null)
            {
                config = new DictObj.Dict();
                config.put("value", value);
                config.put("default", value);
                config.put("description", description);
                config.put("instance_values", new DictObj.Dict());
                widgetConfig.put(key, config);
                changed = true;
            }
            else
            {
                // override previous pair changing only one of its values
                if (!isDefaultValue && !Objects.equals(config.get("value"), value))
                {
                    config.put("value", value);
                    changed = true;
                }
                if (isDefaultValue && !Objects.equals(config.get("default"), value))
                {
                    config.put("default", value);
                    changed = true;
                }
                if (isDefaultValue && !Objects.equals(config.get("description"), description))
                {
                    config.put("description", description);
                    changed = true;
                }
            }
        }
        return changed;
    }

    public void replaceConfiguration(DictObj.Dict newConfig)
    {
        ArrayList<Triple<String, String, Integer>> changed = new ArrayList<>();
        ArrayList<Triple<String, String, Integer>> changedExplicit = new ArrayList<>();
        synchronized (lock)
        {
            Pair<Set<String>, Set<String>> intersectionAndXor = Utils.intersectionAndXor(configurations.keyset(), newConfig.keyset());

            //all new widgets and removed widgets
            for (String widget : intersectionAndXor.second)
            {
                changed.add(new Triple<>(widget, null, null));
            }

            for (String widget : intersectionAndXor.first)
            {
                Pair<Set<String>, Set<String>> intersectionAndXorWidget = Utils.intersectionAndXor(configurations.getDict(widget).keyset(), newConfig.getDict(widget).keyset());

                //all new keys and removed keys
                for (String key : intersectionAndXorWidget.second)
                {
                    changed.add(new Triple<>(widget, key, null));
                }

                //all changed values or changed defaults
                for (String key : intersectionAndXorWidget.first)
                {
                    //only check changed value
                    if (!configurations.getDict(widget).getDict(key).getString("value").equals(newConfig.getDict(widget).getDict(key).getString("value")))
                    {
                        changed.add(new Triple<>(widget, key, NONLOCAL_ID));
                    }

                    //don't override default
                    newConfig.getDict(widget).getDict(key).put("default", configurations.getDict(widget).getDict(key).getString("default"));
                    newConfig.getDict(widget).getDict(key).put("description", configurations.getDict(widget).getDict(key).getString("description"));

                    DictObj.Dict oldInstanceValues = configurations.getDict(widget).getDict(key).getDict("instance_values");
                    DictObj.Dict newInstanceValues = newConfig.getDict(widget).getDict(key).getDict("instance_values");

                    Pair<Set<String>, Set<String>> intersectionAndXorInstanceValues = Utils.intersectionAndXor(oldInstanceValues != null ? oldInstanceValues.keyset() : new HashSet<>(), newInstanceValues != null ? newInstanceValues.keyset() : new HashSet<>());

                    // all new widget ids and removed widget ids
                    for (String widgetId : intersectionAndXorInstanceValues.second)
                    {
                        changed.add(new Triple<>(widget, key, Integer.parseInt(widgetId)));
                    }

                    //all changed widget ids
                    for (String widgetId : intersectionAndXorInstanceValues.first)
                    {
                        changed.add(new Triple<>(widget, key, Integer.parseInt(widgetId)));
                    }
                }
            }

            for (Triple<String, String, Integer> change : changed)
            {
                if (change.component2() != null && change.component3() != null)
                {
                    changedExplicit.add(new Triple<>(change.component1(), change.component2(), change.component3()));
                }
                if (change.component2() == null)
                {
                    // add deleted and new keys explicitly
                    for (DictObj.Dict config : new DictObj.Dict[]{ configurations, newConfig })
                    {
                        DictObj.Dict widgetConfig = config.getDict(change.component1());
                        if (widgetConfig != null)
                        {
                            for (String key : widgetConfig.keyset())
                            {
                                changedExplicit.add(new Triple<>(change.component1(), key, NONLOCAL_ID));
                                DictObj.Dict values = config.getDict(change.component1()).getDict(key).getDict("instance_values");
                                if (values != null)
                                {
                                    for (String widgetId : values.keyset())
                                    {
                                        changedExplicit.add(new Triple<>(change.component1(), key, Integer.parseInt(widgetId)));
                                    }
                                }
                            }
                        }
                    }
                }
                if (change.component2() != null && change.component3() == null)
                {
                    for (DictObj.Dict config : new DictObj.Dict[]{ configurations, newConfig })
                    {
                        DictObj.Dict widgetConfig = config.getDict(change.component1());
                        if (widgetConfig != null)
                        {
                            // mark deleted and new instance values explicitly
                            DictObj.Dict keyConfig = widgetConfig.getDict(change.component2());
                            if (keyConfig != null)
                            {
                                DictObj.Dict values = keyConfig.getDict("instance_values");
                                if (values != null)
                                {
                                    for (String widgetId : values.keyset())
                                    {
                                        changedExplicit.add(new Triple<>(change.component1(), change.component2(), Integer.parseInt(widgetId)));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            configurations = newConfig;
        }

        synchronized (lock)
        {
            saveChanges(configurations, changedExplicit);
        }

        for (Triple<String, String, Integer> change : changed)
        {
            notifyConfigurationUpdate(change.component1(), change.component2(), change.component3() != null ? change.component3() : NONLOCAL_ID);
        }
    }

    private void notifyConfigurationUpdate(String widget, String key, int widgetId)
    {
        if (listener != null)
        {
            listener.onChange(widget, key, widgetId);
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

    public static String buildKey(String widget, String key, int widgetId)
    {
        // has to be unique
        return widget.replace(":", ":_") + "::" + key.replace(":", ":_") + "::" + widgetId;
    }

    public void load()
    {
        StoreData instanceValuesStore = StoreData.Factory.create(context, "configurations_instance_values");
        StoreData store = StoreData.Factory.create(context, "configurations");

        Set<String> instanceValuesStoreKeys = instanceValuesStore.getAll();
        Set<String> storeKeys = store.getAll();

        DictObj.Dict newconfig = new DictObj.Dict();
        DictObj.Dict allInstanceValues = new DictObj.Dict();

        //only instance values
        for (String storeKey : instanceValuesStoreKeys)
        {
            DictObj.Dict obj = instanceValuesStore.getDict(storeKey);
            if (obj == null)
            {
                continue;
            }

            String widget = obj.getString("widget");
            String key = obj.getString("key");
            int widgetId = obj.getInt("widget_id", NONLOCAL_ID);
            String value = obj.getString("value");

            if (widget == null || key == null || widgetId == NONLOCAL_ID)
            {
                continue;
            }

            DictObj.Dict configs = allInstanceValues.getDict(widget);
            if (configs == null)
            {
                allInstanceValues.put(widget, new DictObj.Dict());
                configs = allInstanceValues.getDict(widget);
            }

            DictObj.Dict config = configs.getDict(key);
            if (config == null)
            {
                configs.put(key, new DictObj.Dict());
                config = configs.getDict(key);
            }

            config.put(widgetId+"", value);
        }

        //only values
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
            String description = obj.getString("description");

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
            configDict.put("description", description);

            DictObj.Dict instanceValues = new DictObj.Dict();

            DictObj.Dict instanceValueConfigs = allInstanceValues.getDict(widget);
            if (instanceValueConfigs != null)
            {
                DictObj.Dict instanceValueConfig = instanceValueConfigs.getDict(key);
                if (instanceValueConfig != null)
                {
                    for (DictObj.Entry entry : instanceValueConfig.entries())
                    {
                        instanceValues.put(entry.key, (String)entry.value);
                    }
                }
            }

            configDict.put("instance_values", instanceValues);
            widgetConfig.put(key, configDict);
        }

        synchronized (lock)
        {
            configurations = newconfig;
        }

        notifyConfigurationUpdate(null, null, NONLOCAL_ID);
    }

    private void saveChanges(DictObj.Dict configDict, Collection<Triple<String, String, Integer>> changes)
    {
        StoreData instanceValuesStore = StoreData.Factory.create(context, "configurations_instance_values");
        StoreData store = StoreData.Factory.create(context, "configurations");
        for (Triple<String, String, Integer> change : changes)
        {
            String widget = change.component1();
            String key = change.component2();
            int widgetId = change.component3();

            DictObj.Dict widgetConfig = configDict.getDict(widget);
            if (widgetConfig == null)
            {
                // deleted
                store.remove(buildKey(widget, key));
                instanceValuesStore.remove(buildKey(widget, key, widgetId));
            }
            else
            {
                DictObj.Dict configObj = widgetConfig.getDict(key);
                if (configObj == null)
                {
                    // deleted
                    store.remove(buildKey(widget, key));
                    instanceValuesStore.remove(buildKey(widget, key, widgetId));
                }
                else
                {
                    if (widgetId == NONLOCAL_ID)
                    {
                        DictObj.Dict obj = new DictObj.Dict();
                        obj.put("widget", widget);
                        obj.put("key", key);
                        obj.put("value", configObj.getString("value"));
                        obj.put("default", configObj.getString("default"));
                        obj.put("description", configObj.getString("description"));
                        store.put(buildKey(widget, key), obj);
                    }
                    else
                    {
                        DictObj.Dict instanceValues = configObj.getDict("instance_values");
                        if (instanceValues == null)
                        {
                            // deleted
                            instanceValuesStore.remove(buildKey(widget, key, widgetId));
                        }
                        else
                        {
                            if (!instanceValues.hasKey(widgetId+""))
                            {
                                //deleted
                                instanceValuesStore.remove(buildKey(widget, key, widgetId));
                            }
                            else
                            {
                                DictObj.Dict obj = new DictObj.Dict();
                                obj.put("widget", widget);
                                obj.put("key", key);
                                obj.put("widget_id", widgetId);
                                obj.put("value", instanceValues.getString(widgetId + ""));
                                instanceValuesStore.put(buildKey(widget, key, widgetId), obj);
                            }
                        }
                    }
                }
            }
        }
        instanceValuesStore.apply();
        store.apply();
    }
}
