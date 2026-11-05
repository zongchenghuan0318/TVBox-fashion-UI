package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.MotionEvent;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.owen.tvrecyclerview.widget.TvRecyclerView;

/**
 * 首页卡片行。
 * 焦点在卡片间移动时，库内部触发的滚动用的是「按距离估算时长 + 快起急停」的默认动画，
 * 整排卡片滑起来偏硬；这里统一改为固定时长 + 缓和进出曲线，切换更丝滑。
 * 时长与 {@code FirstFragment} 里卡片聚焦放大保持一致，滑动和放大看起来是同一个动作。
 * <p>
 * 另外补上左右对称留白：没有留白时列表滚到起点/终点就停住了，
 * 首末两张卡永远只能贴在屏幕最左或最右，居中排布会断在两端。
 */
public class HomeCardRecyclerView extends TvRecyclerView {
    /** 焦点切换时整排滑动的时长（毫秒） */
    public static final int SCROLL_DURATION = 360;
    /** 缓和进出曲线：起步不急、收尾不顿 */
    private static final Interpolator SCROLL_INTERPOLATOR = new PathInterpolator(0.12f, 0.72f, 0.18f, 1f);
    public interface GestureListener { void onTouch(boolean touching); }
    private GestureListener gestureListener;
    private boolean userTouching, dragged;
    public void setGestureListener(GestureListener listener) { gestureListener = listener; }

    public HomeCardRecyclerView(Context context) {
        this(context, null);
    }

