package com.happy;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Tal on 14/01/2018.
 */
public class DynamicView
{
    private int id;
    public String type;
    public ArrayList<RemoteMethodCall> methodCalls = new ArrayList<>();
    public ArrayList<DynamicView> children = new ArrayList<>();
    public Object tag;
    public int view_id;
    public int xml_id;

    private static AtomicInteger id_counter = new AtomicInteger(1);
    private static int genId()
    {
        return id_counter.getAndIncrement();
    }

    public String toString()
    {
        String ret = id + " " + type;
        if(methodCalls.size() > 0)
        {
            ret += "(";
            for(RemoteMethodCall call : methodCalls)
            {
                ret += call.toString() + ", ";
            }
            ret += ")";
        }

        if(children.size() > 0)
        {
            ret += "{";
            for(DynamicView view : children)
            {
                ret += "("+view.toString() + "), ";
            }
            ret += "}";
        }
        return ret;
    }

    public DynamicView(String type)
    {
        this(genId(), type);
    }

    private DynamicView(int id, String type)
    {
        this(id, type, 0, 0);
    }

    public DynamicView(int id, String type, int view_id, int xml_id)
    {
        this.id = id;
        this.type = type;
        this.view_id = view_id;
        this.xml_id = xml_id;

        if(!Widget.typeToClass.containsKey(type) && !type.equals("*"))
        {
            throw new IllegalArgumentException("no such type: "+type);
        }
    }

    public int getId()
    {
        return id;
    }

    public static DynamicView fromJSON(String json, boolean extractAll) {
        try
        {
            return fromJSON(new JSONObject(json), extractAll);
        }
        catch (JSONException e)
        {
            e.printStackTrace();
            throw new IllegalArgumentException("json deserialization failed");
        }
    }
    private static DynamicView fromJSON(JSONObject obj, boolean extractAll) throws JSONException
    {
        int id;
        if(obj.has("id") && extractAll)
        {
            id = obj.getInt("id");
        }
        else
        {
            id = genId();
        }

        int xml_id = 0;
        int view_id = 0;
        if(obj.has("xmlId") && extractAll)
        {
            xml_id = obj.getInt("xmlId");
        }
        if(obj.has("viewId") && extractAll)
        {
            view_id = obj.getInt("viewId");
        }

        DynamicView view = new DynamicView(id, obj.getString("type"), view_id, xml_id);

        Log.d("HAPY", "building "+view.id+" "+view.type);

        if(obj.has("tag"))
        {
            view.tag = obj.get("tag");
        }

        if(obj.has("methodCalls"))
        {
            JSONArray callsarr = obj.getJSONArray("methodCalls");
            Log.d("HAPY", callsarr.length() + " calls");
            for (int i = 0; i < callsarr.length(); i++)
            {
                view.methodCalls.add(RemoteMethodCall.fromJSON(callsarr.getJSONObject(i)));
            }
        }
        if(obj.has("children"))
        {
            JSONArray childarr = obj.getJSONArray("children");
            Log.d("HAPY", childarr.length() + " children");
            for (int i = 0; i < childarr.length(); i++)
            {
                DynamicView child = DynamicView.fromJSON(childarr.getJSONObject(i), extractAll);
                Log.d("HAPY", "adding child: " + child.toString());
                view.children.add(child);
            }
        }
        return view;
    }

    private JSONObject toJSONObj() throws JSONException
    {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("type", type);
        obj.put("xmlId", xml_id);
        obj.put("viewId", view_id);
        if(tag != null)
        {
            obj.put("tag", tag);
        }
        if (!methodCalls.isEmpty())
        {
            JSONArray callarr = new JSONArray();
            for (RemoteMethodCall call : methodCalls)
            {
                callarr.put(call.toJSONObj());
            }
            obj.put("methodCalls", callarr);
        }
        if (!children.isEmpty())
        {
            JSONArray viewarr = new JSONArray();
            for (DynamicView view : children)
            {
                viewarr.put(view.toJSONObj());
            }
            obj.put("children", viewarr);
        }
        return obj;
    }
    public String toJSON()
    {
        try
        {
            return toJSONObj().toString(2);
        }
        catch (JSONException e)
        {
            e.printStackTrace();
            throw new IllegalArgumentException("json serialization failed");
        }
    }
}
