package com.github.tvbox.osc.api;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.util.MD5;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.MyFilesManager;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.model.Response;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

import static com.github.tvbox.osc.api.ApiConfig.UA_OKHTTP;

final class SpiderLoader {
    /** 站点级 jar：jar url → 本地加载后的 jar 路径（加载失败存空串） */
    private final ConcurrentHashMap<String, String> mSiteJarPaths = new ConcurrentHashMap<>();
    /** 站点级 jar 的按 url 下载锁：避免并发线程重复下载同一个包 */
    private final ConcurrentHashMap<String, Object> mSiteJarLocks = new ConcurrentHashMap<>();
    private final BaseLoader mBaseLoader = new BaseLoader();
    private Runnable pendingRefresh;
    private boolean refreshRequested;
    private final FutureTask<Void> globalJarTask;
    private final AtomicBoolean globalJarStarted = new AtomicBoolean();

    SpiderLoader(String spider) {
        globalJarTask = new FutureTask<>(() -> {
            if (!NullUtil.isNull(spider)) loadGlobalJar(spider);
            return null;
        });
    }

    private void ensureGlobalJar() {
        if (globalJarStarted.compareAndSet(false, true)) {
            new Thread(globalJarTask, "load-global-spider").start();
        }
        try {
            globalJarTask.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待爬虫 JAR 加载被中断", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("爬虫 JAR 加载失败", e.getCause());
        }
    }

    synchronized void refreshCachedJar() {
        refreshRequested = true;
        if (pendingRefresh == null) return;
        new Thread(pendingRefresh, "refresh-spider-cache").start();
        pendingRefresh = null;
    }

    synchronized void clear() {
        pendingRefresh = null;
        refreshRequested = false;
        mBaseLoader.clear();
        mSiteJarPaths.clear();
    }

    /**
     * 加载配置里的 spider jar（csp 爬虫包）
     * <p>
     * 原实现走 aar 的 Downloader(FileDownloader)，落盘路径取自 MyFilesManager.getLogPath()，
     * 即公共外部存储 /storage/emulated/0/<pkg>/log/，必须先拿到 WRITE_EXTERNAL_STORAGE；
     * 未授权时下载必然失败（IOException: Create parent directory failed），随后回退到内置
     * assets/tvbox.jar —— 而该内置包只有 2 个条目、不含任何 csp_* 站点类，于是配置里 45 个
     * csp_* 站点全都拿不到实现类，首页站点探测（ApiConfig.initHomeBean）无一成功，左栏只剩「首页」。
     * <p>
     * 现改为下载到应用私有目录 files/spider（不需要任何权限，电视端同样可用），
     * 在首次使用时由共享任务加载，并发调用等待同一次加载完成。
     */
    private void loadGlobalJar(String spider) {
        String[] urls = spider.split(";md5;");
        final String jarUrl = urls.length > 0 ? urls[0].trim() : "";
        final String md5 = urls.length > 1 ? urls[1].trim() : "";
        final Context ctx = App.getInstance();
        long startedAt = android.os.SystemClock.elapsedRealtime();
        boolean ok = false;
        File refreshTarget = null;
        try {
            if (!NullUtil.isNull(jarUrl)) {
                File jarDir = new File(ctx.getFilesDir().getAbsolutePath() + "/spider");
                if (jarDir.exists() || jarDir.mkdirs()) {
                    File jarFile = new File(jarDir, "csp_" + MD5.encode(jarUrl) + ".jar");
                    File pending = new File(jarFile.getAbsolutePath() + ".pending");
                    if (pending.exists()) {
                        if (isValidJar(pending, md5)) pending.renameTo(jarFile);
                        else pending.delete();
                    }
                    // 与原实现的 setForceReDownload(false) 一致：已下过就直接复用，不必每次启动重下
                    boolean cached = isValidJar(jarFile, md5);
                    if (cached || downloadJar(jarUrl, jarFile, md5)) {
                        ok = mBaseLoader.loadJar(jarFile.getAbsolutePath());
                        L.w("loadJar file=" + jarFile.getName() + " size=" + jarFile.length()
                                + " success=" + ok);
                        if (!ok) {
                            if (downloadJar(jarUrl, jarFile, md5)) {
                                ok = mBaseLoader.loadJar(jarFile.getAbsolutePath());
                            }
                        }
                        if (ok && cached && md5.isEmpty()) refreshTarget = pending;
                    }
                } else {
                    L.w("loadJar 创建目录失败 " + jarDir.getAbsolutePath());
                }
            }
        } catch (Throwable th) {
            L.w("loadJar 异常 " + th.getMessage());
        }
        if (!ok) {
            // 兜底：内置 assets/tvbox.jar（只有 2 个条目，不含 csp_* 站点类）
            MyFilesManager.copyFileFromAssets(App.getInstance(), "tvbox.jar",
                    App.getInstance().getFilesDir().getAbsolutePath(), "csp.jar");
            File cspFile = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp.jar");
            L.w("loadJar 内置兜底 csp.jar size=" + cspFile.length()
                    + " success=" + mBaseLoader.loadJar(cspFile.getAbsolutePath()));
        }
        // Stage updates for the next load, keeping the active JAR intact.
        if (refreshTarget != null) {
            File target = refreshTarget;
            synchronized (SpiderLoader.this) {
                pendingRefresh = () -> downloadJar(jarUrl, target);
                if (refreshRequested) refreshCachedJar();
            }
        }
        android.util.Log.i("Startup", "lazy spider ms="
                + (android.os.SystemClock.elapsedRealtime() - startedAt));
    }

