package com.github.tvbox.osc.util;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Public release traffic only. Never uses the playback client's relaxed TLS settings. */
public final class UpdateClient {
    /** 主更新源：Gitee 仓库里的静态清单，国内可直连。 */
    public static final String GITEE_REPOSITORY = "https://gitee.com/the-eternal-cycle/TVBox-fashion-UI";
    public static final String GITEE_MANIFEST_URL = GITEE_REPOSITORY + "/raw/main/release/update.json";
    /** 备用更新源：GitHub 发行版，沿用原有三条加速线路。 */
    public static final String GITHUB_REPOSITORY = "https://github.com/zongchenghuan0318/TVBox-fashion-UI";
    public static final String GITHUB_MANIFEST_URL = GITHUB_REPOSITORY + "/releases/latest/download/update.json";
    private static final String[] GITHUB_PREFIXES = {"https://gh-proxy.com/", "https://ghfast.top/", ""};
    private final OkHttpClient metadata;
    private final OkHttpClient download;
    private volatile boolean cancelled;
    private Call currentCall;

    public static final class Info {
        public int versionCode;
        public String versionName;
        public String changelog;
        public String apkUrl;
        /** 备用安装包地址；旧版清单没有该字段。 */
        public String apkUrlFallback;
        public String sha256;
        public long size;

        public boolean isNewerThan(long installedVersion) { return versionCode > installedVersion; }

        public static Info parse(String json) throws IOException {
            try {
                Info info = new Gson().fromJson(json, Info.class);
                if (info == null || info.versionCode <= 0 || info.versionName == null
                        || !info.versionName.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                        || info.changelog == null || info.size <= 0 || info.sha256 == null
                        || !info.sha256.matches("(?i)[0-9a-f]{64}")
                        || !isReleaseApk(info.apkUrl, info.versionName)
                        || (info.apkUrlFallback != null && !info.apkUrlFallback.isEmpty()
                        && !isGithubApk(info.apkUrlFallback, info.versionName))) {
                    throw new IllegalArgumentException();
                }
                return info;
            } catch (RuntimeException e) {
                throw new IOException("更新信息格式不正确", e);
            }
        }

        /** 主地址可以是 Gitee 或 GitHub 的对应版本发行包，兼容只含 GitHub 地址的旧清单。 */
        private static boolean isReleaseApk(String url, String versionName) {
            return isGiteeApk(url, versionName) || isGithubApk(url, versionName);
        }

        private static boolean isGiteeApk(String url, String versionName) {
            return (GITEE_REPOSITORY + "/releases/download/v" + versionName + "/Z.apk").equals(url);
        }

        private static boolean isGithubApk(String url, String versionName) {
            return (GITHUB_REPOSITORY + "/releases/download/v" + versionName + "/Z.apk").equals(url);
        }
    }

    public interface Progress { void update(long received, long total, int route); }
    public interface PackageVerifier { void verify(File apk) throws Exception; }

    public UpdateClient() { this(new OkHttpClient()); }

    UpdateClient(OkHttpClient base) {
        metadata = base.newBuilder().connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS)
                .followSslRedirects(false).retryOnConnectionFailure(false).build();
        download = base.newBuilder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS)
                .followSslRedirects(false).retryOnConnectionFailure(false).build();
    }

    public synchronized void cancel() {
        cancelled = true;
        if (currentCall != null) currentCall.cancel();
    }

    private void checkCancelled() throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("已取消更新");
    }

    private Response request(OkHttpClient client, String url) throws IOException {
        Call call;
        synchronized (this) {
            checkCancelled();
            call = client.newCall(new Request.Builder().url(url)
                    .header("User-Agent", "ZMovie-Updater")
                    .header("Cache-Control", "no-cache").build());
            currentCall = call;
        }
        Response response = call.execute();
        if (!response.isSuccessful() || response.body() == null) {
            int code = response.code();
            response.close();
            throw new IOException("下载服务返回 HTTP " + code);
        }
        return response;
    }

    public Info fetchInfo() throws IOException {
        IOException last = null;
        try {
            return readManifest(GITEE_MANIFEST_URL);
        } catch (IOException e) { last = e; }
        for (String prefix : GITHUB_PREFIXES) {
            checkCancelled();
            try {
                return readManifest(prefix + GITHUB_MANIFEST_URL);
            } catch (IOException e) { last = e; }
        }
        checkCancelled();
        throw new IOException("无法检查更新，请检查网络后重试", last);
    }

    private Info readManifest(String url) throws IOException {
        checkCancelled();
        try (Response response = request(metadata, url);
             InputStream input = response.body().byteStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkCancelled();
                if (output.size() + count > 262144) throw new IOException("更新信息过大");
                output.write(buffer, 0, count);
            }
            String json = output.toString("UTF-8");
            // 清单可能带 UTF-8 BOM，去掉后再解析，避免个别 JSON 解析器报格式错误。
            if (!json.isEmpty() && json.charAt(0) == '\uFEFF') json = json.substring(1);
            return Info.parse(json);
        }
    }

    /** Gitee 优先；清单带备用地址时再依次尝试 GitHub 的三条线路，旧清单则只走 GitHub。 */
    private static List<String> downloadRoutes(Info info) {
        List<String> routes = new ArrayList<>();
        boolean hasFallback = info.apkUrlFallback != null && !info.apkUrlFallback.isEmpty();
        if (hasFallback) routes.add(info.apkUrl);
        String github = hasFallback ? info.apkUrlFallback : info.apkUrl;
        for (String prefix : GITHUB_PREFIXES) routes.add(prefix + github);
        return routes;
    }

    public File download(Info info, File directory, Progress progress, PackageVerifier verifier) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建下载目录");
        List<String> routes = downloadRoutes(info);
        IOException last = null;
        for (int route = 0; route < routes.size(); route++) {
            checkCancelled();
            File file = File.createTempFile("update-", ".apk", directory);
            boolean verified = false;
            try {
                MessageDigest digest = sha256();
                long received = 0;
                long lastProgress = 0;
                progress.update(0, info.size, route);
                try (Response response = request(download, routes.get(route));
                     InputStream input = response.body().byteStream();
                     FileOutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[32768];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        checkCancelled();
                        received += count;
                        if (received > info.size) throw new IOException("安装包大小不符");
                        digest.update(buffer, 0, count);
                        output.write(buffer, 0, count);
                        long now = System.nanoTime();
                        if (now - lastProgress >= TimeUnit.MILLISECONDS.toNanos(250)) {
                            progress.update(received, info.size, route);
                            lastProgress = now;
                        }
                    }
                }
                checkCancelled();
                if (received != info.size) throw new IOException("安装包下载不完整");
                if (!hex(digest.digest()).equalsIgnoreCase(info.sha256)) throw new IOException("安装包校验失败");
                verifier.verify(file);
                checkCancelled();
                progress.update(received, info.size, route);
                verified = true;
                return file;
            } catch (Exception e) {
                last = e instanceof IOException ? (IOException) e : new IOException("安装包验证失败", e);
            } finally {
                if (!verified) file.delete();
            }
        }
        checkCancelled();
        throw new IOException(last == null ? "下载失败，请重试" : last.getMessage(), last);
    }

    public static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    public static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
