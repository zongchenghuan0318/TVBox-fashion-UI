package com.github.tvbox.osc.ui.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.view.CapsuleSwitch;
import com.github.tvbox.osc.util.LiveSourceManager;
import com.studio.osc.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 直播源管理：每行显示名称、摘要与启用状态；操作统一收进“更多”菜单，
 * 遥控器每行只需一次焦点停留。
 */
public class LiveSourceManagerActivity extends BaseActivity {
    private static final String ACTION_TOGGLE = "切换启用";
    private static final String ACTION_UP = "上移";
    private static final String ACTION_DOWN = "下移";
    private static final String ACTION_EDIT = "编辑";
    private static final String ACTION_DELETE = "删除";

    private LinearLayout list;
    private ArrayList<LiveSourceManager.Source> sources;

    @Override protected int getLayoutResID() { return R.layout.activity_live_source_manager; }

    @Override
    protected void init() {
        list = findViewById(R.id.liveSourceList);
        findViewById(R.id.liveSourceBack).setOnClickListener(v -> finish());
        findViewById(R.id.liveSourceAdd).setOnClickListener(v -> edit(null, -1));
        render();
        findViewById(R.id.liveSourceAdd).post(() -> {
            if (list.getChildCount() > 1) {
                list.getChildAt(1).requestFocus();
            } else {
                findViewById(R.id.liveSourceAdd).requestFocus();
            }
        });
    }

    private void render() {
        render(-1);
    }

    /**
     * 重建列表后把焦点还给操作过的行，避免遥控器焦点被打回列表顶部。
     */
    private void render(int focusIndex) {
        sources = new ArrayList<>(LiveSourceManager.getSources());
        list.removeAllViews();
        addHeader("我的直播源");
        if (sources.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有自定义直播源\n点击右上角“添加直播源”开始配置");
            empty.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.settings_text_secondary));
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(20, 48, 20, 48);
            list.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (int i = 0; i < sources.size(); i++) addRow(sources.get(i), i);
        if (focusIndex >= 0) {
            int idx = Math.min(focusIndex, sources.size() - 1);
            View row = list.getChildAt(idx + 1); // 第 0 个子 View 是标题
            if (row != null) list.post(row::requestFocus);
        }
    }

    private void addHeader(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.color_FFFFFF));
        view.setTextSize(18);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(12, 10, 12, 10);
        list.addView(view, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void addRow(LiveSourceManager.Source source, int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundResource(R.drawable.selector_setting_row_unified);
        row.setPadding(dp(20), dp(14), dp(20), dp(14));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(0, 0, 0, dp(12));
        list.addView(row, rowParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        row.addView(texts, textsParams);

        TextView title = new TextView(this);
        title.setText(LiveSourceManager.displayName(source, index));
        title.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.color_FFFFFF));
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        texts.addView(title);

        TextView detail = new TextView(this);
        detail.setText(source.content.length() > 80 ? source.content.substring(0, 80) + "…" : source.content);
        detail.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.settings_text_secondary));
        detail.setTextSize(13);
        detail.setMaxLines(2);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.setMargins(0, dp(5), 0, 0);
        texts.addView(detail, detailParams);

        // 启用状态：胶囊开关只做展示，修改在“更多”菜单中进行
        CapsuleSwitch state = new CapsuleSwitch(this);
        state.setChecked(source.enabled);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-2, -2);
        stateParams.setMargins(dp(16), 0, dp(16), 0);
        row.addView(state, stateParams);

        TextView more = new TextView(this);
        more.setText("更多 ›");
        more.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.color_FFFFFF));
        more.setTextSize(15);
        more.setFocusable(false);
        row.addView(more);

        row.setContentDescription(LiveSourceManager.displayName(source, index)
                + (source.enabled ? "，已启用" : "，已停用") + "，按确定打开更多操作");
        row.setNextFocusUpId(index == 0 ? R.id.liveSourceBack : View.NO_ID);
        row.setNextFocusDownId(index == sources.size() - 1 ? R.id.liveSourceAdd : View.NO_ID);
        row.setOnClickListener(v -> showActions(source, index));
    }

    private void showActions(LiveSourceManager.Source source, int index) {
        List<String> actions = new ArrayList<>(Arrays.asList(
                source.enabled ? "停用" : "启用", ACTION_UP, ACTION_DOWN, ACTION_EDIT, ACTION_DELETE));
        SelectDialog.pick(this, LiveSourceManager.displayName(source, index), actions, -1,
                new SelectDialogAdapter.SelectDialogInterface<String>() {
                    @Override
                    public void click(String value, int pos) {
                        if (value.equals("启用") || value.equals("停用")) {
                            source.enabled = !source.enabled;
                            save();
                            render(index);
                        } else if (value.equals(ACTION_UP)) {
                            move(index, -1);
                        } else if (value.equals(ACTION_DOWN)) {
                            move(index, 1);
                        } else if (value.equals(ACTION_EDIT)) {
                            edit(source, index);
                        } else if (value.equals(ACTION_DELETE)) {
                            confirmDelete(source, index);
                        }
                    }

                    @Override
                    public String getDisplay(String val) {
                        return val;
                    }
                });
    }

    private void move(int index, int offset) {
        int target = index + offset;
        if (target < 0 || target >= sources.size()) return;
        LiveSourceManager.Source source = sources.remove(index);
        sources.add(target, source);
        save();
        render(target);
    }

    private void save() {
        LiveSourceManager.saveUserSources(sources);
    }

    private void confirmDelete(LiveSourceManager.Source source, int index) {
        new AlertDialog.Builder(new androidx.appcompat.view.ContextThemeWrapper(this, R.style.DarkAlertDialog))
                .setTitle("删除直播源？")
                .setMessage("删除后该源的频道将不会在下一次刷新中出现，当前正在播放的频道不会中断。")
                .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> {
                    sources.remove(index);
                    save();
                    render(index);
                }).show();
    }

    private void edit(LiveSourceManager.Source old, int index) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText name = new EditText(this);
        name.setHint("名称（可选）");
        EditText content = new EditText(this);
        content.setHint("远程 URL、JSON 或 M3U/M3U8 内容");
        content.setMinLines(6);
        content.setGravity(Gravity.TOP);
        content.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (old != null) {
            name.setText(old.name);
            content.setText(old.content);
        }
        box.addView(name);
        box.addView(content);
        new AlertDialog.Builder(new androidx.appcompat.view.ContextThemeWrapper(this, R.style.DarkAlertDialog))
                .setTitle(old == null ? "添加直播源" : "编辑直播源").setView(box)
                .setNegativeButton("取消", null).setPositiveButton("保存", (d, w) -> {
                    String value = content.getText().toString().trim();
                    if (value.isEmpty()) return;
                    for (int i = 0; i < sources.size(); i++) {
                        if (i != index && LiveSourceManager.key(sources.get(i).content).equals(LiveSourceManager.key(value))) {
                            new AlertDialog.Builder(new androidx.appcompat.view.ContextThemeWrapper(this, R.style.DarkAlertDialog))
                                    .setMessage("已有相同内容或地址的直播源")
                                    .setPositiveButton("确定", null).show();
                            return;
                        }
                    }
                    if (old == null) {
                        sources.add(LiveSourceManager.create(name.getText().toString(), value, true));
                    } else {
                        old.name = name.getText().toString().trim();
                        old.content = value;
                    }
                    save();
                    render(old == null ? Integer.MAX_VALUE : index);
                }).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
