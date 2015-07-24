/*
 * Copyright (c) 2010, Sony Ericsson Mobile Communication AB. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *    * Redistributions of source code must retain the above copyright notice, this
 *      list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright notice,
 *      this list of conditions and the following disclaimer in the documentation
 *      and/or other materials provided with the distribution.
 *    * Neither the name of the Sony Ericsson Mobile Communication AB nor the names
 *      of its contributors may be used to endorse or promote products derived from
 *      this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.dodola.bubblecloudexample;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Adapter;
import android.widget.AdapterView;

import java.util.LinkedList;

/**
 * A simple list view that displays the items as 3D blocks
 */
public class MyListView extends AdapterView<Adapter> {

    /** Width of the items compared to the width of the list */
    private static final float ITEM_WIDTH = 0.85f;

    /** Space occupied by the item relative to the height of the item */
    private static final float ITEM_VERTICAL_SPACE = 1.45f;

    /** Ambient light intensity */
    private static final int AMBIENT_LIGHT = 55;

    /** Diffuse light intensity */
    private static final int DIFFUSE_LIGHT = 200;

    /** Specular light intensity */
    private static final float SPECULAR_LIGHT = 70;

    /** Shininess constant */
    private static final float SHININESS = 200;

    /** The max intensity of the light */
    private static final int MAX_INTENSITY = 0xFF;

    /** Amount of down scaling */
    private static final float SCALE_DOWN_FACTOR = 0.15f;

    /** Amount to rotate during one screen length */
    private static final int DEGREES_PER_SCREEN = 270;

    /** Represents an invalid child index */
    private static final int INVALID_INDEX = -1;

    /** Distance to drag before we intercept touch events */
    private static final int TOUCH_SCROLL_THRESHOLD = 10;

    /** Children added with this layout mode will be added below the last child */
    private static final int LAYOUT_MODE_BELOW = 0;

    /** Children added with this layout mode will be added above the first child */
    private static final int LAYOUT_MODE_ABOVE = 1;

    /** User is not touching the list */
    private static final int TOUCH_STATE_RESTING = 0;

    /** User is touching the list and right now it's still a "click" */
    private static final int TOUCH_STATE_CLICK = 1;

    /** User is scrolling the list */
    private static final int TOUCH_STATE_SCROLL = 2;

    /** The adapter with all the data */
    private Adapter mAdapter;

    /** Current touch state */
    private int mTouchState = TOUCH_STATE_RESTING;

    /** X-coordinate of the down event */
    private int mTouchStartX;

    /** Y-coordinate of the down event */
    private int mTouchStartY;

    /**
     * The top of the first item when the touch down event was received
     */
    private int mListTopStart;

    /** The current top of the first item */
    private int mListTop;

    /**
     * The offset from the top of the currently first visible item to the top of
     * the first item
     */
    private int mListTopOffset;

    /** Current rotation */
    private int mListRotation;

    /** The adaptor position of the first visible item */
    private int mFirstItemPosition;

    /** The adaptor position of the last visible item */
    private int mLastItemPosition;

    /** A list of cached (re-usable) item views */
    private final LinkedList<View> mCachedItemViews = new LinkedList<View>();

    /** Used to check for long press actions */
    private Runnable mLongPressRunnable;

    /** Reusable rect */
    private Rect mRect;

    /** Camera used for 3D transformations */
    private Camera mCamera;

    /** Re-usable matrix for canvas transformations */
    private Matrix mMatrix;

    /** Paint object to draw with */
    private Paint mPaint;

    /** true if rotation of the items is enabled */
    private boolean mRotationEnabled = true;

    /** true if lighting of the items is enabled */
    private boolean mLightEnabled = true;

    /**
     * Constructor
     * 
     * @param context The context
     * @param attrs Attributes
     */
    public MyListView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setAdapter(final Adapter adapter) {
        mAdapter = adapter;
        removeAllViewsInLayout();
        requestLayout();
    }

    @Override
    public Adapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void setSelection(final int position) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public View getSelectedView() {
        throw new UnsupportedOperationException("Not supported");
    }

