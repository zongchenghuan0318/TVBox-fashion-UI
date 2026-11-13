package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.studio.osc.R;

import java.util.ArrayList;

/**
 * 搜索源管理列表项：独立勾选框、源名称、类型标签（直连/爬虫/JS/PY）、优先级序号徽标。
 */
public class SearchSiteAdapter extends BaseQuickAdapter<SearchSiteAdapter.Row, BaseViewHolder> {

    public static final int TYPE_SITE = 2;

    public static class Row {
        public final int type;
        public final String key;
        public final String name;
        public final String kindTag;
        public final boolean directHttp;
        public boolean checked;
        /** 勾选顺序序号（1 起），未勾选为 0 */
        public int rank;

        public Row(int type, String key, String name, String kindTag, boolean directHttp, boolean checked) {
            this.type = type;
            this.key = key;
            this.name = name;
            this.kindTag = kindTag;
            this.directHttp = directHttp;
            this.checked = checked;
        }
    }

    public interface OnRowAction {
        void onRow(Row row);
    }

    private OnRowAction onRowAction = null;

    public SearchSiteAdapter() {
        super(R.layout.item_search_site, new ArrayList<>());
    }

    public void setOnRowAction(OnRowAction action) {
        this.onRowAction = action;
    }

    @Override
    protected void convert(BaseViewHolder helper, final Row item) {
        TextView checkView = helper.getView(R.id.tvSiteCheck);
        TextView nameView = helper.getView(R.id.tvSiteName);
        TextView typeView = helper.getView(R.id.tvSiteType);
        TextView rankView = helper.getView(R.id.tvSiteRank);

        checkView.setText(item.checked ? "✓" : "");
        checkView.setTextColor(ContextCompat.getColor(checkView.getContext(),
                item.checked ? R.color.settings_success : R.color.settings_text_muted));

        nameView.setText(item.name);
        nameView.setAlpha(item.checked ? 1f : 0.6f);

        typeView.setText(item.kindTag);
        typeView.setTextColor(ContextCompat.getColor(typeView.getContext(),
                item.directHttp ? R.color.settings_success : R.color.settings_text_muted));

        if (item.checked && item.rank > 0) {
            rankView.setVisibility(View.VISIBLE);
            rankView.setText("#" + item.rank);
        } else {
            rankView.setVisibility(View.GONE);
        }

        helper.itemView.setSelected(item.checked);
        helper.itemView.setContentDescription(item.name + "，" + item.kindTag
                + (item.checked ? "，已勾选，排序第 " + item.rank : "，未勾选"));
        helper.itemView.setOnClickListener(v -> {
            if (onRowAction != null) {
                onRowAction.onRow(item);
            }
        });
    }
}
