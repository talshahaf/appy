package com.appy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;

interface TutorialStepListener
{
    void tutorialStepDone(int stepsDone, boolean allDone);
}

public class Tutorial implements OverlayHoleView.OnHoleClick, TutorialStepListener
{
    public interface TutorialFinishedListener
    {
        void tutorialFinished();
    }

    private final Handler handler;
    private TutorialOverlayView overlay;
    private DrawerLayout drawer;
    private ViewGroup content;
    private Activity activity;
    private Toolbar fileBrowserToolbar;
    private ListView fileBrowserList;
    private Widget service;
    private TutorialFinishedListener tutorialFinishedListener;

    private int mStepsDone = 0;

    private static boolean tutorialDone = false;
    private static final ArrayList<TutorialStepListener> gTutorialStepDoneListeners = new ArrayList<>();

    public static final int overlayColor = Color.argb(150, 0, 0, 0);

    public static final int circleHolePad = 30;
    public static final int rectHolePadW = 30;
    public static final int rectHolePadH = 15;

    public static final int waitMilli = 100;
    public static final int waitMaxRetries = 10;

    public static final String welcomeMessage = "Welcome to Appy!\n\nThe app that lets you build your own home screen widgets using nothing but Python.";

    public static class StepProps
    {
        public enum Anchor
        {
            ABSOLUTE,
            ABOVE_HOLE,
            BELOW_HOLE,
        };
        public String text;
        public float y;
        public boolean yIsFactor;
        public Anchor anchor;
        public Integer videoRes;

        public StepProps(String text, float y, boolean yIsFactor, Anchor anchor, Integer videoRes)
        {
            this.text = text;
            this.y = y;
            this.yIsFactor = yIsFactor;
            this.anchor = anchor;
            this.videoRes = videoRes;
        }
    }
    public static final StepProps[] stepProps = new StepProps[]{
            //starting from 1
            new StepProps("", 0, false, StepProps.Anchor.ABSOLUTE, null),
            //status change
            new StepProps(welcomeMessage + "\n\nAppy is now installing python and downloads some helpful libraries (pip, requests, setuptools and more).\nIt shouldn't take more than 20 seconds, and once it's done you can add your first widget.", 20, false, StepProps.Anchor.BELOW_HOLE, null),
            //click on menu
            new StepProps(welcomeMessage + "\n\nAppy is ready. Next, we'll import your first widget.\nClick on the menu icon.", 20, false, StepProps.Anchor.BELOW_HOLE, null),
            //click on files
            new StepProps("Click on 'Files' to open the python file management tab.\nFrom there you can import new script files.", 20, false, StepProps.Anchor.BELOW_HOLE, null),
            //click on add
            new StepProps("Here all the imported python files would show up.\nClick on '+' to import a new one from the file system.", 20, false, StepProps.Anchor.ABOVE_HOLE, null),
            //click on goto
            new StepProps("The starting path is the preferred script path where you should add your scripts.\nFor now, click on 'goto' and select the examples dir.", 20, false, StepProps.Anchor.BELOW_HOLE, null),
            //waiting for dialog
            new StepProps("", 0, false, StepProps.Anchor.ABSOLUTE, null),
            //click on pilling
            new StepProps("You can import any of these example widgets or make your own.\nFor now, we'll go with 'pilling' which uses PIL to draw an image.", 20, false, StepProps.Anchor.BELOW_HOLE, null),
            new StepProps("That's it! you can now add an Appy widget to your home screen and select 'pilling'.", 0.33f, true, StepProps.Anchor.ABSOLUTE, R.raw.addwidget),
    };

    public Tutorial()
    {
        handler = new Handler();
        gTutorialStepDoneListeners.add(this);
    }

    private void setOverlayVisible(boolean visible)
    {
        overlay.setVisibility(visible ? View.VISIBLE : View.GONE);
        overlay.setEnabled(visible);
    }

