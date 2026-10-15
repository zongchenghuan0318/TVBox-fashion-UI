package com.fongmi.quickjs.bean;

import com.fongmi.quickjs.utils.Parser;

public class Cache {

    private Parser.Element pdfaDoc;
    private Parser.Element pdfhDoc;
    private String pdfaHtml = "";
    private String pdfhHtml = "";

    public Parser.Element getPdfa(String html) {
        if (html == null) html = "";
        if (!html.equals(pdfaHtml)) updatePdfa(html);
        return pdfaDoc;
    }

    public Parser.Element getPdfh(String html) {
        if (html == null) html = "";
        if (!html.equals(pdfhHtml)) updatePdfh(html);
        return pdfhDoc;
    }

    public void updatePdfa(String html) {
        pdfaHtml = html;
        pdfaDoc = Parser.parseDom(html);
    }

    public void updatePdfh(String html) {
        pdfhHtml = html;
        pdfhDoc = Parser.parseDom(html);
    }
}
