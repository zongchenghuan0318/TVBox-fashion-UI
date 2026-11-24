package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.studio.osc.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


/**
 * @author pj567
 * @date :2020/12/23
 * @description: 电视端虚拟键盘，支持焦点记忆、长按删除连续删字、直达搜索与扫码搜切换
 */
public class SearchKeyboard extends FrameLayout {
    public static final int KEY_POS_CLEAR = 0;
    public static final int KEY_POS_DELETE = 1;
    public static final int KEY_POS_REMOTE = 38;
    public static final int KEY_POS_SEARCH = 39;
    private static final long REPEAT_DELETE_INITIAL_MS = 320L;
    private static final long REPEAT_DELETE_INTERVAL_MS = 110L;

    private RecyclerView mRecyclerView;
    private final List<String> keys = Arrays.asList(
            "清空", "删除",
            "A", "B", "C", "D", "E", "F",
            "G", "H", "I", "J", "K", "L",
            "M", "N", "O", "P", "Q", "R",
            "S", "T", "U", "V", "W", "X",
            "Y", "Z", "1", "2", "3", "4",
            "5", "6", "7", "8", "9", "0",
            "扫码搜", "立即搜索"
    );
    private final List<Keyboard> keyboardList = new ArrayList<>();
    private OnSearchKeyListener searchKeyListener;
    private Runnable onInteractListener;
    private int lastFocusedPosition = 2; // 默认记住字母 A，避免回到键盘时落到「清空」误删
    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private boolean repeatDeleteActive;
    private final Runnable repeatDeleteRunnable = new Runnable() {
        @Override
        public void run() {
            if (!repeatDeleteActive) return;
            if (searchKeyListener != null) {
                searchKeyListener.onSearchKey(KEY_POS_DELETE, keys.get(KEY_POS_DELETE));
            }
            repeatHandler.postDelayed(this, REPEAT_DELETE_INTERVAL_MS);
        }
    };

    private final OnFocusChangeListener focusChangeListener = new OnFocusChangeListener() {
        @Override
        public void onFocusChange(View itemView, boolean hasFocus) {
            if (null != itemView && itemView != mRecyclerView) {
                itemView.setSelected(hasFocus);
                if (hasFocus) {
                    int pos = mRecyclerView.getChildAdapterPosition(itemView);
                    if (pos >= 0 && pos < keys.size()) {
                        lastFocusedPosition = pos;
                    }
                } else {
                    stopRepeatDelete();
                }
            }
        }
    };

    public SearchKeyboard(@NonNull Context context) {
        this(context, null);
    }

