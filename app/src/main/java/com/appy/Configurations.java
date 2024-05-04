package com.appy;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Configurations
{
    private final Object lock = new Object();
    // widget -> key -> (current value, default value)
    private HashMap<String, HashMap<String, Pair<String, String>>> widgetConfigurations = new HashMap<>();

    private Context context;

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
            for (Map.Entry<String, HashMap<String, Pair<String, String>>> entry : widgetConfigurations.entrySet())
            {
                ret.put(entry.getKey(), entry.getValue().size());
            }
            return ret;
        }
    }

    public HashMap<String, String> getValues(String widget)
    {
        synchronized (lock)
        {
            HashMap<String, Pair<String, String>> configs = widgetConfigurations.get(widget);
            HashMap<String, String> values = new HashMap<>();
            if (configs != null)
            {
                for (Map.Entry<String, Pair<String, String>> entry : configs.entrySet())
                {
                    values.put(entry.getKey(), entry.getValue().first);
                }
            }
            return values;
        }
    }

    public void resetKey(String widget, String key)
    {
        boolean changed = false;
        String serialized = null;
        synchronized (lock)
        {
            HashMap<String, Pair<String, String>> configs = widgetConfigurations.get(widget);
            if (configs != null)
            {
                Pair<String, String> values = configs.get(key);
                if (values != null)
                {
                    if (!values.first.equals(values.second))
                    {
                        changed = true;
                    }
                    configs.put(key, new Pair<>(values.second, values.second));
                }
            }

            if (changed)
            {
                serialized = serialize();
            }
        }

        if (changed)
        {
            configurationUpdate(widget, key);
            save(serialized);
        }
    }

    public void resetWidget(String widget)
    {
        boolean changed = false;
        String serialized = null;
        synchronized (lock)
        {
            HashMap<String, Pair<String, String>> configs = widgetConfigurations.get(widget);
            if (configs != null)
            {
                for (String key : configs.keySet())
                {
                    Pair<String, String> values = configs.get(key);
                    if (values != null)
                    {
                        if (!values.first.equals(values.second))
                        {
                            changed = true;
                        }
                        configs.put(key, new Pair<>(values.second, values.second));
                    }
                }
            }

            if (changed)
            {
                serialized = serialize();
            }
        }

        if (changed)
        {
            configurationUpdate(widget, null);
            save(serialized);
        }
    }

    public void deleteKey(String widget, String key)
    {
        boolean changed = false;
        String serialized = null;
        synchronized (lock)
        {
            HashMap<String, Pair<String, String>> configs = widgetConfigurations.get(widget);
            if (configs != null)
            {
                if (configs.containsKey(key))
                {
                    configs.remove(key);
                    changed = true;
                    serialized = serialize();
                }
            }
        }

        if (changed)
        {
            configurationUpdate(widget, key);
            save(serialized);
        }
    }

    public void deleteWidget(String widget)
    {
        boolean changed = false;
        String serialized = null;
        synchronized (lock)
        {
            if (widgetConfigurations.containsKey(widget))
            {
                widgetConfigurations.remove(widget);
                changed = true;
                serialized = serialize();
            }
        }

        if (changed)
        {
            configurationUpdate(widget, null);
            save(serialized);
        }
    }

    public void setDefaultConfig(String widget, String[] keys, String[] values)
    {
        boolean changed = false;
        for (int i = 0; i < keys.length; i++)
        {
            if (setConfigNoSave(widget, keys[i], values[i], true))
            {
                changed = true;
            }
        }

        if (changed)
        {
            save(serialize());
        }
    }

    public void setConfig(String widget, String key, String value)
    {
        if (setConfigNoSave(widget, key, value, false))
        {
            save(serialize());
        }
    }

    public boolean setConfigNoSave(String widget, String key, String value, boolean defaultValue)
    {
        boolean changed = false;
        synchronized (lock)
        {
            HashMap<String, Pair<String, String>> widgetConfig = widgetConfigurations.get(widget);
            if (widgetConfig == null)
            {
                widgetConfig = new HashMap<>();
                widgetConfigurations.put(widget, widgetConfig);
            }

            Pair<String, String> values = widgetConfig.get(key);
            if (values == null)
            {
                values = new Pair<>(value, value);
                changed = true;
            }
            else
            {
                // override previous pair changing only one of its values
                if (!defaultValue && !values.first.equals(value))
                {
                    changed = true;
                }
                if (defaultValue && !values.second.equals(value))
                {
                    changed = true;
                }
                values = new Pair<>(defaultValue ? values.first : value, defaultValue ? value : values.second);
            }
            widgetConfig.put(key, values);
        }

        if (changed)
        {
            configurationUpdate(widget, key);
        }
        return changed;
    }

    public String serialize()
    {
        try
        {
            synchronized (lock)
            {
                JSONObject obj = new JSONObject();
                for (Map.Entry<String, HashMap<String, Pair<String, String>>> pair : widgetConfigurations.entrySet())
                {
                    JSONObject widgetObj = new JSONObject();
                    for (Map.Entry<String, Pair<String, String>> pair2 : pair.getValue().entrySet())
                    {
                        JSONObject values = new JSONObject();
                        values.put("value", pair2.getValue().first);
                        values.put("default", pair2.getValue().second);
                        widgetObj.put(pair2.getKey(), values);
                    }
                    obj.put(pair.getKey(), widgetObj);
                }
                return obj.toString();
            }
        }
        catch (JSONException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public static HashMap<String, HashMap<String, Pair<String, String>>> deserialize(String data)
    {
        try
        {
            HashMap<String, HashMap<String, Pair<String, String>>> result = new HashMap<>();
            JSONObject obj = new JSONObject(data);
            Iterator<String> it = obj.keys();
            while (it.hasNext())
            {
                String widgetKey = it.next();
                HashMap<String, Pair<String, String>> configs = new HashMap<>();
                JSONObject widgetObj = obj.getJSONObject(widgetKey);
                Iterator<String> it2 = widgetObj.keys();
                while (it2.hasNext())
                {
                    String configKey = it2.next();
                    JSONObject values = widgetObj.getJSONObject(configKey);
                    configs.put(configKey, new Pair<>(values.getString("value"), values.getString("default")));
                }
                result.put(widgetKey, configs);
            }

            return result;
        }
        catch (JSONException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public void load()
    {
        SharedPreferences sharedPref = context.getSharedPreferences("appy", Context.MODE_PRIVATE);
        String config = sharedPref.getString("configurations", null);
        if (config == null)
        {
            return;
        }

        HashMap<String, HashMap<String, Pair<String, String>>> result = deserialize(config);
        synchronized (lock)
        {
            widgetConfigurations = result;
        }

        configurationUpdate(null, null);
    }

    public void replaceConfiguration(HashMap<String, HashMap<String, Pair<String, String>>> newConfig)
    {
        ArrayList<Pair<String, String>> changed = new ArrayList<>();
        synchronized (lock)
        {
            Pair<Set<String>, Set<String>> intersectionAndXor = Utils.intersectionAndXor(widgetConfigurations.keySet(), newConfig.keySet());

            //all new widgets and removed widgets
            for (String widget : intersectionAndXor.second)
            {
                changed.add(new Pair<>(widget, null));
            }

            for (String widget : intersectionAndXor.first)
            {
                Pair<Set<String>, Set<String>> intersectionAndXorWidget = Utils.intersectionAndXor(widgetConfigurations.get(widget).keySet(), newConfig.get(widget).keySet());

                //all new keys and removed keys
                for (String key : intersectionAndXorWidget.second)
                {
                    changed.add(new Pair<>(widget, key));
                }

                //all changed values or changed defaults
                for (String key : intersectionAndXorWidget.first)
                {
                    //only check changed value
                    if (!widgetConfigurations.get(widget).get(key).first.equals(newConfig.get(widget).get(key).first))
                    {
                        changed.add(new Pair<>(widget, key));
                    }

                    if (!widgetConfigurations.get(widget).get(key).second.equals(newConfig.get(widget).get(key).second))
                    {
                        //don't override default
                        newConfig.get(widget).put(key,
                                new Pair<>(newConfig.get(widget).get(key).first, widgetConfigurations.get(widget).get(key).second));
                    }
                }
            }

            widgetConfigurations = newConfig;
        }

        for (Pair<String, String> change : changed)
        {
            configurationUpdate(change.first, change.second);
        }
    }

    private void configurationUpdate(String widget, String key)
    {
        if (listener != null)
        {
            listener.onChange(widget, key);
        }
    }

    private void save(String serialized)
    {
        SharedPreferences sharedPref = context.getSharedPreferences("appy", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("configurations", serialized);
        editor.apply();
    }
}
