package com.github.tvbox.osc.ui.dialog;

import android.content.Context;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.ui.adapter.SearchSiteAdapter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.studio.osc.R;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

/**
 * 搜索源管理：勾选哪些站点参与聚合搜索（全选/全不选/逐项勾选）。
 * 勾选结果实时写入 Hawk，全局影响所有聚合搜索入口；
 * 勾选的先后顺序同时也是所有搜索界面的搜索与展示顺序。
 */
public class SearchSiteDialog extends BaseDialog {

    public interface OnChanged {
        void onChanged();
    }

    private final List<SearchSiteAdapter.Row> rows = new ArrayList<>();
    /** 勾选顺序表：新勾选的排到最后，取消勾选的移除 */
    private final List<String> order = new ArrayList<>();
    private SearchSiteAdapter adapter;
    private OnChanged onChanged = null;

    public SearchSiteDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_search_site);
    }

    public void setOnChanged(OnChanged listener) {
        this.onChanged = listener;
    }

    public void init() {
        rows.clear();
        rows.add(new SearchSiteAdapter.Row(SearchSiteAdapter.TYPE_SELECT_ALL, null, null, true));
        rows.add(new SearchSiteAdapter.Row(SearchSiteAdapter.TYPE_UNSELECT_ALL, null, null, false));
        order.clear();
        order.addAll(ApiConfig.getInstance().getSearchSiteOrder());
        for (SiteBean bean : ApiConfig.getInstance().getSiteBeanList()) {
            // 配置里本身就不可搜索的源，列出来也没有意义
            if (!bean.isSearchable()) continue;
            rows.add(new SearchSiteAdapter.Row(SearchSiteAdapter.TYPE_SITE, bean.getKey(), bean.getName(),
                    ApiConfig.getInstance().isSearchSiteEnabled(bean.getKey())));
        }
        // 默认启用/配置新增、还没登记过顺序的源按默认优先级补进顺序表：
        // 命中优先关键词的排最前（文采┃秒播 > 非凡┃影视），其余按站点表顺序，
        // 这样列表里的序号与搜索页的展示顺序从一开始就一致
        fillMissingChecked();
        refreshRank();

        adapter = new SearchSiteAdapter();
        adapter.setOnRowAction(new SearchSiteAdapter.OnRowAction() {
            @Override
            public void onRow(SearchSiteAdapter.Row row) {
                if (row.type == SearchSiteAdapter.TYPE_SELECT_ALL) {
                    setAllChecked(true);
                } else if (row.type == SearchSiteAdapter.TYPE_UNSELECT_ALL) {
                    setAllChecked(false);
                } else {
                    row.checked = !row.checked;
                    if (row.checked) {
                        // 勾选顺序即排序顺序：新勾选的排到最后
                        order.remove(row.key);
                        order.add(row.key);
                    } else {
                        order.remove(row.key);
                    }
                    refreshRank();
                    adapter.notifyDataSetChanged();
                    save();
                }
            }
        });
        adapter.setNewData(rows);

        TvRecyclerView list = findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setSelectedPosition(0);
        // 打开即把顺序表与勾选状态对齐写回，保证首次进入后顺序就是确定的
        save();
    }

    private void setAllChecked(boolean checked) {
        for (SearchSiteAdapter.Row row : rows) {
            if (row.type != SearchSiteAdapter.TYPE_SITE) continue;
            row.checked = checked;
        }
        if (checked) {
            // 全选时按默认优先级补顺序：文采│秒播、非凡│影视排最前
            fillMissingChecked();
        } else {
            order.clear();
        }
        refreshRank();
        adapter.notifyDataSetChanged();
        save();
    }

    /**
     * 把已勾选、但还没登记进顺序表的源补上：先按默认优先级关键词命中顺序补
     * （文采│秒播 > 非凡│影视），再按列表顺序兜底，保证不漏源。
     */
    private void fillMissingChecked() {
        for (String key : ApiConfig.getInstance().getDefaultSearchSiteOrder()) {
            if (order.contains(key) || !isChecked(key)) continue;
            order.add(key);
        }
        for (SearchSiteAdapter.Row row : rows) {
            if (row.type == SearchSiteAdapter.TYPE_SITE && row.checked && !order.contains(row.key)) {
                order.add(row.key);
            }
        }
    }

    private boolean isChecked(String key) {
        for (SearchSiteAdapter.Row row : rows) {
            if (row.type == SearchSiteAdapter.TYPE_SITE && key.equals(row.key)) {
                return row.checked;
            }
        }
        return false;
    }

    /** 把勾选顺序写回每行的序号，列表里直接显示「☑ 1. 源名」，让排序顺序可见可预期 */
    private void refreshRank() {
        for (SearchSiteAdapter.Row row : rows) {
            if (row.type != SearchSiteAdapter.TYPE_SITE) continue;
            row.rank = row.checked ? order.indexOf(row.key) + 1 : 0;
        }
    }

    private void save() {
        ArrayList<String> disabled = new ArrayList<>();
        for (SearchSiteAdapter.Row row : rows) {
            if (row.type == SearchSiteAdapter.TYPE_SITE && !row.checked) {
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
