package com.appy;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;


/**
 * Created by Tal on 14/01/2018.
 */
public class DynamicView implements DynamicViewListElement
{
    public static final int SERIALIZATION_VERSION = 2;

    public static final String METHOD_ADDED = "ma";
    public static final String METHOD_DELETED = "md";
    public static final String D_ADDED = "da";
    public static final String D_DELETED = "dm";

    private final long id;
    public String type;
    public ArrayList<RemoteMethodCall> methodCalls = new ArrayList<>();
    public DynamicViewList children = new DynamicViewList();
    public String tag;
    public HashMap<String, String> customAttributes = new HashMap<>();
    public int view_id;
    public int container_id;
    public int xml_id;
    public Attributes attributes = new Attributes();
    public int actualWidth;
    public int actualHeight;
    public HashMap<String, String> selectors = new HashMap<>();

    private static long genId()
    {
        return Utils.generateId();
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

    public void applyChanges(DictObj.Dict changes)
    {
        if (!changes.hasKey("id"))
        {
            throw new ChangeSyncException("change with no id");
        }
        if (id != changes.getLong("id", 0))
        {
            throw new ChangeSyncException("id mismatch: " + id + ", " + changes.getLong("id", 0));
        }

        if (!type.equals(changes.getString("type")))
        {
            throw new ChangeSyncException("type mismatch: " + type + ", " + changes.getString("type"));
        }

        if (changes.hasKey("tag"))
        {
            tag = changes.getString("tag");
        }

        if (changes.hasKey("newActions"))
        {
            DictObj.List actions = changes.getList("newActions");
            for (int i = 0; i < actions.size(); i++)
            {
                DictObj.Dict action = actions.getDict(i);
                switch(action.getString("action"))
                {
                    case METHOD_ADDED -> methodCalls.add(RemoteMethodCall.fromDict(action.getDict("value")));
                    case METHOD_DELETED -> methodCalls.removeIf(method -> method.getIdentifier().equals(action.getString("key1")));
                    case D_ADDED -> {
                        String key1 = action.getString("key1");
                        if (key1.equals("attributes"))
                        {
                            attributes.attributes.put(Attributes.Type.valueOf(action.getString("key2")), Attributes.AttributeValue.fromDict(action.getDict("value")));
                        }
                        else if (key1.equals("selectors"))
                        {
                            selectors.put(action.getString("key2"), action.get("value").toString());
                        }
                        else
                        {
                            customAttributes.put(key1, action.getString("value"));
                        }
                    }
                    case D_DELETED -> {
                        String key1 = action.getString("key1");
                        if (key1.equals("attributes"))
                        {
                            attributes.attributes.remove(Attributes.Type.valueOf(action.getString("key2")));
                        }
                        else if (key1.equals("selectors"))
                        {
                            selectors.remove(action.getString("key2"));
                        }
                        else
                        {
                            customAttributes.remove(key1);
                        }
                    }
                }
            }
        }
        if (changes.hasKey("children"))
        {
            children.applyChanges(changes.getDict("children"), true);
        }
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

        view.tag = obj.getString("tag");
        DictObj.Dict customAttributes = obj.getDict("customAttributes");
        if (customAttributes != null)
        {
            for (DictObj.Entry entry : customAttributes.entries())
            {
                view.customAttributes.put(entry.key, (String)entry.value);
            }
        }

        view.actualWidth = (int)obj.getLong("actualWidth", 0);
        view.actualHeight = (int)obj.getLong("actualHeight", 0);

        if (obj.hasKey("selectors"))
        {
            for(DictObj.Entry entry : obj.getDict("selectors").entries())
            {
                view.selectors.put(entry.key, entry.value.toString());
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
            view.children = DynamicViewList.fromDict(obj.getDict("children"));
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

        DictObj.Dict customAttributesObj = new DictObj.Dict();
        for (Map.Entry<String, String> entry : customAttributes.entrySet())
        {
            customAttributesObj.put(entry.getKey(), entry.getValue());
        }
        obj.put("customAttributes", customAttributesObj);

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
            obj.put("tag", tag);
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
            obj.put("children", children.toDict());
        }
        return obj;
    }

    public RemoteMethodCall findMethodCall(String identifier)
    {
        for (int i = methodCalls.size() - 1; i >= 0; i--)
        {
            RemoteMethodCall method = methodCalls.get(i);
            if (method.getIdentifierIgnorePrefix().equals(identifier))
            {
                return method;
            }
        }
        return null;
    }

    public static class ChangeSyncException extends RuntimeException
    {

        public ChangeSyncException(String s)
        {
            super(s);
        }
    }

    public static class DynamicViewList implements DynamicViewListElement, Iterable<DynamicViewListElement>
    {
        private long id;
        private final ArrayList<DynamicViewListElement> list;

        public DynamicViewList()
        {
            this.id = genId();
            list = new ArrayList<>();
        }
        public DynamicViewList(long id)
        {
            this.id = id;
            list = new ArrayList<>();
        }

        @Override
        public long getId()
        {
            return id;
        }

        public int size()
        {
            return list.size();
        }

        public boolean isEmpty()
        {
            return list.isEmpty();
        }

        public DynamicViewListElement get(int index)
        {
            return list.get(index);
        }

        public DynamicView getView(int index)
        {
            return (DynamicView)list.get(index);
        }

        public DynamicViewList getList(int index)
        {
            return (DynamicViewList)list.get(index);
        }

        public void add(DynamicViewListElement element)
        {
            if (element == null)
            {
                throw new RuntimeException("cannot add null");
            }
            list.add(element);
        }

        public DynamicView findView(long id, boolean recursive)
        {
            return (DynamicView) find(id, recursive);
        }

        public DynamicViewListElement find(long id, boolean recursive)
        {
            for (DynamicViewListElement e : list)
            {
                if (e.getId() == id)
                {
                    return e;
                }

                if (!recursive)
                {
                    continue;
                }

                DynamicViewList inner;
                if (e instanceof DynamicView)
                {
                    inner = ((DynamicView) e).children;
                }
                else
                {
                    inner = (DynamicViewList) e;
                }

                DynamicViewListElement found = inner.find(id, true);
                if (found != null)
                {
                    return found;
                }
            }
            return null;
        }

        @Override
        public void applyChanges(DictObj.Dict changes)
        {
            applyChanges(changes, false);
        }

        public void applyChanges(DictObj.Dict changes, boolean canReplaceId)
        {
            if (!changes.hasKey("id"))
            {
                throw new ChangeSyncException("change with no id");
            }

            if (canReplaceId && isEmpty())
            {
                id = changes.getLong("id", 0);
            }
            else
            {
                if (id != changes.getLong("id", 0))
                {
                    throw new ChangeSyncException("id mismatch: " + id + ", " + changes.getLong("id", 0));
                }
            }

            //save
            HashMap<Long, DynamicViewListElement> baseElements = new HashMap<>();
            for (DynamicViewListElement e : list)
            {
                baseElements.put(e.getId(), e);
            }

            list.clear();

            DictObj.Dict addedActions = changes.getDict("addedActions");
            DictObj.Dict deletedActions = changes.getDict("deletedActions");
            DictObj.Dict elementActions = changes.getDict("elementActions");

            DictObj.List newlist = changes.getList("list");
            for (int i = 0; i < newlist.size(); i++)
            {
                long id = newlist.getLong(i, 0);
                String idstr = id+"";
                DynamicViewListElement e;
                if (addedActions.hasKey(idstr))
                {
                    DictObj.Dict added = addedActions.getDict(idstr);
                    if (added.hasKey("type"))
                    {
                        e = new DynamicView(id, added.getString("type"));
                    }
                    else
                    {
                        e = new DynamicViewList(id);
                    }
                    e.applyChanges(elementActions.getDict(idstr));
                }
                else if (baseElements.containsKey(id) && !deletedActions.hasKey(idstr))
                {
                    e = baseElements.get(id);
                    if (elementActions.hasKey(idstr))
                    {
                        e.applyChanges(elementActions.getDict(idstr));
                    }
                }
                else
                {
                    throw new ChangeSyncException("changes out of sync on " + id + ": " + baseElements.containsKey(id) + " " + deletedActions.hasKey(idstr));
                }
                list.add(e);
            }
        }

        public static DynamicViewList createEmpty(DictObj.Dict changes)
        {
            return new DynamicViewList(changes.getLong("id", 0));
        }

        @Override
        public DictObj.Dict toDict()
        {
            DictObj.Dict ret = new DictObj.Dict();
            DictObj.List lst = new DictObj.List();
            for (DynamicViewListElement element : list)
            {
                lst.add(element.toDict());
            }
            ret.put("id", id);
            ret.put("list", lst);
            return ret;
        }

        public static DynamicViewList fromDict(DictObj.Dict dict)
        {
            DynamicViewList ret = new DynamicViewList(dict.getLong("id", 0));
            DictObj.List list = dict.getList("list");
            for (int i = 0; i < list.size(); i++)
            {
                DictObj.Dict element = list.getDict(i);
                if (element.hasKey("list"))
                {
                    ret.add(DynamicViewList.fromDict(element));
                }
                else
                {
                    ret.add(DynamicView.fromDict(element));
                }
            }
            return ret;
        }

        @NonNull
        @Override
        public Iterator<DynamicViewListElement> iterator()
        {
            return list.iterator();
        }

        public Iterable<DynamicView> viewIterator()
        {
            return new Iterable<>()
            {
                @NonNull
                @Override
                public Iterator<DynamicView> iterator()
                {
                    return new Utils.IteratorConverter<>(DynamicViewList.this.iterator());
                }
            };
        }

        public Iterable<DynamicViewList> listIterator()
        {
            return new Iterable<>()
            {
                @NonNull
                @Override
                public Iterator<DynamicViewList> iterator()
                {
                    return new Utils.IteratorConverter<>(DynamicViewList.this.iterator());
                }
            };
        }
    }
}
