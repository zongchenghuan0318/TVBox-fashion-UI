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

    /** GitHub 附件里的清单：主地址必须是 GitHub，旧客户端（1.1.19 及更早）只认这种写法。 */
    private static String githubPrimaryManifest(byte[] apk) {
        return "{\"versionCode\":114,\"versionName\":\"1.1.4\",\"changelog\":\"修复与改进\","
                + "\"apkUrl\":\"" + UpdateClient.GITHUB_REPOSITORY + "/releases/download/v1.1.4/Z.apk\","
                + "\"apkUrlFallback\":\"" + UpdateClient.GITEE_REPOSITORY + "/releases/download/v1.1.4/Z.apk\","
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
        assertEquals(UpdateClient.GITEE_LATEST_RELEASE_URL, urls.get(0));
        assertEquals("https://gh-proxy.com/" + UpdateClient.GITHUB_MANIFEST_URL, urls.get(1));
        assertEquals("https://ghfast.top/" + UpdateClient.GITHUB_MANIFEST_URL, urls.get(2));
    }

    @Test public void manifestWithUtf8BomIsAccepted() throws Exception {
        UpdateClient client = client(chain -> response(chain,
                ("\uFEFF" + giteeManifest(APK)).getBytes(StandardCharsets.UTF_8)));
        assertEquals(114, client.fetchInfo().versionCode);
    }

    @Test public void giteeMetadataUsesOnlyReleaseApiAndAttachment() throws Exception {
        List<String> urls = new ArrayList<>();
        UpdateClient client = client(chain -> {
            String url = chain.request().url().toString();
            urls.add(url);
            assertNull(chain.request().header("Authorization"));
            String body = url.equals(UpdateClient.GITEE_LATEST_RELEASE_URL)
                    ? "{\"tag_name\":\"v1.1.4\",\"prerelease\":false}"
                    : "\uFEFF" + giteeManifest(APK);
            return response(chain, body.getBytes(StandardCharsets.UTF_8));
        });
        assertEquals(114, client.fetchInfo().versionCode);
        assertEquals(2, urls.size());
        assertEquals(UpdateClient.GITEE_LATEST_RELEASE_URL, urls.get(0));
        assertEquals(UpdateClient.GITEE_REPOSITORY + "/releases/download/v1.1.4/update.json", urls.get(1));
    }

    @Test public void invalidOrIncompleteGiteeReleaseFallsBackToGithub() throws Exception {
        String[] releases = {"null", "{}", "<html>error</html>",
                "{\"tag_name\":\"../../raw/main\"}",
                "{\"tag_name\":\"v1.1.4\",\"prerelease\":true}",
                "{\"tag_name\":\"v1.1.5\"}",
                "{\"tag_name\":\"v1.1.4\"}"};
        for (String release : releases) {
            List<String> urls = new ArrayList<>();
            UpdateClient client = client(chain -> {
                String url = chain.request().url().toString();
                urls.add(url);
                if (url.equals(UpdateClient.GITEE_LATEST_RELEASE_URL)) {
                    return response(chain, release.getBytes(StandardCharsets.UTF_8));
                }
                if (url.startsWith(UpdateClient.GITEE_REPOSITORY)) {
                    if (release.contains("v1.1.5")) return response(chain, giteeManifest(APK).getBytes(StandardCharsets.UTF_8));
                    throw new IOException("attachment missing");
                }
                return response(chain, githubManifest(APK).getBytes(StandardCharsets.UTF_8));
            });
            assertEquals(114, client.fetchInfo().versionCode);
            assertEquals("https://gh-proxy.com/" + UpdateClient.GITHUB_MANIFEST_URL, urls.get(urls.size() - 1));
            for (String url : urls) assertFalse(url.contains("/raw/"));
        }
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

    @Test public void githubPrimaryManifestKeepsGiteeAsLastResort() throws Exception {
        List<String> urls = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        UpdateClient client = client(chain -> {
            urls.add(chain.request().url().toString());
            return response(chain, calls.incrementAndGet() <= 3 ? new byte[APK.length] : APK);
        });
        File directory = temp.newFolder();
        File file = client.download(UpdateClient.Info.parse(githubPrimaryManifest(APK)), directory,
                (received, total, route) -> { }, apk -> { });
        assertTrue(file.isFile());
        String github = UpdateClient.GITHUB_REPOSITORY + "/releases/download/v1.1.4/Z.apk";
        assertEquals("https://gh-proxy.com/" + github, urls.get(0));
        assertEquals("https://ghfast.top/" + github, urls.get(1));
        assertEquals(github, urls.get(2));
        assertEquals(UpdateClient.GITEE_REPOSITORY + "/releases/download/v1.1.4/Z.apk", urls.get(3));
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
