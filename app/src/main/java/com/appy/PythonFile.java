package com.appy;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by Tal on 23/03/2018.
 */

public class PythonFile
{
    enum State
    {
        IDLE,
        RUNNING,
        ACTIVE,
        FAILED,
    }

    public PythonFile(String path)
    {
        this(path, "", null);
    }

    public PythonFile(String path, String lastError, Date lastErrorDate)
    {
        this.path = path;
        this.lastError = lastError;
        this.lastErrorDate = lastErrorDate;
        this.state = State.IDLE;
    }

    public String path;
    public String lastError;
    public Date lastErrorDate;
    public State state;
    public String hash;

    public static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");

    public JSONObject serialize() throws JSONException
    {
        JSONObject obj = new JSONObject();
        obj.put("path", path);

        if(lastError != null)
        {
            byte[] data;
            try
            {
                data = lastError.getBytes("UTF-8");
            }
            catch (UnsupportedEncodingException e)
            {
                throw new IllegalArgumentException("can't encode lastError", e);
            }
            obj.put("lastError", Base64.encodeToString(data, Base64.DEFAULT));
        }
        if(lastErrorDate != null)
        {
            obj.put("lastErrorDate", dateFormat.format(lastErrorDate));
        }
        return obj;
    }

    public static PythonFile deserialize(JSONObject obj) throws JSONException
    {
        String lastError = null;
        Date lastErrorDate = null;
        if(obj.has("lastError"))
        {
            try
            {
                lastError = new String(Base64.decode(obj.getString("lastError"), Base64.DEFAULT), "UTF-8");
            }
            catch (UnsupportedEncodingException e)
            {
                throw new IllegalArgumentException("can't encode lastError", e);
            }
        }
        if(obj.has("lastErrorDate"))
        {
            String serializedDate = obj.getString("lastErrorDate");
            try
            {
                lastErrorDate = dateFormat.parse(serializedDate);
            }
            catch (ParseException e)
            {
                lastErrorDate = new Date();
            }
        }
        return new PythonFile(obj.getString("path"), lastError, lastErrorDate);
    }

    public String serializeSingle()
    {
        try
        {
            return serialize().toString();
        }
        catch(JSONException e)
        {
            throw new IllegalArgumentException("json serialization failed", e);
        }
    }

    public static PythonFile deserializeSingle(String json)
    {
        try
        {
            JSONObject obj = new JSONObject(json);
            return deserialize(obj);
        }
        catch(JSONException e)
        {
            throw new IllegalArgumentException("json serialization failed", e);
        }
    }

    public static ArrayList<PythonFile> deserializeArray(String json)
    {
        try
        {
            ArrayList<PythonFile> result = new ArrayList<>();
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++)
            {
                result.add(PythonFile.deserialize(arr.getJSONObject(i)));
            }
            return result;
        }
        catch(JSONException e)
        {
            throw new IllegalArgumentException("json serialization failed", e);
        }
    }

    public static String serializeArray(ArrayList<PythonFile> files)
    {
        try
        {
            JSONArray arr = new JSONArray();
            for (PythonFile f : files)
            {
                arr.put(f.serialize());
            }
            return arr.toString();
        }
        catch(JSONException e)
        {
            throw new IllegalArgumentException("json serialization failed", e);
        }
    }
}
