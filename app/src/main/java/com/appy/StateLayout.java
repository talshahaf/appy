package com.appy;

import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class StateLayout
{
    public HashMap<String, String> globals = new HashMap<>();
    public HashMap<String, HashMap<String, String>> nonlocals = new HashMap<>();
    public HashMap<String, HashMap<String, HashMap<String, String>>> locals = new HashMap<>();

    public static Set<String> listScopes()
    {
        HashSet<String> result = new HashSet<>();
        result.add("globals");
        result.add("nonlocals");
        result.add("locals");
        return result;
    }

    public static int getDepth(String scope)
    {
        if (scope.equals("globals"))
        {
            return 1;
        }

        if (scope.equals("nonlocals"))
        {
            return 2;
        }

        if (scope.equals("locals"))
        {
            return 3;
        }

        throw new IllegalArgumentException("unknown scope");
    }

    public String getValue(List<String> keyPath, String key)
    {
        return getValue(keyPath.get(0),
                keyPath.size() > 1 ? keyPath.get(1) : null,
                keyPath.size() > 2 ? keyPath.get(2) : null,
                key);
    }

    public Set<String> listDict(List<String> keyPath)
    {
        return listDict(keyPath.get(0), keyPath.size() > 1 ? keyPath.get(1) : null, keyPath.size() > 2 ? keyPath.get(2) : null);
    }

    public Set<String> listDict(String scope, String widget, String widgetId)
    {
        if (scope.equals("globals"))
        {
            if (widget != null || widgetId != null)
            {
                throw new IllegalArgumentException("global has no more levels");
            }
            return globals.keySet();
        }

        if (scope.equals("nonlocals"))
        {
            if (widgetId != null)
            {
                throw new IllegalArgumentException("nonlocals has no more levels");
            }

            if (widget == null)
            {
                return nonlocals.keySet();
            }
            else
            {
                HashMap<String, String> map = nonlocals.get(widget);
                if (map == null)
                {
                    return new HashSet<>();
                }
                return map.keySet();
            }
        }

        if (scope.equals("locals"))
        {
            if (widget == null)
            {
                return locals.keySet();
            }
            else if (widgetId == null)
            {
                HashMap<String, HashMap<String, String>> map = locals.get(widget);
                if (map == null)
                {
                    return new HashSet<>();
                }
                return map.keySet();
            }
            else
            {
                HashMap<String, HashMap<String, String>> map1 = locals.get(widget);
                if (map1 == null)
                {
                    return new HashSet<>();
                }
                HashMap<String, String> map2 = map1.get(widgetId);
                if (map2 == null)
                {
                    return new HashSet<>();
                }
                return map2.keySet();
            }
        }

        throw new IllegalArgumentException("unknown scope");
    }

    public String getValue(String scope, String widget, String widgetId, String key)
    {
        if (scope.equals("globals"))
        {
            if (widget != null || widgetId != null)
            {
                throw new IllegalArgumentException("global has no more levels");
            }
            return globals.get(key);
        }

        if (scope.equals("nonlocals"))
        {
            if (widgetId != null)
            {
                throw new IllegalArgumentException("nonlocals has no more levels");
            }

            if (widget == null)
            {
                throw new IllegalArgumentException("nonlocals has more levels");
            }

            return nonlocals.get(widget).get(key);
        }

        if (scope.equals("locals"))
        {
            if (widget == null || widgetId == null)
            {
                throw new IllegalArgumentException("locals has more levels");
            }

            return locals.get(widget).get(widgetId).get(key);
        }

        throw new IllegalArgumentException("unknown scope");
    }

    public static StateLayout deserialize(JSONObject obj) throws JSONException
    {
        Iterator<String> it;
        StateLayout stateLayout = new StateLayout();

        JSONObject globals = obj.getJSONObject("globals");
        it = globals.keys();
        while (it.hasNext())
        {
            String key = it.next();
            stateLayout.globals.put(key, globals.getString(key));
        }

        JSONObject nonlocals = obj.getJSONObject("nonlocals");
        it = nonlocals.keys();
        while (it.hasNext())
        {
            String widget = it.next();

            HashMap<String, String> tmpNonLocalsWidget = new HashMap<>();
            stateLayout.nonlocals.put(widget, tmpNonLocalsWidget);

            JSONObject nonlocalsWidget = nonlocals.getJSONObject(widget);

            Iterator<String> it2 = nonlocalsWidget.keys();
            while (it2.hasNext())
            {
                String key = it2.next();
                tmpNonLocalsWidget.put(key, nonlocalsWidget.getString(key));
            }
        }

        JSONObject locals = obj.getJSONObject("locals");
        it = locals.keys();
        while (it.hasNext())
        {
            String widget = it.next();

            HashMap<String, HashMap<String, String>> tmpLocalsWidget = new HashMap<>();
            stateLayout.locals.put(widget, tmpLocalsWidget);

            JSONObject localsWidget = locals.getJSONObject(widget);

            Iterator<String> it2 = localsWidget.keys();
            while (it2.hasNext())
            {
                String widgetId = it2.next();

                HashMap<String, String> tmpLocalsWidgetWidgetId = new HashMap<>();
                tmpLocalsWidget.put(widgetId, tmpLocalsWidgetWidgetId);

                JSONObject localsWidgetWidgetId = localsWidget.getJSONObject(widgetId);

                Iterator<String> it3 = localsWidgetWidgetId.keys();
                while (it3.hasNext())
                {
                    String key = it3.next();
                    tmpLocalsWidgetWidgetId.put(key, localsWidgetWidgetId.getString(key));
                }
            }
        }

        return stateLayout;
    }

    public static StateLayout deserialize(String json)
    {
        try
        {
            JSONObject obj = new JSONObject(json);
            return deserialize(obj);
        }
        catch (JSONException e)
        {
            throw new IllegalArgumentException("json serialization failed", e);
        }
    }
}
