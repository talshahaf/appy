package com.appy;

import android.support.v4.app.Fragment;

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
