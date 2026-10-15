package com.fongmi.quickjs.bean;

public class Info {

    private String url;
    private String ext;
    private String msg;

    public Info() {
    }

    public Info(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getExt() {
        return ext == null ? "" : ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }

    public String getMsg() {
        return msg == null ? "" : msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
