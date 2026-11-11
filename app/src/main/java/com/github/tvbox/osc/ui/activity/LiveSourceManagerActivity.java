package com.github.tvbox.osc.ui.activity;

import android.app.AlertDialog;
import android.net.Uri;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.util.LiveSourceManager;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;

import java.util.ArrayList;
import java.util.List;

/** 直播源管理：左侧选择源，右侧执行操作，排序确认后才保存。 */
public class LiveSourceManagerActivity extends BaseActivity {
    private static final String LAST_SELECTED = "last_live_source_index";
    private LinearLayout list;
    private View actions;
    private TextView toggle, edit, sort, delete, hint;
    private ArrayList<LiveSourceManager.Source> sources;
    private LiveSourceManager.Source selected;
    private ArrayList<LiveSourceManager.Source> originalOrder;

    @Override protected int getLayoutResID() { return R.layout.activity_live_source_manager; }

    @Override protected void init() {
        list = findViewById(R.id.liveSourceList);
        actions = findViewById(R.id.liveSourceActions);
        toggle = findViewById(R.id.liveSourceToggle);
        edit = findViewById(R.id.liveSourceEdit);
        sort = findViewById(R.id.liveSourceSort);
        delete = findViewById(R.id.liveSourceDelete);
        hint = findViewById(R.id.liveSourceHint);
        sources = new ArrayList<>(LiveSourceManager.getSources());
        if (!sources.isEmpty()) selected = sources.get(Math.max(0,
                Math.min(Hawk.get(LAST_SELECTED, 0), sources.size() - 1)));
        findViewById(R.id.liveSourceBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.liveSourceAdd).setOnClickListener(v -> edit(null));
        toggle.setOnClickListener(v -> {
            if (originalOrder != null) move(-1);
            else {
                selected.enabled = !selected.enabled;
                save();
                updatePanel();
            }
        });
        edit.setOnClickListener(v -> {
            if (originalOrder != null) move(1);
            else edit(selected);
        });
        sort.setOnClickListener(v -> {
            if (originalOrder != null) finishSorting(true);
            else {
                originalOrder = new ArrayList<>(sources);
                updatePanel();
                focusSelected();
            }
        });
        delete.setOnClickListener(v -> {
            if (originalOrder != null) finishSorting(false);
            else confirmDelete();
        });
        renderRows();
        focusSelected();
    }

