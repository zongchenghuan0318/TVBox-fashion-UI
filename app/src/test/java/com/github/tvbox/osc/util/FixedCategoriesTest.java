package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.MovieSort;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class FixedCategoriesTest {
    private static final List<String> EXPECTED = Arrays.asList("首页", "电影", "电视剧", "综艺", "动漫", "纪录片");

    private void assertFixedTitles(List<MovieSort.SortData> categories) {
        List<String> actual = new ArrayList<>();
        for (MovieSort.SortData category : categories) actual.add(category.name);
        assertEquals(EXPECTED, actual);
    }

    @Test public void failedResponseStillShowsAllFiveCategories() {
        List<MovieSort.SortData> result = DefaultConfig.adjustSort("source", null, true);
        assertFixedTitles(result);
        for (int i = 1; i < result.size(); i++) {
            assertFalse(result.get(i).available);
            assertEquals("source", result.get(i).sourceKey);
        }
    }

    @Test public void shuffledAliasesKeepRealIdsAndFixedOrder() {
        List<MovieSort.SortData> source = Arrays.asList(
                new MovieSort.SortData("doc-id", "记录片"), new MovieSort.SortData("anime-id", "国产动漫"),
                new MovieSort.SortData("show-id", "大陆综艺"), new MovieSort.SortData("movie-id", "动作片"),
                new MovieSort.SortData("series-id", "国产剧"));
        List<MovieSort.SortData> result = DefaultConfig.adjustSort("source", source, true);
        assertFixedTitles(result);
        assertEquals("movie-id", result.get(1).id);
        assertEquals("series-id", result.get(2).id);
        assertEquals("show-id", result.get(3).id);
        assertEquals("anime-id", result.get(4).id);
        assertEquals("doc-id", result.get(5).id);
        for (int i = 1; i < result.size(); i++) assertTrue(result.get(i).available);
    }

    @Test public void repeatedMappingDoesNotRenameOrModifySourceData() {
        MovieSort.SortData raw = new MovieSort.SortData("real-id", "动作片");
        raw.filterSelect.put("year", "2026");
        List<MovieSort.SortData> source = Arrays.asList(raw);
        List<MovieSort.SortData> first = DefaultConfig.adjustSort("source", source, true);
        List<MovieSort.SortData> second = DefaultConfig.adjustSort("source", source, true);
        assertFixedTitles(first);
        assertFixedTitles(second);
        assertEquals("动作片", raw.name);
        assertEquals("real-id", second.get(1).id);
        first.get(1).filterSelect.clear();
        assertEquals("2026", raw.filterSelect.get("year"));
        assertNotSame(raw, first.get(1));
    }

    @Test public void animatedAndDocumentaryMoviesDoNotFillMovieSlot() {
        List<MovieSort.SortData> result = DefaultConfig.adjustSort("source", Arrays.asList(
                new MovieSort.SortData("anime", "动漫电影"),
                new MovieSort.SortData("doc", "纪录电影")), true);
        assertFixedTitles(result);
        assertFalse(result.get(1).available);
        assertEquals("anime", result.get(4).id);
        assertEquals("doc", result.get(5).id);
    }

    @Test public void partialResponseKeepsMissingSlotsUnavailable() {
        List<MovieSort.SortData> result = DefaultConfig.adjustSort("source", Arrays.asList(
                new MovieSort.SortData("tv-real", "剧集")), true);
        assertFixedTitles(result);
        assertEquals("tv-real", result.get(2).id);
        assertTrue(result.get(2).available);
        assertFalse(result.get(1).available);
        assertFalse(result.get(3).available);
        assertFalse(result.get(4).available);
        assertFalse(result.get(5).available);
    }

    @Test public void cachedPlaceholdersNeverBecomeRealCategories() {
        List<MovieSort.SortData> placeholders = DefaultConfig.adjustSort("source", null, true);
        List<MovieSort.SortData> result = DefaultConfig.adjustSort("source", placeholders, true);
        assertFixedTitles(result);
        for (int i = 1; i < result.size(); i++) assertFalse(result.get(i).available);
    }
}
