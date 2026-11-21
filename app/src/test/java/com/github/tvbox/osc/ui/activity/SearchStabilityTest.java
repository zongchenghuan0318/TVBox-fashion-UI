package com.github.tvbox.osc.ui.activity;

import com.fongmi.quickjs.bean.Req;
import com.fongmi.quickjs.utils.Connect;
import com.github.tvbox.osc.bean.AbsJson;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;

import static org.junit.Assert.*;

public class SearchStabilityTest {

    private static Movie.Video video(String sourceKey, String id, String name) {
        Movie.Video v = new Movie.Video();
        v.sourceKey = sourceKey;
        v.id = id;
        v.name = name;
        return v;
    }

    @Test
    public void matchesQueryIsCaseInsensitiveAndNullSafe() {
        assertTrue(SearchActivity.matchesQuery("Avatar: The Way of Water", "AVATAR"));
        assertTrue(SearchActivity.matchesQuery("Top Gun: Maverick", "TOP"));
        assertTrue(SearchActivity.matchesQuery("庆余年 2", "庆余年2"));
        assertTrue(SearchActivity.matchesQuery("庆余年·第2季", "庆余年 2"));
        assertTrue(SearchActivity.matchesQuery("狂飙", "狂飙"));
        assertFalse(SearchActivity.matchesQuery("狂飙", null));
        assertFalse(SearchActivity.matchesQuery(null, "狂飙"));
        assertFalse(SearchActivity.matchesQuery("狂飙", "   "));
    }

    @Test
    public void matchesVideoSupportsActorAndDirectorSearch() {
        Movie.Video v = video("s1", "1", "功夫");
        v.actor = "周星驰,元华,元秋";
        v.director = "周星驰";
        assertTrue(SearchActivity.matchesVideo(v, "周星驰"));
        assertTrue(SearchActivity.matchesVideo(v, "元秋"));
        assertFalse(SearchActivity.matchesVideo(v, "张艺谋"));
        assertTrue(SearchActivity.matchesVideo(v, "周星驰 功夫"));
        assertTrue(SearchActivity.matchesVideo(v, "功夫　元秋"));
        assertFalse(SearchActivity.matchesVideo(v, "周星驰 功夫 张艺谋"));
    }

    @Test
    public void preserveExistingOrderPreventsHeadInsertionWhileBrowsing() {
        Map<String, Integer> order = new HashMap<>();
        order.put("priority_1", 0);
        order.put("low_priority", 10);

        Movie.Video existing1 = video("low_priority", "low-1", "Movie 1");
        Movie.Video existing2 = video("low_priority", "low-2", "Movie 2");
        Movie.Video lateHigh = video("priority_1", "high-1", "High Movie");

        List<Movie.Video> merged = SearchActivity.mergeAndOrderResults(
                Arrays.asList(existing1, existing2),
                Collections.singletonList(lateHigh),
                order,
                200,
                true);
        assertEquals(3, merged.size());
        assertSame(existing1, merged.get(0));
        assertSame(existing2, merged.get(1));
        assertSame(lateHigh, merged.get(2));
    }

    @Test
    public void cleanHotWordTitlePreservesSpacesAndHyphens() {
        assertEquals("Spider Man", SearchActivity.cleanHotWordTitle("《Spider Man》"));
        assertEquals("名侦探 柯南", SearchActivity.cleanHotWordTitle(" <名侦探   柯南> "));
        assertEquals("007-幽灵党", SearchActivity.cleanHotWordTitle("《007-幽灵党》"));
        assertEquals("X-Men", SearchActivity.cleanHotWordTitle("X-Men"));
        assertEquals("", SearchActivity.cleanHotWordTitle(null));
    }

    @Test
    public void matchScoreAndSameSourceOrderingPreferExactTitles() {
        Map<String, Integer> order = Collections.singletonMap("s1", 0);
        Movie.Video extra = video("s1", "1", "解说 狂飙 花絮");
        Movie.Video prefix = video("s1", "2", "狂飙 第二季");
        Movie.Video exact = video("s1", "3", "狂飙");

        List<Movie.Video> merged = SearchActivity.mergeAndOrderResults(
                Collections.emptyList(),
                Arrays.asList(extra, prefix, exact),
                order,
                200,
                false,
                "狂飙");
        assertEquals(3, merged.size());
        assertSame(exact, merged.get(0));
        assertSame(prefix, merged.get(1));
        assertSame(extra, merged.get(2));
    }

