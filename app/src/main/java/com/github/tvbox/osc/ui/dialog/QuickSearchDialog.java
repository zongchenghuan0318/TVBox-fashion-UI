package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.activity.UiHelper;
import com.github.tvbox.osc.ui.adapter.QuickSearchAdapter;
import com.github.tvbox.osc.ui.adapter.SearchWordAdapter;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.PhoneParams;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.studio.osc.R;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuickSearchDialog extends BaseDialog {
    private SearchWordAdapter mWordAdapter;
    private QuickSearchAdapter mSearchAdapter;
    private TvRecyclerView mRv;
    private TvRecyclerView mWordRV;
    private String mSearchWd;
    private Activity mAct;

    public QuickSearchDialog(@NotNull Activity activity, String wd) {
        super(activity, R.style.CustomDialogStyleDim);
        mAct = activity;
        setCanceledOnTouchOutside(false);
        setCancelable(true);
        setContentView(R.layout.dialog_quick_search);
        mSearchWd = wd;
        init(activity);
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        Window dialogWindow = getWindow();
        if (dialogWindow != null) {
            WindowManager.LayoutParams lp = dialogWindow.getAttributes();
            lp.width = (int) (PhoneParams.getScreenWidth(mAct) * 0.7); // 宽度设置为屏幕的0.9
            lp.height = (int) (PhoneParams.getScreenHeight(mAct) * 0.9); // 宽度设置为屏幕的0.9
            dialogWindow.setAttributes(lp);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_QUICK_SEARCH) {
            if (event.obj != null) {
                List<Movie.Video> data = (List<Movie.Video>) event.obj;
                List<Movie.Video> videos = filterVideoList(data, mSearchWd);
                mSearchAdapter.addData(videos);
                sortBySourceOrder();
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD) {
            if (event.obj != null) {
                List<String> words = (List<String>) event.obj;
                mWordAdapter.setNewData(words);
            }
        }
    }

    /** 结果按「搜索源管理」的勾选顺序排列；顺序没变就不动列表，避免打断用户操作 */
    private void sortBySourceOrder() {
        List<Movie.Video> old = mSearchAdapter.getData();
        List<Movie.Video> merged = new ArrayList<>(old);
        Collections.sort(merged, (a, b) -> ApiConfig.getInstance().getSearchSiteOrderIndex(a.sourceKey)
                - ApiConfig.getInstance().getSearchSiteOrderIndex(b.sourceKey));
        for (int i = 0; i < old.size(); i++) {
            if (old.get(i) != merged.get(i)) {
                mSearchAdapter.setNewData(merged);
                return;
            }
        }
    }

    public static List<Movie.Video> filterVideoList(List<Movie.Video> videoList, String searchWord) {
        List<Movie.Video> videos = new ArrayList<>();
        for (Movie.Video video : videoList) {
            if (!NullUtil.isNull(video.name) && video.name.contains(searchWord)) {
                if (UiHelper.canPlay(video) && UiHelper.canPlayName(video)) {
                    videos.add(video);
                }
            }
        }
        return videos;
    }

    private void init(Context context) {
        EventBus.getDefault().register(this);
        setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                EventBus.getDefault().unregister(QuickSearchDialog.this);
            }
        });
        mRv = findViewById(R.id.sortRV);
        mSearchAdapter = new QuickSearchAdapter();
        mRv.setHasFixedSize(true);
        mRv.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));
        mRv.setAdapter(mSearchAdapter);
        mSearchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                Movie.Video video = mSearchAdapter.getData().get(position);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_SELECT, video));
                dismiss();
            }
        });
        mSearchAdapter.setNewData(new ArrayList<>());

        //切换关键字搜索
        mWordAdapter = new SearchWordAdapter();
        mWordRV = findViewById(R.id.mGridViewWord);
        mWordRV.setAdapter(mWordAdapter);
        mWordRV.setLayoutManager(new V7LinearLayoutManager(context, 0, false));
        mWordAdapter.setOnItemClickListener((adapter, view, position) -> {
            mSearchAdapter.getData().clear();
            mSearchAdapter.notifyDataSetChanged();
            mSearchWd = mWordAdapter.getData().get(position);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD_CHANGE, mSearchWd));
        });
        mWordAdapter.setNewData(new ArrayList<>());

    }
}