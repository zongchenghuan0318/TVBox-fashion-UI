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
    public static final int SCROLL_DURATION = 350;
    /** 缓和进出曲线：起步不急、收尾不顿 */
    private static final Interpolator SCROLL_INTERPOLATOR = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    private boolean userTouching;

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
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                userTouching = true;
                stopScroll();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                userTouching = false;
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    public boolean isUserTouching() {
        return userTouching;
    }

    public int contentCenterX() {
        return windowCenterInList();
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
        int left = Math.max(0, Math.min(getWidth() - cardWidth, windowCenterInList() - cardWidth / 2));
        int right = Math.max(0, getWidth() - left - cardWidth);
        if (getPaddingLeft() != left || getPaddingRight() != right) {
            setPadding(left, getPaddingTop(), right, getPaddingBottom());
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
        int childCenter = (card.getLeft() + card.getRight()) / 2;
        int distance = childCenter - windowCenterInList();
        if (Math.abs(distance) > 1) {
            stopScroll();
            scrollBy(distance, 0);
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
        int targetLeft = windowCenterInList() - cardWidth / 2;
        stopScroll();
        if (getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) getLayoutManager()).scrollToPositionWithOffset(position, targetLeft);
        } else {
            scrollToPosition(position);
        }
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
