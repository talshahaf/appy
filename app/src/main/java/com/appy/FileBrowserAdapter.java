package com.appy;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Tal on 23/03/2018.
 */

public class FileBrowserAdapter extends BaseAdapter
{
    private ArrayList<Integer> selected = new ArrayList<>();
    private File current;
    private File[] files;
    Context context;
    boolean isRoot;

    public static final int FILE_RESOURCE = android.R.drawable.ic_dialog_map;
    public static final int DIRECTORY_RESOURCE = android.R.drawable.ic_dialog_email;
    public static final int PYTHON_FILE_RESOURCE = android.R.drawable.ic_dialog_alert;

    public ArrayList<File> getSelected()
    {
        ArrayList<File> selectedFiles = new ArrayList<>();
        for(int i : selected)
        {
            selectedFiles.add(files[i]);
        }
        return selectedFiles;
    }

    public File getCurrent()
    {
        return current;
    }

    public boolean isParent(int position)
    {
        return position == 0 && !isRoot;
    }

    public FileBrowserAdapter(Context context, File[] files, File current, boolean isRoot) {
        this.context = context;
        this.files = files;
        this.current = current;
        this.isRoot = isRoot;
    }

    @Override
    public int getCount() {
        return files.length + (isRoot ? 0 : 1);
    }

    @Override
    public Object getItem(int position) {
        int index = position - (isRoot ? 0 : 1);
        if(index == -1)
        {
            return current;
        }
        return files[index];
    }

    @Override
    public long getItemId(int position) {
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
            view = m_inflater.inflate(R.layout.filebrowser_item, null);
            viewHolder = new ViewHolder();
            viewHolder.filename = view.findViewById(R.id.filename);
            viewHolder.date = view.findViewById(R.id.filedate);
            viewHolder.icon = view.findViewById(R.id.fileicon);
            viewHolder.checkbox = view.findViewById(R.id.filecheckbox);
            view.setTag(viewHolder);
        }
        else
        {
            view = convertView;
            viewHolder = ((ViewHolder) view.getTag());
        }

        File item = (File)getItem(position);
        if(item == current)
        {
            viewHolder.filename.setText("..");
            viewHolder.icon.setImageResource(DIRECTORY_RESOURCE);
            viewHolder.checkbox.setVisibility(View.INVISIBLE);
            viewHolder.date.setText("");
        }
        else
        {
            viewHolder.filename.setText(item.getName());
            viewHolder.icon.setImageResource(setFileImageType(item));
            viewHolder.date.setText(getLastDate(item));
        }

        viewHolder.checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if(isChecked)
                {
                    selected.add(position);
                }
                else
                {
                    //remove item, not removeAt
                    selected.remove(Integer.valueOf(position));
                }
            }
        });
        return view;
    }

    class ViewHolder
    {
        CheckBox checkbox;
        ImageView icon;
        TextView filename;
        TextView date;
    }

    private int setFileImageType(File file)
    {
        if (file.isDirectory())
        {
            return DIRECTORY_RESOURCE;
        }
        else
        {
            String extension = file.getName().substring(file.getName().lastIndexOf(".") + 1);
            if(extension.equalsIgnoreCase("py"))
            {
                return PYTHON_FILE_RESOURCE;
            }
            else
            {
                return FILE_RESOURCE;
            }
        }
    }

    String getLastDate(File file)
    {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(file.lastModified());
    }
}
