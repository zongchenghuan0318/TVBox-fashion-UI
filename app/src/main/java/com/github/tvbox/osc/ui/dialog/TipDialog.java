package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.TextView;

import com.studio.osc.R;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;

public class TipDialog extends BaseDialog {

    public TipDialog(@NonNull @NotNull Context context, String tip, String left, String right, OnListener listener) {
        super(context);
        setContentView(R.layout.dialog_tip);
        setCanceledOnTouchOutside(false);
        TextView tipInfo = findViewById(R.id.tipInfo);
        TextView leftBtn = findViewById(R.id.leftBtn);
        TextView rightBtn = findViewById(R.id.rightBtn);
        tipInfo.setText(tip);
        leftBtn.setText(left);
        rightBtn.setText(right);
        leftBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.left();
            }
        });
        rightBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.right();
            }
        });
        setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (listener != null) listener.cancel();
            }
        });
    }

    public static void confirm(Context context, String title, String message, String confirmText, Runnable onConfirm) {
        final TipDialog[] holder = new TipDialog[1];
        TipDialog dialog = new TipDialog(context, message, confirmText, "取消", new OnListener() {
            @Override
            public void left() {
                holder[0].dismiss();
                if (onConfirm != null) onConfirm.run();
            }

            @Override
            public void right() {
                holder[0].dismiss();
            }

            @Override
            public void cancel() {
                holder[0].dismiss();
            }
        });
        holder[0] = dialog;
        TextView titleView = dialog.findViewById(R.id.tipTitle);
        if (titleView != null && title != null && !title.isEmpty()) {
            titleView.setText(title);
            titleView.setVisibility(View.VISIBLE);
        }
        dialog.setOnShowListener(d -> {
            View cancelBtn = dialog.findViewById(R.id.rightBtn);
            if (cancelBtn != null) cancelBtn.requestFocus();
        });
        dialog.show();
    }

    public interface OnListener {
        void left();

        void right();

        void cancel();
    }
}
