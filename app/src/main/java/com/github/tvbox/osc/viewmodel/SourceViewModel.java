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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.KXml2Driver;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SourceViewModel extends ViewModel {
    private final ThreadLocal<Boolean> folderSpiderCall = new ThreadLocal<>();

    public boolean isInFolderSpiderCall() {
        return Boolean.TRUE.equals(folderSpiderCall.get());
    }
    public static final String TAG_QUICK_SEARCH = "quick_search";
    private final Object searchRequestTag = new Object();

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
                if (raw == null) return 0;
                String s = raw.trim();
                if (s.isEmpty()) return 0;
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    try {
                        return (int) Double.parseDouble(s);
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
    private static final int MAX_SEARCH_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final long SPIDER_SEARCH_TIMEOUT_SECONDS = 8L;
    private static final long FOLDER_SPIDER_TIMEOUT_SECONDS = 12L;
    private static final java.util.regex.Pattern XML_EMPTY_OR_BAD_YEAR =
            java.util.regex.Pattern.compile("<year>(?!\\s*\\d+\\s*</year>)[^<]*</year>");
    private static final java.util.regex.Pattern XML_EMPTY_OR_BAD_TID =
            java.util.regex.Pattern.compile("<tid>(?!\\s*-?\\d+\\s*</tid>)[^<]*</tid>");

    public static final class SearchTaskHandle {
        private final AtomicBoolean done;
        private volatile Future<?> workerFuture;
        private volatile ScheduledFuture<?> timeoutFuture;

        SearchTaskHandle(AtomicBoolean done) {
            this.done = done;
        }

        void setWorkerFuture(Future<?> workerFuture) {
            this.workerFuture = workerFuture;
        }

        void setTimeoutFuture(ScheduledFuture<?> timeoutFuture) {
            this.timeoutFuture = timeoutFuture;
            if (done != null && done.get() && timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
        }

        public boolean isDone() {
            return done != null && done.get();
        }

        public void cancel() {
            if (done != null) {
                done.set(true);
            }
            ScheduledFuture<?> tf = timeoutFuture;
            if (tf != null) {
                tf.cancel(false);
            }
            Future<?> wf = workerFuture;
            if (wf != null) {
                wf.cancel(true);
            }
        }
    }

    private static HierarchicalStreamDriver createXmlDriver() {
        try {
            Class.forName("org.kxml2.io.KXmlParser");
            return new KXml2Driver();
        } catch (Throwable ignored) {
            return new DomDriver();
        }
    }

    private static XStream createSortXmlXStream() {
        XStream xstream = new XStream(createXmlDriver());
        xstream.processAnnotations(new Class<?>[]{
                AbsSortXml.class,
                MovieSort.class,
                MovieSort.SortData.class,
                Movie.class,
                Movie.Video.class,
                Movie.Video.UrlsBean.class,
                Movie.Video.UrlsBean.FlagUrlsInfo.class
        });
        xstream.ignoreUnknownElements();
        return xstream;
    }

    private static XStream createAbsXmlXStream() {
        XStream xstream = new XStream(createXmlDriver());
        xstream.processAnnotations(new Class<?>[]{
                AbsXml.class,
                Movie.class,
                Movie.Video.class,
                Movie.Video.UrlsBean.class,
                Movie.Video.UrlsBean.FlagUrlsInfo.class
        });
        xstream.ignoreUnknownElements();
        return xstream;
    }

    // XStream 实例按线程隔离，彻底消除并发解析时的内部状态竞态
    private static final ThreadLocal<XStream> SORT_XML_XSTREAM = ThreadLocal.withInitial(SourceViewModel::createSortXmlXStream);
    private static final ThreadLocal<XStream> ABS_XML_XSTREAM = ThreadLocal.withInitial(SourceViewModel::createAbsXmlXStream);

    private static String readResponseBodyLimited(okhttp3.Response response, int maxBytes) throws IOException {
        if (response == null || response.body() == null) {
            throw new IllegalStateException("网络请求错误");
        }
        long contentLength = response.body().contentLength();
        if (contentLength > maxBytes) {
            response.close();
            throw new IOException("响应体过大: " + contentLength);
        }
        try (InputStream in = response.body().byteStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(
                    contentLength > 0 ? (int) Math.min(contentLength, 65536) : 8192);
            byte[] buf = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("响应体超过限制: " + maxBytes);
                }
                out.write(buf, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public MutableLiveData<AbsSortXml> sortResult;
    public MutableLiveData<AbsXml> listResult;
    public MutableLiveData<AbsXml> searchResult;
    public MutableLiveData<AbsXml> quickSearchResult;
    public MutableLiveData<AbsXml> detailResult;
    public MutableLiveData<JSONObject> playResult;
    public ExecutorService mExecutorService;
    private final ExecutorService mSearchParseExecutor = createDaemonPool(4, "search-parse-");
    private final ExecutorService mSpiderWorkerExecutor = createDaemonPool(10, "spider-pool-");
    private final ScheduledExecutorService mSearchTimeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "search-timeout");
        t.setDaemon(true);
        return t;
    });

    private static ExecutorService createDaemonPool(int maxThreads, String prefix) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                maxThreads, maxThreads, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private final AtomicInteger threadIndex = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, prefix + threadIndex.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
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
        cancelSearchRequests();
        resultHandler.removeCallbacksAndMessages(null);
        FutureTask<String> playTask = activePlayContentTask;
        if (playTask != null) {
            playTask.cancel(true);
        }
        playRequestId++;
        mSearchParseExecutor.shutdownNow();
        mSpiderWorkerExecutor.shutdownNow();
        mSearchTimeoutScheduler.shutdownNow();
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
                Future<String> future = mSpiderWorkerExecutor.submit(() -> {
                    Spider sp = ApiConfig.getInstance().getCSP(sourceBean);
                    return sp != null ? sp.homeContent(true) : null;
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

    private static String sanitizeHttpUrl(String rawUrl) {
        if (rawUrl == null) return "";
        String normalized = ApiConfig.normalizeUrl(rawUrl);
        if (normalized == null) return "";
        String trimmed = normalized.trim();
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "";
        }
        return trimmed;
    }

    public int getFolderList(String sourceKey, String id) {
        final int requestId = ++categoryRequestId;
        OkGo.getInstance().cancelTag(categoryRequestTag);
        SiteBean sourceBean = ApiConfig.getInstance().getSiteBean(sourceKey);
        if (sourceBean == null || TextUtils.isEmpty(id)) {
            postCategoryResult(null, requestId);
            return requestId;
        }
        if (sourceBean.getType() == 3) {
            if (mSpiderWorkerExecutor.isShutdown() || mSearchTimeoutScheduler.isShutdown()) {
                postCategoryResult(null, requestId);
                return requestId;
            }
            final AtomicBoolean finished = new AtomicBoolean(false);
            final Future<?>[] workerRef = new Future<?>[1];
            final ScheduledFuture<?>[] timeoutRef = new ScheduledFuture<?>[1];
            try {
                workerRef[0] = mSpiderWorkerExecutor.submit(() -> {
                    if (finished.get() || requestId != categoryRequestId || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    // 线程真正开始执行时再启动 12 秒超时，避免入队等待被误杀且不占满 3 线程 mExecutorService
                    try {
                        timeoutRef[0] = mSearchTimeoutScheduler.schedule(() -> {
                            if (finished.compareAndSet(false, true)) {
                                if (workerRef[0] != null) workerRef[0].cancel(true);
                                android.util.Log.w("SearchRoute", "folder timeout request=" + requestId
                                        + " source=" + sourceKey);
                                postCategoryResult(null, requestId);
                            }
                        }, FOLDER_SPIDER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    } catch (RejectedExecutionException ignored) {
                    }
                    try {
                        Spider spider = ApiConfig.getInstance().getCSP(sourceBean);
                        if (spider == null) {
                            if (finished.compareAndSet(false, true)) {
                                if (timeoutRef[0] != null) timeoutRef[0].cancel(false);
                                postCategoryResult(null, requestId);
                            }
                            return;
                        }
                        boolean panSearchFolder = "S_zpsGuard".equals(spider.getClass().getSimpleName());
                        AbsXml parsed = loadSpiderFolder(spider, sourceKey, id, requestId, panSearchFolder);
                        if (!panSearchFolder && requestId == categoryRequestId
                                && !Thread.currentThread().isInterrupted()
                                && (parsed == null || parsed.movie == null || parsed.movie.videoList == null
                                || parsed.movie.videoList.isEmpty())) {
                            parsed = loadSpiderFolder(spider, sourceKey, id, requestId, true);
                        }
                        if (finished.compareAndSet(false, true)) {
                            if (timeoutRef[0] != null) timeoutRef[0].cancel(false);
                            postCategoryResult(parsed, requestId);
                        }
                    } catch (Throwable th) {
                        if (finished.compareAndSet(false, true)) {
                            if (timeoutRef[0] != null) timeoutRef[0].cancel(false);
                            android.util.Log.w("SearchRoute", "folder error request=" + requestId
                                    + " errorType=" + th.getClass().getName());
                            L.e("getFolderList " + th.getMessage());
                            postCategoryResult(null, requestId);
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                postCategoryResult(null, requestId);
            }
        } else if (sourceBean.getType() == 0 || sourceBean.getType() == 1) {
            String apiUrl = sanitizeHttpUrl(sourceBean.getApi());
            if (apiUrl.isEmpty()) {
                postCategoryResult(null, requestId);
                return requestId;
            }
            try {
                OkGo.<AbsXml>get(apiUrl)
                        .tag(categoryRequestTag)
                        .retryCount(0)
                        .params("ac", sourceBean.getType() == 0 ? "videolist" : "detail")
                        .params("ids", id)
                        .execute(new AbsCallback<AbsXml>() {
                            @Override
                            public AbsXml convertResponse(okhttp3.Response response) throws Throwable {
                                // 在后台网络线程完成 XML/JSON 解析，避免大目录阻塞主线程
                                String body = readResponseBodyLimited(response, MAX_SEARCH_RESPONSE_BYTES);
                                return sourceBean.getType() == 0
                                        ? parseXml(null, body, sourceKey, 0)
                                        : parseJson(null, body, sourceKey, 0);
                            }

                            @Override
                            public void onSuccess(Response<AbsXml> response) {
                                postCategoryResult(response.body(), requestId);
                            }

                            @Override
                            public void onError(Response<AbsXml> response) {
                                postCategoryResult(null, requestId);
                            }
                        });
            } catch (Throwable th) {
                L.w("getFolderList invalid url=" + apiUrl + " err=" + th.getMessage());
                postCategoryResult(null, requestId);
            }
        } else {
            postCategoryResult(null, requestId);
        }
        return requestId;
    }

    private AbsXml loadSpiderFolder(Spider spider, String sourceKey, String id, int requestId,
                                    boolean useCategory) throws Throwable {
        String method = useCategory ? "categoryContent" : "detailContent";
        String json;
        folderSpiderCall.set(true);
        try {
            json = useCategory
                    ? spider.categoryContent(id, "1", false, new HashMap<>())
                    : spider.detailContent(new ArrayList<>(Collections.singletonList(id)));
        } catch (UnsupportedOperationException e) {
            android.util.Log.i("SearchRoute", "folder unsupported request=" + requestId + " method=" + method);
            return null;
        } finally {
            folderSpiderCall.remove();
        }
        AbsXml parsed = TextUtils.isEmpty(json) ? null : parseJson(null, json, sourceKey, 0);
        android.util.Log.i("SearchRoute", "folder return request=" + requestId + " method=" + method
                + " spider=" + spider.getClass().getName()
                + " entries=" + (parsed == null || parsed.movie == null || parsed.movie.videoList == null
                ? 0 : parsed.movie.videoList.size()));
        return parsed;
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
        getHomeRecList(ApiConfig.getInstance(), sourceBean, ids, callback);
    }

    public void getHomeRecList(ApiConfig config, SiteBean sourceBean, ArrayList<String> ids, HomeRecCallback callback) {
        if (sourceBean.getType() == 3) {
            Runnable waitResponse = () -> {
                Future<String> future = mSpiderWorkerExecutor.submit(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        Spider sp = config.getCSP(sourceBean);
                        return sp != null ? sp.homeVideoContent() : null;
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
                        .tag(config == ApiConfig.getInstance() ? "home_rec" : config)
                        .params("ac", sourceBean.getType() == 0 ? "videolist" : "detail")
                        .execute(absCallback);
            } else {
                OkGo.<String>get(sourceBean.getApi())
                        .tag(config == ApiConfig.getInstance() ? "home_rec" : config)
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
            if (mExecutorService == null || mExecutorService.isShutdown()) return;
            mExecutorService.execute(() -> {
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

    public void cancelSearchRequests() {
        OkGo.getInstance().cancelTag(searchRequestTag);
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

    /**
     * 单层异步搜索派发：HTTP 源走 OkGo 异步回调，爬虫源直接投递到 mSpiderWorkerExecutor；
     * 8 秒单源超时在工作线程真正开始执行该源时才启动计时，避免队列后面的健康源在排队期间被提前误杀。
     */
    public SearchTaskHandle getSearchAsync(String sourceKey, String wd, int requestId) {
        SiteBean siteBean = ApiConfig.getInstance().getSiteBean(sourceKey);
        if (siteBean == null) {
            postSearchResult(searchResult, null, requestId, sourceKey);
            return null;
        }
        int type = siteBean.getType();
        if (type == 0 || type == 1) {
            doHttpSearch(searchResult, siteBean, wd, requestId);
            return null;
        }
        if (type != 3) {
            postSearchResult(searchResult, null, requestId, sourceKey);
            return null;
        }
        if (mSpiderWorkerExecutor.isShutdown() || mSearchTimeoutScheduler.isShutdown()) {
            postSearchResult(searchResult, null, requestId, sourceKey);
            return null;
        }
        final AtomicBoolean finished = new AtomicBoolean(false);
        final SearchTaskHandle handle = new SearchTaskHandle(finished);
        try {
            Future<?> workerFuture = mSpiderWorkerExecutor.submit(() -> {
                if (finished.get() || Thread.currentThread().isInterrupted()) return;
                // 任务真正拿到线程开始执行后再开启 8 秒倒计时，避免排队时间计入单源超时
                try {
                    ScheduledFuture<?> timeoutFuture = mSearchTimeoutScheduler.schedule(() -> {
                        if (finished.compareAndSet(false, true)) {
                            Future<?> wf = handle.workerFuture;
                            if (wf != null) wf.cancel(true);
                            L.w(sourceKey + " spider search timed out");
                            postSearchResult(searchResult, null, requestId, sourceKey);
                        }
                    }, SPIDER_SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    handle.setTimeoutFuture(timeoutFuture);
                } catch (RejectedExecutionException ignored) {
                }
                if (finished.get() || Thread.currentThread().isInterrupted()) return;
                try {
                    Spider spider = ApiConfig.getInstance().getCSP(siteBean);
                    if (spider == null) {
                        throw new IllegalStateException("搜索源未初始化");
                    }
                    String content = spider.searchContent(wd, false);
                    if (finished.compareAndSet(false, true)) {
                        ScheduledFuture<?> tf = handle.timeoutFuture;
                        if (tf != null) tf.cancel(false);
                        parseJson(searchResult, content, siteBean.getKey(), 0, requestId);
                    }
                } catch (Throwable e) {
                    if (finished.compareAndSet(false, true)) {
                        ScheduledFuture<?> tf = handle.timeoutFuture;
                        if (tf != null) tf.cancel(false);
                        L.e(sourceKey + " getSearchAsync " + e.getMessage());
                        postSearchResult(searchResult, null, requestId, sourceKey);
                    }
                }
            });
            handle.setWorkerFuture(workerFuture);
            return handle;
        } catch (RejectedExecutionException e) {
            if (finished.compareAndSet(false, true)) {
                postSearchResult(searchResult, null, requestId, sourceKey);
            }
            return null;
        }
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
            if (mSpiderWorkerExecutor.isShutdown()) {
                postSearchResult(searchType, null, requestId, sourceKey);
                return;
            }
            Future<String> searchFuture = null;
            try {
                searchFuture = mSpiderWorkerExecutor.submit(() -> {
                    Spider spider = ApiConfig.getInstance().getCSP(siteBean);
                    if (spider == null) {
                        throw new IllegalStateException("搜索源未初始化");
                    }
                    return spider.searchContent(wd, false);
                });
                String content = searchFuture.get(8, TimeUnit.SECONDS);
                parseJson(searchType, content, siteBean.getKey(), 0, requestId);
            } catch (TimeoutException e) {
                if (searchFuture != null) {
                    searchFuture.cancel(true);
                }
                L.w(sourceKey + " spider search timed out");
                postSearchResult(searchType, null, requestId, sourceKey);
            } catch (InterruptedException e) {
                if (searchFuture != null) {
                    searchFuture.cancel(true);
                }
                Thread.currentThread().interrupt();
                postSearchResult(searchType, null, requestId, sourceKey);
            } catch (Throwable e) {
                L.e(sourceKey + " doSearch " + e.getMessage());
                postSearchResult(searchType, null, requestId, sourceKey);
            }
        } else if (type == 0 || type == 1) {
            doHttpSearch(searchType, siteBean, wd, requestId);
        } else {
            postSearchResult(searchType, null, requestId, sourceKey);
        }
    }

    private void doHttpSearch(MutableLiveData<AbsXml> searchType, SiteBean siteBean, String wd, int requestId) {
        final int type = siteBean.getType();
        final String apiUrl = sanitizeHttpUrl(siteBean.getApi());
        if (apiUrl.isEmpty()) {
            postSearchResult(searchType, null, requestId, siteBean.getKey());
            return;
        }
        final long beginTime = System.currentTimeMillis();
        final Object tag = (searchType == quickSearchResult) ? TAG_QUICK_SEARCH : searchRequestTag;
        try {
            OkGo.<String>get(apiUrl)
                    .params("wd", wd)
                    // XML 采集源带 ac=videolist、JSON 采集源带 ac=detail，保证搜索卡片拿到海报封面图
                    .params("ac", type == 0 ? "videolist" : "detail")
                    .retryCount(0)
                    .tag(tag)
                    .execute(new AbsCallback<String>() {
                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            return readResponseBodyLimited(response, MAX_SEARCH_RESPONSE_BYTES);
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
        } catch (Throwable th) {
            L.w("doHttpSearch invalid url=" + apiUrl + " err=" + th.getMessage());
            postSearchResult(searchType, null, requestId, siteBean.getKey());
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

    public interface PlayPrefetchCallback {
        void onResult(JSONObject info);
    }

    /**
     * 静默预解析下一集播放地址：独立于 playRequestId 与 activePlayContentTask，
     * 不取消当前集任务、不污染 playResult，失败静默返回 null。
     */
    public void prefetchPlayUrl(String sourceKey, String playFlag, String progressKey,
                                String url, PlayPrefetchCallback callback) {
        if (callback == null) return;
        SiteBean sourceBean = ApiConfig.getInstance().getSiteBean(sourceKey);
        if (sourceBean == null) {
            resultHandler.post(() -> callback.onResult(null));
            return;
        }
        int type = sourceBean.getType();
        if (type == 3) {
            mExecutorService.execute(() -> {
                JSONObject parsed = null;
                try {
                    Spider spider = ApiConfig.getInstance().getCSP(sourceBean);
                    if (spider != null) {
                        String json = spider.playerContent(playFlag, url, ApiConfig.getInstance().getmVipFlagList());
                        if (json != null && !json.trim().isEmpty()) {
                            JSONObject result = new JSONObject(json);
                            result.put("key", url);
                            result.put("proKey", progressKey);
                            if (!result.has("flag")) {
                                result.put("flag", playFlag);
                            }
                            parsed = result;
                        }
                    }
                } catch (Throwable ignored) {
                }
                final JSONObject finalResult = parsed;
                resultHandler.post(() -> callback.onResult(finalResult));
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
                resultHandler.post(() -> callback.onResult(result));
            } catch (Throwable ignored) {
                resultHandler.post(() -> callback.onResult(null));
            }
        } else {
            resultHandler.post(() -> callback.onResult(null));
        }
    }

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
            AbsSortJson sortJson = GSON.fromJson(obj, ABS_SORT_JSON_TYPE);
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
            if (xml == null || xml.isEmpty()) return null;
            AbsSortXml absXml = (AbsSortXml) SORT_XML_XSTREAM.get().fromXML(xml);
            if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                for (MovieSort.SortData sort : absXml.classes.sortList) {
                    if (sort.filters == null) {
                        sort.filters = new ArrayList<>();
                    }
                }
            }
            return absXml;
        } catch (Throwable e) {
            return null;
        }
    }

    private void parseSeriesUrl(AbsXml absXml, String sourceKey, boolean compactForSearch) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null) {
            for (Movie.Video video : absXml.movie.videoList) {
                if (video == null) continue;
                boolean playable = false;
                if (video.urlsBean != null && video.urlsBean.flagInfoList != null) {
                    for (Movie.Video.UrlsBean.FlagUrlsInfo urlInfo : video.urlsBean.flagInfoList) {
                        if (urlInfo == null) continue;
                        if (!TextUtils.isEmpty(urlInfo.urls)) {
                            playable = true;
                            if (!compactForSearch) {
                                urlInfo.nameUrlBeans = UiHelper.getUrlsList(urlInfo.urls);
                            }
                        }
                    }
                }
                video.hasPlayableUrls = playable;
                if (compactForSearch) {
                    // 搜索列表保留轻量的演员/导演用于关键词匹配，释放巨型播放串与长剧情简介以降低 GC 与 OOM 风险
                    video.urlsBean = null;
                    video.des = null;
                }
                video.sourceKey = sourceKey;
            }
        }
    }

    private AbsXml parseXml(MutableLiveData<AbsXml> result, String xml, String sourceKey, long duration) {
        return parseXml(result, xml, sourceKey, duration, -1);
    }

    private AbsXml parseXml(MutableLiveData<AbsXml> result, String xml, String sourceKey, long duration, int requestId) {
        try {
            if (xml == null || xml.isEmpty()) {
                postSearchResult(result, null, requestId, sourceKey);
                if (result != null && result != searchResult && result != quickSearchResult) {
                    if (result == detailResult) postDetailResult(null, requestId);
                    else result.postValue(null);
                }
                return null;
            }
            L.d(sourceKey + " parseXml length=" + xml.length());
            if (xml.contains("<year>")) {
                xml = XML_EMPTY_OR_BAD_YEAR.matcher(xml).replaceAll("<year>0</year>");
            }
            if (xml.contains("<tid>")) {
                xml = XML_EMPTY_OR_BAD_TID.matcher(xml).replaceAll("<tid>0</tid>");
            }
            if (xml.contains("<state></state>")) {
                xml = xml.replace("<state></state>", "<state>0</state>");
            }
            AbsXml absXml = (AbsXml) ABS_XML_XSTREAM.get().fromXML(xml);
            parseSeriesUrl(absXml, sourceKey, searchResult == result);
            if (searchResult == result) {
                addDuration(duration, absXml);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, absXml, requestId, sourceKey));
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, absXml, requestId, sourceKey));
            } else if (result != null) {
                if (result == detailResult) {
                    if (requestId == detailRequestId) postDetailResult(absXml, requestId);
                } else {
                    result.postValue(absXml);
                }
            }
            return absXml;
        } catch (Throwable e) {
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
            if (json == null || json.isEmpty()) {
                postSearchResult(result, null, requestId, sourceKey);
                if (result != null && result != searchResult && result != quickSearchResult) {
                    if (result == detailResult) postDetailResult(null, requestId);
                    else result.postValue(null);
                }
                return null;
            }
            AbsXml absXml = null;
            AbsJson absJson = GSON.fromJson(json, ABS_JSON_TYPE);
            L.d(sourceKey + " parseJson length=" + json.length());
            if (absJson != null) {
                absXml = absJson.toAbsXml();
                parseSeriesUrl(absXml, sourceKey, searchResult == result);
                if (searchResult == result) {
                    addDuration(duration, absXml);
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, absXml, requestId, sourceKey));
                } else if (quickSearchResult == result) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, absXml, requestId, sourceKey));
                } else if (result != null) {
                    if (result == detailResult) {
                        if (requestId == detailRequestId) postDetailResult(absXml, requestId);
                    } else {
                        result.postValue(absXml);
                    }
                }
            } else {
                postSearchResult(result, null, requestId, sourceKey);
                if (result != null && result != searchResult && result != quickSearchResult) {
                    if (result == detailResult) postDetailResult(null, requestId);
                    else result.postValue(null);
                }
            }
            return absXml;
        } catch (Throwable e) {
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
        if (absXml == null || absXml.movie == null || absXml.movie.videoList == null) return;
        for (Movie.Video item : absXml.movie.videoList) {
            if (item != null && duration > 0) {
                item.dulation = duration;
            }
        }
    }
}
