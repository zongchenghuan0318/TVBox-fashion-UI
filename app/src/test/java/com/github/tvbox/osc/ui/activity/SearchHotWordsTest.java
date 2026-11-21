package com.github.tvbox.osc.ui.activity;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class SearchHotWordsTest {
    private static final long NOW = 2 * 24 * 60 * 60 * 1000L;

    @Test
    public void prefersFreshHomeTitlesAndRefreshesLegacyOrExpiredEntries() {
        SearchActivity.HotWordsCache home = cache("首页片单", NOW - 2000, null);
        SearchActivity.HotWordsCache legacyHome = cache("旧首页片单", 0, null);
        SearchActivity.HotWordsCache rank = cache("B站热榜", NOW - 1000, null);
        SearchActivity.HotWordsCache expired = cache("过期热榜", NOW / 2, null);

        assertSame(home, SearchActivity.selectHotWordsCache(home, rank, "site", NOW));
        assertSame(rank, SearchActivity.selectHotWordsCache(legacyHome, rank, "site", NOW));
        assertSame(legacyHome, SearchActivity.selectHotWordsCache(legacyHome, null, "site", NOW));
        assertFalse(legacyHome.isFresh(NOW));
        assertFalse(expired.isFresh(NOW));
    }

    @Test
    public void sourceRecommendationsStayScopedToTheCurrentConfiguration() {
        SearchActivity.HotWordsCache local = cache("自建源片名", NOW - 1, "config-a\nsite");

        assertSame(local, SearchActivity.selectHotWordsCache(null, local, "config-a\nsite", NOW));
        assertNull(SearchActivity.selectHotWordsCache(null, local, "config-b\nsite", NOW));
    }

    @Test
    public void rotationShowsTenTitlesAndWrapsThroughThePool() {
        List<String> pool = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            pool.add("片名 " + i);
        }

        assertEquals(pool.subList(0, 10), SearchActivity.hotWordsBatch(pool, 0));
        assertEquals(pool.subList(10, 20), SearchActivity.hotWordsBatch(pool, 10));
        List<String> wrapped = SearchActivity.hotWordsBatch(pool, 20);
        assertEquals(10, wrapped.size());
        assertEquals(pool.subList(20, 24), wrapped.subList(0, 4));
        assertEquals(pool.subList(0, 6), wrapped.subList(4, 10));
    }

    private static SearchActivity.HotWordsCache cache(String title, long updatedAt, String sourceScope) {
        return new SearchActivity.HotWordsCache(Collections.singletonList(title), updatedAt, sourceScope);
    }
}
