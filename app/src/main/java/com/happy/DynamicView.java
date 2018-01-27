package com.happy;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

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
    public int xml_id;
    public int view_id;

    private static int id_counter = 0;
    private static int genId()
    {
        return id_counter++;
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

    public DynamicView(String type) throws Exception
    {
        this(genId(), type);
    }

    private DynamicView(int id, String type) throws Exception
    {
        this.id = id;
        if(!Widget.typeToLayout.containsKey(type))
        {
            throw new Exception("no such type: "+type);
        }
        this.type = type;
    }

    public int getId()
    {
        return id;
    }

//    public DynamicView duplicate() throws Exception
//    {
//        DynamicView view = new DynamicView(this.type);
//        for(DynamicView child : children)
//        {
//            view.children.add(child.duplicate());
//        }
//        view.methodCalls.addAll(methodCalls);
//        return view;
//    }
//
//    public void removeIdentifierMethods(String identifier)
//    {
//        ArrayList<RemoteMethodCall> found = new ArrayList<>();
//        for(RemoteMethodCall call : methodCalls)
//        {
//            if(identifier.equals(call.getIdentifier()))
//            {
//                found.add(call);
//            }
//        }
//        methodCalls.removeAll(found);
//    }

    public static DynamicView fromJSON(String json) throws Exception{
        JSONObject obj = new JSONObject(json);
        return fromJSON(obj);
    }
    private static DynamicView fromJSON(JSONObject obj) throws Exception
    {
        int id;
        if(obj.has("id"))
        {
            id = obj.getInt("id");
        }
        else
        {
            id = genId();
        }
        DynamicView view = new DynamicView(id, obj.getString("type"));

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
                DynamicView child = DynamicView.fromJSON(childarr.getJSONObject(i));
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
    public String toJSON() throws JSONException
    {
        return toJSONObj().toString();
    }
}
