//package com.github.tvbox.osc.ui.activity;
//
//import android.annotation.TargetApi;
//import android.app.Activity;
//import android.content.Context;
//import android.net.http.SslError;
//import android.os.Build;
//import android.view.KeyEvent;
//import android.webkit.SslErrorHandler;
//import android.webkit.ValueCallback;
//import android.webkit.WebResourceRequest;
//import android.webkit.WebResourceResponse;
//import android.webkit.WebView;
//
//import com.github.tvbox.osc.util.AdBlocker;
//import com.lcstudio.commonsurport.L;
//
//import org.xwalk.core.XWalkResourceClient;
//import org.xwalk.core.XWalkView;
//import org.xwalk.core.XWalkWebResourceRequest;
//import org.xwalk.core.XWalkWebResourceResponse;
//
//import java.io.ByteArrayInputStream;
//import java.util.HashMap;
//import java.util.Map;
//
//import androidx.annotation.NonNull;
//import me.jessyan.autosize.AutoSize;
//
//class WebViewClient {
//
//
//    //========================webView类==============================================
//    class MyWebView extends WebView {
//        public MyWebView(@NonNull Context context) {
//            super(context);
//        }
//
//        @Override
//        public void setOverScrollMode(int mode) {
//            super.setOverScrollMode(mode);
//            if (mContext instanceof Activity)
//                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayActivity.this);
//        }
//
//        @Override
//        public boolean dispatchKeyEvent(KeyEvent event) {
//            return false;
//        }
//    }
//
//    class MyXWalkView extends XWalkView {
//        public MyXWalkView(Context context) {
//            super(context);
//        }
//
//        @Override
//        public void setOverScrollMode(int mode) {
//            super.setOverScrollMode(mode);
//            if (mContext instanceof Activity) {
//                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayActivity.this);
//            }
//        }
//
//        @Override
//        public boolean dispatchKeyEvent(KeyEvent event) {
//            return false;
//        }
//    }
//
//    class SysWebClient extends android.webkit.WebViewClient {
//        @Override
//        public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
//            sslErrorHandler.proceed();
//        }
//
//        @Override
//        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
//            return false;
//        }
//
//        @Override
//        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
//            L.d("shouldInterceptRequest() url");
//            WebResourceResponse response = checkIsVideo(url, null);
//            if (response == null) {
//                return super.shouldInterceptRequest(view, url);
//            } else {
//                return response;
//            }
//        }
//
//        @Override
//        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
//        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
//            L.d("shouldInterceptRequest() request ");
//            String url = request.getUrl().toString();
//            HashMap<String, String> webHeaders = new HashMap<>();
//            try {
//                Map<String, String> hds = request.getRequestHeaders();
//                for (String k : hds.keySet()) {
//                    if (k.equalsIgnoreCase("user-agent")
//                            || k.equalsIgnoreCase("referer")
//                            || k.equalsIgnoreCase("origin")) {
//                        webHeaders.put(k, " " + hds.get(k));
//                    }
//                }
//            } catch (Throwable th) {
//                L.w("shouldInterceptRequest " + th.getMessage());
//            }
//            WebResourceResponse response = checkIsVideo(url, webHeaders);
//            if (response == null) {
//                return super.shouldInterceptRequest(view, request);
//            } else {
//                return response;
//            }
//        }
//
//        private WebResourceResponse checkIsVideo(String url, HashMap<String, String> headers) {
//            L.d("checkIsVideo() url=" + url);
//            if (url.endsWith("/favicon.ico")) {
//                return new WebResourceResponse("image/png", null, null);
//            }
//            boolean isAd;
//            if (!loadedUrlsMap.containsKey(url)) {
//                isAd = AdBlocker.isAd(url);
//                loadedUrlsMap.put(url, isAd);
//            } else {
//                isAd = loadedUrlsMap.get(url);
//            }
//
//            if (checkVideoFormat(url)) {
//                m_bLoadFound = true;
//            }
//            playVideo(url, headers);
//            stopLoadWebView(false);
//
//            return isAd || m_bLoadFound ? AdBlocker.createEmptyResource() : null;
//        }
//    }
//
//    class XWalkWebClient extends XWalkResourceClient {
//        public XWalkWebClient(XWalkView view) {
//            super(view);
//        }
//
//        @Override
//        public void onDocumentLoadedInFrame(XWalkView view, long frameId) {
//            super.onDocumentLoadedInFrame(view, frameId);
//        }
//
//        @Override
//        public void onLoadStarted(XWalkView view, String url) {
//            super.onLoadStarted(view, url);
//        }
//
//        @Override
//        public void onLoadFinished(XWalkView view, String url) {
//            super.onLoadFinished(view, url);
//        }
//
//        @Override
//        public void onProgressChanged(XWalkView view, int progressInPercent) {
//            super.onProgressChanged(view, progressInPercent);
//        }
//
//        @Override
//        public XWalkWebResourceResponse shouldInterceptLoadRequest(XWalkView view, XWalkWebResourceRequest request) {
//            L.d("shouldInterceptLoadRequest()");
//            String url = request.getUrl().toString();
//            if (url.endsWith("/favicon.ico")) {
//                return createXWalkWebResourceResponse("image/png", null, null);
//            }
//            L.d("shouldInterceptLoadRequest url:" + url);
//            boolean ad;
//            if (!loadedUrlsMap.containsKey(url)) {
//                ad = AdBlocker.isAd(url);
//                loadedUrlsMap.put(url, ad);
//            } else {
//                ad = loadedUrlsMap.get(url);
//            }
//            if (!ad && !m_bLoadFound) {
//                if (checkVideoFormat(url)) {
//                    m_bLoadFound = true;
//                    HashMap<String, String> webHeaders = new HashMap<>();
//                    try {
//                        Map<String, String> hds = request.getRequestHeaders();
//                        for (String k : hds.keySet()) {
//                            if (k.equalsIgnoreCase("user-agent")
//                                    || k.equalsIgnoreCase("referer")
//                                    || k.equalsIgnoreCase("origin")) {
//                                webHeaders.put(k, " " + hds.get(k));
//                            }
//                        }
//                    } catch (Throwable th) {
//                        L.w(th.getMessage());
//                    }
//                    if (webHeaders != null && !webHeaders.isEmpty()) {
//                        playVideo(url, webHeaders);
//                    } else {
//                        playVideo(url, null);
//                    }
//                    stopLoadWebView(false);
//                }
//            }
//            return ad || m_bLoadFound ?
//                    createXWalkWebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes())) :
//                    super.shouldInterceptLoadRequest(view, request);
//        }
//
//        @Override
//        public boolean shouldOverrideUrlLoading(XWalkView view, String s) {
//            return false;
//        }
//
//        @Override
//        public void onReceivedSslError(XWalkView view, ValueCallback<Boolean> callback, SslError error) {
//            callback.onReceiveValue(true);
//        }
//
//    }
//
//}
