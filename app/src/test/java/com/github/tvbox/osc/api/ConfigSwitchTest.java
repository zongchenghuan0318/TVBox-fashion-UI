package com.github.tvbox.osc.api;

import com.github.tvbox.osc.bean.SiteBean;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
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
}
