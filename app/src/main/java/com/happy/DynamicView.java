package com.happy;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Tal on 14/01/2018.
 */
public class DynamicView
{
    public interface OnClick
    {
        void onClick(DynamicView view);
    }

    public interface OnItemClick
    {
        boolean onClick(DynamicView collection, DynamicView item, int position);
    }

    public ArrayList<DynamicView> children = new ArrayList<>();
    public String type;
    public HashMap<String, Variable> attrs = new HashMap<>();
    public int id;
    public OnClick onClick;
    public OnItemClick onItemClick;
}