    public HomeCardRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HomeCardRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void smoothScrollBy(int dx, int dy) {
        // RecyclerView 对 0 位移也会启动一次 SmoothScroller。首页在焦点回调和
        // 自动轮播回调重叠时会不断收到这类请求，导致列表看起来一直在滚动。
        if (userTouching || (dx == 0 && dy == 0)) {
            return;
        }
        // 只保留最后一次定位请求，避免焦点切换和控件自身的可见性定位叠加。
        stopScroll();
        super.smoothScrollBy(dx, dy, SCROLL_INTERPOLATOR, SCROLL_DURATION);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                userTouching = true;
                dragged = false;
                if (gestureListener != null) gestureListener.onTouch(true);
                stopScroll();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                userTouching = false;
                break;
            default:
                break;
        }
        if (getScrollState() == SCROLL_STATE_DRAGGING) dragged = true;
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (gestureListener != null) gestureListener.onTouch(false);
            if (dragged && event.getActionMasked() == MotionEvent.ACTION_UP) snapToCenterCard();
        }
        return handled;
    }

    public boolean isUserTouching() {
        return userTouching;
    }

    public boolean verticalCards() {
        return getLayoutManager() instanceof LinearLayoutManager
                && ((LinearLayoutManager)getLayoutManager()).getOrientation() == LinearLayoutManager.VERTICAL;
    }

    public int cardCenter(View card) {
        return verticalCards() ? (card.getTop()+card.getBottom())/2 : (card.getLeft()+card.getRight())/2;
    }

    public int contentCenter() {
        return verticalCards() ? getHeight()/2 : windowCenterInList();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // 子视图布局完才能量到卡片宽度；补完留白后本方法会再走一遍，值不变即收敛
        applyCenterPadding();
    }

    /**
     * 左右留白：让首末两张卡也能停在屏幕正中。
     * 没有留白时列表滚到起点/终点就停住了，首末卡永远只能贴边，
     * 中间那些卡「滚到正中」的位置也偏在一边（列表本身并不在屏幕正中）。
     * 留白按屏幕（窗口）中点反推，左侧刚好等于「中点 - 半张卡」。
     *
     * @return 留白是否已经就位
     */
    public boolean applyCenterPadding() {
        int cardWidth = measuredCardWidth();
        if (getWidth() == 0 || cardWidth <= 0) {
            return false;
        }
        if (verticalCards()) {
            View card = getChildAt(0);
            if (card == null || card.getHeight() == 0) return false;
            int top = Math.max(0,(getHeight()-card.getHeight())/2);
            int left = Math.max(0,Math.min(getWidth()-cardWidth,windowCenterInList()-cardWidth/2));
            if (getPaddingTop()!=top || getPaddingLeft()!=left || getPaddingRight()!=0)
                setPadding(left,top,0,top);
            return true;
        }
        int left = Math.max(0, Math.min(getWidth() - cardWidth, windowCenterInList() - cardWidth / 2));
        int right = Math.max(0, getWidth() - left - cardWidth);
        int pad = getResources().getDimensionPixelSize(com.studio.osc.R.dimen.vs_8);
        if (getPaddingLeft() != left || getPaddingRight() != right || getPaddingTop() != pad) {
            setPadding(left, pad, right, pad);
        }
        return true;
    }

    /**
     * 把某张卡送到屏幕正中。首页焦点回调可能在滚动过程中再次触发，
     * 因此这里使用一次性位移，避免多个 SmoothScroller 互相重启而持续滚动。
     */
    public void centerCard(View card) {
        if (userTouching || card == null || card.getWidth() == 0 || getWidth() == 0) {
            return;
        }
        // RecyclerView 的正位移表示内容向左移，所以位移量是两者之差。
        int childCenter = cardCenter(card);
        int distance = childCenter - contentCenter();
        if (Math.abs(distance) > 1) {
            smoothScrollBy(verticalCards()?0:distance, verticalCards()?distance:0);
        }
    }

    /** 目标卡还没布局时一次性定位，避免先滚到目标再补一次居中造成卡顿 */
    public void centerPosition(int position) {
        if (getWidth() == 0 || getLayoutManager() == null) {
            return;
        }
        int cardWidth = measuredCardWidth();
        if (cardWidth <= 0) {
            scrollToPosition(position);
            return;
        }
        int targetLeft = 0; // LinearLayoutManager offsets are relative to the padded start.
        stopScroll();
        if (getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) getLayoutManager()).scrollToPositionWithOffset(position, targetLeft);
        } else {
            scrollToPosition(position);
        }
    }

    /**
     * 手动滑动结束后，将距离屏幕中心最近的卡片吸附到中心并交还焦点。
     */
    public void snapToCenterCard() {
        post(() -> {
            if (userTouching || !isAttachedToWindow() || getChildCount() == 0 || getLayoutManager() == null) {
                return;
            }
            final int centerX = contentCenter();
            View nearest = null;
            int nearestDistance = Integer.MAX_VALUE;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child == null) {
                    continue;
                }
                int distance = Math.abs(cardCenter(child) - centerX);
                if (distance < nearestDistance) {
                    nearest = child;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) {
                return;
            }
            int position = getChildAdapterPosition(nearest);
            if (position == NO_POSITION) {
                return;
            }
            int distance = cardCenter(nearest) - centerX;
            if (Math.abs(distance) > 1) {
                stopScroll();
                super.smoothScrollBy(verticalCards()?0:distance, verticalCards()?distance:0, SCROLL_INTERPOLATOR, SCROLL_DURATION);
            }
            setSelectedPosition(position);
            if (!isInTouchMode() && hasFocus()) nearest.requestFocus();
        });
    }

    /**
     * 屏幕（窗口）横向中点换算到列表自身的坐标系。
     * 列表左侧被侧边栏和内容内边距挤开，列表中点并不等于屏幕中点，必须按屏幕算。
     */
    private int windowCenterInList() {
        int[] listLoc = new int[2];
        getLocationOnScreen(listLoc);
        View root = getRootView();
        int[] rootLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        int rootWidth = root.getWidth();
        if (rootWidth <= 0) {
            return getWidth() / 2;
        }
        int center = rootLoc[0] + rootWidth / 2 - listLoc[0];
        if (center <= 0 || center >= getWidth()) {
            return getWidth() / 2;
        }
        return center;
    }

    private int measuredCardWidth() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != null && child.getWidth() > 0) {
                return child.getWidth();
            }
        }
        return 0;
    }

    /**
     * 库里「把聚焦的卡滚进视野」的默认实现，会按距离自己估时长、按列表半宽差算偏移，
     * 跟我们上面的居中滚动叠在一起滚两次，聚焦的卡就会在正中左右来回摆。
     * 这里直接关掉：居中只由 {@link #centerCard} 一处负责，无论自动轮播还是手动导航，
     * 落点算法都一样。
     */
    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
        return false;
    }
}
