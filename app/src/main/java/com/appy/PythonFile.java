package com.appy;

import java.text.ParseException;
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



    public DictObj.Dict toDict()
    {
        DictObj.Dict obj = new DictObj.Dict();
        obj.put("path", path);

        if (lastError != null)
        {
            obj.put("lastError", lastError);
        }
        if (lastErrorDate != null)
        {
            obj.put("lastErrorDate", Constants.DATE_FORMAT.format(lastErrorDate));
        }
        return obj;
    }

    public static PythonFile fromDict(DictObj.Dict obj)
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
                lastErrorDate = Constants.DATE_FORMAT.parse(serializedDate);
            }
            catch (ParseException e)
            {
                lastErrorDate = new Date();
            }
        }
        return new PythonFile(obj.getString("path"), lastError, lastErrorDate);
    }

    public static ArrayList<PythonFile> fromList(DictObj.List list)
    {
        ArrayList<PythonFile> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++)
        {
            result.add(PythonFile.fromDict(list.getDict(i)));
        }
        return result;
    }

    public static DictObj.List toList(List<PythonFile> files)
    {
        DictObj.List list = new DictObj.List();
        for (PythonFile f : files)
        {
            list.add(f.toDict());
        }
        return list;
    }
}
