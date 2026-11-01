package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
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
    private View llLoading;
    private View llEmpty;
    private String mSearchWd;
    private Activity mAct;
    private Runnable mOnDialogDismiss;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mSearchAdapter == null || mSearchAdapter.getData().isEmpty()) {
                showEmpty();
            }
        }
    };

    public void setOnDialogDismissListener(Runnable listener) {
        mOnDialogDismiss = listener;
    }

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
            lp.width = (int) (PhoneParams.getScreenWidth(mAct) * 0.7); // 宽度设置为屏幕的0.7
            lp.height = (int) (PhoneParams.getScreenHeight(mAct) * 0.9); // 高度设置为屏幕的0.9
            dialogWindow.setAttributes(lp);
        }
    }

    private void showLoading() {
        mHandler.removeCallbacks(mTimeoutRunnable);
        if (llLoading != null) llLoading.setVisibility(View.VISIBLE);
        if (llEmpty != null) llEmpty.setVisibility(View.GONE);
        mHandler.postDelayed(mTimeoutRunnable, 6000);
    }

    private void showEmpty() {
        if (llLoading != null) llLoading.setVisibility(View.GONE);
        if (llEmpty != null) llEmpty.setVisibility(View.VISIBLE);
    }

    private void showContent() {
        mHandler.removeCallbacks(mTimeoutRunnable);
        if (llLoading != null) llLoading.setVisibility(View.GONE);
        if (llEmpty != null) llEmpty.setVisibility(View.GONE);
    }

    private void selectVideo(int position) {
        if (mSearchAdapter != null && position >= 0 && position < mSearchAdapter.getData().size()) {
            Movie.Video video = mSearchAdapter.getData().get(position);
            if (video != null) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_SELECT, video));
                dismiss();
            }
        }
    }

    private void selectWord(int position) {
        if (mWordAdapter != null && position >= 0 && position < mWordAdapter.getData().size()) {
            String word = mWordAdapter.getData().get(position);
            if (word != null && !word.isEmpty()) {
                mSearchAdapter.getData().clear();
                mSearchAdapter.notifyDataSetChanged();
                mSearchWd = word;
                showLoading();
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD_CHANGE, mSearchWd));
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_QUICK_SEARCH) {
            if (event.obj != null) {
                List<Movie.Video> data = (List<Movie.Video>) event.obj;
                List<Movie.Video> videos = filterVideoList(data, mSearchWd);
                if (videos != null && !videos.isEmpty()) {
                    mSearchAdapter.addData(videos);
                    sortBySourceOrder();
                    showContent();
                }
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
                mHandler.removeCallbacksAndMessages(null);
                EventBus.getDefault().unregister(QuickSearchDialog.this);
                if (mOnDialogDismiss != null) mOnDialogDismiss.run();
            }
        });

        llLoading = findViewById(R.id.llQuickSearchLoading);
        llEmpty = findViewById(R.id.llQuickSearchEmpty);

        mRv = findViewById(R.id.sortRV);
        mSearchAdapter = new QuickSearchAdapter();
        mRv.setHasFixedSize(true);
        mRv.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));
        mRv.setAdapter(mSearchAdapter);

        // 遥控器与触摸/鼠标点击双重保障
        mRv.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                selectVideo(position);
            }
        });
        mRv.setOnInBorderKeyEventListener((direction, focused) -> {
            if (direction == View.FOCUS_UP) {
                if (mWordRV != null && mWordRV.getChildCount() > 0) {
                    mWordRV.requestFocus();
                    return true;
                }
            }
            return false;
        });
        mSearchAdapter.setOnItemClickListener((adapter, view, position) -> selectVideo(position));
        mSearchAdapter.setNewData(new ArrayList<>());

        //切换关键字搜索
        mWordAdapter = new SearchWordAdapter();
        mWordRV = findViewById(R.id.mGridViewWord);
        mWordRV.setAdapter(mWordAdapter);
        mWordRV.setLayoutManager(new V7LinearLayoutManager(context, 0, false));
        mWordRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                selectWord(position);
            }
        });
        mWordRV.setOnInBorderKeyEventListener((direction, focused) -> {
            if (direction == View.FOCUS_DOWN) {
                if (mRv != null && mRv.getChildCount() > 0) {
                    mRv.requestFocus();
                    return true;
                }
            }
            return false;
        });
        mWordAdapter.setOnItemClickListener((adapter, view, position) -> selectWord(position));
        mWordAdapter.setNewData(new ArrayList<>());

        showLoading();
    }
}
