package com.appy;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class StoreData
{
    public static class StoreDbHelper extends SQLiteOpenHelper
    {
        // If you change the database schema, you must increment the database version.
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "store.db";

        public StoreDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE store (id INTEGER PRIMARY KEY, domain TEXT, identifier TEXT, value BLOB, UNIQUE (domain, identifier) ON CONFLICT REPLACE);");
        }
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // This database is only a cache for online data, so its upgrade policy is
            // to simply to discard the data and start over
            db.execSQL("DROP TABLE IF EXISTS store;");
            onCreate(db);
        }
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }
    }


    public static class Factory
    {
        private final static Object lock = new Object();
        private final static HashMap<String, StoreData> singletons = new HashMap<>();
        private static ExecutorService executor = null;

        public static StoreData create(Context context, String domain)
        {
            if (executor == null)
            {
                executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

                try
                {
                    Field field = CursorWindow.class.getDeclaredField("sCursorWindowSize");
                    field.setAccessible(true);
                    field.set(null, Constants.STORE_CURSOR_SIZE);
                }
                catch (Exception e)
                {
                    Log.e("APPY", "Failed to set cursor window size", e);
                }
            }
            synchronized (lock)
            {
                if (!singletons.containsKey(domain))
                {
                    StoreData store = new StoreData(new StoreDbHelper(context), domain);
                    store.load();
                    singletons.put(domain, store);
                }
                return singletons.get(domain);
            }
        }

        //waits for all applys called before this, and maybe some applys called after
        public static void commitAll()
        {
            ArrayList<StoreData> copy;
            synchronized (lock)
            {
                copy = new ArrayList<>(singletons.values());
            }
            for (StoreData store : copy)
            {
                store.commitAll();
            }
        }

        private static void post(Runnable runnable)
        {
            executor.submit(runnable);
        }
    }

    private static final Object objlock = new Object();
    private DictObj.Dict store;
    private final Set<String> changed = new HashSet<>();
    private final String domain;
    private final StoreDbHelper dbHelper;

    private StoreData(StoreDbHelper dbHelper, String domain)
    {
        this.dbHelper = dbHelper;
        this.domain = domain;
    }

    public void load()
    {
        try(SQLiteDatabase db = dbHelper.getReadableDatabase())
        {
            DictObj.Dict domainObj = new DictObj.Dict();

            try (Cursor cursor = db.query("store", new String[]{"domain", "identifier", "value"}, "domain = ?", new String[]{domain}, null, null, null))
            {
                while (cursor.moveToNext())
                {
                    int identifierIndex = cursor.getColumnIndexOrThrow("identifier");
                    int valueIndex = cursor.getColumnIndexOrThrow("value");
                    byte[] value = cursor.getBlob(valueIndex);
                    if (value != null)
                    {
                        domainObj.put(cursor.getString(identifierIndex), DictObj.deserialize(value, false));
                    }
                }
            }
            finally
            {
                synchronized (objlock)
                {
                    store = domainObj;
                    changed.clear();
                }
            }
        }
    }

    public void commit()
    {
        try(SQLiteDatabase db = dbHelper.getWritableDatabase())
        {
            Set<String> changedCopy;
            synchronized (objlock)
            {
                if (!changed.isEmpty())
                {
                    db.beginTransaction();
                    try
                    {
                        changedCopy = new HashSet<>(changed);

                        for (String key : changedCopy)
                        {
                            DictObj value = (DictObj) store.get(key);
                            if (value == null)
                            {
                                //key removed
                                db.delete("store", "domain = ? AND identifier = ?", new String[]{domain, key});
                            }
                            else
                            {
                                ContentValues values = new ContentValues();
                                values.put("domain", domain);
                                values.put("identifier", key);
                                values.put("value", value.serialize());

                                db.insert("store", null, values);
                            }
                        }
                        changed.clear();
                        db.setTransactionSuccessful();
                    }
                    finally
                    {
                        db.endTransaction();
                    }
                }
            }
        }
    }

    public void commitAll()
    {
        while (true)
        {
            synchronized (objlock)
            {
                if (changed.isEmpty())
                {
                    return;
                }
            }
            commit();
        }
    }

    public void apply()
    {
        Factory.post(StoreData.this::commit);
    }

    public boolean isSaved()
    {
        return changed.isEmpty();
    }

    //cannot return values as they need to be copied
    public Set<String> getAllStartingWith(String keyPrefix)
    {
        HashSet<String> ret = new HashSet<>();
        synchronized (objlock)
        {
            for (DictObj.Entry entry : store.entries())
            {
                if (entry.key.startsWith(keyPrefix))
                {
                    ret.add(entry.key);
                }
            }
        }
        return ret;
    }

    public Set<String> getAll()
    {
        return getAllStartingWith("");
    }

    public DictObj.Dict getDict(String key)
    {
        synchronized (objlock)
        {
            DictObj.Dict obj = store.getDict(key);
            if (obj == null)
            {
                return null;
            }
            return (DictObj.Dict) obj.copy(false);
        }
    }

    public DictObj.List getList(String key)
    {
        synchronized (objlock)
        {
            DictObj.List obj = store.getList(key);
            if (obj == null)
            {
                return null;
            }
            return (DictObj.List) obj.copy(false);
        }
    }

    public boolean hasKey(String key)
    {
        synchronized (objlock)
        {
            return store.hasKey(key);
        }
    }

    public void remove(String key)
    {
        synchronized (objlock)
        {
            store.remove(key);
            changed.add(key);
        }
    }

    public void removeAll()
    {
        synchronized (objlock)
        {
            changed.addAll(store.keyset());
            store.removeAll();
        }
    }

    public void put(String key, DictObj val)
    {
        synchronized (objlock)
        {
            store.put(key, val);
            changed.add(key);
        }
    }
}
