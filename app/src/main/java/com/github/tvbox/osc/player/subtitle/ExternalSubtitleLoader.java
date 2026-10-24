package com.github.tvbox.osc.player.subtitle;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.lzy.okgo.OkGo;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** One cancellable external subtitle request; callbacks always run on the main thread. */
public final class ExternalSubtitleLoader {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client;
    private long generation;
    private boolean released;
    private Future<?> pending;
    private Call activeCall;
    private InputStream activeStream;

    public interface Callback {
        void onLoaded(ExternalSubtitle subtitle);
        void onError(String message);
    }

    public ExternalSubtitleLoader(Context context) {
        this.context = context.getApplicationContext();
        client = OkGo.getInstance().getOkHttpClient().newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS).build();
    }

    public synchronized void load(String uri, Map<String, String> headers, Callback callback) {
        if (callback == null) throw new IllegalArgumentException("callback == null");
        if (released) return;
        cancel();
        final long token = generation;
        Map<String, String> requestHeaders = headers == null ? new HashMap<>() : new HashMap<>(headers);
        pending = executor.submit(() -> {
            try {
                if (uri == null || uri.trim().isEmpty()) throw new IllegalArgumentException("未提供字幕地址");
                Uri parsed = Uri.parse(uri);
                String scheme = parsed.getScheme();
                byte[] bytes;
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    Request.Builder builder = new Request.Builder().url(uri);
                    Set<String> forwardedHeaders = new HashSet<>();
                    for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                        if (entry.getKey() == null || entry.getValue() == null) continue;
                        String key = entry.getKey().toLowerCase(Locale.ROOT);
                        // Video range and transport headers must not truncate the subtitle or override its host.
                        if (key.equals("host") || key.equals("connection") || key.equals("content-length")
                                || key.equals("transfer-encoding") || key.equals("range") || key.equals("if-range")
                                || key.equals("accept-encoding") || key.equals("upgrade")) continue;
                        builder.header(entry.getKey(), entry.getValue());
                        forwardedHeaders.add(key);
                    }
                    Request request = builder.build();
                    HttpUrl origin = request.url();
                    OkHttpClient requestClient = client.newBuilder().addNetworkInterceptor(chain -> {
                        Request next = chain.request();
                        if (!sameOrigin(origin, next.url())) {
                            Request.Builder safe = next.newBuilder();
                            for (String key : forwardedHeaders) {
                                // Keep browser compatibility headers; never replay site credentials to another origin.
                                if (!key.equals("user-agent") && !key.equals("accept")
                                        && !key.equals("accept-language") && !key.equals("referer")) {
                                    safe.removeHeader(key);
                                }
                            }
                            next = safe.build();
                        }
                        return chain.proceed(next);
                    }).build();
                    Call call = requestClient.newCall(request);
                    synchronized (this) {
                        if (!isCurrent(token)) return;
                        activeCall = call;
                    }
                    try (Response response = call.execute()) {
                        if (!response.isSuccessful()) throw new IOException("字幕下载失败（HTTP " + response.code() + "）");
                        ResponseBody body = response.body();
                        if (body == null) throw new IOException("字幕文件为空");
                        if (body.contentLength() > MAX_BYTES) throw new IOException("字幕文件超过 4 MB");
                        bytes = read(body.byteStream(), token);
                    }
                } else if ("content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme)) {
                    bytes = read(context.getContentResolver().openInputStream(parsed), token);
                } else if (scheme == null && uri.startsWith("/")) {
                    bytes = read(new FileInputStream(uri), token);
                } else {
                    throw new IllegalArgumentException("不支持此字幕地址，请选择本地文件或 HTTP 字幕");
                }
                ExternalSubtitle subtitle = ExternalSubtitle.parse(decode(bytes), parsed.getLastPathSegment());
                deliver(token, () -> callback.onLoaded(subtitle));
            } catch (Exception ex) {
                String detail = ex.getMessage();
                String message = ex instanceof IllegalArgumentException
                        ? detail != null && (detail.startsWith("字幕") || detail.startsWith("未")
                        || detail.startsWith("不支持") || detail.startsWith("ASS")) ? detail : "字幕地址或请求头无效"
                        : ex instanceof SecurityException ? "无法读取字幕，请重新选择文件授权"
                        : ex instanceof java.net.SocketTimeoutException ? "字幕下载超时，请重试"
                        : ex instanceof IOException && (ex.getMessage() != null &&
                        (ex.getMessage().startsWith("字幕") || ex.getMessage().startsWith("无法"))) ? ex.getMessage()
                        : "字幕加载失败，请检查文件或网络";
                deliver(token, () -> callback.onError(message == null ? "字幕加载失败" : message));
            } finally {
                synchronized (this) {
                    if (token == generation) {
                        activeCall = null;
                        activeStream = null;
                    }
                }
            }
        });
    }

    private static boolean sameOrigin(HttpUrl first, HttpUrl second) {
        return first.scheme().equals(second.scheme()) && first.host().equals(second.host())
                && first.port() == second.port();
    }

    private byte[] read(InputStream stream, long token) throws IOException {
        if (stream == null) throw new IOException("无法读取字幕文件");
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            synchronized (this) {
                if (!isCurrent(token)) throw new IOException("cancelled");
                activeStream = input;
            }
            byte[] buffer = new byte[8192];
            int size;
            while ((size = input.read(buffer)) != -1) {
                synchronized (this) {
                    if (!isCurrent(token) || Thread.currentThread().isInterrupted()) throw new IOException("cancelled");
                }
                if (output.size() + size > MAX_BYTES) throw new IOException("字幕文件超过 4 MB");
                output.write(buffer, 0, size);
            }
            return output.toByteArray();
        }
    }

    private static String decode(byte[] bytes) throws CharacterCodingException {
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            return Charset.forName("GB18030").newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    private synchronized boolean isCurrent(long token) {
        return !released && token == generation;
    }

    private void deliver(long token, Runnable callback) {
        main.post(() -> {
            synchronized (this) {
                if (!isCurrent(token)) return;
                callback.run();
            }
        });
    }

    public synchronized void cancel() {
        generation++;
        if (activeCall != null) activeCall.cancel();
        activeCall = null;
        if (pending != null) pending.cancel(true);
        pending = null;
        if (activeStream != null) {
            try { activeStream.close(); } catch (IOException ignored) { }
        }
        activeStream = null;
    }

    public synchronized void release() {
        cancel();
        released = true;
        executor.shutdownNow();
    }
}
