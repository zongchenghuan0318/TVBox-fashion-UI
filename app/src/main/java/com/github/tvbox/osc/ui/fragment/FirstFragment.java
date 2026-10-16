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
import android.view.animation.DecelerateInterpolator;
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
import com.github.tvbox.osc.bean.SiteBean;
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
import com.github.tvbox.osc.util.SharpenTransformation;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.lifecycle.ViewModelProvider;
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
    /** 豆瓣被风控后的冷却截止时间：冷却期内不再直连豆瓣，只用缓存/兜底，避免继续砸接口把限流期拉长 */
    private static long sDoubanBlockedUntil = 0L;
    private static final long DOU_BAN_BLOCK_COOLDOWN_MS = 5 * 60 * 1000L;
    /** 豆瓣翻页上限：按出口 IP 限流，翻页过多必然触发风控，最多只额外拉 2 页 */
    private static final int DOU_BAN_MAX_START = 40;
    private int mStart = 40;
    private TvRecyclerView mHotGridView;
    private ImageView mHeroBackdrop;
    private ImageView mHeroBackdropAlt;
    private boolean mHeroUsingAlt = false;
    private int mHeroSeq = 0;
    /** 已经完成交叉淡入的序号，防止同一张海报的缩略图与高清图各淡入一次造成闪动 */
    private int mHeroSwappedSeq = -1;
    private Movie.Video mHeroVod;
    /**
     * 全屏背景是 1080px 大图放大到屏幕宽再逐像素锐化，解码很重；
     * 快速滚卡时逐个加载会互相拖后腿（背景明显跟不上卡片），
     * 所以先排队，焦点停稳后再真正解码。
     */
    private static final long HERO_IMAGE_SETTLE_DELAY = 130L;
    private final Handler mHeroHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHeroImageRun = new Runnable() {
        @Override
        public void run() {
            if (mHeroVod != null) {
                loadHeroImage(mHeroVod);
            }
        }
    };
    /**
     * 卡片弧形排布：以列表中心为弧顶，中间的卡最高最亮，
     * 越往两端越低、越暗、略微向外翻转，整排形成一条弧线；
     * 滚动时卡片沿弧线滑动，切换不再是平铺一排平移。
     */
    private static final float ARC_DROP_RATIO = 0.36f;   // 两端下沉高度（相对卡片高度）
    private static final float ARC_ROTATION_Y = 13f;     // 两端卡片的偏转角（度），形成弧面
    private static final float ARC_MIN_ALPHA = 0.74f;    // 两端卡片的最低不透明度
    /** 弧线深度归一化基准：距中心达到列表半宽的这个比例时，下沉量达到最大 */
    private static final float ARC_REACH_RATIO = 0.72f;
    private static final long ITEM_SCALE_DURATION = 220L; // 聚焦缩放时长，短且不带回弹更顺
    /** 当前选中卡片的位置，弧顶跟随它移动 */
    private int mArcFocusPos = 0;
    private TextView mHeroTitle;
    private TextView mHeroNote;
    private TextView mHeroMeta;
    private TextView mHeroDesc;
    private TextView mHomeTitle;
    private TextView mHomeSub;
    private ExecutorService mExecutorService;
    private SourceViewModel mSourceViewModel;

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
        mSourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
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
            //首页卡片点击统一进聚合搜索：不管有没有 id，都拿片名去搜
            if (vod.name == null || vod.name.isEmpty()) {
                return;
            }
            Intent newIntent = new Intent(mContext, SearchActivity.class);
            newIntent.putExtra("title", vod.name);
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mActivity.startActivity(newIntent);
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
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(ITEM_SCALE_DURATION)
                        .setInterpolator(new DecelerateInterpolator(1.8f)).start();
                applyArcLayout();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.08f).scaleY(1.08f).setDuration(ITEM_SCALE_DURATION)
                        .setInterpolator(new DecelerateInterpolator(1.8f)).start();
                mArcFocusPos = position;
                applyArcLayout();
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
        // 滚动过程中实时重算弧线，卡片才会顺着弧面前后滑动而不是整排平移
        mHotGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                applyArcLayout();
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                applyArcLayout();
            }
        });
        mHotGridView.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                oldLeft, oldTop, oldRight, oldBottom) -> applyArcLayout());
        mHotGridView.setAdapter(mAdapter);
        mHotGridView.post(this::applyArcLayout);
    }

    /**
     * 把当前可见的卡片摆到一条弧线上：弧顶落在当前选中的卡片上，
     * 选中的卡最高最亮，越往两端越低、越暗、略微向外翻转；
     * 归一化基准取「卡片中心到弧顶的距离 / 列表半宽的比例」，
     * 贴到列表两端时弧线最深，屏幕宽度或卡片数量变化都不影响弧线的深浅。
     */
    private void applyArcLayout() {
        if (mHotGridView == null || mAdapter == null) {
            return;
        }
        int childCount = mHotGridView.getChildCount();
        int width = mHotGridView.getWidth();
        if (childCount < 2 || width == 0) {
            return;
        }
        float centerX = width / 2f;
        float apexX = centerX;
        int paddingTop = mHotGridView.getPaddingTop();
        // 弧顶跟随选中的卡片：选中的卡始终是弧线最高点，两侧依次落下
        for (int i = 0; i < childCount; i++) {
            View child = mHotGridView.getChildAt(i);
            if (child != null && child.getWidth() > 0
                    && mHotGridView.getChildAdapterPosition(child) == mArcFocusPos) {
                apexX = child.getLeft() + child.getWidth() / 2f;
                break;
            }
        }
        for (int i = 0; i < childCount; i++) {
            View child = mHotGridView.getChildAt(i);
            if (child == null || child.getWidth() == 0 || isLoadMoreView(child)) {
                continue;
            }
            float childCenter = child.getLeft() + child.getWidth() / 2f;
            // 只按列表自身的可视范围归一，避免列表预先布局的屏外卡片把弧线摊平；
            // 到列表两端附近即达到最大下沉，中段也能看出明显的弧度
            float offset = Math.max(0f, Math.min(width, childCenter)) - apexX;
            float t = Math.min(1f, Math.abs(offset) / (centerX * ARC_REACH_RATIO));
            float curve = t * t; // 二次曲线：弧顶附近平缓，越往外变化越快，弧线更自然
            float drop = ARC_DROP_RATIO * child.getHeight() * curve;
            // 用 translationY 把卡片对到「弧线应有的位置」：弧顶固定在行的上方，
            // 两端依次下沉；下沉量始终在列表自身余量内，不会顶到上面的文字
            child.setTranslationY(paddingTop + drop - child.getTop());
            child.setRotationY(ARC_ROTATION_Y * curve * (offset < 0 ? -1f : 1f));
            child.setAlpha(1f - (1f - ARC_MIN_ALPHA) * curve);
        }
    }

    /** 底部「加载更多」footer 不参与弧线排布，否则会跟着一起越位 */
    private boolean isLoadMoreView(View child) {
        int position = mHotGridView != null ? mHotGridView.getChildAdapterPosition(child) : -1;
        return position >= mAdapter.getData().size();
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
        mHeroHandler.removeCallbacks(mHeroImageRun);
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
        L.w("homeFeed start homeSource=" + homeSource
                + " recCount=" + (mRecommendVideos == null ? 0 : mRecommendVideos.size()));
        if (homeSource == 1) {
            //网站推荐
            int recCount = mRecommendVideos == null ? 0 : mRecommendVideos.size();
            setHeader("网站推荐", recCount > 0 ? "共 " + recCount + " 部" : "当前接口暂无推荐");
            showSuccess();
            if (recCount > 0) {
                mAdapter.setNewData(mRecommendVideos);
                bindHero(mRecommendVideos.get(0));
            } else {
                //当前数据源的推荐也可能为空（豆瓣系被风控时会这样），必须走同一条兜底链，不能留白页
                loadHotRankFeed();
            }
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
            showSuccess();
            if (vodList.isEmpty()) {
                //没有播放记录也不留白页，同样走兜底链
                mAdapter.setNewData(vodList);
                loadHotRankFeed();
            } else {
                mAdapter.setNewData(vodList);
                bindHero(vodList.get(0));
            }
        } else {
            //豆瓣推荐固定放在第一位；豆瓣被风控或断网时，loadDoubanRecommend 内部会依次
            //回落到本地缓存 → 主流热榜 → 站点推荐 → 聚合源，保证首页不留白
            showLoading();
            loadDoubanRecommend();
        }
    }

    /**
     * 豆瓣推荐层：主页默认就是这一层，直连取不到就用上次成功的本地缓存；
     * 连缓存都没有才回落到热榜 / 站点推荐 / 聚合源，始终不留白页。
     * 豆瓣按出口 IP 风控（返回「检测到有异常请求从您的IP发出」），一旦命中就冷却几分钟不再直连。
     */
    private void loadDoubanRecommend() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                int year = DateUtil.getLastNYear(0);
                List<Movie.Video> list0 = null;
                if (isDoubanBlocked()) {
                    //冷却期内不再碰豆瓣接口，直接用缓存，省得把限流期越砸越长
                    L.w("homeFeed douban skip: blocked");
                } else {
                    String doubanUrl = UiConstans.DOU_BAN_URL + (year - 1) + "," + year + "&start=" + 0;
                    //超时压到 10 秒：豆瓣挂起时尽快收尾，别让用户对着转圈等
                    String result = ApiConfig.getWithHeaders(doubanUrl, doubanHeaders(), 10 * 1000);
                    boolean risk = result != null && result.contains("\"msg\"");
                    if (risk) {
                        sDoubanBlockedUntil = System.currentTimeMillis() + DOU_BAN_BLOCK_COOLDOWN_MS;
                    }
                    list0 = parseJson(result);
                    L.w("homeFeed douban len=" + (result == null ? -1 : result.length())
                            + " risk=" + risk
                            + " parsed=" + (list0 == null ? -1 : list0.size()));
                }
                final boolean fresh = !NullUtil.isNull(list0) && !list0.isEmpty();
                mDobanVideos.clear();
                if (fresh) {
                    //成功一次就缓存，豆瓣按 IP 限流（时通时断）时用它兜底
                    saveDoubanCache(list0);
                    mDobanVideos.addAll(list0);
                } else {
                    List<Movie.Video> cache = loadDoubanCache();
                    L.w("homeFeed douban cache=" + (cache == null ? 0 : cache.size()));
                    if (cache != null && !cache.isEmpty()) {
                        mDobanVideos.addAll(cache);
                    }
                }
                mActivity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (mDobanVideos.isEmpty()) {
                        //豆瓣直连与缓存都拿不到：回落到热榜 / 站点推荐 / 聚合源，依然不留白页
                        loadHotRankFeed();
                        return;
                    }
                    showSuccess();
                    setHeader("豆瓣推荐", "豆瓣 · " + (year - 1) + "-" + year + " 评分排序"
                            + (fresh ? "" : "（离线缓存）"));
                    mAdapter.setNewData(mDobanVideos);
                    bindHero(mDobanVideos.get(0));
                });
            }
        }.start();
    }

    /** 豆瓣按出口 IP 风控：带上来源页 Referer 和固定的 bid Cookie，降低被判定为异常请求的概率 */
    private Map<String, String> doubanHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://movie.douban.com/");
        headers.put("Cookie", "bid=" + doubanBid());
        return headers;
    }

    /** bid 是豆瓣的身份串，首次随机生成后固定保存，每次换新身份反而更容易被判异常请求 */
    private String doubanBid() {
        String bid = Hawk.get(HawkConfig.DOU_BAN_BID, "");
        if (NullUtil.isNull(bid)) {
            String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            StringBuilder sb = new StringBuilder();
            java.util.Random random = new java.util.Random();
            for (int i = 0; i < 11; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            bid = sb.toString();
            Hawk.put(HawkConfig.DOU_BAN_BID, bid);
        }
        return bid;
    }

    private boolean isDoubanBlocked() {
        return System.currentTimeMillis() < sDoubanBlockedUntil;
    }

    private static final String HOT_RANK_URL = "https://api.bilibili.com/pgc/web/rank/list?day=3&season_type=";
    //热榜分类优先级：电视剧 → 电影 → 番剧 → 国创，取第一个有数据的
    private static final String[] HOT_RANK_TYPES = {"5", "2", "1", "4"};
    private static final int HOT_RANK_MAX = 40;

    /**
     * 主流热榜层（B站 PGC 影视/剧集榜）：片名干净、海报由图床直出，作为首页推荐的首选层。
     * 拿不到才回落到「数据源推荐 → 聚合源 → 豆瓣」这条链
     */
    private void loadHotRankFeed() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                for (String type : HOT_RANK_TYPES) {
                    List<Movie.Video> list = null;
                    try {
                        String json = HttpDoRequest.getInstance(getContext())
                                .doGetRequestInOkGo(HOT_RANK_URL + type, null, true, 10 * 1000);
                        list = parseHotRank(json);
                    } catch (Throwable th) {
                        L.e(th);
                    }
                    L.w("homeFeed hotRank type=" + type + " -> " + (list == null ? -1 : list.size()));
                    if (list != null && !list.isEmpty()) {
                        final List<Movie.Video> videos = list;
                        mActivity.runOnUiThread(() -> {
                            if (!isAdded()) return;
                            showFallbackVideos("热门榜单", "正在热播 · 排行榜实时更新", videos);
                        });
                        return;
                    }
                }
                //热榜也没拿到，回到原有兜底链
                mActivity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    showRecommendFallback();
                });
            }
        }.start();
    }

    /**
     * 解析 B站 PGC 榜单：result.list[] 里的 title 是片名、cover 是海报，
     * cover 有 http 的必须换成 https，否则被 cleartext 策略拦掉不显示
     */
    private List<Movie.Video> parseHotRank(String json) {
        List<Movie.Video> videos = new ArrayList<>();
        if (NullUtil.isNull(json)) return videos;
        try {
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
            if (obj == null || !obj.has("result") || obj.get("result").isJsonNull()) return videos;
            JsonObject result = obj.getAsJsonObject("result");
            if (result == null || !result.has("list") || result.get("list").isJsonNull()) return videos;
            JsonArray arr = result.getAsJsonArray("list");
            for (int i = 0; i < arr.size() && videos.size() < HOT_RANK_MAX; i++) {
                JsonElement el = arr.get(i);
                if (el == null || !el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String title = o.has("title") && !o.get("title").isJsonNull() ? o.get("title").getAsString() : null;
                if (NullUtil.isNull(title)) continue;
                Movie.Video vod = new Movie.Video();
                vod.name = title;
                vod.note = "热榜 TOP" + (i + 1);
                if (o.has("cover") && !o.get("cover").isJsonNull()) {
                    String cover = o.get("cover").getAsString();
                    vod.pic = cover.startsWith("http://") ? "https://" + cover.substring(7) : cover;
                }
                videos.add(vod);
            }
        } catch (Throwable th) {
            L.e(th);
        }
        return videos;
    }

    /**
     * 首页推荐兜底链：当前数据源推荐 → 配置里其它聚合源 → 豆瓣（直连/缓存） → 空态
     */
    private void showRecommendFallback() {
        L.w("homeFeed fallback recCount=" + (mRecommendVideos == null ? 0 : mRecommendVideos.size()));
        if (mRecommendVideos != null && !mRecommendVideos.isEmpty()) {
            showFallbackVideos("网站推荐", "已自动匹配可用的数据源", mRecommendVideos);
            return;
        }
        fillFromOtherSource();
    }

    private void showFallbackVideos(String title, String subTitle, List<Movie.Video> videos) {
        //兜底链拿到内容也必须收起 LoadSir 的转圈，否则卡片被加载层遮住
        showSuccess();
        setHeader(title, subTitle);
        mAdapter.setNewData(videos);
        //兜底列表已是一次性全量，不能再按豆瓣分页续拉
        mAdapter.setEnableLoadMore(false);
        //列表换数据后控件可能停在上一份数据的滚动位置，强制回到第一张
        mHotGridView.setSelection(0);
        bindHero(videos.get(0));
    }

    /**
     * 当前数据源（豆瓣系）也取不到时，换配置里别的源来取，保证首页有内容：
     * 优先 http api 源（出图最稳），再试 csp 源；跳过工具类站点（云盘/盘搜没有海报墙）
     */
    private void fillFromOtherSource() {
        List<SiteBean> all = ApiConfig.getInstance().getSiteBeanList();
        SiteBean home = ApiConfig.getInstance().getHomeBean();
        String homeKey = home == null ? "" : home.getKey();
        List<SiteBean> candidates = new ArrayList<>();
        for (int pass = 0; pass < 2 && candidates.isEmpty(); pass++) {
            for (SiteBean bean : all) {
                if (bean == null || NullUtil.isNull(bean.getKey())) continue;
                if (bean.getKey().equals(homeKey) || "豆瓣".equals(bean.getKey())) continue;
                if (!bean.isSearchable()) continue;
                boolean httpApi = bean.getType() == 0 || bean.getType() == 1;
                if ((pass == 0) != httpApi) continue;
                candidates.add(bean);
                if (candidates.size() >= 4) break;
            }
        }
        StringBuilder keys = new StringBuilder();
        for (SiteBean bean : candidates) {
            keys.append(bean.getKey()).append("(type=").append(bean.getType()).append(") ");
        }
        L.w("homeFeed candidates=" + candidates.size() + " [" + keys + "] homeKey=" + homeKey);
        trySource(candidates, 0);
    }

    private void trySource(final List<SiteBean> candidates, final int index) {
        if (index >= candidates.size()) {
            L.w("homeFeed all sources empty, fallback to douban");
            mActivity.runOnUiThread(() -> {
                if (!isAdded()) return;
                //聚合源全军覆没，最后一层回落豆瓣（直连→本地缓存），仍无内容才显示空态
                loadDoubanRecommend();
            });
            return;
        }
        final SiteBean bean = candidates.get(index);
        L.w("homeFeed try#" + index + " key=" + bean.getKey() + " type=" + bean.getType());
        mSourceViewModel.getHomeRecList(bean, new ArrayList<String>(), new SourceViewModel.HomeRecCallback() {
            @Override
            public void done(List<Movie.Video> videos) {
                L.w("homeFeed try#" + index + " -> " + (videos == null ? -1 : videos.size()));
                if (videos != null && !videos.isEmpty()) {
                    mActivity.runOnUiThread(() -> {
                        if (!isAdded()) return;
                        showFallbackVideos(bean.getName() + " 推荐", "已自动匹配可用的数据源", videos);
                    });
                } else {
                    trySource(candidates, index + 1);
                }
            }
        });
    }

    private void saveDoubanCache(List<Movie.Video> videos) {
        try {
            Hawk.put(HawkConfig.HOME_DOUBAN_CACHE, new Gson().toJson(videos));
        } catch (Throwable th) {
            L.e(th);
        }
    }

    /**
     * Hawk 存对象会丢泛型元素类型（读回变成 LinkedTreeMap，取用时 ClassCastException 闪退），所以统一存 JSON 字符串
     */
    private List<Movie.Video> loadDoubanCache() {
        try {
            String json = Hawk.get(HawkConfig.HOME_DOUBAN_CACHE, "");
            if (NullUtil.isNull(json)) return null;
            List<Movie.Video> cache = new Gson().fromJson(json, new TypeToken<List<Movie.Video>>() {
            }.getType());
            return (cache == null || cache.isEmpty()) ? null : cache;
        } catch (Throwable th) {
            L.e(th);
            return null;
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
     * 用当前焦点节目的海报与信息刷新背景 hero 区：
     * 文案立即跟进卡片（开销小），海报则排队等焦点停稳后加载
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
        applyHeroInfo(vod);
        mHeroHandler.removeCallbacks(mHeroImageRun);
        if (NullUtil.isNull(vod.pic)) {
            return;
        }
        mHeroHandler.postDelayed(mHeroImageRun, HERO_IMAGE_SETTLE_DELAY);
    }

    /** 更新 hero 区的文字信息（片名、备注、元信息、简介） */
    private void applyHeroInfo(Movie.Video vod) {
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
    }

    /** 真正加载背景海报：先给一张缩略图让背景立刻跟着卡片变，高清图解码好后再原地替换 */
    private void loadHeroImage(Movie.Video vod) {
        if (vod == null || NullUtil.isNull(vod.pic) || mHeroBackdrop == null || mHeroBackdropAlt == null) {
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
                //缩略图先到，背景不用等高清解码完才跟着卡片变
                .thumbnail(0.2f)
                .placeholder(R.drawable.shape_home_hero_placeholder)
                .error(R.drawable.shape_home_hero_placeholder)
                .dontAnimate()
                //豆瓣最大只有 1080px 宽，全屏背景要放大 2 倍多，补一次锐化找回边缘
                .bitmapTransform(new SharpenTransformation(incoming.getContext()))
                .listener(new RequestListener<Object, GlideDrawable>() {
                    @Override
                    public boolean onException(Exception e, Object model, Target<GlideDrawable> target, boolean isFirstResource) {
                        swapHeroOnce(seq, incoming, outgoing, false);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(GlideDrawable resource, Object model, Target<GlideDrawable> target,
                                                   boolean isFromMemoryCache, boolean isFirstResource) {
                        swapHeroOnce(seq, incoming, outgoing, true);
                        return false;
                    }
                })
                .into(incoming);
    }

    /**
     * 一张海报的缩略图与高清图会各回调一次，同一序号只淡入一次；
     * 高清图随后由 Glide 直接替换到当前图层上（无动画），因此不会重播淡入
     */
    private void swapHeroOnce(int seq, ImageView incoming, ImageView outgoing, boolean zoom) {
        if (seq != mHeroSeq || mHeroSwappedSeq == seq) {
            return;
        }
        mHeroSwappedSeq = seq;
        swapHero(incoming, outgoing, zoom);
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
                //豆瓣按出口 IP 限流：正处于冷却期或已翻到上限就直接收尾，不再砸接口
                if (isDoubanBlocked() || start > DOU_BAN_MAX_START) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.loadMoreEnd();
                        }
                    });
                    return;
                }
                int year = DateUtil.getLastNYear(0);
                String doubanUrl = UiConstans.DOU_BAN_URL + (year - 1) + "," + year + "&start=" + start;
                String result = ApiConfig.getWithHeaders(doubanUrl, doubanHeaders(), 10 * 1000);
                if (result != null && result.contains("\"msg\"")) {
                    sDoubanBlockedUntil = System.currentTimeMillis() + DOU_BAN_BLOCK_COOLDOWN_MS;
                }
                List<Movie.Video> listMore = parseJson(result);
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //UiHelper.showToast(getActivity(), doubanUrl + " result=" + result);
                        L.d(doubanUrl + " result=" + result);
                        if (NullUtil.isNull(listMore) || listMore.isEmpty()) {
                            //豆瓣被限流或已到底：结束本轮加载，避免继续砸接口加重限流
                            mAdapter.loadMoreEnd();
                            return;
                        }
                        mAdapter.addData(listMore);//解决焦点跳走的问题
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
            //豆瓣风控时会返回 {"msg":"检测到有异常请求从您的IP发出,请登录再试!","r":1}，没有 data 字段
            if (infoJson == null || !infoJson.has("data") || infoJson.get("data").isJsonNull()) {
                return videos;
            }
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