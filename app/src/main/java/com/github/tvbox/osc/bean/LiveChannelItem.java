package com.github.tvbox.osc.bean;

import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2021/1/12
 */
public class LiveChannelItem {
    private int channelIndex;  // 频道索引号
    private int channelNum;
    private String channelName;   //频道名称
    public int sourceIndex = 0;  //频道源索引
    private ArrayList<String> channelSourceNames;//频道源名称
    private ArrayList<String> channelUrls;    //频道源地址
    public int sourceNum = 0;  //频道源总数

    public void setChannelIndex(int channelIndex) {
        this.channelIndex = channelIndex;
    }

    public int getChannelIndex() {
        return channelIndex;
    }

    public void setChannelNum(int channelNum) {
        this.channelNum = channelNum;
    }

    public int getChannelNum() {
        return channelNum;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelUrls(ArrayList<String> channelUrls) {
        this.channelUrls = channelUrls;
        sourceNum = channelUrls.size();
    }

    public void preSource() {
        sourceIndex--;
        if (sourceIndex < 0) sourceIndex = sourceNum - 1;
    }

    public void nextSource() {
        sourceIndex++;
        if (sourceIndex == sourceNum) sourceIndex = 0;
    }

    public void setSourceIndex(int sourceIndex) {
        this.sourceIndex = sourceIndex;
    }

    public int getSourceIndex() {
        return sourceIndex;
    }

    public String getUrl() {
        if(NullUtil.isNull(channelUrls)){
            L.d("channelUrls is Null");
            return  "";
        }
        return channelUrls.get(sourceIndex);
    }

    public int getSourceNum() {
        return sourceNum;
    }

    public ArrayList<String> getChannelSourceNames() {
        return channelSourceNames;
    }

    public void setChannelSourceNames(ArrayList<String> channelSourceNames) {
        this.channelSourceNames = channelSourceNames;
    }

    public String getSourceName() {
        return channelSourceNames.get(sourceIndex);
    }

}