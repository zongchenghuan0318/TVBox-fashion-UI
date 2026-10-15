package com.github.tvbox.osc.ui.adapter;

import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.studio.osc.R;

import java.util.ArrayList;

/**
 * 搜索源管理列表：全选/全不选与站点行混在同一个列表里，
 * 避免 TV 遥控器场景下多个容器之间的焦点跳转问题。
 */
public class SearchSiteAdapter extends BaseQuickAdapter<SearchSiteAdapter.Row, BaseViewHolder> {

    public static final int TYPE_SELECT_ALL = 0;
    public static final int TYPE_UNSELECT_ALL = 1;
    public static final int TYPE_SITE = 2;

    public static class Row {
        public final int type;
        public final String key;
        public final String name;
        public boolean checked;

        public Row(int type, String key, String name, boolean checked) {
            this.type = type;
            this.key = key;
            this.name = name;
            this.checked = checked;
        }
    }

    public interface OnRowAction {
        void onRow(Row row);
    }

    private OnRowAction onRowAction = null;

    public SearchSiteAdapter() {
        super(R.layout.item_dialog_select, new ArrayList<>());
    }

    public void setOnRowAction(OnRowAction action) {
        this.onRowAction = action;
    }

    @Override
    protected void convert(BaseViewHolder helper, final Row item) {
        String text;
        if (item.type == TYPE_SELECT_ALL) {
            text = "【 全选 】";
        } else if (item.type == TYPE_UNSELECT_ALL) {
            text = "【 全不选 】";
        } else {
            text = (item.checked ? "☑ " : "☐ ") + item.name;
        }
        helper.setText(R.id.tvName, text);
        helper.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onRowAction != null) {
                    onRowAction.onRow(item);
                }
            }
        });
    }
}
