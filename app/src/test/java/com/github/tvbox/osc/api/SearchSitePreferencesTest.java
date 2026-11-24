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

    @Test
    public void defaultSearchOrderPrioritizesFenzhuWencaiFeifanLiangziByName() {
        Map<String, SiteBean> sites = new LinkedHashMap<>();
        sites.put("AiNewHuBan", site("AiNewHuBan", "💓虎斑┃4K💓", 1));
        sites.put("lzi", site("lzi", "量子┃影视", 1));
        sites.put("ffzy", site("ffzy", "非凡┃影视", 1));
        sites.put("WexAiWenCai", site("WexAiWenCai", "💥文才┃秒播💥", 1));
        sites.put("AppV7 | 粉猪追剧", site("AppV7 | 粉猪追剧", "💥粉猪┃秒播💥", 1));
        sites.put("disabled_search", site("disabled_search", "粉猪不可搜", 0));

        SearchSitePreferences prefs = new SearchSitePreferences(sites);
        List<SiteBean> ordered = prefs.getOrderedSearchSiteList();

        assertEquals(5, ordered.size());
        assertEquals("AppV7 | 粉猪追剧", ordered.get(0).getKey());
        assertEquals("WexAiWenCai", ordered.get(1).getKey());
        assertEquals("ffzy", ordered.get(2).getKey());
        assertEquals("lzi", ordered.get(3).getKey());
        assertEquals("AiNewHuBan", ordered.get(4).getKey());
    }

    @Test
    public void unlistedSitesAfterConfigSwitchStillFollowDefaultPriority() {
        Map<String, SiteBean> sites = new LinkedHashMap<>();
        sites.put("花卷", site("花卷", "💓花卷┃4K💓", 1));
        sites.put("lzi", site("lzi", "量子┃影视", 1));
        sites.put("ffzy", site("ffzy", "非凡┃影视", 1));
        sites.put("WexAiWenCai", site("WexAiWenCai", "💥文才┃秒播💥", 1));
        sites.put("AppV7_粉猪追剧", site("AppV7_粉猪追剧", "💥粉猪┃秒播💥", 1));

        SearchSitePreferences prefs = new SearchSitePreferences(sites);
        // Simulate stale order saved from a different config source whose keys don't exist here
        prefs.setSearchSiteOrder(Arrays.asList("old_only_key_1", "old_only_key_2"));

        List<SiteBean> ordered = prefs.getOrderedSearchSiteList();
        assertEquals("AppV7_粉猪追剧", ordered.get(0).getKey());
        assertEquals("WexAiWenCai", ordered.get(1).getKey());
        assertEquals("ffzy", ordered.get(2).getKey());
        assertEquals("lzi", ordered.get(3).getKey());
        assertEquals("花卷", ordered.get(4).getKey());
    }

    @Test
    public void eachVodConfigRemembersOwnOrderWhileNewConfigUsesDefaultPriority() {
        Map<String, SiteBean> sites = new LinkedHashMap<>();
        SearchSitePreferences prefs = new SearchSitePreferences(sites);

        // 配置 A：ysc.tengxuntaobao.win
        sites.put("bfzy", site("bfzy", "暴风┃影视", 1));
        sites.put("lzi", site("lzi", "量子┃影视", 1));
        sites.put("ffzy", site("ffzy", "非凡┃影视", 1));
        sites.put("WexAiWenCai", site("WexAiWenCai", "💥文才┃秒播💥", 1));
        sites.put("AppV7_粉猪追剧", site("AppV7_粉猪追剧", "💥粉猪┃秒播💥", 1));
        prefs.setConfigScope("https://ysc.tengxuntaobao.win/");

        // 用户在配置 A 里自定义顺序并禁用 bfzy
        prefs.setSearchSiteDisabled(Collections.singletonList("bfzy"));
        prefs.setSearchSiteOrder(Arrays.asList("WexAiWenCai", "AppV7_粉猪追剧", "ffzy", "lzi"));
        List<SiteBean> orderA = prefs.getOrderedSearchSiteList();
        assertEquals(4, orderA.size());
        assertEquals("WexAiWenCai", orderA.get(0).getKey());
        assertEquals("AppV7_粉猪追剧", orderA.get(1).getKey());
        assertFalse(prefs.isSearchSiteEnabled("bfzy"));

        // 切换到配置 B：tv.xn--yhqu5zs87a.top（key 完全不同，且从未自定义）
        sites.clear();
        sites.put("bfzy", site("bfzy", "暴风┃影视", 1));
        sites.put("lzi", site("lzi", "量子┃影视", 1));
        sites.put("非凡", site("非凡", "非凡┃影视", 1));
        sites.put("玩偶", site("玩偶", "玩偶哥哥┃4K", 1));
        sites.put("文采", site("文采", "文采┃秒播", 1));
        prefs.setConfigScope("https://tv.xn--yhqu5zs87a.top");

        // 配置 B 不受配置 A 的禁用和旧 key 影响，默认走 粉猪 > 文采/文才 > 非凡 > 量子 > 其余
        assertTrue(prefs.isSearchSiteEnabled("bfzy"));
        List<SiteBean> orderB = prefs.getOrderedSearchSiteList();
        assertEquals(5, orderB.size());
        assertEquals("文采", orderB.get(0).getKey());
        assertEquals("非凡", orderB.get(1).getKey());
        assertEquals("lzi", orderB.get(2).getKey());
        assertEquals(0, prefs.getSearchSiteOrderIndex("文采"));
        assertEquals(1, prefs.getSearchSiteOrderIndex("非凡"));
        assertEquals(2, prefs.getSearchSiteOrderIndex("lzi"));

        // 切回配置 A（哪怕不带末尾斜杠），仍记住配置 A 自己的排序和禁用状态
        sites.clear();
        sites.put("bfzy", site("bfzy", "暴风┃影视", 1));
        sites.put("lzi", site("lzi", "量子┃影视", 1));
        sites.put("ffzy", site("ffzy", "非凡┃影视", 1));
        sites.put("WexAiWenCai", site("WexAiWenCai", "💥文才┃秒播💥", 1));
        sites.put("AppV7_粉猪追剧", site("AppV7_粉猪追剧", "💥粉猪┃秒播💥", 1));
        prefs.setConfigScope("https://ysc.tengxuntaobao.win");

        List<SiteBean> restoredA = prefs.getOrderedSearchSiteList();
        assertEquals(4, restoredA.size());
        assertEquals("WexAiWenCai", restoredA.get(0).getKey());
        assertEquals("AppV7_粉猪追剧", restoredA.get(1).getKey());
        assertFalse(prefs.isSearchSiteEnabled("bfzy"));
    }

    @Test
    public void configScopeNormalizesChineseDomainAndTrailingSlash() {
        assertEquals(
                SearchSitePreferences.normalizeConfigScope("http://肥猫.net/"),
                SearchSitePreferences.normalizeConfigScope("http://xn--z7x900a.net"));
    }
}
