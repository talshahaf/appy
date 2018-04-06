package com.appy;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;

/**
 * Created by Tal on 19/03/2018.
 */

public abstract class MyFragment extends Fragment
{
    public void onShow()
    {

    }
    public void onHide()
    {

    }

    public void onBound()
    {

    }

    public Widget getWidgetService()
    {
        return ((MainActivity)getActivity()).widgetService;
    }

    private int menuId = -1;
    public void setMenuId(int id)
    {
        menuId = id;
    }

    public int getMenuId()
    {
        return menuId;
    }
}
