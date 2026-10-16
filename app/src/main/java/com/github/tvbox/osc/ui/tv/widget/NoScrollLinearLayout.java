package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/**
 * 完全禁用滚动的LinearLayout
 * 用于固定侧边栏，防止触摸滑动
 */
public class NoScrollLinearLayout extends LinearLayout {
    
    public NoScrollLinearLayout(Context context) {
        super(context);
        init();
    }
    
    public NoScrollLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public NoScrollLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // 禁用所有滚动相关的功能
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
    }
    
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 不拦截触摸事件，让子View可以处理点击
        return false;
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 消费触摸事件但不产生滚动
        // 返回false让子View可以处理
        return false;
    }
    
    @Override
    public void scrollTo(int x, int y) {
        // 完全禁用scrollTo方法
        // 不调用super，阻止任何滚动
    }
    
    @Override
    public void scrollBy(int x, int y) {
        // 完全禁用scrollBy方法
        // 不调用super，阻止任何滚动
    }
    
    @Override
    public boolean canScrollVertically(int direction) {
        // 告诉系统此View不可垂直滚动
        return false;
    }
    
    @Override
    public boolean canScrollHorizontally(int direction) {
        // 告诉系统此View不可水平滚动
        return false;
    }
    
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // 阻止父View拦截触摸事件
        super.requestDisallowInterceptTouchEvent(true);
    }
}
