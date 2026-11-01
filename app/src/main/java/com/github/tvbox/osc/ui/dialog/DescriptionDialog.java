package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.studio.osc.R;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;

/**
 * 影视全景剧情简介弹窗：支持遥控器上下滚动浏览长篇大论，返回键/点击直接退出
 */
public class DescriptionDialog extends BaseDialog {

    public DescriptionDialog(@NonNull @NotNull Context context, String title, String content) {
        super(context, R.style.CustomDialogStyleDim);
        setContentView(R.layout.dialog_description);
        setCanceledOnTouchOutside(true);
        setCancelable(true);

        TextView tvTitle = findViewById(R.id.tvDesTitle);
        TextView tvContent = findViewById(R.id.tvDesFullContent);
        TextView btnClose = findViewById(R.id.btnDesClose);

        if (tvTitle != null) {
            tvTitle.setText(title == null || title.isEmpty() ? "剧情简介" : "《" + title + "》 剧情简介");
        }
        if (tvContent != null) {
            tvContent.setText(content == null || content.isEmpty() ? "暂无剧情简介" : content);
        }
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
            setOnShowListener(dialog -> btnClose.requestFocus());
        }
    }
}
