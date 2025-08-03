package com.appy;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Tal on 23/03/2018.
 */

public class FileGridAdapter extends BaseAdapter
{
    private ArrayList<PythonFile> files;
    private HashMap<String, PythonFile.State> stateOverride = new HashMap<>();
    Context context;
    ItemActionListener listener;

    interface ItemActionListener
    {
        void onDelete(PythonFile file);

        void onRefresh(PythonFile file);

        void onInfo(PythonFile file);
    }

    public void setItems(ArrayList<PythonFile> files)
    {
        this.files = files;
    }

    public FileGridAdapter(Context context, ItemActionListener listener)
    {
        this.context = context;
        this.listener = listener;
        this.files = new ArrayList<>();
    }

    public void setStateOverride(PythonFile file, PythonFile.State state)
    {
        stateOverride.put(file.path, state);
    }

    public void clearStateOverride(PythonFile file)
    {
        stateOverride.remove(file.path);
    }

    @Override
    public int getCount()
    {
        return files.size();
    }

    @Override
    public Object getItem(int position)
    {
        return files.get(position);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent)
    {
        View view;
        ViewHolder viewHolder;
        if (convertView == null)
        {
            LayoutInflater m_inflater = LayoutInflater.from(context);
            view = m_inflater.inflate(R.layout.filegrid_item, null);
            viewHolder = new ViewHolder();
            viewHolder.layout = view.findViewById(R.id.filegrid_layout);
            viewHolder.name = view.findViewById(R.id.filegrid_name);
            viewHolder.icon = view.findViewById(R.id.filegrid_icon);
            viewHolder.info = view.findViewById(R.id.filegrid_info);
            viewHolder.delete = view.findViewById(R.id.filegrid_delete);
            viewHolder.refresh = view.findViewById(R.id.filegrid_refresh);
            viewHolder.edit = view.findViewById(R.id.filegrid_edit);
            view.setTag(viewHolder);
        }
        else
        {
            view = convertView;
            viewHolder = ((ViewHolder) view.getTag());
        }

        final PythonFile file = files.get(position);

        int color = R.color.grid_grey;
        PythonFile.State state = file.state;
        PythonFile.State override = stateOverride.get(file.path);

        switch (override == null ? state : override)
        {
            case ACTIVE:
            {
                color = R.color.grid_green;
                break;
            }
            case RUNNING:
            {
                color = R.color.grid_yellow;
                break;
            }
            case FAILED:
            {
                color = R.color.grid_red;
                break;
            }
        }
        viewHolder.layout.setBackgroundTintList(context.getResources().getColorStateList(color));

        viewHolder.name.setText(new File(file.path).getName());
        viewHolder.info.setOnClickListener(v -> {
            if (listener != null)
            {
                listener.onInfo(file);
            }
        });
        viewHolder.delete.setOnClickListener(v -> {
            if (listener != null)
            {
                listener.onDelete(file);
            }
        });
        viewHolder.refresh.setOnClickListener(v -> {
            if (listener != null)
            {
                listener.onRefresh(file);
            }
        });
        viewHolder.edit.setOnClickListener(v -> {
            Intent intent = new Intent(context, FileEditorActivity.class);
            intent.putExtra(FileEditorActivity.FILE_EDITOR_PATH_EXTRA, file.path);
            context.startActivity(intent);
        });
        return view;
    }

    static class ViewHolder
    {
        View layout;
        ImageView icon;
        TextView name;
        ImageView info;
        ImageView delete;
        ImageView refresh;
        ImageView edit;
    }
}
