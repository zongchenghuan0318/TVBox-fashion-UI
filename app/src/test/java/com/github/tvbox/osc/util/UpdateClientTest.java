package com.github.tvbox.osc.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

public class UpdateClientTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final byte[] APK = "example signed APK bytes".getBytes(StandardCharsets.UTF_8);

    private static String manifest(byte[] apk) {
        return "{\"versionCode\":114,\"versionName\":\"1.1.4\",\"changelog\":\"修复与改进\","
                + "\"apkUrl\":\"" + UpdateClient.REPOSITORY + "/releases/download/v1.1.4/Z.apk\","
                + "\"size\":" + apk.length + ",\"sha256\":\""
                + UpdateClient.hex(UpdateClient.sha256().digest(apk)) + "\"}";
    }

    private static UpdateClient client(Interceptor interceptor) {
        return new UpdateClient(new OkHttpClient.Builder().addInterceptor(interceptor).build());
    }

    private static Response response(Interceptor.Chain chain, byte[] body) {
        return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(ResponseBody.create(MediaType.parse("application/octet-stream"), body)).build();
    }

    @Test public void validatesManifestAndComparesNumericVersion() throws Exception {
        UpdateClient.Info info = UpdateClient.Info.parse(manifest(APK));
        assertTrue(info.isNewerThan(113));
        assertFalse(info.isNewerThan(114));
        assertFalse(info.isNewerThan(115));
        assertEquals("修复与改进", info.changelog);
        String[] invalid = {"<html>error</html>", "{}", "null",
                manifest(APK).replace("https://github.com/", "http://github.com/"),
                manifest(APK).replace("/v1.1.4/", "/share/"),
                manifest(APK).replace("\"size\":" + APK.length, "\"size\":0"),
                manifest(APK).replace("\"sha256\":\"", "\"sha256\":\"broken")};
        for (String json : invalid) {
            try { UpdateClient.Info.parse(json); fail("accepted invalid manifest"); }
            catch (IOException expected) { }
        }
    }

    @Test public void metadataFallsBackAcrossNetworkHtmlAndOrigin() throws Exception {
        List<String> urls = new ArrayList<>();
        UpdateClient client = client(chain -> {
            urls.add(chain.request().url().toString());
            assertNull(chain.request().header("Authorization"));
            assertEquals(TimeUnit.SECONDS.toNanos(8), chain.call().timeout().timeoutNanos());
            if (urls.size() == 1) throw new IOException("simulated timeout");
            if (urls.size() == 2) return response(chain, "<html>proxy error</html>".getBytes(StandardCharsets.UTF_8));
            return response(chain, manifest(APK).getBytes(StandardCharsets.UTF_8));
        });
        assertEquals(114, client.fetchInfo().versionCode);
        assertEquals("https://gh-proxy.com/" + UpdateClient.MANIFEST_URL, urls.get(0));
        assertEquals("https://ghfast.top/" + UpdateClient.MANIFEST_URL, urls.get(1));
        assertEquals(UpdateClient.MANIFEST_URL, urls.get(2));
    }

    @Test public void metadataFailureAndOversizedResponseTerminateAfterThreeRoutes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        UpdateClient client = client(chain -> {
            calls.incrementAndGet();
            return response(chain, new byte[262145]);
        });
        try { client.fetchInfo(); fail(); } catch (IOException expected) { }
        assertEquals(3, calls.get());
    }

    @Test public void corruptAndTruncatedDownloadsFallBackAndOnlyKeepVerifiedFile() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger verified = new AtomicInteger();
        List<Integer> routes = new ArrayList<>();
        UpdateClient client = client(chain -> {
            assertEquals(10000, chain.connectTimeoutMillis());
            assertEquals(20000, chain.readTimeoutMillis());
            assertEquals(0, chain.call().timeout().timeoutNanos());
            int attempt = calls.incrementAndGet();
            return response(chain, attempt == 1 ? new byte[APK.length] : attempt == 2 ? new byte[2] : APK);
        });
        File directory = temp.newFolder();
        File file = client.download(UpdateClient.Info.parse(manifest(APK)), directory,
                (received, total, route) -> routes.add(route), apk -> {
                    assertArrayEquals(APK, Files.readAllBytes(apk.toPath()));
                    verified.incrementAndGet();
                });
        assertTrue(file.isFile());
        assertEquals(3, calls.get());
        assertEquals(1, verified.get());
        assertEquals(1, directory.list().length);
        assertTrue(routes.contains(0) && routes.contains(1) && routes.contains(2));
    }

    @Test public void packageRejectionNeverReturnsInstallableFile() throws Exception {
        AtomicInteger verified = new AtomicInteger();
        UpdateClient client = client(chain -> response(chain, APK));
        File directory = temp.newFolder();
        try {
            client.download(UpdateClient.Info.parse(manifest(APK)), directory, (a, b, c) -> {}, apk -> {
                verified.incrementAndGet();
                throw new IOException("安装包签名不符");
            });
            fail();
        } catch (IOException expected) { assertEquals("安装包签名不符", expected.getMessage()); }
        assertEquals(3, verified.get());
        assertEquals(0, directory.list().length);
    }

    @Test public void cancellationDuringDownloadStopsFallbackAndDeletesPartialFile() throws Exception {
        byte[] bytes = new byte[100000];
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger verified = new AtomicInteger();
        UpdateClient client = client(chain -> { calls.incrementAndGet(); return response(chain, bytes); });
        File directory = temp.newFolder();
        try {
            client.download(UpdateClient.Info.parse(manifest(bytes)), directory, (received, total, route) -> {
                if (received > 0) client.cancel();
            }, file -> verified.incrementAndGet());
            fail();
        } catch (IOException expected) { assertEquals("已取消更新", expected.getMessage()); }
        assertEquals(1, calls.get());
        assertEquals(0, verified.get());
        assertEquals(0, directory.list().length);
    }

    @Test public void cancelBeforeCheckDoesNotStartNetwork() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        UpdateClient client = client(chain -> { calls.incrementAndGet(); return response(chain, APK); });
        client.cancel();
        try { client.fetchInfo(); fail(); } catch (IOException expected) { }
        assertEquals(0, calls.get());
    }
}
