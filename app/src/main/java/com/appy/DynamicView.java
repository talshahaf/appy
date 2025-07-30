package com.appy;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
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
            id = Math.abs(random.nextLong());
            if (id < 0)
            {
                id = -id;
            }
        }
        return id;
    }

    public DynamicView()
    {

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

    public static DynamicView fromDict(DictObj.Dict obj)
    {
        long id;
        if (obj.hasKey("id"))
        {
            id = obj.getLong("id", 0);
        }
        else
        {
            id = genId();
        }

        int xml_id = 0;
        int container_id = 0;
        int view_id = 0;

        xml_id = (int)obj.getLong("xmlId", 0);
        if (obj.hasKey("containerId"))
        {
            view_id = (int)obj.getLong("containerId", 0);
        }
        if (obj.hasKey("viewId"))
        {
            view_id = (int)obj.getLong("viewId", 0);
        }

        DynamicView view = new DynamicView(id, obj.getString("type"), view_id, container_id, xml_id);

        //Log.d("APPY", "building "+view.id+" "+view.type);

        view.tag = obj.get("tag");

        view.actualWidth = (int)obj.getLong("actualWidth", 0);
        view.actualHeight = (int)obj.getLong("actualHeight", 0);

        if (obj.hasKey("selectors"))
        {
            for(DictObj.Entry entry : obj.getDict("selectors").entries())
            {
                view.selectors.put(entry.key, (String)entry.value);
            }
        }

        if (obj.hasKey("attributes"))
        {
            view.attributes = Attributes.fromDict(obj.getDict("attributes"));
        }

        if (obj.hasKey("methodCalls"))
        {
            DictObj.List callsarr = obj.getList("methodCalls");
            //Log.d("APPY", callsarr.length() + " calls");
            for (int i = 0; i < callsarr.size(); i++)
            {
                view.methodCalls.add(RemoteMethodCall.fromDict(callsarr.getDict(i)));
            }
        }
        if (obj.hasKey("children"))
        {
            DictObj.List childarr = obj.getList("children");
            //Log.d("APPY", childarr.length() + " children");
            for (int i = 0; i < childarr.size(); i++)
            {
                view.children.add(DynamicView.fromDictList(childarr.getList(i)));
            }
        }
        return view;
    }

    public DictObj.Dict toDict()
    {
        DictObj.Dict obj = new DictObj.Dict();
        obj.put("id", id);
        obj.put("type", type);
        obj.put("xmlId", xml_id);
        obj.put("viewId", view_id);
        obj.put("containerId", container_id);
        obj.put("actualWidth", actualWidth);
        obj.put("actualHeight", actualHeight);

        if (!selectors.isEmpty())
        {
            DictObj.Dict selectorsObj = new DictObj.Dict();
            for (String key : selectors.keySet())
            {
                selectorsObj.put(key, selectors.get(key));
            }
            obj.put("selectors", selectorsObj);
        }

        if (tag != null)
        {
            if (tag instanceof String)
            {
                obj.put("tag", (String)tag);
            }
            else if (tag instanceof Long)
            {
                obj.put("tag", (Long)tag);
            }
            else if (tag instanceof Integer)
            {
                obj.put("tag", (Integer)tag);
            }
            else if (tag instanceof DictObj)
            {
                obj.put("tag", (DictObj)tag);
            }
            else
            {
                throw new IllegalArgumentException("unsupported tag: " + tag.getClass().getName());
            }
        }

        if (!attributes.attributes.isEmpty())
        {
            obj.put("attributes", attributes.toDict());
        }

        if (!methodCalls.isEmpty())
        {
            DictObj.List callarr = new DictObj.List();
            for (RemoteMethodCall call : methodCalls)
            {
                callarr.add(call.toDict());
            }
            obj.put("methodCalls", callarr);
        }
        if (!children.isEmpty())
        {
            DictObj.List childarr = new DictObj.List();
            for (ArrayList<DynamicView> views : children)
            {
                childarr.add(DynamicView.toDictList(views));
            }
            obj.put("children", childarr);
        }
        return obj;
    }

    public DynamicView copy()
    {
        return fromDict(toDict());
    }

    public static ArrayList<DynamicView> fromDictList(DictObj.List list)
    {
        ArrayList<DynamicView> ret = new ArrayList<>();
        for (int i = 0; i < list.size(); i++)
        {
            ret.add(DynamicView.fromDict(list.getDict(i)));
        }
        return ret;
    }

    public static DictObj.List toDictList(ArrayList<DynamicView> views)
    {
        DictObj.List viewarr = new DictObj.List();
        for (DynamicView view : views)
        {
            viewarr.add(view.toDict());
        }
        return viewarr;
    }

    public static ArrayList<DynamicView> copyArray(ArrayList<DynamicView> arr)
    {
        return DynamicView.fromDictList(DynamicView.toDictList(arr));
    }

    public static DynamicView findById(ArrayList<DynamicView> arr, long id)
    {
        for (DynamicView e : arr)
        {
            if (e.id == id)
            {
                return e;
            }
            for (ArrayList<DynamicView> child : e.children)
            {
                DynamicView found = findById(child, id);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }
}
