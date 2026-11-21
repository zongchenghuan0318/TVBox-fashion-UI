package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.Movie;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BilibiliHotRankTest {
    @Test
    public void extractsTitlesAndCoversWithOriginalRankAcrossInvalidItems() {
        String body = "{\"result\":{\"list\":[{\"title\":\"剧集\",\"cover\":\"http://i.example/film.jpg\"},"
                + "null,{\"title\":[]},{\"title\":\"电影\",\"cover\":null}]}}";
        List<Movie.Video> rank = BilibiliHotRank.parse(body);

        assertEquals(2, rank.size());
        assertEquals("剧集", rank.get(0).name);
        assertEquals("https://i.example/film.jpg", rank.get(0).pic);
        assertEquals("电影", rank.get(1).name);
        assertEquals("热榜 TOP4", rank.get(1).note);
        assertTrue(BilibiliHotRank.parse("{\"result\":null}").isEmpty());
    }
}
