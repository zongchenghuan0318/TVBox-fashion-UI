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
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.adapter.PinyinAdapter;
import com.github.tvbox.osc.ui.adapter.SearchHistoryAdapter;
import com.github.tvbox.osc.ui.adapter.SearchResultAdapter;
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
import com.uisupport.update.Initer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.DiffUtil;

public class SearchActivity extends BaseActivity {
    private LinearLayout llLayout;
    private LinearLayout llWord;
    private LinearLayout llClearHistory;
    private TvRecyclerView mSearchResultRV;
    private TvRecyclerView mHotWordRV;
    private TvRecyclerView mSearchHistoryRV;
    private SourceViewModel sourceViewModel;
    private EditText etSearch;
    private TextView tvSearch;
    private TextView tvSearchProgress;
    private TextView tvRetrySearch;
    private TextView tvClear;
    private SearchKeyboard keyboard;
    private SearchResultAdapter searchResultAdapter;
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
    private int resultGeneration;
    private final Runnable mergeResults = this::mergePendingResults;
    private volatile int searchRequestId;
    private int searchSourceCount;
    private int completedSearchSources;
    private List<SiteBean> activeSearchSites = new ArrayList<>();
    private final Set<String> respondedSourceKeys = new HashSet<>();
    private volatile boolean searchFinished;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Object suggestionTag = new Object();
    private final Object hotWordsTag = new Object();
    private int suggestionRequestId;
    private Runnable suggestionRunnable;
    private boolean suppressSuggestionWatcher;
    private static final long SUGGESTION_DEBOUNCE_MS = 250L;
    private static final int MAX_SEARCH_HISTORY = 20;
    private final Runnable searchTimeout = new Runnable() {
        @Override
        public void run() {
            if (!searchFinished && searchResultAdapter != null) {
                searchFinished = true;
                OkGo.getInstance().cancelTag("search");
                updateSearchProgress(true);
                List<SiteBean> pending = getUnresponsiveSites();
                tvRetrySearch.setVisibility(pending.isEmpty() ? View.GONE : View.VISIBLE);
                if (!searchResultAdapter.getData().isEmpty() && !pending.isEmpty()) {
                    Toast.makeText(SearchActivity.this, "已显示部分搜索结果，部分来源响应超时", Toast.LENGTH_SHORT).show();
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
        tvRetrySearch = findViewById(R.id.tvRetrySearch);
        tvClear = findViewById(R.id.tvClear);
        mSearchResultRV = findViewById(R.id.sortRV);
        keyboard = findViewById(R.id.keyBoardRoot);
        mHotWordRV = findViewById(R.id.mGridViewWord);
        mSearchHistoryRV = findViewById(R.id.serch_history_rv);
        llClearHistory = findViewById(R.id.llClearHistory);
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
        tvRetrySearch.setOnClickListener(v -> retryUnresponsiveSources());
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
        searchResultAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchResultAdapter.getData().get(position);
                if (video != null) {
                    stopSearchRequests();
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    bundle.putString("note", video.note);
                    bundle.putString("movieName", video.name);
                    jumpActivity(DetailActivity.class, bundle);
                }
            }
        });
    }

