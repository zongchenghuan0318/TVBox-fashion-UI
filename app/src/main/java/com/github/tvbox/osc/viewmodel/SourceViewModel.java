package com.github.tvbox.osc.viewmodel;

import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.AbsJson;
import com.github.tvbox.osc.bean.AbsSortJson;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.activity.UiHelper;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.thunder.Thunder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SourceViewModel extends ViewModel {
    public MutableLiveData<AbsSortXml> sortResult;
    public MutableLiveData<AbsXml> listResult;
    public MutableLiveData<AbsXml> searchResult;
    public MutableLiveData<AbsXml> quickSearchResult;
    public MutableLiveData<AbsXml> detailResult;
    public MutableLiveData<JSONObject> playResult;
    public ExecutorService mExecutorService;
    private final ExecutorService mSearchParseExecutor = Executors.newFixedThreadPool(2);
    private volatile FutureTask<String> activePlayContentTask;
    private volatile int playRequestId;
    private volatile int detailRequestId;
    private volatile int lastPostedDetailRequestId = -1;
    private volatile int categoryRequestId;
    private volatile int lastPostedCategoryRequestId = -1;
    private final Object categoryRequestTag = new Object();
    private final Object detailRequestTag = new Object();
    private final Handler resultHandler = new Handler(Looper.getMainLooper());
    private final Object sortRequestTag = new Object();
    private volatile int sortRequestId;
    private String lastPostedSortSourceKey;

    public interface HomeRecCallback {
        void done(List<Movie.Video> videos);
    }

    public SourceViewModel() {
        mExecutorService = Executors.newFixedThreadPool(3);
        sortResult = new MutableLiveData<>();
        listResult = new MutableLiveData<>();
        searchResult = new MutableLiveData<>();
        quickSearchResult = new MutableLiveData<>();
        detailResult = new MutableLiveData<>();
        playResult = new MutableLiveData<>();
    }

    @Override
    protected void onCleared() {
        cancelHomeRequests();
        cancelCategoryRequests();
        cancelDetailRequests();
        cancelSortRequests();
        resultHandler.removeCallbacksAndMessages(null);
        FutureTask<String> playTask = activePlayContentTask;
        if (playTask != null) {
            playTask.cancel(true);
        }
        playRequestId++;
        mSearchParseExecutor.shutdownNow();
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
            mExecutorService = null;
        }
        super.onCleared();
    }

    public void cancelHomeRequests() {
        OkGo.getInstance().cancelTag("home_rec");
    }

    public void cancelCategoryRequests() {
        categoryRequestId++;
        OkGo.getInstance().cancelTag(categoryRequestTag);
    }

    public void getSort(String sourceKey) {
        final int requestId = ++sortRequestId;
        OkGo.getInstance().cancelTag(sortRequestTag);
        L.d("getSort start sourceKey="+sourceKey);
        if (NullUtil.isNull(sourceKey)) {
            postSortResult(null, requestId, sourceKey);
            return;
        }
        SiteBean sourceBean = ApiConfig.getInstance().getSiteBean(sourceKey);
        if (sourceBean == null) {
            postSortResult(null, requestId, sourceKey);
            return;
        }
        int type = sourceBean.getType();
        L.d("getSort() begin type="+type);
        if (type == 3) {
            Runnable waitResponse = () -> {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<String> future = executor.submit(() -> {
                    Spider sp = ApiConfig.getInstance().getCSP(sourceBean);
                    return sp.homeContent(true);
                });
                String sortJson = null;
                try {
                    sortJson = future.get(15, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                } catch (InterruptedException | ExecutionException e) {
                } finally {
                    L.d("getSort() sortJson="+sortJson);
                    if (sortJson != null) {
                        AbsSortXml sortXml = sortJson(sortResult, sortJson,sourceKey);
                        postSortResult(sortXml, requestId, sourceKey);
                        if (sortXml != null && Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
                            AbsXml absXml = parseJson(null, sortJson, sourceBean.getKey(), 0);
                            if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                                sortXml.videoList = absXml.movie.videoList;
                                postSortResult(sortXml, requestId, sourceKey);
                            } else {
                                getHomeRecList(sourceBean, null, new HomeRecCallback() {
                                    @Override
                                    public void done(List<Movie.Video> videos) {
                                        sortXml.videoList = videos;
                                        postSortResult(sortXml, requestId, sourceKey);
                                    }
                                });
                            }
                        } else {
                            postSortResult(sortXml, requestId, sourceKey);
                        }
                    } else {
                        postSortResult(null, requestId, sourceKey);
                    }
                    try {
                        executor.shutdown();
                    } catch (Throwable th) {
                         L.e(th);
                    }
                }
            };
            mExecutorService.execute(waitResponse);
        } else if (type == 0 || type == 1) {
            L.d("getSort api=" + sourceBean.getApi());
            OkGo.<String>get(sourceBean.getApi())
                    .tag(sortRequestTag)
                    .execute(new AbsCallback<String>() {
                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            if (response.body() != null) {
                                return response.body().string();
                            } else {
                                L.d(" getSort() 网络请求失败");
                                throw new IllegalStateException("网络请求失败");
                            }
                        }

                        @Override
                        public void onSuccess(Response<String> response) {
                            L.d("getSort() response="+response.body());
                            AbsSortXml sortXml = null;
                            if (type == 0) {
                                sortXml = sortXml(sortResult, response.body(), sourceKey);
                            } else if (type == 1) {
                                sortXml = sortJson(sortResult, response.body(), sourceKey);
                            }
                            if (sortXml == null) {
                                L.d("getSort sortXml == null");
                                postSortResult(null, requestId, sourceKey);
                                return;
                            }
                            postSortResult(sortXml, requestId, sourceKey);
                            if (Hawk.get(HawkConfig.HOME_REC, 0) == 1 && sortXml.movie != null
                                    && !NullUtil.isNull(sortXml.movie.videoList)) {
                                ArrayList<String> ids = new ArrayList<>();
                                for (Movie.Video vod : sortXml.movie.videoList) {
                                    ids.add(vod.id);
                                }
                                L.d("getSort getHomeRecList start");
                                AbsSortXml finalSortXml = sortXml;
                                getHomeRecList(sourceBean, ids, videos -> {
                                    finalSortXml.videoList = videos;
                                    postSortResult(finalSortXml, requestId, sourceKey);
                                });
                            } else {
                                postSortResult(sortXml, requestId, sourceKey);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            super.onError(response);
                            L.d("getSort() 网络请求错误");
                            postSortResult(null, requestId, sourceKey);
                        }
                    });
        } else {
            postSortResult(null, requestId, sourceKey);
        }
    }

    private void postSortResult(AbsSortXml data, int requestId, String sourceKey) {
        resultHandler.post(() -> {
            if (requestId != sortRequestId) return;
            lastPostedSortSourceKey = sourceKey;
            sortResult.setValue(data);
        });
    }

    public String getLastPostedSortSourceKey() { return lastPostedSortSourceKey; }

    public void cancelSortRequests() {
        sortRequestId++;
        OkGo.getInstance().cancelTag(sortRequestTag);
    }

    public int getMovieList(MovieSort.SortData sortData, int page) {
        final int requestId = ++categoryRequestId;
        OkGo.getInstance().cancelTag(categoryRequestTag);
        SiteBean homeSourceBean = sortData == null || TextUtils.isEmpty(sortData.sourceKey)
                ? ApiConfig.getInstance().getHomeBean() : ApiConfig.getInstance().getSiteBean(sortData.sourceKey);
        if (homeSourceBean == null || sortData == null || !sortData.available) {
            postCategoryResult(null, requestId);
            return requestId;
        }
        final String categoryId = sortData.id;
        final HashMap<String, String> selectedFilters = new HashMap<>(sortData.filterSelect);
        int type = homeSourceBean.getType();
        if (type == 3) {
            mExecutorService.execute(() -> {
                try {
                    Spider sp = ApiConfig.getInstance().getCSP(homeSourceBean);
                    AbsXml data = parseJson(null,
                            sp.categoryContent(categoryId, page + "", true, selectedFilters),
                            homeSourceBean.getKey(), 0);
                    postCategoryResult(data, requestId);
                } catch (Throwable th) {
                    L.e(th);
                    postCategoryResult(null, requestId);
                }
            });
        } else if (type == 0 || type == 1) {
//            mExecutorService.execute(() -> {
//                HashMap<String, String> map = new HashMap<>();
//                map.put("ac", type == 0 ? "videolist" : "detail");
//                map.put("t", sortData.id);
//                map.put("pg", page + "");
//                String url = homeSourceBean.getApi();
//                String result = HttpDoRequest.getInstance(App.getInstance()).doGetRequest(url, map, UiConstans.bUseCase, 5 * 60 * 1000);
//                if (type == 0) {
//                    parseXml(listResult, result, homeSourceBean.getKey(), 0);
//                } else {
//                    parseJson(listResult, result, homeSourceBean.getKey(), 0);
//                }
//            });

            OkGo.<String>get(homeSourceBean.getApi())
                    .tag(categoryRequestTag)
                    .params("ac", type == 0 ? "videolist" : "detail")
                    .params("t", categoryId)
                    .params("pg", page)
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
                            if (type == 0) {
                                String xml = response.body();
                                postCategoryResult(parseXml(null, xml, homeSourceBean.getKey(), 0), requestId);
                            } else {
                                String json = response.body();
                                postCategoryResult(parseJson(null, json, homeSourceBean.getKey(), 0), requestId);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            super.onError(response);
                            postCategoryResult(null, requestId);
                        }
                    });
        } else {
            postCategoryResult(null, requestId);
        }
        return requestId;
    }

    private void postCategoryResult(AbsXml data, int requestId) {
        resultHandler.post(() -> {
            if (requestId != categoryRequestId) return;
            lastPostedCategoryRequestId = requestId;
            listResult.setValue(data);
        });
    }

    public int getLastPostedCategoryRequestId() {
        return lastPostedCategoryRequestId;
    }

    public void getHomeRecList(SiteBean sourceBean, ArrayList<String> ids, HomeRecCallback callback) {
        if (sourceBean.getType() == 3) {
            Runnable waitResponse = () -> {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<String> future = executor.submit(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        Spider sp = ApiConfig.getInstance().getCSP(sourceBean);
                        return sp.homeVideoContent();
                    }
                });
                String sortJson = null;
                try {
                    sortJson = future.get(15, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    L.e(e.getMessage());
                    future.cancel(true);
                } catch (InterruptedException | ExecutionException e) {
                    L.e(e.getMessage());
                } finally {
                    if (sortJson != null) {
                        AbsXml absXml = parseJson(null, sortJson, sourceBean.getKey(), 0);
                        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null) {
                            callback.done(absXml.movie.videoList);
                        } else {
                            callback.done(null);
                        }
                    } else {
                        callback.done(null);
                    }
                    try {
                        executor.shutdown();
                    } catch (Throwable th) {
                        L.e(th);
                    }
                }
            };
            mExecutorService.execute(waitResponse);
        } else if (sourceBean.getType() == 0 || sourceBean.getType() == 1) {
            AbsCallback<String> absCallback = new AbsCallback<String>() {
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
                    AbsXml absXml;
                    if (sourceBean.getType() == 0) {
                        String xml = response.body();
                        absXml = parseXml(null, xml, sourceBean.getKey(), 0);
                    } else {
                        String json = response.body();
                        absXml = parseJson(null, json, sourceBean.getKey(), 0);
                    }
                    if (absXml != null && absXml.movie != null && absXml.movie.videoList != null) {
                        callback.done(absXml.movie.videoList);
                    } else {
                        callback.done(null);
                    }
                }

                @Override
                public void onError(Response<String> response) {
                    super.onError(response);
                    callback.done(null);
                }
            };
            //推荐场景可能没有 ids（按最新列表取），此时不带 ids 参数
            if (ids == null || ids.isEmpty()) {
                OkGo.<String>get(sourceBean.getApi())
                        .tag("home_rec")
                        .params("ac", sourceBean.getType() == 0 ? "videolist" : "detail")
                        .execute(absCallback);
            } else {
                OkGo.<String>get(sourceBean.getApi())
                        .tag("home_rec")
                        .params("ac", sourceBean.getType() == 0 ? "videolist" : "detail")
                        .params("ids", TextUtils.join(",", ids))
                        .execute(absCallback);
            }
        } else {
            callback.done(null);
        }
    }

    public int getDetail(String sourceKey, String id) {
        int requestId = ++detailRequestId;
        requestDetail(sourceKey, id, requestId);
        return requestId;
    }

    private void requestDetail(String sourceKey, String id, int requestId) {
        OkGo.getInstance().cancelTag(detailRequestTag);
        SiteBean sourceBean = ApiConfig.getInstance().getSiteBean(sourceKey);
        if (null == sourceBean) {
            postDetailResult(null, requestId);
            return;
        }
        int type = sourceBean.getType();
        L.d("getDetail() type=" + type +" sourceKey="+sourceKey);
        if (type == 3) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    Spider sp = ApiConfig.getInstance().getCSP(sourceBean);
                    List<String> ids = new ArrayList<>();
                    ids.add(id);
                    String detail = sp.detailContent(ids);
                    L.d("getDetail json="+detail);
                    parseJson(detailResult, detail, sourceBean.getKey(), 0, requestId);
                } catch (Exception th) {
                    L.w("getDetail " + th.getMessage());
                    postDetailResult(null, requestId);
                } finally {
                    // 一次性线程池用完即关，否则每次调用都会泄漏一个线程
                    executor.shutdown();
                }
            });
        } else if (type == 0 || type == 1) {
//            mExecutorService.execute(() -> {
//                HashMap<String, String> map = new HashMap<>();
//                map.put("ac", type == 0 ? "videolist" : "detail");
//                map.put("ids", id);
//                String url = sourceBean.getApi();
//                if (url.endsWith("/")) {
//                    url = url.substring(0, url.length() - 1);
//                }
//                String result = HttpDoRequest.getInstance(App.getInstance()).doGetRequest(url, map, UiConstans.bUseCase, 5 * 60 * 1000);
//                if (type == 0) {
//                    L.d("detail xml=" + result);
//                    parseXml(detailResult, result, sourceBean.getKey(), 0);
//                } else {
//                    L.d("detail json=" + result);
//                    parseJson(detailResult, result, sourceBean.getKey(), 0);
//                }
//            });

            OkGo.<String>get(sourceBean.getApi())
                    .tag(detailRequestTag)
                    .params("ac", type == 0 ? "videolist" : "detail")
                    .params("ids", id)
                    .execute(new AbsCallback<String>() {
                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            if (response.body() != null) {
                                //L.d(" " + response.body().string());//不要消耗这个body，否则请求出错
                                return response.body().string();
                            } else {
                                L.d("getDetail 网络请求失败");
                                throw new IllegalStateException("网络请求失败");
                            }
                        }

                        @Override
                        public void onSuccess(Response<String> response) {
                            if (type == 0) {
                                String xml = response.body();
                                L.d("getDetail xml=" + xml);
                                parseXml(detailResult, xml, sourceBean.getKey(), 0, requestId);
                            } else {
                                String json = response.body();
                                L.d("getDetail json=" + json);
                                parseJson(detailResult, json, sourceBean.getKey(), 0, requestId);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            super.onError(response);
                            L.d("getDetail 网络请求错误");
                            if (response != null) {
                                L.d("  " + response.body());
                            }
                            postDetailResult(null, requestId);
                        }
                    });
        } else {
            postDetailResult(null, requestId);
        }
    }

    private void postDetailResult(AbsXml data, int requestId) {
        resultHandler.post(() -> {
            if (requestId != detailRequestId) return;
            lastPostedDetailRequestId = requestId;
            detailResult.setValue(data);
        });
    }

    public void cancelDetailRequests() {
        detailRequestId++;
        OkGo.getInstance().cancelTag(detailRequestTag);
    }

    public int getLastPostedDetailRequestId() {
        return lastPostedDetailRequestId;
    }

    public void getSearch( String sourceKey, String wd) {
        doSearch(searchResult, sourceKey, wd);
    }

    public void getSearch(String sourceKey, String wd, int requestId) {
        doSearch(searchResult, sourceKey, wd, requestId);
    }

    public void getQuickSearch(String sourceKey, String wd) {
        doSearch(quickSearchResult, sourceKey, wd);
    }

    public void getQuickSearch(String sourceKey, String wd, int requestId) {
        doSearch(quickSearchResult, sourceKey, wd, requestId);
    }

    private void doSearch(MutableLiveData<AbsXml> searchType, String sourceKey, String wd) {
        doSearch(searchType, sourceKey, wd, -1);
    }

    private void doSearch(MutableLiveData<AbsXml> searchType, String sourceKey, String wd, int requestId) {
        SiteBean siteBean = ApiConfig.getInstance().getSiteBean(sourceKey);
        if (siteBean == null) {
            postSearchResult(searchType, null, requestId, sourceKey);
            return;
        }
        int type = siteBean.getType();
        if (type == 3) {
            FutureTask<String> searchTask = null;
            try {
                searchTask = new FutureTask<>(() -> {
                    Spider spider = ApiConfig.getInstance().getCSP(siteBean);
                    if (spider == null) {
                        throw new IllegalStateException("搜索源未初始化");
                    }
                    return spider.searchContent(wd, false);
                });
                Thread searchThread = new Thread(searchTask, "search-" + sourceKey);
                searchThread.setDaemon(true);
                searchThread.start();
                String content = searchTask.get(12, TimeUnit.SECONDS);
                L.d(siteBean.getName()+" doSearch sp  content="+content);
                parseJson(searchType, content, siteBean.getKey(), 0, requestId);
            } catch (TimeoutException e) {
                if (searchTask != null) {
                    searchTask.cancel(true);
                }
                L.w(sourceKey + " spider search timed out");
                postSearchResult(searchType, null, requestId, sourceKey);
            } catch (InterruptedException e) {
                if (searchTask != null) {
                    searchTask.cancel(true);
                }
                Thread.currentThread().interrupt();
                postSearchResult(searchType, null, requestId, sourceKey);
            } catch (ExecutionException | RuntimeException e) {
                L.e(sourceKey + " doSearch " + e.getMessage());
                postSearchResult(searchType, null, requestId, sourceKey);
            } catch (Exception e) {
                L.e(sourceKey + " doSearch " + e.getMessage());
                postSearchResult(searchType, null, requestId, sourceKey);
            }
        } else if (type == 0 || type == 1) {
            final long beginTime = System.currentTimeMillis();
            OkGo.<String>get(siteBean.getApi())
                    .params("wd", wd)
                    .params(type == 1 ? "ac" : null, type == 1 ? "detail" : null)
                    .tag("search")
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
                            long duration = System.currentTimeMillis() - beginTime;
                            if (type == 0) {
                                String xml = response.body();
                                submitSearchParse(searchType, requestId, siteBean.getKey(), () ->
                                        parseXml(searchType, xml, siteBean.getKey(), duration, requestId));
                            } else {
                                String json = response.body();
                                submitSearchParse(searchType, requestId, siteBean.getKey(), () ->
                                        parseJson(searchType, json, siteBean.getKey(), duration, requestId));
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            super.onError(response);
                            postSearchResult(searchType, null, requestId, siteBean.getKey());
                        }

                    });
        } else {
            postSearchResult(searchType, null, requestId, sourceKey);
        }
    }

    private void postSearchResult(MutableLiveData<AbsXml> result, AbsXml data, int requestId, String sourceKey) {
        if (searchResult == result) {
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, data, requestId, sourceKey));
        } else if (quickSearchResult == result) {
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, data, requestId, sourceKey));
        }
    }

    private void submitSearchParse(MutableLiveData<AbsXml> result, int requestId, String sourceKey, Runnable parse) {
        try {
            mSearchParseExecutor.execute(parse);
        } catch (RejectedExecutionException e) {
            L.e("search parse executor rejected request: " + e.getMessage());
            postSearchResult(result, null, requestId, sourceKey);
        }
    }

