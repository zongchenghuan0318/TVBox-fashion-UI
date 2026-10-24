package com.github.tvbox.osc.ui.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.util.LiveSourceManager;
import com.studio.osc.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LiveSourceManagerActivity extends BaseActivity {
    private LinearLayout list;
    private ArrayList<LiveSourceManager.Source> sources;

    @Override protected int getLayoutResID() { return R.layout.activity_live_source_manager; }

    @Override protected void init() {
        list = findViewById(R.id.liveSourceList);
        findViewById(R.id.liveSourceBack).setOnClickListener(v -> finish());
        findViewById(R.id.liveSourceAdd).setOnClickListener(v -> edit(null, -1));
        render();
    }

    private void render() {
        sources = new ArrayList<>(LiveSourceManager.getSources());
        list.removeAllViews();
        addHeader("我的直播源");
        if (sources.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有自定义直播源\n点击右上角“添加直播源”开始配置");
            empty.setTextColor(Color.rgb(154, 168, 189));
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(20, 48, 20, 48);
            list.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        } else {
            for (int i = 0; i < sources.size(); i++) addRow(sources.get(i), i, false);
        }
    }

    private void addHeader(String text) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextColor(Color.WHITE); view.setTextSize(18);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(12, 10, 12, 10);
        list.addView(view, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void addRow(LiveSourceManager.Source source, int index, boolean builtin) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setFocusable(true);
        row.setBackgroundResource(R.drawable.bg_live_source_card);
        row.setPadding(dp(20), dp(14), dp(20), dp(14));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(0, 0, 0, dp(12));
        list.addView(row, rowParams);
        TextView title = new TextView(this);
        title.setText(LiveSourceManager.displayName(source, Math.max(index, 0))
                + (builtin ? "（内置）" : ""));
        title.setTextColor(Color.WHITE); title.setTextSize(18); title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView detail = new TextView(this);
        detail.setText(source.content.length() > 80 ? source.content.substring(0, 80) + "…" : source.content);
        detail.setTextColor(Color.rgb(160, 174, 196)); detail.setTextSize(13); detail.setMaxLines(2);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.setMargins(0, dp(5), 0, dp(10));
        row.addView(title); row.addView(detail);
        detail.setLayoutParams(detailParams);
        if (!builtin) {
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            Switch enabled = new Switch(this); enabled.setText("启用"); enabled.setTextColor(Color.WHITE);
            enabled.setChecked(source.enabled);
            enabled.setMinHeight(dp(48));
            enabled.setOnClickListener(v -> { source.enabled = enabled.isChecked(); save(); });
            TextView up = action("上移"); up.setOnClickListener(v -> move(index, -1));
            TextView down = action("下移"); down.setOnClickListener(v -> move(index, 1));
            TextView edit = action("编辑"); edit.setOnClickListener(v -> edit(source, index));
            TextView delete = action("删除"); delete.setOnClickListener(v -> confirmDelete(source, index));
            actions.addView(enabled); actions.addView(up); actions.addView(down);
            actions.addView(edit); actions.addView(delete); row.addView(actions);
        }
    }

    private TextView action(String text) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextColor(Color.WHITE); v.setTextSize(14);
        v.setGravity(Gravity.CENTER); v.setFocusable(true); v.setClickable(true);
        v.setMinWidth(dp(64)); v.setMinHeight(dp(44));
        v.setPadding(dp(8), 0, dp(8), 0);
        v.setBackgroundResource(R.drawable.bg_live_source_action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(44));
        params.setMargins(dp(6), 0, 0, 0);
        v.setLayoutParams(params);
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void move(int index, int offset) {
        int target = index + offset; if (target < 0 || target >= sources.size()) return;
        LiveSourceManager.Source source = sources.remove(index); sources.add(target, source); save(); render();
    }

    private void save() { LiveSourceManager.saveUserSources(sources); }

    private void confirmDelete(LiveSourceManager.Source source, int index) {
        new AlertDialog.Builder(this).setTitle("删除直播源？")
                .setMessage("删除后该源的频道将不会在下一次刷新中出现，当前正在播放的频道不会中断。")
                .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> {
                    sources.remove(index); save(); render();
                }).show();
    }

    private void edit(LiveSourceManager.Source old, int index) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        EditText name = new EditText(this); name.setHint("名称（可选）");
        EditText content = new EditText(this); content.setHint("远程 URL、JSON 或 M3U/M3U8 内容");
        content.setMinLines(6); content.setGravity(Gravity.TOP); content.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (old != null) { name.setText(old.name); content.setText(old.content); }
        box.addView(name); box.addView(content);
        new AlertDialog.Builder(this).setTitle(old == null ? "添加直播源" : "编辑直播源").setView(box)
                .setNegativeButton("取消", null).setPositiveButton("保存", (d, w) -> {
                    String value = content.getText().toString().trim();
                    if (value.isEmpty()) return;
                    for (int i = 0; i < sources.size(); i++) if (i != index &&
                            LiveSourceManager.key(sources.get(i).content).equals(LiveSourceManager.key(value))) {
                        new AlertDialog.Builder(this).setMessage("已有相同内容或地址的直播源").setPositiveButton("确定", null).show(); return;
                    }
                    if (old == null) sources.add(LiveSourceManager.create(name.getText().toString(), value, true));
                    else { old.name = name.getText().toString().trim(); old.content = value; }
                    save(); render();
                }).show();
    }
}
