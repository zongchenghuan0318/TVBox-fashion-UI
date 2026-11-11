package com.github.tvbox.osc.ui.activity;

import com.github.tvbox.osc.util.LiveSourceManager;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class LiveSourceOrderingTest {
    @Test public void consecutiveMovesKeepTheSameSourceAndOtherSourcesInOrder() {
        LiveSourceManager.Source a = new LiveSourceManager.Source();
        LiveSourceManager.Source b = new LiveSourceManager.Source();
        LiveSourceManager.Source c = new LiveSourceManager.Source();
        List<LiveSourceManager.Source> sources = new ArrayList<>(Arrays.asList(a, b, c));
        int position = LiveSourceManagerActivity.moveSource(sources, 0, 1);
        position = LiveSourceManagerActivity.moveSource(sources, position, 1);
        assertEquals(2, position);
        assertEquals(Arrays.asList(b, c, a), sources);
        position = LiveSourceManagerActivity.moveSource(sources, position, -1);
        assertEquals(1, position);
        assertSame(a, sources.get(position));
    }

    @Test public void boundaryMovesDoNotReorderOrWrapTheList() {
        LiveSourceManager.Source a = new LiveSourceManager.Source();
        LiveSourceManager.Source b = new LiveSourceManager.Source();
        List<LiveSourceManager.Source> sources = new ArrayList<>(Arrays.asList(a, b));
        assertEquals(0, LiveSourceManagerActivity.moveSource(sources, 0, -1));
        assertEquals(1, LiveSourceManagerActivity.moveSource(sources, 1, 1));
        assertEquals(Arrays.asList(a, b), sources);
    }

    @Test public void movePreviewDoesNotChangeTheOriginalOrderOrSourceSettings() {
        LiveSourceManager.Source a = new LiveSourceManager.Source();
        a.enabled = false;
        a.content = "https://example.com/a.m3u";
        LiveSourceManager.Source b = new LiveSourceManager.Source();
        List<LiveSourceManager.Source> original = new ArrayList<>(Arrays.asList(a, b));
        List<LiveSourceManager.Source> preview = new ArrayList<>(original);
        LiveSourceManagerActivity.moveSource(preview, 0, 1);
        assertEquals(Arrays.asList(a, b), original);
        assertEquals(Arrays.asList(b, a), preview);
        assertFalse(a.enabled);
        assertEquals("https://example.com/a.m3u", a.content);
    }
}
