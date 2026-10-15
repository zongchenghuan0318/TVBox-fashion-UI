package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.TextView;

import com.lcstudio.commonsurport.util.PhoneParams;
import com.studio.osc.R;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;

public class AboutDialog extends BaseDialog {

    public AboutDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_about);
        TextView aboutTV = findViewById(R.id.about_tv);
        aboutTV.setText("本软件只提供聚合展示功能，所有资源来自网上, 软件不参与任何制作, 上传, 储存, 下载等内容. 软件仅供学习参考"
                +"\n版本号："+ PhoneParams.getAppSelfVersionName(getContext()));
    }
}