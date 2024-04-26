package com.appy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.animation.Animator;
import androidx.core.animation.LinearInterpolator;
import androidx.core.animation.ValueAnimator;

public class OverlayHoleView extends View implements ValueAnimator.AnimatorUpdateListener
{
    public interface OnHoleClick
    {
        void onHoleClick();
    }

    public enum HoleShape
    {
        None,
        Circle,
        Rect,
    }

    HoleShape holeShape = HoleShape.None;
    int holeX = 0;
    int holeY = 0;
    float holeW = 0;
    float holeH = 0;

    int overlayColor = Color.argb(200, 0, 0, 0);

    private RadialGradient mGradient = null;
    private Paint mPaintFill = null;
    private Paint mPaintHole = null;
    private Paint mPaintRing = null;
    private OnHoleClick onHoleClick = null;

    private Handler handler;

    private ValueAnimator animator;
    private float animationValue = 0;

    public OverlayHoleView(Context context)
    {
        super(context);
        init();
    }

    public OverlayHoleView(Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }

    public OverlayHoleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        init();
    }

    public OverlayHoleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes)
    {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public void init()
    {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        handler = new Handler();

        mPaintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintFill.setStyle(Paint.Style.FILL);

        mPaintHole = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintHole.setStyle(Paint.Style.FILL);
        mPaintHole.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        mPaintRing = new Paint(Paint.ANTI_ALIAS_FLAG);

        animator = new ValueAnimator();
        animator.setDuration(1000);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //if (mGradient != null)
        {
            mPaintFill.setColor(overlayColor);
            canvas.drawRect(0, 0, getWidth(), getHeight(), mPaintFill);

            if (holeW > 0 && holeH > 0 && holeShape != HoleShape.None)
            {
                if (holeShape == HoleShape.Circle)
                {
                    canvas.drawCircle(holeX, holeY, holeW, mPaintHole);

                    mPaintRing.setColor(Color.argb((int)((1 - animationValue) * 255), 255, 255, 255));
                    mPaintRing.setStyle(Paint.Style.STROKE);
                    mPaintRing.setStrokeWidth(5f);
                    canvas.drawCircle(holeX, holeY, holeW + animationValue * holeW, mPaintRing);
                }
                else if (holeShape == HoleShape.Rect)
                {
                    canvas.drawRoundRect(holeX - holeW, holeY - holeH,
                            holeX + holeW, holeY + holeH,
                            holeW * 0.1f, holeH * 0.1f, mPaintHole);

                    mPaintRing.setColor(Color.argb((int)((1 - animationValue) * 255), 255, 255, 255));
                    mPaintRing.setStyle(Paint.Style.STROKE);
                    mPaintRing.setStrokeWidth(5f);
                    float W = holeW + (holeW * animationValue);
                    float H = holeH + (holeH * animationValue);
                    canvas.drawRoundRect(holeX - W, holeY - H,
                                         holeX + W, holeY + H,
                                         W * 0.1f, H * 0.1f, mPaintRing);
                }
            }
            else
            {
                stopAnimation();
            }

            if (holeW > 0 && holeH > 0 && holeShape != HoleShape.None)
            {
                if (!animator.isStarted())
                {
                    startAnimation();
                }
            }
        }
    }

    @Override
    public void onAnimationUpdate(@NonNull Animator animation)
    {
        animationValue = (float)animator.getAnimatedValue();
        this.invalidate();
    }

    // Call this to start the animation
    public void startAnimation()
    {
        // Sets the START and END values for the animation (FROM current y position TO next position)
        animator.setFloatValues(0, 1);
        animator.start();
    }

    public void stopAnimation()
    {
        animator.cancel();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);

        if (!isEnabled())
        {
            //Allow
            return false;
        }

        float xDiff = event.getX() - holeX;
        float yDiff = event.getY() - holeY;

        boolean inHole = false;

        switch (holeShape)
        {
            case Circle:
                inHole = xDiff * xDiff + yDiff * yDiff <= holeW * holeW;
                break;
            case Rect:
                inHole = Math.abs(xDiff) < holeW && Math.abs(yDiff) < holeH;
                break;
        }

        if (!inHole)
        {
            //Block
            return true;
        }

        if (onHoleClick != null)
        {
            handler.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    if (onHoleClick != null)
                    {
                        onHoleClick.onHoleClick();
                    }
                }
            }, 100);
        }

        //Allow
        return false;
    }

    public int getHoleX()
    {
        return holeX;
    }

    public void setAbsoluteHole(int holeX, int holeY, float holeW, float holeH, HoleShape holeShape)
    {
        int[] loc = new int[2];
        getLocationInWindow(loc);
        setHole(holeX - loc[0], holeY - loc[1], holeW, holeH, holeShape);
    }

    public void setHole(int holeX, int holeY, float holeW, float holeH, HoleShape holeShape)
    {
        this.holeX = holeX;
        this.holeY = holeY;
        this.holeW = holeW;
        this.holeH = holeH;
        this.holeShape = holeShape;

        invalidate();
    }

    public void setNoHole()
    {
        this.holeShape = HoleShape.None;
        this.holeW = 0;
        this.holeH = 0;
    }

    public void setOverlayColor(int overlayColor)
    {
        this.overlayColor = overlayColor;
        invalidate();
    }

    public int getHoleY()
    {
        return holeY;
    }

    public float getHoleW()
    {
        return holeW;
    }

    public float getHoleH()
    {
        return holeH;
    }

    public int getOverlayColor()
    {
        return overlayColor;
    }

    public HoleShape getHoleShape()
    {
        return holeShape;
    }

    public void setOnHoleClick(OnHoleClick onHoleClick)
    {
        this.onHoleClick = onHoleClick;
    }
}