    /**
     * 显式带 okhttp UA 下载 jar 到目标文件；先写 .tmp 再改名，避免中断留下半截文件
     */
    private static boolean downloadJar(String url, File dest) {
        return downloadJar(url, dest, "");
    }

    private static boolean isValidJar(File file, String md5) {
        if (!file.isFile() || file.length() == 0) return false;
        if (!md5.isEmpty() && !md5.equalsIgnoreCase(MD5.getFileMd5(file))) return false;
        try (ZipFile jar = new ZipFile(file)) {
            return jar.getEntry("classes.dex") != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean downloadJar(String url, File dest, String md5) {
        File tmp = null;
        okhttp3.Response response = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            tmp = File.createTempFile("spider_", ".tmp", dest.getParentFile());
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", UA_OKHTTP)
                    .build();
            response = OkGo.getInstance().getOkHttpClient().newCall(request).execute();
            if (!response.isSuccessful() || response.body() == null) {
                L.w("loadJar 下载失败 code=" + response.code() + " url=" + url);
                return false;
            }
            in = response.body().byteStream();
            out = new FileOutputStream(tmp);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
            out.close();
            out = null;
            in.close();
            in = null;
            if (!isValidJar(tmp, md5)) {
                return false;
            }
            boolean moved = tmp.renameTo(dest);
            L.w("loadJar 下载完成 size=" + dest.length() + " rename=" + moved);
            return moved;
        } catch (Throwable th) {
            L.w("loadJar 下载异常 " + th.getMessage());
            return false;
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
            if (response != null) response.close();
            if (tmp != null && tmp.exists()) tmp.delete();
        }
    }

    Spider getCSP(SiteBean sourceBean) {
        if (sourceBean.getApi() != null && sourceBean.getApi().startsWith("csp_")) ensureGlobalJar();
        //该源自带爬虫包时先把它准备好，否则类找不到会静默降级成 SpiderNull（表现为搜索结果为空）
        String jarPath = preloadSiteJar(sourceBean.getJar());
        return mBaseLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt(), jarPath);
    }

    /**
     * 下载并加载站点级 jar（配置里 site.jar 字段），返回本地 jar 路径。
     * 同一 url 只允许一个线程下载，其余线程在锁上等它完成——否则并发线程会在 jar 就绪前查类，
     * 全部落到"类找不到"的空结果上。结果按 url 缓存，失败缓存空串避免反复重试。
     */
    String preloadSiteJar(String jarWithMd5) {
        if (NullUtil.isNull(jarWithMd5)) return "";
        String url = jarWithMd5.split(";md5;")[0].trim();
        if (NullUtil.isNull(url)) return "";
        String cached = mSiteJarPaths.get(url);
        if (cached != null) return cached;
        Object lock = mSiteJarLocks.get(url);
        if (lock == null) {
            mSiteJarLocks.putIfAbsent(url, new Object());
            lock = mSiteJarLocks.get(url);
        }
        synchronized (lock) {
            cached = mSiteJarPaths.get(url);
            if (cached != null) return cached;
            String path = "";
            try {
                File jarDir = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/spider");
                if (jarDir.exists() || jarDir.mkdirs()) {
                    File jarFile = new File(jarDir, "csp_" + MD5.encode(url) + ".jar");
                    if ((jarFile.exists() && jarFile.length() > 0) || downloadJar(url, jarFile)) {
                        if (mBaseLoader.loadExtraJar(jarFile.getAbsolutePath())) {
                            path = jarFile.getAbsolutePath();
                            L.w("preloadSiteJar ok " + url + " size=" + jarFile.length());
                        } else {
                            L.w("preloadSiteJar 加载失败 " + url);
                        }
                    } else {
                        L.w("preloadSiteJar 下载失败 " + url);
                    }
                }
            } catch (Throwable th) {
                L.e(th);
            }
            mSiteJarPaths.put(url, path);
            return path;
        }
    }

    Object[] proxyLocal(Map param) {
        return mBaseLoader.proxy(param);
    }

    JSONObject jarExt(String key, LinkedHashMap<String, String> jxs, String url) {
        ensureGlobalJar();
        return mBaseLoader.jsonExt(key, jxs, url);
    }

    JSONObject jarExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        ensureGlobalJar();
        return mBaseLoader.jsonExtMix(flag, key, name, jxs, url);
    }

}
