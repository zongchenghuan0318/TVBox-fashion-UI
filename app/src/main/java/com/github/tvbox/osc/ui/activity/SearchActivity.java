package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.PinyinAdapter;
import com.github.tvbox.osc.ui.adapter.SearchHistoryAdapter;
import com.github.tvbox.osc.ui.adapter.SearchResultAdapter;
import com.github.tvbox.osc.ui.dialog.CloudDriveDialog;
import com.github.tvbox.osc.ui.dialog.FolderDialog;
import com.github.tvbox.osc.ui.dialog.SearchSiteDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.util.SpiderActionHelper;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.ui.tv.widget.SafeTvLayoutManagers;
import com.github.tvbox.osc.ui.tv.widget.SearchKeyboard;
import com.github.tvbox.osc.util.AppExecutors;
import com.github.tvbox.osc.util.BilibiliHotRank;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PosterPreloader;
import com.github.tvbox.osc.util.SeniorMode;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.SPDataUtil;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.studio.osc.R;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SearchActivity extends BaseActivity {
    private static final String TRACE_TAG = "SearchRoute";
    private boolean folderMode;
    private int folderRequestId = -1;
    private String folderId = "";
    private String folderSourceKey = "";
    private String folderTitle = "";
    private View llSearchRoot;
    private View llSearchLeftPanel;
    private View llFolderHeader;
    private TextView tvFolderModeTitle;
    private TextView tvFolderModeSubtitle;
    private LinearLayout llWord;
    private TextView tvHotLabel;
    private LinearLayout llClearHistory;
    private TextView tvHistoryHint;
    private ImageView ivBack;
    private TvRecyclerView mSearchResultRV;
    private TvRecyclerView mHotWordRV;
    private TvRecyclerView mSearchHistoryRV;
    private SourceViewModel sourceViewModel;
    private EditText etSearch;
    private View ivClearInput;
    private TextView tvSearch;
    private View llSearchStatusBar;
    private View pbSearchLoading;
    private TextView tvSearchProgress;
    private View btnCloudLoginTop;
    private View btnManageSearchSourcesTop;
    private TextView tvClear;
    private SearchKeyboard keyboard;
    private View remoteRoot;
    private View btnBackToKeyboard;
    private TextView tvAddress;
    private ImageView ivQRCode;
    private String remoteQrAddress = "";
    private int remoteQrSizePx;
    private View llSearchGuide;
    private View llSearchLoading;
    private TextView tvSearchLoadingTip;
    private SearchResultAdapter searchResultAdapter;
    private View llSearchEmpty;
    private TextView tvSearchEmptyTip;
    private View btnEmptyRetry;
    private View btnEmptyManageSources;
    private PinyinAdapter wordAdapter;
    private SearchHistoryAdapter historyAdapter;
    private String searchWord = "";
    private SPDataUtil spDataUtil = null;
    private final List<SourceViewModel.SearchTaskHandle> searchTasks = new ArrayList<>();
    private final ExecutorService resultExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("search-result-"));
    private final ExecutorService historyIoExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("search-history-"));
    private final List<Movie.Video> pendingResults = new ArrayList<>();
    private final Map<String, Integer> sourceOrder = new HashMap<>();
    private static final Set<String> SESSION_SLOW_SOURCES = Collections.synchronizedSet(new HashSet<>());
    /** 进程级历史内存缓存：避免再次进入搜索页等待异步磁盘读取引起布局下移抖动 */
    private static volatile List<String> sHistoryCache;
    private boolean resultMergeRunning;
    private boolean mergeScheduled;
    private int inFlightFilterTasks;
    private int resultGeneration;
    private boolean resultsTruncated;
    private final Runnable mergeResults = new Runnable() {
        @Override
        public void run() {
            mergeScheduled = false;
            mergePendingResults();
        }
    };
    private static final AtomicInteger SEARCH_REQUEST_IDS = new AtomicInteger();
    private volatile int searchRequestId;
    private int searchSourceCount;
    private int completedSearchSources;
    private List<SiteBean> activeSearchSites = new ArrayList<>();
    /** 首轮已回包（无论成功或失败）的站点，用于计算首轮完成进度 */
    private final Set<String> initialCompletedKeys = new HashSet<>();
    /** 已经成功拿到结果的站点，用于避免对已成功站点重复发起重试 */
    private final Set<String> succeededSourceKeys = new HashSet<>();
    /** 当前正在排队或执行中的重试源集合，非空时不提前显示空结果页 */
    private final Set<String> pendingRetryKeys = new HashSet<>();
    private final Map<String, Integer> sourceAttempts = new HashMap<>();
    private final List<Runnable> sourceRetryTasks = new ArrayList<>();
    private final Set<String> failedSourceKeys = new HashSet<>();
    private boolean retryPhase;
    private boolean retryInProgress;
    private boolean searchTimedOut;
    private volatile boolean searchFinished;
    private boolean activityResumed;
    private boolean destroying;
    private boolean userScrolledResults;
    private View lastScaledResultItem;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Object suggestionTag = new Object();
    private int suggestionRequestId;
    private Runnable suggestionRunnable;
    private boolean suppressSuggestionWatcher;
    private static int nextHotWordsOffset;
    private int hotWordsOffset;
    private int hotWordsRequestId;
    private String hotWordsLoadingScope;
    private Future<?> hotRankTask;
    private boolean hotWordsAllowWhenSearched;
    private final List<String> cachedHistoryWords = new ArrayList<>();
    private boolean historyLoaded;
    private long lastSearchTriggerAt;
    private String lastSearchTriggerWord = "";
    private long lastWordSelectAt;
    private long lastResultOpenAt;
    private long lastBackHintAt;
    private View keyboardSearchFocus;
    private SearchSiteDialog activeSearchSiteDialog;
    private CloudDriveDialog activeCloudDriveDialog;
    private FolderDialog activeFolderDialog;
    private TipDialog activeTipDialog;
    private static final long SUGGESTION_DEBOUNCE_MS = 250L;
    private static final long SEARCH_DUPLICATE_GUARD_MS = 400L;
    private static final long WORD_CLICK_GUARD_MS = 350L;
    private static final long RESULT_CLICK_GUARD_MS = 450L;
    private static final long BACK_HINT_INTERVAL_MS = 2200L;
    private static final int MAX_SEARCH_HISTORY = 20;
    private static final int MAX_SUGGESTION_RESPONSE_BYTES = 512 * 1024;
    private static final long SEARCH_TIMEOUT_MS = 16000L;
    private static final long RETRY_TIMEOUT_MS = 10000L;
    private static final int MAX_SOURCE_ATTEMPTS = 2;
    private static final long SOURCE_RETRY_DELAY_MS = 2000L;
    private static final long RESULT_MERGE_DELAY_MS = 280L;
    private static final int MAX_SOURCE_RESULTS = 30;
    private static final int MAX_TOTAL_RESULTS = 200;
    private static final Pattern AGGREGATE_NOTE = Pattern.compile("(搜索|结果|资源|合集)\\s*有?共?\\s*\\d+\\s*(条|个文件|部)");
    private static final Pattern MATCH_SEPARATOR = Pattern.compile("[\\s·\\-:：_—/()（）\\[\\]【】《》<>]+");

    private final Runnable searchTimeout = new Runnable() {
        @Override
        public void run() {
            if (!searchFinished && searchResultAdapter != null && !destroying && !isFinishing() && !isDestroyed()) {
                searchTimedOut = true;
                finalizeSearchSession();
                updateSearchProgress();
            }
        }
    };

    private final Runnable retryTimeout = new Runnable() {
        @Override
        public void run() {
            if (searchFinished || destroying || isFinishing() || isDestroyed()) return;
            searchTimedOut = true;
            finalizeSearchSession();
            updateSearchProgress();
        }
    };

    private static ThreadFactory daemonThreadFactory(final String prefix) {
        return new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + index.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_search;
    }

    @Override
    protected void init() {
        spDataUtil = new SPDataUtil(SearchActivity.this);
        initView();
        initViewModel();
        initData();
    }

    @Override
    protected void onReload() {
        if (destroying || isFinishing() || isDestroyed()) return;
        if (folderMode) {
            loadFolder(folderId, folderSourceKey, folderTitle);
        } else if (!TextUtils.isEmpty(searchWord)) {
            search(searchWord, true);
        } else {
            requestHotWords(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        resumeSearchPresentation();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) resumeSearchPresentation();
    }

    private void resumeSearchPresentation() {
        scheduleMergeResults();
        updateSearchProgress();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        stopSearchRequests();
        cancelSuggestions();
        dismissActiveDialogs();
        if (folderMode && (intent == null || !intent.hasExtra("folderId"))) {
            exitFolderModeUi();
        }
        initData();
    }

    private int resolveSearchGridColumns() {
        return SeniorMode.isEnabled() ? 2 : 3;
    }

    private void exitFolderModeUi() {
        folderMode = false;
        folderRequestId = -1;
        folderId = "";
        folderSourceKey = "";
        folderTitle = "";
        if (llSearchLeftPanel != null) llSearchLeftPanel.setVisibility(View.VISIBLE);
        if (llFolderHeader != null) llFolderHeader.setVisibility(View.GONE);
        if (mSearchResultRV != null) {
            if (Hawk.get(HawkConfig.SEARCH_VIEW, 1) == 0) {
                mSearchResultRV.setLayoutManager(SafeTvLayoutManagers.linear(this.mContext, 1, false));
            } else {
                mSearchResultRV.setLayoutManager(SafeTvLayoutManagers.grid(this.mContext, resolveSearchGridColumns()));
            }
        }
        if (searchResultAdapter != null) {
            searchResultAdapter.setNewData(new ArrayList<>());
        }
    }

    private void initView() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        llSearchRoot = findViewById(R.id.llSearchRoot);
        ivBack = findViewById(R.id.back_tv);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }
        llSearchLeftPanel = findViewById(R.id.llSearchLeftPanel);
        llFolderHeader = findViewById(R.id.llFolderHeader);
        tvFolderModeTitle = findViewById(R.id.tvFolderModeTitle);
        tvFolderModeSubtitle = findViewById(R.id.tvFolderModeSubtitle);
        View btnFolderModeBack = findViewById(R.id.btnFolderModeBack);
        if (btnFolderModeBack != null) {
            btnFolderModeBack.setOnClickListener(v -> finish());
        }
        llWord = findViewById(R.id.llWord);
        tvHotLabel = findViewById(R.id.hot_lable_tv);
        etSearch = findViewById(R.id.etSearch);
        etSearch.setOnClickListener(v -> showSoftKeyboard(etSearch));
        ivClearInput = findViewById(R.id.ivClearInput);
        if (ivClearInput != null) {
            ivClearInput.setOnClickListener(v -> {
                hideSoftKeyboard();
                clearInputAndResetSearch();
            });
        }
        tvSearch = findViewById(R.id.tvSearch);
        llSearchStatusBar = findViewById(R.id.llSearchStatusBar);
        pbSearchLoading = findViewById(R.id.pbSearchLoading);
        tvSearchProgress = findViewById(R.id.tvSearchProgress);
        btnCloudLoginTop = findViewById(R.id.btnCloudLoginTop);
        btnManageSearchSourcesTop = findViewById(R.id.btnManageSearchSourcesTop);
        tvClear = findViewById(R.id.tvClear);
        mSearchResultRV = findViewById(R.id.sortRV);
        keyboard = findViewById(R.id.keyBoardRoot);
        remoteRoot = findViewById(R.id.remoteRoot);
        btnBackToKeyboard = findViewById(R.id.btnBackToKeyboard);
        tvAddress = findViewById(R.id.tvAddress);
        ivQRCode = findViewById(R.id.ivQRCode);
        if (ivQRCode != null) {
            ivQRCode.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> generateRemoteQRCode());
        }
        llSearchGuide = findViewById(R.id.llSearchGuide);
        llSearchLoading = findViewById(R.id.llSearchLoading);
        tvSearchLoadingTip = findViewById(R.id.tvSearchLoadingTip);
        mHotWordRV = findViewById(R.id.mGridViewWord);
        mSearchHistoryRV = findViewById(R.id.serch_history_rv);
        llClearHistory = findViewById(R.id.llClearHistory);
        tvHistoryHint = findViewById(R.id.tvHistoryHint);
        llSearchEmpty = findViewById(R.id.llSearchEmpty);
        tvSearchEmptyTip = findViewById(R.id.tvSearchEmptyTip);
        btnEmptyRetry = findViewById(R.id.btnEmptyRetry);
        btnEmptyManageSources = findViewById(R.id.btnEmptyManageSources);

        if (btnBackToKeyboard != null) {
            btnBackToKeyboard.setOnClickListener(v -> showVirtualKeyboardPanel());
        }
        if (btnEmptyRetry != null) {
            btnEmptyRetry.setOnClickListener(v -> {
                FastClickCheckUtil.check(v, 350);
                if (folderMode) {
                    loadFolder(folderId, folderSourceKey, folderTitle);
                } else if (!TextUtils.isEmpty(searchWord)) {
                    search(searchWord, true);
                } else {
                    searchClick();
                }
            });
        }

        View.OnClickListener openManageSources = v -> {
            FastClickCheckUtil.check(v);
            if (destroying || isFinishing() || isDestroyed()) return;
            if (activeSearchSiteDialog != null && activeSearchSiteDialog.isShowing()) return;
            final boolean[] changed = {false};
            SearchSiteDialog dialog = new SearchSiteDialog(SearchActivity.this);
            dialog.setOnChanged(() -> changed[0] = true);
            dialog.init();
            dialog.setOnDismissListener(d -> {
                if (activeSearchSiteDialog != dialog) return;
                activeSearchSiteDialog = null;
                if (destroying || isFinishing() || isDestroyed()) return;
                if (changed[0] && !folderMode && !TextUtils.isEmpty(searchWord)) {
                    search(searchWord, true);
                } else {
                    resumeSearchPresentation();
                }
            });
            activeSearchSiteDialog = dialog;
            dialog.show();
        };
        if (btnEmptyManageSources != null) {
            btnEmptyManageSources.setOnClickListener(openManageSources);
        }
        if (btnCloudLoginTop != null) {
            btnCloudLoginTop.setVisibility(SpiderActionHelper.hasCloudConfigSites() ? View.VISIBLE : View.GONE);
            btnCloudLoginTop.setOnClickListener(v -> {
                FastClickCheckUtil.check(v);
                if (destroying || isFinishing() || isDestroyed()) return;
                if (activeCloudDriveDialog != null && activeCloudDriveDialog.isShowing()) return;
                CloudDriveDialog dialog = new CloudDriveDialog(SearchActivity.this);
                dialog.setOnDismissListener(d -> {
                    if (activeCloudDriveDialog != dialog) return;
                    activeCloudDriveDialog = null;
                    if (!destroying && !isFinishing() && !isDestroyed()) {
                        resumeSearchPresentation();
                    }
                });
                activeCloudDriveDialog = dialog;
                dialog.show();
            });
        }
        if (btnManageSearchSourcesTop != null) {
            btnManageSearchSourcesTop.setOnClickListener(openManageSources);
        }
        initHistoryView();
        initHotRecycleView();
        initSearchResultRecycleView();

        View.OnTouchListener hideImeOnDown = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                hideSoftKeyboard();
            }
            return false;
        };
        if (llSearchRoot != null) {
            llSearchRoot.setOnTouchListener(hideImeOnDown);
        }
        mSearchResultRV.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                hideSoftKeyboard();
                if (searchResultAdapter != null && !searchResultAdapter.getData().isEmpty()) {
                    userScrolledResults = true;
                }
            }
            return false;
        });
        mHotWordRV.setOnTouchListener(hideImeOnDown);
        mSearchHistoryRV.setOnTouchListener(hideImeOnDown);

        tvSearch.setOnClickListener(v -> {
            FastClickCheckUtil.check(v, 350);
            searchClick();
        });
        tvClear.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            clearHistory();
        });
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    actionId == EditorInfo.IME_ACTION_NEXT ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getAction() == KeyEvent.ACTION_DOWN
                            && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                searchClick();
                return true;
            }
            return false;
        });
        keyboard.setOnInteractListener(this::hideSoftKeyboard);
        keyboard.setOnSearchKeyListener((pos, key) -> {
            L.d(pos + " key = " + key);
            if (pos == SearchKeyboard.KEY_POS_CLEAR) {
                clearInputAndResetSearch();
            } else if (pos == SearchKeyboard.KEY_POS_DELETE) {
                deleteAtCursor();
            } else if (pos == SearchKeyboard.KEY_POS_REMOTE) {
                showRemoteQRCodePanel();
            } else if (pos == SearchKeyboard.KEY_POS_SEARCH) {
                View focused = getCurrentFocus();
                searchClick(isChildOf(focused, keyboard) ? focused : null);
            } else if (pos > SearchKeyboard.KEY_POS_DELETE) {
                insertAtCursor(key);
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
                updateClearInputIcon(editable);
                if (suppressSuggestionWatcher) return;
                // 中文输入法拼音组词中间态（Composing）不触发高频联想请求
                if (BaseInputConnection.getComposingSpanStart(editable) != -1) {
                    return;
                }
                loadPinyinResult(editable.toString().trim());
            }
        });
    }

    private void updateClearInputIcon(CharSequence text) {
        if (ivClearInput != null) {
            ivClearInput.setVisibility(text != null && text.length() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void insertAtCursor(String key) {
        if (etSearch == null || key == null) return;
        Editable editable = etSearch.getText();
        if (editable == null) {
            etSearch.setText(key);
            etSearch.setSelection(key.length());
            return;
        }
        BaseInputConnection.removeComposingSpans(editable);
        int start = etSearch.getSelectionStart();
        int end = etSearch.getSelectionEnd();
        int len = editable.length();
        if (start < 0 || end < 0) {
            start = len;
            end = len;
        }
        int from = Math.max(0, Math.min(start, end));
        int to = Math.min(len, Math.max(start, end));
        editable.replace(from, to, key);
        int nextPos = Math.min(editable.length(), from + key.length());
        etSearch.setSelection(nextPos);
    }

    private void deleteAtCursor() {
        if (etSearch == null) return;
        Editable editable = etSearch.getText();
        if (editable == null || editable.length() == 0) return;
        BaseInputConnection.removeComposingSpans(editable);
        int start = etSearch.getSelectionStart();
        int end = etSearch.getSelectionEnd();
        int len = editable.length();
        if (start < 0 || end < 0) {
            start = len;
            end = len;
        }
        int from = Math.max(0, Math.min(start, end));
        int to = Math.min(len, Math.max(start, end));
        if (from != to) {
            editable.delete(from, to);
            etSearch.setSelection(Math.min(editable.length(), from));
        } else if (from > 0) {
            editable.delete(from - 1, from);
            etSearch.setSelection(Math.min(editable.length(), from - 1));
        }
    }

    /** 清空输入框时同步终止未完成的上一轮搜索并恢复初始引导与热词 */
    private void clearInputAndResetSearch() {
        stopSearchRequests();
        cancelSuggestions();
        searchWord = "";
        if (etSearch != null && etSearch.length() > 0) {
            suppressSuggestionWatcher = true;
            etSearch.setText("");
            suppressSuggestionWatcher = false;
        }
        wordAdapter.setNewData(new ArrayList<>());
        updateHotWordPanel(false);
        requestHotWords(false);
        if (searchResultAdapter != null) {
            searchResultAdapter.setNewData(new ArrayList<>());
        }
        resetResultItemScale();
        if (llSearchStatusBar != null) llSearchStatusBar.setVisibility(View.GONE);
        if (llSearchLoading != null) llSearchLoading.setVisibility(View.GONE);
        if (llSearchEmpty != null) llSearchEmpty.setVisibility(View.GONE);
        if (llSearchGuide != null && !folderMode) llSearchGuide.setVisibility(View.VISIBLE);
        if (mSearchResultRV != null) mSearchResultRV.setVisibility(View.INVISIBLE);
    }

    private void showRemoteQRCodePanel() {
        hideSoftKeyboard();
        if (keyboard != null) keyboard.setVisibility(View.GONE);
        if (remoteRoot != null) remoteRoot.setVisibility(View.VISIBLE);
        refreshQRCode();
        if (btnBackToKeyboard != null) {
            btnBackToKeyboard.post(() -> {
                if (!destroying && !isFinishing() && !isDestroyed() && btnBackToKeyboard.isShown()) {
                    btnBackToKeyboard.requestFocus();
                }
            });
        }
    }

    private void showVirtualKeyboardPanel() {
        closeRemotePanel();
        if (keyboard != null) keyboard.requestRestoredFocus();
    }

    private void closeRemotePanel() {
        if (keyboard != null) keyboard.setVisibility(View.VISIBLE);
        if (remoteRoot != null) remoteRoot.setVisibility(View.GONE);
    }

    /**
     * 热词/联想列有内容才显示：没有内容时收起来，把宽度让给搜索结果
     */
    private void updateHotWordPanel(boolean isSuggestion) {
        if (llWord == null) {
            return;
        }
        if (tvHotLabel != null) {
            tvHotLabel.setText(isSuggestion ? "搜索联想" : "热门搜索");
        }
        boolean empty = folderMode || wordAdapter == null || NullUtil.isNull(wordAdapter.getData());
        int targetVisibility = empty ? View.GONE : View.VISIBLE;
        if (llWord.getVisibility() != targetVisibility) {
            llWord.setVisibility(targetVisibility);
        }
    }

    private void resetResultItemScale() {
        if (lastScaledResultItem != null) {
            lastScaledResultItem.animate().cancel();
            lastScaledResultItem.setScaleX(1.0f);
            lastScaledResultItem.setScaleY(1.0f);
            lastScaledResultItem = null;
        }
    }

    private void initSearchResultRecycleView() {
        mSearchResultRV.setHasFixedSize(true);
        mSearchResultRV.setItemAnimator(null);
        searchResultAdapter = new SearchResultAdapter();
        final boolean listMode = searchResultAdapter.isListView();
        if (listMode) {
            mSearchResultRV.setLayoutManager(SafeTvLayoutManagers.linear(this.mContext, 1, false));
        } else {
            mSearchResultRV.setLayoutManager(SafeTvLayoutManagers.grid(this.mContext, resolveSearchGridColumns()));
        }
        mSearchResultRV.setAdapter(searchResultAdapter);
        // 列表视图无海报控件，跳过预加载器节省网络和解码资源
        if (!listMode) {
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
        }
        mSearchResultRV.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    userScrolledResults = true;
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    userScrolledResults = true;
                }
            }
        });
        mSearchResultRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null && !listMode && !SeniorMode.isEnabled()) {
                    itemView.animate().cancel();
                    itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                    if (lastScaledResultItem == itemView) {
                        lastScaledResultItem = null;
                    }
                }
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null && !listMode && !SeniorMode.isEnabled()) {
                    if (lastScaledResultItem != null && lastScaledResultItem != itemView) {
                        lastScaledResultItem.animate().cancel();
                        lastScaledResultItem.setScaleX(1.0f);
                        lastScaledResultItem.setScaleY(1.0f);
                    }
                    itemView.animate().cancel();
                    itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
                    lastScaledResultItem = itemView;
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null) FastClickCheckUtil.check(itemView);
                openSearchResult(position);
            }
        });
        searchResultAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            openSearchResult(position);
        });
    }

    private void openSearchResult(int position) {
        if (destroying || isFinishing() || isDestroyed()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastResultOpenAt < RESULT_CLICK_GUARD_MS) return;
        lastResultOpenAt = now;
        if (searchResultAdapter == null || position < 0 || position >= searchResultAdapter.getData().size()) return;
        Movie.Video video = searchResultAdapter.getData().get(position);
        if (video == null) return;
        if (SpiderActionHelper.isActionItem(video)) {
            SpiderActionHelper.handleVideoAction(SearchActivity.this, video, null);
            return;
        }
        boolean opensFolder = isFolder(video) || isAggregateSearchCard(video);
        android.util.Log.i(TRACE_TAG, "click activity=" + System.identityHashCode(this)
                + " position=" + position + " route=" + (opensFolder ? "folder" : "detail")
                + " explicitFolder=" + isFolder(video)
                + " hasTag=" + !TextUtils.isEmpty(video.tag)
                + " hasPlayableUrls=" + hasPlayableUrls(video)
                + " noteMatches=" + (!TextUtils.isEmpty(video.note) && AGGREGATE_NOTE.matcher(video.note).find()));
        if (opensFolder) {
            if (folderMode) {
                stopSearchRequests();
                cancelSuggestions();
                Bundle folder = new Bundle();
                folder.putString("folderId", video.id);
                folder.putString("folderSourceKey", video.sourceKey);
                folder.putString("title", video.name);
                jumpActivity(SearchActivity.class, folder);
                return;
            }
            if (activeFolderDialog != null && activeFolderDialog.isShowing()) {
                return;
            }
            // 打开聚合目录浮层时不掐断底层搜源，弹窗关闭时自动合并期间新到达的结果
            FolderDialog dialog = new FolderDialog(SearchActivity.this, sourceViewModel, video);
            dialog.setOnDismissListener(d -> {
                if (activeFolderDialog != dialog) return;
                activeFolderDialog = null;
                if (!destroying && !isFinishing() && !isDestroyed()) {
                    resumeSearchPresentation();
                }
            });
            activeFolderDialog = dialog;
            dialog.show();
            return;
        }
        if (TextUtils.isEmpty(video.id)) {
            Toast.makeText(mContext, "该条目缺少影片编号，无法打开详情", Toast.LENGTH_SHORT).show();
            return;
        }
        // 进入详情页不再强行终止未完成的搜索源，返回搜索页时可无缝看到后续补充结果
        Bundle bundle = new Bundle();
        bundle.putString("id", video.id);
        bundle.putString("sourceKey", video.sourceKey);
        bundle.putString("note", video.note);
        bundle.putString("movieName", video.name);
        bundle.putString("poster", video.pic);
        jumpActivity(DetailActivity.class, bundle);
    }

    public static boolean isFolderItem(Movie.Video video) {
        if (video == null || TextUtils.isEmpty(video.id)) return false;
        return "folder".equalsIgnoreCase(video.tag) || "floder".equalsIgnoreCase(video.tag);
    }

    public static boolean isAggregateItem(Movie.Video video) {
        if (video == null || TextUtils.isEmpty(video.id)) return false;
        if ("aggregate".equalsIgnoreCase(video.tag)
                || "search".equalsIgnoreCase(video.tag)
                || "search_result".equalsIgnoreCase(video.tag)) {
            return true;
        }
        if (hasPlayableUrls(video)) {
            return false;
        }
        // 仅非直连采集源（如盘搜/聚合爬虫）且备注匹配聚合结果数时判定为聚合目录，避免误判普通 CMS 直连源
        SiteBean site = video.sourceKey == null ? null : ApiConfig.getInstance().getSiteBean(video.sourceKey);
        if (site != null && (site.getType() == 0 || site.getType() == 1)) {
            return false;
        }
        return !TextUtils.isEmpty(video.note) && AGGREGATE_NOTE.matcher(video.note).find();
    }

    private boolean isFolder(Movie.Video video) {
        return isFolderItem(video);
    }

    private boolean isAggregateSearchCard(Movie.Video video) {
        return isAggregateItem(video);
    }

    private static boolean hasPlayableUrls(Movie.Video video) {
        if (video == null) {
            return false;
        }
        if (video.hasPlayableUrls) {
            return true;
        }
        if (video.urlsBean == null || video.urlsBean.flagInfoList == null) {
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
        mHotWordRV.setItemAnimator(null);
        mHotWordRV.setLayoutManager(SafeTvLayoutManagers.linear(this.mContext, 1, false));
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
        wordAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view, 350);
            selectHotWord(position);
        });
    }

    private boolean allowWordClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastWordSelectAt < WORD_CLICK_GUARD_MS) {
            return false;
        }
        lastWordSelectAt = now;
        return true;
    }

    private void selectHotWord(int position) {
        if (!allowWordClick()) return;
        if (wordAdapter == null || position < 0 || position >= wordAdapter.getData().size()) return;
        String word = wordAdapter.getItem(position);
        if (word == null || word.trim().isEmpty()) return;
        hideSoftKeyboard();
        if (tvSearch != null && tvSearch.getVisibility() == View.VISIBLE) {
            tvSearch.requestFocus();
        }
        search(word);
    }

    private void initHistoryView() {
        mSearchHistoryRV.setHasFixedSize(true);
        mSearchHistoryRV.setItemAnimator(null);
        mSearchHistoryRV.setLayoutManager(SafeTvLayoutManagers.linear(this.mContext, LinearLayoutManager.HORIZONTAL,
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
        historyAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view, 350);
            selectHistoryWord(position);
        });
        historyAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            if (position >= 0 && position < historyAdapter.getData().size()) {
                String item = historyAdapter.getItem(position);
                deleteSingleHistoryItem(item);
            }
            return true;
        });
        // 进程内已有历史缓存时首帧直接同步渲染，避免异步展开导致键盘突然往下跳
        List<String> preloaded = sHistoryCache;
        if (preloaded != null) {
            historyLoaded = true;
            cachedHistoryWords.clear();
            cachedHistoryWords.addAll(preloaded);
            renderHistoryView(cachedHistoryWords);
        } else {
            renderHistoryView(Collections.emptyList());
            loadHistoryAsync();
        }
    }

    private void selectHistoryWord(int position) {
        if (!allowWordClick()) return;
        if (historyAdapter == null || position < 0 || position >= historyAdapter.getData().size()) return;
        String word = historyAdapter.getItem(position);
        if (word == null || word.trim().isEmpty()) return;
        hideSoftKeyboard();
        if (tvSearch != null && tvSearch.getVisibility() == View.VISIBLE) {
            tvSearch.requestFocus();
        }
        search(word);
    }

    static List<String> sanitizeHistoryWords(Object raw, int maxSize) {
        List<String> result = new ArrayList<>();
        if (!(raw instanceof List<?>)) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (Object item : (List<?>) raw) {
            if (!(item instanceof String)) continue;
            String word = ((String) item).trim();
            if (word.isEmpty()) continue;
            String lower = word.toLowerCase(Locale.ROOT);
            if (seen.add(lower)) {
                result.add(word);
                if (result.size() >= maxSize) break;
            }
        }
        return result;
    }

    private void loadHistoryAsync() {
        if (historyIoExecutor.isShutdown() || spDataUtil == null) return;
        try {
            historyIoExecutor.execute(() -> {
                Object raw = spDataUtil.getObjectValue("histroys");
                List<String> cleaned = sanitizeHistoryWords(raw, MAX_SEARCH_HISTORY);
                sHistoryCache = new ArrayList<>(cleaned);
                boolean needsRewrite = raw instanceof List<?> && ((List<?>) raw).size() != cleaned.size();
                if (needsRewrite) {
                    spDataUtil.saveObjectValue("histroys", new ArrayList<>(cleaned));
                }
                searchHandler.post(() -> {
                    if (destroying || isFinishing() || isDestroyed()) return;
                    if (!historyLoaded) {
                        historyLoaded = true;
                        if (cachedHistoryWords.isEmpty()) {
                            cachedHistoryWords.addAll(cleaned);
                        } else {
                            List<String> merged = new ArrayList<>(cachedHistoryWords);
                            merged.addAll(cleaned);
                            List<String> deduped = sanitizeHistoryWords(merged, MAX_SEARCH_HISTORY);
                            cachedHistoryWords.clear();
                            cachedHistoryWords.addAll(deduped);
                            sHistoryCache = new ArrayList<>(deduped);
                        }
                        renderHistoryView(cachedHistoryWords);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    /** 在单线程 IO 队列中先读取磁盘老历史再合并写入，彻底杜绝带参冷启动时覆盖丢失已有历史 */
    private void addHistoryWordPersistAsync(final String newWord) {
        if (historyIoExecutor.isShutdown() || spDataUtil == null) return;
        try {
            historyIoExecutor.execute(() -> {
                List<String> base = sHistoryCache != null
                        ? new ArrayList<>(sHistoryCache)
                        : sanitizeHistoryWords(spDataUtil.getObjectValue("histroys"), MAX_SEARCH_HISTORY);
                List<String> combined = new ArrayList<>(base.size() + 1);
                combined.add(newWord);
                combined.addAll(base);
                List<String> deduped = sanitizeHistoryWords(combined, MAX_SEARCH_HISTORY);
                sHistoryCache = new ArrayList<>(deduped);
                spDataUtil.saveObjectValue("histroys", new ArrayList<>(deduped));
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void persistHistoryExactAsync(List<String> snapshot) {
        sHistoryCache = new ArrayList<>(snapshot);
        if (historyIoExecutor.isShutdown() || spDataUtil == null) return;
        try {
            historyIoExecutor.execute(() -> spDataUtil.saveObjectValue("histroys", new ArrayList<>(snapshot)));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void deleteSingleHistoryItem(String wd) {
        if (wd == null || wd.trim().isEmpty()) return;
        boolean removed = false;
        for (int i = 0; i < cachedHistoryWords.size(); i++) {
            String item = cachedHistoryWords.get(i);
            if (item != null && item.equalsIgnoreCase(wd)) {
                cachedHistoryWords.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            renderHistoryView(cachedHistoryWords);
            persistHistoryExactAsync(new ArrayList<>(cachedHistoryWords));
            Toast.makeText(mContext, "已删除搜索词：" + wd, Toast.LENGTH_SHORT).show();
            if (cachedHistoryWords.isEmpty()) {
                if (keyboard != null && keyboard.isShown()) {
                    keyboard.requestRestoredFocus();
                } else if (tvSearch != null) {
                    tvSearch.requestFocus();
                }
            }
        }
    }

    private void renderHistoryView(List<String> historyWords) {
        boolean empty = historyWords == null || historyWords.isEmpty();
        historyAdapter.setNewData(empty ? new ArrayList<>() : new ArrayList<>(historyWords));
        mSearchHistoryRV.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (llClearHistory != null) {
            llClearHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (tvHistoryHint != null) tvHistoryHint.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    private void saveHistoryWords(String wd) {
        if (wd == null) return;
        String trimmed = wd.trim();
        if (trimmed.isEmpty()) return;
        for (int i = 0; i < cachedHistoryWords.size(); i++) {
            String item = cachedHistoryWords.get(i);
            if (item != null && item.equalsIgnoreCase(trimmed)) {
                cachedHistoryWords.remove(i);
                break;
            }
        }
        cachedHistoryWords.add(0, trimmed);
        if (cachedHistoryWords.size() > MAX_SEARCH_HISTORY) {
            cachedHistoryWords.subList(MAX_SEARCH_HISTORY, cachedHistoryWords.size()).clear();
        }
        renderHistoryView(cachedHistoryWords);
        if (historyLoaded) {
            persistHistoryExactAsync(new ArrayList<>(cachedHistoryWords));
        } else {
            addHistoryWordPersistAsync(trimmed);
        }
    }

    private void clearHistory() {
        if (destroying || isFinishing() || isDestroyed()) return;
        if (activeTipDialog != null && activeTipDialog.isShowing()) return;
        TipDialog dialog = TipDialog.confirm(this, "清空搜索历史", "确定要清空全部搜索历史记录吗？", "清空", this::doClearHistory);
        dialog.setOnDismissListener(d -> {
            if (activeTipDialog != dialog) return;
            activeTipDialog = null;
            resumeSearchPresentation();
        });
        activeTipDialog = dialog;
    }

    private void doClearHistory() {
        historyLoaded = true;
        cachedHistoryWords.clear();
        renderHistoryView(cachedHistoryWords);
        persistHistoryExactAsync(new ArrayList<>());
        if (keyboard != null && keyboard.isShown()) {
            keyboard.requestRestoredFocus();
        } else if (tvSearch != null) {
            tvSearch.requestFocus();
        }
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.listResult.observe(this, absXml -> {
            if (folderRequestId < 0 || sourceViewModel.getLastPostedCategoryRequestId() != folderRequestId) return;
            android.util.Log.i(TRACE_TAG, "folder response activity=" + System.identityHashCode(this)
                    + " request=" + folderRequestId);
            if (llSearchGuide != null) llSearchGuide.setVisibility(View.GONE);
            if (llSearchLoading != null) llSearchLoading.setVisibility(View.GONE);
            if (absXml == null || absXml.movie == null || absXml.movie.videoList == null
                    || absXml.movie.videoList.isEmpty()) {
                if (tvFolderModeSubtitle != null) tvFolderModeSubtitle.setText("目录加载超时或暂无可用资源");
                if (llSearchEmpty != null) {
                    llSearchEmpty.setVisibility(View.VISIBLE);
                    if (tvSearchEmptyTip != null) tvSearchEmptyTip.setText("该聚合目录加载超时或暂无可播放资源");
                    if (btnEmptyRetry != null) {
                        btnEmptyRetry.setVisibility(View.VISIBLE);
                        btnEmptyRetry.requestFocus();
                    }
                    if (btnEmptyManageSources != null) btnEmptyManageSources.setVisibility(View.GONE);
                }
                mSearchResultRV.setVisibility(View.GONE);
            } else {
                if (llSearchEmpty != null) llSearchEmpty.setVisibility(View.GONE);
                searchResultAdapter.setNewData(new ArrayList<>(absXml.movie.videoList));
                if (tvFolderModeSubtitle != null) {
                    tvFolderModeSubtitle.setText("共 " + searchResultAdapter.getData().size() + " 项资源");
                }
                mSearchResultRV.setVisibility(View.VISIBLE);
                mSearchResultRV.scrollToPosition(0);
                mSearchResultRV.post(() -> {
                    if (destroying || isFinishing() || isDestroyed()) return;
                    mSearchResultRV.setSelection(0);
                    mSearchResultRV.requestFocus();
                });
            }
        });
    }

    private static String readSmallBody(okhttp3.Response response) throws IOException {
        if (response == null || response.body() == null) return "";
        long len = response.body().contentLength();
        if (len > MAX_SUGGESTION_RESPONSE_BYTES) {
            response.close();
            throw new IOException("响应过大");
        }
        try (InputStream in = response.body().byteStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int total = 0;
            int read;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > MAX_SUGGESTION_RESPONSE_BYTES) {
                    throw new IOException("响应超限");
                }
                out.write(buf, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** 清理热词/联想标题：保留连字符 `-` 与中英文词间空格，避免破坏 X-Men / 007-幽灵党 / Spider Man */
    static String cleanHotWordTitle(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("[<>《》]", "").replaceAll("\\s+", " ").trim();
    }

    private void loadPinyinResult(String key) {
        final int requestId = ++suggestionRequestId;
        if (suggestionRunnable != null) {
            searchHandler.removeCallbacks(suggestionRunnable);
            suggestionRunnable = null;
        }
        OkGo.getInstance().cancelTag(suggestionTag);
        if (TextUtils.isEmpty(key)) {
            requestHotWords(true);
            return;
        }

        suggestionRunnable = () -> {
            if (requestId != suggestionRequestId
                    || destroying || isFinishing() || isDestroyed()
                    || etSearch == null
                    || !key.equals(etSearch.getText().toString().trim())) {
                return;
            }
            OkGo.<ArrayList<String>>get("https://suggest.video.iqiyi.com/")
                    .tag(suggestionTag)
                    .retryCount(0)
                    .params("if", "mobile")
                    .params("key", key)
                    .execute(new AbsCallback<ArrayList<String>>() {
                        @Override
                        public ArrayList<String> convertResponse(okhttp3.Response response) throws Throwable {
                            LinkedHashSet<String> deduped = new LinkedHashSet<>();
                            String result = readSmallBody(response);
                            if (TextUtils.isEmpty(result)) return new ArrayList<>();
                            JsonElement root = JsonParser.parseString(result);
                            if (root == null || !root.isJsonObject()) return new ArrayList<>();
                            JsonElement dataEl = root.getAsJsonObject().get("data");
                            if (dataEl == null || !dataEl.isJsonArray()) return new ArrayList<>();
                            JsonArray itemList = dataEl.getAsJsonArray();
                            for (JsonElement ele : itemList) {
                                if (ele == null || !ele.isJsonObject()) continue;
                                JsonElement nameEl = ele.getAsJsonObject().get("name");
                                if (nameEl == null || nameEl.isJsonNull()) continue;
                                String cleaned = cleanHotWordTitle(nameEl.getAsString());
                                if (!cleaned.isEmpty()) {
                                    deduped.add(cleaned);
                                }
                            }
                            return new ArrayList<>(deduped);
                        }

                        @Override
                        public void onSuccess(Response<ArrayList<String>> response) {
                            if (requestId != suggestionRequestId
                                    || etSearch == null
                                    || !key.equals(etSearch.getText().toString().trim())
                                    || destroying || isFinishing() || isDestroyed()) {
                                return;
                            }
                            ArrayList<String> hots = response.body();
                            wordAdapter.setNewData(hots == null ? new ArrayList<>() : hots);
                            updateHotWordPanel(true);
                        }

                        @Override
                        public void onError(Response<ArrayList<String>> response) {
                            // 请求失败时清理旧词的过期联想，避免图文不符
                            if (requestId != suggestionRequestId
                                    || etSearch == null
                                    || !key.equals(etSearch.getText().toString().trim())
                                    || destroying || isFinishing() || isDestroyed()) {
                                return;
                            }
                            wordAdapter.setNewData(new ArrayList<>());
                            updateHotWordPanel(true);
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
            if (key != null && !key.trim().isEmpty()) {
                search(key);
                return;
            }
        }
        clearInputAndResetSearch();
        if (remoteRoot != null && remoteRoot.isShown()) closeRemotePanel();
        if (keyboard != null && keyboard.isShown()) {
            keyboard.requestLetterFocus();
        } else if (tvSearch != null) {
            tvSearch.requestFocus();
        }
    }

    private void loadFolder(String id, String sourceKey, String title) {
        folderRequestId = -1;
        this.folderId = id == null ? "" : id;
        this.folderSourceKey = sourceKey == null ? "" : sourceKey;
        this.folderTitle = title == null ? "" : title;
        if (folderMode) {
            if (llSearchLeftPanel != null) llSearchLeftPanel.setVisibility(View.GONE);
            if (llWord != null) llWord.setVisibility(View.GONE);
            if (llSearchStatusBar != null) llSearchStatusBar.setVisibility(View.GONE);
            if (llSearchGuide != null) llSearchGuide.setVisibility(View.GONE);
            if (llSearchEmpty != null) llSearchEmpty.setVisibility(View.GONE);
            if (llFolderHeader != null) llFolderHeader.setVisibility(View.VISIBLE);
            searchResultAdapter.setNewData(new ArrayList<>());
            mSearchResultRV.setVisibility(View.GONE);
            if (tvFolderModeTitle != null) tvFolderModeTitle.setText(TextUtils.isEmpty(title) ? "聚合目录" : title);
            if (tvFolderModeSubtitle != null) tvFolderModeSubtitle.setText("正在加载聚合目录…");
            if (llSearchLoading != null) {
                llSearchLoading.setVisibility(View.VISIBLE);
                if (tvSearchLoadingTip != null) tvSearchLoadingTip.setText("正在加载聚合目录…");
            }
            if (Hawk.get(HawkConfig.SEARCH_VIEW, 1) != 0 && mSearchResultRV != null) {
                int cols = SeniorMode.isEnabled() ? 4 : 5;
                mSearchResultRV.setLayoutManager(SafeTvLayoutManagers.grid(this.mContext, cols));
            }
        }
        if (folderId.trim().isEmpty() || folderSourceKey.trim().isEmpty()) {
            if (llSearchLoading != null) llSearchLoading.setVisibility(View.GONE);
            if (tvFolderModeSubtitle != null) tvFolderModeSubtitle.setText("目录信息不完整");
            if (llSearchEmpty != null) llSearchEmpty.setVisibility(View.VISIBLE);
            if (tvSearchEmptyTip != null) tvSearchEmptyTip.setText("目录信息不完整");
            if (btnEmptyRetry != null) btnEmptyRetry.setVisibility(View.GONE);
            if (btnEmptyManageSources != null) btnEmptyManageSources.setVisibility(View.GONE);
            View back = findViewById(R.id.btnFolderModeBack);
            if (back != null) back.requestFocus();
            return;
        }
        folderRequestId = sourceViewModel.getFolderList(sourceKey, id);
        android.util.Log.i(TRACE_TAG, "folder requested activity=" + System.identityHashCode(this)
                + " request=" + folderRequestId);
    }

    private static final int HOT_WORDS_VISIBLE = 10;
    private static final int HOT_WORDS_CACHE_LIMIT = 40;
    private static final long HOT_WORDS_MAX_AGE_MS = 24 * 60 * 60 * 1000L;
    private static final List<String> FALLBACK_HOT_WORDS = Arrays.asList("狂飙", "三体", "繁花", "庆余年", "黑镜", "漫长的季节");
    private static final Gson HOT_WORDS_GSON = new Gson();

    static final class HotWordsCache {
        @SerializedName("words") final List<String> words;
        @SerializedName("updatedAt") final long updatedAt;
        @SerializedName("sourceScope") final String sourceScope;

        HotWordsCache(List<String> words, long updatedAt, String sourceScope) {
            this.words = words;
            this.updatedAt = updatedAt;
            this.sourceScope = sourceScope;
        }

        boolean isFresh(long now) {
            return updatedAt > 0 && now >= updatedAt && now - updatedAt < HOT_WORDS_MAX_AGE_MS;
        }
    }

    private void requestHotWords(boolean allowWhenSearched) {
        if (!canShowHotWords(allowWhenSearched)) return;
        SiteBean homeSite = ApiConfig.getInstance().getHomeBean();
        String sourceScope = hotWordsSourceScope(homeSite);
        if (hotWordsLoadingScope != null && !sourceScope.equals(hotWordsLoadingScope)) {
            hotWordsRequestId++;
            hotWordsLoadingScope = null;
            if (hotRankTask != null) hotRankTask.cancel(true);
        }
        hotWordsAllowWhenSearched = allowWhenSearched;
        hotWordsOffset = nextHotWordsOffset;
        nextHotWordsOffset = (nextHotWordsOffset + HOT_WORDS_VISIBLE) & Integer.MAX_VALUE;

        long now = System.currentTimeMillis();
        HotWordsCache home = readHomeHotWords();
        HotWordsCache saved = readSavedHotWords();
        HotWordsCache cached = selectHotWordsCache(home, saved, sourceScope, now);
        showHotWords(cached == null ? FALLBACK_HOT_WORDS : cached.words);
        if ((cached != null && cached.isFresh(now)) || hotWordsLoadingScope != null) return;

        hotWordsLoadingScope = sourceScope;
        int requestId = ++hotWordsRequestId;
        hotRankTask = AppExecutors.io().submit(() -> {
            Set<String> words = new LinkedHashSet<>();
            for (String type : BilibiliHotRank.SEASON_TYPES) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    addHotWordTitles(words, BilibiliHotRank.load(type, 2000));
                } catch (RuntimeException e) {
                    L.w("读取影视热榜失败 " + e.getMessage());
                }
                if (words.size() >= 20) break;
            }
            searchHandler.post(() -> {
                if (!isHotWordsRequestActive(requestId)) return;
                hotRankTask = null;
                if (!words.isEmpty()) {
                    finishHotWordsRefresh(requestId, new ArrayList<>(words), null);
                } else {
                    requestHomeSourceHotWords(requestId, homeSite, sourceScope);
                }
            });
        });
    }

    private void requestHomeSourceHotWords(int requestId, SiteBean site, String sourceScope) {
        if (site == null || TextUtils.isEmpty(site.getKey())
                || !sourceScope.equals(hotWordsSourceScope(ApiConfig.getInstance().getHomeBean()))) {
            hotWordsLoadingScope = null;
            return;
        }
        try {
            sourceViewModel.getHomeRecList(site, null, videos -> {
                Set<String> words = new LinkedHashSet<>();
                addHotWordTitles(words, videos);
                searchHandler.post(() -> finishHotWordsRefresh(requestId, new ArrayList<>(words), sourceScope));
            });
        } catch (RuntimeException e) {
            hotWordsLoadingScope = null;
            L.w("读取站点推荐热词失败 " + e.getMessage());
        }
    }

    private void finishHotWordsRefresh(int requestId, List<String> words, String sourceScope) {
        if (!isHotWordsRequestActive(requestId)) return;
        hotWordsLoadingScope = null;
        if (words.isEmpty() || (sourceScope != null
                && !sourceScope.equals(hotWordsSourceScope(ApiConfig.getInstance().getHomeBean())))) {
            return;
        }
        try {
            HotWordsCache cache = new HotWordsCache(words, System.currentTimeMillis(), sourceScope);
            Hawk.put(HawkConfig.SEARCH_HOT_WORDS_CACHE, HOT_WORDS_GSON.toJson(cache));
        } catch (RuntimeException e) {
            L.w("保存搜索热词失败 " + e.getMessage());
        }
        // 首页可能在榜单请求途中刚拿到了更新的豆瓣片单。
        if (canShowHotWords(hotWordsAllowWhenSearched)) {
            HotWordsCache home = readHomeHotWords();
            showHotWords(home != null && home.isFresh(System.currentTimeMillis()) ? home.words : words);
        }
    }

    private static HotWordsCache readHomeHotWords() {
        try {
            String json = Hawk.get(HawkConfig.HOME_DOUBAN_CACHE, "");
            List<Movie.Video> videos = HOT_WORDS_GSON.fromJson(json, new TypeToken<List<Movie.Video>>() {}.getType());
            Set<String> words = new LinkedHashSet<>();
            addHotWordTitles(words, videos);
            if (!words.isEmpty()) {
                return new HotWordsCache(new ArrayList<>(words), Hawk.get(HawkConfig.HOME_DOUBAN_CACHE_UPDATED_AT, 0L), null);
            }
        } catch (RuntimeException e) {
            L.w("读取首页热词缓存失败 " + e.getMessage());
        }
        return null;
    }

    private static HotWordsCache readSavedHotWords() {
        try {
            String json = Hawk.get(HawkConfig.SEARCH_HOT_WORDS_CACHE, "");
            HotWordsCache cache = HOT_WORDS_GSON.fromJson(json, HotWordsCache.class);
            if (cache != null && cache.words != null && !cache.words.isEmpty()) return cache;
        } catch (RuntimeException e) {
            L.w("读取搜索热词缓存失败 " + e.getMessage());
        }
        return null;
    }

    static HotWordsCache selectHotWordsCache(HotWordsCache home, HotWordsCache saved, String sourceScope, long now) {
        if (home != null && home.isFresh(now)) return home;
        if (saved == null || (saved.sourceScope != null && !saved.sourceScope.equals(sourceScope))) return home;
        return saved.isFresh(now) || home == null || saved.updatedAt > home.updatedAt ? saved : home;
    }

    private static String hotWordsSourceScope(SiteBean site) {
        return Hawk.get(HawkConfig.API_URL, "") + "\n" + (site == null ? "" : site.getKey() + "\n" + site.getApi());
    }

    private static void addHotWordTitles(Set<String> words, List<Movie.Video> videos) {
        if (videos == null) return;
        for (Movie.Video video : videos) {
            if (words.size() >= HOT_WORDS_CACHE_LIMIT) return;
            if (video == null || video.isMore || isFolderItem(video)) continue;
            String title = cleanHotWordTitle(video.name);
            if (!title.isEmpty()) words.add(title);
        }
    }

    private void showHotWords(List<String> words) {
        List<String> batch = hotWordsBatch(words, hotWordsOffset);
        if (!batch.equals(wordAdapter.getData())) wordAdapter.setNewData(batch);
        updateHotWordPanel(false);
    }

    static List<String> hotWordsBatch(List<String> words, int offset) {
        int size = words.size();
        if (size <= HOT_WORDS_VISIBLE) return new ArrayList<>(words);
        List<String> batch = new ArrayList<>(HOT_WORDS_VISIBLE);
        int start = offset % size;
        for (int i = 0; i < HOT_WORDS_VISIBLE; i++) {
            batch.add(words.get((start + i) % size));
        }
        return batch;
    }

    private boolean isHotWordsRequestActive(int requestId) {
        return requestId == hotWordsRequestId && !destroying && !isFinishing() && !isDestroyed();
    }

    private boolean canShowHotWords(boolean allowWhenSearched) {
        return !folderMode && !destroying && !isFinishing() && !isDestroyed()
                && wordAdapter != null && etSearch != null && TextUtils.isEmpty(etSearch.getText().toString().trim())
                && (allowWhenSearched || TextUtils.isEmpty(searchWord));
    }

    static boolean isValidRemoteAddress(String address) {
        if (address == null) return false;
        try {
            URI uri = URI.create(address.trim());
            String host = uri.getHost();
            return "http".equalsIgnoreCase(uri.getScheme()) && host != null
                    && uri.getPort() > 0 && uri.getPort() <= 65535
                    && !"0.0.0.0".equals(host) && !host.startsWith("127.")
                    && !"localhost".equalsIgnoreCase(host) && host.indexOf(':') < 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void refreshQRCode() {
        if (tvAddress == null || ivQRCode == null) return;
        final String address = ControlManager.get().getAddress(false);
        if (!isValidRemoteAddress(address)) {
            remoteQrAddress = "";
            remoteQrSizePx = 0;
            ivQRCode.setImageDrawable(null);
            ivQRCode.setVisibility(View.INVISIBLE);
            tvAddress.setText("未获取到局域网地址，请确认电视已连接 Wi-Fi 或有线网络后重试");
            return;
        }
        if (!address.equals(remoteQrAddress)) {
            remoteQrAddress = address;
            remoteQrSizePx = 0;
            ivQRCode.setImageDrawable(null);
        }
        tvAddress.setText("手机与电视连接同一局域网\n扫码或在浏览器访问\n" + address);
        ivQRCode.setVisibility(View.VISIBLE);
        generateRemoteQRCode();
    }

    private void generateRemoteQRCode() {
        if (!ivQRCode.isShown() || remoteQrAddress.isEmpty()) return;
        int size = Math.min(ivQRCode.getWidth(), ivQRCode.getHeight());
        if (size < 32 || size == remoteQrSizePx) return;
        remoteQrSizePx = size;
        String address = remoteQrAddress;
        AppExecutors.io().execute(() -> {
            Bitmap bmp = QRCodeGen.generateBitmap(address, size, size, 4);
            searchHandler.post(() -> {
                if (destroying || isFinishing() || isDestroyed()) return;
                if (bmp != null && size == remoteQrSizePx && address.equals(remoteQrAddress)) {
                    ivQRCode.setImageBitmap(bmp);
                }
            });
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void searchEvent(ServerEvent event) {
        if (!folderMode && event != null && event.type == ServerEvent.SERVER_SEARCH
                && event.obj instanceof String) {
            String title = ((String) event.obj).trim();
            if (!title.isEmpty()) {
                dismissActiveDialogs();
                if (remoteRoot != null && remoteRoot.getVisibility() == View.VISIBLE) {
                    if (remoteRoot.hasFocus() && tvSearch != null) tvSearch.requestFocus();
                    closeRemotePanel();
                }
                search(title, true);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void refresh(RefreshEvent event) {
        if (event == null) return;
        if (event.type == RefreshEvent.TYPE_API_CONFIG_CHANGED) {
            // 切换接口订阅后清空旧配置的慢源惩罚记录
            SESSION_SLOW_SOURCES.clear();
            return;
        }
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            if (folderMode || event.requestId != searchRequestId || searchFinished
                    || destroying || isFinishing() || isDestroyed()) {
                return;
            }
            if (event.sourceKey == null) return;
            if (retryPhase ? !pendingRetryKeys.contains(event.sourceKey)
                    : initialCompletedKeys.contains(event.sourceKey)) return;
            boolean success = event.obj != null;
            if (success) {
                SESSION_SLOW_SOURCES.remove(event.sourceKey);
                try {
                    showSearchData((AbsXml) event.obj);
                } catch (Exception e) {
                    L.e("showSearchData " + e.getMessage());
                    success = false;
                }
            } else {
                SESSION_SLOW_SOURCES.add(event.sourceKey);
            }
            if (!retryPhase) {
                initialCompletedKeys.add(event.sourceKey);
                completedSearchSources++;
                if (success) {
                    succeededSourceKeys.add(event.sourceKey);
                } else {
                    failedSourceKeys.add(event.sourceKey);
                }
                if (completedSearchSources >= searchSourceCount) {
                    beginRetryPhase();
                    if (!retryInProgress) finalizeSearchSession();
                }
            } else if (success) {
                failedSourceKeys.remove(event.sourceKey);
                succeededSourceKeys.add(event.sourceKey);
                initialCompletedKeys.add(event.sourceKey);
                pendingRetryKeys.remove(event.sourceKey);
                checkRetryPhaseComplete();
            } else if (failedSourceKeys.contains(event.sourceKey)) {
                if (!retrySearchSource(event.sourceKey, event.requestId)) {
                    pendingRetryKeys.remove(event.sourceKey);
                    checkRetryPhaseComplete();
                }
            }
            updateSearchProgress();
        }
    }

    private void checkRetryPhaseComplete() {
        if (retryInProgress && pendingRetryKeys.isEmpty()) {
            finalizeSearchSession();
        }
    }

    private void finalizeSearchSession() {
        searchFinished = true;
        retryInProgress = false;
        pendingRetryKeys.clear();
        clearSourceRetryTasks();
        searchHandler.removeCallbacks(searchTimeout);
        searchHandler.removeCallbacks(retryTimeout);
        if (sourceViewModel != null) sourceViewModel.cancelSearchRequests();
        for (SourceViewModel.SearchTaskHandle task : searchTasks) {
            if (task != null) {
                task.cancel();
            }
        }
        searchTasks.clear();
    }

    private void hideSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            View view = getCurrentFocus();
            if (view == null) {
                view = etSearch != null ? etSearch : (getWindow() != null ? getWindow().getDecorView() : null);
            }
            if (view != null && view.getWindowToken() != null) {
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
        searchClick(null);
    }

    private void searchClick(View keyboardFocus) {
        hideSoftKeyboard();
        String wd = etSearch == null ? "" : etSearch.getText().toString().trim();
        if (!TextUtils.isEmpty(wd)) {
            search(wd, false, keyboardFocus);
        } else {
            Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void search(String wd) {
        search(wd, false);
    }

    private void search(String wd, boolean force) {
        search(wd, force, null);
    }

    private void search(String wd, boolean force, View keyboardFocus) {
        if (destroying || isFinishing() || isDestroyed()) return;
        String trimmed = wd == null ? "" : wd.trim();
        if (trimmed.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        if (!force && !searchFinished
                && trimmed.equals(lastSearchTriggerWord)
                && (now - lastSearchTriggerAt) < SEARCH_DUPLICATE_GUARD_MS) {
            return;
        }
        lastSearchTriggerAt = now;
        lastSearchTriggerWord = trimmed;

        hideSoftKeyboard();
        View focused = getCurrentFocus();
        if (focused != null && isChildOf(focused, llWord) && tvSearch != null) {
            tvSearch.requestFocus();
        }
        stopSearchRequests();
        keyboardSearchFocus = keyboardFocus;
        cancelSuggestions();
        wordAdapter.setNewData(new ArrayList<>());
        updateHotWordPanel(false);
        searchRequestId = SEARCH_REQUEST_IDS.incrementAndGet();
        searchFinished = false;
        searchTimedOut = false;
        retryInProgress = false;
        userScrolledResults = false;
        resultsTruncated = false;
        inFlightFilterTasks = 0;
        completedSearchSources = 0;
        searchSourceCount = 0;
        initialCompletedKeys.clear();
        succeededSourceKeys.clear();
        pendingRetryKeys.clear();
        sourceAttempts.clear();
        failedSourceKeys.clear();
        retryPhase = false;
        clearSourceRetryTasks();
        resetResultItemScale();
        this.searchWord = trimmed;
        if (llSearchGuide != null) llSearchGuide.setVisibility(View.GONE);
        if (llSearchEmpty != null) llSearchEmpty.setVisibility(View.GONE);
        if (etSearch != null) {
            suppressSuggestionWatcher = true;
            etSearch.setText(trimmed);
            etSearch.setSelection(etSearch.getText().length());
            updateClearInputIcon(trimmed);
            suppressSuggestionWatcher = false;
        }
        searchResultAdapter.setNewData(new ArrayList<>());
        mSearchResultRV.scrollToPosition(0);

        boolean started = doSearch();
        if (!started) {
            keyboardSearchFocus = null;
            return;
        }
        if (llSearchStatusBar != null) llSearchStatusBar.setVisibility(View.VISIBLE);
        if (pbSearchLoading != null) pbSearchLoading.setVisibility(View.VISIBLE);
        tvSearchProgress.setVisibility(View.VISIBLE);
        tvSearchProgress.setText("正在搜索「" + trimmed + "」 (0/" + searchSourceCount + " 源)…");
        if (llSearchLoading != null) {
            llSearchLoading.setVisibility(View.VISIBLE);
            if (tvSearchLoadingTip != null) tvSearchLoadingTip.setText("正在聚合搜索「" + trimmed + "」…");
        }
        mSearchResultRV.setVisibility(View.VISIBLE);
        if (searchSourceCount > 0) {
            searchHandler.postDelayed(searchTimeout, SEARCH_TIMEOUT_MS);
        }
        saveHistoryWords(trimmed);
    }

    private boolean doSearch() {
        List<SiteBean> siteBeans = ApiConfig.getInstance().getOrderedSearchSiteList();
        if (siteBeans.isEmpty()) {
            searchFinished = true;
            if (llSearchStatusBar != null) llSearchStatusBar.setVisibility(View.GONE);
            if (llSearchLoading != null) llSearchLoading.setVisibility(View.GONE);
            if (llSearchEmpty != null) {
                llSearchEmpty.setVisibility(View.VISIBLE);
                if (tvSearchEmptyTip != null) tvSearchEmptyTip.setText("当前未启用任何搜索源");
                if (btnEmptyRetry != null) btnEmptyRetry.setVisibility(View.GONE);
                if (btnEmptyManageSources != null) {
                    btnEmptyManageSources.setVisibility(View.VISIBLE);
                    final int generation = resultGeneration;
                    btnEmptyManageSources.post(() -> {
                        if (generation == resultGeneration && canFocusSearchOutcome(null)) {
                            btnEmptyManageSources.requestFocus();
                        }
                    });
                }
            }
            if (mSearchResultRV != null) mSearchResultRV.setVisibility(View.GONE);
            Toast.makeText(mContext, "未选择任何搜索源，请先在搜索源管理中勾选", Toast.LENGTH_LONG).show();
            return false;
        }
        searchSourceCount = siteBeans.size();
        activeSearchSites = new ArrayList<>(siteBeans);
        sourceOrder.clear();
        for (int i = 0; i < siteBeans.size(); i++) sourceOrder.put(siteBeans.get(i).getKey(), i);
        initialCompletedKeys.clear();
        succeededSourceKeys.clear();
        pendingRetryKeys.clear();
        sourceAttempts.clear();
        failedSourceKeys.clear();
        retryPhase = false;
        retryInProgress = false;
        submitSearchSources(siteBeans);
        return true;
    }

    private boolean retrySearchSource(String sourceKey, int requestId) {
        int attempt = sourceAttempts.containsKey(sourceKey) ? sourceAttempts.get(sourceKey) : 0;
        attempt++;
        sourceAttempts.put(sourceKey, attempt);
        if (attempt >= MAX_SOURCE_ATTEMPTS) {
            L.w("search source exhausted key=" + sourceKey + " attempts=" + attempt);
            return false;
        }

        final String query = searchWord;
        final Runnable[] retryHolder = new Runnable[1];
        retryHolder[0] = () -> {
            sourceRetryTasks.remove(retryHolder[0]);
            if (searchFinished || requestId != searchRequestId
                    || destroying || isFinishing() || isDestroyed()
                    || succeededSourceKeys.contains(sourceKey)) {
                pendingRetryKeys.remove(sourceKey);
                checkRetryPhaseComplete();
                return;
            }
            SourceViewModel.SearchTaskHandle handle = sourceViewModel.getSearchAsync(sourceKey, query, requestId);
            if (handle != null) {
                searchTasks.add(handle);
            }
        };
        sourceRetryTasks.add(retryHolder[0]);
        pendingRetryKeys.add(sourceKey);
        searchHandler.postDelayed(retryHolder[0], SOURCE_RETRY_DELAY_MS);
        L.w("retry search source key=" + sourceKey + " attempt=" + (attempt + 1));
        return true;
    }

    private void beginRetryPhase() {
        if (retryPhase) return;
        retryPhase = true;
        searchHandler.removeCallbacks(searchTimeout);
        boolean scheduledAny = false;
        for (String sourceKey : new ArrayList<>(failedSourceKeys)) {
            if (retrySearchSource(sourceKey, searchRequestId)) {
                scheduledAny = true;
            }
        }
        if (scheduledAny) {
            retryInProgress = true;
            searchHandler.removeCallbacks(retryTimeout);
            searchHandler.postDelayed(retryTimeout, RETRY_TIMEOUT_MS);
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

        List<SiteBean> directHttp = new ArrayList<>();
        List<SiteBean> fastSpiders = new ArrayList<>();
        List<SiteBean> slowSpiders = new ArrayList<>();

        for (SiteBean siteBean : siteBeans) {
            if (siteBean.getType() == 0 || siteBean.getType() == 1) {
                directHttp.add(siteBean);
            } else if (SESSION_SLOW_SOURCES.contains(siteBean.getKey())) {
                slowSpiders.add(siteBean);
            } else {
                fastSpiders.add(siteBean);
            }
        }

        for (SiteBean siteBean : directHttp) {
            if (searchFinished || requestId != searchRequestId) return;
            sourceViewModel.getSearchAsync(siteBean.getKey(), query, requestId);
        }

        List<SiteBean> orderedSpiders = new ArrayList<>(fastSpiders.size() + slowSpiders.size());
        orderedSpiders.addAll(fastSpiders);
        orderedSpiders.addAll(slowSpiders);
        for (final SiteBean siteBean : orderedSpiders) {
            if (searchFinished || requestId != searchRequestId) return;
            SourceViewModel.SearchTaskHandle handle = sourceViewModel.getSearchAsync(siteBean.getKey(), query, requestId);
            if (handle != null) {
                searchTasks.add(handle);
            }
        }
    }

    private void stopSearchRequests() {
        finalizeSearchSession();
        keyboardSearchFocus = null;
        retryPhase = false;
        resultGeneration++;
        pendingResults.clear();
        inFlightFilterTasks = 0;
        resultMergeRunning = false;
        mergeScheduled = false;
        failedSourceKeys.clear();
        searchHandler.removeCallbacks(mergeResults);
    }

    private void cancelSuggestions() {
        suggestionRequestId++;
        if (suggestionRunnable != null) {
            searchHandler.removeCallbacks(suggestionRunnable);
            suggestionRunnable = null;
        }
        OkGo.getInstance().cancelTag(suggestionTag);
    }

    private void updateSearchProgress() {
        if (folderMode || searchSourceCount == 0 || tvSearchProgress == null
                || searchResultAdapter == null || !isForegroundReadyForMerge()) return;
        if (TextUtils.isEmpty(searchWord)) return;
        int resultCount = searchResultAdapter.getData().size();
        boolean hasResults = resultCount > 0;
        // 仍有过滤任务/待合并队列/重试进行中时绝不提前判定完成弹出空态页
        boolean pipelineBusy = inFlightFilterTasks > 0 || !pendingResults.isEmpty()
                || resultMergeRunning || mergeScheduled;
        boolean fullyFinished = searchFinished && !pipelineBusy;
        int doneCount = Math.min(completedSearchSources, searchSourceCount);

        if (hasResults) {
            if (llSearchLoading != null) llSearchLoading.setVisibility(View.GONE);
            if (llSearchEmpty != null) llSearchEmpty.setVisibility(View.GONE);
            if (mSearchResultRV != null) mSearchResultRV.setVisibility(View.VISIBLE);
            if (llSearchStatusBar != null) llSearchStatusBar.setVisibility(View.VISIBLE);
            tvSearchProgress.setVisibility(View.VISIBLE);
            if (fullyFinished) {
                if (pbSearchLoading != null) pbSearchLoading.setVisibility(View.GONE);
                String summary = resultsTruncated
                        ? ("已展示前 " + resultCount + " 条结果（已启用 " + searchSourceCount + " 个源）")
                        : ("共找到 " + resultCount + " 条结果 · 已启用 " + searchSourceCount + " 个源");
                tvSearchProgress.setText(summary);
            } else {
                if (pbSearchLoading != null) pbSearchLoading.setVisibility(View.VISIBLE);
                tvSearchProgress.setText("已找到 " + resultCount + " 条结果 · " + doneCount + "/" + searchSourceCount + " 源补充中");
            }
            return;
        }

        if (fullyFinished) {
            View keyboardFocus = keyboardSearchFocus;
            keyboardSearchFocus = null;
            boolean focusEmpty = canFocusSearchOutcome(keyboardFocus);
            if (llSearchLoading != null) llSearchLoading.setVisibility(View.GONE);
            if (llSearchStatusBar != null) llSearchStatusBar.setVisibility(View.GONE);
            if (pbSearchLoading != null) pbSearchLoading.setVisibility(View.GONE);
            tvSearchProgress.setVisibility(View.GONE);
            if (llSearchEmpty != null) {
                boolean wasEmptyHidden = llSearchEmpty.getVisibility() != View.VISIBLE;
                llSearchEmpty.setVisibility(View.VISIBLE);
                if (tvSearchEmptyTip != null) {
                    tvSearchEmptyTip.setText(searchTimedOut
                            ? "部分搜索源响应超时，暂未找到相关资源"
                            : "未找到相关影视资源");
                }
                if (btnEmptyRetry != null) btnEmptyRetry.setVisibility(View.VISIBLE);
                if (btnEmptyManageSources != null) btnEmptyManageSources.setVisibility(View.VISIBLE);
                View target = btnEmptyRetry != null ? btnEmptyRetry : btnEmptyManageSources;
                if (wasEmptyHidden && target != null && focusEmpty) {
                    final int generation = resultGeneration;
                    target.post(() -> {
                        if (generation == resultGeneration && llSearchEmpty.isShown()
                                && canFocusSearchOutcome(keyboardFocus)) {
                            target.requestFocus();
                        }
                    });
                }
            }
            if (mSearchResultRV != null) mSearchResultRV.setVisibility(View.GONE);
        } else {
            if (llSearchEmpty != null) llSearchEmpty.setVisibility(View.GONE);
            if (llSearchStatusBar != null) llSearchStatusBar.setVisibility(View.VISIBLE);
            if (pbSearchLoading != null) pbSearchLoading.setVisibility(View.VISIBLE);
            tvSearchProgress.setVisibility(View.VISIBLE);
            String msg = retryInProgress && !pendingRetryKeys.isEmpty()
                    ? "首轮无结果，正在重试 " + pendingRetryKeys.size() + " 个源…"
                    : "正在搜索「" + searchWord + "」 (" + doneCount + "/" + searchSourceCount + " 源)…";
            tvSearchProgress.setText(msg);
            if (llSearchLoading != null) {
                llSearchLoading.setVisibility(View.VISIBLE);
                if (tvSearchLoadingTip != null) tvSearchLoadingTip.setText(msg);
            }
            if (mSearchResultRV != null) mSearchResultRV.setVisibility(View.VISIBLE);
        }
    }

    private static String normalizeForMatch(String text) {
        if (text == null) return "";
        return MATCH_SEPARATOR.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    static boolean matchesQuery(String text, String query) {
        if (text == null || text.isEmpty() || query == null) return false;
        String trimmedQuery = query.trim();
        if (trimmedQuery.isEmpty()) return false;
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerQuery = trimmedQuery.toLowerCase(Locale.ROOT);
        if (lowerText.contains(lowerQuery)) {
            return true;
        }
        String normText = normalizeForMatch(text);
        String normQuery = normalizeForMatch(trimmedQuery);
        if (!normQuery.isEmpty() && normText.contains(normQuery)) {
            return true;
        }
        String[] tokens = lowerQuery.split("\\s+");
        if (tokens.length > 1) {
            for (String token : tokens) {
                if (!token.isEmpty() && !lowerText.contains(token)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    static boolean matchesVideo(Movie.Video video, String query) {
        if (video == null || query == null) return false;
        if (matchesVideoFields(video, query)) return true;
        String[] terms = query.trim().split("[\\s\\p{Z}]+");
        if (terms.length < 2) return false;
        for (String term : terms) {
            if (!term.isEmpty() && !matchesVideoFields(video, term)) return false;
        }
        return true;
    }

    private static boolean matchesVideoFields(Movie.Video video, String query) {
        return matchesQuery(video.name, query) || matchesQuery(video.actor, query)
                || matchesQuery(video.director, query);
    }

    /** 匹配度评分（同源内精准正片优先）：3=完全同名，2=前缀命中，1=片名包含，0=演员/导演命中 */
    static int matchScore(Movie.Video video, String query) {
        if (video == null || query == null) return 0;
        String normQuery = normalizeForMatch(query);
        if (normQuery.isEmpty()) return 0;
        String normName = normalizeForMatch(video.name);
        if (normName.equals(normQuery)) return 3;
        if (normName.startsWith(normQuery)) return 2;
        if (matchesQuery(video.name, query)) return 1;
        return 0;
    }

    static List<Movie.Video> mergeAndOrderResults(List<Movie.Video> existing,
                                                  List<Movie.Video> incoming,
                                                  Map<String, Integer> order,
                                                  int maxTotal) {
        return mergeAndOrderResults(existing, incoming, order, maxTotal, false, "");
    }

    static List<Movie.Video> mergeAndOrderResults(List<Movie.Video> existing,
                                                  List<Movie.Video> incoming,
                                                  Map<String, Integer> order,
                                                  int maxTotal,
                                                  boolean preserveExistingOrder) {
        return mergeAndOrderResults(existing, incoming, order, maxTotal, preserveExistingOrder, "");
    }

    static List<Movie.Video> mergeAndOrderResults(List<Movie.Video> existing,
                                                  List<Movie.Video> incoming,
                                                  Map<String, Integer> order,
                                                  int maxTotal,
                                                  boolean preserveExistingOrder,
                                                  String query) {
        Set<String> seen = new HashSet<>();
        List<Movie.Video> base = new ArrayList<>(existing == null ? 0 : existing.size());
        if (existing != null) {
            for (Movie.Video v : existing) {
                if (v == null) continue;
                if (seen.add(dedupeKeyOf(v))) {
                    base.add(v);
                }
            }
        }
        List<Movie.Video> fresh = new ArrayList<>(incoming == null ? 0 : incoming.size());
        if (incoming != null) {
            for (Movie.Video v : incoming) {
                if (v == null) continue;
                if (seen.add(dedupeKeyOf(v))) {
                    fresh.add(v);
                }
            }
        }
        if (preserveExistingOrder && !base.isEmpty()) {
            sortBySourceAndMatch(fresh, order, query);
            List<Movie.Video> combined = new ArrayList<>(base.size() + fresh.size());
            combined.addAll(base);
            combined.addAll(fresh);
            if (maxTotal > 0 && combined.size() > maxTotal) {
                combined.subList(maxTotal, combined.size()).clear();
            }
            return combined;
        }
        List<Movie.Video> combined = new ArrayList<>(base.size() + fresh.size());
        combined.addAll(base);
        combined.addAll(fresh);
        sortBySourceAndMatch(combined, order, query);
        if (maxTotal > 0 && combined.size() > maxTotal) {
            combined.subList(maxTotal, combined.size()).clear();
        }
        return combined;
    }

    private static void sortBySourceAndMatch(List<Movie.Video> list, Map<String, Integer> order, String query) {
        final boolean rankByMatch = query != null && !query.trim().isEmpty();
        Collections.sort(list, (a, b) -> {
            int orderA = (order != null && order.containsKey(a.sourceKey)) ? order.get(a.sourceKey) : Integer.MAX_VALUE;
            int orderB = (order != null && order.containsKey(b.sourceKey)) ? order.get(b.sourceKey) : Integer.MAX_VALUE;
            int cmp = Integer.compare(orderA, orderB);
            if (cmp != 0 || !rankByMatch) {
                return cmp;
            }
            return Integer.compare(matchScore(b, query), matchScore(a, query));
        });
    }

    private static String dedupeKeyOf(Movie.Video video) {
        String source = video.sourceKey == null ? "" : video.sourceKey;
        String id = video.id == null ? "" : video.id;
        if (!id.isEmpty()) {
            return source + "|" + id;
        }
        String name = video.name == null ? "" : video.name;
        return source + "||" + name;
    }

    private void showSearchData(AbsXml absXml) {
        if (absXml == null || absXml.movie == null || NullUtil.isNull(absXml.movie.videoList)) return;
        if (resultExecutor.isShutdown()) return;
        final int generation = resultGeneration;
        final List<Movie.Video> sourceVideos = new ArrayList<>(absXml.movie.videoList);
        final String query = searchWord;
        if (TextUtils.isEmpty(query)) return;
        inFlightFilterTasks++;
        try {
            resultExecutor.execute(() -> {
                List<Movie.Video> matched = new ArrayList<>();
                for (Movie.Video video : sourceVideos) {
                    if (video != null && !TextUtils.isEmpty(video.id)
                            && matchesVideo(video, query)
                            && UiHelper.canPlay(video) && UiHelper.canPlayName(video)) {
                        matched.add(video);
                    }
                }
                // 单源截断前先按匹配度排序，保证精准正片不会被排在第 30 条后截丢
                if (matched.size() > 1) {
                    Collections.sort(matched, (a, b) -> Integer.compare(matchScore(b, query), matchScore(a, query)));
                }
                if (matched.size() > MAX_SOURCE_RESULTS) {
                    matched.subList(MAX_SOURCE_RESULTS, matched.size()).clear();
                }
                final List<Movie.Video> videos = matched;
                searchHandler.post(() -> {
                    if (generation != resultGeneration || destroying || isFinishing() || isDestroyed()) return;
                    inFlightFilterTasks = Math.max(0, inFlightFilterTasks - 1);
                    if (!videos.isEmpty()) {
                        pendingResults.addAll(videos);
                        scheduleMergeResults();
                    } else {
                        updateSearchProgress();
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            inFlightFilterTasks = Math.max(0, inFlightFilterTasks - 1);
        }
    }

    private boolean isForegroundReadyForMerge() {
        return activityResumed && hasWindowFocus() && !destroying && !isFinishing() && !isDestroyed()
                && (activeFolderDialog == null || !activeFolderDialog.isShowing())
                && (activeSearchSiteDialog == null || !activeSearchSiteDialog.isShowing())
                && (activeCloudDriveDialog == null || !activeCloudDriveDialog.isShowing())
                && (activeTipDialog == null || !activeTipDialog.isShowing());
    }

    private boolean canFocusSearchOutcome(View keyboardFocus) {
        if (!isForegroundReadyForMerge()) return false;
        View focused = getCurrentFocus();
        return focused == null || focused == tvSearch || focused == keyboardFocus
                || isChildOf(focused, llSearchStatusBar) || isChildOf(focused, llSearchEmpty);
    }

    private boolean isBrowsingResults() {
        return userScrolledResults || (mSearchResultRV != null && mSearchResultRV.hasFocus());
    }

    private void scheduleMergeResults() {
        if (mergeScheduled || resultMergeRunning || pendingResults.isEmpty()) return;
        if (!isForegroundReadyForMerge()) return;
        mergeScheduled = true;
        searchHandler.removeCallbacks(mergeResults);
        long delay = (searchResultAdapter == null || searchResultAdapter.getData().isEmpty())
                ? 0L : RESULT_MERGE_DELAY_MS;
        searchHandler.postDelayed(mergeResults, delay);
    }

    private void mergePendingResults() {
        if (resultMergeRunning || pendingResults.isEmpty()
                || destroying || isFinishing() || isDestroyed()
                || !isForegroundReadyForMerge()
                || resultExecutor.isShutdown()) {
            return;
        }
        resultMergeRunning = true;
        final int generation = resultGeneration;
        final String query = searchWord;
        final List<Movie.Video> old = new ArrayList<>(searchResultAdapter.getData());
        final List<Movie.Video> incoming = new ArrayList<>(pendingResults);
        pendingResults.clear();
        // 遥控器聚焦结果墙或触屏主动滑动过结果墙时，均锁定已展示顺序防止头部插队跳变
        final boolean preserveExistingOrder = !old.isEmpty() && isBrowsingResults();
        final Map<String, Integer> order = new HashMap<>(sourceOrder);
        try {
            resultExecutor.execute(() -> {
                final List<Movie.Video> fullMerged = mergeAndOrderResults(
                        old, incoming, order, 0, preserveExistingOrder, query);
                final boolean truncated = fullMerged.size() > MAX_TOTAL_RESULTS;
                final List<Movie.Video> merged = truncated
                        ? new ArrayList<>(fullMerged.subList(0, MAX_TOTAL_RESULTS))
                        : fullMerged;
                final DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return old.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return merged.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldPosition, int newPosition) {
                        return old.get(oldPosition) == merged.get(newPosition);
                    }

                    @Override
                    public boolean areContentsTheSame(int oldPosition, int newPosition) {
                        return true;
                    }
                }, false);
                searchHandler.post(() -> applyMergedResults(
                        generation, old, incoming, merged, changes, truncated, preserveExistingOrder));
            });
        } catch (RejectedExecutionException ignored) {
            resultMergeRunning = false;
        }
    }

    private void applyMergedResults(final int generation,
                                    final List<Movie.Video> old,
                                    final List<Movie.Video> incoming,
                                    final List<Movie.Video> merged,
                                    final DiffUtil.DiffResult changes,
                                    final boolean truncated,
                                    final boolean preserveExistingOrder) {
        if (generation != resultGeneration || destroying || isFinishing() || isDestroyed()) return;
        if (!isForegroundReadyForMerge() || (!old.isEmpty() && !preserveExistingOrder && isBrowsingResults())) {
            pendingResults.addAll(0, incoming);
            resultMergeRunning = false;
            scheduleMergeResults();
            return;
        }
        if (mSearchResultRV != null && mSearchResultRV.isComputingLayout()) {
            mSearchResultRV.post(() -> applyMergedResults(
                    generation, old, incoming, merged, changes, truncated, preserveExistingOrder));
            return;
        }
        if (truncated) {
            resultsTruncated = true;
        }
        List<Movie.Video> currentData = searchResultAdapter.getData();
        boolean snapshotMatches = currentData.size() == old.size();
        if (snapshotMatches) {
            for (int i = 0; i < old.size(); i++) {
                if (currentData.get(i) != old.get(i)) {
                    snapshotMatches = false;
                    break;
                }
            }
        }
        if (snapshotMatches) {
            currentData.clear();
            currentData.addAll(merged);
            changes.dispatchUpdatesTo(searchResultAdapter);
        } else {
            searchResultAdapter.setNewData(new ArrayList<>(merged));
        }
        resultMergeRunning = false;
        if (!merged.isEmpty()) {
            if (llSearchGuide != null) llSearchGuide.setVisibility(View.GONE);
            if (llSearchLoading != null) llSearchLoading.setVisibility(View.GONE);
            if (llSearchEmpty != null) llSearchEmpty.setVisibility(View.GONE);
            if (mSearchResultRV != null) mSearchResultRV.setVisibility(View.VISIBLE);
            if (mSearchResultRV != null && !isBrowsingResults()) {
                mSearchResultRV.scrollToPosition(0);
            }
            if (old.isEmpty()) {
                final View keyboardFocus = keyboardSearchFocus;
                keyboardSearchFocus = null;
                mSearchResultRV.post(() -> {
                    if (generation != resultGeneration || destroying || isFinishing() || isDestroyed()) return;
                    if (!isBrowsingResults() && canFocusSearchOutcome(keyboardFocus)) {
                        mSearchResultRV.setSelection(0);
                        mSearchResultRV.requestFocus();
                    }
                });
            }
        }
        updateSearchProgress();
        if (!pendingResults.isEmpty()) scheduleMergeResults();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            View currentFocus = getCurrentFocus();
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (!folderMode && currentFocus != null && isChildOf(currentFocus, keyboard)) {
                    View next = currentFocus.focusSearch(View.FOCUS_RIGHT);
                    if (next == null || !isChildOf(next, keyboard)) {
                        if (focusMiddleOrRight()) return true;
                    }
                } else if (!folderMode && currentFocus != null && (currentFocus == tvSearch
                        || currentFocus == tvClear
                        || isChildOf(currentFocus, mSearchHistoryRV)
                        || isChildOf(currentFocus, remoteRoot))) {
                    View next = currentFocus.focusSearch(View.FOCUS_RIGHT);
                    if (next == null || !isChildOf(next, llSearchLeftPanel)) {
                        if (focusMiddleOrRight()) return true;
                    }
                } else if (!folderMode && currentFocus != null && isChildOf(currentFocus, mHotWordRV)) {
                    View next = currentFocus.focusSearch(View.FOCUS_RIGHT);
                    if (next == null || !isChildOf(next, mHotWordRV)) {
                        if (focusRightArea()) return true;
                    }
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (!folderMode && currentFocus != null && currentFocus == btnManageSearchSourcesTop) {
                    if (tvSearch != null && tvSearch.isShown() && tvSearch.requestFocus()) return true;
                } else if (!folderMode && currentFocus != null && (isChildOf(currentFocus, mSearchResultRV)
                        || isChildOf(currentFocus, llSearchEmpty))) {
                    View next = currentFocus.focusSearch(View.FOCUS_LEFT);
                    boolean stayingInRight = next != null && (isChildOf(next, mSearchResultRV)
                            || isChildOf(next, llSearchEmpty));
                    if (!stayingInRight) {
                        resetResultItemScale();
                        if (llWord != null && llWord.isShown() && mHotWordRV != null && mHotWordRV.getChildCount() > 0) {
                            mHotWordRV.requestFocus();
                            return true;
                        } else if (focusLeftPanel()) {
                            return true;
                        }
                    }
                } else if (!folderMode && currentFocus != null && isChildOf(currentFocus, mHotWordRV)) {
                    View next = currentFocus.focusSearch(View.FOCUS_LEFT);
                    if (next == null || !isChildOf(next, mHotWordRV)) {
                        if (focusLeftPanel()) {
                            return true;
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean focusMiddleOrRight() {
        if (llWord != null && llWord.isShown() && mHotWordRV != null && mHotWordRV.getChildCount() > 0) {
            mHotWordRV.requestFocus();
            return true;
        }
        return focusRightArea();
    }

    private boolean focusLeftPanel() {
        if (folderMode) return false;
        if (keyboard != null && keyboard.isShown()) {
            keyboard.requestLetterFocus();
            return true;
        }
        if (remoteRoot != null && remoteRoot.isShown() && btnBackToKeyboard != null) {
            return btnBackToKeyboard.requestFocus();
        }
        return false;
    }

    private boolean focusRightArea() {
        if (mSearchResultRV != null && mSearchResultRV.isShown() && mSearchResultRV.getChildCount() > 0) {
            mSearchResultRV.requestFocus();
            return true;
        }
        if (llSearchEmpty != null && llSearchEmpty.isShown()) {
            if (btnEmptyRetry != null && btnEmptyRetry.isShown()) {
                btnEmptyRetry.requestFocus();
                return true;
            }
            if (btnEmptyManageSources != null && btnEmptyManageSources.isShown()) {
                btnEmptyManageSources.requestFocus();
                return true;
            }
        }
        if (llSearchStatusBar != null && llSearchStatusBar.isShown()
                && btnManageSearchSourcesTop != null && btnManageSearchSourcesTop.isShown()) {
            btnManageSearchSourcesTop.requestFocus();
            return true;
        }
        return false;
    }

    @Override
    public void finish() {
        if (sourceViewModel != null && sourceViewModel.isInFolderSpiderCall()) {
            android.util.Log.w(TRACE_TAG, "ignored finish from folder spider activity="
                    + System.identityHashCode(this));
            return;
        }
        super.finish();
    }

    @Override
    public void onBackPressed() {
        if (folderMode) {
            finish();
            return;
        }
        View currentFocus = getCurrentFocus();
        // 电视端层级返回：焦点在右侧结果/热词/空态/状态栏时先回左侧面板（不误关正在显示的扫码面板）
        if (currentFocus != null && (isChildOf(currentFocus, mSearchResultRV)
                || isChildOf(currentFocus, mHotWordRV)
                || isChildOf(currentFocus, llSearchEmpty)
                || isChildOf(currentFocus, llSearchStatusBar))) {
            resetResultItemScale();
            if (focusLeftPanel()) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastBackHintAt > BACK_HINT_INTERVAL_MS) {
                    lastBackHintAt = now;
                    String hint = remoteRoot != null && remoteRoot.isShown()
                            ? "焦点已回到扫码面板，再按一次返回切回键盘"
                            : "焦点已回到左侧操作区，再按一次返回退出";
                    Toast.makeText(mContext, hint, Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
        if (remoteRoot != null && remoteRoot.isShown()) {
            showVirtualKeyboardPanel();
            return;
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

    private void dismissActiveDialogs() {
        if (activeSearchSiteDialog != null) {
            try {
                if (activeSearchSiteDialog.isShowing()) {
                    activeSearchSiteDialog.dismiss();
                }
            } catch (Throwable ignored) {
            }
            activeSearchSiteDialog = null;
        }
        if (activeCloudDriveDialog != null) {
            try {
                if (activeCloudDriveDialog.isShowing()) {
                    activeCloudDriveDialog.dismiss();
                }
            } catch (Throwable ignored) {
            }
            activeCloudDriveDialog = null;
        }
        if (activeFolderDialog != null) {
            try {
                if (activeFolderDialog.isShowing()) {
                    activeFolderDialog.dismiss();
                }
            } catch (Throwable ignored) {
            }
            activeFolderDialog = null;
        }
        if (activeTipDialog != null) {
            try {
                if (activeTipDialog.isShowing()) {
                    activeTipDialog.dismiss();
                }
            } catch (Throwable ignored) {
            }
            activeTipDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        destroying = true;
        dismissActiveDialogs();
        stopSearchRequests();
        cancelSuggestions();
        if (hotRankTask != null) hotRankTask.cancel(true);
        searchHandler.removeCallbacksAndMessages(null);
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        resultExecutor.shutdownNow();
        // 使用有序 shutdown，允许最后一次已排队的历史记录写盘顺利完成，避免丢历史
        historyIoExecutor.shutdown();
        super.onDestroy();
    }
}
