package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.tvbox.osc.util.UpdateClient;
import com.studio.osc.BuildConfig;
import com.studio.osc.R;

import java.util.Locale;

import me.jessyan.autosize.utils.AutoSizeUtils;

public final class UpdateDialog extends BaseDialog {
    private final TextView title;
    private final TextView notes;
    private final TextView status;
    private final TextView action;
    private final TextView cancel;
    private final TextView browser;
    private final ProgressBar progress;

    public UpdateDialog(Activity activity) {
        super(activity);
        setContentView(R.layout.dialog_update);
        setCanceledOnTouchOutside(false);
        title = findViewById(R.id.updateTitle);
        notes = findViewById(R.id.updateNotes);
        status = findViewById(R.id.updateStatus);
        action = findViewById(R.id.updateAction);
        cancel = findViewById(R.id.updateCancel);
        browser = findViewById(R.id.updateBrowser);
        progress = findViewById(R.id.updateProgress);
        cancel.setOnClickListener(v -> dismiss());
    }

    @Override public void show() {
        super.show();
        getWindow().setLayout(AutoSizeUtils.mm2px(getContext(), 760), ViewGroup.LayoutParams.WRAP_CONTENT);
        cancel.requestFocus();
    }

    public void checking() {
        title.setText("检查更新");
        notes.setText("正在获取最新版本信息…");
        status.setText("");
        action.setVisibility(View.GONE);
        cancel.setText("取消");
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
    }

    public void checked(String message, Runnable retry) {
        notes.setText("当前版本 " + BuildConfig.VERSION_NAME);
        result(message, "重新检查", retry);
    }

    public void available(UpdateClient.Info info, Runnable download, Runnable openBrowser) {
        title.setText("发现新版本 " + info.versionName);
        notes.setText(info.changelog.isEmpty() ? "暂无更新说明" : info.changelog);
        progress.setVisibility(View.GONE);
        status.setText(String.format(Locale.CHINA, "安装包 %.1f MB", info.size / 1048576.0));
        setAction("立即更新", download);
        cancel.setText("稍后再说");
        browser.setVisibility(View.VISIBLE);
        browser.setOnClickListener(v -> openBrowser.run());
    }

    public void progress(long received, long total, int route) {
        if (action.getVisibility() == View.VISIBLE) cancel.requestFocus();
        action.setVisibility(View.GONE);
        browser.setVisibility(View.GONE);
        cancel.setText("取消下载");
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        int percent = (int) (received * 100.0 / total);
        progress.setProgress(percent);
        String line = route == 2 ? "GitHub 原始线路" : "加速线路 " + (route + 1);
        status.setText(String.format(Locale.CHINA, "%s · %d%%（%.1f / %.1f MB）%s", line, percent,
                received / 1048576.0, total / 1048576.0, received == total ? "\n正在验证安装包…" : ""));
    }

    public void result(String message, String button, Runnable retry) {
        status.setText(message);
        progress.setVisibility(View.GONE);
        cancel.setText("关闭");
        browser.setVisibility(browser.hasOnClickListeners() ? View.VISIBLE : View.GONE);
        if (retry == null) action.setVisibility(View.GONE);
        else setAction(button, retry);
    }

    public void ready(Runnable install) {
        progress.setVisibility(View.GONE);
        status.setText("安装包验证通过，请确认安装");
        cancel.setText("关闭");
        setAction("安装新版", install);
    }

    private void setAction(String text, Runnable listener) {
        action.setVisibility(View.VISIBLE);
        action.setText(text);
        action.setOnClickListener(v -> listener.run());
        action.requestFocus();
    }
}
