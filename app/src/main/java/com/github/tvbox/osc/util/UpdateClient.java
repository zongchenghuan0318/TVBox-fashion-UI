package com.github.tvbox.osc.util;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Public release traffic only. Never uses the playback client's relaxed TLS settings. */
public final class UpdateClient {
    public static final String REPOSITORY = "https://github.com/zongchenghuan0318/TVBox-fashion-UI";
    public static final String MANIFEST_URL = REPOSITORY + "/releases/latest/download/update.json";
    private static final String[] PREFIXES = {"https://gh-proxy.com/", "https://ghfast.top/", ""};
    private final OkHttpClient metadata;
    private final OkHttpClient download;
    private volatile boolean cancelled;
    private Call currentCall;

    public static final class Info {
        public int versionCode;
        public String versionName;
        public String changelog;
        public String apkUrl;
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
                        || !(REPOSITORY + "/releases/download/v" + info.versionName + "/Z.apk").equals(info.apkUrl)) {
                    throw new IllegalArgumentException();
                }
                return info;
            } catch (RuntimeException e) {
                throw new IOException("更新信息格式不正确", e);
            }
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
        for (String prefix : PREFIXES) {
            checkCancelled();
            try (Response response = request(metadata, prefix + MANIFEST_URL);
                 InputStream input = response.body().byteStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled();
                    if (output.size() + count > 262144) throw new IOException("更新信息过大");
                    output.write(buffer, 0, count);
                }
                return Info.parse(output.toString("UTF-8"));
            } catch (IOException e) { last = e; }
        }
        checkCancelled();
        throw new IOException("无法检查更新，请检查网络后重试", last);
    }

    public File download(Info info, File directory, Progress progress, PackageVerifier verifier) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建下载目录");
        IOException last = null;
        for (int route = 0; route < PREFIXES.length; route++) {
            checkCancelled();
            File file = File.createTempFile("update-", ".apk", directory);
            boolean verified = false;
            try {
                MessageDigest digest = sha256();
                long received = 0;
                long lastProgress = 0;
                progress.update(0, info.size, route);
                try (Response response = request(download, PREFIXES[route] + info.apkUrl);
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
