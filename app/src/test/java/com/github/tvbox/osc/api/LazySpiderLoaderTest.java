package com.github.tvbox.osc.api;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.bean.SiteBean;

import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class LazySpiderLoaderTest {
    private static class Fixture {
        final AtomicInteger loads = new AtomicInteger();
        final AtomicBoolean ready = new AtomicBoolean();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final SpiderLoader loader = new SpiderLoader("https://example.com/spider.jar");

        Fixture() throws Exception {
            Field task = SpiderLoader.class.getDeclaredField("globalJarTask");
            task.setAccessible(true);
            task.set(loader, new FutureTask<Void>(() -> {
                loads.incrementAndGet();
                started.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                ready.set(true);
                return null;
            }));
            Field base = SpiderLoader.class.getDeclaredField("mBaseLoader");
            base.setAccessible(true);
            base.set(loader, new BaseLoader() {
                @Override public Spider getSpider(String key, String api, String ext, String path) {
                    if (api.startsWith("csp_")) assertTrue(ready.get());
                    return null;
                }

                @Override public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
                    assertTrue(ready.get());
                    return null;
                }

                @Override public JSONObject jsonExtMix(String flag, String key, String name,
                                                       LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
                    assertTrue(ready.get());
                    return null;
                }
            });
        }

        void use(String api) {
            SiteBean site = new SiteBean();
            site.setKey(api);
            site.setApi(api);
            loader.getCSP(site);
        }
    }

    @Test public void configurationAndNonJarSourcesDoNotLoadGlobalJar() throws Exception {
        Fixture fixture = new Fixture();
        assertEquals(0, fixture.loads.get());
        fixture.use("https://example.com/api");
        fixture.use("https://example.com/source.js");
        fixture.use("https://example.com/source.py");
        fixture.loader.proxyLocal(new HashMap<>());
        fixture.loader.refreshCachedJar();
        assertEquals(0, fixture.loads.get());
    }

    @Test public void concurrentSpiderRequestsWaitForOneSharedLoad() throws Exception {
        Fixture fixture = new Fixture();
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> requests = new ArrayList<>();
            for (int i = 0; i < 8; i++) requests.add(workers.submit(() -> fixture.use("csp_Test")));
            assertTrue(fixture.started.await(2, TimeUnit.SECONDS));
            assertEquals(1, fixture.loads.get());
            for (Future<?> request : requests) assertFalse(request.isDone());
            fixture.release.countDown();
            for (Future<?> request : requests) request.get(2, TimeUnit.SECONDS);
            fixture.use("csp_Another");
            assertEquals(1, fixture.loads.get());
        } finally {
            fixture.release.countDown();
            workers.shutdownNow();
        }
    }

    @Test public void cancellingARequestDoesNotCancelTheSharedLoad() throws Exception {
        Fixture fixture = new Fixture();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> fixture.use("csp_First"));
            assertTrue(fixture.started.await(2, TimeUnit.SECONDS));
            first.cancel(true);
            Future<?> second = workers.submit(() -> fixture.use("csp_Second"));
            fixture.release.countDown();
            second.get(2, TimeUnit.SECONDS);
            assertEquals(1, fixture.loads.get());
            assertTrue(fixture.ready.get());
        } finally {
            fixture.release.countDown();
            workers.shutdownNow();
        }
    }

    @Test public void jarParsersLoadTheGlobalJarBeforeParsing() throws Exception {
        Fixture fixture = new Fixture();
        fixture.release.countDown();
        fixture.loader.jarExt("Test", new LinkedHashMap<>(), "https://example.com/video");
        fixture.loader.jarExtMix("flag", "Test", "name", new LinkedHashMap<>(), "https://example.com/video");
        assertEquals(1, fixture.loads.get());
    }

    @Test public void configurationLoadersDoNotShareAnInFlightJar() throws Exception {
        Fixture previous = new Fixture();
        Fixture current = new Fixture();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> oldRequest = worker.submit(() -> previous.use("csp_Old"));
            assertTrue(previous.started.await(2, TimeUnit.SECONDS));
            current.release.countDown();
            current.use("csp_Current");
            assertTrue(current.ready.get());
            assertFalse(previous.ready.get());
            previous.release.countDown();
            oldRequest.get(2, TimeUnit.SECONDS);
            assertEquals(1, current.loads.get());
        } finally {
            previous.release.countDown();
            current.release.countDown();
            worker.shutdownNow();
        }
    }
}
