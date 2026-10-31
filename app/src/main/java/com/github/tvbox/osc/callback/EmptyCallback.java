package com.github.tvbox.osc.callback;

import android.content.Context;
import android.view.View;

import com.kingja.loadsir.callback.Callback;
import com.studio.osc.R;

/**
 * @author pj567
 * @date :2020/12/24
 * @description:
 */
public class EmptyCallback extends Callback {
    @Override
    protected int onCreateView() {
        return R.layout.loadsir_empty_layout;
    }

    @Override
    protected boolean onReloadEvent(Context context, View view) {
        return true;
    }

    @Override
    protected void onViewCreate(Context context, View view) {
        super.onViewCreate(context, view);
        View btn = view.findViewById(R.id.btnEmptyAction);
        if (btn != null) {
            btn.setFocusable(true);
            btn.post(btn::requestFocus);
        }
    }
}