    @Override
    public void tutorialStepDone(int stepsDone, boolean allDone)
    {
        if (allDone)
        {
            tutorialDone = true;
        }

        setOverlayVisible(!tutorialDone);

        Log.d("APPY", "Tutorial stepsDone: " + stepsDone + ", " + allDone);
        mStepsDone = stepsDone;

        StepProps stepProp = stepProps[mStepsDone];
        if (stepProp.anchor == StepProps.Anchor.ABSOLUTE)
        {
            overlay.setTextTopFromTop(stepProp.y, stepProp.yIsFactor);
        }
        else if (stepProp.anchor == StepProps.Anchor.BELOW_HOLE || stepProp.anchor == StepProps.Anchor.ABOVE_HOLE)
        {
            overlay.setTextFromHole(stepProp.y, stepProp.yIsFactor, stepProp.anchor == StepProps.Anchor.BELOW_HOLE);
        }

        if (stepProp.videoRes != null && activity != null)
        {
            overlay.setVideo(Uri.parse("android.resource://" + activity.getPackageName() + "/" + stepProp.videoRes));
        }
        else
        {
            overlay.setNoVideo();
        }

        overlay.setText(stepProp.text);

        if (mStepsDone == 8 && fileBrowserToolbar != null)
        {
            overlay.hideBoxNoAnimation();
        }
        else
        {
            overlay.showBox();
        }

        if (mStepsDone == 1 && !tutorialDone)
        {
            checkStartupStatus();
        }

        overlay.setButtonText(mStepsDone == 8 ? "DONE" : "SKIP TUTORIAL");
        if (mStepsDone == 8)
        {
            overlay.setNoHole();
        }
    }

    public void onOverlayButtonClick()
    {
        if (mStepsDone < 8)
        {
            Utils.showConfirmationDialog(activity,
                    "Skip Tutorial", "Are you sure?", 0,
                    "Skip", "Don't skip", () -> finishTutorial(false));
        }
        else
        {
            finishTutorial(false);
        }
    }

    public void onStepError(int step)
    {
        if (step == 1 || step == 2)
        {
            //can't go back
            finishTutorial(true);
            return;
        }
        // Go back one
        mStepsDone = step - 2;
        doNextStep();
    }

    public void checkStartupStatus()
    {
        if (service != null)
        {
            Constants.StartupState state = service.getStartupState();
            if (mStepsDone == 1)
            {
                if (state == Constants.StartupState.COMPLETED)
                {
                    doNextStep();
                }
                else if (state == Constants.StartupState.ERROR)
                {
                    //TODO point to logcat?
                    finishTutorial(true);
                }
            }
        }
    }

    public void fillMainComponents(Activity mainActivity, TutorialOverlayView overlay, DrawerLayout drawer, ViewGroup content)
    {
        this.overlay = overlay;
        this.drawer = drawer;
        this.content = content;
        this.activity = mainActivity;

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this.activity.getApplicationContext());
        tutorialDone = sharedPref.getBoolean("tutorial_done", false);