    public SearchKeyboard(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchKeyboard(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    private void initView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_keyborad, this);
        mRecyclerView = view.findViewById(R.id.mRecyclerView);
        GridLayoutManager manager = new GridLayoutManager(getContext(), 6);
        mRecyclerView.setLayoutManager(manager);
        mRecyclerView.setItemAnimator(null);
        mRecyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View child) {
                if (child.isFocusable() && null == child.getOnFocusChangeListener()) {
                    child.setOnFocusChangeListener(focusChangeListener);
                }
                bindDeleteRepeatHandlers(child);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
                stopRepeatDelete();
            }
        });
        int size = keys.size();
        for (int i = 0; i < size; i++) {
            keyboardList.add(new Keyboard(1, keys.get(i)));
        }
        int preferredKeyHeight = getResources().getDimensionPixelSize(R.dimen.vs_42);
        final KeyboardAdapter adapter = new KeyboardAdapter(keyboardList, preferredKeyHeight);
        mRecyclerView.setAdapter(adapter);
        adapter.setSpanSizeLookup(new BaseQuickAdapter.SpanSizeLookup() {
            @Override
            public int getSpanSize(GridLayoutManager gridLayoutManager, int position) {
                if (position == KEY_POS_CLEAR || position == KEY_POS_DELETE
                        || position == KEY_POS_REMOTE || position == KEY_POS_SEARCH) {
                    return 3;
                }
                return 1;
            }
        });
        int rows = 1 + manager.getSpanSizeLookup().getSpanGroupIndex(keys.size() - 1, manager.getSpanCount());
        int minimumKeyHeight = getResources().getDimensionPixelSize(R.dimen.vs_35);
        int rowMargins = 2 * getResources().getDimensionPixelSize(R.dimen.vs_2);
        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int availableHeight = bottom - top;
            int fittingKeyHeight = availableHeight / rows - rowMargins;
            adapter.setKeyHeight(Math.max(minimumKeyHeight, Math.min(preferredKeyHeight, fittingKeyHeight)));
        });

        adapter.setOnItemClickListener((adp, v, position) -> {
            if (onInteractListener != null) {
                onInteractListener.run();
            }
            Keyboard keyboard = (Keyboard) adp.getItem(position);
            if (searchKeyListener != null && keyboard != null) {
                searchKeyListener.onSearchKey(position, keyboard.getKey());
            }
        });
    }

    private void bindDeleteRepeatHandlers(final View child) {
        child.setOnTouchListener((v, event) -> {
            int pos = mRecyclerView.getChildAdapterPosition(v);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (onInteractListener != null) {
                    onInteractListener.run();
                }
                if (pos == KEY_POS_DELETE) {
                    startRepeatDelete();
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (pos == KEY_POS_DELETE) {
                    stopRepeatDelete();
                }
            }
            return false;
        });
        child.setOnKeyListener((v, keyCode, event) -> {
            int pos = mRecyclerView.getChildAdapterPosition(v);
            if (pos == KEY_POS_DELETE
                    && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) {
                    if (searchKeyListener != null) {
                        searchKeyListener.onSearchKey(KEY_POS_DELETE, keys.get(KEY_POS_DELETE));
                    }
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    stopRepeatDelete();
                }
            }
            return false;
        });
    }

    private void startRepeatDelete() {
        stopRepeatDelete();
        repeatDeleteActive = true;
        repeatHandler.postDelayed(repeatDeleteRunnable, REPEAT_DELETE_INITIAL_MS);
    }

    private void stopRepeatDelete() {
        repeatDeleteActive = false;
        repeatHandler.removeCallbacks(repeatDeleteRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopRepeatDelete();
        super.onDetachedFromWindow();
    }

    /** 将焦点定位到键盘首个字母键（A），避免默认落到「清空」键误触；返回是否已同步获得焦点 */
    public boolean requestLetterFocus() {
        return requestKeyPositionFocus(2);
    }

    /** 恢复到上次离开键盘前停驻的按键（若上次是清空键则回落到字母 A，避免误清） */
    public boolean requestRestoredFocus() {
        int target = (lastFocusedPosition >= 1 && lastFocusedPosition < keys.size())
                ? lastFocusedPosition : 2;
        return requestKeyPositionFocus(target);
    }

    private boolean requestKeyPositionFocus(final int targetPos) {
        if (mRecyclerView == null || !isShown()) return false;
        RecyclerView.LayoutManager manager = mRecyclerView.getLayoutManager();
        if (manager == null) return false;
        View target = manager.findViewByPosition(targetPos);
        if (target != null && target.requestFocus()) return true;
        // 触屏模式下普通按键不支持 focusableInTouchMode，不伪造成功也不再异步抢焦
        if (isInTouchMode()) return false;
        mRecyclerView.post(() -> {
            if (!isShown() || isInTouchMode()) return;
            View visibleTarget = manager.findViewByPosition(targetPos);
            if (visibleTarget != null && visibleTarget.requestFocus()) return;
            mRecyclerView.scrollToPosition(targetPos);
            mRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    View laidOutTarget = manager.findViewByPosition(targetPos);
                    if (isShown() && !isInTouchMode() && laidOutTarget != null) {
                        laidOutTarget.requestFocus();
                    }
                }
            });
        });
        return false;
    }

    static class Keyboard implements MultiItemEntity {
        private int itemType;
        private String key;

        private Keyboard(int itemType, String key) {
            this.itemType = itemType;
            this.key = key;
        }

        @Override
        public int getItemType() {
            return itemType;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    private static class KeyboardAdapter extends BaseMultiItemQuickAdapter<Keyboard, BaseViewHolder> {
        private int keyHeight;

        private KeyboardAdapter(List<Keyboard> data, int keyHeight) {
            super(data);
            this.keyHeight = keyHeight;
            addItemType(1, R.layout.item_keyboard);
        }

        private void setKeyHeight(int keyHeight) {
            if (this.keyHeight == keyHeight) return;
            this.keyHeight = keyHeight;
            notifyItemRangeChanged(0, getItemCount());
        }

        @Override
        protected void convert(BaseViewHolder helper, Keyboard item) {
            if (helper.getItemViewType() == 1) {
                helper.setText(R.id.keyName, item.key);
                ViewGroup.LayoutParams params = helper.itemView.getLayoutParams();
                if (params.height != keyHeight) {
                    params.height = keyHeight;
                    helper.itemView.setLayoutParams(params);
                }
            }
        }
    }

    public void setOnSearchKeyListener(OnSearchKeyListener listener) {
        searchKeyListener = listener;
    }

    public void setOnInteractListener(Runnable listener) {
        onInteractListener = listener;
    }

    public interface OnSearchKeyListener {
        void onSearchKey(int pos, String key);
    }
}
