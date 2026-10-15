package com.github.tvbox.osc.viewmodel;

import android.text.TextUtils;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    public void getSort(String sourceKey) {
        L.d("getSort start sourceKey="+sourceKey);
        if (NullUtil.isNull(sourceKey)) {
            sortResult.postValue(null);
            return;
        }
        SiteBean sourceBean = ApiConfig.getInstance().getSiteBean(sourceKey);
        if (sourceBean == null) {
            sortResult.postValue(null);
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
                        if (sortXml != null && Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
                            AbsXml absXml = parseJson(null, sortJson, sourceBean.getKey(), 0);
                            if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                                sortXml.videoList = absXml.movie.videoList;
                                sortResult.postValue(sortXml);
                            } else {
                                getHomeRecList(sourceBean, null, new HomeRecCallback() {
                                    @Override
                                    public void done(List<Movie.Video> videos) {
                                        sortXml.videoList = videos;
                                        sortResult.postValue(sortXml);
                                    }
                                });
                            }
                        } else {
                            sortResult.postValue(sortXml);
                        }
                    } else {
                        sortResult.postValue(null);
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
                    .tag(sourceBean.getKey() + "_sort")
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
                                sortResult.postValue(null);
                                return;
                            }
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
                                    sortResult.postValue(finalSortXml);
                                });
                            } else {
                                sortResult.postValue(sortXml);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            super.onError(response);
                            L.d("getSort() 网络请求错误");
                            sortResult.postValue(null);
                        }
                    });
        } else {
            sortResult.postValue(null);
        }
    }

    public void getMovieList(MovieSort.SortData sortData, int page) {
        SiteBean homeSourceBean = ApiConfig.getInstance().getHomeBean();
        int type = homeSourceBean.getType();
        if (type == 3) {
            mExecutorService.execute(() -> {
                try {
                    Spider sp = ApiConfig.getInstance().getCSP(homeSourceBean);
                    parseJson(listResult, sp.categoryContent(sortData.id, page + "", true, sortData.filterSelect), homeSourceBean.getKey(), 0);
                } catch (Throwable th) {
                    L.e(th);
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
                    .tag(homeSourceBean.getApi())
                    .params("ac", type == 0 ? "videolist" : "detail")
                    .params("t", sortData.id)
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
                                parseXml(listResult, xml, homeSourceBean.getKey(), 0);
                            } else {
                                String json = response.body();
                                parseJson(listResult, json, homeSourceBean.getKey(), 0);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            super.onError(response);
                            listResult.postValue(null);
                        }
                    });
        } else {
            listResult.postValue(null);
        }
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
                        .tag("detail")
                        .params("ac", sourceBean.getType() == 0 ? "videolist" : "detail")
                        .execute(absCallback);
            } else {
                OkGo.<String>get(sourceBean.getApi())
                        .tag("detail")
                        .params("ac", sourceBean.getType() == 0 ? "videolist" : "detail")
                        .params("ids", TextUtils.join(",", ids))
                        .execute(absCallback);
            }
        } else {
            callback.done(null);
        }
    }

    public void getDetail(String sourceKey, String id) {
        SiteBean sourceBean = ApiConfig.getInstance().getSiteBean(sourceKey);
        if (null == sourceBean) return;
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
                    parseJson(detailResult, detail, sourceBean.getKey(), 0);
                } catch (Exception th) {
                    L.w("getDetail " + th.getMessage());
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
                    .tag("detail")
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
                                parseXml(detailResult, xml, sourceBean.getKey(), 0);
                            } else {
                                String json = response.body();
                                L.d("getDetail json=" + json);
                                parseJson(detailResult, json, sourceBean.getKey(), 0);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            super.onError(response);
                            L.d("getDetail 网络请求错误");
                            if (response != null) {
                                L.d("  " + response.body());
                            }
                            detailResult.postValue(null);
                        }
                    });
        } else {
            detailResult.postValue(null);
        }
    }

    public void getSearch( String sourceKey, String wd) {
        doSearch(searchResult, sourceKey, wd);
    }

    public void getQuickSearch(String sourceKey, String wd) {
        doSearch(quickSearchResult, sourceKey, wd);
    }

    private void doSearch(MutableLiveData<AbsXml> searchType, String sourceKey, String wd) {
        SiteBean siteBean = ApiConfig.getInstance().getSiteBean(sourceKey);
        if (siteBean == null) return;
        int type = siteBean.getType();
        if (type == 3) {
            try {
                Spider sp = ApiConfig.getInstance().getCSP(siteBean);
                L.d("doSearch sp "+sp);
                if (sp == null) return;
                String content = sp.searchContent(wd, false);
                L.d(siteBean.getName()+" doSearch sp  content="+content);
                parseJson(searchType, content, siteBean.getKey(), 0);
            } catch (Exception e) {
                L.e(sourceKey + " doSearch " + e.getMessage());
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
                                parseXml(searchType, xml, siteBean.getKey(), duration);
                            } else {
                                String json = response.body();
                                parseJson(searchType, json, siteBean.getKey(), duration);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            super.onError(response);
                            if (searchResult == searchType) {
                                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null));
                            } else if (quickSearchResult == searchType) {
                                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, null));
                            }
                        }
                    });
        } else {
            searchType.postValue(null);
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
        SiteBean sourceBean = ApiConfig.getInstance().getSiteBean(sourceKey);
        int type = sourceBean.getType();
        L.d("type="+type);
        if (type == 3) {
            mExecutorService.execute(() -> {
                Spider sp = ApiConfig.getInstance().getCSP(sourceBean);
                String json = sp.playerContent(playFlag, url, ApiConfig.getInstance().getmVipFlagList());
                L.d(sourceKey+" getPlayUrl json="+json);
                try {
                    JSONObject result = new JSONObject(json);//包含了url
                    result.put("key", url);
                    result.put("proKey", progressKey);
                    if (!result.has("flag")) {
                        result.put("flag", playFlag);
                    }
                    playResult.postValue(result);
                } catch (Throwable th) {
                    L.e("getPlayUrl "+th.getMessage());
                    playResult.postValue(null);
                }
            });
        } else if (type == 0 || type == 1) {
            try {
                JSONObject result = new JSONObject();
                result.put("key", url);
                String playUrl = sourceBean.getPlayerUrl().trim();
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
                playResult.postValue(result);
            } catch (Throwable th) {
                L.e(th.getMessage());
                playResult.postValue(null);
            }
        } else {
            playResult.postValue(null);
        }
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

    private void checkThunder(AbsXml data) {
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
                                detailResult.postValue(data);
                            }
                        }

                        @Override
                        public void list(String urls) {
                            urlInfo.urls = urls;
                            urlInfo.nameUrlBeans = UiHelper.getUrlsList(urls);
                            detailResult.postValue(data);
                        }

                        @Override
                        public void play(String url) {
                        }
                    });
                }
            }
        }
        if (!bThunderParse) {
            detailResult.postValue(data);
        }
    }

    private AbsXml parseXml(MutableLiveData<AbsXml> result, String xml, String sourceKey, long duration) {
        try {
            L.d(sourceKey+" parseXml() xml="+xml);
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
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, absXml));
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, absXml));
            } else if (result != null) {
                if (result == detailResult) {
                    checkThunder(absXml);
                } else {
                    result.postValue(absXml);
                }
            }
            return absXml;
        } catch (Exception e) {
            if (searchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null));
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, null));
            } else if (result != null) {
                result.postValue(null);
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
        try {
            AbsXml absXml = null;
            AbsJson absJson = new Gson().fromJson(json, new TypeToken<AbsJson>() {}.getType());
            L.d(sourceKey + " absJson=" + json);
            if (absJson != null) {
                absXml = absJson.toAbsXml();
                parseSeriesUrl(absXml, sourceKey);
                if (searchResult == result) {
                    addDuration(duration, absXml);
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, absXml));
                } else if (quickSearchResult == result) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, absXml));
                } else if (result != null) {
                    if (result == detailResult) {
                        checkThunder(absXml);
                    } else {
                        result.postValue(absXml);
                    }
                }
            }else{
                result.postValue(null);
            }
            return absXml;
        } catch (Exception e) {
            if (searchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null));
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, null));
            } else if (result != null) {
                result.postValue(null);
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