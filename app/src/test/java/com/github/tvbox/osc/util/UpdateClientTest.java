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

    /** 旧版清单：只有 GitHub 地址，用于验证向后兼容。 */
    private static String githubManifest(byte[] apk) {
        return "{\"versionCode\":114,\"versionName\":\"1.1.4\",\"changelog\":\"修复与改进\","
                + "\"apkUrl\":\"" + UpdateClient.GITHUB_REPOSITORY + "/releases/download/v1.1.4/Z.apk\","
                + "\"size\":" + apk.length + ",\"sha256\":\""
                + UpdateClient.hex(UpdateClient.sha256().digest(apk)) + "\"}";
    }

    /** 新版清单：Gitee 主地址 + GitHub 备用地址。 */
    private static String giteeManifest(byte[] apk) {
        return "{\"versionCode\":114,\"versionName\":\"1.1.4\",\"changelog\":\"修复与改进\","
                + "\"apkUrl\":\"" + UpdateClient.GITEE_REPOSITORY + "/releases/download/v1.1.4/Z.apk\","
                + "\"apkUrlFallback\":\"" + UpdateClient.GITHUB_REPOSITORY + "/releases/download/v1.1.4/Z.apk\","
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
        UpdateClient.Info info = UpdateClient.Info.parse(giteeManifest(APK));
        assertTrue(info.isNewerThan(113));
        assertFalse(info.isNewerThan(114));
        assertFalse(info.isNewerThan(115));
        assertEquals("修复与改进", info.changelog);
        assertEquals(114, UpdateClient.Info.parse(githubManifest(APK)).versionCode);
        String[] invalid = {"<html>error</html>", "{}", "null",
                giteeManifest(APK).replace("https://gitee.com/", "http://gitee.com/"),
                giteeManifest(APK).replace("/v1.1.4/", "/share/"),
                giteeManifest(APK).replace("\"size\":" + APK.length, "\"size\":0"),
                giteeManifest(APK).replace("\"sha256\":\"", "\"sha256\":\"broken"),
                giteeManifest(APK).replace(UpdateClient.GITHUB_REPOSITORY, "https://example.com")};
        for (String json : invalid) {
            try { UpdateClient.Info.parse(json); fail("accepted invalid manifest"); }
            catch (IOException expected) { }
        }
    }

    @Test public void metadataFallsBackAcrossGiteeNetworkHtmlAndGithub() throws Exception {
        List<String> urls = new ArrayList<>();
        UpdateClient client = client(chain -> {
            urls.add(chain.request().url().toString());
            assertNull(chain.request().header("Authorization"));
            assertEquals(TimeUnit.SECONDS.toNanos(8), chain.call().timeout().timeoutNanos());
            if (urls.size() == 1) throw new IOException("simulated timeout");
            if (urls.size() == 2) return response(chain, "<html>proxy error</html>".getBytes(StandardCharsets.UTF_8));
            return response(chain, giteeManifest(APK).getBytes(StandardCharsets.UTF_8));
        });
        assertEquals(114, client.fetchInfo().versionCode);
        assertEquals(UpdateClient.GITEE_MANIFEST_URL, urls.get(0));
        assertEquals("https://gh-proxy.com/" + UpdateClient.GITHUB_MANIFEST_URL, urls.get(1));
        assertEquals("https://ghfast.top/" + UpdateClient.GITHUB_MANIFEST_URL, urls.get(2));
    }

    @Test public void manifestWithUtf8BomIsAccepted() throws Exception {
        UpdateClient client = client(chain -> response(chain,
                ("\uFEFF" + giteeManifest(APK)).getBytes(StandardCharsets.UTF_8)));
        assertEquals(114, client.fetchInfo().versionCode);
    }

    @Test public void metadataFailureAndOversizedResponseTerminateAfterFourRoutes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        UpdateClient client = client(chain -> {
            calls.incrementAndGet();
            return response(chain, new byte[262145]);
        });
        try { client.fetchInfo(); fail(); } catch (IOException expected) { }
        assertEquals(4, calls.get());
    }

    @Test public void giteeIsPreferredAndGithubIsTheFallback() throws Exception {
        List<String> urls = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        UpdateClient client = client(chain -> {
            urls.add(chain.request().url().toString());
            return response(chain, calls.incrementAndGet() == 1 ? new byte[APK.length] : APK);
        });
        File directory = temp.newFolder();
        File file = client.download(UpdateClient.Info.parse(giteeManifest(APK)), directory,
                (received, total, route) -> { }, apk -> { });
        assertTrue(file.isFile());
        assertEquals(UpdateClient.GITEE_REPOSITORY + "/releases/download/v1.1.4/Z.apk", urls.get(0));
        assertEquals("https://gh-proxy.com/" + UpdateClient.GITHUB_REPOSITORY + "/releases/download/v1.1.4/Z.apk", urls.get(1));
    }

    @Test public void legacyGithubManifestStillUsesThreeMirrorRoutes() throws Exception {
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
        File file = client.download(UpdateClient.Info.parse(githubManifest(APK)), directory,
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
            client.download(UpdateClient.Info.parse(giteeManifest(APK)), directory, (a, b, c) -> {}, apk -> {
                verified.incrementAndGet();
                throw new IOException("安装包签名不符");
            });
            fail();
        } catch (IOException expected) { assertEquals("安装包签名不符", expected.getMessage()); }
        assertEquals(4, verified.get());
        assertEquals(0, directory.list().length);
    }

    @Test public void cancellationDuringDownloadStopsFallbackAndDeletesPartialFile() throws Exception {
        byte[] bytes = new byte[100000];
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger verified = new AtomicInteger();
        UpdateClient client = client(chain -> { calls.incrementAndGet(); return response(chain, bytes); });
        File directory = temp.newFolder();
        try {
            client.download(UpdateClient.Info.parse(githubManifest(bytes)), directory, (received, total, route) -> {
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
