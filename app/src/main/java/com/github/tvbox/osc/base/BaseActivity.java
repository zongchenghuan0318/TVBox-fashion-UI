package com.github.tvbox.osc.base;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.util.RemoteKeySound;
import com.github.tvbox.osc.util.AppManager;
import com.kingja.loadsir.callback.Callback;
import com.kingja.loadsir.core.LoadService;
import com.kingja.loadsir.core.LoadSir;
import com.lcstudio.commonsurport.L;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.PermissionChecker;
import me.jessyan.autosize.AutoSizeCompat;
import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.internal.CustomAdapt;

public abstract class BaseActivity extends AppCompatActivity implements CustomAdapt {
    protected Context mContext;
    private LoadService mLoadService;
    private static float screenRatio = -100.0f;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            applyLandscapeOrientation();
            if (screenRatio < 0) {
                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(dm);
                int screenWidth = dm.widthPixels;
                int screenHeight = dm.heightPixels;
                screenRatio = (float) Math.max(screenWidth, screenHeight) / (float) Math.min(screenWidth, screenHeight);
            }
        } catch (Throwable th) {
            L.e(th);
        }
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResID());
        RemoteKeySound.attach(getWindow(), this);
        com.github.tvbox.osc.util.SeniorMode.apply(findViewById(android.R.id.content));
        installPluginProxyPanelGuard();
        mContext = this;
        AppManager.getInstance().addActivity(this);
        init();
    }

    private final ViewTreeObserver.OnGlobalLayoutListener pluginProxyPanelListener = () -> {
        ViewGroup content = getWindow() != null ? getWindow().findViewById(android.R.id.content) : null;
        if (content != null) {
            hideInjectedPluginOverlays(content);
        }
    };

    private final ViewGroup.OnHierarchyChangeListener contentHierarchyListener = new ViewGroup.OnHierarchyChangeListener() {
        @Override
        public void onChildViewAdded(View parent, View child) {
            if (isInjectedPluginOverlay(parent, child)) {
                suppressOverlayView(child);
            } else if (parent instanceof ViewGroup && isExtraNoIdChild((ViewGroup) parent, child)) {
                child.post(() -> {
                    if (isInjectedPluginOverlay(parent, child)) {
                        suppressOverlayView(child);
                    }
                });
            }
        }

        @Override
        public void onChildViewRemoved(View parent, View child) {
        }
    };

    private void installPluginProxyPanelGuard() {
        try {
            ViewGroup content = getWindow() != null ? getWindow().findViewById(android.R.id.content) : null;
            if (content == null) return;
            content.setOnHierarchyChangeListener(contentHierarchyListener);
            content.getViewTreeObserver().addOnGlobalLayoutListener(pluginProxyPanelListener);
            hideInjectedPluginOverlays(content);
        } catch (Throwable ignored) {
        }
    }

    private void hideInjectedPluginOverlays(ViewGroup content) {
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (isInjectedPluginOverlay(content, child)) {
                suppressOverlayView(child);
            }
        }
    }

    private boolean isExtraNoIdChild(ViewGroup parent, View child) {
        if (child == null || child.getId() != View.NO_ID) return false;
        if (child instanceof com.kingja.loadsir.core.LoadLayout) return false;
        return parent.indexOfChild(child) > 0;
    }

    private boolean isInjectedPluginOverlay(View parent, View child) {
        if (!(parent instanceof ViewGroup) || !isExtraNoIdChild((ViewGroup) parent, child)) {
            return false;
        }
        return containsDeterminateProgressBar(child);
    }

    private boolean containsDeterminateProgressBar(View view) {
        if (view instanceof ProgressBar) {
            return !((ProgressBar) view).isIndeterminate();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsDeterminateProgressBar(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void suppressOverlayView(View overlay) {
        if (overlay.getVisibility() != View.GONE) {
            overlay.setVisibility(View.GONE);
        }
        overlay.setAlpha(0f);
    }

    private void applyLandscapeOrientation() {
        PackageManager packageManager = getPackageManager();
        boolean television = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || (getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK)
                == Configuration.UI_MODE_TYPE_TELEVISION;
        if (!television) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSysBar();
    }

    public void hideSysBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
    }

    @Override
    public Resources getResources() {
        Resources resources = super.getResources();
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (getWindowManager() != null) {
                // Application 的尺寸可能仍属于启动前的竖屏；用当前 Activity 的窗口重新同步。
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(metrics);
                int width = metrics.widthPixels;
                int height = metrics.heightPixels;
                int orientation = resources.getConfiguration().orientation;
                if ((orientation == Configuration.ORIENTATION_LANDSCAPE && width < height)
                        || (orientation == Configuration.ORIENTATION_PORTRAIT && width > height)) {
                    int previousWidth = width;
                    width = height;
                    height = previousWidth;
                }
                if (width > 0 && height > 0) {
                    AutoSizeConfig.getInstance().setScreenWidth(width).setScreenHeight(height);
                }
            }
            AutoSizeCompat.autoConvertDensityOfCustomAdapt(resources, this);
        }
        return resources;
    }

    public boolean hasPermission(String permission) {
        boolean has = true;
        try {
            has = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED;
        } catch (Exception e) {
            L.e(e);
        }
        return has;
    }

    protected abstract int getLayoutResID();

    protected abstract void init();

    protected void setLoadSir(View view) {
        L.d("setLoadSir");
        if (mLoadService == null) {
            mLoadService = LoadSir.getDefault().register(view, new Callback.OnReloadListener() {
                @Override
                public void onReload(View v) {
                    BaseActivity.this.onReload();
                }
            });
        }
    }

    protected void onReload() {
        init();
    }

    protected void showLoading() {
        if (mLoadService != null) {
            mLoadService.showCallback(LoadingCallback.class);
        }
    }

    protected void showEmpty() {
        if (null != mLoadService) {
            mLoadService.showCallback(EmptyCallback.class);
        }
    }

    /**
     * 为空态覆盖层增加一个第二操作按钮（如“换源搜索”）。每次显示空态时生效。
     */
    protected void setEmptyAction(String text, View.OnClickListener listener) {
        if (mLoadService == null) return;
        mLoadService.setCallBack(EmptyCallback.class, (context, view) -> {
            android.widget.TextView btn = view.findViewById(com.studio.osc.R.id.btnEmptySwitch);
            if (btn == null) return;
            btn.setText(text);
            btn.setOnClickListener(listener);
            btn.setVisibility(View.VISIBLE);
        });
    }

    protected void showSuccess() {
        if (null != mLoadService) {
            mLoadService.showSuccess();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            ViewGroup content = getWindow() != null ? getWindow().findViewById(android.R.id.content) : null;
            if (content != null) {
                content.setOnHierarchyChangeListener(null);
                ViewTreeObserver observer = content.getViewTreeObserver();
                if (observer.isAlive()) {
                    observer.removeOnGlobalLayoutListener(pluginProxyPanelListener);
                }
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
        AppManager.getInstance().finishActivity(this);
    }

    public void jumpActivity(Class<? extends BaseActivity> clazz) {
        Intent intent = new Intent(mContext, clazz);
        startActivity(intent);
    }

    public void jumpActivity(Class<? extends BaseActivity> clazz, Bundle bundle) {
        Intent intent = new Intent(mContext, clazz);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    protected String getAssetText(String fileName) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            AssetManager assets = getAssets();
            BufferedReader bf = new BufferedReader(new InputStreamReader(assets.open(fileName)));
            String line;
            while ((line = bf.readLine()) != null) {
                stringBuilder.append(line);
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            L.e(e);
        }
        return "";
    }

    @Override
    public float getSizeInDp() {
        return isBaseOnWidth() ? 1280 : 720;
    }

    @Override
    public boolean isBaseOnWidth() {
        return !(screenRatio >= 4.0f);
    }

}
