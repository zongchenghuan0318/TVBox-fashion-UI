package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.ui.adapter.SearchSiteAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.util.DefaultConfig;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.studio.osc.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 搜索源管理：顶部固定快捷操作栏（含一键精选高速源） + 下方按优先级展示的站点列表。
 * 自动清理失效配置残留 key 消除序号断层；支持长按调序（置顶/上移/下移），点「取消」放弃改动，点「应用」才生效。
 */
public class SearchSiteDialog extends BaseDialog {

    public interface OnChanged {
        void onChanged();
    }

    private final List<SearchSiteAdapter.Row> rows = new ArrayList<>();
    private final Map<String, Integer> originalIndex = new HashMap<>();
    private final List<String> order = new ArrayList<>();
    private final Set<String> initialDisabled = new HashSet<>();
    private final List<String> initialOrder = new ArrayList<>();
    private SearchSiteAdapter adapter;
    private TvRecyclerView listView;
    private OnChanged onChanged = null;
    private TextView subtitleView;

    public SearchSiteDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_search_site);
    }

    public void setOnChanged(OnChanged listener) {
        this.onChanged = listener;
    }

    public void init() {
        subtitleView = findViewById(R.id.tvSearchSiteSubtitle);
        rows.clear();
        originalIndex.clear();
        order.clear();
        for (SiteBean bean : ApiConfig.getInstance().getOrderedSearchSiteList()) {
            order.add(bean.getKey());
        }

        int idx = 0;
        for (SiteBean bean : ApiConfig.getInstance().getSiteBeanList()) {
            if (!bean.isSearchable()) continue;
            originalIndex.put(bean.getKey(), idx++);
            boolean directHttp = bean.getType() == 0 || bean.getType() == 1;
            String kindTag = resolveKindTag(bean);
            rows.add(new SearchSiteAdapter.Row(SearchSiteAdapter.TYPE_SITE, bean.getKey(),
                    bean.getName(), kindTag, directHttp,
                    ApiConfig.getInstance().isSearchSiteEnabled(bean.getKey())));
        }
        refreshRankAndSortRows();
        captureInitialSnapshot();
        refreshSubtitle();

        findViewById(R.id.btnFastPreset).setOnClickListener(v -> applyFastPreset());
        findViewById(R.id.btnSelectAll).setOnClickListener(v -> setAllChecked(true));
        findViewById(R.id.btnUnselectAll).setOnClickListener(v -> setAllChecked(false));
        findViewById(R.id.btnPinFirst).setOnClickListener(v -> pickFirst());
        View cancelBtn = findViewById(R.id.btnSearchSiteCancel);
        if (cancelBtn != null) {
            cancelBtn.setOnClickListener(v -> dismiss());
        }
        findViewById(R.id.btnSearchSiteDone).setOnClickListener(v -> applyAndDismiss());

        SearchSiteAdapter.OnRowAction action = row -> {
            if (row == null) return;
            row.checked = !row.checked;
            if (row.checked) {
                order.remove(row.key);
                order.add(row.key);
            } else {
                order.remove(row.key);
            }
            refreshRows();
            focusRow(row);
        };

        adapter = new SearchSiteAdapter();
        adapter.setOnRowAction(action);
        adapter.setOnRowLongAction(this::showRowOrderMenu);
        adapter.setNewData(rows);

        listView = findViewById(R.id.list);
        listView.setItemAnimator(null);
        listView.setAdapter(adapter);
        listView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (position >= 0 && position < rows.size()) {
                    action.onRow(rows.get(position));
                }
            }
        });
        listView.setSelectedPosition(0);
        listView.post(() -> {
            if (!rows.isEmpty()) {
                listView.requestFocus();
            } else {
                findViewById(R.id.btnSearchSiteDone).requestFocus();
            }
        });
    }

    private void captureInitialSnapshot() {
        initialDisabled.clear();
        for (SearchSiteAdapter.Row row : rows) {
            if (!row.checked) {
                initialDisabled.add(row.key);
            }
        }
        initialOrder.clear();
        initialOrder.addAll(order);
    }

    private boolean hasPendingChanges() {
        Set<String> currentDisabled = new HashSet<>();
        for (SearchSiteAdapter.Row row : rows) {
            if (!row.checked) {
                currentDisabled.add(row.key);
            }
        }
        return !currentDisabled.equals(initialDisabled) || !order.equals(initialOrder);
    }

    private int countChecked() {
        int checked = 0;
        for (SearchSiteAdapter.Row row : rows) {
            if (row.checked) checked++;
        }
        return checked;
    }

    private void applyAndDismiss() {
        if (!hasPendingChanges()) {
            dismiss();
            return;
        }
        if (countChecked() == 0 && !rows.isEmpty()) {
            Toast.makeText(getContext(), "请至少勾选 1 个搜索源，或点击「取消」放弃修改", Toast.LENGTH_SHORT).show();
            return;
        }
        save();
        dismiss();
    }

    private void refreshRows() {
        refreshRankAndSortRows();
        refreshSubtitle();
        adapter.notifyDataSetChanged();
    }

    private void focusRow(SearchSiteAdapter.Row row) {
        int target = rows.indexOf(row);
        RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
        if (target < 0 || layoutManager == null) return;
        layoutManager.scrollToPosition(target);
        listView.setSelectedPosition(target);
        OneShotPreDrawListener.add(listView, () -> {
            if (!isShowing() || rows.get(target) != row) return;
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(target);
            if (holder != null) {
                holder.itemView.requestFocus();
            }
        });
    }

    private void showRowOrderMenu(SearchSiteAdapter.Row row) {
        if (row == null) return;
        List<String> actions = Arrays.asList("置顶第一位", "上移一位", "下移一位");
        SelectDialog.pick(getContext(), row.name,
                row.checked ? ("当前排第 " + row.rank + " 位，选择调整后的顺序") : "未勾选（调序后将自动启用）",
                actions, -1,
                new SelectDialogAdapter.SelectDialogInterface<String>() {
                    @Override
                    public void click(String value, int pos) {
                        if (pos == 0) {
                            pinFirst(row);
                        } else if (pos == 1) {
                            moveRowOrder(row, -1);
                        } else if (pos == 2) {
                            moveRowOrder(row, 1);
                        }
                    }

                    @Override
                    public String getDisplay(String val) {
                        return val;
                    }
                });
    }

    private void moveRowOrder(SearchSiteAdapter.Row row, int delta) {
        if (row == null) return;
        if (!row.checked) {
            row.checked = true;
            order.remove(row.key);
            order.add(row.key);
        }
        int idx = order.indexOf(row.key);
        if (idx < 0) return;
        int target = Math.max(0, Math.min(order.size() - 1, idx + delta));
        if (target != idx) {
            order.remove(idx);
            order.add(target, row.key);
        }
        refreshRows();
        focusRow(row);
    }

    private String resolveKindTag(SiteBean bean) {
        if (bean.getType() == 0 || bean.getType() == 1) return "直连";
        String api = bean.getApi();
        if (api != null && api.endsWith(".js")) return "JS";
        if (api != null && api.endsWith(".py")) return "PY";
        return "爬虫";
    }

    /**
     * 一键精选高速常用源：保留全部直连源 + 优先级关键词命中的主力源（最多 12 个），
     * 避免大配置四五十个慢源同时排队拉长搜索。
     */
    private void applyFastPreset() {
        if (rows.isEmpty()) return;
        for (SearchSiteAdapter.Row row : rows) {
            row.checked = false;
        }
        order.clear();

        int selected = 0;
        final int targetCap = 12;

        for (String kw : DefaultConfig.SEARCH_SITE_PRIORITY) {
            for (SearchSiteAdapter.Row row : rows) {
                if (!row.checked && row.name != null && row.name.contains(kw)) {
                    row.checked = true;
                    order.add(row.key);
                    selected++;
                }
            }
        }
        for (SearchSiteAdapter.Row row : rows) {
            if (selected >= targetCap) break;
            if (!row.checked && row.directHttp) {
                row.checked = true;
                order.add(row.key);
                selected++;
            }
        }
        for (SearchSiteAdapter.Row row : rows) {
            if (selected >= 8) break;
            if (!row.checked) {
                row.checked = true;
                order.add(row.key);
                selected++;
            }
        }

        refreshRows();
        if (listView != null && !rows.isEmpty()) {
            listView.scrollToPosition(0);
        }
        Toast.makeText(getContext(), "已精选 " + selected + " 个高速常用源，点击「应用」生效", Toast.LENGTH_SHORT).show();
    }

    private void refreshSubtitle() {
        if (subtitleView == null) return;
        int checked = countChecked();
        String suffix = hasPendingChanges() ? " · 有未保存修改" : " · 序号越小越优先展示";
        subtitleView.setText("已选 " + checked + " / " + rows.size() + " 个源" + suffix);
    }

    /** 让某个源排到搜索顺序的第一位（并自动勾选） */
    private void pickFirst() {
        if (rows.isEmpty()) return;
        int currentFirstIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).checked && rows.get(i).rank == 1) {
                currentFirstIndex = i;
                break;
            }
        }
        SelectDialog.pick(getContext(), "选择排在第一位的搜索源",
                "选中后会自动勾选并排至最前", rows, currentFirstIndex,
                new SelectDialogAdapter.SelectDialogInterface<SearchSiteAdapter.Row>() {
                    @Override
                    public void click(SearchSiteAdapter.Row value, int pos) {
                        pinFirst(value);
                    }

                    @Override
                    public String getDisplay(SearchSiteAdapter.Row val) {
                        return val.name;
                    }

                    @Override
                    public String getSubDisplay(SearchSiteAdapter.Row val) {
                        return val.kindTag + (val.checked ? " · 当前第 " + val.rank + " 位" : " · 未勾选（选中后自动启用并置顶）");
                    }
                });
    }

    private void pinFirst(SearchSiteAdapter.Row row) {
        row.checked = true;
        order.remove(row.key);
        order.add(0, row.key);
        refreshRows();
        focusRow(row);
    }

    private void setAllChecked(boolean checked) {
        for (SearchSiteAdapter.Row row : rows) {
            row.checked = checked;
        }
        if (checked) {
            fillMissingChecked();
        } else {
            order.clear();
        }
        refreshRows();
    }

    private void fillMissingChecked() {
        for (String key : ApiConfig.getInstance().getDefaultSearchSiteOrder()) {
            if (order.contains(key) || !isChecked(key)) continue;
            order.add(key);
        }
        for (SearchSiteAdapter.Row row : rows) {
            if (row.checked && !order.contains(row.key)) {
                order.add(row.key);
            }
        }
        // 只保留实际已勾选的 key，保证 #1..#N 连续无断层
        List<String> cleaned = new ArrayList<>();
        for (String key : order) {
            if (isChecked(key) && !cleaned.contains(key)) {
                cleaned.add(key);
            }
        }
        order.clear();
        order.addAll(cleaned);
    }

    private boolean isChecked(String key) {
        for (SearchSiteAdapter.Row row : rows) {
            if (key.equals(row.key)) {
                return row.checked;
            }
        }
        return false;
    }

    private void refreshRank() {
        for (SearchSiteAdapter.Row row : rows) {
            row.rank = row.checked ? order.indexOf(row.key) + 1 : 0;
        }
    }

    private void refreshRankAndSortRows() {
        refreshRank();
        Collections.sort(rows, (a, b) -> {
            if (a.checked != b.checked) {
                return a.checked ? -1 : 1;
            }
            if (a.checked) {
                return Integer.compare(a.rank, b.rank);
            }
            int idxA = originalIndex.containsKey(a.key) ? originalIndex.get(a.key) : Integer.MAX_VALUE;
            int idxB = originalIndex.containsKey(b.key) ? originalIndex.get(b.key) : Integer.MAX_VALUE;
            return Integer.compare(idxA, idxB);
        });
    }

    private void save() {
        ArrayList<String> disabled = new ArrayList<>();
        for (SearchSiteAdapter.Row row : rows) {
            if (!row.checked) {
                disabled.add(row.key);
            }
        }
        ApiConfig.getInstance().setSearchSiteDisabled(disabled);
        ApiConfig.getInstance().setSearchSiteOrder(order);
        if (onChanged != null) {
            onChanged.onChanged();
        }
    }
}
