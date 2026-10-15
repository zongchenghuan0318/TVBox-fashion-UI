package com.github.tvbox.osc.ui.fragment;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.BounceInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapter;
import com.github.tvbox.osc.ui.tv.widget.LoadMoreView;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.GlideImg;
import com.github.tvbox.osc.util.HawkConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.http.HttpDoRequest;
import com.lcstudio.commonsurport.util.DateUtil;
import com.lcstudio.commonsurport.util.NullUtil;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.studio.osc.R;
import com.uisupport.UiConstans;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FirstFragment extends BaseLazyFragment implements View.OnClickListener {

    private LinearLayout tvLive;
    private LinearLayout tvSearch;
    private LinearLayout tvSetting;
    private LinearLayout tvHistory;
    private LinearLayout tvCollect;
    private LinearLayout tvPush;
    private HomeHotVodAdapter mAdapter;
    private List<Movie.Video> mRecommendVideos;
    private List<Movie.Video> mDobanVideos = new ArrayList<>();
    private int mStart = 40;
    private TvRecyclerView mHotGridView;
    private ImageView mHeroBackdrop;
    private ImageView mHeroBackdropAlt;
    private boolean mHeroUsingAlt = false;
    private int mHeroSeq = 0;
    private Movie.Video mHeroVod;
    private TextView mHeroTitle;
    private TextView mHeroNote;
    private TextView mHeroMeta;
    private TextView mHeroDesc;
    private TextView mHomeTitle;
    private TextView mHomeSub;
    private ExecutorService mExecutorService;

    // 首页静置自动轮播：无操作 6 秒后平滑右移一张，末张回到第一张
    private static final long AUTO_SCROLL_DELAY = 6000L;
    private final Handler mAutoScrollHandler = new Handler(Looper.getMainLooper());
    private final Runnable mAutoScrollRun = new Runnable() {
        @Override
        public void run() {
            autoScrollNext();
            scheduleAutoScroll();
        }
    };
    // 页面内任何焦点变化（切 Tab、左栏导航、卡片滚动）都算用户操作，重置轮播计时；
    // 同时让背景海报跟随当前聚焦卡片（自动轮播不触发 OnItemListener，需要从焦点反查位置）
    private final ViewTreeObserver.OnGlobalFocusChangeListener mFocusChangeListener = (oldFocus, newFocus) -> {
        scheduleAutoScroll();
        bindHeroFromFocus(newFocus);
    };

    public static FirstFragment newInstance(List<Movie.Video> recVod) {
        return new FirstFragment().setmRecommendVideos(recVod);
    }

    public FirstFragment setmRecommendVideos(List<Movie.Video> recVod) {
        this.mRecommendVideos = recVod;
        return this;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_home;
    }

    @Override
    protected void init() {
        mExecutorService =  Executors.newSingleThreadExecutor();
        tvLive = findViewById(R.id.tvLive);
        tvSearch = findViewById(R.id.tvSearch);
        tvSetting = findViewById(R.id.tvSetting);
        tvCollect = findViewById(R.id.tvFavorite);
        tvHistory = findViewById(R.id.tvHistory);
        tvPush = findViewById(R.id.shareLayout);
        mHotGridView = findViewById(R.id.tvHotList);
        mHeroBackdrop = findViewById(R.id.ivHeroBackdrop);
        mHeroBackdropAlt = findViewById(R.id.ivHeroBackdropAlt);
        mHeroTitle = findViewById(R.id.tvHeroTitle);
        mHeroNote = findViewById(R.id.tvHeroNote);
        mHeroMeta = findViewById(R.id.tvHeroMeta);
        mHeroDesc = findViewById(R.id.tvHeroDesc);
        mHomeTitle = findViewById(R.id.tvHomeTitle);
        mHomeSub = findViewById(R.id.tvHomeSub);

        tvLive.setOnClickListener(this);
        tvSearch.setOnClickListener(this);
        tvSetting.setOnClickListener(this);
        tvHistory.setOnClickListener(this);
        tvPush.setOnClickListener(this);
        tvCollect.setOnClickListener(this);
        tvLive.setOnFocusChangeListener(focusChangeListener);
        tvSearch.setOnFocusChangeListener(focusChangeListener);
        tvSetting.setOnFocusChangeListener(focusChangeListener);
        tvHistory.setOnFocusChangeListener(focusChangeListener);
        tvPush.setOnFocusChangeListener(focusChangeListener);
        tvCollect.setOnFocusChangeListener(focusChangeListener);

        rootView.getViewTreeObserver().addOnGlobalFocusChangeListener(mFocusChangeListener);
        initGridView();
        setLoadSir(mHotGridView);
        getVideos();
    }

    private void initGridView() {
        // 首页为单排大卡片横向流：焦点随遥控方向横向推进
        mHotGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, LinearLayoutManager.HORIZONTAL, false));
        mAdapter = new HomeHotVodAdapter();
        mAdapter.setOnItemClickListener((adapter, view, position) -> {
            if (ApiConfig.getInstance().getSiteBeanList().isEmpty()) {
                Toast.makeText(mContext, "配置文件获取中，请稍后", Toast.LENGTH_SHORT).show();
                return;
            }
            Movie.Video vod = ((Movie.Video) adapter.getItem(position));
            if (vod.isMore) {
                //点击查看更多电影
                getMoreVideos(mStart);
                mStart += 20;
                return;
            }
            if (vod.id != null && !vod.id.isEmpty()) {
                Bundle bundle = new Bundle();
                bundle.putString("id", vod.id);
                bundle.putString("sourceKey", vod.sourceKey);
                jumpActivity(DetailActivity.class, bundle);
            } else {
                Intent newIntent = new Intent(mContext, SearchActivity.class);
                newIntent.putExtra("title", vod.name);
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mActivity.startActivity(newIntent);
            }
        });

        mAdapter.setOnLoadMoreListener(() -> {
            //滑到底触发
            mAdapter.setEnableLoadMore(true);
            getMoreVideos(mStart);
            mStart += 20;
        }, mHotGridView);
        mAdapter.setLoadMoreView(new LoadMoreView());

        mHotGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.06f).scaleY(1.06f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                // 焦点滚动时同步切换背景海报与节目信息
                bindHero((Movie.Video) mAdapter.getItem(position));
                // 焦点由用户操作变化时，自动轮播重新计时
                scheduleAutoScroll();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });
        // 有按键操作就打断自动轮播并重新计时
        mHotGridView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                scheduleAutoScroll();
            }
            return false;
        });
        mHotGridView.setAdapter(mAdapter);
    }

    @Override
    protected void onFragmentResume() {
        scheduleAutoScroll();
    }

    @Override
    protected void onFragmentPause() {
        stopAutoScroll();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAutoScroll();
        if (rootView != null) {
            rootView.getViewTreeObserver().removeOnGlobalFocusChangeListener(mFocusChangeListener);
        }
    }

    private void scheduleAutoScroll() {
        mAutoScrollHandler.removeCallbacks(mAutoScrollRun);
        mAutoScrollHandler.postDelayed(mAutoScrollRun, AUTO_SCROLL_DELAY);
    }

    private void stopAutoScroll() {
        mAutoScrollHandler.removeCallbacks(mAutoScrollRun);
    }

    /**
     * 自动推进到下一张卡片：正在滚动时跳过（等下一次计时），
     * 焦点停在左栏说明用户正在导航，此时不抢焦点也不轮播
     */
    private void autoScrollNext() {
        if (mHotGridView == null || mAdapter == null) return;
        if (mHotGridView.isScrolling() || isSidebarFocused()) return;
        List<Movie.Video> data = mAdapter.getData();
        int count = data.size();
        // 末尾的“查看更多”不是影片，轮播时跳过
        while (count > 0 && data.get(count - 1).isMore) count--;
        if (count < 2) return;
        int current = mHotGridView.getSelectedPosition();
        int next = current + 1;
        if (next >= count) next = 0;
        mHotGridView.setSelectionWithSmooth(next);
        // 库的平滑选中不会走 OnItemListener，也不会真的移动焦点，这里主动同步背景
        bindHero(data.get(next));
    }

    private boolean isSidebarFocused() {
        return tvLive.hasFocus() || tvSearch.hasFocus() || tvSetting.hasFocus()
                || tvHistory.hasFocus() || tvCollect.hasFocus() || tvPush.hasFocus();
    }

    /**
     * 从当前聚焦的控件反查它在卡片列表中的位置，同步背景海报
     */
    private void bindHeroFromFocus(View focused) {
        if (mHotGridView == null || mAdapter == null || focused == null) return;
        // 焦点可能在卡片内部子控件上，必须用 findContainingViewHolder 向上找，
        // getChildAdapterPosition 只接受 RecyclerView 的直接子 View，否则会 ClassCastException
        RecyclerView.ViewHolder holder = mHotGridView.findContainingViewHolder(focused);
        if (holder == null) return;
        List<Movie.Video> data = mAdapter.getData();
        int position = holder.getLayoutPosition();
        if (position < 0 || position >= data.size()) return;
        bindHero(data.get(position));
    }

    private void getVideos() {
        int homeSource = Hawk.get(HawkConfig.HOME_REC, 0);
        if (homeSource == 1) {
            //网站推荐
            int recCount = mRecommendVideos == null ? 0 : mRecommendVideos.size();
            setHeader("网站推荐", recCount > 0 ? "共 " + recCount + " 部" : "当前接口暂无推荐");
            if (recCount > 0) {
                mAdapter.setNewData(mRecommendVideos);
                bindHero(mRecommendVideos.get(0));
            } else {
                setHeroEmpty("暂无推荐影片", "可在设置中切换其他接口");
            }
            showSuccess();
        } else if (homeSource == 2) {
            //历史
            List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(100);
            List<Movie.Video> vodList = new ArrayList<>();
            for (VodInfo vodInfo : allVodRecord) {
                Movie.Video vod = new Movie.Video();
                vod.id = vodInfo.id;
                vod.sourceKey = vodInfo.sourceKey;
                vod.name = vodInfo.name;
                vod.pic = vodInfo.pic;
                if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty()) {
                    vod.note = "上次看到" + vodInfo.playNote;
                }
                vodList.add(vod);
            }
            setHeader("继续观看", vodList.isEmpty() ? "暂无播放记录" : "最近 " + vodList.size() + " 条播放记录");
            mAdapter.setNewData(vodList);
            if (vodList.isEmpty()) {
                setHeroEmpty("还没有播放记录", "看过的影片会出现在这里");
            } else {
                bindHero(vodList.get(0));
            }
            showSuccess();
        } else {
            //豆瓣推荐
            showLoading();
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    int year = DateUtil.getLastNYear(0);
                    String doubanUrl = UiConstans.DOU_BAN_URL + (year - 1) + "," + year + "&start=" + 0;
                    String result = HttpDoRequest.getInstance(getContext()).doGetRequestInOkGo(doubanUrl, null, true, 5 * 60 * 1000);
                    List<Movie.Video> list0 = parseJson(result);
                    if (!NullUtil.isNull(list0)) {
                        mDobanVideos.addAll(list0);
                    }
                    mActivity.runOnUiThread(() -> {
                        setHeader("豆瓣热播", "豆瓣 · " + (year - 1) + "-" + year + " 评分排序");
                        showSuccess();
                        mAdapter.setNewData(mDobanVideos);
                        if (mDobanVideos.isEmpty()) {
                            setHeroEmpty("暂无内容", "请检查网络后重试");
                        } else {
                            bindHero(mDobanVideos.get(0));
                        }
                        getMoreVideos(20);
                    });
                }
            }.start();
        }
    }

    private void setHeader(String title, String subTitle) {
        if (mHomeTitle != null) {
            mHomeTitle.setText(title);
        }
        if (mHomeSub != null) {
            mHomeSub.setText(subTitle);
        }
    }

    /**
     * 无内容时的安静空状态：不出图，只留标题与一行提示
     */
    private void setHeroEmpty(String title, String tip) {
        mHeroVod = null;
        if (mHeroTitle != null) {
            mHeroTitle.setText(title);
        }
        if (mHeroNote != null) {
            mHeroNote.setVisibility(View.GONE);
        }
        if (mHeroMeta != null) {
            mHeroMeta.setVisibility(View.VISIBLE);
            mHeroMeta.setText(tip);
        }
        if (mHeroDesc != null) {
            mHeroDesc.setVisibility(View.GONE);
        }
    }

    /**
     * 用当前焦点节目的海报与信息刷新背景 hero 区
     */
    private void bindHero(Movie.Video vod) {
        if (vod == null || mHeroBackdrop == null || mHeroBackdropAlt == null || vod.isMore) {
            return;
        }
        // 焦点回调和列表回调可能对同一张卡片各调用一次，去重避免重复播放淡入
        if (vod == mHeroVod) {
            return;
        }
        mHeroVod = vod;
        mHeroTitle.setText(NullUtil.isNull(vod.name) ? "" : vod.name);
        if (NullUtil.isNull(vod.note)) {
            mHeroNote.setVisibility(View.GONE);
        } else {
            mHeroNote.setVisibility(View.VISIBLE);
            mHeroNote.setText(vod.note);
        }
        String meta = buildMeta(vod);
        if (NullUtil.isNull(meta)) {
            mHeroMeta.setVisibility(View.GONE);
        } else {
            mHeroMeta.setVisibility(View.VISIBLE);
            mHeroMeta.setText(meta);
        }
        if (NullUtil.isNull(vod.des)) {
            mHeroDesc.setVisibility(View.GONE);
        } else {
            mHeroDesc.setVisibility(View.VISIBLE);
            mHeroDesc.setText(vod.des);
        }
        if (NullUtil.isNull(vod.pic)) {
            return;
        }
        final int seq = ++mHeroSeq;
        // incoming 为当前隐藏的一层，先把新海报载进来，载入完成后再与 outgoing 交叉淡入
        final ImageView incoming = mHeroUsingAlt ? mHeroBackdrop : mHeroBackdropAlt;
        final ImageView outgoing = mHeroUsingAlt ? mHeroBackdropAlt : mHeroBackdrop;
        incoming.setAlpha(0f);
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        Glide.with(incoming.getContext())
                .load(GlideImg.model(vod.pic))
                .placeholder(R.drawable.shape_home_hero_placeholder)
                .error(R.drawable.shape_home_hero_placeholder)
                .dontAnimate()
                .listener(new RequestListener<Object, GlideDrawable>() {
                    @Override
                    public boolean onException(Exception e, Object model, Target<GlideDrawable> target, boolean isFirstResource) {
                        if (seq == mHeroSeq) {
                            swapHero(incoming, outgoing, false);
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(GlideDrawable resource, Object model, Target<GlideDrawable> target,
                                                   boolean isFromMemoryCache, boolean isFirstResource) {
                        if (seq == mHeroSeq) {
                            swapHero(incoming, outgoing, true);
                        }
                        return false;
                    }
                })
                .into(incoming);
    }

    /**
     * 双层 cross-dissolve：新海报淡入并缓慢推近，旧海报同时淡出
     */
    private void swapHero(ImageView incoming, ImageView outgoing, boolean zoom) {
        ObjectAnimator.ofFloat(incoming, "alpha", 0f, 1f).setDuration(300).start();
        ObjectAnimator.ofFloat(outgoing, "alpha", 1f, 0f).setDuration(300).start();
        if (zoom) {
            ObjectAnimator.ofFloat(incoming, "scaleX", 1f, 1.05f).setDuration(6000).start();
            ObjectAnimator.ofFloat(incoming, "scaleY", 1f, 1.05f).setDuration(6000).start();
        }
        mHeroUsingAlt = (incoming == mHeroBackdropAlt);
    }

    /**
     * 拼接 "年份 · 地区 类型 · 主演" 形式的元信息行
     */
    private String buildMeta(Movie.Video vod) {
        StringBuilder sb = new StringBuilder();
        if (vod.year > 0) {
            sb.append(vod.year);
        }
        if (!NullUtil.isNull(vod.area)) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(vod.area);
        }
        if (!NullUtil.isNull(vod.type)) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(vod.type);
        }
        if (!NullUtil.isNull(vod.actor)) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(vod.actor);
        }
        return sb.toString();
    }

    private void getMoreVideos(int start) {
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                int year = DateUtil.getLastNYear(0);
                String doubanUrl = UiConstans.DOU_BAN_URL + (year - 1) + "," + year + "&start=" + start;
                String result = HttpDoRequest.getInstance(getContext()).doGetRequestInOkGo(doubanUrl, null, true, 5 * 60 * 1000);
                List<Movie.Video> listMore = parseJson(result);
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //UiHelper.showToast(getActivity(), doubanUrl + " result=" + result);
                        L.d(doubanUrl + " result=" + result);
                        if (!NullUtil.isNull(listMore)) {
                            mAdapter.addData(listMore);//解决焦点跳走的问题
                        }
                        mAdapter.loadMoreComplete();
                    }
                });
            }
        });
    }

    private ArrayList<Movie.Video> parseJson(String json) {
        ArrayList<Movie.Video> videos = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            JsonArray array = infoJson.getAsJsonArray("data");
            for (JsonElement ele : array) {
                JsonObject obj = (JsonObject) ele;
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("rate").getAsString();
                vod.pic = obj.get("cover").getAsString();
                videos.add(vod);
            }
        } catch (Throwable th) {
            L.e(th);
        }
        return videos;
    }

    private View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }
        }
    };

    @Override
    public void onClick(View v) {
        FastClickCheckUtil.check(v);
        if (v.getId() == R.id.tvLive) {
            jumpActivity(LivePlayActivity.class);
        } else if (v.getId() == R.id.tvSearch) {
            jumpActivity(SearchActivity.class);
        } else if (v.getId() == R.id.tvSetting) {
            jumpActivity(SettingActivity.class);
        } else if (v.getId() == R.id.tvHistory) {
            jumpActivity(HistoryActivity.class);
        } else if (v.getId() == R.id.shareLayout) {
        } else if (v.getId() == R.id.tvFavorite) {
            jumpActivity(CollectActivity.class);
        }
    }
}