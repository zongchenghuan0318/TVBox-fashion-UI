package com.github.tvbox.osc.api;

import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.util.MD5;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class StartupCacheTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    private JsonObject config(String group) {
        return JsonParser.parseString("{\"lives\":[{\"group\":\"" + group
                + "\",\"channels\":[{\"name\":\"Channel\",\"urls\":[\"https://example.com/live\"]}]}]}")
                .getAsJsonObject();
    }

    @Test public void liveChannelsAreDeferredUntilFirstUse() throws Exception {
        LiveSourceConfig live = new LiveSourceConfig();
        live.deferConfiguredLives("https://example.com/config", config("First"));
        Field groups = LiveSourceConfig.class.getDeclaredField("mLiveChannelGroupList");
        groups.setAccessible(true);
        assertTrue(((List<?>) groups.get(live)).isEmpty());
        assertEquals("First", live.getChannelGroupList().get(0).getGroupName());
        assertEquals(1, live.getChannelGroupList().size());
    }

    @Test public void switchingConfigDiscardsPreviouslyLoadedChannels() {
        LiveSourceConfig live = new LiveSourceConfig();
        live.deferConfiguredLives("https://example.com/one", config("First"));
        live.getChannelGroupList();
        live.deferConfiguredLives("https://example.com/two", config("Second"));
        live.restoreConfiguredLives();
        assertEquals(1, live.getChannelGroupList().size());
        assertEquals("Second", live.getChannelGroupList().get(0).getGroupName());
    }

    @Test public void customLiveChannelsCanRestoreDeferredConfig() {
        LiveSourceConfig live = new LiveSourceConfig();
        live.deferConfiguredLives("https://example.com/config", config("Configured"));
        live.loadLives(config("Custom").getAsJsonArray("lives"));
        assertEquals("Custom", live.getChannelGroupList().get(0).getGroupName());
        live.restoreConfiguredLives();
        List<LiveChannelGroup> groups = live.getChannelGroupList();
        assertEquals("Configured", groups.get(0).getGroupName());
        assertEquals(1, groups.get(0).getLiveChannels().size());
    }

    @Test public void emptyConfigClearsPreviousChannels() {
        LiveSourceConfig live = new LiveSourceConfig();
        live.deferConfiguredLives("https://example.com/one", config("First"));
        live.getChannelGroupList();
        live.deferConfiguredLives("https://example.com/two", JsonParser.parseString("{\"lives\":[]}").getAsJsonObject());
        assertTrue(live.getChannelGroupList().isEmpty());
    }

    private File jar(String entry) throws Exception {
        File file = files.newFile();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            out.putNextEntry(new ZipEntry(entry));
            out.write(new byte[] {1, 2, 3});
            out.closeEntry();
        }
        return file;
    }

    private boolean valid(File jar, String md5) throws Exception {
        Method method = SpiderLoader.class.getDeclaredMethod("isValidJar", File.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, jar, md5);
    }

    @Test public void cachedJarRequiresDexAndMatchingConfiguredChecksum() throws Exception {
        File jar = jar("classes.dex");
        assertTrue(valid(jar, ""));
        assertTrue(valid(jar, MD5.getFileMd5(jar).toUpperCase(java.util.Locale.ROOT)));
        assertFalse(valid(jar, "00000000000000000000000000000000"));
        assertFalse(valid(jar("index.html"), ""));
    }

    @Test public void partialAndMissingDownloadsAreRejected() throws Exception {
        File partial = files.newFile();
        try (FileOutputStream out = new FileOutputStream(partial)) {
            out.write("<html>download failed</html>".getBytes("UTF-8"));
        }
        assertFalse(valid(partial, ""));
        assertFalse(valid(files.newFile(), ""));
        assertFalse(valid(new File(files.getRoot(), "missing.jar"), ""));
    }
}
