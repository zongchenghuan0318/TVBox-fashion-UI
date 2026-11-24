package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.AbsJson;
import com.github.tvbox.osc.bean.AbsSortJson;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.UiHelper;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.tv.widget.SafeTvLayoutManagers;
import com.github.tvbox.osc.util.AppExecutors;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.SeniorMode;
import com.github.tvbox.osc.util.SpiderActionHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.lcstudio.commonsurport.L;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.studio.osc.R;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;

/**
 * 网盘与配置中心浮层弹窗：
 * - 不影响首页固定 5 分类设计，专门展示配置中心/网盘类站点的原始分类与条目；
 * - 完整支持新版 vod_tag="action" 扫码弹窗协议、旧版 detailContent 触发扫码弹窗、
 *   以及个人网盘多级目录浏览与直接进详情播放。
 */
public class CloudDriveDialog extends BaseDialog {

    private static final TypeAdapter<Number> LENIENT_INT_ADAPTER = new TypeAdapter<Number>() {
        @Override
        public void write(JsonWriter out, Number value) throws IOException {
            out.value(value);
        }

        @Override
        public Number read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            if (token == JsonToken.NULL) {
                in.nextNull();
                return 0;
            }
            if (token == JsonToken.NUMBER) {
                try {
                    return in.nextInt();
                } catch (NumberFormatException e) {
                    return (int) in.nextDouble();
                }
            }
            if (token == JsonToken.STRING) {
                String raw = in.nextString();
                if (raw == null || raw.trim().isEmpty()) return 0;
                try {
                    return Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    try {
                        return (int) Double.parseDouble(raw.trim());
                    } catch (NumberFormatException ignored) {
                        return 0;
                    }
                }
            }
            if (token == JsonToken.BOOLEAN) {
                return in.nextBoolean() ? 1 : 0;
            }
            in.skipValue();
            return 0;
        }
    };

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(int.class, LENIENT_INT_ADAPTER)
            .registerTypeAdapter(Integer.class, LENIENT_INT_ADAPTER)
            .create();
    private static final Type ABS_JSON_TYPE = new TypeToken<AbsJson>() {}.getType();
    private static final Type ABS_SORT_JSON_TYPE = new TypeToken<AbsSortJson>() {}.getType();

    private static final class FolderLevel {
        final String title;
        final List<Movie.Video> videos;
        int savedPos;

        FolderLevel(String title, List<Movie.Video> videos) {
            this.title = title;
            this.videos = videos;
        }
    }

    private final Activity activity;
    private final TextView tvTitle;
    private final TextView tvSubtitle;
    private final View btnSwitchSite;
    private final View llStorageBanner;
    private final TvRecyclerView rvTabs;
    private final TvRecyclerView rvItems;
    private final View llLoading;
    private final TextView tvLoadingTip;
    private final View llEmpty;
    private final TextView tvEmptyTip;
    private final View btnRetry;

    private final TabAdapter tabAdapter = new TabAdapter();
    private final CardAdapter cardAdapter = new CardAdapter();
    private final Deque<FolderLevel> folderStack = new ArrayDeque<>();

    private List<SiteBean> allSpiderSites = new ArrayList<>();
    private SiteBean currentSite;
    private List<MovieSort.SortData> currentCategories = new ArrayList<>();
    private int selectedTabIndex = -1;
    private int requestGeneration = 0;
    private boolean cloudConfigTouched = false;

    public CloudDriveDialog(@NonNull Activity activity) {
        super(activity, R.style.CustomDialogStyleDim);
        this.activity = activity;
        setContentView(R.layout.dialog_cloud_drive);
        setCanceledOnTouchOutside(false);

        tvTitle = findViewById(R.id.tvCloudTitle);
        tvSubtitle = findViewById(R.id.tvCloudSubtitle);
        btnSwitchSite = findViewById(R.id.btnCloudSwitchSite);
        llStorageBanner = findViewById(R.id.llCloudStorageBanner);
        rvTabs = findViewById(R.id.rvCloudTabs);
        rvItems = findViewById(R.id.rvCloudItems);
        llLoading = findViewById(R.id.llCloudLoading);
        tvLoadingTip = findViewById(R.id.tvCloudLoadingTip);
        llEmpty = findViewById(R.id.llCloudEmpty);
        tvEmptyTip = findViewById(R.id.tvCloudEmptyTip);
        btnRetry = findViewById(R.id.btnCloudRetry);

        findViewById(R.id.btnCloudBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnCloudClose).setOnClickListener(v -> dismiss());
        btnSwitchSite.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            openSitePicker();
        });
        btnRetry.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            reloadCurrentView();
        });
        findViewById(R.id.btnCloudGrantStorage).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            requestStoragePermission();
        });

        setupTabs();
        setupGrid();
        refreshStorageBanner();
        initSites();
    }

    private void setupTabs() {
        rvTabs.setLayoutManager(SafeTvLayoutManagers.linear(activity, 0, false));
        rvTabs.setAdapter(tabAdapter);
        tabAdapter.setOnItemClickListener((adp, view, position) -> {
            FastClickCheckUtil.check(view);
            selectCategoryTab(position);
        });
        rvTabs.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                selectCategoryTab(position);
            }
        });
    }

    private void setupGrid() {
        int cols = SeniorMode.isEnabled() ? 3 : 4;
        rvItems.setHasFixedSize(true);
        rvItems.setItemAnimator(null);
        rvItems.setLayoutManager(SafeTvLayoutManagers.grid(activity, cols));
        rvItems.setAdapter(cardAdapter);
        rvItems.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null && !SeniorMode.isEnabled()) {
                    itemView.animate().cancel();
                    itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(130).start();
                }
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                FolderLevel top = folderStack.peek();
                if (top != null && position >= 0) {
                    top.savedPos = position;
                }
                if (itemView != null && !SeniorMode.isEnabled()) {
                    itemView.animate().cancel();
                    itemView.animate().scaleX(1.03f).scaleY(1.03f).setDuration(130).start();
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                onCardClicked(position);
            }
        });
        cardAdapter.setOnItemClickListener((adp, view, position) -> {
            FastClickCheckUtil.check(view);
            onCardClicked(position);
        });
    }

    private void refreshStorageBanner() {
        try {
            boolean granted = XXPermissions.isGranted(activity, Permission.Group.STORAGE);
            llStorageBanner.setVisibility(granted ? View.GONE : View.VISIBLE);
        } catch (Throwable ignored) {
            llStorageBanner.setVisibility(View.GONE);
        }
    }

    private void requestStoragePermission() {
        try {
            XXPermissions.with(activity)
                    .permission(Permission.Group.STORAGE)
                    .request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            refreshStorageBanner();
                            Toast.makeText(activity, "已授予存储权限，网盘登录态可正常保存", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            refreshStorageBanner();
                            if (never) {
                                Toast.makeText(activity, "请在系统设置中开启存储权限", Toast.LENGTH_SHORT).show();
                                XXPermissions.startPermissionActivity(activity, permissions);
                            } else {
                                Toast.makeText(activity, "未授予存储权限", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } catch (Throwable th) {
            L.w("requestStoragePermission " + th.getMessage());
        }
    }

    private void initSites() {
        allSpiderSites = SpiderActionHelper.getAllSpiderSites();
        List<SiteBean> recommended = SpiderActionHelper.getRecommendedCloudSites();
        btnSwitchSite.setVisibility(allSpiderSites.size() > 1 ? View.VISIBLE : View.GONE);
        if (!recommended.isEmpty()) {
            currentSite = recommended.get(0);
        } else if (!allSpiderSites.isEmpty()) {
            currentSite = allSpiderSites.get(0);
        }
    }

    @Override
    public void show() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        super.show();
        if (currentSite == null) {
            showEmptyState("当前点播接口里没有可用的网盘或爬虫配置站点，请先在「配置接口」里切换支持网盘的配置源。");
            return;
        }
        loadSiteHome(currentSite);
    }

    @Override
    public void dismiss() {
        requestGeneration++;
        if (cloudConfigTouched) {
            cloudConfigTouched = false;
            ApiConfig.getInstance().resetSpiders();
        }
        super.dismiss();
    }

    @Override
    public void onBackPressed() {
        if (!folderStack.isEmpty()) {
            folderStack.pop();
            if (!folderStack.isEmpty()) {
                FolderLevel parent = folderStack.peek();
                renderCards(parent.videos, parent.title, parent.savedPos);
                return;
            }
            if (selectedTabIndex >= 0 && selectedTabIndex < currentCategories.size()) {
                loadCategory(currentSite, currentCategories.get(selectedTabIndex));
            } else if (currentSite != null) {
                loadSiteHome(currentSite);
            }
            return;
        }
        super.onBackPressed();
    }

    private void openSitePicker() {
        if (allSpiderSites.isEmpty()) {
            Toast.makeText(activity, "当前接口没有可切换的爬虫站点", Toast.LENGTH_SHORT).show();
            return;
        }
        int selectedIndex = 0;
        if (currentSite != null) {
            for (int i = 0; i < allSpiderSites.size(); i++) {
                if (TextUtils.equals(allSpiderSites.get(i).getKey(), currentSite.getKey())) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        SelectDialog.pick(activity, "选择网盘 / 配置站点",
                "优先列出配置中心与网盘相关站点，也可切换任意爬虫站点",
                allSpiderSites, selectedIndex,
                new SelectDialogAdapter.SelectDialogInterface<SiteBean>() {
                    @Override
                    public void click(SiteBean value, int pos) {
                        if (value == null) return;
                        currentSite = value;
                        folderStack.clear();
                        loadSiteHome(value);
                    }

                    @Override
                    public String getDisplay(SiteBean val) {
                        return val.getName();
                    }

                    @Override
                    public String getSubDisplay(SiteBean val) {
                        int priority = SpiderActionHelper.getCloudSitePriority(val);
                        if (priority >= 3) return "配置中心 / 网盘登录入口";
                        if (priority == 2) return "工具 / 个人云盘站点";
                        if (priority == 1) return "网盘资源站点";
                        return "爬虫站点";
                    }
                });
    }

    private void reloadCurrentView() {
        if (currentSite == null) return;
        folderStack.clear();
        if (selectedTabIndex >= 0 && selectedTabIndex < currentCategories.size()) {
            loadCategory(currentSite, currentCategories.get(selectedTabIndex));
        } else {
            loadSiteHome(currentSite);
        }
    }

    private void loadSiteHome(SiteBean site) {
        if (site == null) return;
        final int gen = ++requestGeneration;
        folderStack.clear();
        currentCategories = new ArrayList<>();
        selectedTabIndex = -1;
        tabAdapter.setSelectedIndex(-1);
        tabAdapter.setNewData(new ArrayList<>());
        rvTabs.setVisibility(View.GONE);

        tvTitle.setText(TextUtils.isEmpty(site.getName()) ? "网盘与配置中心" : site.getName());
        tvSubtitle.setText("正在连接站点获取配置项…");
        showLoadingState("正在加载「" + site.getName() + "」…");

        AppExecutors.io().execute(() -> {
            List<MovieSort.SortData> categories = new ArrayList<>();
            List<Movie.Video> videos = new ArrayList<>();
            String errorMsg = null;
            try {
                Spider sp = ApiConfig.getInstance().getCSP(site);
                if (sp == null) {
                    errorMsg = "站点爬虫初始化失败";
                } else {
                    String homeJson = sp.homeContent(true);
                    if (!TextUtils.isEmpty(homeJson)) {
                        categories = parseCategories(homeJson, site.getKey());
                        videos = parseVideos(homeJson, site.getKey());
                    }
                    if (videos.isEmpty()) {
                        String homeVodJson = sp.homeVideoContent();
                        if (!TextUtils.isEmpty(homeVodJson)) {
                            videos = parseVideos(homeVodJson, site.getKey());
                        }
                    }
                    if (videos.isEmpty() && !categories.isEmpty()) {
                        String firstCatJson = sp.categoryContent(categories.get(0).id, "1", true, new HashMap<>());
                        if (!TextUtils.isEmpty(firstCatJson)) {
                            videos = parseVideos(firstCatJson, site.getKey());
                        }
                    }
                }
            } catch (Throwable th) {
                errorMsg = th.getMessage();
                L.w("CloudDriveDialog loadSiteHome err=" + th.getMessage());
            }

            final List<MovieSort.SortData> finalCategories = categories;
            final List<Movie.Video> finalVideos = videos;
            final String finalErr = errorMsg;
            activity.runOnUiThread(() -> {
                if (!isShowing() || activity.isFinishing() || activity.isDestroyed() || gen != requestGeneration) {
                    return;
                }
                currentCategories = finalCategories;
                if (!finalCategories.isEmpty()) {
                    rvTabs.setVisibility(View.VISIBLE);
                    selectedTabIndex = 0;
                    tabAdapter.setSelectedIndex(0);
                    tabAdapter.setNewData(new ArrayList<>(finalCategories));
                } else {
                    rvTabs.setVisibility(View.GONE);
                }

                if (!finalVideos.isEmpty()) {
                    renderCards(finalVideos, site.getName(), 0);
                } else if (!finalCategories.isEmpty()) {
                    loadCategory(site, finalCategories.get(0));
                } else {
                    String tip = TextUtils.isEmpty(finalErr)
                            ? "「" + site.getName() + "」暂未返回可展示的配置项，可尝试切换其他站点"
                            : "加载失败：" + finalErr;
                    showEmptyState(tip);
                }
            });
        });
    }

    private void selectCategoryTab(int position) {
        if (currentSite == null || position < 0 || position >= currentCategories.size()) return;
        folderStack.clear();
        selectedTabIndex = position;
        tabAdapter.setSelectedIndex(position);
        loadCategory(currentSite, currentCategories.get(position));
    }

    private void loadCategory(SiteBean site, MovieSort.SortData category) {
        if (site == null || category == null) return;
        final int gen = ++requestGeneration;
        tvTitle.setText(site.getName());
        tvSubtitle.setText(category.name + " · 正在加载…");
        showLoadingState("正在加载「" + category.name + "」…");

        AppExecutors.io().execute(() -> {
            List<Movie.Video> videos = new ArrayList<>();
            String errorMsg = null;
            try {
                Spider sp = ApiConfig.getInstance().getCSP(site);
                if (sp != null) {
                    String json = sp.categoryContent(category.id, "1", true, new HashMap<>());
                    if (!TextUtils.isEmpty(json)) {
                        videos = parseVideos(json, site.getKey());
                    }
                }
            } catch (Throwable th) {
                errorMsg = th.getMessage();
                L.w("CloudDriveDialog loadCategory err=" + th.getMessage());
            }

            final List<Movie.Video> finalVideos = videos;
            final String finalErr = errorMsg;
            activity.runOnUiThread(() -> {
                if (!isShowing() || activity.isFinishing() || activity.isDestroyed() || gen != requestGeneration) {
                    return;
                }
                if (!finalVideos.isEmpty()) {
                    renderCards(finalVideos, site.getName() + " · " + category.name, 0);
                } else {
                    showEmptyState(TextUtils.isEmpty(finalErr)
                            ? "「" + category.name + "」下暂无内容，可切换其他分类或站点"
                            : "加载失败：" + finalErr);
                }
            });
        });
    }

    private void onCardClicked(int position) {
        if (position < 0 || position >= cardAdapter.getData().size()) return;
        Movie.Video video = cardAdapter.getData().get(position);
        if (video == null) return;

        // 1. 新版 action 协议：直接执行动作或弹出二维码/输入/菜单弹窗
        if (SpiderActionHelper.isActionItem(video)) {
            cloudConfigTouched = true;
            SpiderActionHelper.handleVideoAction(activity, video, this::refreshAfterAction);
            return;
        }

        // 2. 目录项（如个人云盘的文件夹）
        if (SearchActivity.isFolderItem(video)) {
            openNestedFolder(video);
            return;
        }

        // 3. 普通卡片：对配置类站点先走一次 detailContent，兼容老版 JAR 在详情里直接弹扫码框或返回子目录
        if (currentSite != null && SpiderActionHelper.getCloudSitePriority(currentSite) >= 2) {
            cloudConfigTouched = true;
            openConfigOrDetailItem(video);
            return;
        }

        openDetailActivity(video, null);
    }

    private void openNestedFolder(Movie.Video folderVideo) {
        final SiteBean site = currentSite;
        if (site == null || TextUtils.isEmpty(folderVideo.id)) return;
        final int gen = ++requestGeneration;
        showLoadingState("正在打开「" + folderVideo.name + "」…");

        AppExecutors.io().execute(() -> {
            List<Movie.Video> children = new ArrayList<>();
            try {
                Spider sp = ApiConfig.getInstance().getCSP(site);
                if (sp != null) {
                    String json = sp.categoryContent(folderVideo.id, "1", false, new HashMap<>());
                    children = parseVideos(json, site.getKey());
                    if (children.isEmpty()) {
                        String detailJson = sp.detailContent(new ArrayList<>(Collections.singletonList(folderVideo.id)));
                        children = parseVideos(detailJson, site.getKey());
                    }
                }
            } catch (Throwable th) {
                L.w("CloudDriveDialog openNestedFolder err=" + th.getMessage());
            }
            final List<Movie.Video> finalChildren = children;
            activity.runOnUiThread(() -> {
                if (!isShowing() || activity.isFinishing() || activity.isDestroyed() || gen != requestGeneration) {
                    return;
                }
                if (finalChildren.isEmpty()) {
                    Toast.makeText(activity, "该目录暂无内容", Toast.LENGTH_SHORT).show();
                    if (!cardAdapter.getData().isEmpty()) {
                        showGridState();
                    } else {
                        showEmptyState("该目录暂无内容");
                    }
                    return;
                }
                String title = TextUtils.isEmpty(folderVideo.name) ? "云盘目录" : folderVideo.name;
                folderStack.push(new FolderLevel(title, finalChildren));
                renderCards(finalChildren, title, 0);
            });
        });
    }

    private void openConfigOrDetailItem(Movie.Video clicked) {
        final SiteBean site = currentSite;
        if (site == null || TextUtils.isEmpty(clicked.id)) return;
        final int gen = ++requestGeneration;
        showLoadingState("正在执行「" + clicked.name + "」…");

        AppExecutors.io().execute(() -> {
            List<Movie.Video> results = new ArrayList<>();
            try {
                Spider sp = ApiConfig.getInstance().getCSP(site);
                if (sp != null) {
                    String detailJson = sp.detailContent(new ArrayList<>(Collections.singletonList(clicked.id)));
                    if (!TextUtils.isEmpty(detailJson)) {
                        if (SpiderActionHelper.looksLikeActionJson(detailJson)) {
                            final String actionPayload = detailJson;
                            activity.runOnUiThread(() -> {
                                if (!isShowing() || activity.isFinishing() || activity.isDestroyed() || gen != requestGeneration) {
                                    return;
                                }
                                showGridState();
                                SpiderActionHelper.executeAction(activity, site.getKey(),
                                        actionPayload, clicked.name, this::refreshAfterAction);
                            });
                            return;
                        }
                        results = parseVideos(detailJson, site.getKey());
                    }
                }
            } catch (Throwable th) {
                L.w("CloudDriveDialog openConfigOrDetailItem err=" + th.getMessage());
            }

            final List<Movie.Video> finalResults = results;
            activity.runOnUiThread(() -> {
                if (!isShowing() || activity.isFinishing() || activity.isDestroyed() || gen != requestGeneration) {
                    return;
                }
                showGridState();
                if (finalResults.isEmpty()) {
                    // 老版 JAR 可能已在 detailContent 里自行弹出扫码框或处理完动作，不跳转空白详情页
                    return;
                }
                if (finalResults.size() == 1) {
                    Movie.Video single = finalResults.get(0);
                    if (SpiderActionHelper.isActionItem(single)) {
                        SpiderActionHelper.handleVideoAction(activity, single, this::refreshAfterAction);
                        return;
                    }
                    if (single.hasPlayableUrls) {
                        openDetailActivity(clicked, single);
                        return;
                    }
                    String info = !TextUtils.isEmpty(single.des) ? single.des : single.note;
                    if (!TextUtils.isEmpty(info)) {
                        Toast.makeText(activity, info.replaceAll("<[^>]+>", "").trim(), Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                String title = TextUtils.isEmpty(clicked.name) ? "子目录" : clicked.name;
                folderStack.push(new FolderLevel(title, finalResults));
                renderCards(finalResults, title, 0);
            });
        });
    }

    private void openDetailActivity(Movie.Video summary, Movie.Video preloadedDetail) {
        if (summary == null || TextUtils.isEmpty(summary.id)) return;
        Bundle bundle = new Bundle();
        if (preloadedDetail != null) {
            bundle.putSerializable("video", preloadedDetail);
        }
        bundle.putString("id", summary.id);
        bundle.putString("sourceKey", summary.sourceKey);
        bundle.putString("note", summary.note);
        bundle.putString("movieName", summary.name);
        bundle.putString("poster", summary.pic);
        Intent intent = new Intent(activity, DetailActivity.class);
        intent.putExtras(bundle);
        activity.startActivity(intent);
    }

    private void refreshAfterAction() {
        ApiConfig.getInstance().resetSpiders();
        if (!isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
        if (folderStack.isEmpty()) {
            reloadCurrentView();
        }
    }

    private void renderCards(List<Movie.Video> videos, String subtitlePrefix, int targetPos) {
        showGridState();
        cardAdapter.setNewData(new ArrayList<>(videos));
        boolean showScopeTip = folderStack.isEmpty() && currentSite != null
                && SpiderActionHelper.getCloudSitePriority(currentSite) >= 3;
        tvSubtitle.setText(subtitlePrefix + " · 共 " + videos.size() + " 项"
                + (showScopeTip ? "（登录仅对当前配置源生效）" : ""));
        final int safePos = Math.max(0, Math.min(targetPos, Math.max(0, videos.size() - 1)));
        rvItems.scrollToPosition(safePos);
        rvItems.post(() -> {
            if (!isShowing()) return;
            rvItems.setSelection(safePos);
            rvItems.requestFocus();
        });
    }

    private void showLoadingState(String tip) {
        llEmpty.setVisibility(View.GONE);
        rvItems.setVisibility(View.INVISIBLE);
        llLoading.setVisibility(View.VISIBLE);
        tvLoadingTip.setText(tip);
    }

    private void showGridState() {
        llLoading.setVisibility(View.GONE);
        llEmpty.setVisibility(View.GONE);
        rvItems.setVisibility(View.VISIBLE);
    }

    private void showEmptyState(String tip) {
        llLoading.setVisibility(View.GONE);
        rvItems.setVisibility(View.GONE);
        llEmpty.setVisibility(View.VISIBLE);
        tvEmptyTip.setText(tip);
        btnRetry.post(() -> {
            if (isShowing()) btnRetry.requestFocus();
        });
    }

    private static List<MovieSort.SortData> parseCategories(String json, String sourceKey) {
        List<MovieSort.SortData> result = new ArrayList<>();
        try {
            AbsSortJson sortJson = GSON.fromJson(JsonParser.parseString(json), ABS_SORT_JSON_TYPE);
            AbsSortXml xml = sortJson == null ? null : sortJson.toAbsSortXml();
            if (xml != null && xml.classes != null && xml.classes.sortList != null) {
                for (MovieSort.SortData item : xml.classes.sortList) {
                    if (item == null || TextUtils.isEmpty(item.id)) continue;
                    item.sourceKey = sourceKey;
                    item.available = true;
                    result.add(item);
                }
            }
        } catch (Throwable th) {
            L.w("CloudDriveDialog parseCategories err=" + th.getMessage());
        }
        return result;
    }

    private static List<Movie.Video> parseVideos(String json, String sourceKey) {
        List<Movie.Video> result = new ArrayList<>();
        if (TextUtils.isEmpty(json)) return result;
        try {
            AbsJson absJson = GSON.fromJson(json, ABS_JSON_TYPE);
            AbsXml absXml = absJson == null ? null : absJson.toAbsXml();
            if (absXml != null && absXml.movie != null && absXml.movie.videoList != null) {
                for (Movie.Video video : absXml.movie.videoList) {
                    if (video == null) continue;
                    video.sourceKey = sourceKey;
                    boolean playable = false;
                    if (video.urlsBean != null && video.urlsBean.flagInfoList != null) {
                        for (Movie.Video.UrlsBean.FlagUrlsInfo info : video.urlsBean.flagInfoList) {
                            if (info != null && !TextUtils.isEmpty(info.urls)) {
                                playable = true;
                                info.nameUrlBeans = UiHelper.getUrlsList(info.urls);
                            }
                        }
                    }
                    video.hasPlayableUrls = playable;
                    result.add(video);
                }
            }
        } catch (Throwable th) {
            L.w("CloudDriveDialog parseVideos err=" + th.getMessage());
        }
        return result;
    }

    private static final class TabAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
        private int selectedIndex = -1;

        TabAdapter() {
            super(R.layout.item_cloud_tab, new ArrayList<>());
        }

        void setSelectedIndex(int index) {
            int old = selectedIndex;
            selectedIndex = index;
            if (old >= 0 && old < getData().size()) notifyItemChanged(old);
            if (selectedIndex >= 0 && selectedIndex < getData().size()) notifyItemChanged(selectedIndex);
        }

        @Override
        protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
            TextView tv = helper.getView(R.id.tvCloudTabName);
            tv.setText(item.name);
            tv.setSelected(helper.getAdapterPosition() == selectedIndex);
        }
    }

    private final class CardAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
        CardAdapter() {
            super(R.layout.item_cloud_card, new ArrayList<>());
        }

        @Override
        protected void convert(BaseViewHolder helper, Movie.Video item) {
            helper.setText(R.id.tvCloudCardName, TextUtils.isEmpty(item.name) ? "未命名" : item.name);
            TextView tvNote = helper.getView(R.id.tvCloudCardNote);
            if (!TextUtils.isEmpty(item.note)) {
                tvNote.setText(item.note);
                tvNote.setVisibility(View.VISIBLE);
            } else if (SpiderActionHelper.isActionItem(item)) {
                tvNote.setText("按确定键配置或扫码");
                tvNote.setVisibility(View.VISIBLE);
            } else if (SearchActivity.isFolderItem(item)) {
                tvNote.setText("点击展开目录");
                tvNote.setVisibility(View.VISIBLE);
            } else {
                tvNote.setVisibility(View.GONE);
            }

            TextView tvBadge = helper.getView(R.id.tvCloudCardBadge);
            if (SpiderActionHelper.isActionItem(item)) {
                tvBadge.setText("扫码 / 配置");
                tvBadge.setTextColor(activity.getResources().getColor(R.color.settings_success));
                tvBadge.setVisibility(View.VISIBLE);
            } else if (SearchActivity.isFolderItem(item)) {
                tvBadge.setText("目录");
                tvBadge.setTextColor(activity.getResources().getColor(R.color.settings_info));
                tvBadge.setVisibility(View.VISIBLE);
            } else {
                tvBadge.setVisibility(View.GONE);
            }

            ImageView ivPic = helper.getView(R.id.ivCloudCardPic);
            if (!TextUtils.isEmpty(item.pic) && (item.pic.startsWith("http://") || item.pic.startsWith("https://"))) {
                Glide.with(activity)
                        .load(item.pic)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .placeholder(R.drawable.icon_setting)
                        .error(R.drawable.icon_setting)
                        .into(ivPic);
            } else {
                Glide.with(activity).clear(ivPic);
                ivPic.setImageResource(R.drawable.icon_setting);
            }
        }
    }
}
