package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.ui.adapter.SettingMenuAdapter;
import com.github.tvbox.osc.ui.adapter.SettingPageAdapter;
import com.github.tvbox.osc.ui.fragment.ModelSettingFragment;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.lcstudio.commonsurport.util.SPDataUtil;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.studio.osc.R;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SettingActivity extends BaseActivity implements View.OnClickListener {
    private TvRecyclerView mGridView;
    private ViewPager mViewPager;
    private SettingMenuAdapter sortAdapter;
    private SettingPageAdapter pageAdapter;
    private List<BaseLazyFragment> fragments = new ArrayList<>();
    private ModelSettingFragment settingFragment;
    private boolean sortChange = false;
    private int defaultSelected = 0;
    private int sortFocused = 0;
    private Handler mHandler;
    private String oldHomeKey;
    private String oldApi;
    private String oldApiConfig;
    private int homeRec;
    private int dnsOpt;
    private boolean settingsReported;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_setting;
    }

    @Override
    protected void init() {
        mHandler = new Handler();
        initView();
        initData();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.title_setting_tv:
                finish();
                break;
            default:
                break;
        }
    }

    private void initView() {
        mGridView = findViewById(R.id.sortRV);
        mViewPager = findViewById(R.id.viewPager);
        findViewById(R.id.title_setting_tv).setOnClickListener(this);
        sortAdapter = new SettingMenuAdapter();
        mGridView.setAdapter(sortAdapter);
        mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        sortAdapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {
            @Override
            public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
                if (view.getId() == R.id.tvName) {
                    if (view.getParent() != null) {
                        ((ViewGroup) view.getParent()).requestFocus();
                        sortFocused = position;
                        if (sortFocused != defaultSelected) {
                            defaultSelected = sortFocused;
                            mViewPager.setCurrentItem(sortFocused, false);
                        }
                    }
                }
            }
        });
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null) {
                    TextView tvName = itemView.findViewById(R.id.tvName);
                    tvName.setTextColor(getResources().getColor(R.color.color_CCFFFFFF));
                }
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null) {
                    sortChange = true;
                    sortFocused = position;
                    TextView tvName = itemView.findViewById(R.id.tvName);
                    tvName.setTextColor(Color.WHITE);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });
    }

    private void initData() {
        oldApi = Hawk.get(HawkConfig.API_URL, ApiConfig.DEFAULT_URL);
        oldApiConfig = new SPDataUtil(this).getStringValue(ApiConfig.PRE_KEY_CONFIG, "");
        oldHomeKey = Hawk.get(HawkConfig.USER_HOME_API, "");
        homeRec = Hawk.get(HawkConfig.HOME_REC, 0);
        dnsOpt = Hawk.get(HawkConfig.DOH_URL, 0);
        List<String> sortList = new ArrayList<>();
        sortList.add("常规与播放");
        sortAdapter.setNewData(sortList);
        initViewPager();
    }

    private void initViewPager() {
        settingFragment = ModelSettingFragment.newInstance();
        fragments.add(settingFragment);
        pageAdapter = new SettingPageAdapter(getSupportFragmentManager(), fragments);
        mViewPager.setAdapter(pageAdapter);
        mViewPager.setCurrentItem(0);
    }

    private Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                if (sortFocused != defaultSelected) {
                    defaultSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                }
            }
        }
    };

    private Runnable mDevModeRun = new Runnable() {
        @Override
        public void run() {
            devMode = "";
        }
    };


    public interface DevModeCallback {
        void onChange();
    }

    public static DevModeCallback callback = null;

    String devMode = "";

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mHandler.removeCallbacks(mDataRunnable);
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_0:
                    mHandler.removeCallbacks(mDevModeRun);
                    devMode += "0";
                    mHandler.postDelayed(mDevModeRun, 200);
                    if (devMode.length() >= 4) {
                        if (callback != null) {
                            callback.onChange();
                        }
                    }
                    break;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            mHandler.postDelayed(mDataRunnable, 200);
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
        // 只报告一次，且整个读取/比较/事件派发过程全部兜底：即使某个 Hawk 键读取或
        // EventBus 派发抛异常，也绝不能让它冒泡成未捕获异常触发进程崩溃。
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
                // 返回首页是最常见的操作之一：此处宁可漏报一次刷新，也绝不能崩溃。
            }
        }
        super.finish();
    }

    /** null 安全的字符串比较：避免某一侧读成 null 时 equals 抛 NPE。 */
    private static boolean safeEquals(String left, String right) {
        if (left == null) return right == null;
        return left.equals(right);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        settingFragment.activityResult(requestCode, resultCode, data);
    }
}
