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
 * 勾选结果实时写入 Hawk，全局影响所有聚合搜索入口。
 */
public class SearchSiteDialog extends BaseDialog {

    public interface OnChanged {
        void onChanged();
    }

    private final List<SearchSiteAdapter.Row> rows = new ArrayList<>();
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
        for (SiteBean bean : ApiConfig.getInstance().getSiteBeanList()) {
            // 配置里本身就不可搜索的源，列出来也没有意义
            if (!bean.isSearchable()) continue;
            rows.add(new SearchSiteAdapter.Row(SearchSiteAdapter.TYPE_SITE, bean.getKey(), bean.getName(),
                    ApiConfig.getInstance().isSearchSiteEnabled(bean.getKey())));
        }

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
                    adapter.notifyDataSetChanged();
                    save();
                }
            }
        });
        adapter.setNewData(rows);

        TvRecyclerView list = findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setSelectedPosition(0);
    }

    private void setAllChecked(boolean checked) {
        for (SearchSiteAdapter.Row row : rows) {
            if (row.type == SearchSiteAdapter.TYPE_SITE) {
                row.checked = checked;
            }
        }
        adapter.notifyDataSetChanged();
        save();
    }

    private void save() {
        ArrayList<String> disabled = new ArrayList<>();
        for (SearchSiteAdapter.Row row : rows) {
            if (row.type == SearchSiteAdapter.TYPE_SITE && !row.checked) {
                disabled.add(row.key);
            }
        }
        ApiConfig.getInstance().setSearchSiteDisabled(disabled);
        if (onChanged != null) {
            onChanged.onChanged();
        }
    }
}