    private void initHotRecycleView() {
        mHotWordRV.setHasFixedSize(true);
        mHotWordRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        wordAdapter = new PinyinAdapter();
        mHotWordRV.setAdapter(wordAdapter);
        wordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                search(wordAdapter.getItem(position));
            }
        });
    }

    private void initHistoryView() {
        mSearchHistoryRV.setHasFixedSize(true);
        mSearchHistoryRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, LinearLayoutManager.HORIZONTAL,
                false));
        historyAdapter = new SearchHistoryAdapter();
        mSearchHistoryRV.setAdapter(historyAdapter);
        historyAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                search(historyAdapter.getItem(position));
            }
        });
        refreshHistoryView();
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
        spDataUtil.saveObjectValue("histroys", new ArrayList<String>());
        refreshHistoryView();
        //按钮清空后自己会隐藏，把焦点交回搜索键，避免焦点悬空
        tvSearch.requestFocus();
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
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
                                th.printStackTrace();
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
        String key = "";
        Intent intent = getIntent();
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
    }

    private void requestHotWords() {
        Initer.init(this);
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
                            requestHotWords_my(generation);
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }
    private boolean canShowHotWords(int generation) {
        return !isFinishing() && !isDestroyed() && generation == suggestionRequestId
                && etSearch != null && TextUtils.isEmpty(etSearch.getText().toString().trim())
                && TextUtils.isEmpty(searchWord);
    }

    private void requestHotWords_my(int generation) {
        new Thread() {
            @Override
            public void run() {
                super.run();
                //热词只是推荐内容，接口异常时不能把搜索页带崩
                try {
                    final HotWords hotWordsBean = HttpUtil.getHotWords(getApplicationContext(), UiConstans.HOT_WORDS_URL, "获取热词");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!canShowHotWords(generation)) return;
                            if (hotWordsBean != null && !NullUtil.isNull(hotWordsBean.resultData)) {
                                ArrayList<String> hots = new ArrayList<>();
                                for (HotWords.Word w : hotWordsBean.resultData) {
                                    hots.add(w.name);
                                }
                                if (!NullUtil.isNull(hots)) {
                                    wordAdapter.setNewData(hots);
                                    updateHotWordPanel();
                                }
                            }
                        }
                    });
                } catch (Throwable th) {
                    L.e("requestHotWords_my " + th.getMessage());
                }
            }
        }.start();
    }

    private void refreshQRCode() {
//        String address = ControlManager.get().getAddress(false);
//        tvAddress.setText(String.format("远程搜索使用手机/电脑扫描下面二维码或者直接浏览器访问地址\n%s", address));
//        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address, 300, 300));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void searchEvent(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            search(title);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            if (event.requestId != searchRequestId || searchFinished) {
                return;
            }
            if (event.sourceKey == null || !respondedSourceKeys.add(event.sourceKey)) return;
            try {
                showSearchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                L.e("showSearchData " + e.getMessage());
                showSearchData(null);
            }
            completedSearchSources++;
            updateSearchProgress(false);
            if (completedSearchSources >= searchSourceCount) {
                searchFinished = true;
                searchHandler.removeCallbacks(searchTimeout);
                tvRetrySearch.setVisibility(View.GONE);
            }
        }
    }

    private void hideSoftKeyboard() {
        System_UI.hideInputMethod(getBaseContext(), etSearch);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
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
        stopSearchRequests();
        tvRetrySearch.setVisibility(View.GONE);
        cancelSuggestions();
        wordAdapter.setNewData(new ArrayList<>());
        updateHotWordPanel();
        searchHandler.removeCallbacks(searchTimeout);
        searchRequestId++;
        searchFinished = false;
        completedSearchSources = 0;
        searchSourceCount = 0;
        showSuccess();
        this.searchWord = wd;
        tvSearchProgress.setVisibility(View.VISIBLE);
        tvSearchProgress.setText("正在准备搜索来源…");
        mSearchResultRV.setVisibility(View.VISIBLE);
        searchResultAdapter.setNewData(new ArrayList<>());
        doSearch();
        if (searchSourceCount > 0) {
            searchHandler.postDelayed(searchTimeout, 15000L);
        }
        if (etSearch != null) {
            suppressSuggestionWatcher = true;
            etSearch.setText(wd);
            etSearch.setSelection(etSearch.getText().length());
            suppressSuggestionWatcher = false;
        }
        saveHistoryWords(wd);
        refreshHistoryView();
        upSearchKey(wd);
        tvSearch.requestFocus();
    }

    private void upSearchKey(final String wd) {
        new Thread() {
            @Override
            public void run() {
                super.run();
                //上报搜索词只是统计信息，接口不可用时不能让搜索页闪退
                try {
                    HttpUtil.getHotWords(getApplicationContext(), UiConstans.HOT_WORDS_URL, wd);
                } catch (Throwable th) {
                    L.e("upSearchKey " + th.getMessage());
                }
            }
        }.start();
    }

    private void doSearch() {
        OkGo.getInstance().cancelTag("search");
        searchResultAdapter.setNewData(new ArrayList<>());

        // 站点顺序即「搜索源管理」里的勾选顺序（已过滤掉未勾选与不可搜索的源）
        List<SiteBean> siteBeans = ApiConfig.getInstance().getOrderedSearchSiteList();
        if (siteBeans.isEmpty()) {
            searchFinished = true;
            tvSearchProgress.setText("没有启用搜索源，请先在设置中勾选可搜索的来源");
            Toast.makeText(mContext, "未选择任何搜索源，请到 设置 → 设置其他 → 搜索源管理 中勾选", Toast.LENGTH_LONG).show();
            return;
        }
        searchSourceCount = siteBeans.size();
        activeSearchSites = new ArrayList<>(siteBeans);
        sourceOrder.clear();
        for (int i = 0; i < siteBeans.size(); i++) sourceOrder.put(siteBeans.get(i).getKey(), i);
        respondedSourceKeys.clear();
        updateSearchProgress(false);
        submitSearchSources(siteBeans);
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

    private List<SiteBean> getUnresponsiveSites() {
        List<SiteBean> pending = new ArrayList<>();
        for (SiteBean siteBean : activeSearchSites) {
            if (!respondedSourceKeys.contains(siteBean.getKey())) pending.add(siteBean);
        }
        return pending;
    }

    private void retryUnresponsiveSources() {
        if (resultMergeRunning || !pendingResults.isEmpty()) {
            if (!resultMergeRunning) {
                searchHandler.removeCallbacks(mergeResults);
                mergePendingResults();
            }
            final int generation = resultGeneration;
            searchHandler.postDelayed(() -> {
                if (generation == resultGeneration) retryUnresponsiveSources();
            }, 100L);
            return;
        }
        List<SiteBean> pending = getUnresponsiveSites();
        if (pending.isEmpty()) {
            tvRetrySearch.setVisibility(View.GONE);
            return;
        }
        stopSearchRequests();
        searchRequestId++;
        searchFinished = false;
        completedSearchSources = 0;
        searchSourceCount = pending.size();
        activeSearchSites = new ArrayList<>(pending);
        tvRetrySearch.setVisibility(View.GONE);
        updateSearchProgress(false);
        submitSearchSources(pending);
        searchHandler.postDelayed(searchTimeout, 15000L);
    }

    private void stopSearchRequests() {
        searchFinished = true;
        resultGeneration++;
        pendingResults.clear();
        resultMergeRunning = false;
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
        tvSearchProgress.setVisibility(View.VISIBLE);
        if (timedOut && completedSearchSources < searchSourceCount && !searchResultAdapter.getData().isEmpty()) {
            tvSearchProgress.setText("已找到 " + searchResultAdapter.getData().size() + " 条，" + completedSearchSources
                    + "/" + searchSourceCount + " 个来源已响应；其余来源超时");
        } else if (timedOut && completedSearchSources < searchSourceCount) {
            tvSearchProgress.setText("搜索超时，没有找到结果；可重试未响应来源");
        } else if (completedSearchSources >= searchSourceCount && searchSourceCount > 0) {
            int count = searchResultAdapter.getData().size();
            tvSearchProgress.setText(count == 0
                    ? "搜索完成（" + searchSourceCount + " 个来源），没有匹配结果"
                    : "搜索完成 · " + count + " 条结果 · " + searchSourceCount + " 个来源");
        } else {
            tvSearchProgress.setText("正在搜索 · 已收到 " + completedSearchSources + "/" + searchSourceCount + " 个来源的响应");
        }
    }

    private void showSearchData(AbsXml absXml) {
        if (absXml == null || absXml.movie == null || NullUtil.isNull(absXml.movie.videoList)) return;
        List<Movie.Video> videos = UiHelper.filterVideoList(absXml, searchWord);
        if (videos == null || videos.isEmpty()) return;
        pendingResults.addAll(videos);
        if (!resultMergeRunning) {
            searchHandler.removeCallbacks(mergeResults);
            searchHandler.postDelayed(mergeResults, 200L);
        }
    }

    private void mergePendingResults() {
        if (resultMergeRunning || pendingResults.isEmpty() || isFinishing() || isDestroyed() || resultExecutor.isShutdown()) return;
        resultMergeRunning = true;
        final int generation = resultGeneration;
        final List<Movie.Video> old = new ArrayList<>(searchResultAdapter.getData());
        final List<Movie.Video> merged = new ArrayList<>(old);
        merged.addAll(pendingResults);
        pendingResults.clear();
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
                    mSearchResultRV.setVisibility(View.VISIBLE);
                }
                updateSearchProgress(searchFinished && completedSearchSources < searchSourceCount);
                if (!pendingResults.isEmpty()) searchHandler.postDelayed(mergeResults, 200L);
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
                    if (next == null || isChildOf(next, keyboard)) {
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
                    if (next == null || isChildOf(next, mHotWordRV)) {
                        if (mSearchResultRV != null && mSearchResultRV.getVisibility() == View.VISIBLE && mSearchResultRV.getChildCount() > 0) {
                            mSearchResultRV.requestFocus();
                            return true;
                        }
                    }
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (currentFocus != null && isChildOf(currentFocus, mSearchResultRV)) {
                    View next = currentFocus.focusSearch(View.FOCUS_LEFT);
                    if (next == null || isChildOf(next, mSearchResultRV)) {
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
                    if (next == null || isChildOf(next, mHotWordRV)) {
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
