package com.appy;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class ListFragmentAdapter extends BaseAdapter
{
    public static class Item
    {
        String key;
        String value;
        String keyPrefix;
        Object arg;

        @Override
        public String toString()
        {
            return key;
        }

        public Item(String key, String value, String keyPrefix, Object arg)
        {
            this.key = key;
            this.value = value;
            this.keyPrefix = keyPrefix;
            this.arg = arg;
        }

        public Item(String key, String value)
        {
            this(key, value, "", null);
        }

        public Item(String key, String value, Object arg)
        {
            this(key, value, "", arg);
        }
    }
    public static int MAX_VALUE_LENGTH = 100;
    private Context context;
    private ArrayList<Item> items;

    public ListFragmentAdapter(Context context, ArrayList<Item> items)
    {
        this.context = context;
        this.items = items;
    }

    @Override
    public int getCount()
    {
        return items.size();
    }

    @Override
    public Object getItem(int position)
    {
        return items.get(position);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {

        View twoLineListItem;

        if (convertView == null)
        {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            twoLineListItem = inflater.inflate(R.layout.configs_list_item, null);
        }
        else
        {
            twoLineListItem = convertView;
        }

        TextView text1 = twoLineListItem.findViewById(R.id.text1);
        TextView text2 = twoLineListItem.findViewById(R.id.text2);

        text1.setText(items.get(position).keyPrefix + items.get(position).key);

        String value = items.get(position).value;
        if (value.length() > MAX_VALUE_LENGTH)
        {
            value = value.substring(0, MAX_VALUE_LENGTH - 3) + "...";
        }

        text2.setText(value);

        return twoLineListItem;
    }
}
