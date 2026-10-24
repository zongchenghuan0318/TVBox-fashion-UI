package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.ui.adapter.GridFilterKVAdapter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.studio.osc.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.NonNull;

public class GridFilterDialog extends BaseDialog {
    private LinearLayout filterRoot;
    private MovieSort.SortData currentSortData;
    private final HashMap<String, String> selectionsBeforeShow = new HashMap<>();
    private TvRecyclerView firstFilterRow;
    private String firstFilterKey;
    private ArrayList<String> firstFilterValues;

    public GridFilterDialog(@NonNull @NotNull Context context) {
        super(context);
        setCanceledOnTouchOutside(false);
        setCancelable(true);
        setContentView(R.layout.dialog_grid_filter);
        filterRoot = findViewById(R.id.filterRoot);
        findViewById(R.id.filterDone).setOnClickListener(v -> dismiss());
        findViewById(R.id.filterReset).setOnClickListener(v -> {
            if (currentSortData != null) currentSortData.filterSelect.clear();
            dismiss();
        });
    }

    public interface Callback {
        void change();
    }

    public void setOnDismiss(Callback callback) {
        setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if (currentSortData != null && !selectionsBeforeShow.equals(currentSortData.filterSelect)) {
                    callback.change();
                }
            }
        });
    }

    public void setData(MovieSort.SortData sortData) {
        currentSortData = sortData;
        firstFilterRow = null;
        filterRoot.removeAllViews();
        ArrayList<MovieSort.SortFilter> filters = sortData.filters;
        for (MovieSort.SortFilter filter : filters) {
            if (filter.values == null || filter.values.isEmpty()) continue;
            View line = LayoutInflater.from(getContext()).inflate(R.layout.item_grid_filter, null);
            ((TextView) line.findViewById(R.id.filterName)).setText(filter.name);
            TvRecyclerView gridView = line.findViewById(R.id.mFilterKv);
            gridView.setHasFixedSize(true);
            gridView.setLayoutManager(new V7LinearLayoutManager(getContext(), 0, false));
            GridFilterKVAdapter filterKVAdapter = new GridFilterKVAdapter();
            gridView.setAdapter(filterKVAdapter);
            String key = filter.key;
            ArrayList<String> values = new ArrayList<>(filter.values.keySet());
            ArrayList<String> keys = new ArrayList<>(filter.values.values());
            filterKVAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                    if (!keys.get(position).equals(sortData.filterSelect.get(key))) {
                        sortData.filterSelect.put(key, keys.get(position));
                        filterKVAdapter.setSelectedLabel(values.get(position));
                    }
                }
            });
            filterKVAdapter.setNewData(values);
            int selected = keys.indexOf(sortData.filterSelect.get(key));
            if (selected < 0 && !values.isEmpty()) selected = 0;
            if (selected >= 0) {
                filterKVAdapter.setSelectedLabel(values.get(selected));
                gridView.scrollToPosition(selected);
            }
            filterRoot.addView(line);
            if (firstFilterRow == null) {
                firstFilterRow = gridView;
                firstFilterKey = key;
                firstFilterValues = keys;
            }
        }
    }

    public void show() {
        selectionsBeforeShow.clear();
        if (currentSortData != null) {
            selectionsBeforeShow.putAll(currentSortData.filterSelect);
            setData(currentSortData);
        }
        super.show();
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.gravity = Gravity.BOTTOM;
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        View panel = findViewById(R.id.filterPanel);
        ScrollView scroll = findViewById(R.id.filterScroll);
        LinearLayout.LayoutParams scrollParams = (LinearLayout.LayoutParams) scroll.getLayoutParams();
        scrollParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        scrollParams.weight = 0;
        scroll.setLayoutParams(scrollParams);
        int width = getContext().getResources().getDisplayMetrics().widthPixels;
        int maxHeight = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.75f);
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        layoutParams.height = Math.min(panel.getMeasuredHeight(), maxHeight);
        if (panel.getMeasuredHeight() > maxHeight) {
            scrollParams.height = 0;
            scrollParams.weight = 1;
            scroll.setLayoutParams(scrollParams);
        }
        layoutParams.dimAmount = 0f;
        getWindow().getDecorView().setPadding(0, 0, 0, 0);
        getWindow().setAttributes(layoutParams);
        if (firstFilterRow != null) {
            int selected = firstFilterValues.indexOf(currentSortData.filterSelect.get(firstFilterKey));
            int position = Math.max(0, selected);
            firstFilterRow.scrollToPosition(position);
            firstFilterRow.post(() -> focusSelectedOption(position, 0));
        } else {
            findViewById(R.id.filterDone).requestFocus();
        }
    }

    private void focusSelectedOption(int position, int retry) {
        if (!isShowing() || firstFilterRow == null) return;
        if (firstFilterRow.findViewHolderForAdapterPosition(position) != null || retry >= 8) {
            firstFilterRow.setSelection(position);
            firstFilterRow.requestFocus();
        } else {
            firstFilterRow.postDelayed(() -> focusSelectedOption(position, retry + 1), 50L);
        }
    }
}