        overlay.setOnHoleClick(this);
        overlay.setOnButtonClick(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onOverlayButtonClick();
            }
        });
    }

    public void fillFileBrowserComponents(Activity fileBrowserActivity, TutorialOverlayView fileBrowserOverlay, Toolbar fileBrowserToolbar, ListView fileBrowserList)
    {
        this.activity = fileBrowserActivity;
        this.fileBrowserToolbar = fileBrowserToolbar;
        this.fileBrowserList = fileBrowserList;
        this.overlay = fileBrowserOverlay;

        fileBrowserOverlay.setOnHoleClick(this);
        overlay.setOnButtonClick(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onOverlayButtonClick();
            }
        });
    }

    public void setTutorialFinishedListener(TutorialFinishedListener tutorialFinishedListener)
    {
        this.tutorialFinishedListener = tutorialFinishedListener;
    }

    public boolean isFinished()
    {
        return tutorialDone;
    }

    public void writeIsDone(boolean done)
    {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this.activity.getApplicationContext()).edit();
        editor.putBoolean("tutorial_done", done);
        editor.apply();
    }

    public boolean startMain(@Nullable Widget service)
    {
        setOverlayVisible(!tutorialDone);
        if (tutorialDone)
        {
            if (tutorialFinishedListener != null)
            {
                tutorialFinishedListener.tutorialFinished();
            }
            return false;
        }

        Log.d("APPY", "Tutorial start");

        if (service != null)
        {
            this.service = service;
        }

        mStepsDone = 0;
        doNextStep();
        return true;
    }

    public void startFileBrowser()
    {
        setOverlayVisible(!tutorialDone);
        if (tutorialDone)
        {
            return;
        }

        mStepsDone = 4;
        doNextStep();
    }

    public void callStepDone(int step, boolean allDone)
    {
        for (TutorialStepListener listener : gTutorialStepDoneListeners)
        {
            listener.tutorialStepDone(step, allDone);
        }
    }

    public void finishTutorial(boolean showAgain)
    {
        Log.d("APPY", "Tutorial finish");

        tutorialDone = true;
        if (!showAgain)
        {
            writeIsDone(true);
        }
        callStepDone(mStepsDone, true);

        if (tutorialFinishedListener != null)
        {
            tutorialFinishedListener.tutorialFinished();
        }
    }

    public void onStartupStatusChange(Widget service)
    {
        this.service = service;
        checkStartupStatus();
    }

    public void onServiceBound(Widget service)
    {
        this.service = service;
        checkStartupStatus();
    }

    public void onConfigurationChanged()
    {
        if (mStepsDone != 0)
        {
            //redo step
            mStepsDone = mStepsDone - 1;
            doNextStep();
        }
    }

    public boolean allowBackPress()
    {
        return tutorialDone;
    }

    public void onActivityPaused()
    {

    }

    public void onActivityResumed()
    {
        if (overlay != null)
        {
            overlay.resumeVideoIfNeeded();
        }
    }

    public void onActivityDestroyed()
    {
        gTutorialStepDoneListeners.remove(this);
    }

    public void onFileBrowserDialogDone()
    {
        if (mStepsDone == 5)
        {
            handler.postDelayed(() -> {
                View[] views = findViewByText(fileBrowserList, "pilling.py", true);
                if (views.length > 0)
                {
                    mStepsDone = 6;
                    doNextStep();
                }
                else
                {
                    //go back to 4
                    mStepsDone = 4;
                    doNextStep();
                }
            }, waitMilli);
        }
    }

    public void onFileBrowserImportDone()
    {
        if (mStepsDone == 7)
        {
            handler.postDelayed(this::doNextStep, waitMilli);
        }
    }

    @Override
    public void onHoleClick()
    {
        if (mStepsDone == 2 || mStepsDone == 3 || mStepsDone == 5)
        {
            doNextStep();
        }
    }

    public void doNextStep()
    {
        switch (mStepsDone)
        {
            case 0:
            {
                // on tutorial start / on control status error
                step(mStepsDone + 1, content, "control_status_indicator", true, false, circleHolePad * 2, circleHolePad * 2);
                break;
            }
            case 1:
            {
                // on control status success
                step(mStepsDone + 1, drawer, "fragment_menu_button", true, false, circleHolePad, circleHolePad);
                break;
            }
            case 2:
            {
                // on menu button click
                step(mStepsDone + 1, drawer, "Files", false, true, rectHolePadW, rectHolePadH);
                break;
            }
            case 3:
            {
                // on files tab click
                step(mStepsDone + 1, content, "open_file_browser_button", true, false, circleHolePad, circleHolePad);
                break;
            }
            case 4:
            {
                // on filebrowser activity shown
                step(mStepsDone + 1, fileBrowserToolbar, "Go to", false, true, rectHolePadW, rectHolePadH);
                break;
            }
            case 5:
            {
                // on goto click
                Log.d("APPY", "Tutorial waiting for dialog");
                overlay.setNoHole();
                // moving to 6 only on onFileBrowserDialogDone
                break;
            }
            case 6:
            {
                // on dialog done
                step(mStepsDone + 1, fileBrowserList, "fileitem_pilling.py", true, true, 0, 0);
                break;
            }
            case 7:
            {
                // on pilling click
                overlay.setNoHole();
                callStepDone(mStepsDone + 1, false);
                break;
            }
        }
    }

    public void step(int step, View root, String needle, boolean desc, boolean rectHole, int holePadW, int holePadH)
    {
        overlay.setNoHole();
        overlay.hideBox();

        final View[][] viewsStorage = new View[1][];

        waitForCondition(() -> {
            if (desc)
            {
                viewsStorage[0] = findViewByDesc(root, needle, true);
            }
            else
            {
                viewsStorage[0] = findViewByText(root, needle, true);
            }

            return viewsStorage[0].length > 0;
        }, () -> {
            View view = viewsStorage[0][0];

            waitForAnimationStop(view, () -> {
                float[] hole;
                if (desc || !(view instanceof TextView))
                {
                    hole = viewAbsoluteBounding(view, rectHole);
                }
                else
                {
                    hole = textViewAbsoluteBounding((TextView) view, rectHole);
                }

                overlay.setOverlayColor(overlayColor);
                overlay.setAbsoluteHole((int) hole[0], (int) hole[1],
                        hole[2] + holePadW,
                        hole[3] + holePadH,
                        hole[2],
                        hole[3],
                        rectHole ? OverlayHoleView.HoleShape.Rect : OverlayHoleView.HoleShape.Circle);

                callStepDone(step, false);
            }, () -> {
                Log.d("APPY", "Tutorial wait max retries");
                onStepError(step);
            });
        }, () -> {
            Log.d("APPY", "Tutorial element " + needle + " not found");
            onStepError(step);
        });
    }

    public interface Condition
    {
        boolean check();
    }

    public void waitForCondition(Condition check, Runnable success, Runnable error)
    {
        final Runnable[] waiterStorage = new Runnable[1];
        final int[] retriesStorage = new int[1];
        retriesStorage[0] = waitMaxRetries;
        waiterStorage[0] = () -> {
            if (check.check())
            {
                success.run();
            }
            else
            {
                if (retriesStorage[0] > 0)
                {
                    retriesStorage[0]--;
                    handler.postDelayed(waiterStorage[0], waitMilli);
                }
                else
                {
                    error.run();
                }
            }
        };

        handler.postDelayed(waiterStorage[0], waitMilli);
    }

    public void waitForAnimationStop(final View view, Runnable f, Runnable error)
    {
        final int[] prevLoc = new int[2];
        view.getLocationInWindow(prevLoc);

        waitForCondition(() -> {
            int[] newLoc = new int[2];
            view.getLocationInWindow(newLoc);

            if ((newLoc[0] != 0 || newLoc[1] != 0) && newLoc[0] == prevLoc[0] && newLoc[1] == prevLoc[1])
            {
                return true;
            }

            prevLoc[0] = newLoc[0];
            prevLoc[1] = newLoc[1];
            return false;
        }, f, error);
    }

    public interface ViewFinder
    {
        boolean match(View view);
    }

    private void viewTraversalHelper(ArrayList<View> found, View base, ViewFinder finder, boolean oneEnough)
    {
        if (finder.match(base))
        {
            found.add(base);
            if (oneEnough)
            {
                return;
            }
        }

        if (base instanceof ViewGroup)
        {
            for (int i = 0; i < ((ViewGroup) base).getChildCount(); i++)
            {
                viewTraversalHelper(found, ((ViewGroup) base).getChildAt(i), finder, oneEnough);
            }
        }
    }

    public View[] viewTraversal(View base, ViewFinder finder, boolean oneEnough)
    {
        ArrayList<View> found = new ArrayList<>();
        viewTraversalHelper(found, base, finder, oneEnough);
        return found.toArray(new View[]{});
    }

    public View[] findViewByText(View root, String text, boolean oneEnough)
    {
        //fragmentContainer
        //drawer

        return viewTraversal(root, new ViewFinder()
        {
            @Override
            public boolean match(View view)
            {
                if (view instanceof TextView)
                {
                    return ((TextView) view).getText().toString().equals(text);
                }
                return false;
            }
        }, oneEnough);
    }

    public View[] findViewByDesc(View root, String desc, boolean oneEnough)
    {
        return viewTraversal(root, new ViewFinder()
        {
            @Override
            public boolean match(View view)
            {
                CharSequence seq = view.getContentDescription();
                return seq != null && desc.equals(seq.toString());
            }
        }, oneEnough);
    }

    public float[] viewAbsoluteBounding(View view, boolean rectBounding)
    {
        int[] loc = new int[2];
        view.getLocationInWindow(loc);

        float x = view.getMeasuredWidth() / 2.0f;
        float y = view.getMeasuredHeight() / 2.0f;

        if (rectBounding)
        {
            return new float[]{x + loc[0], y + loc[1], x, y};
        }
        else
        {
            float r = Math.max(x, y);
            return new float[]{x + loc[0], y + loc[1], r, r};
        }
    }

    public float[] textViewAbsoluteBounding(TextView textView, boolean rectBounding)
    {
        Rect rect = textViewBounds(textView);

        int[] loc = new int[2];
        textView.getLocationInWindow(loc);

        float x = (rect.left + rect.right) / 2.0f;
        float y = (rect.top + rect.bottom) / 2.0f;

        if (rectBounding)
        {
            return new float[]{x + loc[0], y + loc[1], (rect.right - rect.left) / 2.0f, (rect.bottom - rect.top) / 2.0f};
        }
        else
        {
            float r = Math.max(rect.right - rect.left, rect.bottom - rect.top) / 2.0f;
            return new float[]{x + loc[0], y + loc[1], r, r};
        }
    }

    public Rect textViewBounds(TextView textView)
    {
        String s = (String) textView.getText();

        // bounds will store the rectangle that will circumscribe the text.
        Rect bounds = new Rect();
        Paint textPaint = textView.getPaint();

        // Get the bounds for the text. Top and bottom are measured from the baseline. Left
        // and right are measured from 0.
        textPaint.getTextBounds(s, 0, s.length(), bounds);
        int baseline = textView.getBaseline();
        bounds.top = baseline + bounds.top;
        bounds.bottom = baseline + bounds.bottom;
        int startPadding = textView.getPaddingStart();
        bounds.left += startPadding;

        // textPaint.getTextBounds() has already computed a value for the width of the text,
        // however, Paint#measureText() gives a more accurate value.
        bounds.right = (int) textPaint.measureText(s, 0, s.length()) + startPadding;

        return bounds;
    }

}
