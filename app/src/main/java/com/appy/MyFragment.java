package com.appy;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
    void onBound();

    Widget getWidgetService();

    void setMenuId(int id);

    int getMenuId();

    void setArgument(Bundle arg);
}

public abstract class MyFragment extends Fragment implements MyFragmentInterface
{
    @Override
    public void onResume()
    {
        resumed = true;
        if (getWidgetService() != null)
        {
            bound = true;
        }
        if (bound)
        {
            onResumedAndBound();
        }
        super.onResume();
    }

    @Override
    public void onPause()
    {
        resumed = false;
        super.onPause();
    }

    public void onBound()
    {
        bound = true;
        if (attached)
        {
            onAttachedAndBound();
        }
        if (started)
        {
            onStartedAndBound();
        }
        if (resumed)
        {
            onResumedAndBound();
        }
    }

    @Override
    public void onStart()
    {
        started = true;
        if (getWidgetService() != null)
        {
            bound = true;
        }
        if (bound)
        {
            onStartedAndBound();
        }
        super.onStart();
    }

    @Override
    public void onAttach(Context context)
    {
        attached = true;
        if (getWidgetService() != null)
        {
            bound = true;
        }
        if (bound)
        {
            onAttachedAndBound();
        }
        super.onAttach(context);
    }

    @Override
    public void onDetach()
    {
        attached = false;
        super.onDetach();
    }

    @Override
    public void onStop()
    {
        started = false;
        super.onStop();
    }

    public void onAttachedAndBound()
    {

    }

    public void onStartedAndBound()
    {

    }

    public void onResumedAndBound()
    {

    }

    public Widget getWidgetService()
    {
        if (getActivity() == null)
        {
            return null;
        }
        return ((MainActivity)getActivity()).widgetService;
    }

    private boolean attached = false;
    private boolean resumed = false;
    private boolean started = false;
    private boolean bound = false;
    protected Bundle fragmentArg = null;

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

    @CallSuper
    public void setArgument(Bundle arg)
    {
        fragmentArg = arg;
        onArgument();
    }

    public void onArgument()
    {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        MyFragment.this.onActivityResult(result.getData());
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
    @Override
    public void onResume()
    {
        resumed = true;
        if (getWidgetService() != null)
        {
            bound = true;
        }
        if (bound)
        {
            onResumedAndBound();
        }
        super.onResume();
    }

    @Override
    public void onPause()
    {
        resumed = false;
        super.onPause();
    }

    public void onBound()
    {
        bound = true;
        if (attached)
        {
            onAttachedAndBound();
        }
        if (started)
        {
            onStartedAndBound();
        }
        if (resumed)
        {
            onResumedAndBound();
        }
    }

    @Override
    public void onStart()
    {
        started = true;
        if (getWidgetService() != null)
        {
            bound = true;
        }
        if (bound)
        {
            onStartedAndBound();
        }
        super.onStart();
    }

    @Override
    public void onAttach(Context context)
    {
        attached = true;
        if (getWidgetService() != null)
        {
            bound = true;
        }
        if (bound)
        {
            onAttachedAndBound();
        }
        super.onAttach(context);
    }

    @Override
    public void onDetach()
    {
        attached = false;
        super.onDetach();
    }

    @Override
    public void onStop()
    {
        started = false;
        super.onStop();
    }

    public void onAttachedAndBound()
    {

    }

    public void onStartedAndBound()
    {

    }

    public void onResumedAndBound()
    {

    }

    public Widget getWidgetService()
    {
        if (getActivity() == null)
        {
            return null;
        }
        return ((MainActivity)getActivity()).widgetService;
    }

    private boolean attached = false;
    private boolean resumed = false;
    private boolean started = false;
    private boolean bound = false;

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
