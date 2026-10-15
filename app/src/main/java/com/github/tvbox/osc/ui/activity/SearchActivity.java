package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

public class SearchActivity extends BaseActivity {
    private LinearLayout llLayout;
    private LinearLayout llWord;
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
        historyAdapter.setNewData(getHistoryWords());
        mSearchHistoryRV.setAdapter(historyAdapter);
        historyAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                search(historyAdapter.getItem(position));
            }
        });
    }

    private List<String> getHistoryWords() {
        List<String> historyWords = (List<String>) spDataUtil.getObjectValue("histroys");
        if (NullUtil.isNull(historyWords)) {
            historyWords = new ArrayList<>();
            historyWords.add("斗罗大陆");
            historyWords.add("火影");
            historyWords.add("三国");
            historyWords.add("莲花楼");
            historyWords.add("流浪地球");
            historyWords.add("繁花");
            historyWords.add("暗战");
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
        List<String> historyWords = new ArrayList<>();
        spDataUtil.saveObjectValue("histroys", historyWords);
        historyAdapter.setNewData(null);
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
            try {
                showSearchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                showSearchData(null);
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
        showLoading();
        this.searchWord = wd;
        mSearchResultRV.setVisibility(View.INVISIBLE);
        searchResultAdapter.setNewData(new ArrayList<>());
        doSearch();
        if (etSearch != null) {
            etSearch.setText(wd);
            etSearch.setSelection(etSearch.getText().length());
        }
        saveHistoryWords(wd);
        historyAdapter.setNewData(getHistoryWords());
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

        List<SiteBean> siteBeans = new ArrayList<>();
        siteBeans.addAll(ApiConfig.getInstance().getSiteBeanList());
        for (SiteBean siteBean : siteBeans) {
            if (!siteBean.isSearchable()) {
                continue;
            }
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    sourceViewModel.getSearch(siteBean.getKey(), searchWord);
                }
            });
        }
    }

    private void showSearchData(AbsXml absXml) {
        if (absXml != null && absXml.movie != null && !NullUtil.isNull(absXml.movie.videoList)) {
            List<Movie.Video> videos = UiHelper.filterVideoList(absXml, searchWord);
            if (searchResultAdapter.getData().size() > 0) {
                searchResultAdapter.addData(videos);
            } else {
                searchResultAdapter.setNewData(videos);
            }
            if (searchResultAdapter.getData().size() >= 1) {
                showSuccess();
                mSearchResultRV.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        OkGo.getInstance().cancelTag("search");
        EventBus.getDefault().unregister(this);
    }
}