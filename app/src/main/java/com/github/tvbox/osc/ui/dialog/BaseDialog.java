package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;

import com.studio.osc.R;

import androidx.annotation.NonNull;

public class BaseDialog extends Dialog {
    public BaseDialog(@NonNull Context context) {
        super(context, R.style.CustomDialogStyle);
    }

    public BaseDialog(Context context, int customDialogStyle) {
        super(context, customDialogStyle);
    }

    @Override
    public void show() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        super.show();
        hideSysBar();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        // 部分厂商 TV 固件 clearFlags 后不会自动把焦点推入弹窗，手动触发一次确保遥控器可操作
        View decorView = getWindow().getDecorView();
        decorView.post(() -> {
            View focused = decorView.findFocus();
            if (focused == null) {
                decorView.requestFocus();
            }
        });
    }

    private void hideSysBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
    }
}
