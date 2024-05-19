package com.appy;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Tal on 23/03/2018.
 */

public class PythonFile
{
    public enum State
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

    public DictObj.Dict serialize()
    {
        DictObj.Dict obj = new DictObj.Dict();
        obj.put("path", path);

        if (lastError != null)
        {
            obj.put("lastError", lastError);
        }
        if (lastErrorDate != null)
        {
            obj.put("lastErrorDate", dateFormat.format(lastErrorDate));
        }
        return obj;
    }

    public static PythonFile deserialize(DictObj.Dict obj)
    {
        String lastError = null;
        Date lastErrorDate = null;
        if (obj.hasKey("lastError"))
        {
            lastError = obj.getString("lastError");
        }
        if (obj.hasKey("lastErrorDate"))
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

    public static ArrayList<PythonFile> deserializeArray(byte[] arr)
    {
        ArrayList<PythonFile> result = new ArrayList<>();
        DictObj.List list = DictObj.List.deserialize(arr);
        for (int i = 0; i < list.size(); i++)
        {
            result.add(PythonFile.deserialize(list.getDict(i)));
        }
        return result;
    }

    public static byte[] serializeArray(List<PythonFile> files)
    {
        DictObj.List list = new DictObj.List();
        for (PythonFile f : files)
        {
            list.add(f.serialize());
        }
        return list.serialize();
    }
}
