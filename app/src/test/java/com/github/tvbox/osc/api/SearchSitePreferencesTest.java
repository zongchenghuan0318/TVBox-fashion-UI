package com.github.tvbox.osc.api;

import com.github.tvbox.osc.bean.SiteBean;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class SearchSitePreferencesTest {

    private static SiteBean site(String key, String name, int searchable) {
        SiteBean bean = new SiteBean();
        bean.setKey(key);
        bean.setName(name);
        bean.setSearchable(searchable);
        return bean;
    }

    @Test
    public void orderedSearchSitesUsesInMemoryCacheAndRespectsOrderAndDisabled() {
        Map<String, SiteBean> sites = new LinkedHashMap<>();
        sites.put("a", site("a", "普通源A", 1));
        sites.put("b", site("b", "文采┃秒播", 1));
        sites.put("c", site("c", "非凡┃影视", 1));
        sites.put("d", site("d", "不可搜", 0));

        SearchSitePreferences prefs = new SearchSitePreferences(sites);
        prefs.setSearchSiteDisabled(Collections.singletonList("a"));
        prefs.setSearchSiteOrder(Arrays.asList("c", "b"));

        assertFalse(prefs.isSearchSiteEnabled("a"));
        assertTrue(prefs.isSearchSiteEnabled("b"));
        assertTrue(prefs.isSearchSiteEnabled("c"));

        List<SiteBean> ordered = prefs.getOrderedSearchSiteList();
        assertEquals(2, ordered.size());
        assertEquals("c", ordered.get(0).getKey());
        assertEquals("b", ordered.get(1).getKey());
    }
}
