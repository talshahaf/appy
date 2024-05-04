package com.appy;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintProperties;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.constraintlayout.widget.Guideline;

public class TutorialOverlayView extends ConstraintLayout
{
    private OverlayHoleView overlay;
    private TextView text;
    private ConstraintLayout box;
    private Guideline guideline;
    private Button button;

    public TutorialOverlayView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        init();
    }

    public TutorialOverlayView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }

    public TutorialOverlayView(Context context)
    {
        super(context);
        init();
    }

    private void init()
    {
        inflate(getContext(), R.layout.tutorial_overlay, this);
        overlay = findViewById(R.id.overlay);
        text = findViewById(R.id.overlay_text);
        box = findViewById(R.id.overlay_text_bg);
        guideline = findViewById(R.id.guideline);
        button = findViewById(R.id.overlay_btn);
    }

    public void setOverlayColor(int color)
    {
        overlay.setOverlayColor(color);
    }

    public void setAbsoluteHole(int holeX, int holeY, float holeW, float holeH, OverlayHoleView.HoleShape holeShape)
    {
        overlay.setAbsoluteHole(holeX, holeY, holeW, holeH, holeShape);
    }

    public float[] getAbsoluteHolePosition()
    {
        return overlay.getAbsoluteHolePosition();
    }

    public float[] getHolePosition()
    {
        return overlay.getHolePosition();
    }

    public void setNoHole()
    {
        overlay.setNoHole();
    }

    public void setText(String text)
    {
        this.text.setText(text);
    }

    public void setOnHoleClick(OverlayHoleView.OnHoleClick onHoleClick)
    {
        overlay.setOnHoleClick(onHoleClick);
    }

    public void setTextTopFromTop(float margin, boolean marginAsFactor)
    {
        float height = getMeasuredHeight();

        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) guideline.getLayoutParams();
        params.guidePercent = marginAsFactor ? margin : (margin / height);
        guideline.setLayoutParams(params);

        setBoxToGuideline(true);
    }

    public void setTextFromHole(float margin, boolean marginAsFactor, boolean textTop)
    {
        float height = getMeasuredHeight();
        float[] hole = getHolePosition();

        float pixelMargin = marginAsFactor ? (margin * height) : margin;
        float guidePosition = textTop ? (hole[1] + hole[3] + pixelMargin) : (hole[1] - hole[3] - pixelMargin);

        //connectGuideline(textTop);
        ConstraintLayout.LayoutParams guideParams = (ConstraintLayout.LayoutParams) guideline.getLayoutParams();
        guideParams.guidePercent = guidePosition / height;
        guideline.setLayoutParams(guideParams);

        setBoxToGuideline(textTop);
    }

    public void setBoxToGuideline(boolean topToTop)
    {
        ConstraintLayout.LayoutParams boxParams = (ConstraintLayout.LayoutParams) box.getLayoutParams();
        boxParams.topToTop = topToTop ? R.id.guideline : ConstraintProperties.UNSET;
        boxParams.bottomToTop = topToTop ? ConstraintProperties.UNSET : R.id.guideline;
        box.setLayoutParams(boxParams);
    }

    public void setButtonText(String text)
    {
        button.setText(text);
    }

    public void setOnButtonClick(OnClickListener onclick)
    {
        button.setOnClickListener(onclick);
    }
}