package com.github.tvbox.osc.ui.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.RefreshEvent;
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
    private TvRecyclerView mFlagGridView;
    private TvRecyclerView mSeriesGridView;
    private LinearLayout mEmptyLayout;
    private SourceViewModel mSourceViewModel;
    private Movie.Video mVideo;
    private VodInfo mVodInfo;
    private SeriesFlagAdapter mFlagAdapter;
    private SeriesAdapter mSeriesAdapter;
    public String vodId;
    public String sourceKey;
    public String noteStr;
    public String movieName;
    boolean bSeriesSelect = false;
    private View mFocusView = null;
    private String searchTitle = "";
    private boolean bHadQuickStart = false;
    private List<Movie.Video> quickSearchList = new ArrayList<>();
    private List<String> wordList = new ArrayList<>();
    public ExecutorService mExecutorService;

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
        TextView tvCollect = findViewById(R.id.tvCollect);
        TextView tvQuickSearch = findViewById(R.id.tvQuickSearch);
        mEmptyLayout = findViewById(R.id.mEmptyPlaylist);

        initSeriesGridView();
        initFlagGridView();
        setLoadSir(findViewById(R.id.llLayout));

        tvSort.setOnClickListener(v -> {
            if (mVodInfo != null && mVodInfo.seriesMap.size() > 0) {
                mVodInfo.reverseSort = !mVodInfo.reverseSort;
                mVodInfo.reverse();
                mSeriesAdapter.notifyDataSetChanged();
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
            quickSearchDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    OkGo.getInstance().cancelTag("search");
                }
            });
        });

        tvCollect.setOnClickListener(v -> {
            RoomDataManger.insertVodCollect(sourceKey, mVodInfo);
            Toast.makeText(DetailActivity.this, "已加入收藏夹", Toast.LENGTH_SHORT).show();
        });
    }

    private void initViewModelObserve() {
        mSourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        mSourceViewModel.detailResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                    showSuccess();
                    mVideo = absXml.movie.videoList.get(0);
                    mVodInfo = new VodInfo();
                    mVodInfo.setVideo(mVideo);
                    mVodInfo.sourceKey = mVideo.sourceKey;

                    tvName.setText(mVideo.name);
                    setTextShow(tvSite, "来源：", ApiConfig.getInstance().getSiteBean(mVideo.sourceKey).getName());
                    setTextShow(tvYear, "年份：", mVideo.year == 0 ? "" : String.valueOf(mVideo.year));
                    setTextShow(tvArea, "地区：", mVideo.area);
                    setTextShow(tvLang, "语言：", mVideo.lang);
                    setTextShow(tvType, "类型：", mVideo.type);
                    setTextShow(tvActor, "演员：", mVideo.actor);
                    setTextShow(tvDirector, "导演：", mVideo.director);
                    setTextShow(tvDes, "内容简介：", removeHtmlTag(mVideo.des));
                    if (!NullUtil.isNull(mVideo.pic)) {
                        Glide.with(thumpImg.getContext()).load(mVideo.pic)
                                .placeholder(R.drawable.img_loading_placeholder)
                                .error(R.drawable.img_loading_placeholder)
                                .into(thumpImg);
                    } else {
                        thumpImg.setImageResource(R.drawable.img_loading_placeholder);
                    }

                    if (mVodInfo.seriesMap != null && mVodInfo.seriesMap.size() > 0) {
                        mFlagGridView.setVisibility(View.VISIBLE);
                        mSeriesGridView.setVisibility(View.VISIBLE);
                        tvPlay.setVisibility(View.VISIBLE);
                        mEmptyLayout.setVisibility(View.GONE);

                        VodInfo vodInfoRecord = RoomDataManger.getVodInfo(sourceKey, vodId);
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
                        refreshList();
                    } else {
                        mFlagGridView.setVisibility(View.GONE);
                        mSeriesGridView.setVisibility(View.GONE);
                        tvPlay.setVisibility(View.GONE);
                        mEmptyLayout.setVisibility(View.VISIBLE);
                        String site = ApiConfig.getInstance().getSiteBean(sourceKey).getName();
                        PrintUtil.saveError(site + movieName, "暂无播放数据");
                    }
                } else {
                    showEmpty();
                    String site = ApiConfig.getInstance().getSiteBean(sourceKey).getName();
                    PrintUtil.saveError(site + movieName, "没找到详情数据");
                }
            }
        });
    }

    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            noteStr = bundle.getString("note");
            movieName = bundle.getString("movieName");
            loadDetail(bundle.getString("id", null), bundle.getString("sourceKey", ""));
        }
    }

    private void loadDetail(String vid, String key) {
        if (vid != null) {
            vodId = vid;
            sourceKey = key;
            showLoading();
            L.d("loadDetail()");
            mSourceViewModel.getDetail(sourceKey, vodId);
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
        mSeriesGridView = findViewById(R.id.seriesRV);
        mSeriesGridView.setHasFixedSize(true);
        mSeriesGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, isBaseOnWidth() ? 6 : 7));
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
        if (mVodInfo.seriesMap.get(mVodInfo.playFlag).size() <= mVodInfo.playIndex) {
            mVodInfo.playIndex = 0;
        }

        if (mVodInfo.seriesMap.get(mVodInfo.playFlag) != null) {
            mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex).selected = true;
        }

        mSeriesAdapter.setNewData(mVodInfo.seriesMap.get(mVodInfo.playFlag));
        mSeriesGridView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mSeriesGridView.scrollToPosition(mVodInfo.playIndex);
            }
        }, 100);
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
        List<SiteBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.getInstance().getSiteBeanList());
        SiteBean home = ApiConfig.getInstance().getHomeBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<String> siteKey = new ArrayList<>();
        for (SiteBean bean : searchRequestList) {
            // 设置里被取消勾选的源不参与任何聚合搜索
            if (!bean.isSearchable() || !bean.isQuickSearch() || !ApiConfig.getInstance().isSearchSiteEnabled(bean.getKey())) {
                continue;
            }
            siteKey.add(bean.getKey());
        }
        OkGo.getInstance().cancelTag("search");
        for (String key : siteKey) {
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    mSourceViewModel.getQuickSearch(key, searchTitle);
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
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, data));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        OkGo.getInstance().cancelTag("fenci");
        OkGo.getInstance().cancelTag("detail");
        OkGo.getInstance().cancelTag("search");
        EventBus.getDefault().unregister(this);
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