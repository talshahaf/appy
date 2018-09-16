package com.appy;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
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
        void onChange();
    }

    private ChangeListener listener;

    public void setListener(ChangeListener listener)
    {
        this.listener = listener;
    }

    public Configurations(Context context)
    {
        this.context = context;
    }

    public HashMap<String, Integer> listWidgets()
    {
        synchronized (lock)
        {
            HashMap<String, Integer> ret = new HashMap<>();
            for(Map.Entry<String, HashMap<String, Pair<String, String>>> entry : widgetConfigurations.entrySet())
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
            if(configs != null)
            {
                for(Map.Entry<String, Pair<String, String>> entry : configs.entrySet())
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
            if(configs != null)
            {
                Pair<String, String> values = configs.get(key);
                if(values != null)
                {
                    if(!values.first.equals(values.second))
                    {
                        changed = true;
                    }
                    configs.put(key, new Pair<>(values.second, values.second));
                }
            }

            if(changed)
            {
                serialized = serialize();
            }
        }

        if(changed)
        {
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
            if(configs != null)
            {
                for(String key : configs.keySet())
                {
                    Pair<String, String> values = configs.get(key);
                    if (values != null)
                    {
                        if(!values.first.equals(values.second))
                        {
                            changed = true;
                        }
                        configs.put(key, new Pair<>(values.second, values.second));
                    }
                }
            }

            if(changed)
            {
                serialized = serialize();
            }
        }

        if(changed)
        {
            save(serialized);
        }
    }

    public void setDefaultConfig(String widget, String[] keys, String[] values)
    {
        boolean changed = false;
        for(int i = 0; i < keys.length; i++)
        {
            if(setConfig(widget, keys[i], values[i], true))
            {
                changed = true;
            }
        }

        if(changed)
        {
            save(serialize());
        }
    }

    public void setConfig(String widget, String key, String value)
    {
        if(setConfig(widget, key, value, false))
        {
            save(serialize());
        }
    }

    public boolean setConfig(String widget, String key, String value, boolean defaultValue)
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
            if(values == null)
            {
                values = new Pair<>(value, value);
                changed = true;
            }
            else
            {
                // override previous pair changing only one of its values
                if(!defaultValue && !values.first.equals(value))
                {
                    changed = true;
                }
                if(defaultValue && !values.second.equals(value))
                {
                    changed = true;
                }
                values = new Pair<>(defaultValue ? values.first : value, defaultValue ? value : values.second);
            }
            widgetConfig.put(key, values);
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
                        values.put("value",   pair2.getValue().first);
                        values.put("default", pair2.getValue().second);
                        widgetObj.put(pair2.getKey(), values);
                    }
                    obj.put(pair.getKey(), widgetObj);
                }
                return obj.toString();
            }
        }
        catch(JSONException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public void load()
    {
        SharedPreferences sharedPref = context.getSharedPreferences("appy", Context.MODE_PRIVATE);
        String config = sharedPref.getString("configurations", null);
        if(config == null)
        {
            return;
        }

        try
        {
            synchronized (lock)
            {
                widgetConfigurations = new HashMap<>();
                JSONObject obj = new JSONObject(config);
                Iterator<String> it = obj.keys();
                while(it.hasNext())
                {
                    String widgetKey = it.next();
                    HashMap<String, Pair<String, String>> configs = new HashMap<>();
                    JSONObject widgetObj = obj.getJSONObject(widgetKey);
                    Iterator<String> it2 = widgetObj.keys();
                    while(it2.hasNext())
                    {
                        String configKey = it2.next();
                        JSONObject values = widgetObj.getJSONObject(configKey);
                        configs.put(configKey, new Pair<>(values.getString("value"), values.getString("default")));
                    }
                    widgetConfigurations.put(widgetKey, configs);
                }
            }
        }
        catch(JSONException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private void save(String serialized)
    {
        SharedPreferences sharedPref = context.getSharedPreferences("appy", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("configurations", serialized);
        editor.apply();

        if(listener != null)
        {
            listener.onChange();
        }
    }
}
