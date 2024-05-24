package com.appy;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
                executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
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
        public static void waitForAllSaves()
        {
            ArrayList<StoreData> copy;
            synchronized (lock)
            {
                copy = new ArrayList<>(singletons.values());
            }
            for (StoreData store : copy)
            {
                store.waitForSave();
            }
        }

        private static void post(Runnable runnable)
        {
            executor.submit(runnable);
        }
    }

    private static final Object objlock = new Object();
    private static final Object filelock = new Object();
    private DictObj.Dict store;
    private final Set<String> changed = new HashSet<>();
    private final String domain;
    private final StoreDbHelper dbHelper;
//    private final File path;
//    private final File tmppath;

    private static final Object notifier = new Object();

//    private static Pair<File, File> buildFiles(Context context, String domain)
//    {
//        return new Pair<>(new File(context.getFilesDir(), domain + ".store"),
//                new File(context.getFilesDir(), domain + ".store.tmp"));
//    }

    private StoreData(StoreDbHelper dbHelper, String domain)
    {
        //Pair<File, File> paths = buildFiles(context, domain);
        this.dbHelper = dbHelper;
        this.domain = domain;
        //this.path = paths.first;
        //this.tmppath = paths.second;
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
                    domainObj.put(cursor.getString(identifierIndex), DictObj.deserialize(cursor.getBlob(valueIndex), false));
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

//
//        byte[] data = null;
//        try
//        {
//            synchronized (filelock)
//            {
//                data = Utils.readAndHashFile(path, Constants.STORE_MAX_DOMAIN_SIZE).first;
//            }
//
//            DictObj.Dict obj = DictObj.Dict.deserialize(data);
//
//            synchronized (objlock)
//            {
//                store = obj;
//                changed.clear();
//            }
//        }
//        catch (FileNotFoundException e)
//        {
//            //ok
//            synchronized (objlock)
//            {
//                store = new DictObj.Dict();
//                changed.clear();
//            }
//        }
//        catch (IOException e)
//        {
//            Log.e("APPY", "StoreData load failed", e);
//            throw new RuntimeException(e);
//        }
//        catch (Exception e)
//        {
//            Log.e("APPY", "deserialization failed: "+ (data != null ? Base64.encodeToString(data, Base64.DEFAULT) : "null"), e);
//            throw new RuntimeException(e);
//        }
    }

    public void commit()
    {
        try(SQLiteDatabase db = dbHelper.getWritableDatabase())
        {
            Set<String> changedCopy;
            synchronized (objlock)
            {
                db.beginTransaction();
                try
                {
                    changedCopy = new HashSet<>(changed);

                    for (String key : changedCopy)
                    {
                        ContentValues values = new ContentValues();
                        values.put("domain", domain);
                        values.put("identifier", key);
                        values.put("value", ((DictObj) store.get(key)).serialize());

                        db.insert("store", null, values);
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
        finally
        {
            synchronized (notifier)
            {
                notifier.notifyAll();
            }
        }

//        try
//        {
//            byte[] data;
//            synchronized (objlock)
//            {
//                data = store.serialize();
//                changed.clear();
//            }
//            synchronized (filelock)
//            {
//                Utils.writeFile(tmppath, data);
//                if (!tmppath.renameTo(path))
//                {
//                    throw new RuntimeException("renaming store failed");
//                }
//            }
//        }
//        catch (IOException e)
//        {
//            Log.e("APPY", "StoreData commit failed", e);
//            throw new RuntimeException(e);
//        }
//        finally
//        {
//            synchronized (notifier)
//            {
//                notifier.notifyAll();
//            }
//        }
    }

    public void waitForSave()
    {
        synchronized (notifier)
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

                try
                {
                    notifier.wait();
                }
                catch (InterruptedException ignored)
                {

                }
            }
        }
    }

    public void apply()
    {
        Factory.post(new Runnable()
        {
            @Override
            public void run()
            {
                StoreData.this.commit();
            }
        });
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
