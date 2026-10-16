package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

/**
 * 禁止用户手动切换页面的 ViewPager:
 * - 不响应触摸滑动
 * - 不响应方向键切换
 * - 只允许通过 setCurrentItem() 编程式切换
 * 
 * @author acer
 * @date 2018/7/24
 */

public class NoScrollViewPager extends ViewPager {

    public NoScrollViewPager(@NonNull Context context) {
        this(context, null);
    }

    public NoScrollViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * 拦截触摸事件:返回 false 让事件不会传递给 onTouchEvent,
     * 彻底阻止触摸滑动切换页面
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    /**
     * 不处理触摸事件:即使有触摸事件到达这里,也不响应
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return false;
    }

    /**
     * 禁止按键事件导致页面切换:
     * 返回 false 让按键事件不被 ViewPager 处理,
     * 避免左右方向键触发页面切换
     */
    @Override
    public boolean executeKeyEvent(KeyEvent event) {
        return false;
    }

    /**
     * 禁止方向键的箭头滚动:
     * 这是 ViewPager 响应方向键切换页面的核心方法,
     * 直接返回 false 彻底禁止方向键切换
     */
    @Override
    public boolean arrowScroll(int direction) {
        return false;
    }
}
