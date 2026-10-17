package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

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
    private TextView tvClear;
    private SearchKeyboard keyboard;
    private SearchResultAdapter searchResultAdapter;
    private PinyinAdapter wordAdapter;
    private SearchHistoryAdapter historyAdapter;
    private String searchWord = "";
    private SPDataUtil spDataUtil = null;
    public ExecutorService mExecutorService;
    private int searchRequestId;
    private int searchSourceCount;
    private int completedSearchSources;
    private boolean searchFinished;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchTimeout = new Runnable() {
        @Override
        public void run() {
            if (!searchFinished && searchResultAdapter != null) {
                searchFinished = true;
                OkGo.getInstance().cancelTag("search");
                if (searchResultAdapter.getData().isEmpty()) {
                    showEmpty();
                    Toast.makeText(SearchActivity.this, "搜索超时，请检查网络或更换搜索源", Toast.LENGTH_SHORT).show();
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
        mExecutorService = Executors.newFixedThreadPool(5);
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
        llLayout = findViewById(R.id.llLayout);
        llWord = findViewById(R.id.llWord);
        etSearch = findViewById(R.id.etSearch);
        tvSearch = findViewById(R.id.tvSearch);
        tvClear = findViewById(R.id.tvClear);
        mSearchResultRV = findViewById(R.id.sortRV);
        keyboard = findViewById(R.id.keyBoardRoot);
        mHotWordRV = findViewById(R.id.mGridViewWord);
        mSearchHistoryRV = findViewById(R.id.serch_history_rv);
        llClearHistory = findViewById(R.id.llClearHistory);
        initHistoryView();
        initHotRecycleView();
        initSearchResultRecycleView();

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
                    if (text.length() > 0) {
                        loadPinyinResult(text);
                        etSearch.setSelection(text.length());
                    }
                } else if (pos == 1) {//删除按钮
                    String text = etSearch.getText().toString().trim();
                    if (text.length() > 0) {
                        text = text.substring(0, text.length() - 1);
                        etSearch.setText(text);
                    }
                    if (text.length() > 0) {
                        loadPinyinResult(text);
                        etSearch.setSelection(text.length());
                    }
                } else if (pos == 0) {//远程搜索
                    etSearch.setText("");
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
            mSearchResultRV.setLayoutManager(new V7GridLayoutManager(this.mContext, 3));
        }
        searchResultAdapter = new SearchResultAdapter();
        mSearchResultRV.setAdapter(searchResultAdapter);
        searchResultAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchResultAdapter.getData().get(position);
                if (video != null) {
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
//        OkGo.<String>get("https://s.video.qq.com/smartbox")
//        OkGo.<String>get("https://node.video.qq.com/smartbox")
//                .params("plat", 2)
//                .params("ver", 0)
//                .params("num", 10)
//                .params("otype", "json")
//                .params("query", key)
//                .execute(new AbsCallback<String>() {
//                    @Override
//                    public void onSuccess(Response<String> response) {
//                        try {
//                            ArrayList<String> hots = new ArrayList<>();
//                            String result = response.body();
//                            L.d(result);
//                            JsonObject json = JsonParser.parseString(result.substring(result.indexOf("{"), result.lastIndexOf("}") + 1)).getAsJsonObject();
//                            JsonArray itemList = json.get("item").getAsJsonArray();
//                            for (JsonElement ele : itemList) {
//                                JsonObject obj = (JsonObject) ele;
//                                hots.add(obj.get("word").getAsString().trim());
//                            }
//                            wordAdapter.setNewData(hots);
//                        } catch (Throwable th) {
//                            th.printStackTrace();
//                        }
//                    }
//
//                    @Override
//                    public String convertResponse(okhttp3.Response response) throws Throwable {
//                        return response.body().string();
//                    }
//                });
        OkGo.<String>get("https://suggest.video.iqiyi.com/")
                .params("if", "mobile")
                .params("key", key)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
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
        // 加载热词
        OkGo.<String>get("https://node.video.qq.com/x/api/hot_mobilesearch")
                .params("channdlId", "0")
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
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
                            requestHotWords_my();
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
    private void requestHotWords_my() {
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
            showLoading();
            search(title);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            if (event.requestId != searchRequestId || searchFinished) {
                return;
            }
            try {
                showSearchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                L.e("showSearchData " + e.getMessage());
                showSearchData(null);
            }
            completedSearchSources++;
            if (completedSearchSources >= searchSourceCount) {
                searchFinished = true;
                searchHandler.removeCallbacks(searchTimeout);
                if (searchResultAdapter.getData().isEmpty()) {
                    showEmpty();
                }
            }
        }
    }

    private void searchClick() {
        System_UI.hideInputMethod(getBaseContext(), etSearch);
        String wd = etSearch.getText().toString().trim();
        if (!TextUtils.isEmpty(wd)) {
            search(wd);
        } else {
            Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void search(String wd) {
        OkGo.getInstance().cancelTag("search");
        searchHandler.removeCallbacks(searchTimeout);
        searchRequestId++;
        searchFinished = false;
        completedSearchSources = 0;
        searchSourceCount = 0;
        showLoading();
        this.searchWord = wd;
        mSearchResultRV.setVisibility(View.INVISIBLE);
        searchResultAdapter.setNewData(new ArrayList<>());
        doSearch();
        if (searchSourceCount > 0) {
            searchHandler.postDelayed(searchTimeout, 15000L);
        }
        if (etSearch != null) {
            etSearch.setText(wd);
            etSearch.setSelection(etSearch.getText().length());
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
            // 一个源都没勾选时不能一直停在加载中（搜索页没有超时兜底）
            showEmpty();
            Toast.makeText(mContext, "未选择任何搜索源，请到 设置 → 设置其他 → 搜索源管理 中勾选", Toast.LENGTH_LONG).show();
            return;
        }
        searchSourceCount = siteBeans.size();
        for (final SiteBean siteBean : siteBeans) {
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    sourceViewModel.getSearch(siteBean.getKey(), searchWord, searchRequestId);
                }
            });
        }
    }

    private void showSearchData(AbsXml absXml) {
        if (absXml == null || absXml.movie == null || NullUtil.isNull(absXml.movie.videoList)) return;
        List<Movie.Video> videos = UiHelper.filterVideoList(absXml, searchWord);
        List<Movie.Video> old = searchResultAdapter.getData();
        List<Movie.Video> merged = new ArrayList<>(old);
        merged.addAll(videos);
        // 展示顺序与「搜索源管理」的勾选顺序一致；Collections.sort 是稳定排序，
        // 同一个源内部仍保持返回顺序
        Collections.sort(merged, (a, b) -> ApiConfig.getInstance().getSearchSiteOrderIndex(a.sourceKey)
                - ApiConfig.getInstance().getSearchSiteOrderIndex(b.sourceKey));
        if (old.size() > 0 && isSamePrefix(old, merged)) {
            // 新结果本来就落在末尾，增量追加即可，避免整表刷新打断焦点
            searchResultAdapter.addData(videos);
        } else {
            searchResultAdapter.setNewData(merged);
        }
        if (searchResultAdapter.getData().size() >= 1) {
            showSuccess();
            mSearchResultRV.setVisibility(View.VISIBLE);
        }
    }

    /** merged 的前缀是否与原列表完全一致（同一批对象、同一顺序） */
    private boolean isSamePrefix(List<Movie.Video> old, List<Movie.Video> merged) {
        for (int i = 0; i < old.size(); i++) {
            if (old.get(i) != merged.get(i)) return false;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchFinished = true;
        searchHandler.removeCallbacks(searchTimeout);
        searchHandler.removeCallbacks(searchTimeout);
        OkGo.getInstance().cancelTag("search");
        EventBus.getDefault().unregister(this);
        // 释放聚合搜索线程池，避免退出搜索页后仍存活 5 条线程
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
        }
    }
}