package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.ui.adapter.SearchSiteAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.util.DefaultConfig;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.studio.osc.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索源管理：顶部固定快捷操作栏（含一键精选高速源） + 下方纯站点列表 + 实时计数与优先级序号。
 */
public class SearchSiteDialog extends BaseDialog {

    public interface OnChanged {
        void onChanged();
    }

    private final List<SearchSiteAdapter.Row> rows = new ArrayList<>();
    private final List<String> order = new ArrayList<>();
    private SearchSiteAdapter adapter;
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
        order.clear();
        order.addAll(ApiConfig.getInstance().getSearchSiteOrder());
        for (SiteBean bean : ApiConfig.getInstance().getSiteBeanList()) {
            if (!bean.isSearchable()) continue;
            boolean directHttp = bean.getType() == 0 || bean.getType() == 1;
            String kindTag = resolveKindTag(bean);
            rows.add(new SearchSiteAdapter.Row(SearchSiteAdapter.TYPE_SITE, bean.getKey(),
                    bean.getName(), kindTag, directHttp,
                    ApiConfig.getInstance().isSearchSiteEnabled(bean.getKey())));
        }
        fillMissingChecked();
        refreshRank();
        refreshSubtitle();

        findViewById(R.id.btnFastPreset).setOnClickListener(v -> applyFastPreset());
        findViewById(R.id.btnSelectAll).setOnClickListener(v -> setAllChecked(true));
        findViewById(R.id.btnUnselectAll).setOnClickListener(v -> setAllChecked(false));
        findViewById(R.id.btnPinFirst).setOnClickListener(v -> pickFirst());
        findViewById(R.id.btnSearchSiteDone).setOnClickListener(v -> dismiss());

        SearchSiteAdapter.OnRowAction action = row -> {
            if (row == null) return;
            row.checked = !row.checked;
            if (row.checked) {
                order.remove(row.key);
                order.add(row.key);
            } else {
                order.remove(row.key);
            }
            refreshRank();
            refreshSubtitle();
            adapter.notifyDataSetChanged();
            save();
        };

        adapter = new SearchSiteAdapter();
        adapter.setOnRowAction(action);
        adapter.setNewData(rows);

        TvRecyclerView list = findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setOnItemListener(new TvRecyclerView.OnItemListener() {
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
        list.setSelectedPosition(0);
        list.post(() -> {
            if (!rows.isEmpty()) {
                list.requestFocus();
            } else {
                findViewById(R.id.btnSearchSiteDone).requestFocus();
            }
        });
        save();
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

        // 1. 优先保留默认高优关键词命中源（文采 / 非凡等）
        for (String kw : DefaultConfig.SEARCH_SITE_PRIORITY) {
            for (SearchSiteAdapter.Row row : rows) {
                if (!row.checked && row.name != null && row.name.contains(kw)) {
                    row.checked = true;
                    order.add(row.key);
                    selected++;
                }
            }
        }
        // 2. 补充全部 HTTP 直连采集源
        for (SearchSiteAdapter.Row row : rows) {
            if (selected >= targetCap) break;
            if (!row.checked && row.directHttp) {
                row.checked = true;
                order.add(row.key);
                selected++;
            }
        }
        // 3. 若配置里直连源很少，再从头补足到 8 个，避免可用源过少
        for (SearchSiteAdapter.Row row : rows) {
            if (selected >= 8) break;
            if (!row.checked) {
                row.checked = true;
                order.add(row.key);
                selected++;
            }
        }

        refreshRank();
        refreshSubtitle();
        adapter.notifyDataSetChanged();
        save();
        Toast.makeText(getContext(), "已精选 " + selected + " 个高速常用源（点「全选」可恢复）", Toast.LENGTH_SHORT).show();
    }

    private void refreshSubtitle() {
        if (subtitleView == null) return;
        int checked = 0;
        for (SearchSiteAdapter.Row row : rows) {
            if (row.checked) checked++;
        }
        subtitleView.setText("已选 " + checked + " / " + rows.size() + " 个源 · 勾选顺序即搜索展示优先级");
    }

    /** 让某个源排到搜索顺序的第一位（并自动勾选） */
    private void pickFirst() {
        if (rows.isEmpty()) return;
        SelectDialog.pick(getContext(), "选择排在第一位的搜索源",
                "选中后会自动勾选并排至最前", rows, -1,
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
        refreshRank();
        refreshSubtitle();
        adapter.notifyDataSetChanged();
        save();
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
        refreshRank();
        refreshSubtitle();
        adapter.notifyDataSetChanged();
        save();
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
