package com.appy;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintProperties;
import androidx.constraintlayout.widget.Guideline;

public class TutorialOverlayView extends ConstraintLayout
{
    private OverlayHoleView overlay;
    private TextView text;
    private ConstraintLayout box;
    private Guideline guideline;
    private Button button;
    private VideoView video;

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
        setVisibility(View.GONE);

        overlay = findViewById(R.id.overlay);
        text = findViewById(R.id.overlay_text);
        box = findViewById(R.id.overlay_text_bg);
        guideline = findViewById(R.id.guideline);
        button = findViewById(R.id.overlay_btn);
        video = findViewById(R.id.overlay_video);

        video.setVisibility(View.GONE);
        video.setOnPreparedListener(mp -> mp.setLooping(true));
    }

    public void setOverlayColor(int color)
    {
        overlay.setOverlayColor(color);
    }

    public void setAbsoluteHole(int holeX, int holeY, float holeW, float holeH, float holeClickW, float holeClickH, OverlayHoleView.HoleShape holeShape)
    {
        overlay.setAbsoluteHole(holeX, holeY, holeW, holeH, holeClickW, holeClickH, holeShape);
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

    public void setVideo(Uri uri)
    {
        video.setVisibility(View.VISIBLE);
        video.setVideoURI(uri);
        video.start();
    }

    public void setNoVideo()
    {
        video.setVisibility(View.GONE);
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

    public void hideBox()
    {
        box.setVisibility(View.VISIBLE);
        box.animate().alpha(0.0f).setDuration(100);
    }

    public void showBox()
    {
        box.setVisibility(View.VISIBLE);
        box.animate().alpha(1.0f).setDuration(100);
    }

    public void hideBoxNoAnimation()
    {
        box.setVisibility(View.GONE);
    }

    public void resumeVideoIfNeeded()
    {
        if (video.getVisibility() == View.VISIBLE)
        {
            video.start();
        }
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