    @Test
    public void isValidRemoteAddressRejectsUnusableFallbackHosts() {
        assertFalse(SearchActivity.isValidRemoteAddress(null));
        assertFalse(SearchActivity.isValidRemoteAddress(""));
        assertFalse(SearchActivity.isValidRemoteAddress("not a URL"));
        assertFalse(SearchActivity.isValidRemoteAddress("http://0.0.0.0:9978/"));
        assertFalse(SearchActivity.isValidRemoteAddress("http://127.0.0.1:9978/"));
        assertFalse(SearchActivity.isValidRemoteAddress("http://127.0.0.2:9978/"));
        assertFalse(SearchActivity.isValidRemoteAddress("http://localhost:9978/"));
        assertFalse(SearchActivity.isValidRemoteAddress("http://192.168.1.100:99999/"));
        assertTrue(SearchActivity.isValidRemoteAddress("http://10.0.0.0:9978/"));
        assertTrue(SearchActivity.isValidRemoteAddress("http://192.168.1.100:9978/"));
    }

    @Test
    public void lenientGsonParsesDirtyIntFieldsWithoutDroppingResults() throws Exception {
        Field gsonField = SourceViewModel.class.getDeclaredField("GSON");
        gsonField.setAccessible(true);
        Gson gson = (Gson) gsonField.get(null);
        Field typeField = SourceViewModel.class.getDeclaredField("ABS_JSON_TYPE");
        typeField.setAccessible(true);
        Type type = (Type) typeField.get(null);

        String dirtyJson = "{\"code\":\"1\",\"page\":\"\",\"pagecount\":null,\"total\":\"未知\","
                + "\"list\":[{\"vod_id\":\"42\",\"type_id\":\"\",\"group_id\":null,\"vod_name\":\"三体\"}]}";
        AbsJson parsed = gson.fromJson(dirtyJson, type);
        assertNotNull(parsed);
        assertEquals(1, parsed.code);
        assertEquals(0, parsed.page);
        assertEquals(0, parsed.total);
        assertNotNull(parsed.list);
        assertEquals(1, parsed.list.size());
        assertEquals("三体", parsed.list.get(0).vod_name);
        assertEquals(0, parsed.list.get(0).type_id);
    }

    @Test
    public void mergeAndOrderResultsSortsBeforeTruncatingSoHighPriorityLateSourcesWin() {
        Map<String, Integer> order = new HashMap<>();
        order.put("priority_1", 0);
        order.put("low_priority", 10);

        List<Movie.Video> existing = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            existing.add(video("low_priority", "low-" + i, "Movie " + i));
        }
        List<Movie.Video> incoming = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            incoming.add(video("priority_1", "high-" + i, "High Movie " + i));
        }

        List<Movie.Video> merged = SearchActivity.mergeAndOrderResults(existing, incoming, order, 200);
        assertEquals(200, merged.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("priority_1", merged.get(i).sourceKey);
            assertEquals("high-" + i, merged.get(i).id);
        }
    }

    @Test
    public void mergeAndOrderResultsDeduplicatesSameSourceAndId() {
        Map<String, Integer> order = Collections.singletonMap("s1", 0);
        Movie.Video first = video("s1", "100", "狂飙");
        Movie.Video duplicate = video("s1", "100", "狂飙");
        Movie.Video other = video("s1", "101", "狂飙 番外");

        List<Movie.Video> merged = SearchActivity.mergeAndOrderResults(
                Collections.singletonList(first),
                Arrays.asList(duplicate, other),
                order,
                200);
        assertEquals(2, merged.size());
        assertSame(first, merged.get(0));
        assertSame(other, merged.get(1));
    }

    @Test
    public void sanitizeHistoryWordsRemovesNullsBlanksAndCaseDuplicates() {
        List<Object> corrupted = new ArrayList<>();
        corrupted.add(null);
        corrupted.add("");
        corrupted.add("   ");
        corrupted.add("Avatar");
        corrupted.add("avatar");
        corrupted.add(12345);
        corrupted.add(" 三体 ");
        corrupted.add("狂飙");

        List<String> cleaned = SearchActivity.sanitizeHistoryWords(corrupted, 2);
        assertEquals(Arrays.asList("Avatar", "三体"), cleaned);
        assertTrue(SearchActivity.sanitizeHistoryWords("not-a-list", 20).isEmpty());
        assertTrue(SearchActivity.sanitizeHistoryWords(null, 20).isEmpty());
    }

    @Test
    public void quickJsConnectReusesSharedConnectionPoolAndDispatcher() throws Exception {
        Method clientMethod = Connect.class.getDeclaredMethod("client", Req.class);
        clientMethod.setAccessible(true);
        Req req1 = Req.objectFrom("{\"timeout\":5000}");
        Req req2 = Req.objectFrom("{\"timeout\":8000}");
        OkHttpClient c1 = (OkHttpClient) clientMethod.invoke(null, req1);
        OkHttpClient c2 = (OkHttpClient) clientMethod.invoke(null, req2);

        assertSame(c1.connectionPool(), c2.connectionPool());
        assertSame(c1.dispatcher(), c2.dispatcher());
        assertEquals(5000, c1.readTimeoutMillis());
        assertEquals(8000, c2.readTimeoutMillis());
    }
}
