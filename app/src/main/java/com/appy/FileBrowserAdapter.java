package com.appy;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.Collection;

/**
 * Created by Tal on 23/03/2018.
 */

public class FileBrowserAdapter extends BaseAdapter
{
    public static class FileItem
    {
        File file;
        boolean checked = false;
    }

    interface OnCheckedChanged
    {
        void onCheckedChanged(FileBrowserAdapter adapter, File file, boolean checked);
    }

    private final FileItem current;
    private final FileItem[] files;
    Context context;
    boolean isRoot;
    OnCheckedChanged checkedListener;
    boolean selectingEnabled = true;

    public static final int FILE_RESOURCE = R.drawable.any_file;
    public static final int DIRECTORY_RESOURCE = R.drawable.folder;
    public static final int PYTHON_FILE_RESOURCE = R.drawable.python_file;

    public void setCheckedListener(OnCheckedChanged listener)
    {
        checkedListener = listener;
    }

    public void setSelectingEnabled(boolean enabled)
    {
        selectingEnabled = enabled;
        notifyDataSetInvalidated();
    }

    public void updateSelection(Collection<File> selected)
    {
        for (FileItem file : files)
        {
            file.checked = selected.contains(file.file);
        }
        notifyDataSetInvalidated();
    }

    public FileItem getCurrent()
    {
        return current;
    }

    public boolean isParent(int position)
    {
        return position == 0 && !isRoot;
    }

    public FileBrowserAdapter(Context context, FileItem[] files, FileItem current, boolean isRoot)
    {
        this.context = context;
        this.files = files;
        this.current = current;
        this.isRoot = isRoot;
    }

    @Override
    public int getCount()
    {
        return files.length + (isRoot ? 0 : 1);
    }

    @Override
    public Object getItem(int position)
    {
        int index = position - (isRoot ? 0 : 1);
        if (index == -1)
        {
            return current;
        }
        return files[index];
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

        final FileItem item = (FileItem) getItem(position);
        if (item == current)
        {
            viewHolder.filename.setText("..");
            viewHolder.icon.setImageResource(DIRECTORY_RESOURCE);
            viewHolder.checkbox.setVisibility(View.INVISIBLE);
            viewHolder.date.setText("");
        }
        else
        {
            viewHolder.filename.setText(item.file.getName());
            viewHolder.icon.setImageResource(setFileImageType(item));
            viewHolder.date.setText(getLastDate(item));
            viewHolder.checkbox.setChecked(item.checked);
            viewHolder.checkbox.setEnabled(selectingEnabled);
        }

        view.setContentDescription("fileitem_" + viewHolder.filename.getText());

        viewHolder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed())
            {
                // Not from user!
                return;
            }

            item.checked = isChecked;
            if (checkedListener != null)
            {
                checkedListener.onCheckedChanged(FileBrowserAdapter.this, item.file, isChecked);
            }
        });
        return view;
    }

    static class ViewHolder
    {
        CheckBox checkbox;
        ImageView icon;
        TextView filename;
        TextView date;
    }

    private int setFileImageType(FileItem file)
    {
        if (file.file.isDirectory())
        {
            return DIRECTORY_RESOURCE;
        }
        else
        {
            String extension = file.file.getName().substring(file.file.getName().lastIndexOf(".") + 1);
            if (extension.equalsIgnoreCase("py"))
            {
                return PYTHON_FILE_RESOURCE;
            }
            else
            {
                return FILE_RESOURCE;
            }
        }
    }

    String getLastDate(FileItem file)
    {
        return Constants.DATE_FORMAT.format(file.file.lastModified());
    }
}
