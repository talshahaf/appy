package com.appy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.CallSuper;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;

/**
 * Created by Tal on 19/03/2018.
 */

interface MyFragmentInterface
{
    void onShow(MainActivity activity);

    void onHide(MainActivity activity);

    void onBound();

    Widget getWidgetService();

    void setMenuId(int id);

    int getMenuId();

    void setArgument(Bundle arg);
}

public abstract class MyFragment extends Fragment implements MyFragmentInterface
{
    public void onShow(MainActivity activity)
    {

    }

    public void onHide(MainActivity activity)
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
    ActivityResultLauncher<Intent> activityResultLauncher = null;

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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // There are no request codes
                            MyFragment.this.onActivityResult(result.getData());
                        }
                    }
                });
        return null;
    }

    public void requestActivityResult(Intent request)
    {
        if (activityResultLauncher == null)
        {
            throw new IllegalStateException("super.onCreateView() must be called before requestActivityResult");
        }
        activityResultLauncher.launch(request);
    }

    public void onActivityResult(Intent data)
    {

    }
}

abstract class MySettingsFragment extends PreferenceFragmentCompat implements MyFragmentInterface
{
    public void onShow(MainActivity activity)
    {

    }

    public void onHide(MainActivity activity)
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
