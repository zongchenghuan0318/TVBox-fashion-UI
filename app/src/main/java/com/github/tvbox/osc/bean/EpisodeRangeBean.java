package com.github.tvbox.osc.bean;

import java.io.Serializable;

public class EpisodeRangeBean implements Serializable {
    public int startIndex;
    public int endIndex;
    public String title;

    public EpisodeRangeBean() {}

    public EpisodeRangeBean(int startIndex, int endIndex, String title) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.title = title;
    }
}
