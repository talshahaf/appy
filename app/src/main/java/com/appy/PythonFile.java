package com.appy;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

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

    public PythonFile(String path, String info)
    {
        this.path = path;
        this.info = info;
        this.state = State.IDLE;
    }

    public String path;
    public String info;
    public State state;

    public JSONObject serialize() throws JSONException
    {
        JSONObject obj = new JSONObject();
        obj.put("path", path);
        byte[] data;
        try
        {
            data = info.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new IllegalArgumentException("can't encode info");
        }
        obj.put("info", Base64.encodeToString(data, Base64.DEFAULT));
        return obj;
    }

    public static PythonFile deserialize(JSONObject obj) throws JSONException
    {
        String text;
        try
        {
            text = new String(Base64.decode(obj.getString("info"), Base64.DEFAULT), "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new IllegalArgumentException("can't encode info");
        }
        return new PythonFile(obj.getString("path"), text);
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
            e.printStackTrace();
            throw new IllegalArgumentException("json serialization failed");
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
            e.printStackTrace();
            throw new IllegalArgumentException("json serialization failed");
        }
    }
}