    /**
     * Enables and disables individual rotation of the items.
     * 
     * @param enable If rotation should be enabled or not
     */
    public void enableRotation(final boolean enable) {
        mRotationEnabled = enable;
        if (!mRotationEnabled) {
            mListRotation = 0;
        }
        invalidate();
    }

    /**
     * Checks whether rotation is enabled
     * 
     * @return true if rotation is enabled
     */
    public boolean isRotationEnabled() {
        return mRotationEnabled;
    }

    /**
     * Enables and disables lighting of the items.
     * 
     * @param enable If lighting should be enabled or not
     */
    public void enableLight(final boolean enable) {
        mLightEnabled = enable;
        if (!mLightEnabled) {
            mPaint.setColorFilter(null);
        } else {
            mPaint.setAlpha(0xFF);
        }
        invalidate();
    }

    /**
     * Checks whether lighting is enabled
     * 
     * @return true if rotation is enabled
     */
    public boolean isLightEnabled() {
        return mLightEnabled;
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startTouch(event);
                return false;

            case MotionEvent.ACTION_MOVE:
                return startScrollIfNeeded(event);

            default:
                endTouch();
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (getChildCount() == 0) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startTouch(event);
                break;

            case MotionEvent.ACTION_MOVE:
                if (mTouchState == TOUCH_STATE_CLICK) {
                    startScrollIfNeeded(event);
                }
                if (mTouchState == TOUCH_STATE_SCROLL) {
                    scrollList((int)event.getY() - mTouchStartY);
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mTouchState == TOUCH_STATE_CLICK) {
                    clickChildAt((int)event.getX(), (int)event.getY());
                }
                endTouch();
                break;

            default:
                endTouch();
                break;
        }
        return true;
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right,
            final int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // if we don't have an adapter, we don't need to do anything
        if (mAdapter == null) {
            return;
        }

        if (getChildCount() == 0) {
            mLastItemPosition = -1;
            fillListDown(mListTop, 0);
        } else {
            final int offset = mListTop + mListTopOffset - getChildTop(getChildAt(0));
            removeNonVisibleViews(offset);
            fillList(offset);
        }

