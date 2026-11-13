package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.ui.view.HomeLayoutPreviewView;
import com.github.tvbox.osc.util.HawkConfig;
import com.studio.osc.R;

/**
 * 首页布局风格可视化选择器：用 2×2 空间示意图直观对比 4 种首页风格。
 */
public class HomeLayoutDialog extends BaseDialog {

    public interface OnPickListener {
        void onPick(int layoutStyle);
    }

    public HomeLayoutDialog(@NonNull Context context, int currentLayout, OnPickListener listener) {
        super(context);
        setContentView(R.layout.dialog_home_layout);

        bindCard(R.id.cardImmersive, R.id.previewImmersive, R.id.badgeImmersive,
                HawkConfig.HOME_LAYOUT_IMMERSIVE, currentLayout, listener);
        bindCard(R.id.cardAppleTv, R.id.previewAppleTv, R.id.badgeAppleTv,
                HawkConfig.HOME_LAYOUT_APPLE_TV, currentLayout, listener);
        bindCard(R.id.cardDrawer, R.id.previewDrawer, R.id.badgeDrawer,
                HawkConfig.HOME_LAYOUT_DRAWER, currentLayout, listener);
        bindCard(R.id.cardSidebar, R.id.previewSidebar, R.id.badgeSidebar,
                HawkConfig.HOME_LAYOUT_SIDEBAR, currentLayout, listener);

        final int initialId = cardIdFor(currentLayout);
        View root = findViewById(initialId);
        if (root != null) {
            root.post(root::requestFocus);
        }
    }

    private void bindCard(int cardId, int previewId, int badgeId, int style,
                          int currentLayout, OnPickListener listener) {
        HomeLayoutPreviewView preview = findViewById(previewId);
        if (preview != null) {
            preview.setLayoutStyle(style);
        }
        boolean selected = (style == currentLayout);
        View badge = findViewById(badgeId);
        if (badge != null) {
            badge.setVisibility(selected ? View.VISIBLE : View.GONE);
        }
        View card = findViewById(cardId);
        if (card != null) {
            card.setSelected(selected);
            card.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPick(style);
                }
                dismiss();
            });
        }
    }

    private int cardIdFor(int layout) {
        switch (layout) {
            case HawkConfig.HOME_LAYOUT_APPLE_TV:
                return R.id.cardAppleTv;
            case HawkConfig.HOME_LAYOUT_DRAWER:
                return R.id.cardDrawer;
            case HawkConfig.HOME_LAYOUT_SIDEBAR:
                return R.id.cardSidebar;
            case HawkConfig.HOME_LAYOUT_IMMERSIVE:
            default:
                return R.id.cardImmersive;
        }
    }
}
