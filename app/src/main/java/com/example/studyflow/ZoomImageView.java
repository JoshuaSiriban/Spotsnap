package com.example.studyflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * An ImageView you can pinch to zoom, drag while zoomed, and double tap to zoom in and out.
 * It sits inside a ViewPager2: when the picture is not zoomed (or you drag past its edge),
 * the swipe moves on to the next picture.
 */
public class ZoomImageView extends AppCompatImageView {

    private static final float MAX_ZOOM = 4f;
    private static final float DOUBLE_TAP_ZOOM = 2.5f;

    private final Matrix matrix = new Matrix();
    private float zoom = 1f;                 // 1 = the whole picture fits the screen
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    public ZoomImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float target = Math.max(1f, Math.min(MAX_ZOOM, zoom * detector.getScaleFactor()));
                        zoomTo(target, detector.getFocusX(), detector.getFocusY());
                        return true;
                    }
                });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (zoom > 1.01f) fitImage();
                else zoomTo(DOUBLE_TAP_ZOOM, e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (zoom <= 1.01f) return false;   // not zoomed: let the pager swipe

                RectF rect = imageRect();
                if (rect == null) return false;
                // dragging past the edge of the picture: hand the swipe over to the pager
                boolean atRightEdge = distanceX > 0 && rect.right <= getWidth() + 1;
                boolean atLeftEdge = distanceX < 0 && rect.left >= -1;
                if ((atRightEdge || atLeftEdge) && getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }

                matrix.postTranslate(-distanceX, -distanceY);
                keepInside();
                setImageMatrix(matrix);
                return true;
            }
        });
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        fitImage();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitImage();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        // While zoomed (or pinching) the picture keeps the touches, the pager must not take them
        boolean start = event.getActionMasked() == MotionEvent.ACTION_DOWN
                || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN;
        if (start && (zoom > 1.01f || event.getPointerCount() > 1) && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
    }

    /** Centres the whole picture on the screen (zoom 1). */
    private void fitImage() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) return;
        float dw = drawable.getIntrinsicWidth();
        float dh = drawable.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;

        float scale = Math.min(getWidth() / dw, getHeight() / dh);
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate((getWidth() - dw * scale) / 2f, (getHeight() - dh * scale) / 2f);
        zoom = 1f;
        setImageMatrix(matrix);
    }

    private void zoomTo(float target, float focusX, float focusY) {
        float factor = target / zoom;
        zoom = target;
        matrix.postScale(factor, factor, focusX, focusY);
        keepInside();
        setImageMatrix(matrix);
    }

    /** Where the picture is on the screen right now (null without a picture). */
    private RectF imageRect() {
        Drawable drawable = getDrawable();
        if (drawable == null) return null;
        RectF rect = new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        matrix.mapRect(rect);
        return rect;
    }

    /** No empty space around a zoomed picture; a small picture stays centred. */
    private void keepInside() {
        RectF rect = imageRect();
        if (rect == null) return;

        float dx = 0;
        float dy = 0;
        if (rect.width() <= getWidth()) dx = (getWidth() - rect.width()) / 2f - rect.left;
        else if (rect.left > 0) dx = -rect.left;
        else if (rect.right < getWidth()) dx = getWidth() - rect.right;

        if (rect.height() <= getHeight()) dy = (getHeight() - rect.height()) / 2f - rect.top;
        else if (rect.top > 0) dy = -rect.top;
        else if (rect.bottom < getHeight()) dy = getHeight() - rect.bottom;

        matrix.postTranslate(dx, dy);
    }
}