//    public void getQuickSearch(String sourceKey, String wd) {
//        SiteBean sourceBean = ApiConfig.getInstance().getSource(sourceKey);
//        int type = sourceBean.getType();
//        if (type == 3) {
//            try {
//                Spider sp = ApiConfig.getInstance().getCSP(sourceBean);
//                parseJson(quickSearchResult, sp.searchContent(wd, true), sourceBean.getKey(), 0);
//            } catch (Throwable th) {
//                L.e(th);
//            }
//        } else if (type == 0 || type == 1) {
//            //快速搜索
//            HashMap<String, String> map = new HashMap<>();
//            map.put("wd", wd);
//            map.put("ac", "detail");
//            search
//            String url = sourceBean.getApi();
//            String result = HttpDoRequest.getInstance(App.getInstance()).doGetRequestInOkGo(url, map, UiConstans.bUseCase, 5 * 60 * 1000);
//            if (type == 0) {
//                parseXml(quickSearchResult, result, sourceBean.getKey(), 0);
//            } else {
//                parseJson(quickSearchResult, result, sourceBean.getKey(), 0);
//            }
//        } else {
//            quickSearchResult.postValue(null);
//        }
//    }

    public void getPlayUrl(String sourceKey, String playFlag, String progressKey, String url) {
        L.d("getPlayUrl() sourceKey=" +sourceKey);
        final int requestId = ++playRequestId;
        FutureTask<String> previousTask = activePlayContentTask;
        if (previousTask != null) {
            previousTask.cancel(true);
        }
        SiteBean sourceBean = ApiConfig.getInstance().getSiteBean(sourceKey);
        if (sourceBean == null) {
            postPlayResult(null, requestId);
            return;
        }
        int type = sourceBean.getType();
        L.d("type="+type);
        if (type == 3) {
            FutureTask<String> task = new FutureTask<>(() -> {
                Spider spider = ApiConfig.getInstance().getCSP(sourceBean);
                if (spider == null) {
                    throw new IllegalStateException("播放源未初始化");
                }
                return spider.playerContent(playFlag, url, ApiConfig.getInstance().getmVipFlagList());
            });
            activePlayContentTask = task;
            Thread spiderThread = new Thread(task, "play-content-" + sourceKey);
            spiderThread.setDaemon(true);
            spiderThread.start();
            mExecutorService.execute(() -> {
                try {
                    String json = task.get(15, TimeUnit.SECONDS);
                    if (requestId != playRequestId) {
                        return;
                    }
                    L.d(sourceKey+" getPlayUrl json="+json);
                    JSONObject result = new JSONObject(json);//包含了url
                    result.put("key", url);
                    result.put("proKey", progressKey);
                    if (!result.has("flag")) {
                        result.put("flag", playFlag);
                    }
                    postPlayResult(result, requestId);
                } catch (TimeoutException e) {
                    task.cancel(true);
                    if (requestId == playRequestId) {
                        L.w(sourceKey + " playerContent timed out");
                        postPlayResult(null, requestId);
                    }
                } catch (InterruptedException e) {
                    task.cancel(true);
                    Thread.currentThread().interrupt();
                    if (requestId == playRequestId) {
                        postPlayResult(null, requestId);
                    }
                } catch (Exception e) {
                    if (requestId != playRequestId) {
                        return;
                    }
                    L.e("getPlayUrl "+e.getMessage());
                    postPlayResult(null, requestId);
                }
            });
        } else if (type == 0 || type == 1) {
            try {
                JSONObject result = new JSONObject();
                result.put("key", url);
                String configuredPlayerUrl = sourceBean.getPlayerUrl();
                String playUrl = configuredPlayerUrl == null ? "" : configuredPlayerUrl.trim();
                if (DefaultConfig.isVideoFormat(url) && playUrl.isEmpty()) {
                    result.put("parse", 0);
                    result.put("url", url);
                } else {
                    result.put("parse", 1);
                    result.put("url", url);
                }
                result.put("playUrl", playUrl);
                result.put("flag", playFlag);
                L.d(sourceKey + " getPlayUrl json=" + new Gson().toJson(result));
                postPlayResult(result, requestId);
            } catch (Throwable th) {
                L.e(th.getMessage());
                postPlayResult(null, requestId);
            }
        } else {
            postPlayResult(null, requestId);
        }
    }

    private void postPlayResult(JSONObject data, int requestId) {
        resultHandler.post(() -> {
            if (requestId == playRequestId) playResult.setValue(data);
        });
    }

    private MovieSort.SortFilter getSortFilter(JsonObject obj) {
        String key = obj.get("key").getAsString();
        String name = obj.get("name").getAsString();
        JsonArray kv = obj.getAsJsonArray("value");
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (JsonElement ele : kv) {
            values.put(ele.getAsJsonObject().get("n").getAsString(), ele.getAsJsonObject().get("v").getAsString());
        }
        MovieSort.SortFilter filter = new MovieSort.SortFilter();
        filter.key = key;
        filter.name = name;
        filter.values = values;
        return filter;
    }

    private AbsSortXml sortJson(MutableLiveData<AbsSortXml> result, String json, String soureKey) {
        try {
            L.d(soureKey+" sortJson() json="+json);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            AbsSortJson sortJson = new Gson().fromJson(obj, new TypeToken<AbsSortJson>() {}.getType());
            AbsSortXml absSortXml = sortJson.toAbsSortXml();
            try {
                if (obj.has("filters")) {
                    LinkedHashMap<String, ArrayList<MovieSort.SortFilter>> sortFilters = new LinkedHashMap<>();
                    JsonObject filters = obj.getAsJsonObject("filters");
                    for (String key : filters.keySet()) {
                        ArrayList<MovieSort.SortFilter> sortFilter = new ArrayList<>();
                        JsonElement one = filters.get(key);
                        if (one.isJsonObject()) {
                            sortFilter.add(getSortFilter(one.getAsJsonObject()));
                        } else {
                            for (JsonElement ele : one.getAsJsonArray()) {
                                sortFilter.add(getSortFilter(ele.getAsJsonObject()));
                            }
                        }
                        sortFilters.put(key, sortFilter);
                    }
                    for (MovieSort.SortData sort : absSortXml.classes.sortList) {
                        if (sortFilters.containsKey(sort.id) && sortFilters.get(sort.id) != null) {
                            sort.filters = sortFilters.get(sort.id);
                        }
                    }
                }
            } catch (Throwable th) {
                L.e(th);
            }
            return absSortXml;
        } catch (Exception e) {
            L.e(e.getMessage());
            return null;
        }
    }

    private AbsSortXml sortXml(MutableLiveData<AbsSortXml> result, String xml, String soureKey) {
        try {
            L.d(soureKey + " sortXml() xml=" + xml);
            XStream xstream = new XStream(new DomDriver());//创建Xstram对象
            xstream.autodetectAnnotations(true);
            xstream.processAnnotations(AbsSortXml.class);
            xstream.ignoreUnknownElements();
            AbsSortXml absXml = (AbsSortXml) xstream.fromXML(xml);
            for (MovieSort.SortData sort : absXml.classes.sortList) {
                if (sort.filters == null) {
                    sort.filters = new ArrayList<>();
                }
            }
            return absXml;
        } catch (Exception e) {
            return null;
        }
    }

    private void parseSeriesUrl(AbsXml absXml, String sourceKey) {
        if (absXml.movie != null && absXml.movie.videoList != null) {
            for (Movie.Video video : absXml.movie.videoList) {
                if (video.urlsBean != null && video.urlsBean.flagInfoList != null) {
                    for (Movie.Video.UrlsBean.FlagUrlsInfo urlInfo : video.urlsBean.flagInfoList) {
                        L.d("urlInfo.urls="+urlInfo.urls);
                        urlInfo.nameUrlBeans = UiHelper.getUrlsList(urlInfo.urls);
                    }
                }
                video.sourceKey = sourceKey;
            }
        }
    }

    private void checkThunder(AbsXml data, int requestId) {
        boolean bThunderParse = false;
        if (data.movie != null && data.movie.videoList != null && data.movie.videoList.size() == 1) {
            Movie.Video video = data.movie.videoList.get(0);
            if (video != null && video.urlsBean != null && video.urlsBean.flagInfoList != null && video.urlsBean.flagInfoList.size() == 1) {
                Movie.Video.UrlsBean.FlagUrlsInfo urlInfo = video.urlsBean.flagInfoList.get(0);
                if (urlInfo != null && urlInfo.nameUrlBeans.size() == 1 && Thunder.isSupportUrl(urlInfo.nameUrlBeans.get(0).url)) {
                    bThunderParse = true;
                    Thunder.parse(App.getInstance(), urlInfo.nameUrlBeans.get(0).url, new Thunder.ThunderCallback() {
                        @Override
                        public void status(int code, String info) {
                            if (code >= 0) {
                                L.d(info);
                            } else {
                                urlInfo.nameUrlBeans.get(0).name = info;
                                postDetailResult(data, requestId);
                            }
                        }

                        @Override
                        public void list(String urls) {
                            urlInfo.urls = urls;
                            urlInfo.nameUrlBeans = UiHelper.getUrlsList(urls);
                            postDetailResult(data, requestId);
                        }

                        @Override
                        public void play(String url) {
                        }
                    });
                }
            }
        }
        if (!bThunderParse) {
            postDetailResult(data, requestId);
        }
    }

    private AbsXml parseXml(MutableLiveData<AbsXml> result, String xml, String sourceKey, long duration) {
        return parseXml(result, xml, sourceKey, duration, -1);
    }

    private AbsXml parseXml(MutableLiveData<AbsXml> result, String xml, String sourceKey, long duration, int requestId) {
        try {
            L.d(sourceKey + " parseXml length=" + (xml == null ? -1 : xml.length()));
            XStream xstream = new XStream(new DomDriver());//创建Xstram对象
            xstream.autodetectAnnotations(true);
            xstream.processAnnotations(AbsXml.class);
            xstream.ignoreUnknownElements();
            if (xml.contains("<year></year>")) {
                xml = xml.replace("<year></year>", "<year>2020</year>");
            }
            if (xml.contains("<state></state>")) {
                xml = xml.replace("<state></state>", "<state>0</state>");
            }
            AbsXml absXml = (AbsXml) xstream.fromXML(xml);
            parseSeriesUrl(absXml, sourceKey);
            if (searchResult == result) {
                addDuration(duration, absXml);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, absXml, requestId, sourceKey));
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, absXml, requestId, sourceKey));
            } else if (result != null) {
                if (result == detailResult) {
                    if (requestId == detailRequestId) checkThunder(absXml, requestId);
                } else {
                    result.postValue(absXml);
                }
            }
            return absXml;
        } catch (Exception e) {
            if (searchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null, requestId, sourceKey));
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, null, requestId, sourceKey));
            } else if (result != null) {
                if (result == detailResult) postDetailResult(null, requestId);
                else result.postValue(null);
            }
            return null;
        }
    }

    // 测试数据
    /*json = "{\n" +
            "\t\"list\": [{\n" +
            "\t\t\"vod_id\": \"137133\",\n" +
            "\t\t\"vod_name\": \"磁力测试\",\n" +
            "\t\t\"vod_pic\": \"https:/img9.doubanio.com/view/photo/s_ratio_poster/public/p2656327176.webp\",\n" +
            "\t\t\"type_name\": \"剧情 / 爱情 / 古装\",\n" +
            "\t\t\"vod_year\": \"2022\",\n" +
            "\t\t\"vod_area\": \"中国大陆\",\n" +
            "\t\t\"vod_remarks\": \"40集全\",\n" +
            "\t\t\"vod_actor\": \"刘亦菲\",\n" +
            "\t\t\"vod_director\": \"杨阳\",\n" +
            "\t\t\"vod_content\": \"　　在钱塘开茶铺的赵盼儿（刘亦菲 饰）惊闻未婚夫、新科探花欧阳旭（徐海乔 饰）要另娶当朝高官之女，不甘命运的她誓要上京讨个公道。在途中她遇到了出自权门但生性正直的皇城司指挥顾千帆（陈晓 饰），并卷入江南一场大案，两人不打不相识从而结缘。赵盼儿凭借智慧解救了被骗婚而惨遭虐待的“江南第一琵琶高手”宋引章（林允 饰）与被苛刻家人逼得离家出走的豪爽厨娘孙三娘（柳岩 饰），三位姐妹从此结伴同行，终抵汴京，见识世间繁华。为了不被另攀高枝的欧阳旭从东京赶走，赵盼儿与宋引章、孙三娘一起历经艰辛，将小小茶坊一步步发展为汴京最大的酒楼，揭露了负心人的真面目，收获了各自的真挚感情和人生感悟，也为无数平凡女子推开了一扇平等救赎之门。\",\n" +
            "\t\t\"vod_play_from\": \"磁力测试\",\n" +
            "\t\t\"vod_play_url\": \"0$magnet:?xt=urn:btih:9e9358b946c427962533472efdd2efd9e9e38c67&dn=%e9%98%b3%e5%85%89%e7%94%b5%e5%bd%b1www.ygdy8.com.%e7%83%ad%e8%a1%80.2022.BD.1080P.%e9%9f%a9%e8%af%ad%e4%b8%ad%e8%8b%b1%e5%8f%8c%e5%ad%97.mkv&tr=udp%3a%2f%2ftracker.opentrackr.org%3a1337%2fannounce&tr=udp%3a%2f%2fexodus.desync.com%3a6969%2fannounce\"\n" +
            "\t}]\n" +
            "}";*/
    private AbsXml parseJson(MutableLiveData<AbsXml> result, String json, String sourceKey, long duration) {
        return parseJson(result, json, sourceKey, duration, -1);
    }

    private AbsXml parseJson(MutableLiveData<AbsXml> result, String json, String sourceKey, long duration, int requestId) {
        try {
            AbsXml absXml = null;
            AbsJson absJson = new Gson().fromJson(json, new TypeToken<AbsJson>() {}.getType());
            L.d(sourceKey + " parseJson length=" + (json == null ? -1 : json.length()));
            if (absJson != null) {
                absXml = absJson.toAbsXml();
                parseSeriesUrl(absXml, sourceKey);
                if (searchResult == result) {
                    addDuration(duration, absXml);
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, absXml, requestId, sourceKey));
                } else if (quickSearchResult == result) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, absXml, requestId, sourceKey));
                } else if (result != null) {
                    if (result == detailResult) {
                        if (requestId == detailRequestId) checkThunder(absXml, requestId);
                    } else {
                        result.postValue(absXml);
                    }
                }
            } else {
                postSearchResult(result, null, requestId, sourceKey);
            }
            return absXml;
        } catch (Exception e) {
            if (searchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null, requestId, sourceKey));
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, null, requestId, sourceKey));
            } else if (result != null) {
                if (result == detailResult) postDetailResult(null, requestId);
                else result.postValue(null);
            }
            return null;
        }
    }

    private void addDuration(long duration, AbsXml absXml) {
        if (absXml == null || absXml.movie == null) return;
        for (Movie.Video item : absXml.movie.videoList) {
            if (duration > 0) {
                item.name = "(" + duration + ")" + item.name;
                item.dulation = duration;
            }
        }
    }
}
