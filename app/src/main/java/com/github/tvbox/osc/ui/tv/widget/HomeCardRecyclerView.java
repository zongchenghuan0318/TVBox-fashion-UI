package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import com.owen.tvrecyclerview.widget.TvRecyclerView;

/**
 * 首页卡片行。
 * 焦点在卡片间移动时，库内部触发的滚动用的是「按距离估算时长 + 快起急停」的默认动画，
 * 整排卡片滑起来偏硬；这里统一改为固定时长 + 缓和进出曲线，切换更丝滑。
 * 时长与 {@code FirstFragment} 里卡片聚焦放大保持一致，滑动和放大看起来是同一个动作。
 */
public class HomeCardRecyclerView extends TvRecyclerView {
    /** 焦点切换时整排滑动的时长（毫秒） */
    public static final int SCROLL_DURATION = 350;
    /** 缓和进出曲线：起步不急、收尾不顿 */
    private static final Interpolator SCROLL_INTERPOLATOR = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

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
        super.smoothScrollBy(dx, dy, SCROLL_INTERPOLATOR, SCROLL_DURATION);
    }
}
