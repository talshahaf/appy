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
    public interface KeyFormat
    {
        String format(Item item);
    }
    public static class Item
    {
        String key;
        String value;
        KeyFormat keyFormat;
        Object arg;

        @Override
        public String toString()
        {
            return keyFormat.format(this);
        }

        public Item(String key, String value, KeyFormat keyFormat, Object arg)
        {
            this.key = key;
            this.value = value;
            this.keyFormat = keyFormat;
            this.arg = arg;
        }

        public Item(String key, String value)
        {
            this(key, value, item -> item.key, null);
        }

        public Item(String key, String value, Object arg)
        {
            this(key, value, item -> item.key, arg);
        }
    }
    public static int MAX_VALUE_LENGTH = 100;
    private final Context context;
    private final ArrayList<Item> items;

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

    public static String trimValue(String value)
    {
        if (value.length() > MAX_VALUE_LENGTH)
        {
            value = value.substring(0, MAX_VALUE_LENGTH - 3) + "...";
        }
        return value;
    }

    public static String trimValuePerLine(String value, int maxlines)
    {
        String[] lines = value.split("\n");
        String[] result = new String[Math.min(lines.length, maxlines)];
        for (int i = 0; i < result.length; i++)
        {
            result[i] = trimValue(lines[i]);
        }
        return String.join("\n", result);
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

        text1.setText(trimValue(items.get(position).keyFormat.format(items.get(position))));

        String value = items.get(position).value;
        if (value == null)
        {
            value = "null";
        }

        text2.setText(trimValuePerLine(value, 2));

        return twoLineListItem;
    }
}
