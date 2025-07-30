package com.appy;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.IBinder;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.content.Intent;
import com.google.android.material.navigation.NavigationView;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.ActionBar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;

import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class MainActivity extends AppCompatActivity implements StatusListener, AppPropsListener, WidgetChosenListener
{
    private TutorialOverlayView tutorialOverlayView;
    private Tutorial tutorial = null;
    private FrameLayout fragmentContainer;
    private DrawerLayout drawer;
    private Toolbar toolbar;
    private NavigationView navView;
    private TextView bottomText;
    private HashMap<Integer, Pair<Class<?>, Fragment>> fragments = new HashMap<>();
    public static final String FRAGMENT_TAG = "FRAGMENT";

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        if (tutorial != null)
        {
            tutorial.onConfigurationChanged();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Utils.setCrashHandlerIfNeeded(Utils.getCrashPath(this, Constants.CrashIndex.JAVA_CRASH_INDEX));

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragments.put(R.id.navigation_control, new Pair<>(ControlFragment.class, null));
        fragments.put(R.id.navigation_files, new Pair<>(FilesFragment.class, null));
        fragments.put(R.id.navigation_configs, new Pair<>(ConfigsFragment.class, null));
        fragments.put(R.id.navigation_apps, new Pair<>(AppsFragment.class, null));
        fragments.put(R.id.navigation_logcat, new Pair<>(LogcatFragment.class, null));
        fragments.put(R.id.navigation_pip, new Pair<>(PipFragment.class, null));
        fragments.put(R.id.navigation_state, new Pair<>(StateFragment.class, null));
        fragments.put(R.id.navigation_timer, new Pair<>(TimerFragment.class, null));
        fragments.put(R.id.navigation_crash, new Pair<>(CrashFragment.class, null));
        fragments.put(R.id.navigation_settings, new Pair<>(SettingsFragment.class, null));

        // Set a Toolbar to replace the ActionBar.
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_action_menu);
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

        bottomText = findViewById(R.id.bottomtext);
        bottomText.setVisibility(View.GONE);

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

        boolean inTutorialMode = tutorial.startMain(widgetService);
        if (inTutorialMode)
        {
            //start on control
            selectDrawerItem(navView.getMenu().getItem(0), null);
            return;
        }

        // if not in tutorial, do fancy intent handling

        String startingFragment = getIntent().getStringExtra(Constants.FRAGMENT_NAME_EXTRA);
        Bundle fragmentArg = getIntent().getBundleExtra(Constants.FRAGMENT_ARG_EXTRA);

        if (getIntent().getExtras() != null && fragmentArg == null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
        {
            //try to pull from base level
            Bundle args = getIntent().getExtras().deepCopy();
            HashSet<String> keys = new HashSet<>(args.keySet());
            for (String key : keys)
            {
                if (!key.startsWith(Constants.FRAGMENT_ARG_PREFIX) || key.equals(Constants.FRAGMENT_ARG_EXTRA))
                {
                    args.remove(key);
                }
            }

            if (!args.isEmpty())
            {
                fragmentArg = args;
            }
        }

        // Handle py files
        if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getData() != null)
        {
            startingFragment = "Files";

            fragmentArg = new Bundle();
            fragmentArg.putParcelable(Constants.FRAGMENT_ARG_FILEURI, getIntent().getData());
        }

        int startingFragmentIndex = 0;
        for (int i = 0; i < navView.getMenu().size(); i++)
        {
            if (navView.getMenu().getItem(i).getTitle().toString().equalsIgnoreCase(startingFragment))
            {
                startingFragmentIndex = i;
                break;
            }
        }
        selectDrawerItem(navView.getMenu().getItem(startingFragmentIndex), fragmentArg);
    }

    public void permissionsResult(boolean granted)
    {
        if (!granted)
        {
            bottomText.setVisibility(View.VISIBLE);
            //Toast.makeText(this, "All time location access denied, appy might have trouble running (because android kills it)", Toast.LENGTH_LONG).show();
        }
        else
        {
            bottomText.setVisibility(View.GONE);
        }
    }

    public static final boolean usingSpecialForeground = true;

    public static final String[][] permissionsSteps = !usingSpecialForeground ? new String[][]{
            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION} : new String[]{})
    } : new String[][]{};

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
            Log.d("APPY", "Requesting location permissions step " + neededStep);
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
                        permissionsResult(false);
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

        if (permissions.length == 0)
        {
            //what?
            return;
        }

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
        if (item.getItemId() == android.R.id.home)
        {
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

            fragments.put(itemId, new Pair<>(cls.first, (Fragment) fragment));
        }

        if (fragment == null)
        {
            return;
        }
        fragment.setArgument(fragmentArg);

        MyFragmentInterface prev = (MyFragmentInterface) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);

        if (prev != fragment)
        {
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
            widgetService.setAppPropsListener(MainActivity.this);
            widgetService.setWidgetChosenListener(MainActivity.this);
            for (Pair<Class<?>, Fragment> frag : fragments.values())
            {
                if (frag.second != null)
                {
                    ((MyFragmentInterface) frag.second).onBound();
                }
            }
            if (tutorial != null)
            {
                tutorial.onServiceBound(widgetService);
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
            widgetService.setAppPropsListener(null);
            widgetService.setWidgetChosenListener(null);
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


    @Override
    public void onAppPropsChange(int widgetId, int androidWidgetId, DictObj.Dict data)
    {
        Fragment fragment = fragments.get(R.id.navigation_apps).second;
        if (fragment != null)
        {
            ((AppsFragment) fragment).onAppPropsChange(widgetId, androidWidgetId, data);
        }
    }

    @Override
    public void onWidgetChosen(int widgetId, int androidWidgetId, String name)
    {
        Fragment fragment = fragments.get(R.id.navigation_apps).second;
        if (fragment != null)
        {
            ((AppsFragment) fragment).onWidgetChosen(widgetId, androidWidgetId, name);
        }
    }

    @Override
    public void onWidgetCleared(int widgetId, int androidWidgetId)
    {
        Fragment fragment = fragments.get(R.id.navigation_apps).second;
        if (fragment != null)
        {
            ((AppsFragment) fragment).onWidgetCleared(widgetId, androidWidgetId);
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();

        if (tutorial != null)
        {
            tutorial.onActivityPaused();
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (tutorial != null)
        {
            tutorial.onActivityResumed();
        }
        if (tutorial == null || tutorial.isFinished())
        {
            int neededStep = checkPermissions();
            permissionsResult(neededStep == -1);
        }
    }
}
