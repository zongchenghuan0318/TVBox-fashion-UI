package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.github.tvbox.osc.util.SeniorMode;
import com.github.tvbox.osc.util.UpdateClient;
import com.studio.osc.BuildConfig;
import com.studio.osc.R;

import java.util.Locale;

import me.jessyan.autosize.utils.AutoSizeUtils;

public final class UpdateDialog extends BaseDialog {
    private final TextView title, subtitle, badge, status, percent, action, cancel, browser, targetVersion, notes;
    private final ImageView icon;
    private final View versions, notesPanel, statusPanel;
    private final ProgressBar progress;

    public UpdateDialog(Activity activity) {
        super(activity);
        setContentView(R.layout.dialog_update);
        setCanceledOnTouchOutside(false);
        title = findViewById(R.id.updateTitle);
        subtitle = findViewById(R.id.updateSubtitle);
        badge = findViewById(R.id.updateBadge);
        icon = findViewById(R.id.updateIcon);
        notes = findViewById(R.id.updateNotes);
        targetVersion = findViewById(R.id.updateTargetVersion);
        versions = findViewById(R.id.updateVersions);
        notesPanel = findViewById(R.id.updateNotesPanel);
        statusPanel = findViewById(R.id.updateStatusPanel);
        status = findViewById(R.id.updateStatus);
        percent = findViewById(R.id.updatePercent);
        action = findViewById(R.id.updateAction);
        cancel = findViewById(R.id.updateCancel);
        browser = findViewById(R.id.updateBrowser);
        progress = findViewById(R.id.updateProgress);
        ((TextView) findViewById(R.id.updateCurrentVersion)).setText("v" + BuildConfig.VERSION_NAME);
        cancel.setOnClickListener(v -> dismiss());
    }

    @Override public void show() {
        super.show();
        int width = Math.min(AutoSizeUtils.mm2px(getContext(), 800),
                (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.9f));
        getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.dimAmount = 0.68f;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setAttributes(attributes);
        SeniorMode.apply(findViewById(R.id.updateSheetContent));
        cancel.requestFocus();
    }

    private void header(String heading, String detail, String label, int drawable, int color) {
        title.setText(heading);
        subtitle.setText(detail);
        badge.setText("Z影视 · " + label);
        int tint = ContextCompat.getColor(getContext(), color);
        badge.setTextColor(tint);
        icon.setImageResource(drawable);
        icon.setColorFilter(tint);
    }

    public void checking() {
        header("正在检查更新", "为你查找最新版本，请稍候", "检查中",
                R.drawable.ic_update_download, R.color.settings_accent);
        versions.setVisibility(View.GONE);
        notesPanel.setVisibility(View.GONE);
        browser.setVisibility(View.GONE);
        action.setVisibility(View.GONE);
        cancel.setText("取消检查");
        statusPanel.setVisibility(View.VISIBLE);
        status.setText("正在连接更新服务…");
        percent.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        cancel.requestFocus();
    }

    public void checked(String message, Runnable retry) {
        boolean latest = retry == null;
        header(latest ? "已是最新版本" : "暂时无法检查更新", "当前版本 v" + BuildConfig.VERSION_NAME,
                latest ? "保持最新" : "稍后再试",
                latest ? R.drawable.ic_update_check : R.drawable.ic_update_notice,
                latest ? R.color.settings_success : R.color.settings_warning);
        versions.setVisibility(View.GONE);
        notesPanel.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        percent.setVisibility(View.GONE);
        browser.setVisibility(View.GONE);
        statusPanel.setVisibility(latest ? View.GONE : View.VISIBLE);
        status.setText(message);
        cancel.setText("完成");
        if (latest) { action.setVisibility(View.GONE); cancel.requestFocus(); }
        else setAction("重新检查", retry);
    }

    public void available(UpdateClient.Info info, Runnable download, Runnable openBrowser) {
        header("新版本已就绪", "新功能与体验优化，已经为你准备好", "发现更新",
                R.drawable.ic_update_download, R.color.settings_accent);
        targetVersion.setText("v" + info.versionName);
        versions.setVisibility(View.VISIBLE);
        notesPanel.setVisibility(View.VISIBLE);
        notes.setText(info.changelog.isEmpty() ? "本次更新带来了体验优化。" : info.changelog);
        statusPanel.setVisibility(View.GONE);
        subtitle.setText(String.format(Locale.CHINA, "安装包 %.1f MB · 保留你的设置与观看记录", info.size / 1048576.0));
        setAction("下载更新", download);
        cancel.setText("稍后再说");
        browser.setVisibility(View.VISIBLE);
        browser.setOnClickListener(v -> openBrowser.run());
    }

    public void progress(long received, long total, int route) {
        boolean verifying = received == total;
        header(verifying ? "正在验证更新" : "正在下载新版", "保持当前窗口，完成后即可安装",
                verifying ? "验证中" : "下载中", R.drawable.ic_update_download, R.color.settings_accent);
        if (action.getVisibility() == View.VISIBLE) cancel.requestFocus();
        action.setVisibility(View.GONE);
        browser.setVisibility(View.GONE);
        notesPanel.setVisibility(View.GONE);
        cancel.setText("取消下载");
        statusPanel.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(verifying);
        int value = (int) (received * 100.0 / total);
        progress.setProgress(value);
        percent.setText(value + "%");
        percent.setVisibility(View.VISIBLE);
        status.setText(verifying ? "正在核对安装包，请稍候…" :
                String.format(Locale.CHINA, "%.1f MB / %.1f MB", received / 1048576.0, total / 1048576.0));
    }

    public void result(String message, String button, Runnable retry) {
        header("下载暂未完成", "请检查连接后重试，或使用浏览器下载", "需要重试",
                R.drawable.ic_update_notice, R.color.settings_warning);
        showNotice(message, button, retry);
    }

    public void installNotice(String message, String button, Runnable retry) {
        header("继续安装新版", "请按照系统提示，完成安装确认", "安装确认",
                R.drawable.ic_update_notice, R.color.settings_warning);
        showNotice(message, button, retry);
    }

    private void showNotice(String message, String button, Runnable retry) {
        notesPanel.setVisibility(View.GONE);
        statusPanel.setVisibility(View.VISIBLE);
        status.setText(message);
        progress.setVisibility(View.GONE);
        percent.setVisibility(View.GONE);
        cancel.setText("关闭");
        browser.setVisibility(browser.hasOnClickListeners() ? View.VISIBLE : View.GONE);
        if (retry == null) action.setVisibility(View.GONE);
        else setAction(button, retry);
    }

    public void ready(Runnable install) {
        header("准备好更新了", "安装后保留你的设置与观看记录", "验证通过",
                R.drawable.ic_update_check, R.color.settings_success);
        notesPanel.setVisibility(View.GONE);
        statusPanel.setVisibility(View.GONE);
        cancel.setText("暂不安装");
        setAction("安装新版", install);
    }

    private void setAction(String text, Runnable listener) {
        action.setVisibility(View.VISIBLE);
        action.setText(text);
        action.setOnClickListener(v -> listener.run());
        action.requestFocus();
    }
}
