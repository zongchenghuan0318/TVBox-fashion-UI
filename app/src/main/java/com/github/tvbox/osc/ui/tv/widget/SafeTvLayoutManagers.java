package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;

import androidx.recyclerview.widget.RecyclerView;

import com.lcstudio.commonsurport.L;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

/**
 * 电视端安全 LayoutManager：禁用预测性条目动画并对 onLayoutChildren 兜底，
 * 避免流式搜索结果持续合并/刷新时触发 RecyclerView Inconsistency detected 闪退。
 */
public final class SafeTvLayoutManagers {

    private SafeTvLayoutManagers() {
    }

    public static V7GridLayoutManager grid(Context context, int spanCount) {
        return new SafeGridLayoutManager(context, spanCount);
    }

    public static V7LinearLayoutManager linear(Context context, int orientation, boolean reverseLayout) {
        return new SafeLinearLayoutManager(context, orientation, reverseLayout);
    }

    private static final class SafeGridLayoutManager extends V7GridLayoutManager {
        SafeGridLayoutManager(Context context, int spanCount) {
            super(context, spanCount);
        }

        @Override
        public boolean supportsPredictiveItemAnimations() {
            return false;
        }

        @Override
        public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
            try {
                super.onLayoutChildren(recycler, state);
            } catch (IndexOutOfBoundsException e) {
                L.w("SafeGridLayoutManager onLayoutChildren ignored: " + e.getMessage());
            }
        }
    }

    private static final class SafeLinearLayoutManager extends V7LinearLayoutManager {
        SafeLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
            super(context, orientation, reverseLayout);
        }

        @Override
        public boolean supportsPredictiveItemAnimations() {
            return false;
        }

        @Override
        public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
            try {
                super.onLayoutChildren(recycler, state);
            } catch (IndexOutOfBoundsException e) {
                L.w("SafeLinearLayoutManager onLayoutChildren ignored: " + e.getMessage());
            }
        }
    }
}
