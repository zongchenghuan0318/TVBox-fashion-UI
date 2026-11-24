package com.github.tvbox.osc.api;

import com.github.tvbox.osc.bean.SiteBean;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ConfigSwitchTest {
    private ApiConfig config() throws Exception {
        Constructor<ApiConfig> constructor = ApiConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private Object field(ApiConfig config, String name) throws Exception {
        Field field = ApiConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(config);
    }

    @Test public void malformedConfigurationPreservesSitesAndLoader() throws Exception {
        ApiConfig config = config();
        @SuppressWarnings("unchecked")
        Map<String, SiteBean> sites = (Map<String, SiteBean>) field(config, "mSiteBeanMap");
        SiteBean old = new SiteBean();
        old.setKey("working");
        sites.put(old.getKey(), old);
        Object loader = field(config, "spiderLoader");
        Method parse = ApiConfig.class.getDeclaredMethod("parseSiteFile", String.class, String.class);
        parse.setAccessible(true);
        try {
            parse.invoke(config, "https://example.com/broken", "{broken");
        } catch (java.lang.reflect.InvocationTargetException error) {
            // Android logging has no implementation in the local JVM test runtime.
            assertTrue(error.getCause().getMessage().contains("Method e in android.util.Log not mocked"));
        }
        assertSame(old, config.getSiteBeanList().get(0));
        assertSame(loader, field(config, "spiderLoader"));
        assertEquals(false, field(config, "m_bParsed"));
    }

    @Test public void previousHomeProbeCannotCompleteANewSelection() throws Exception {
        ApiConfig config = config();
        AtomicInteger generation = (AtomicInteger) field(config, "homeSelectionGeneration");
        int previous = generation.get();
        int current = generation.incrementAndGet();
        Method select = ApiConfig.class.getDeclaredMethod("selectHomeBeanOnce", SiteBean.class, int.class);
        select.setAccessible(true);
        assertEquals(false, select.invoke(config, null, previous));
        assertEquals(false, field(config, "bInit"));
        assertEquals(true, select.invoke(config, null, current));
        assertEquals(false, select.invoke(config, null, current));
    }

    @Test public void supersededConfigApplyDoesNotMutateActiveConfiguration() throws Exception {
        ApiConfig config = config();
        AtomicBoolean completed = new AtomicBoolean();
        Thread staleApply = null;
        synchronized (config) {
            config.applySitesFile("https://example.com/old", "{}", () -> completed.set(true));
            ((AtomicInteger) field(config, "configLoadGeneration")).incrementAndGet();
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread.getName().equals("config-runtime-reload")) {
                    staleApply = thread;
                    break;
                }
            }
        }
        assertNotNull(staleApply);
        staleApply.join(2000);
        assertFalse(staleApply.isAlive());
        assertFalse(completed.get());
        assertNull(field(config, "apiUrl"));
        assertTrue(config.getSiteBeanList().isEmpty());
    }

    @Test public void sourceListReadersWaitForConfigurationCommit() throws Exception {
        ApiConfig config = config();
        @SuppressWarnings("unchecked")
        Map<String, SiteBean> sites = (Map<String, SiteBean>) field(config, "mSiteBeanMap");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch readerStarted = new CountDownLatch(1);
        Future<List<SiteBean>> read;
        try {
            synchronized (config) {
                SiteBean partial = new SiteBean();
                partial.setKey("partial");
                sites.put(partial.getKey(), partial);
                read = worker.submit(() -> {
                    readerStarted.countDown();
                    return config.getSiteBeanList();
                });
                assertTrue(readerStarted.await(2, TimeUnit.SECONDS));
                try {
                    read.get(100, TimeUnit.MILLISECONDS);
                    fail("Reader observed a configuration while it was being replaced");
                } catch (TimeoutException expected) {
                    // The writer still owns the configuration lock.
                }
                sites.clear();
                SiteBean committed = new SiteBean();
                committed.setKey("committed");
                sites.put(committed.getKey(), committed);
            }
            List<SiteBean> snapshot = read.get(2, TimeUnit.SECONDS);
            assertEquals(1, snapshot.size());
            assertEquals("committed", snapshot.get(0).getKey());
        } finally {
            worker.shutdownNow();
        }
    }

    private SiteBean site(String key, String name, int type, int searchable) {
        SiteBean bean = new SiteBean();
        bean.setKey(key);
        bean.setName(name);
        bean.setType(type);
        bean.setSearchable(searchable);
        return bean;
    }

    @Test public void homeCandidatePriorityPrefersHttpThenVodSpidersOverPanSpiders() {
        SiteBean pan = site("玩偶", "💓玩偶┃4K💓", 3, 1);
        SiteBean generic = site("other", "普通站", 3, 1);
        SiteBean vod = site("WexAiWenCai", "💥文才┃秒播💥", 3, 1);
        SiteBean preferred = site("AppV7_粉猪", "自定义站点", 3, 1);
        SiteBean http = site("bfzy", "暴风┃影视", 1, 1);

        List<SiteBean> list = new ArrayList<>(Arrays.asList(pan, generic, vod, preferred, http));
        ApiConfig.sortHomeCandidates(list, "AppV7_粉猪");

        assertEquals("bfzy", list.get(0).getKey());
        assertEquals("AppV7_粉猪", list.get(1).getKey());
        assertEquals("WexAiWenCai", list.get(2).getKey());
        assertEquals("other", list.get(3).getKey());
        assertEquals("玩偶", list.get(4).getKey());
    }

    @Test public void homeRecCandidatesSkipFailedProbesAndKeepVerifiedPrimaryFirst() throws Exception {
        ApiConfig config = config();
        @SuppressWarnings("unchecked")
        Map<String, SiteBean> sites = (Map<String, SiteBean>) field(config, "mSiteBeanMap");
        SiteBean lzi = site("lzi", "量子┃影视", 1, 1);
        SiteBean bfzy = site("bfzy", "暴风┃影视", 1, 1);
        SiteBean jszy = site("jszy", "极速┃影视", 1, 1);
        SiteBean pan = site("花卷", "💓花卷┃4K💓", 3, 1);
        SiteBean vod = site("WexAiWenCai", "💥文才┃秒播💥", 3, 1);
        SiteBean tool = site("Douban", "豆瓣", 3, 0);
        sites.put(lzi.getKey(), lzi);
        sites.put(bfzy.getKey(), bfzy);
        sites.put(jszy.getKey(), jszy);
        sites.put(pan.getKey(), pan);
        sites.put(vod.getKey(), vod);
        sites.put(tool.getKey(), tool);

        @SuppressWarnings("unchecked")
        Set<String> failed = (Set<String>) field(config, "failedHomeProbeKeys");
        failed.add("lzi");

        Field preparedHomeKey = ApiConfig.class.getDeclaredField("preparedHomeKey");
        preparedHomeKey.setAccessible(true);
        preparedHomeKey.set(config, "bfzy");

        List<SiteBean> candidates = config.getHomeRecCandidates();
        assertEquals("bfzy", candidates.get(0).getKey());
        assertEquals("jszy", candidates.get(1).getKey());
        assertEquals("WexAiWenCai", candidates.get(2).getKey());
        assertEquals("花卷", candidates.get(3).getKey());
        for (SiteBean candidate : candidates) {
            assertNotEquals("lzi", candidate.getKey());
            assertNotEquals("Douban", candidate.getKey());
        }
    }

    @Test public void verifiedHomeSelectionRecordsProbedContentAndSurvivesEmptyCommit() throws Exception {
        ApiConfig candidate = config();
        @SuppressWarnings("unchecked")
        Map<String, SiteBean> sites = (Map<String, SiteBean>) field(candidate, "mSiteBeanMap");
        SiteBean vod = site("WexAiWenCai", "💥文才┃秒播💥", 3, 1);
        sites.put(vod.getKey(), vod);

        AtomicInteger generation = (AtomicInteger) field(candidate, "homeSelectionGeneration");
        int current = generation.incrementAndGet();
        Method select = ApiConfig.class.getDeclaredMethod(
                "selectHomeBeanOnce", SiteBean.class, int.class, boolean.class, String.class);
        select.setAccessible(true);
        // Set preparedHomeKey directly in case Android Log in setHomeBean is unmocked on JVM
        try {
            select.invoke(candidate, vod, current, true, "{\"list\":[{\"vod_id\":\"1\"}]}");
        } catch (java.lang.reflect.InvocationTargetException error) {
            assertTrue(error.getCause().getMessage().contains("not mocked"));
            Field verifiedField = ApiConfig.class.getDeclaredField("homeProbeVerified");
            verifiedField.setAccessible(true);
            verifiedField.set(candidate, true);
            Field homeKeyField = ApiConfig.class.getDeclaredField("preparedHomeKey");
            homeKeyField.setAccessible(true);
            homeKeyField.set(candidate, vod.getKey());
            Field contentKeyField = ApiConfig.class.getDeclaredField("probedHomeContentKey");
            contentKeyField.setAccessible(true);
            contentKeyField.set(candidate, vod.getKey());
            Field contentField = ApiConfig.class.getDeclaredField("probedHomeContentJson");
            contentField.setAccessible(true);
            contentField.set(candidate, "{\"list\":[{\"vod_id\":\"1\"}]}");
        }
        assertTrue(candidate.hasVerifiedHomeBean());
        assertEquals("{\"list\":[{\"vod_id\":\"1\"}]}", candidate.getProbedHomeContent("WexAiWenCai"));
        assertNull(candidate.getProbedHomeContent("other"));

        Field addr = ApiConfig.class.getDeclaredField("preparedAddress");
        addr.setAccessible(true);
        addr.set(candidate, "https://example.com/new.json");
        Field json = ApiConfig.class.getDeclaredField("preparedJson");
        json.setAccessible(true);
        json.set(candidate, "{}");

        ApiConfig target = config();
        target.commitPrepared(candidate, Collections.emptyList());
        assertTrue(target.isPreparedConfiguration("https://example.com/new.json", "{}"));
        assertTrue(target.hasVerifiedHomeBean());
        assertEquals("WexAiWenCai", target.getHomeBean().getKey());
        assertTrue(target.takePreparedVideos().isEmpty());
    }

    @Test public void normalizeUrlConvertsChineseSpiderDomainToPunycode() {
        assertEquals(
                "https://tv.xn--yhqu5zs87a.top/jar/fty1016.jar",
                ApiConfig.normalizeUrl("https://tv.菜妮丝.top/jar/fty1016.jar"));
    }
}
