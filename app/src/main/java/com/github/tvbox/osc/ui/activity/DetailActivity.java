package com.github.tvbox.osc.ui.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.github.tvbox.osc.util.BlurTransformation;
import com.github.tvbox.osc.util.GlideImg;
import com.github.tvbox.osc.util.PosterImageLoader;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.bean.EpisodeRangeBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.PlayerEpisodeRangeAdapter;
import com.github.tvbox.osc.ui.adapter.SeriesAdapter;
import com.github.tvbox.osc.ui.adapter.SeriesFlagAdapter;
import com.github.tvbox.osc.ui.dialog.QuickSearchDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.studio.osc.R;
import com.uisupport.update.Initer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import xyz.doikki.videoplayer.util.PrintUtil;

/**
 * tvbox交流QQ群：879811030
 */
public class DetailActivity extends BaseActivity implements View.OnClickListener {
    private ImageView thumpImg;
    private ImageView ivDetailBackdrop;
    private TextView tvName;
    private TextView tvYear;
    private TextView tvSite;
    private TextView tvArea;
    private TextView tvLang;
    private TextView tvType;
    private TextView tvActor;
    private TextView tvDirector;
    private TextView tvDes;
    private TextView tvPlay;
    private TextView tvCollect;
    private TvRecyclerView mFlagGridView;
    private TvRecyclerView mSeriesRangeRV;
    private TvRecyclerView mSeriesGridView;
    private LinearLayout mEmptyLayout;
    private SourceViewModel mSourceViewModel;
    private Movie.Video mVideo;
    private VodInfo mVodInfo;
    private SeriesFlagAdapter mFlagAdapter;
    private SeriesAdapter mSeriesAdapter;
    private PlayerEpisodeRangeAdapter mSeriesRangeAdapter;
    public String vodId;
    public String sourceKey;
    public String noteStr;
    public String movieName;
    boolean bSeriesSelect = false;
    /** 进入详情页后，首次加载完成时把焦点落到「当前集」上（否则默认停在左上角返回键） */
    private boolean bNeedFocusSeries = true;
    private boolean autoPlayFromHistory;
    private int detailRequestId;
    /** 当前集还没被布局出来时的重试次数，避免滚动/布局未完成导致焦点设置失效 */
    private int mFocusSeriesRetry = 0;
    private View mFocusView = null;
    private String searchTitle = "";
    private boolean bHadQuickStart = false;
    private int quickSearchRequestId;
    private List<Movie.Video> quickSearchList = new ArrayList<>();
    private List<String> wordList = new ArrayList<>();
    public ExecutorService mExecutorService;
    private final Handler detailHandler = new Handler(Looper.getMainLooper());
    private final Runnable detailTimeout = new Runnable() {
        @Override
        public void run() {
            showEmpty();
            Toast.makeText(DetailActivity.this, "详情加载超时，请检查网络或更换搜索源", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_detail;
    }

    @Override
    protected void init() {
        L.d("init()");
        EventBus.getDefault().register(this);
        mExecutorService = Executors.newFixedThreadPool(5);
        initView();
        initViewModelObserve();
        initData();
    }

    private void initView() {
        thumpImg = findViewById(R.id.ivThumb);
        ivDetailBackdrop = findViewById(R.id.ivDetailBackdrop);
        tvName = findViewById(R.id.tvName);
        tvName.setOnClickListener(this);
        tvYear = findViewById(R.id.tvYear);
        tvSite = findViewById(R.id.tvSite);
        tvArea = findViewById(R.id.tvArea);
        tvLang = findViewById(R.id.tvLang);
        tvType = findViewById(R.id.tvType);
        tvActor = findViewById(R.id.tvActor);
        tvDirector = findViewById(R.id.tvDirector);
        tvDes = findViewById(R.id.tvDes);
        tvPlay = findViewById(R.id.tvPlay);
        TextView tvSort = findViewById(R.id.tvSort);
        tvCollect = findViewById(R.id.tvCollect);
        TextView tvQuickSearch = findViewById(R.id.tvQuickSearch);
        mEmptyLayout = findViewById(R.id.mEmptyPlaylist);

        initSeriesGridView();
        initFlagGridView();
        setLoadSir(findViewById(R.id.llLayout));

        tvSort.setOnClickListener(v -> {
            if (mVodInfo != null && mVodInfo.seriesMap.size() > 0) {
                mVodInfo.reverseSort = !mVodInfo.reverseSort;
                mVodInfo.reverse();
                List<VodInfo.VodSeries> currentSeries = mVodInfo.seriesMap.get(mVodInfo.playFlag);
                if (currentSeries != null && currentSeries.size() > 0) {
                    if (mVodInfo.playIndex >= 0 && mVodInfo.playIndex < currentSeries.size()) {
                        mVodInfo.playIndex = currentSeries.size() - 1 - mVodInfo.playIndex;
                    }
                }
                tvSort.setText(mVodInfo.reverseSort ? "正\t序" : "倒\t序");
                refreshList(false);
            }
        });
        tvPlay.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            jumpToPlay();
        });

        Initer.init(this);
        //分词搜索
        tvQuickSearch.setOnClickListener(v -> {
            startQuickSearch();
            QuickSearchDialog quickSearchDialog = new QuickSearchDialog(DetailActivity.this, searchTitle);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, quickSearchList));
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, wordList));
            quickSearchDialog.show();
            quickSearchDialog.setOnDialogDismissListener(() -> {
                quickSearchRequestId++;
                OkGo.getInstance().cancelTag("search");
            });
        });

        tvCollect.setEnabled(false);
        tvCollect.setOnClickListener(v -> toggleCollect());
    }

    private void initViewModelObserve() {
        mSourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        mSourceViewModel.detailResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                if (mSourceViewModel.getLastPostedDetailRequestId() != detailRequestId) return;
                detailHandler.removeCallbacks(detailTimeout);
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                    showSuccess();
                    mVideo = absXml.movie.videoList.get(0);
                    mVodInfo = new VodInfo();
                    mVodInfo.setVideo(mVideo);
                    mVodInfo.sourceKey = mVideo.sourceKey;
                    refreshCollectButton();

                    tvName.setText(mVideo.name);
                    SiteBean videoSite = ApiConfig.getInstance().getSiteBean(mVideo.sourceKey);
                    setTextShow(tvSite, "来源：", videoSite == null ? mVideo.sourceKey : videoSite.getName());
                    setTextShow(tvYear, "年份：", mVideo.year == 0 ? "" : String.valueOf(mVideo.year));
                    setTextShow(tvArea, "地区：", mVideo.area);
                    setTextShow(tvLang, "语言：", mVideo.lang);
                    setTextShow(tvType, "类型：", mVideo.type);
                    setTextShow(tvActor, "演员：", mVideo.actor);
                    setTextShow(tvDirector, "导演：", mVideo.director);
                    setTextShow(tvDes, "内容简介：", removeHtmlTag(mVideo.des));
                    PosterImageLoader.bind(thumpImg, mVideo.pic);
                    if (ivDetailBackdrop != null && mVideo.pic != null && !mVideo.pic.isEmpty()) {
                        Glide.with(DetailActivity.this)
                                .load(GlideImg.heroModel(mVideo.pic))
                                .dontAnimate()
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .bitmapTransform(new BlurTransformation(DetailActivity.this, 18, 5))
                                .into(ivDetailBackdrop);
                    }

                    if (mVodInfo.seriesMap != null && mVodInfo.seriesMap.size() > 0) {
                        mFlagGridView.setVisibility(View.VISIBLE);
                        mSeriesGridView.setVisibility(View.VISIBLE);
                        tvPlay.setVisibility(View.VISIBLE);
                        mEmptyLayout.setVisibility(View.GONE);

                        final String curSourceKey = sourceKey;
                        final String curVodId = vodId;
                        final VodInfo curVodInfo = mVodInfo;
                        final int curReqId = detailRequestId;

                        // 异步读取 Room 数据库与 JSON 反序列化，彻底消除电视端打开详情弹窗时 50-150ms 的主线程掉帧与卡顿
                        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
                            VodInfo vodInfoRecord = RoomDataManger.getVodInfo(curSourceKey, curVodId);
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed() || curReqId != detailRequestId || curVodInfo != mVodInfo) {
                                    return;
                                }
                                applyVodHistoryAndRender(vodInfoRecord);
                            });
                        });
                    } else {
                        mFlagGridView.setVisibility(View.GONE);
                        mSeriesGridView.setVisibility(View.GONE);
                        tvPlay.setVisibility(View.GONE);
                        mEmptyLayout.setVisibility(View.VISIBLE);
                        SiteBean siteBean = ApiConfig.getInstance().getSiteBean(sourceKey);
                        String site = siteBean == null ? sourceKey : siteBean.getName();
                        PrintUtil.saveError(site + movieName, "暂无播放数据");
                    }
                } else {
                    showEmpty();
                    SiteBean siteBean = ApiConfig.getInstance().getSiteBean(sourceKey);
                    String site = siteBean == null ? sourceKey : siteBean.getName();
                    PrintUtil.saveError(site + movieName, "没找到详情数据");
                }
            }
        });
    }

    private void applyVodHistoryAndRender(VodInfo vodInfoRecord) {
        if (mVodInfo == null) return;
        // 读取历史记录
        if (vodInfoRecord != null) {
            mVodInfo.playIndex = Math.max(vodInfoRecord.playIndex, 0);
            mVodInfo.playFlag = vodInfoRecord.playFlag;
            mVodInfo.playerCfg = vodInfoRecord.playerCfg;
            mVodInfo.reverseSort = vodInfoRecord.reverseSort;
        } else {
            mVodInfo.playIndex = 0;
            mVodInfo.playFlag = null;
            mVodInfo.playerCfg = "";
            mVodInfo.reverseSort = false;
        }

        if (mVodInfo.reverseSort) {
            mVodInfo.reverse();
        }

        if (mVodInfo.playFlag == null || !mVodInfo.seriesMap.containsKey(mVodInfo.playFlag))
            mVodInfo.playFlag = (String) mVodInfo.seriesMap.keySet().toArray()[0];

        int flagScrollTo = 0;
        for (int j = 0; j < mVodInfo.seriesFlags.size(); j++) {
            VodInfo.VodSeriesFlag flag = mVodInfo.seriesFlags.get(j);
            if (flag.name.equals(mVodInfo.playFlag)) {
                flagScrollTo = j;
                flag.selected = true;
            } else
                flag.selected = false;
        }

        mFlagAdapter.setNewData(mVodInfo.seriesFlags);
        mFlagGridView.scrollToPosition(flagScrollTo);
        refreshList(!autoPlayFromHistory);
        if (autoPlayFromHistory) {
            autoPlayFromHistory = false;
            mSeriesGridView.postDelayed(DetailActivity.this::jumpToPlay, 120L);
        }
    }

    private void refreshCollectButton() {
        if (tvCollect == null || mVodInfo == null) return;
        VodCollect record = RoomDataManger.getVodCollect(mVodInfo.sourceKey, mVodInfo.id);
        boolean collected = record != null;
        tvCollect.setText(collected ? "已收藏" : "加入收藏");
        tvCollect.setContentDescription(collected ? "已收藏，按确定取消收藏" : "按确定加入收藏");
        tvCollect.setEnabled(true);
    }

    private void toggleCollect() {
        if (mVodInfo == null) return;
        VodCollect record = RoomDataManger.getVodCollect(mVodInfo.sourceKey, mVodInfo.id);
        if (record == null) {
            RoomDataManger.insertVodCollect(mVodInfo.sourceKey, mVodInfo);
            Toast.makeText(DetailActivity.this, "已加入收藏夹", Toast.LENGTH_SHORT).show();
        } else {
            RoomDataManger.deleteVodCollect(record.getId());
            Toast.makeText(DetailActivity.this, "已取消收藏", Toast.LENGTH_SHORT).show();
        }
        refreshCollectButton();
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_COLLECT_REFRESH));
    }

    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            noteStr = bundle.getString("note");
            movieName = bundle.getString("movieName");
            autoPlayFromHistory = bundle.getBoolean("autoPlayFromHistory", false);
            loadDetail(bundle.getString("id", null), bundle.getString("sourceKey", ""));
        }
    }

    private void loadDetail(String vid, String key) {
        if (vid != null) {
            vodId = vid;
            sourceKey = key;
            // 换源搜索会重新加载详情，这里重置焦点标记，保证重新进入时焦点仍落在当前集
            bNeedFocusSeries = true;
            mFocusSeriesRetry = 0;
            showLoading();
            detailHandler.removeCallbacks(detailTimeout);
            detailHandler.postDelayed(detailTimeout, 20000L);
            L.d("loadDetail()");
            detailRequestId = mSourceViewModel.getDetail(sourceKey, vodId);
        }
    }

    @Override
    public void onClick(View view) {
        if (R.id.tvName == view.getId()) {
            finish();
        }
    }

    private void initFlagGridView() {
        mFlagGridView = findViewById(R.id.mGridViewFlag);
        mFlagGridView.setHasFixedSize(true);
        mFlagGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        mFlagAdapter = new SeriesFlagAdapter();
        mFlagGridView.setAdapter(mFlagAdapter);
        mFlagGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                refresh(itemView, position);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                refresh(itemView, position);
            }

            private void refresh(View itemView, int position) {
                String newFlag = mFlagAdapter.getData().get(position).name;
                if (mVodInfo != null && !mVodInfo.playFlag.equals(newFlag)) {
                    for (int i = 0; i < mVodInfo.seriesFlags.size(); i++) {
                        VodInfo.VodSeriesFlag flag = mVodInfo.seriesFlags.get(i);
                        if (flag.name.equals(mVodInfo.playFlag)) {
                            flag.selected = false;
                            mFlagAdapter.notifyItemChanged(i);
                            break;
                        }
                    }
                    VodInfo.VodSeriesFlag flag = mVodInfo.seriesFlags.get(position);
                    flag.selected = true;
                    mVodInfo.playFlag = newFlag;
                    mFlagAdapter.notifyItemChanged(position);
                    refreshList();
                }
                mFocusView = itemView;
            }
        });
    }

    private void initSeriesGridView(){
        mSeriesRangeRV = findViewById(R.id.seriesRangeRV);
        mSeriesRangeRV.setHasFixedSize(true);
        mSeriesRangeRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        mSeriesRangeAdapter = new PlayerEpisodeRangeAdapter();
        mSeriesRangeRV.setAdapter(mSeriesRangeAdapter);
        mSeriesRangeRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (position >= 0 && position < mSeriesRangeAdapter.getData().size()) {
                    mSeriesRangeAdapter.setSelectedIndex(position);
                    EpisodeRangeBean item = mSeriesRangeAdapter.getData().get(position);
                    mSeriesGridView.scrollToPosition(item.startIndex);
                    mSeriesGridView.setSelection(item.startIndex);
                }
            }
        });
        mSeriesRangeAdapter.setOnItemClickListener((adapter, view, position) -> {
            if (position >= 0 && position < mSeriesRangeAdapter.getData().size()) {
                mSeriesRangeAdapter.setSelectedIndex(position);
                EpisodeRangeBean item = mSeriesRangeAdapter.getData().get(position);
                mSeriesGridView.scrollToPosition(item.startIndex);
                mSeriesGridView.setSelection(item.startIndex);
            }
        });

        mSeriesGridView = findViewById(R.id.seriesRV);
        mSeriesGridView.setHasFixedSize(true);
        mSeriesGridView.setLayoutManager(        new V7GridLayoutManager(this.mContext,
                com.github.tvbox.osc.util.SeniorMode.getGridColumnCount(
                        isBaseOnWidth() ? 6 : 7)));
        mSeriesAdapter = new SeriesAdapter();
        mSeriesGridView.setAdapter(mSeriesAdapter);

        mSeriesGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                bSeriesSelect = false;
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                bSeriesSelect = true;
                if (position >= 0 && mSeriesRangeAdapter != null && mSeriesRangeRV != null && mSeriesRangeRV.getVisibility() == View.VISIBLE) {
                    int rIdx = position / 30;
                    if (rIdx != mSeriesRangeAdapter.getSelectedIndex()) {
                        mSeriesRangeAdapter.setSelectedIndex(rIdx);
                        mSeriesRangeRV.scrollToPosition(rIdx);
                    }
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });
        mSeriesAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                if (mVodInfo != null && mVodInfo.seriesMap.get(mVodInfo.playFlag).size() > 0) {
                    if (mVodInfo.playIndex != position) {
                        mSeriesAdapter.getData().get(mVodInfo.playIndex).selected = false;
                        mSeriesAdapter.notifyItemChanged(mVodInfo.playIndex);
                        mSeriesAdapter.getData().get(position).selected = true;
                        mSeriesAdapter.notifyItemChanged(position);
                        mVodInfo.playIndex = position;
                    }
                    mSeriesAdapter.getData().get(mVodInfo.playIndex).selected = true;
                    mSeriesAdapter.notifyItemChanged(mVodInfo.playIndex);
                    jumpToPlay();
                }
            }
        });
    }

    private void jumpToPlay() {
        if(UiHelper.canPlay(mVideo)&& UiHelper.canPlayName(mVideo)) {
            if (mVodInfo != null && mVodInfo.seriesMap.get(mVodInfo.playFlag).size() > 0) {
                Bundle bundle = new Bundle();
                bundle.putString("sourceKey", sourceKey);
                bundle.putSerializable("VodInfo", mVodInfo);
                jumpActivity(PlayActivity.class, bundle);
            }
        }else{
            L.d(mVideo.type);
        }
    }

    void refreshList() {
        refreshList(true);
    }

    private void refreshList(boolean requestFocus) {
        if (mVodInfo.seriesMap.get(mVodInfo.playFlag).size() <= mVodInfo.playIndex) {
            mVodInfo.playIndex = 0;
        }

        List<VodInfo.VodSeries> currentSeries = mVodInfo.seriesMap.get(mVodInfo.playFlag);
        if (currentSeries != null) {
            currentSeries.get(mVodInfo.playIndex).selected = true;
        }

        mSeriesAdapter.setNewData(currentSeries);

        if (currentSeries != null && currentSeries.size() > 30 && mSeriesRangeRV != null) {
            mSeriesRangeRV.setVisibility(View.VISIBLE);
            List<EpisodeRangeBean> rangeList = new ArrayList<>();
            final int step = 30;
            for (int i = 0; i < currentSeries.size(); i += step) {
                int start = i + 1;
                int end = Math.min(i + step, currentSeries.size());
                rangeList.add(new EpisodeRangeBean(i, end - 1, start + "-" + end));
            }
            mSeriesRangeAdapter.setNewData(rangeList);
            int currentRange = mVodInfo.playIndex / step;
            mSeriesRangeAdapter.setSelectedIndex(currentRange);
            mSeriesRangeRV.scrollToPosition(currentRange);
        } else if (mSeriesRangeRV != null) {
            mSeriesRangeRV.setVisibility(View.GONE);
        }

        if (!requestFocus) return;
        mSeriesGridView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mSeriesGridView.scrollToPosition(mVodInfo.playIndex);
                focusCurrentSeries();
            }
        }, 100);
    }

    /**
     * 把焦点移到当前集上。
     * 首次进入时列表还没有滚动到该集，其子 View 尚未被布局出来，直接 setSelection 会失效，
     * 因此这里在子 View 出现后再设置焦点，并限制重试次数避免死循环。
     */
    private void focusCurrentSeries() {
        if (!bNeedFocusSeries || mVodInfo == null || mSeriesGridView.getLayoutManager() == null) {
            bNeedFocusSeries = false;
            return;
        }
        if (mFocusSeriesRetry++ > 20) {
            bNeedFocusSeries = false;
            return;
        }
        if (mSeriesGridView.getLayoutManager().findViewByPosition(mVodInfo.playIndex) != null) {
            bNeedFocusSeries = false;
            mSeriesGridView.setSelection(mVodInfo.playIndex);
        } else {
            mSeriesGridView.postDelayed(this::focusCurrentSeries, 100);
        }
    }


    private void setTextShow(TextView view, String tag, String info) {
        if (info == null || info.trim().isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setText(Html.fromHtml(getHtml(tag, info)));
    }

    private String getHtml(String label, String content) {
        if (content == null) {
            content = "";
        }
        return label + "<font color=\"#FFFFFF\">" + content + "</font>";
    }

    private String removeHtmlTag(String info) {
        if (info == null) return "";
        return info.replaceAll("\\<.*?\\>", "").replaceAll("\\s", "");
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_REFRESH) {
            if (event.obj != null) {
                if (event.obj instanceof Integer) {
                    int index = (int) event.obj;
                    if (index != mVodInfo.playIndex) {
                        mSeriesAdapter.getData().get(mVodInfo.playIndex).selected = false;
                        mSeriesAdapter.notifyItemChanged(mVodInfo.playIndex);
                        mSeriesAdapter.getData().get(index).selected = true;
                        mSeriesAdapter.notifyItemChanged(index);
                        mSeriesGridView.setSelection(index);
                        mVodInfo.playIndex = index;
                    }
                } else if (event.obj instanceof JSONObject) {
                    mVodInfo.playerCfg = ((JSONObject) event.obj).toString();
                }
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_SELECT) {
            if (event.obj != null) {
                Movie.Video video = (Movie.Video) event.obj;
                loadDetail(video.id, video.sourceKey);
            } else {
                L.d("RefreshEvent.TYPE_QUICK_SEARCH_SELECT  event.obj==null");
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD_CHANGE) {
            //切换关键字搜索
            if (event.obj != null) {
                String word = (String) event.obj;
                switchSearchWord(word);
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_RESULT) {
            if (event.requestId != quickSearchRequestId) return;
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private void switchSearchWord(String word) {
        OkGo.getInstance().cancelTag("search");
        quickSearchList.clear();
        searchTitle = word;
        quickSearch();
    }

    //详情界面 切换关键字搜索
    private void startQuickSearch() {
        if (bHadQuickStart) return;
        bHadQuickStart = true;
        OkGo.getInstance().cancelTag("search");
        wordList.clear();
        searchTitle = mVideo.name;
        quickSearchList.clear();
        wordList.add(searchTitle);
        getFenCi();
        quickSearch();
    }

    private void getFenCi() {
        L.d("getFenCi start");
        // 分词
        OkGo.<String>get("http://api.pullword.com/get.php?source=" + URLEncoder.encode(searchTitle) + "&param1=0&param2=0&json=1")
                .tag("fenci")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) {
                            return response.body().string();
                        } else {
                            throw new IllegalStateException("网络请求错误");
                        }
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        String json = response.body();
                        L.d("getFenCi json=" + json);
                        wordList.clear();
                        try {
                            for (JsonElement je : new Gson().fromJson(json, JsonArray.class)) {
                                wordList.add(je.getAsJsonObject().get("t").getAsString());
                            }
                        } catch (Throwable th) {
                            L.w(th.getMessage());
                        }
                        wordList.add(searchTitle);
                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, wordList));
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        if (null == response) {
                            L.d("getFenCi error= null");
                        } else {
                            L.d("getFenCi error=" + response.body());
                        }
                    }
                });
    }

    private void quickSearch() {
        final int requestId = ++quickSearchRequestId;
        final String query = searchTitle;
        // 搜索顺序与「搜索源管理」的勾选顺序一致（已过滤掉未勾选与不可搜索的源）
        ArrayList<String> siteKey = new ArrayList<>();
        for (SiteBean bean : ApiConfig.getInstance().getOrderedSearchSiteList()) {
            if (!bean.isQuickSearch()) continue;
            siteKey.add(bean.getKey());
        }
        OkGo.getInstance().cancelTag("search");
        for (String key : siteKey) {
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    mSourceViewModel.getQuickSearch(key, query, requestId);
                }
            });
        }
    }

    private void searchData(AbsXml absXml) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                // 去除当前相同的影片
                if (video.sourceKey.equals(sourceKey) && video.id.equals(vodId))
                    continue;
                data.add(video);
            }
            quickSearchList.addAll(data);
            // 展示顺序按搜索源的勾选顺序排列，同一源内部保持返回顺序
            Collections.sort(quickSearchList, (a, b) -> ApiConfig.getInstance().getSearchSiteOrderIndex(a.sourceKey)
                    - ApiConfig.getInstance().getSearchSiteOrderIndex(b.sourceKey));
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, data));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detailHandler.removeCallbacksAndMessages(null);
        OkGo.getInstance().cancelTag("fenci");
        if (mSourceViewModel != null) mSourceViewModel.cancelDetailRequests();
        OkGo.getInstance().cancelTag("search");
        EventBus.getDefault().unregister(this);
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
            mExecutorService = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (bSeriesSelect) {
            if (mFocusView != null && !mFocusView.isFocused()) {
                mFocusView.requestFocus();
                return;
            }
        }
        super.onBackPressed();
    }
}