    private void renderRows() {
        list.removeAllViews();
        if (sources.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有直播源，选择右上角“添加直播源”开始配置");
            empty.setTextColor(getResources().getColor(R.color.settings_text_secondary));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_MM, 14);
            empty.setPadding(mm(12), mm(20), mm(12), mm(20));
            list.addView(empty);
        }
        for (LiveSourceManager.Source source : sources) {
            TextView row = new TextView(this);
            row.setId(View.generateViewId());
            row.setTag(source);
            row.setFocusable(true);
            row.setFocusableInTouchMode(true);
            row.setClickable(true);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(mm(52));
            row.setTextSize(TypedValue.COMPLEX_UNIT_MM, 16);
            row.setMaxLines(2);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setTextColor(getResources().getColor(R.color.color_FFFFFF));
            row.setPadding(mm(14), mm(6), mm(14), mm(6));
            row.setBackgroundResource(R.drawable.selector_live_source_row);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = mm(4);
            list.addView(row, params);
            row.setOnFocusChangeListener((v, focused) -> {
                if (focused && originalOrder == null) {
                    selected = source;
                    updatePanel();
                }
            });
            row.setOnClickListener(v -> {
                if (originalOrder != null) return;
                selected = source;
                updatePanel();
                focusActions();
            });
        }
        updatePanel();
    }

    private void updatePanel() {
        int index = sources.indexOf(selected);
        boolean moving = originalOrder != null;
        for (int i = 0; i < sources.size(); i++) {
            LiveSourceManager.Source source = sources.get(i);
            TextView row = (TextView) list.getChildAt(i);
            String name = LiveSourceManager.displayName(source, i);
            SpannableStringBuilder text = new SpannableStringBuilder(name);
            text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int start = text.length();
            text.append("\n").append(source.enabled ? "已启用" : "已停用").append(" · ").append(sourceSummary(source));
            text.setSpan(new RelativeSizeSpan(0.75f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(0xFFB0B0B0), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            row.setText(text);
            row.setSelected(source == selected);
            row.setContentDescription(name + (source.enabled ? "，已启用" : "，已停用")
                    + (moving && source == selected ? "，正在调整顺序" : "，右键或确定进入操作"));
            row.setNextFocusUpId(i == 0 ? R.id.liveSourceAdd : list.getChildAt(i - 1).getId());
            row.setNextFocusDownId(i == sources.size() - 1 ? row.getId() : list.getChildAt(i + 1).getId());
            row.setNextFocusLeftId(row.getId());
            row.setNextFocusRightId(R.id.liveSourceToggle);
        }
        ((TextView) findViewById(R.id.liveSourceName)).setText(selected == null
                ? "还没有直播源" : LiveSourceManager.displayName(selected, index));
        ((TextView) findViewById(R.id.liveSourceState)).setText(selected == null ? "添加一个直播源开始配置"
                : (moving ? "排序中" : selected.enabled ? "已启用" : "已停用") + " · 第 " + (index + 1) + " / " + sources.size() + " 项");
        ((TextView) findViewById(R.id.liveSourceAddress)).setText(selected == null ? ""
                : selected.content.trim().startsWith("http") ? selected.content.trim() : sourceSummary(selected));
        toggle.setText(moving ? "上移" : selected != null && selected.enabled ? "停用此源" : "启用此源");
        edit.setText(moving ? "下移" : "编辑");
        sort.setText(moving ? "完成排序" : "调整顺序");
        delete.setText(moving ? "取消排序" : "删除");
        toggle.setEnabled(selected != null && (!moving || index > 0));
        edit.setEnabled(selected != null && (!moving || index < sources.size() - 1));
        sort.setEnabled(selected != null && (moving || sources.size() > 1));
        delete.setEnabled(selected != null);
        findViewById(R.id.liveSourceAdd).setEnabled(!moving);
        hint.setText(moving ? "上下移动当前源 · 确定保存 · 返回取消"
                : "上下选源 · 右键或确定进入操作 · 左键返回列表");
        View row = selectedRow();
        int returnId = row == null ? R.id.liveSourceAdd : row.getId();
        findViewById(R.id.liveSourceBack).setNextFocusDownId(returnId);
        findViewById(R.id.liveSourceAdd).setNextFocusDownId(returnId);
        TextView[] buttons = {toggle, edit, sort, delete};
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setNextFocusLeftId(returnId);
            buttons[i].setNextFocusRightId(buttons[i].getId());
            buttons[i].setNextFocusUpId(i == 0 ? R.id.liveSourceAdd : buttons[i - 1].getId());
            buttons[i].setNextFocusDownId(i < buttons.length - 1 ? buttons[i + 1].getId() : buttons[i].getId());
        }
    }

    private String sourceSummary(LiveSourceManager.Source source) {
        String value = source.content.trim();
        if (value.startsWith("{") || value.startsWith("[")) return "JSON 内容";
        if (value.startsWith("#EXTM3U")) return "M3U 内容";
        Uri uri = Uri.parse(value.startsWith("http") ? value : "http://" + value);
        return uri.getHost() == null ? "远程地址" : uri.getHost();
    }

    private View selectedRow() {
        int index = sources.indexOf(selected);
        return index < 0 ? null : list.getChildAt(index);
    }

    private void focusSelected() {
        list.post(() -> {
            View row = selectedRow();
            if (row == null) findViewById(R.id.liveSourceAdd).requestFocus();
            else row.requestFocus();
        });
    }

    private void focusActions() {
        for (TextView button : new TextView[]{toggle, edit, sort, delete}) {
            if (button.isEnabled()) {
                button.requestFocus();
                return;
            }
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int key = event.getKeyCode();
            if (list.hasFocus() && selected != null) {
                if (originalOrder != null) {
                    if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                        move(key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                        return true;
                    }
                    if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                        finishSorting(true);
                        return true;
                    }
                }
                if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    focusActions();
                    return true;
                }
            }
            if (actions.hasFocus() && key == KeyEvent.KEYCODE_DPAD_LEFT) {
                focusSelected();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (originalOrder != null) finishSorting(false);
        else if (actions.hasFocus()) focusSelected();
        else super.onBackPressed();
    }

    static int moveSource(List<LiveSourceManager.Source> sources, int index, int offset) {
        int target = index + offset;
        if (target < 0 || target >= sources.size()) return index;
        sources.add(target, sources.remove(index));
        return target;
    }

    private void move(int offset) {
        int index = sources.indexOf(selected);
        int target = moveSource(sources, index, offset);
        if (target == index) return;
        View row = list.getChildAt(index);
        list.removeView(row);
        list.addView(row, target);
        updatePanel();
        focusSelected();
    }

    private void finishSorting(boolean commit) {
        if (commit) save();
        else {
            sources = originalOrder;
            renderRows();
        }
        originalOrder = null;
        updatePanel();
        focusSelected();
    }

    private void save() { LiveSourceManager.saveUserSources(sources); }

    private void confirmDelete() {
        LiveSourceManager.Source source = selected;
        AlertDialog dialog = new AlertDialog.Builder(new androidx.appcompat.view.ContextThemeWrapper(this, R.style.DarkAlertDialog))
                .setTitle("删除“" + LiveSourceManager.displayName(source, sources.indexOf(source)) + "”？")
                .setMessage("删除后该源的频道将不会在下一次刷新中出现，当前正在播放的频道不会中断。")
                .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> {
                    int index = sources.indexOf(source);
                    sources.remove(source);
                    selected = sources.isEmpty() ? null : sources.get(Math.min(index, sources.size() - 1));
                    save();
                    renderRows();
                }).create();
        dialog.setOnDismissListener(d -> focusSelected());
        dialog.show();
    }

    private void edit(LiveSourceManager.Source source) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(mm(20), 0, mm(20), 0);
        EditText name = new EditText(this);
        name.setHint("名称（可选）");
        name.setSingleLine(true);
        EditText content = new EditText(this);
        content.setHint("远程 URL、JSON 或 M3U 内容");
        content.setMinLines(3);
        content.setMaxLines(5);
        content.setGravity(Gravity.TOP);
        content.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
        if (source != null) {
            name.setText(source.name);
            content.setText(source.content);
        }
        box.addView(name);
        box.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(new androidx.appcompat.view.ContextThemeWrapper(this, R.style.DarkAlertDialog))
                .setTitle(source == null ? "添加直播源" : "编辑直播源").setView(box)
                .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnDismissListener(d -> focusSelected());
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = content.getText().toString().trim();
            if (value.isEmpty()) {
                content.setError("请输入直播源地址或内容");
                content.requestFocus();
                return;
            }
            for (LiveSourceManager.Source existing : sources) {
                if (existing != source && LiveSourceManager.key(existing.content).equals(LiveSourceManager.key(value))) {
                    content.setError("已有相同地址或内容的直播源");
                    content.requestFocus();
                    return;
                }
            }
            if (source == null) {
                selected = LiveSourceManager.create(name.getText().toString(), value, true);
                sources.add(selected);
            } else {
                source.name = name.getText().toString().trim();
                source.content = value;
                selected = source;
            }
            save();
            renderRows();
            dialog.dismiss();
        }));
        dialog.show();
    }

    @Override protected void onPause() {
        List<LiveSourceManager.Source> saved = originalOrder == null ? sources : originalOrder;
        if (selected != null) Hawk.put(LAST_SELECTED, saved.indexOf(selected));
        super.onPause();
    }

    private int mm(int value) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, value, getResources().getDisplayMetrics())); }
}
