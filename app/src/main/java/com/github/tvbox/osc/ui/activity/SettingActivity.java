package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.fragment.ModelSettingFragment;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.lcstudio.commonsurport.util.SPDataUtil;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;

import org.greenrobot.eventbus.EventBus;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SettingActivity extends BaseActivity implements View.OnClickListener {
    private ModelSettingFragment settingFragment;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private String oldHomeKey;
    private String oldApi;
    private String oldApiConfig;
    private int homeRec;
    private int dnsOpt;
    private boolean settingsReported;

    private final Runnable mDevModeRun = () -> devMode = "";
    private String devMode = "";

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_setting;
    }

    @Override
    protected void init() {
        findViewById(R.id.title_setting_tv).setOnClickListener(this);
        initData();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.title_setting_tv) {
            finish();
        }
    }

    private void initData() {
        oldApi = Hawk.get(HawkConfig.API_URL, ApiConfig.DEFAULT_URL);
        oldApiConfig = new SPDataUtil(this).getStringValue(ApiConfig.PRE_KEY_CONFIG, "");
        oldHomeKey = Hawk.get(HawkConfig.USER_HOME_API, "");
        homeRec = Hawk.get(HawkConfig.HOME_REC, 0);
        dnsOpt = Hawk.get(HawkConfig.DOH_URL, 0);

        settingFragment = ModelSettingFragment.newInstance();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settingsContainer, settingFragment)
                .commitNowAllowingStateLoss();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_0) {
            mHandler.removeCallbacks(mDevModeRun);
            devMode += "0";
            mHandler.postDelayed(mDevModeRun, 450);
            if (devMode.length() >= 4 && settingFragment != null) {
                devMode = "";
                settingFragment.enableDebugMode();
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (settingFragment != null && settingFragment.focusNavigationFromContent()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void finish() {
        // 只报告一次，且整个读取/比较/事件派发过程全部兜底
        if (!settingsReported && oldHomeKey != null) {
            settingsReported = true;
            try {
                boolean configChanged = !safeEquals(oldApi, Hawk.get(HawkConfig.API_URL, ApiConfig.DEFAULT_URL))
                        || !safeEquals(oldApiConfig, new SPDataUtil(this).getStringValue(ApiConfig.PRE_KEY_CONFIG, ""));
                boolean homeChanged = !safeEquals(oldHomeKey, Hawk.get(HawkConfig.USER_HOME_API, ""))
                        || homeRec != Hawk.get(HawkConfig.HOME_REC, 0)
                        || dnsOpt != Hawk.get(HawkConfig.DOH_URL, 0);
                if (dnsOpt != Hawk.get(HawkConfig.DOH_URL, 0)) {
                    try {
                        OkGoHelper.init();
                    } catch (Throwable ignored) {
                    }
                }
                if (configChanged || homeChanged) {
                    EventBus.getDefault().post(new RefreshEvent(configChanged
                            ? RefreshEvent.TYPE_API_CONFIG_CHANGED : RefreshEvent.TYPE_HOME_SETTINGS_CHANGED));
                }
            } catch (Throwable ignored) {
            }
        }
        super.finish();
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** null 安全的字符串比较：避免某一侧读成 null 时 equals 抛 NPE。 */
    private static boolean safeEquals(String left, String right) {
        if (left == null) return right == null;
        return left.equals(right);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (settingFragment != null) {
            settingFragment.activityResult(requestCode, resultCode, data);
        }
    }
}
