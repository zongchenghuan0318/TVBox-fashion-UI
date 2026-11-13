package com.github.tvbox.osc.ui.activity;

import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.ui.dialog.BaseDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
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

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_source_manager;
    }

    @Override
    protected void init() {
        list = findViewById(R.id.liveSourceList);
        actions = findViewById(R.id.liveSourceActions);
        toggle = findViewById(R.id.liveSourceToggle);
        edit = findViewById(R.id.liveSourceEdit);
        sort = findViewById(R.id.liveSourceSort);
        delete = findViewById(R.id.liveSourceDelete);
        hint = findViewById(R.id.liveSourceHint);
        sources = new ArrayList<>(LiveSourceManager.getSources());
        if (!sources.isEmpty()) {
            selected = sources.get(Math.max(0, Math.min(Hawk.get(LAST_SELECTED, 0), sources.size() - 1)));
        }
        findViewById(R.id.liveSourceBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.liveSourceAdd).setOnClickListener(v -> edit(null));
        toggle.setOnClickListener(v -> {
            if (originalOrder != null) move(-1);
            else if (selected != null) {
                selected.enabled = !selected.enabled;
                save();
                updatePanel();
            }
        });
        edit.setOnClickListener(v -> {
            if (originalOrder != null) move(1);
            else if (selected != null) edit(selected);
        });
        sort.setOnClickListener(v -> {
            if (originalOrder != null) finishSorting(true);
            else if (selected != null) {
                originalOrder = new ArrayList<>(sources);
                updatePanel();
                focusSelected();
            }
        });
        delete.setOnClickListener(v -> {
            if (originalOrder != null) finishSorting(false);
            else if (selected != null) confirmDelete();
        });
        renderRows();
        focusSelected();
    }

    private void renderRows() {
        list.removeAllViews();
        if (sources.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有直播源，选择右上角「+ 添加直播源」开始配置");
            empty.setTextColor(getResources().getColor(R.color.settings_text_secondary));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_MM, 16);
            empty.setPadding(mm(16), mm(24), mm(16), mm(24));
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
            row.setMinHeight(mm(56));
            row.setTextSize(TypedValue.COMPLEX_UNIT_MM, 17);
            row.setMaxLines(2);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setTextColor(getResources().getColor(R.color.color_FFFFFF));
            row.setPadding(mm(16), mm(8), mm(16), mm(8));
            row.setBackgroundResource(R.drawable.selector_live_source_row);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = mm(6);
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
            text.setSpan(new RelativeSizeSpan(0.76f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(source.enabled ? 0xA8FFFFFF : 0x73FFFFFF),
                    start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        ((TextView) findViewById(R.id.liveSourceAddress)).setText(selected == null ? "暂无地址"
                : selected.content.trim().startsWith("http") ? selected.content.trim() : sourceSummary(selected));
        toggle.setText(moving ? "上移" : selected != null && selected.enabled ? "停用此源" : "启用此源");
        edit.setText(moving ? "下移" : "编辑");
        sort.setText(moving ? "完成排序" : "调整顺序");
        delete.setText(moving ? "取消排序" : "删除");
        delete.setTextColor(getResources().getColor(moving ? R.color.color_FFFFFF : R.color.settings_danger));
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
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

    @Override
    public void onBackPressed() {
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

    private void save() {
        LiveSourceManager.saveUserSources(sources);
    }

    private void confirmDelete() {
        final LiveSourceManager.Source source = selected;
        TipDialog.confirm(this, "删除直播源",
                "确定要删除“" + LiveSourceManager.displayName(source, sources.indexOf(source)) + "”吗？\n删除后下次刷新不再加载，当前正在播放的频道不会中断。",
                "删除", () -> {
                    int index = sources.indexOf(source);
                    if (index >= 0) {
                        sources.remove(source);
                        selected = sources.isEmpty() ? null : sources.get(Math.min(index, sources.size() - 1));
                        save();
                        renderRows();
                        focusSelected();
                    }
                });
    }

    private void edit(LiveSourceManager.Source source) {
        BaseDialog dialog = new BaseDialog(this);
        dialog.setContentView(R.layout.dialog_live_source_edit);
        TextView titleView = dialog.findViewById(R.id.tvLiveEditTitle);
        EditText nameInput = dialog.findViewById(R.id.edLiveSourceName);
        EditText contentInput = dialog.findViewById(R.id.edLiveSourceContent);
        TextView errorView = dialog.findViewById(R.id.tvLiveEditError);

        titleView.setText(source == null ? "添加直播源" : "编辑直播源");
        if (source != null) {
            nameInput.setText(source.name);
            contentInput.setText(source.content);
        }

        dialog.findViewById(R.id.btnLiveEditCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnLiveEditSave).setOnClickListener(v -> {
            String value = contentInput.getText().toString().trim();
            if (value.isEmpty()) {
                errorView.setText("请输入直播源地址或内容");
                errorView.setVisibility(View.VISIBLE);
                contentInput.requestFocus();
                return;
            }
            for (LiveSourceManager.Source existing : sources) {
                if (existing != source && LiveSourceManager.key(existing.content).equals(LiveSourceManager.key(value))) {
                    errorView.setText("已有相同地址或内容的直播源");
                    errorView.setVisibility(View.VISIBLE);
                    contentInput.requestFocus();
                    return;
                }
            }
            if (source == null) {
                selected = LiveSourceManager.create(nameInput.getText().toString(), value, true);
                sources.add(selected);
            } else {
                source.name = nameInput.getText().toString().trim();
                source.content = value;
                selected = source;
            }
            save();
            renderRows();
            dialog.dismiss();
        });
        dialog.setOnDismissListener(d -> focusSelected());
        dialog.show();
    }

    @Override
    protected void onPause() {
        List<LiveSourceManager.Source> saved = originalOrder == null ? sources : originalOrder;
        if (selected != null) Hawk.put(LAST_SELECTED, saved.indexOf(selected));
        super.onPause();
    }

    private int mm(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, value, getResources().getDisplayMetrics()));
    }
}
