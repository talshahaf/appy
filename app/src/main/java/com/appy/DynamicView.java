package com.appy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

/**
 * Created by Tal on 14/01/2018.
 */
public class DynamicView
{
    public static Random random = new Random();

    private long id;
    public String type;
    public ArrayList<RemoteMethodCall> methodCalls = new ArrayList<>();
    public ArrayList<ArrayList<DynamicView>> children = new ArrayList<>();
    public Object tag;
    public int view_id;
    public int container_id;
    public int xml_id;
    public Attributes attributes = new Attributes();
    public int actualWidth;
    public int actualHeight;
    public HashMap<String, String> selectors = new HashMap<>();

    private static long genId()
    {
        long id = 0;
        while (id == 0)
        {
            id = random.nextLong();
            if (id < 0)
            {
                id = -id;
            }
        }
        return id;
    }

    public String toString()
    {
        return toJSON();
    }

    public DynamicView(String type)
    {
        this(genId(), type);
    }

    private DynamicView(long id, String type)
    {
        this(id, type, 0, 0, 0);
    }

    public DynamicView(long id, String type, int view_id, int container_id, int xml_id)
    {
        this.id = id;
        this.type = type;
        this.view_id = view_id;
        this.container_id = container_id;
        this.xml_id = xml_id;

        if (!Constants.typeToClass.containsKey(type) && !type.equals("*"))
        {
            throw new IllegalArgumentException("no such type: " + type);
        }
    }

    public long getId()
    {
        return id;
    }

    public static DynamicView fromJSON(String json)
    {
        try
        {
            return fromJSON(new JSONObject(json));
        }
        catch (JSONException e)
        {
            throw new IllegalArgumentException("json deserialization failed", e);
        }
    }

    private static DynamicView fromJSON(JSONObject obj) throws JSONException
    {
        long id;
        if (obj.has("id"))
        {
            id = obj.getLong("id");
        }
        else
        {
            id = genId();
        }

        int xml_id = 0;
        int container_id = 0;
        int view_id = 0;
        if (obj.has("xmlId"))
        {
            xml_id = obj.getInt("xmlId");
        }
        if (obj.has("containerId"))
        {
            view_id = obj.getInt("containerId");
        }
        if (obj.has("viewId"))
        {
            view_id = obj.getInt("viewId");
        }

        DynamicView view = new DynamicView(id, obj.getString("type"), view_id, container_id, xml_id);

        //Log.d("APPY", "building "+view.id+" "+view.type);

        if (obj.has("tag"))
        {
            view.tag = obj.get("tag");
        }

        if (obj.has("actualWidth"))
        {
            view.actualWidth = obj.getInt("actualWidth");
        }

        if (obj.has("actualHeight"))
        {
            view.actualHeight = obj.getInt("actualHeight");
        }

        if (obj.has("selectors"))
        {
            JSONObject selectors = obj.getJSONObject("selectors");
            Iterator<String> it = selectors.keys();
            while (it.hasNext())
            {
                String key = it.next();
                view.selectors.put(key, selectors.getString(key));
            }
        }

        if (obj.has("attributes"))
        {
            view.attributes = Attributes.fromJSON(obj.getJSONObject("attributes"));
        }

        if (obj.has("methodCalls"))
        {
            JSONArray callsarr = obj.getJSONArray("methodCalls");
            //Log.d("APPY", callsarr.length() + " calls");
            for (int i = 0; i < callsarr.length(); i++)
            {
                view.methodCalls.add(RemoteMethodCall.fromJSON(callsarr.getJSONObject(i)));
            }
        }
        if (obj.has("children"))
        {
            JSONArray childarr = obj.getJSONArray("children");
            //Log.d("APPY", childarr.length() + " children");
            for (int i = 0; i < childarr.length(); i++)
            {
                view.children.add(DynamicView.fromJSONArray(childarr.getJSONArray(i)));
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
        obj.put("containerId", container_id);
        obj.put("actualWidth", actualWidth);
        obj.put("actualHeight", actualHeight);

        if (!selectors.isEmpty())
        {
            JSONObject selectorsObj = new JSONObject();
            for (String key : selectors.keySet())
            {
                selectorsObj.put(key, selectors.get(key));
            }
            obj.put("selectors", selectorsObj);
        }

        if (tag != null)
        {
            obj.put("tag", tag);
        }

        if (!attributes.attributes.isEmpty())
        {
            obj.put("attributes", attributes.toJSON());
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
            JSONArray childarr = new JSONArray();
            for (ArrayList<DynamicView> views : children)
            {
                childarr.put(toJSON(views));
            }
            obj.put("children", childarr);
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
            throw new IllegalArgumentException("json serialization failed", e);
        }
    }

    public static ArrayList<DynamicView> fromJSONArray(JSONArray array) throws JSONException
    {
        ArrayList<DynamicView> ret = new ArrayList<>();
        for (int i = 0; i < array.length(); i++)
        {
            ret.add(DynamicView.fromJSON(array.getJSONObject(i)));
        }
        return ret;
    }

    public static ArrayList<DynamicView> fromJSONArray(String json)
    {
        try
        {
            return fromJSONArray(new JSONArray(json));
        }
        catch (JSONException e)
        {
            throw new IllegalArgumentException("json deserialization failed", e);
        }
    }

    public static JSONArray toJSON(ArrayList<DynamicView> views) throws JSONException
    {
        JSONArray viewarr = new JSONArray();
        for (DynamicView view : views)
        {
            viewarr.put(view.toJSONObj());
        }
        return viewarr;
    }

    public static String toJSONString(ArrayList<DynamicView> views)
    {
        try
        {
            return toJSON(views).toString(2);
        }
        catch (JSONException e)
        {
            throw new IllegalArgumentException("json serialization failed", e);
        }
    }
}
