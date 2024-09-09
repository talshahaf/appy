package com.appy;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;

/**
 * Created by Tal on 19/03/2018.
 */

interface MyFragmentInterface
{
    void onShow();

    void onHide();

    void onBound();

    Widget getWidgetService();

    void setMenuId(int id);

    int getMenuId();

    void setArgument(Bundle arg);
}

abstract class MyFragment extends Fragment implements MyFragmentInterface
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
        if (getActivity() == null)
        {
            return null;
        }
        return ((MainActivity) getActivity()).widgetService;
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

    public void setArgument(Bundle arg)
    {

    }
}

abstract class MySettingsFragment extends PreferenceFragmentCompat implements MyFragmentInterface
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
        return ((MainActivity) getActivity()).widgetService;
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

    public void setArgument(Bundle arg)
    {

    }
}
