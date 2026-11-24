package com.github.tvbox.osc.bean;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class PlayBean {
    public String progressKeyStr;
    public boolean bNeedParse;
    public boolean bNeedJx;
    public String playSubtitleStr;
    public String playUrl;
    public String flag;
    public String url;
    public String msg;
    public HashMap<String, String> headersMap = null;
    public LinkedHashMap<String, String> qualityUrls = null;
}