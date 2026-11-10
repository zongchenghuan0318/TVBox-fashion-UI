package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.adapter.PinyinAdapter;
import com.github.tvbox.osc.ui.adapter.SearchHistoryAdapter;
import com.github.tvbox.osc.ui.adapter.SearchResultAdapter;
import com.github.tvbox.osc.util.PosterPreloader;
import com.github.tvbox.osc.ui.dialog.SearchSiteDialog;
import com.github.tvbox.osc.ui.tv.widget.SearchKeyboard;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.SPDataUtil;
import com.lcstudio.commonsurport.util.System_UI;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.studio.osc.R;
import com.uisupport.UiConstans;
import com.uisupport.update.HotWords;
import com.uisupport.update.HttpUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.DiffUtil;

public class SearchActivity extends BaseActivity {
    private static final String TRACE_TAG = "SearchRoute";
    private boolean folderMode;
    private int folderRequestId = -1;
    private View llLayout;
    private LinearLayout llWord;
    private LinearLayout llClearHistory;
    private TextView tvHistoryHint;
    private TvRecyclerView mSearchResultRV;
    private TvRecyclerView mHotWordRV;
    private TvRecyclerView mSearchHistoryRV;
    private SourceViewModel sourceViewModel;
    private EditText etSearch;
    private TextView tvSearch;
    private TextView tvSearchProgress;
    private TextView tvClear;
    private SearchKeyboard keyboard;
    private SearchResultAdapter searchResultAdapter;
    private View llSearchEmpty;
    private View btnEmptyManageSources;
    private PinyinAdapter wordAdapter;
    private SearchHistoryAdapter historyAdapter;
    private String searchWord = "";
    private SPDataUtil spDataUtil = null;
    public ExecutorService mExecutorService;
    private final List<Future<?>> searchTasks = new ArrayList<>();
    private final ExecutorService resultExecutor = Executors.newSingleThreadExecutor();
    private final List<Movie.Video> pendingResults = new ArrayList<>();
    private final Map<String, Integer> sourceOrder = new HashMap<>();
    private boolean resultMergeRunning;
    private boolean mergeScheduled;
    private int resultGeneration;
    private final Runnable mergeResults = this::mergePendingResults;
    private volatile int searchRequestId;
    private int searchSourceCount;
    private int completedSearchSources;
    private List<SiteBean> activeSearchSites = new ArrayList<>();
    private final Set<String> respondedSourceKeys = new HashSet<>();
    private final Map<String, Integer> sourceAttempts = new HashMap<>();
    private final List<Runnable> sourceRetryTasks = new ArrayList<>();
    private final Set<String> failedSourceKeys = new HashSet<>();
    private boolean retryPhase;
    private volatile boolean searchFinished;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Object suggestionTag = new Object();
    private final Object hotWordsTag = new Object();
    private int suggestionRequestId;
    private Runnable suggestionRunnable;
    private boolean suppressSuggestionWatcher;
    private static final long SUGGESTION_DEBOUNCE_MS = 250L;
    private static final int MAX_SEARCH_HISTORY = 20;
    private static final long SEARCH_TIMEOUT_MS = 45000L;
    private static final int MAX_SOURCE_ATTEMPTS = 3;
    private static final long SOURCE_RETRY_DELAY_MS = 2500L;
    private static final long RESULT_MERGE_DELAY_MS = 500L;
    private static final int MAX_SOURCE_RESULTS = 30;
    private static final int MAX_TOTAL_RESULTS = 200;
    private static final Pattern AGGREGATE_NOTE = Pattern.compile("(搜索|结果|资源)\\s*有?\\s*\\d+\\s*条");
    private final Runnable searchTimeout = new Runnable() {
        @Override
        public void run() {
            if (!searchFinished && searchResultAdapter != null) {
                beginRetryPhase();
                updateSearchProgress(true);
                if (!searchResultAdapter.getData().isEmpty() && completedSearchSources < searchSourceCount) {
                    Toast.makeText(SearchActivity.this, "首轮搜索完成，失败来源将在后台重试", Toast.LENGTH_SHORT).show();
                }
            }

        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_search;
    }

    @Override
    protected void init() {
        spDataUtil = new SPDataUtil(SearchActivity.this);
        // 扩大到 12 线程：避免多源并发时慢速源把所有槽位占满，饿死后续高速源
        int cores = Runtime.getRuntime().availableProcessors();
        mExecutorService = Executors.newFixedThreadPool(Math.min(12, Math.max(8, cores * 2)));
        initView();
        initViewModel();
        initData();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        initData();
    }

    private void initView() {
        EventBus.getDefault().register(this);
        ImageView backTv = findViewById(R.id.back_tv);
        if (backTv != null) {
            backTv.setOnClickListener(v -> finish());
        }
        llLayout = findViewById(R.id.llLayout);
        llWord = findViewById(R.id.llWord);
        etSearch = findViewById(R.id.etSearch);
        etSearch.setOnClickListener(v -> showSoftKeyboard(etSearch));
        tvSearch = findViewById(R.id.tvSearch);
        tvSearchProgress = findViewById(R.id.tvSearchProgress);
        tvClear = findViewById(R.id.tvClear);
        mSearchResultRV = findViewById(R.id.sortRV);
        keyboard = findViewById(R.id.keyBoardRoot);
        mHotWordRV = findViewById(R.id.mGridViewWord);
        mSearchHistoryRV = findViewById(R.id.serch_history_rv);
        llClearHistory = findViewById(R.id.llClearHistory);
        tvHistoryHint = findViewById(R.id.tvHistoryHint);
        llSearchEmpty = findViewById(R.id.llSearchEmpty);
        btnEmptyManageSources = findViewById(R.id.btnEmptyManageSources);
        if (btnEmptyManageSources != null) {
            btnEmptyManageSources.setOnClickListener(v -> {
                FastClickCheckUtil.check(v);
                new SearchSiteDialog(SearchActivity.this).show();
            });
        }
        initHistoryView();
        initHotRecycleView();
        initSearchResultRecycleView();

        mSearchResultRV.setOnTouchListener((v, event) -> {
            hideSoftKeyboard();
            return false;
        });
        mHotWordRV.setOnTouchListener((v, event) -> {
            hideSoftKeyboard();
            return false;
        });
        mSearchHistoryRV.setOnTouchListener((v, event) -> {
            hideSoftKeyboard();
            return false;
        });

        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchClick();
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                clearHistory();
            }
        });
        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                        actionId == EditorInfo.IME_ACTION_NEXT ||
                        actionId == EditorInfo.IME_ACTION_SEARCH ||
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    searchClick();
                    return true;
                }
                return false;
            }
        });
        keyboard.setOnSearchKeyListener(new SearchKeyboard.OnSearchKeyListener() {
            @Override
            public void onSearchKey(int pos, String key) {
                L.d(pos + " key = " + key);
                if (pos > 1) {//按键字母
                    String text = etSearch.getText().toString().trim();
                    text += key;
                    etSearch.setText(text);
                    etSearch.setSelection(text.length());
                } else if (pos == 1) {//删除按钮
                    String text = etSearch.getText().toString().trim();
                    if (text.length() > 0) {
                        text = text.substring(0, text.length() - 1);
                        etSearch.setText(text);
                    }
                    if (text.length() > 0) {
                        etSearch.setSelection(text.length());
                    }
                } else if (pos == 0) {//远程搜索
                    etSearch.setText("");
                }
            }
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (!suppressSuggestionWatcher) {
                    loadPinyinResult(editable.toString().trim());
                }
            }
        });
        setLoadSir(llLayout);
    }

    /**
     * 热词/联想列有内容才显示：没有内容时收起来，把宽度让给搜索结果
     */
    private void updateHotWordPanel() {
        if (llWord == null) {
            return;
        }
        boolean empty = wordAdapter == null || NullUtil.isNull(wordAdapter.getData());
        llWord.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void initSearchResultRecycleView() {
        mSearchResultRV.setHasFixedSize(true);
        if (Hawk.get(HawkConfig.SEARCH_VIEW, 1) == 0) {
            mSearchResultRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        } else {
            mSearchResultRV.setLayoutManager(            new V7GridLayoutManager(this.mContext,
                    com.github.tvbox.osc.util.SeniorMode.getGridColumnCount(3)));
        }
        searchResultAdapter = new SearchResultAdapter();
        mSearchResultRV.setAdapter(searchResultAdapter);
        PosterPreloader.setup(this, mSearchResultRV, new PosterPreloader.PosterProvider() {
            @Override
            public int getCount() {
                return searchResultAdapter != null ? searchResultAdapter.getData().size() : 0;
            }

            @Override
            public String getPosterUrl(int position) {
                if (searchResultAdapter != null && position >= 0 && position < searchResultAdapter.getData().size()) {
                    Movie.Video item = searchResultAdapter.getData().get(position);
                    return item != null ? item.pic : null;
                }
                return null;
            }
        }, 6);
        mSearchResultRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                openSearchResult(position);
            }
        });
        searchResultAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                openSearchResult(position);
            }
        });
    }

    private void openSearchResult(int position) {
        if (searchResultAdapter == null || position < 0 || position >= searchResultAdapter.getData().size()) return;
        Movie.Video video = searchResultAdapter.getData().get(position);
        if (video != null) {
            boolean opensFolder = isFolder(video) || isAggregateSearchCard(video);
            android.util.Log.i(TRACE_TAG, "click activity=" + System.identityHashCode(this)
                    + " position=" + position + " route=" + (opensFolder ? "folder" : "detail")
                    + " explicitFolder=" + isFolder(video)
                    + " hasTag=" + !TextUtils.isEmpty(video.tag)
                    + " hasPlayableUrls=" + hasPlayableUrls(video)
                    + " noteMatches=" + (!TextUtils.isEmpty(video.note) && AGGREGATE_NOTE.matcher(video.note).find()));
            if (opensFolder) {
                stopSearchRequests();
                cancelSuggestions();
                Bundle folder = new Bundle();
                folder.putString("folderId", video.id);
                folder.putString("folderSourceKey", video.sourceKey);
                folder.putString("title", video.name);
                jumpActivity(SearchActivity.class, folder);
                return;
            }
            stopSearchRequests();
            Bundle bundle = new Bundle();
            bundle.putString("id", video.id);
            bundle.putString("sourceKey", video.sourceKey);
            bundle.putString("note", video.note);
            bundle.putString("movieName", video.name);
            jumpActivity(DetailActivity.class, bundle);
        }
    }

    private boolean isFolder(Movie.Video video) {
        if (video == null) return false;
        return "folder".equalsIgnoreCase(video.tag) || "floder".equalsIgnoreCase(video.tag);
    }

    private boolean isAggregateSearchCard(Movie.Video video) {
        if (video == null || TextUtils.isEmpty(video.id)) return false;
        if ("aggregate".equalsIgnoreCase(video.tag)
                || "search".equalsIgnoreCase(video.tag)
                || "search_result".equalsIgnoreCase(video.tag)) {
            return true;
        }
        return !hasPlayableUrls(video)
                && !TextUtils.isEmpty(video.note)
                && AGGREGATE_NOTE.matcher(video.note).find();
    }

    private boolean hasPlayableUrls(Movie.Video video) {
        if (video == null || video.urlsBean == null || video.urlsBean.flagInfoList == null) {
            return false;
        }
        for (Movie.Video.UrlsBean.FlagUrlsInfo flag : video.urlsBean.flagInfoList) {
            if (flag != null && !TextUtils.isEmpty(flag.urls)) {
                return true;
            }
        }
        return false;
    }

    private void initHotRecycleView() {
        mHotWordRV.setHasFixedSize(true);
        mHotWordRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        wordAdapter = new PinyinAdapter();
        mHotWordRV.setAdapter(wordAdapter);
        mHotWordRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                selectHotWord(position);
            }
        });
        wordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                selectHotWord(position);
            }
        });
    }

    private void selectHotWord(int position) {
        if (wordAdapter == null || position < 0 || position >= wordAdapter.getData().size()) return;
        hideSoftKeyboard();
        search(wordAdapter.getItem(position));
    }

    private void initHistoryView() {
        mSearchHistoryRV.setHasFixedSize(true);
        mSearchHistoryRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, LinearLayoutManager.HORIZONTAL,
                false));
        historyAdapter = new SearchHistoryAdapter();
        mSearchHistoryRV.setAdapter(historyAdapter);
        mSearchHistoryRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                selectHistoryWord(position);
            }
        });
        historyAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                selectHistoryWord(position);
            }
        });
        historyAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            if (position >= 0 && position < historyAdapter.getData().size()) {
                String item = historyAdapter.getItem(position);
                deleteSingleHistoryItem(item);
            }
            return true;
        });
        refreshHistoryView();
    }

    private void selectHistoryWord(int position) {
        if (historyAdapter == null || position < 0 || position >= historyAdapter.getData().size()) return;
        hideSoftKeyboard();
        search(historyAdapter.getItem(position));
    }

    private void deleteSingleHistoryItem(String wd) {
        List<String> historys = getHistoryWords();
        if (!NullUtil.isNull(historys)) {
            boolean removed = false;
            for (int i = 0; i < historys.size(); i++) {
                if (historys.get(i).equalsIgnoreCase(wd)) {
                    historys.remove(i);
                    removed = true;
                    break;
                }
            }
            if (removed) {
                spDataUtil.saveObjectValue("histroys", historys);
                refreshHistoryView();
                Toast.makeText(mContext, "已删除搜索词：" + wd, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 搜索历史为空时整块收起：既不会占着位置，也不会出现「清空历史」按钮点了没变化
     */
    private void refreshHistoryView() {
        List<String> historyWords = getHistoryWords();
        boolean empty = historyWords == null || historyWords.isEmpty();
        historyAdapter.setNewData(empty ? new ArrayList<>() : historyWords);
        mSearchHistoryRV.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (llClearHistory != null) {
            llClearHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (tvHistoryHint != null) tvHistoryHint.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * 只读取真实搜索过的历史；清空过就是空列表，不再用内置示例词把历史"填回来"
     */
    private List<String> getHistoryWords() {
        List<String> historyWords = (List<String>) spDataUtil.getObjectValue("histroys");
        if (historyWords == null) {
            historyWords = new ArrayList<>();
        } else if (historyWords.size() > MAX_SEARCH_HISTORY) {
            historyWords = new ArrayList<>(historyWords.subList(0, MAX_SEARCH_HISTORY));
            spDataUtil.saveObjectValue("histroys", historyWords);
        }
        return historyWords;
    }

    private void saveHistoryWords(String wd) {
        List<String> historys = getHistoryWords();
        if (!NullUtil.isNull(historys)) {
            for (String item : historys) {
                if (item.equalsIgnoreCase(wd)) {
                    //原有，移除
                    historys.remove(item);
                    break;
                }
            }
        } else {
            historys = new ArrayList<>();
        }
        historys.add(0, wd);
        if (historys.size() > MAX_SEARCH_HISTORY) {
            historys = new ArrayList<>(historys.subList(0, MAX_SEARCH_HISTORY));
        }
        spDataUtil.saveObjectValue("histroys", historys);
    }
    private void clearHistory() {
        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("清空搜索历史？")
                .setMessage("清空后将无法恢复这些搜索记录。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> doClearHistory())
                .show();
    }

    private void doClearHistory() {
        spDataUtil.saveObjectValue("histroys", new ArrayList<String>());
        refreshHistoryView();
        //按钮清空后自己会隐藏，把焦点交回搜索键，避免焦点悬空
        tvSearch.requestFocus();
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.listResult.observe(this, absXml -> {
            if (folderRequestId < 0 || sourceViewModel.getLastPostedCategoryRequestId() != folderRequestId) return;
            android.util.Log.i(TRACE_TAG, "folder response activity=" + System.identityHashCode(this)
                    + " request=" + folderRequestId);
            showSuccess();
            if (absXml == null || absXml.movie == null || absXml.movie.videoList == null
                    || absXml.movie.videoList.isEmpty()) {
                tvSearchProgress.setText("目录为空或暂时无法加载");
            } else {
                searchResultAdapter.setNewData(new ArrayList<>(absXml.movie.videoList));
                tvSearchProgress.setText("已加载 " + searchResultAdapter.getData().size() + " 条资源");
                mSearchResultRV.setVisibility(View.VISIBLE);
                mSearchResultRV.setSelection(0);
                mSearchResultRV.requestFocus();
            }
        });
    }

    /**
     * 拼音联想
     */
    private void loadPinyinResult(String key) {
        final int requestId = ++suggestionRequestId;
        if (suggestionRunnable != null) {
            searchHandler.removeCallbacks(suggestionRunnable);
            suggestionRunnable = null;
        }
        OkGo.getInstance().cancelTag(suggestionTag);
        if (TextUtils.isEmpty(key)) {
            wordAdapter.setNewData(new ArrayList<>());
            updateHotWordPanel();
            return;
        }
        wordAdapter.setNewData(new ArrayList<>());
        updateHotWordPanel();

        suggestionRunnable = () -> {
            if (requestId != suggestionRequestId || !key.equals(etSearch.getText().toString().trim())) {
                return;
            }
            OkGo.<String>get("https://suggest.video.iqiyi.com/")
                    .tag(suggestionTag)
                    .params("if", "mobile")
                    .params("key", key)
                    .execute(new AbsCallback<String>() {
                        @Override
                        public void onSuccess(Response<String> response) {
                            if (requestId != suggestionRequestId
                                    || !key.equals(etSearch.getText().toString().trim())
                                    || isFinishing() || isDestroyed()) {
                                return;
                            }
                            try {
                                ArrayList<String> hots = new ArrayList<>();
                                String result = response.body();
                                JsonObject json = JsonParser.parseString(result).getAsJsonObject();
                                JsonArray itemList = json.get("data").getAsJsonArray();
                                for (JsonElement ele : itemList) {
                                    JsonObject obj = (JsonObject) ele;
                                    hots.add(obj.get("name").getAsString().trim().replaceAll("<|>|《|》|-", ""));
                                }
                                wordAdapter.setNewData(hots);
                                updateHotWordPanel();
                            } catch (Throwable th) {
                                L.e(th);
                            }
                        }

                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            return response.body().string();
                        }
                    });
        };
        searchHandler.postDelayed(suggestionRunnable, SUGGESTION_DEBOUNCE_MS);
    }


    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("folderId")) {
            folderMode = true;
            loadFolder(intent.getStringExtra("folderId"),
                    intent.getStringExtra("folderSourceKey"),
                    intent.getStringExtra("title"));
            return;
        }
        String key = "";
        if (intent != null && intent.hasExtra("title")) {
            key = intent.getStringExtra("title");
            if (!NullUtil.isNull(key)) {
                //edit不为空，则edit不需要焦点，让parent抢走焦点
                showLoading();
                search(key);
            }
        }
        refreshQRCode();
        requestHotWords();
        tvSearch.requestFocus();
    }

    private void loadFolder(String id, String sourceKey, String title) {
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(sourceKey)) {
            tvSearchProgress.setText("目录信息不完整");
            return;
        }
        if (folderMode) {
            if (etSearch != null) etSearch.setText(TextUtils.isEmpty(title) ? "目录" : title);
            if (llWord != null) llWord.setVisibility(View.GONE);
            if (mSearchHistoryRV != null) mSearchHistoryRV.setVisibility(View.GONE);
            if (tvSearch != null) tvSearch.setVisibility(View.GONE);
        }
        if (tvSearchProgress != null) {
            tvSearchProgress.setVisibility(View.VISIBLE);
            tvSearchProgress.setText("正在加载目录…");
        }
        folderRequestId = sourceViewModel.getFolderList(sourceKey, id);
        android.util.Log.i(TRACE_TAG, "folder requested activity=" + System.identityHashCode(this)
                + " request=" + folderRequestId);
    }

    private void requestHotWords() {
        final int generation = suggestionRequestId;
        if (!canShowHotWords(generation)) return;
        // 加载热词
        OkGo.<String>get("https://node.video.qq.com/x/api/hot_mobilesearch")
                .tag(hotWordsTag)
                .params("channdlId", "0")
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        if (!canShowHotWords(generation)) return;
                        try {
                            ArrayList<String> hots = new ArrayList<>();
                            L.d(response.body());
                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonObject().get("itemList").getAsJsonArray();
                            for (JsonElement ele : itemList) {
                                JsonObject obj = (JsonObject) ele;
                                hots.add(obj.get("title").getAsString().trim().replaceAll("<|>|《|》|-", "").split(" ")[0]);
                            }
                            wordAdapter.setNewData(hots);
                            updateHotWordPanel();
                        } catch (Throwable th) {
                            L.e(th);
                            applyFallbackHotWords(generation);
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        applyFallbackHotWords(generation);
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }
    /** 热词接口失败时的内置兜底，避免热门区域直接消失 */
    private static final String[] FALLBACK_HOT_WORDS = {"狂飙", "三体", "繁花", "庆余年", "黑镜", "漫长的季节"};

    private void applyFallbackHotWords(int generation) {
        if (!canShowHotWords(generation) || wordAdapter == null) return;
        if (wordAdapter.getData() != null && !wordAdapter.getData().isEmpty()) return;
        wordAdapter.setNewData(new ArrayList<>(Arrays.asList(FALLBACK_HOT_WORDS)));
        updateHotWordPanel();
    }

    private boolean canShowHotWords(int generation) {
        return !isFinishing() && !isDestroyed() && generation == suggestionRequestId
                && etSearch != null && TextUtils.isEmpty(etSearch.getText().toString().trim())
                && TextUtils.isEmpty(searchWord);
    }

    private void refreshQRCode() {
//        String address = ControlManager.get().getAddress(false);
//        tvAddress.setText(String.format("远程搜索使用手机/电脑扫描下面二维码或者直接浏览器访问地址\n%s", address));
//        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address, 300, 300));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void searchEvent(ServerEvent event) {
        if (!folderMode && event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            search(title);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            if (folderMode || event.requestId != searchRequestId || searchFinished) {
                return;
            }
            if (event.sourceKey == null) return;
            if (!retryPhase && respondedSourceKeys.contains(event.sourceKey)) return;
            boolean success = event.obj != null;
            if (success) {
                try {
                    showSearchData((AbsXml) event.obj);
                } catch (Exception e) {
                    L.e("showSearchData " + e.getMessage());
                    success = false;
                }
            }
            if (!retryPhase) {
                respondedSourceKeys.add(event.sourceKey);
                completedSearchSources++;
                if (!success) failedSourceKeys.add(event.sourceKey);
                if (completedSearchSources >= searchSourceCount) {
                    beginRetryPhase();
                }
            } else if (success) {
                failedSourceKeys.remove(event.sourceKey);
                respondedSourceKeys.add(event.sourceKey);
            } else if (failedSourceKeys.contains(event.sourceKey)) {
                retrySearchSource(event.sourceKey, event.requestId);
            }
            updateSearchProgress(false);
        }
    }

    private void hideSoftKeyboard() {
        System_UI.hideInputMethod(getBaseContext(), etSearch);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            View view = getCurrentFocus();
            if (view == null) {
                view = etSearch != null ? etSearch : getWindow().getDecorView();
            }
            if (view != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private void showSoftKeyboard(View view) {
        if (view == null) return;
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void searchClick() {
        hideSoftKeyboard();
        String wd = etSearch.getText().toString().trim();
        if (!TextUtils.isEmpty(wd)) {
            search(wd);
        } else {
            Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void search(String wd) {
        hideSoftKeyboard();
        stopSearchRequests();
        cancelSuggestions();
        wordAdapter.setNewData(new ArrayList<>());
        updateHotWordPanel();
        searchHandler.removeCallbacks(searchTimeout);
        searchRequestId++;
        searchFinished = false;
        completedSearchSources = 0;
        searchSourceCount = 0;
        sourceAttempts.clear();
        failedSourceKeys.clear();
        retryPhase = false;
        clearSourceRetryTasks();
        showSuccess();
        this.searchWord = wd;
        if (llSearchEmpty != null) llSearchEmpty.setVisibility(View.GONE);
        tvSearchProgress.setVisibility(View.VISIBLE);
        tvSearchProgress.setText("正在准备搜索来源…");
        mSearchResultRV.setVisibility(View.VISIBLE);
        searchResultAdapter.setNewData(new ArrayList<>());
        mSearchResultRV.scrollToPosition(0);
        doSearch();
        if (searchSourceCount > 0) {
            searchHandler.postDelayed(searchTimeout, SEARCH_TIMEOUT_MS);
        }
        if (etSearch != null) {
            suppressSuggestionWatcher = true;
            etSearch.setText(wd);
            etSearch.setSelection(etSearch.getText().length());
            suppressSuggestionWatcher = false;
        }
        saveHistoryWords(wd);
        refreshHistoryView();
        // 不在这里抢焦点：焦点由首批结果到来时决定（见 mergeResults）
    }

    private void doSearch() {
        OkGo.getInstance().cancelTag("search");
        searchResultAdapter.setNewData(new ArrayList<>());

        // 站点顺序即「搜索源管理」里的勾选顺序（已过滤掉未勾选与不可搜索的源）
        List<SiteBean> siteBeans = ApiConfig.getInstance().getOrderedSearchSiteList();
        if (siteBeans.isEmpty()) {
            searchFinished = true;
            tvSearchProgress.setText("没有启用搜索源，请先在设置中勾选可搜索的来源");
            Toast.makeText(mContext, "未选择任何搜索源，请到 设置 → 常规与播放 → 搜索源管理 中勾选", Toast.LENGTH_LONG).show();
            return;
        }
        searchSourceCount = siteBeans.size();
        activeSearchSites = new ArrayList<>(siteBeans);
        sourceOrder.clear();
        for (int i = 0; i < siteBeans.size(); i++) sourceOrder.put(siteBeans.get(i).getKey(), i);
        respondedSourceKeys.clear();
        sourceAttempts.clear();
        failedSourceKeys.clear();
        retryPhase = false;
        updateSearchProgress(false);
        submitSearchSources(siteBeans);
    }

    private boolean retrySearchSource(String sourceKey, int requestId) {
        int attempt = sourceAttempts.containsKey(sourceKey) ? sourceAttempts.get(sourceKey) : 0;
        attempt++;
        sourceAttempts.put(sourceKey, attempt);
        if (attempt >= MAX_SOURCE_ATTEMPTS) {
            L.w("search source exhausted key=" + sourceKey + " attempts=" + attempt);
            return false;
        }

        final Runnable[] retryHolder = new Runnable[1];
        retryHolder[0] = () -> {
            sourceRetryTasks.remove(retryHolder[0]);
            if (searchFinished || requestId != searchRequestId || respondedSourceKeys.contains(sourceKey)) return;
            sourceViewModel.getSearch(sourceKey, searchWord, requestId);
        };
        sourceRetryTasks.add(retryHolder[0]);
        searchHandler.postDelayed(retryHolder[0], SOURCE_RETRY_DELAY_MS);
        L.w("retry search source key=" + sourceKey + " attempt=" + (attempt + 1));
        return true;
    }

    private void beginRetryPhase() {
        if (retryPhase) return;
        retryPhase = true;
        searchHandler.removeCallbacks(searchTimeout);
        for (String sourceKey : new ArrayList<>(failedSourceKeys)) {
            retrySearchSource(sourceKey, searchRequestId);
        }
    }

    private void clearSourceRetryTasks() {
        for (Runnable retryTask : sourceRetryTasks) {
            searchHandler.removeCallbacks(retryTask);
        }
        sourceRetryTasks.clear();
    }

    private void submitSearchSources(List<SiteBean> siteBeans) {
        final int requestId = searchRequestId;
        final String query = searchWord;
        for (final SiteBean siteBean : siteBeans) {
            searchTasks.add(mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    if (searchFinished || requestId != searchRequestId
                            || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    sourceViewModel.getSearch(siteBean.getKey(), query, requestId);
                }
            }));
        }
    }

    private void stopSearchRequests() {
        searchFinished = true;
        retryPhase = false;
        resultGeneration++;
        pendingResults.clear();
        resultMergeRunning = false;
        clearSourceRetryTasks();
        failedSourceKeys.clear();
        searchHandler.removeCallbacks(mergeResults);
        searchHandler.removeCallbacks(searchTimeout);
        OkGo.getInstance().cancelTag("search");
        for (Future<?> task : searchTasks) {
            task.cancel(true);
        }
        searchTasks.clear();
    }

    private void cancelSuggestions() {
        suggestionRequestId++;
        if (suggestionRunnable != null) {
            searchHandler.removeCallbacks(suggestionRunnable);
            suggestionRunnable = null;
        }
        OkGo.getInstance().cancelTag(suggestionTag);
    }

    private void updateSearchProgress(boolean timedOut) {
        if (tvSearchProgress == null) return;
        boolean hasResults = !searchResultAdapter.getData().isEmpty();
        boolean finished = timedOut || (searchSourceCount > 0 && completedSearchSources >= searchSourceCount) || searchFinished;
        if (finished && hasResults) {
            // 搜索完成且有结果：隐藏进度文字，结果列表直接顶到最上面不被遮挡
            tvSearchProgress.setVisibility(View.GONE);
        } else if (timedOut && completedSearchSources < searchSourceCount && !hasResults) {
            // 超时且无结果：显示提示
            tvSearchProgress.setVisibility(View.VISIBLE);
            tvSearchProgress.setText("搜索超时，没有找到结果");
        } else if (finished && !hasResults) {
            // 完成但无结果：隐藏进度文字（空状态图已经显示了）
            tvSearchProgress.setVisibility(View.GONE);
        } else {
            // 搜索进行中：显示进度
            tvSearchProgress.setVisibility(View.VISIBLE);
            tvSearchProgress.setText("正在搜索 · 已收到 " + completedSearchSources + "/" + searchSourceCount + " 个来源的响应");
        }
        if (llSearchEmpty != null) {
            llSearchEmpty.setVisibility(!hasResults && finished ? View.VISIBLE : View.GONE);
        }
        if (mSearchResultRV != null) {
            mSearchResultRV.setVisibility(!hasResults && finished ? View.GONE : View.VISIBLE);
        }
    }

    private void showSearchData(AbsXml absXml) {
        if (absXml == null || absXml.movie == null || NullUtil.isNull(absXml.movie.videoList)) return;
        final int generation = resultGeneration;
        final List<Movie.Video> sourceVideos = new ArrayList<>(absXml.movie.videoList);
        final String query = searchWord;
        resultExecutor.execute(() -> {
            List<Movie.Video> videos = new ArrayList<>();
            for (Movie.Video video : sourceVideos) {
                if (video != null && !NullUtil.isNull(video.name)
                        && video.name.contains(query)
                        && UiHelper.canPlay(video) && UiHelper.canPlayName(video)) {
                    videos.add(video);
                    if (videos.size() >= MAX_SOURCE_RESULTS) break;
                }
            }
            if (videos == null || videos.isEmpty()) return;
            searchHandler.post(() -> {
                if (generation != resultGeneration || isFinishing() || isDestroyed()) return;
                pendingResults.addAll(videos);
                scheduleMergeResults();
            });
        });
    }

    private void scheduleMergeResults() {
        if (mergeScheduled || resultMergeRunning || pendingResults.isEmpty()) return;
        mergeScheduled = true;
        searchHandler.removeCallbacks(mergeResults);
        searchHandler.postDelayed(() -> {
            mergeScheduled = false;
            mergePendingResults();
        }, RESULT_MERGE_DELAY_MS);
    }

    private void mergePendingResults() {
        if (resultMergeRunning || pendingResults.isEmpty() || isFinishing() || isDestroyed() || resultExecutor.isShutdown()) return;
        resultMergeRunning = true;
        final int generation = resultGeneration;
        final List<Movie.Video> old = new ArrayList<>(searchResultAdapter.getData());
        final List<Movie.Video> merged = new ArrayList<>(old);
        merged.addAll(pendingResults);
        pendingResults.clear();
        if (merged.size() > MAX_TOTAL_RESULTS) {
            merged.subList(MAX_TOTAL_RESULTS, merged.size()).clear();
        }
        final Map<String, Integer> order = new HashMap<>(sourceOrder);
        resultExecutor.execute(() -> {
            Collections.sort(merged, (a, b) -> Integer.compare(
                    order.containsKey(a.sourceKey) ? order.get(a.sourceKey) : Integer.MAX_VALUE,
                    order.containsKey(b.sourceKey) ? order.get(b.sourceKey) : Integer.MAX_VALUE));
            DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return old.size(); }
                @Override public int getNewListSize() { return merged.size(); }
                @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                    return old.get(oldPosition) == merged.get(newPosition);
                }
                @Override public boolean areContentsTheSame(int oldPosition, int newPosition) { return true; }
            });
            searchHandler.post(() -> {
                if (generation != resultGeneration || isFinishing() || isDestroyed()) return;
                searchResultAdapter.getData().clear();
                searchResultAdapter.getData().addAll(merged);
                changes.dispatchUpdatesTo(searchResultAdapter);
                resultMergeRunning = false;
                if (!merged.isEmpty()) {
                    showSuccess();
                    if (llSearchEmpty != null) llSearchEmpty.setVisibility(View.GONE);
                    mSearchResultRV.setVisibility(View.VISIBLE);
                    // 首批结果到来时自动滚回顶部，避免搜索结果从中途位置开始显示
                    if (old.isEmpty()) {
                        mSearchResultRV.scrollToPosition(0);
                        // 用户是在“搜索”按钮上确认的，首批结果出来后直接把焦点交给结果第一项
                        if (tvSearch != null && tvSearch.hasFocus()) {
                            mSearchResultRV.post(() -> {
                                mSearchResultRV.setSelection(0);
                                mSearchResultRV.requestFocus();
                            });
                        }
                    }
                }
                updateSearchProgress(searchFinished && completedSearchSources < searchSourceCount);
                if (!pendingResults.isEmpty()) scheduleMergeResults();
            });
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            View currentFocus = getCurrentFocus();
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (currentFocus != null && isChildOf(currentFocus, keyboard)) {
                    View next = currentFocus.focusSearch(View.FOCUS_RIGHT);
                    if (next == null || !isChildOf(next, keyboard)) {
                        if (llWord != null && llWord.getVisibility() == View.VISIBLE && mHotWordRV != null && mHotWordRV.getChildCount() > 0) {
                            mHotWordRV.requestFocus();
                            return true;
                        } else if (mSearchResultRV != null && mSearchResultRV.getVisibility() == View.VISIBLE && mSearchResultRV.getChildCount() > 0) {
                            mSearchResultRV.requestFocus();
                            return true;
                        }
                    }
                } else if (currentFocus != null && isChildOf(currentFocus, mHotWordRV)) {
                    View next = currentFocus.focusSearch(View.FOCUS_RIGHT);
                    if (next == null || !isChildOf(next, mHotWordRV)) {
                        if (mSearchResultRV != null && mSearchResultRV.getVisibility() == View.VISIBLE && mSearchResultRV.getChildCount() > 0) {
                            mSearchResultRV.requestFocus();
                            return true;
                        }
                    }
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (currentFocus != null && isChildOf(currentFocus, mSearchResultRV)) {
                    View next = currentFocus.focusSearch(View.FOCUS_LEFT);
                    if (next == null || !isChildOf(next, mSearchResultRV)) {
                        if (llWord != null && llWord.getVisibility() == View.VISIBLE && mHotWordRV != null && mHotWordRV.getChildCount() > 0) {
                            mHotWordRV.requestFocus();
                            return true;
                        } else if (keyboard != null && keyboard.getVisibility() == View.VISIBLE) {
                            keyboard.requestFocus();
                            return true;
                        }
                    }
                } else if (currentFocus != null && isChildOf(currentFocus, mHotWordRV)) {
                    View next = currentFocus.focusSearch(View.FOCUS_LEFT);
                    if (next == null || !isChildOf(next, mHotWordRV)) {
                        if (keyboard != null && keyboard.getVisibility() == View.VISIBLE) {
                            keyboard.requestFocus();
                            return true;
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void finish() {
        if (sourceViewModel != null && sourceViewModel.isInFolderSpiderCall()) {
            android.util.Log.w(TRACE_TAG, "ignored finish from folder spider activity="
                    + System.identityHashCode(this));
            return;
        }
        android.util.Log.w(TRACE_TAG, "finish activity=" + System.identityHashCode(this)
                + " request=" + folderRequestId, new Throwable("SearchActivity.finish caller"));
        super.finish();
    }

    @Override
    public void onBackPressed() {
        if (folderMode) {
            finish();
            return;
        }
        View currentFocus = getCurrentFocus();
        // 电视端层级返回：若焦点在搜索结果或热搜词上，按返回键先将焦点送回键盘区，继续在键盘区按返回才退出
        if (currentFocus != null && (isChildOf(currentFocus, mSearchResultRV) || isChildOf(currentFocus, mHotWordRV))) {
            if (keyboard != null && keyboard.getVisibility() == View.VISIBLE) {
                keyboard.requestFocus();
                return;
            }
        }
        super.onBackPressed();
    }

    private boolean isChildOf(View child, View parent) {
        if (child == null || parent == null) return false;
        if (child == parent) return true;
        android.view.ViewParent p = child.getParent();
        while (p != null) {
            if (p == parent) return true;
            p = p.getParent();
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSearchRequests();
        cancelSuggestions();
        OkGo.getInstance().cancelTag(hotWordsTag);
        EventBus.getDefault().unregister(this);
        // 释放聚合搜索线程池，避免退出搜索页后仍存活 5 条线程
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
        }
        resultExecutor.shutdownNow();
    }
}
