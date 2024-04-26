package com.appy;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.content.Intent;

import com.google.android.material.internal.ManufacturerUtils;
import com.google.android.material.navigation.NavigationView;

import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.ActionBar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements StatusListener
{
    private TutorialOverlayView tutorialOverlayView;
    private Tutorial tutorial = null;
    private FrameLayout fragmentContainer;
    private DrawerLayout drawer;
    private Toolbar toolbar;
    private NavigationView navView;
    private HashMap<Integer, Pair<Class<?>, Fragment>> fragments = new HashMap<>();
    public static final String FRAGMENT_TAG = "FRAGMENT";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Utils.setCrashHandlerIfNeeded(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragments.put(R.id.navigation_control, new Pair<>(ControlFragment.class, null));
        fragments.put(R.id.navigation_logcat, new Pair<>(LogcatFragment.class, null));
        fragments.put(R.id.navigation_pip, new Pair<>(PipFragment.class, null));
        fragments.put(R.id.navigation_files, new Pair<>(FilesFragment.class, null));
        fragments.put(R.id.navigation_configs, new Pair<>(ConfigsFragment.class, null));
        fragments.put(R.id.navigation_state, new Pair<>(StateFragment.class, null));
        fragments.put(R.id.navigation_crash, new Pair<>(CrashFragment.class, null));
        fragments.put(R.id.navigation_settings, new Pair<>(SettingsFragment.class, null));

        // Set a Toolbar to replace the ActionBar.
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_24dp);
        actionBar.setHomeActionContentDescription("fragment_menu_button");
        // Find our drawer view
        drawer = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        tutorialOverlayView = findViewById(R.id.tutorial_overlay_view);
        fragmentContainer = findViewById(R.id.container);

        // Setup drawer view
        navView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener()
                {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem)
                    {
                        Log.d("APPY", "onNavigationItemSelected");
                        selectDrawerItem(menuItem, null);
                        return true;
                    }
                });


        getSupportFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener()
        {
            @Override
            public void onBackStackChanged()
            {
                MyFragmentInterface fragment = (MyFragmentInterface) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
                MenuItem menuItem = navView.getMenu().findItem(fragment.getMenuId());
                if (menuItem != null)
                {
                    menuItem.setChecked(true);
                    setTitle(menuItem.getTitle());
                }
            }
        });

        tutorial = new Tutorial();
        tutorial.fillMainComponents(this, tutorialOverlayView, drawer, fragmentContainer);
        tutorial.setTutorialFinishedListener(new Tutorial.TutorialFinishedListener()
        {
            @Override
            public void tutorialFinished()
            {
                permissionDialogShown = false;
                requestPermissionsIfNeeded();
            }
        });

        Widget.startService(this, new Intent(this, Widget.class));
        doBindService();

        String startingFragment = getIntent().getStringExtra(Constants.FRAGMENT_NAME_EXTRA);
        int startingFragmentIndex = 0;
        for (int i = 0; i < navView.getMenu().size(); i++)
        {
            if (navView.getMenu().getItem(i).getTitle().toString().equalsIgnoreCase(startingFragment))
            {
                startingFragmentIndex = i;
                break;
            }
        }
        selectDrawerItem(navView.getMenu().getItem(startingFragmentIndex), getIntent().getBundleExtra(Constants.FRAGMENT_ARG_EXTRA));

        tutorial.startMain(widgetService);
    }

    public void permissionsResult(boolean granted)
    {
        if (!granted)
        {
            Toast.makeText(this, "All time location access denied, appy might have trouble running (because android kills it)", Toast.LENGTH_LONG).show();
        }
    }

    public static final String[][] permissionsSteps = new String[][]{
            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION} : new String[]{})
    };

    public static final int[] REQUEST_PERMISSION_STEPS = new int[]{102, 103};

    public static final String permission_ask_title = "Just one more thing";
    public static final String permission_ask_message = "Appy needs all time location access to remain in background and avoid being killed by Android.\n"+
                                                        "Appy itself will not use location data at all (though specific widgets might).\n"+
                                                        "You will be presented a permission asking prompt. After that, you'll need to set location access to 'all time' in appy settings.\n"+
                                                        "This permission is optional, but widgets will become unresponsive if the service is killed.";
    private boolean permissionDialogShown = false; //we want to show it only once and not for every step.

    public int checkPermissions()
    {
        for (int i = 0; i < permissionsSteps.length; i++)
        {
            for (String permission : permissionsSteps[i])
            {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                {
                    return i; //step i needs asking
                }
            }
        }
        return -1;
    }

    public void setShowPermissionDialog(boolean show)
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("show_permission_dialog", show);
        editor.apply();
    }

    public boolean getShowPermissionDialog()
    {
        SharedPreferences sharedPref = getSharedPreferences("appy", Context.MODE_PRIVATE);
        return sharedPref.getBoolean("show_permission_dialog", true);
    }

    public void requestPermissionsIfNeeded()
    {
        int neededStep = checkPermissions();
        if (neededStep != -1)
        {
            Log.d("APPY", "Requesting location permissions step" + neededStep);
            if (!permissionDialogShown && !permission_ask_message.isEmpty() && getShowPermissionDialog())
            {
                permissionDialogShown = true;
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(permission_ask_title);
                builder.setMessage(permission_ask_message);
                builder.setCancelable(false);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        ActivityCompat.requestPermissions(MainActivity.this, permissionsSteps[neededStep], REQUEST_PERMISSION_STEPS[neededStep]);
                    }
                });
                builder.setNeutralButton("Don't show again", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        setShowPermissionDialog(false);
                        ActivityCompat.requestPermissions(MainActivity.this, permissionsSteps[neededStep], REQUEST_PERMISSION_STEPS[neededStep]);
                    }
                });

                builder.show();
            }
            else
            {
                ActivityCompat.requestPermissions(this, permissionsSteps[neededStep], REQUEST_PERMISSION_STEPS[neededStep]);
            }

        }
        else
        {
            permissionsResult(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean allGranted = true;
        for (int granted : grantResults)
        {
            if (granted != PackageManager.PERMISSION_GRANTED)
            {
                allGranted = false;
                break;
            }
        }

        if (!allGranted)
        {
            permissionsResult(false);
        }
        else
        {
            //next step
            requestPermissionsIfNeeded();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // The action bar home/up action should open or close the drawer.
        switch (item.getItemId())
        {
            case android.R.id.home:
                drawer.openDrawer(GravityCompat.START);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed()
    {
        if (!tutorial.allowBackPress())
        {
            return;
        }
        Log.d("APPY", "back pressed");
        super.onBackPressed();
    }

    public void selectDrawerItem(@NonNull MenuItem menuItem, Bundle fragmentArg)
    {
        // Create a new fragment and specify the fragment to show based on nav item clicked
        int itemId = menuItem.getItemId();

        Pair<Class<?>, Fragment> cls = fragments.get(itemId);
        if (cls == null)
        {
            itemId = R.id.navigation_control;
            cls = fragments.get(itemId);
        }

        MyFragmentInterface fragment = (MyFragmentInterface) cls.second;
        if (fragment == null)
        {
            try
            {
                fragment = (MyFragmentInterface) cls.first.newInstance();
                fragment.setMenuId(itemId);
            }
            catch (Exception e)
            {
                Log.e("APPY", "Exception on selectDrawerItem", e);
            }

            fragments.put(itemId, new Pair<Class<?>, Fragment>(cls.first, (Fragment) fragment));
        }

        if (fragment == null)
        {
            return;
        }
        fragment.setArgument(fragmentArg);

        MyFragmentInterface prev = (MyFragmentInterface) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);

        if (prev != fragment)
        {
            if (prev != null)
            {
                prev.onHide();
            }

            // Insert the fragment by replacing any existing fragment
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(
                    R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                    R.animator.slide_in_from_left, R.animator.slide_out_to_right);
            transaction.setPrimaryNavigationFragment((Fragment) fragment);
            transaction.replace(R.id.container, (Fragment) fragment, FRAGMENT_TAG);
            if (prev != null)
            {
                transaction.addToBackStack(null);
            }
            transaction.commitAllowingStateLoss();
            fragment.onShow();
        }

        // Highlight the selected item has been done by NavigationView
        menuItem.setChecked(true);
        // Set action bar title
        setTitle(menuItem.getTitle());
        // Close the navigation drawer
        drawer.closeDrawers();
    }

    public Widget widgetService = null;

    private ServiceConnection mConnection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            widgetService = ((Widget.LocalBinder) service).getService();
            widgetService.setStatusListener(MainActivity.this);
            for (Pair<Class<?>, Fragment> frag : fragments.values())
            {
                if (frag.second != null)
                {
                    ((MyFragmentInterface) frag.second).onBound();
                }
            }
        }

        public void onServiceDisconnected(ComponentName className)
        {
            widgetService = null;
        }
    };

    void doBindService()
    {
        Intent bindIntent = new Intent(this, Widget.class);
        bindIntent.putExtra(Constants.LOCAL_BIND_EXTRA, true);
        bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    void doUnbindService()
    {
        if (widgetService != null)
        {
            widgetService.setStatusListener(null);
            unbindService(mConnection);
            widgetService = null;
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        tutorial.onActivityDestroyed();
        doUnbindService();
    }

    @Override
    public void onStartupStatusChange()
    {
        Fragment fragment = fragments.get(R.id.navigation_control).second;
        if (fragment != null)
        {
            ((ControlFragment) fragment).onStartupStatusChange();
        }
        if (tutorial != null)
        {
            tutorial.onStartupStatusChange(widgetService);
        }
    }

    @Override
    public void onPythonFileStatusChange()
    {
        Fragment fragment = fragments.get(R.id.navigation_files).second;
        if (fragment != null)
        {
            ((FilesFragment) fragment).onPythonFileStatusChange();
        }
    }
}