        positionItems();
        invalidate();
    }

    @Override
    protected boolean drawChild(final Canvas canvas, final View child, final long drawingTime) {
        // get the bitmap
        final Bitmap bitmap = child.getDrawingCache();
        if (bitmap == null) {
            // if the is null for some reason, default to the standard
            // drawChild implementation
            return super.drawChild(canvas, child, drawingTime);
        }

        // get top left coordinates
        final int top = child.getTop();
        final int left = child.getLeft();

        // get centerX and centerY
        final int childWidth = child.getWidth();
        final int childHeight = child.getHeight();
        final int centerX = childWidth / 2;
        final int centerY = childHeight / 2;

        // get scale
        final float halfHeight = getHeight() / 2;
        final float distFromCenter = (top + centerY - halfHeight) / halfHeight;
        final float scale = (float)(1 - SCALE_DOWN_FACTOR * (1 - Math.cos(distFromCenter)));

        // get rotation
        float childRotation = mListRotation - 20 * distFromCenter;
        childRotation %= 90;
        if (childRotation < 0) {
            childRotation += 90;
        }

        // draw the item
        if (childRotation < 45) {
            drawFace(canvas, bitmap, top, left, centerX, centerY, scale, childRotation - 90);
            drawFace(canvas, bitmap, top, left, centerX, centerY, scale, childRotation);
        } else {
            drawFace(canvas, bitmap, top, left, centerX, centerY, scale, childRotation);
            drawFace(canvas, bitmap, top, left, centerX, centerY, scale, childRotation - 90);
        }

        return false;
    }

    /**
     * Draws a face of the 3D block
     * 
     * @param canvas The canvas to draw on
     * @param view A bitmap of the view to draw
     * @param top Top placement of the view
     * @param left Left placement of the view
     * @param centerX Center x-coordinate of the view
     * @param centerY Center y-coordinate of the view
     * @param scale The scale to draw the view in
     * @param rotation The rotation of the view
     */
    private void drawFace(final Canvas canvas, final Bitmap view, final int top, final int left,
            final int centerX, final int centerY, final float scale, final float rotation) {

        // create the camera if we haven't before
        if (mCamera == null) {
            mCamera = new Camera();
        }

        // save the camera state
        mCamera.save();

        // translate and then rotate the camera
        mCamera.translate(0, 0, centerY);
        mCamera.rotateX(rotation);
        mCamera.translate(0, 0, -centerY);

        // create the matrix if we haven't before
        if (mMatrix == null) {
            mMatrix = new Matrix();
        }

        // get the matrix from the camera and then restore the camera
        mCamera.getMatrix(mMatrix);
        mCamera.restore();

        // translate and scale the matrix
        mMatrix.preTranslate(-centerX, -centerY);
        mMatrix.postScale(scale, scale);
        mMatrix.postTranslate(left + centerX, top + centerY);

        // create and initialize the paint object
        if (mPaint == null) {
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setFilterBitmap(true);
        }

        // set the light
        if (mLightEnabled) {
            mPaint.setColorFilter(calculateLight(rotation));
        } else {
            mPaint.setAlpha(0xFF - (int)(2 * Math.abs(rotation)));
        }

        // draw the bitmap
        canvas.drawBitmap(view, mMatrix, mPaint);
    }

    /**
     * Calculates the lighting of the item based on rotation.
     * 
     * @param rotation The rotation of the item
     * @return A color filter to use
     */
    private LightingColorFilter calculateLight(final float rotation) {
        final double cosRotation = Math.cos(Math.PI * rotation / 180);
        int intensity = AMBIENT_LIGHT + (int)(DIFFUSE_LIGHT * cosRotation);
        int highlightIntensity = (int)(SPECULAR_LIGHT * Math.pow(cosRotation, SHININESS));

        if (intensity > MAX_INTENSITY) {
            intensity = MAX_INTENSITY;
        }
        if (highlightIntensity > MAX_INTENSITY) {
            highlightIntensity = MAX_INTENSITY;
        }

        final int light = Color.rgb(intensity, intensity, intensity);
        final int highlight = Color.rgb(highlightIntensity, highlightIntensity, highlightIntensity);

        return new LightingColorFilter(light, highlight);
    }

    /**
     * Sets and initializes all things that need to when we start a touch
     * gesture.
     * 
     * @param event The down event
     */
    private void startTouch(final MotionEvent event) {
        // save the start place
        mTouchStartX = (int)event.getX();
        mTouchStartY = (int)event.getY();
        mListTopStart = getChildTop(getChildAt(0)) - mListTopOffset;

        // start checking for a long press
        startLongPressCheck();

        // we don't know if it's a click or a scroll yet, but until we know
        // assume it's a click
        mTouchState = TOUCH_STATE_CLICK;
    }

    /**
     * Resets and recycles all things that need to when we end a touch gesture
     */
    private void endTouch() {
        // remove any existing check for longpress
        removeCallbacks(mLongPressRunnable);

        // reset touch state
        mTouchState = TOUCH_STATE_RESTING;
    }

    /**
     * Scrolls the list. Takes care of updating rotation (if enabled) and
     * snapping
     * 
     * @param scrolledDistance The distance to scroll
     */
    private void scrollList(final int scrolledDistance) {
        mListTop = mListTopStart + scrolledDistance;
        if (mRotationEnabled) {
            mListRotation = -(DEGREES_PER_SCREEN * mListTop) / getHeight();
        }
        requestLayout();
    }

    /**
     * Posts (and creates if necessary) a runnable that will when executed call
     * the long click listener
     */
    private void startLongPressCheck() {
        // create the runnable if we haven't already
        if (mLongPressRunnable == null) {
            mLongPressRunnable = new Runnable() {
                public void run() {
                    if (mTouchState == TOUCH_STATE_CLICK) {
                        final int index = getContainingChildIndex(mTouchStartX, mTouchStartY);
                        if (index != INVALID_INDEX) {
                            longClickChild(index);
                        }
                    }
                }
            };
        }

        // then post it with a delay
        postDelayed(mLongPressRunnable, ViewConfiguration.getLongPressTimeout());
    }

    /**
     * Checks if the user has moved far enough for this to be a scroll and if
     * so, sets the list in scroll mode
     * 
     * @param event The (move) event
     * @return true if scroll was started, false otherwise
     */
    private boolean startScrollIfNeeded(final MotionEvent event) {
        final int xPos = (int)event.getX();
        final int yPos = (int)event.getY();
        if (xPos < mTouchStartX - TOUCH_SCROLL_THRESHOLD
                || xPos > mTouchStartX + TOUCH_SCROLL_THRESHOLD
                || yPos < mTouchStartY - TOUCH_SCROLL_THRESHOLD
                || yPos > mTouchStartY + TOUCH_SCROLL_THRESHOLD) {
            // we've moved far enough for this to be a scroll
            removeCallbacks(mLongPressRunnable);
            mTouchState = TOUCH_STATE_SCROLL;
            return true;
        }
        return false;
    }

    /**
     * Returns the index of the child that contains the coordinates given.
     * 
     * @param x X-coordinate
     * @param y Y-coordinate
     * @return The index of the child that contains the coordinates. If no child
     *         is found then it returns INVALID_INDEX
     */
    private int getContainingChildIndex(final int x, final int y) {
        if (mRect == null) {
            mRect = new Rect();
        }
        for (int index = 0; index < getChildCount(); index++) {
            getChildAt(index).getHitRect(mRect);
            if (mRect.contains(x, y)) {
                return index;
            }
        }
        return INVALID_INDEX;
    }

    /**
     * Calls the item click listener for the child with at the specified
     * coordinates
     * 
     * @param x The x-coordinate
     * @param y The y-coordinate
     */
    private void clickChildAt(final int x, final int y) {
        final int index = getContainingChildIndex(x, y);
        if (index != INVALID_INDEX) {
            final View itemView = getChildAt(index);
            final int position = mFirstItemPosition + index;
            final long id = mAdapter.getItemId(position);
            performItemClick(itemView, position, id);
        }
    }

    /**
     * Calls the item long click listener for the child with the specified index
     * 
     * @param index Child index
     */
    private void longClickChild(final int index) {
        final View itemView = getChildAt(index);
        final int position = mFirstItemPosition + index;
        final long id = mAdapter.getItemId(position);
        final OnItemLongClickListener listener = getOnItemLongClickListener();
        if (listener != null) {
            listener.onItemLongClick(this, itemView, position, id);
        }
    }

    /**
     * Removes view that are outside of the visible part of the list. Will not
     * remove all views.
     * 
     * @param offset Offset of the visible area
     */
    private void removeNonVisibleViews(final int offset) {
        // We need to keep close track of the child count in this function. We
        // should never remove all the views, because if we do, we loose track
        // of were we are.
        int childCount = getChildCount();

        // if we are not at the bottom of the list and have more than one child
        if (mLastItemPosition != mAdapter.getCount() - 1 && childCount > 1) {
            // check if we should remove any views in the top
            View firstChild = getChildAt(0);
            while (firstChild != null && getChildBottom(firstChild) + offset < 0) {
                // remove the top view
                removeViewInLayout(firstChild);
                childCount--;
                mCachedItemViews.addLast(firstChild);
                mFirstItemPosition++;

                // update the list offset (since we've removed the top child)
                mListTopOffset += getChildHeight(firstChild);

                // Continue to check the next child only if we have more than
                // one child left
                if (childCount > 1) {
                    firstChild = getChildAt(0);
                } else {
                    firstChild = null;
                }
            }
        }

        // if we are not at the top of the list and have more than one child
        if (mFirstItemPosition != 0 && childCount > 1) {
            // check if we should remove any views in the bottom
            View lastChild = getChildAt(childCount - 1);
            while (lastChild != null && getChildTop(lastChild) + offset > getHeight()) {
                // remove the bottom view
                removeViewInLayout(lastChild);
                childCount--;
                mCachedItemViews.addLast(lastChild);
                mLastItemPosition--;

                // Continue to check the next child only if we have more than
                // one child left
                if (childCount > 1) {
                    lastChild = getChildAt(childCount - 1);
                } else {
                    lastChild = null;
                }
            }
        }
    }

    /**
     * Fills the list with child-views
     * 
     * @param offset Offset of the visible area
     */
    private void fillList(final int offset) {
        final int bottomEdge = getChildBottom(getChildAt(getChildCount() - 1));
        fillListDown(bottomEdge, offset);

        final int topEdge = getChildTop(getChildAt(0));
        fillListUp(topEdge, offset);
    }

    /**
     * Starts at the bottom and adds children until we've passed the list bottom
     * 
     * @param bottomEdge The bottom edge of the currently last child
     * @param offset Offset of the visible area
     */
    private void fillListDown(int bottomEdge, final int offset) {
        while (bottomEdge + offset < getHeight() && mLastItemPosition < mAdapter.getCount() - 1) {
            mLastItemPosition++;
            final View newBottomchild = mAdapter.getView(mLastItemPosition, getCachedView(), this);
            addAndMeasureChild(newBottomchild, LAYOUT_MODE_BELOW);
            bottomEdge += getChildHeight(newBottomchild);
        }
    }

    /**
     * Starts at the top and adds children until we've passed the list top
     * 
     * @param topEdge The top edge of the currently first child
     * @param offset Offset of the visible area
     */
    private void fillListUp(int topEdge, final int offset) {
        while (topEdge + offset > 0 && mFirstItemPosition > 0) {
            mFirstItemPosition--;
            final View newTopCild = mAdapter.getView(mFirstItemPosition, getCachedView(), this);
            addAndMeasureChild(newTopCild, LAYOUT_MODE_ABOVE);
            final int childHeight = getChildHeight(newTopCild);
            topEdge -= childHeight;

            // update the list offset (since we added a view at the top)
            mListTopOffset -= childHeight;
        }
    }

    /**
     * Adds a view as a child view and takes care of measuring it
     * 
     * @param child The view to add
     * @param layoutMode Either LAYOUT_MODE_ABOVE or LAYOUT_MODE_BELOW
     */
    private void addAndMeasureChild(final View child, final int layoutMode) {
        LayoutParams params = child.getLayoutParams();
        if (params == null) {
            params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        final int index = layoutMode == LAYOUT_MODE_ABOVE ? 0 : -1;
        child.setDrawingCacheEnabled(true);
        addViewInLayout(child, index, params, true);

        final int itemWidth = (int)(getWidth() * ITEM_WIDTH);
        child.measure(MeasureSpec.EXACTLY | itemWidth, MeasureSpec.UNSPECIFIED);
    }

    /**
     * Positions the children at the "correct" positions
     */
    private void positionItems() {
        int top = mListTop + mListTopOffset;

        for (int index = 0; index < getChildCount(); index++) {
            final View child = getChildAt(index);

            final int width = child.getMeasuredWidth();
            final int height = child.getMeasuredHeight();
            final int left = (getWidth() - width) / 2;
            final int margin = getChildMargin(child);
            final int childTop = top + margin;

            child.layout(left, childTop, left + width, childTop + height);
            top += height + 2 * margin;
        }
    }

    /**
     * Checks if there is a cached view that can be used
     * 
     * @return A cached view or, if none was found, null
     */
    private View getCachedView() {
        if (mCachedItemViews.size() != 0) {
            return mCachedItemViews.removeFirst();
        }
        return null;
    }

    /**
     * Returns the margin of the child view taking into account the
     * ITEM_VERTICAL_SPACE
     * 
     * @param child The child view
     * @return The margin of the child view
     */
    private int getChildMargin(final View child) {
        return (int)(child.getMeasuredHeight() * (ITEM_VERTICAL_SPACE - 1) / 2);
    }

    /**
     * Returns the top placement of the child view taking into account the
     * ITEM_VERTICAL_SPACE
     * 
     * @param child The child view
     * @return The top placement of the child view
     */
    private int getChildTop(final View child) {
        return child.getTop() - getChildMargin(child);
    }

    /**
     * Returns the bottom placement of the child view taking into account the
     * ITEM_VERTICAL_SPACE
     * 
     * @param child The child view
     * @return The bottom placement of the child view
     */
    private int getChildBottom(final View child) {
        return child.getBottom() + getChildMargin(child);
    }

    /**
     * Returns the height of the child view taking into account the
     * ITEM_VERTICAL_SPACE
     * 
     * @param child The child view
     * @return The height of the child view
     */
    private int getChildHeight(final View child) {
        return child.getMeasuredHeight() + 2 * getChildMargin(child);
    }
}
