package com.github.tvbox.osc.ui.fragment;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.ui.activity.LiveSourceManagerActivity;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.ApiDialog;
import com.github.tvbox.osc.ui.dialog.BackupDialog;
import com.github.tvbox.osc.ui.dialog.HomeCardMotionDialog;
import com.github.tvbox.osc.ui.dialog.HomeLayoutDialog;
import com.github.tvbox.osc.ui.dialog.SearchSiteDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.view.CapsuleSwitch;
import com.github.tvbox.osc.util.AppExecutors;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.RemoteKeySound;
import com.github.tvbox.osc.util.SeniorMode;
import com.github.tvbox.osc.util.UpdateManager;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.PhoneParams;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 设置页：左侧四个分组导航（播放 / 首页与搜索 / 数据与缓存 / 关于），右侧只显示当前分组。
 */
public class ModelSettingFragment extends BaseLazyFragment {
    private static final int GROUP_PLAY = 0;
    private static final int GROUP_HOME = 1;
    private static final int GROUP_DATA = 2;
    private static final int GROUP_ABOUT = 3;

    private static final String[] LANGUAGE_CODES = {"", "zh", "en", "ja", "ko", "fr", "de", "es"};
    private static final String[] LANGUAGE_NAMES = {"自动选择", "中文 zh", "英文 en", "日语 ja",
            "韩语 ko", "法语 fr", "德语 de", "西班牙语 es"};
    private static final String[] NAV_TITLES = {"播放", "首页与搜索", "数据与缓存", "关于"};

    private View[] navItems;
    private View[] groups;
    private final int[] lastGroupFocusId = {R.id.llPlay, R.id.llHomeApi, R.id.llApi, R.id.llAbout};

    private TextView tvDebugOpen;
    private TextView tvMediaCodec;
    private TextView tvPlay;
    private TextView tvRender;
    private TextView tvScale;
    private TextView tvApi;
    private TextView tvLiveSource;
    private TextView tvDoh;
    private TextView tvHomeApi;
    private TextView tvHomeMode;
    private TextView tvCardStyle;
    private TextView tvHomeRec;
    private TextView tvHomeLayout;
    private TextView tvSearchView;
    private TextView tvSearchSite;
    private TextView tvPlayTimeStep;
    private TextView tvPlayTimeStart;
    private TextView tvPlayTimeEnd;
    private TextView tvAudioLanguage;
    private TextView tvSubtitleLanguage;
    private TextView tvClearCache;
    private TextView tvBackupSummary;
    private CapsuleSwitch switchSubtitle;
    private CapsuleSwitch switchAutoUpdate;
    private CapsuleSwitch switchRemoteKeySound;
    private CapsuleSwitch switchSeniorMode;
    private ApiDialog mApiDialog = null;
    private int easterEggClicks;
    private long lastEasterEggClick;

    public static ModelSettingFragment newInstance() {
        return new ModelSettingFragment();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_model;
    }

    public void activityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UpdateManager.INSTALL_PERMISSION_REQUEST) return;
        L.d("onActivityResult（）");
        if (mApiDialog != null) {
            mApiDialog.onActivityResult(requestCode, resultCode, data);
        }
    }

    public void enableDebugMode() {
        View groupDebug = findViewById(R.id.groupDebug);
        if (groupDebug != null) {
            groupDebug.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void init() {
        bindViews();
        bindNavigation();
        bindTexts();
        bindToggles();
        bindPlayback();
        bindHome();
        bindData();
        bindAbout();
        refreshDataGroupStats();
        if (navItems != null && navItems.length > 0) {
            navItems[0].post(() -> {
                if (isAdded() && navItems[0] != null) {
                    navItems[0].requestFocus();
                }
            });
        }
    }

    private void bindViews() {
        tvDebugOpen = findViewById(R.id.tvDebugOpen);
        tvMediaCodec = findViewById(R.id.tvMediaCodec);
        tvPlay = findViewById(R.id.tvPlay);
        tvRender = findViewById(R.id.tvRenderType);
        tvScale = findViewById(R.id.tvScaleType);
        tvApi = findViewById(R.id.tvApi);
        tvLiveSource = findViewById(R.id.tvLiveSource);
        tvDoh = findViewById(R.id.tvDoh);
        tvHomeApi = findViewById(R.id.tvHomeApi);
        tvHomeMode = findViewById(R.id.tvHomeMode);
        tvCardStyle = findViewById(R.id.tvCardStyle);
        tvHomeRec = findViewById(R.id.tvHomeRec);
        tvHomeLayout = findViewById(R.id.tvHomeLayout);
        tvSearchView = findViewById(R.id.tvSearchView);
        tvSearchSite = findViewById(R.id.tvSearchSite);
        tvPlayTimeStep = findViewById(R.id.tvPlayTimeStep);
        tvPlayTimeStart = findViewById(R.id.tvPlayTimeStart);
        tvPlayTimeEnd = findViewById(R.id.tvPlayTimeEnd);
        tvAudioLanguage = findViewById(R.id.tvAudioLanguage);
        tvSubtitleLanguage = findViewById(R.id.tvSubtitleLanguage);
        tvClearCache = findViewById(R.id.tvClearCache);
        tvBackupSummary = findViewById(R.id.tvBackupSummary);
        switchSubtitle = findViewById(R.id.switchSubtitle);
        switchAutoUpdate = findViewById(R.id.switchAutoUpdate);
        switchRemoteKeySound = findViewById(R.id.switchRemoteKeySound);
        switchSeniorMode = findViewById(R.id.switchSeniorMode);
        navItems = new View[]{findViewById(R.id.navPlay), findViewById(R.id.navHome),
                findViewById(R.id.navData), findViewById(R.id.navAbout)};
        groups = new View[]{findViewById(R.id.groupPlay), findViewById(R.id.groupHome),
                findViewById(R.id.groupData), findViewById(R.id.groupAbout)};
    }

    /** 左侧导航：焦点或点击切换分组，右侧只显示当前分组，并记忆每组最后聚焦行 */
    private void bindNavigation() {
        for (int i = 0; i < navItems.length; i++) {
            final int index = i;
            navItems[i].setNextFocusRightId(lastGroupFocusId[i]);
            bindGroupNavigation(groups[i], index, navItems[i].getId());
            navItems[i].setOnClickListener(v -> showGroup(index));
            navItems[i].setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) showGroup(index);
            });
        }
        showGroup(GROUP_PLAY);
    }

    private void bindGroupNavigation(View view, int groupIndex, int navigationId) {
        view.setNextFocusLeftId(navigationId);
        if (view.isFocusable() && view.getId() != View.NO_ID) {
            final View.OnFocusChangeListener existing = view.getOnFocusChangeListener();
            view.setOnFocusChangeListener((v, hasFocus) -> {
                if (existing != null) existing.onFocusChange(v, hasFocus);
                if (hasFocus && v.getId() != View.NO_ID) {
                    lastGroupFocusId[groupIndex] = v.getId();
                    if (navItems != null && groupIndex < navItems.length) {
                        navItems[groupIndex].setNextFocusRightId(v.getId());
                    }
                }
            });
        }
        if (view instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) view;
            for (int i = 0; i < container.getChildCount(); i++) {
                bindGroupNavigation(container.getChildAt(i), groupIndex, navigationId);
            }
        }
    }

    /** 右侧内容先返回其所属导航，左侧再返回则由 Activity 退出设置。 */
    public boolean focusNavigationFromContent() {
        if (groups == null || navItems == null) return false;
        for (int i = 0; i < groups.length; i++) {
            if (groups[i].hasFocus()) {
                return navItems[i].requestFocus();
            }
        }
        return false;
    }

    private void showGroup(int index) {
        for (int i = 0; i < groups.length; i++) {
            boolean active = (i == index);
            groups[i].setVisibility(active ? View.VISIBLE : View.GONE);
            navItems[i].setSelected(active);
            navItems[i].setContentDescription("设置分组：" + NAV_TITLES[i] + (active ? "，当前选中" : ""));
        }
        if (index == GROUP_DATA) {
            refreshDataGroupStats();
        }
        ScrollView scroll = findViewById(R.id.settingsScroll);
        if (scroll != null) scroll.scrollTo(0, 0);
    }

    private void bindTexts() {
        tvSearchSite.setText(getSearchSiteSummary());
        tvPlayTimeStep.setText(getPlayTimeStepSummary(Hawk.get(HawkConfig.PLAY_TIME_STEP, 5)));
        tvPlayTimeStart.setText(getPlayTimeSummary(Hawk.get(HawkConfig.PLAY_TIME_START, 0)));
        tvPlayTimeEnd.setText(getPlayTimeSummary(Hawk.get(HawkConfig.PLAY_TIME_END, 0)));
        tvAudioLanguage.setText(getLanguageSummary(Hawk.get(HawkConfig.AUDIO_LANGUAGE, "")));
        tvSubtitleLanguage.setText(getLanguageSummary(Hawk.get(HawkConfig.SUBTITLE_LANGUAGE, "")));
        tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
        tvApi.setText(Hawk.get(HawkConfig.API_URL, ApiConfig.DEFAULT_URL));
        tvLiveSource.setText("打开管理");
        tvDoh.setText(getDohName(Hawk.get(HawkConfig.DOH_URL, 0)));
        tvHomeRec.setText(getHomeRecName(Hawk.get(HawkConfig.HOME_REC, 0)));
        tvHomeLayout.setText(getHomeLayoutName(Hawk.get(HawkConfig.HOME_LAYOUT_STYLE, HawkConfig.HOME_LAYOUT_DEFAULT)));
        tvHomeMode.setText(Hawk.get(HawkConfig.HOME_LOW_POWER, false) ? "低压力首页" : "当前首页");
        tvCardStyle.setText(HomeCardMotion.current().label);
        tvSearchView.setText(getSearchView(Hawk.get(HawkConfig.SEARCH_VIEW, 1)));
        tvHomeApi.setText(ApiConfig.getInstance().getHomeBean().getName());
        tvScale.setText(PlayerHelper.getScaleName(Hawk.get(HawkConfig.PLAY_SCALE, HawkConfig.PLAY_SCALE_DEFAULT)));
        tvPlay.setText(PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, HawkConfig.PLAY_TYPE_DEFAULT)));
        tvRender.setText(PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, HawkConfig.PLAY_RENDER_DEFAULT)));
        refreshMediaCodecRow();
    }

    /** 开关类设置用胶囊开关显示状态，整行点击切换 */
    private void bindToggles() {
        View autoUpdateRow = findViewById(R.id.llAutoUpdate);
        switchAutoUpdate.setChecked(Hawk.get(HawkConfig.AUTO_UPDATE_CHECK, true));
        autoUpdateRow.setContentDescription("启动时自动检查更新，" + (switchAutoUpdate.isChecked() ? "已开启" : "已关闭"));
        autoUpdateRow.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            boolean enabled = !Hawk.get(HawkConfig.AUTO_UPDATE_CHECK, true);
            Hawk.put(HawkConfig.AUTO_UPDATE_CHECK, enabled);
            switchAutoUpdate.setChecked(enabled);
            v.setContentDescription("启动时自动检查更新，" + (enabled ? "已开启" : "已关闭"));
            v.announceForAccessibility(enabled ? "启动时自动检查更新已开启" : "启动时自动检查更新已关闭，仍可手动检查更新");
        });

        View remoteSoundRow = findViewById(R.id.llRemoteKeySound);
        switchRemoteKeySound.setChecked(Hawk.get(HawkConfig.REMOTE_KEY_SOUND, true));
        remoteSoundRow.setContentDescription("遥控器按键音，" + (switchRemoteKeySound.isChecked() ? "已开启" : "已关闭"));
        remoteSoundRow.setOnClickListener(v -> {
            boolean enabled = !switchRemoteKeySound.isChecked();
            Hawk.put(HawkConfig.REMOTE_KEY_SOUND, enabled);
            switchRemoteKeySound.setChecked(enabled);
            v.setContentDescription("遥控器按键音，" + (enabled ? "已开启" : "已关闭"));
            v.announceForAccessibility(enabled ? "遥控器按键音已开启" : "遥控器按键音已关闭");
            if (enabled) {
                RemoteKeySound.preload(v.getContext());
            }
        });

        View seniorRow = findViewById(R.id.llSeniorMode);
        switchSeniorMode.setChecked(SeniorMode.isEnabled());
        seniorRow.setContentDescription("长辈模式，" + (SeniorMode.isEnabled() ? "已开启" : "已关闭"));
        seniorRow.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            boolean enabled = !SeniorMode.isEnabled();
            Hawk.put(HawkConfig.SENIOR_MODE, enabled);
            switchSeniorMode.setChecked(enabled);
            v.setContentDescription("长辈模式，" + (enabled ? "已开启" : "已关闭"));
            View rootView = getActivity() != null
                    ? getActivity().findViewById(android.R.id.content) : getView();
            SeniorMode.apply(rootView);
            v.announceForAccessibility(enabled ? "长辈模式已开启" : "长辈模式已关闭");
        });

        // 字幕开关显示的是“显示字幕”，内部存储的是“关闭字幕”（subtitle_off）
        switchSubtitle.setChecked(!Hawk.get(HawkConfig.SUBTITLE_OFF, false));
        View subtitleRow = findViewById(R.id.llSubtitleOff);
        subtitleRow.setContentDescription("显示字幕，" + (switchSubtitle.isChecked() ? "已开启" : "已关闭"));
        subtitleRow.setOnClickListener(v -> {
            boolean showSubtitle = !switchSubtitle.isChecked();
            Hawk.put(HawkConfig.SUBTITLE_OFF, !showSubtitle);
            switchSubtitle.setChecked(showSubtitle);
            v.setContentDescription("显示字幕，" + (showSubtitle ? "已开启" : "已关闭"));
            v.announceForAccessibility(showSubtitle ? "字幕已开启" : "字幕已关闭");
        });

        findViewById(R.id.llDebug).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            boolean next = !Hawk.get(HawkConfig.DEBUG_OPEN, false);
            Hawk.put(HawkConfig.DEBUG_OPEN, next);
            tvDebugOpen.setText(next ? "已打开" : "已关闭");
        });
    }

    private void bindPlayback() {
        findViewById(R.id.llPlay).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<Integer> players = Arrays.asList(0, 1, 2);
            SelectDialog.pick(mActivity, "请选择默认播放器",
                    "点播默认推荐 Exo；如遇冷门格式播不出会自动回退 IJK",
                    players,
                    players.indexOf(Hawk.get(HawkConfig.PLAY_TYPE, HawkConfig.PLAY_TYPE_DEFAULT)),
                    new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                        @Override
                        public void click(Integer value, int pos) {
                            Hawk.put(HawkConfig.PLAY_TYPE, value);
                            tvPlay.setText(PlayerHelper.getPlayerName(value));
                            PlayerHelper.init();
                            refreshMediaCodecRow();
                        }

                        @Override
                        public String getDisplay(Integer val) {
                            return PlayerHelper.getPlayerName(val);
                        }

                        @Override
                        public String getSubDisplay(Integer val) {
                            if (val == HawkConfig.PLAY_TYPE_EXO) return "支持本地缓存回看，兼容性佳（推荐）";
                            if (val == HawkConfig.PLAY_TYPE_IJK) return "支持软硬解切换，适合特殊老片源";
                            return "调用系统原生播放器，轻量省内存";
                        }
                    });
        });

        findViewById(R.id.llMediaCodec).setOnClickListener(v -> {
            if (!isIjkPlayer()) {
                Toast.makeText(mActivity, "解码方式仅 IJK 播放器可用，请先在「播放器」中切换", Toast.LENGTH_SHORT).show();
                return;
            }
            FastClickCheckUtil.check(v);
            List<IJKCode> ijkCodes = ApiConfig.getInstance().getmIjkCodes();
            if (ijkCodes == null || ijkCodes.isEmpty()) return;
            int defaultPos = 0;
            String ijkSel = Hawk.get(HawkConfig.IJK_CODEC, HawkConfig.IJK_CODEC_DEFAULT);
            for (int j = 0; j < ijkCodes.size(); j++) {
                if (ijkSel.equals(ijkCodes.get(j).getName())) {
                    defaultPos = j;
                    break;
                }
            }
            SelectDialog.pick(mActivity, "请选择 IJK 解码方式", ijkCodes, defaultPos,
                    new SelectDialogAdapter.SelectDialogInterface<IJKCode>() {
                        @Override
                        public void click(IJKCode value, int pos) {
                            value.selected(true);
                            tvMediaCodec.setText(value.getName());
                        }

                        @Override
                        public String getDisplay(IJKCode val) {
                            return val.getName();
                        }
                    });
        });

        findViewById(R.id.llRender).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<Integer> renders = Arrays.asList(0, 1);
            SelectDialog.pick(mActivity, "请选择默认渲染方式", renders,
                    renders.indexOf(Hawk.get(HawkConfig.PLAY_RENDER, HawkConfig.PLAY_RENDER_DEFAULT)),
                    new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                        @Override
                        public void click(Integer value, int pos) {
                            Hawk.put(HawkConfig.PLAY_RENDER, value);
                            tvRender.setText(PlayerHelper.getRenderName(value));
                            PlayerHelper.init();
                        }

                        @Override
                        public String getDisplay(Integer val) {
                            return PlayerHelper.getRenderName(val);
                        }

                        @Override
                        public String getSubDisplay(Integer val) {
                            return val == 1
                                    ? "硬件直通叠加层，4K/高帧率更流畅（电视推荐）"
                                    : "兼容部分老旧设备画面缩放或裁剪异常";
                        }
                    });
        });

        findViewById(R.id.llScale).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<Integer> scales = Arrays.asList(0, 1, 2, 3, 4, 5);
            SelectDialog.pick(mActivity, "请选择默认画面比例", scales,
                    scales.indexOf(Hawk.get(HawkConfig.PLAY_SCALE, HawkConfig.PLAY_SCALE_DEFAULT)),
                    new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                        @Override
                        public void click(Integer value, int pos) {
                            Hawk.put(HawkConfig.PLAY_SCALE, value);
                            tvScale.setText(PlayerHelper.getScaleName(value));
                        }

                        @Override
                        public String getDisplay(Integer val) {
                            return PlayerHelper.getScaleName(val);
                        }
                    });
        });

        findViewById(R.id.llAudioLanguage).setOnClickListener(
                v -> showLanguageDialog(HawkConfig.AUDIO_LANGUAGE, tvAudioLanguage, "默认音轨语言"));
        findViewById(R.id.llSubtitleLanguage).setOnClickListener(
                v -> showLanguageDialog(HawkConfig.SUBTITLE_LANGUAGE, tvSubtitleLanguage, "默认字幕语言"));
        findViewById(R.id.llPlayTimeStart).setOnClickListener(
                v -> showPlayTimeDialog(HawkConfig.PLAY_TIME_START, tvPlayTimeStart, "片头跳过时间"));
        findViewById(R.id.llPlayTimeEnd).setOnClickListener(
                v -> showPlayTimeDialog(HawkConfig.PLAY_TIME_END, tvPlayTimeEnd, "片尾跳过时间"));
        findViewById(R.id.llPlayTimeStep).setOnClickListener(v -> showPlayTimeStepDialog());
    }

    private void bindHome() {
        findViewById(R.id.llHomeApi).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<SiteBean> sites = ApiConfig.getInstance().getSiteBeanList();
            if (NullUtil.isNull(sites)) return;
            if (sites.isEmpty() || !"".equals(sites.get(0).getKey())) {
                SiteBean auto = new SiteBean();
                auto.setKey("");
                auto.setName("自动选择");
                auto.setApi("api");
                auto.setSearchable(0);
                sites.add(0, auto);
            }
            SelectDialog.pick(mActivity, "请选择首页数据源",
                    "选择「自动选择」时将自动挑选响应最快的可用站点",
                    sites,
                    sites.indexOf(ApiConfig.getInstance().getHomeBean()),
                    new SelectDialogAdapter.SelectDialogInterface<SiteBean>() {
                        @Override
                        public void click(SiteBean value, int pos) {
                            ApiConfig.getInstance().setUserHomeBean(value);
                            tvHomeApi.setText(value.getName());
                        }

                        @Override
                        public String getDisplay(SiteBean val) {
                            return val.getName();
                        }
                    });
        });

        findViewById(R.id.llCardStyle).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            new HomeCardMotionDialog(mActivity, HomeCardMotion.current(), style -> {
                Hawk.put(HawkConfig.HOME_CARD_STYLE, style.name());
                tvCardStyle.setText(style.label);
                Toast.makeText(mActivity, "海报样式已切换为「" + style.label + "」", Toast.LENGTH_SHORT).show();
            }).show();
        });

        findViewById(R.id.llHomeMode).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<Boolean> modes = Arrays.asList(false, true);
            SelectDialog.pick(mActivity, "选择首页性能模式",
                    "返回首页后生效 · 性能较弱设备建议切换为低压力首页",
                    modes,
                    modes.indexOf(Hawk.get(HawkConfig.HOME_LOW_POWER, false)),
                    new SelectDialogAdapter.SelectDialogInterface<Boolean>() {
                        @Override
                        public void click(Boolean value, int pos) {
                            Hawk.put(HawkConfig.HOME_LOW_POWER, value);
                            tvHomeMode.setText(value ? "低压力首页" : "当前首页");
                        }

                        @Override
                        public String getDisplay(Boolean val) {
                            return val ? "低压力首页" : "当前首页";
                        }

                        @Override
                        public String getSubDisplay(Boolean val) {
                            return val ? "静态背景与手动切换，降低内存与 GPU 占用" : "完整背景过渡特效与自动轮播（默认）";
                        }
                    });
        });

        findViewById(R.id.llHomeRec).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<Integer> types = Arrays.asList(0, 1, 2);
            SelectDialog.pick(mActivity, "请选择首页推荐内容", types,
                    types.indexOf(Hawk.get(HawkConfig.HOME_REC, 0)),
                    new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                        @Override
                        public void click(Integer value, int pos) {
                            Hawk.put(HawkConfig.HOME_REC, value);
                            tvHomeRec.setText(getHomeRecName(value));
                        }

                        @Override
                        public String getDisplay(Integer val) {
                            return getHomeRecName(val);
                        }

                        @Override
                        public String getSubDisplay(Integer val) {
                            return getHomeRecDesc(val);
                        }
                    });
        });

        findViewById(R.id.llHomeLayout).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            int currentLayout = Hawk.get(HawkConfig.HOME_LAYOUT_STYLE, HawkConfig.HOME_LAYOUT_DEFAULT);
            new HomeLayoutDialog(mActivity, currentLayout, value -> {
                Hawk.put(HawkConfig.HOME_LAYOUT_STYLE, value);
                tvHomeLayout.setText(getHomeLayoutName(value));
                Toast.makeText(mActivity, "已切换为：" + getHomeLayoutName(value) + "（返回首页即生效）", Toast.LENGTH_SHORT).show();
            }).show();
        });

        findViewById(R.id.llSearchView).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<Integer> types = Arrays.asList(0, 1);
            SelectDialog.pick(mActivity, "请选择搜索结果展示方式", types,
                    types.indexOf(Hawk.get(HawkConfig.SEARCH_VIEW, 1)),
                    new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                        @Override
                        public void click(Integer value, int pos) {
                            Hawk.put(HawkConfig.SEARCH_VIEW, value);
                            tvSearchView.setText(getSearchView(value));
                        }

                        @Override
                        public String getDisplay(Integer val) {
                            return getSearchView(val);
                        }

                        @Override
                        public String getSubDisplay(Integer val) {
                            return val == 0 ? "紧凑文字列表，单屏展示更多结果" : "图文海报缩略图，观感更直观（默认）";
                        }
                    });
        });

        findViewById(R.id.llSearchSite).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            SearchSiteDialog dialog = new SearchSiteDialog(mActivity);
            dialog.setOnChanged(() -> tvSearchSite.setText(getSearchSiteSummary()));
            dialog.init();
            dialog.show();
        });
    }

    private void bindData() {
        findViewById(R.id.llApi).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            if (mApiDialog != null) {
                mApiDialog.dismiss();
                mApiDialog = null;
            }
            mApiDialog = new ApiDialog(mActivity);
            EventBus.getDefault().register(mApiDialog);
            mApiDialog.setOnListener(api -> {
                tvApi.setText(api);
                tvHomeApi.setText(ApiConfig.getInstance().getHomeBean().getName());
                tvSearchSite.setText(getSearchSiteSummary());
                refreshMediaCodecRow();
            });
            mApiDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (mActivity instanceof BaseActivity) {
                        ((BaseActivity) mActivity).hideSysBar();
                    }
                    if (EventBus.getDefault().isRegistered(dialog)) {
                        EventBus.getDefault().unregister(dialog);
                    }
                }
            });
            mApiDialog.show();
        });

        findViewById(R.id.llLiveSource).setOnClickListener(v ->
                startActivity(new Intent(mActivity, LiveSourceManagerActivity.class)));

        findViewById(R.id.llDoh).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<Integer> dohTypes = Arrays.asList(0, 1, 2, 3);
            int current = Hawk.get(HawkConfig.DOH_URL, 0);
            if (current < 0 || current >= dohTypes.size()) current = 0;
            SelectDialog.pick(mActivity, "安全 DNS (DoH)",
                    "当接口或图片域名解析异常时可尝试开启加密 DNS",
                    dohTypes, current,
                    new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                        @Override
                        public void click(Integer value, int pos) {
                            Hawk.put(HawkConfig.DOH_URL, value);
                            tvDoh.setText(getDohName(value));
                            Toast.makeText(mActivity, "安全 DNS 已设为：" + getDohName(value), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public String getDisplay(Integer val) {
                            return getDohName(val);
                        }

                        @Override
                        public String getSubDisplay(Integer val) {
                            if (val == 1) return "Tencent DNSPod (doh.pub)";
                            if (val == 2) return "AliDNS (dns.alidns.com)";
                            if (val == 3) return "360 安全 DNS (doh.360.cn)";
                            return "使用当前系统/路由器默认 DNS";
                        }
                    });
        });

        findViewById(R.id.llClearCache).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            tvClearCache.setText("清理中...");
            AppExecutors.io().execute(() -> {
                long freedBytes = 0;
                try {
                    File glideCacheDir = com.bumptech.glide.Glide.getPhotoCacheDir(requireContext());
                    if (glideCacheDir != null && glideCacheDir.exists()) {
                        freedBytes += getFolderSize(glideCacheDir);
                        com.bumptech.glide.Glide.get(requireContext()).clearDiskCache();
                    }
                } catch (Throwable ignored) {
                }
                try {
                    File videoCache = new File(requireContext().getCacheDir(), "video-cache");
                    if (videoCache.exists()) {
                        freedBytes += getFolderSize(videoCache);
                        FileUtils.recursiveDelete(videoCache);
                    }
                } catch (Throwable ignored) {
                }
                try {
                    CacheManager.clearAll();
                } catch (Throwable ignored) {
                }
                final long totalFreed = freedBytes;
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        try {
                            com.bumptech.glide.Glide.get(requireContext()).clearMemory();
                        } catch (Throwable ignored) {
                        }
                        tvClearCache.setText("已清理 · 0 MB");
                        String freedStr = formatBytes(totalFreed);
                        Toast.makeText(requireContext(), "缓存清理完成（释放 " + freedStr + "）", Toast.LENGTH_SHORT).show();
                        tvClearCache.postDelayed(() -> {
                            if (isAdded()) refreshDataGroupStats();
                        }, 2500L);
                    });
                }
            });
        });

        findViewById(R.id.llBackup).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            BackupDialog dialog = new BackupDialog(getActivity());
            dialog.setOnDismissListener(d -> refreshDataGroupStats());
            dialog.show();
        });
    }

    private void refreshDataGroupStats() {
        if (!isAdded() || getContext() == null) return;
        final android.content.Context appCtx = getContext().getApplicationContext();
        AppExecutors.io().execute(() -> {
            long cacheBytes = 0;
            try {
                File glideCacheDir = com.bumptech.glide.Glide.getPhotoCacheDir(appCtx);
                if (glideCacheDir != null && glideCacheDir.exists()) {
                    cacheBytes += getFolderSize(glideCacheDir);
                }
            } catch (Throwable ignored) {
            }
            try {
                File videoCache = new File(appCtx.getCacheDir(), "video-cache");
                if (videoCache.exists()) {
                    cacheBytes += getFolderSize(videoCache);
                }
            } catch (Throwable ignored) {
            }
            final int backupCount = BackupDialog.getBackupCount();
            final String cacheText = cacheBytes > 0 ? formatBytes(cacheBytes) : "暂无缓存";
            final String backupText = backupCount < 0 ? "本地备份"
                    : (backupCount == 0 ? "暂无备份" : backupCount + " 份本地备份");
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (tvClearCache != null && !"清理中...".equals(tvClearCache.getText().toString())) {
                        tvClearCache.setText(cacheText);
                    }
                    if (tvBackupSummary != null) {
                        tvBackupSummary.setText(backupText);
                    }
                });
            }
        });
    }

    private void bindAbout() {
        ((TextView) findViewById(R.id.versionTV)).setText(PhoneParams.getAppSelfVersionName(getContext()));
        findViewById(R.id.llAbout).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            UpdateManager.check(getActivity(), true);
        });

        final View easterEggTrigger = requireActivity().findViewById(R.id.settingEasterEggTrigger);
        final TextView easterEggTitle = requireActivity().findViewById(R.id.settingEasterEggTitle);
        if (easterEggTrigger != null && easterEggTitle != null) {
            easterEggTrigger.setOnClickListener(v -> handleEasterEggClick(v, easterEggTrigger, easterEggTitle));
        }
    }

    @Override
    public void onDestroyView() {
        UpdateManager.cancelFor(getActivity());
        super.onDestroyView();
    }

    private boolean isIjkPlayer() {
        return Hawk.get(HawkConfig.PLAY_TYPE, HawkConfig.PLAY_TYPE_DEFAULT) == HawkConfig.PLAY_TYPE_IJK;
    }

    /** 解码方式只对 IJK 播放器生效：非 IJK 时整行变淡并直接说明原因 */
    private void refreshMediaCodecRow() {
        View row = findViewById(R.id.llMediaCodec);
        if (row == null || tvMediaCodec == null) return;
        boolean ijk = isIjkPlayer();
        row.setAlpha(ijk ? 1f : 0.45f);
        String codec = Hawk.get(HawkConfig.IJK_CODEC, HawkConfig.IJK_CODEC_DEFAULT);
        tvMediaCodec.setText(ijk ? codec : "仅 IJK 播放器可用");
        row.setContentDescription(ijk ? "解码方式，" + codec : "解码方式，仅 IJK 播放器可用");
    }

    private String getDohName(int type) {
        if (type == 1) return "腾讯 DoH";
        if (type == 2) return "阿里 DoH";
        if (type == 3) return "360 DoH";
        return "关闭";
    }

    private String getLanguageSummary(String language) {
        return displayLanguage(language);
    }

    private String displayLanguage(String code) {
        if (code == null || code.trim().isEmpty()) return "自动选择";
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            if (LANGUAGE_CODES[i].equals(code)) return LANGUAGE_NAMES[i];
        }
        return code;
    }

    /** 语言改为列表选择，避免用户自行输入语言代码 */
    private void showLanguageDialog(String key, TextView summary, String title) {
        FastClickCheckUtil.check(summary);
        ArrayList<String> values = new ArrayList<>(Arrays.asList(LANGUAGE_CODES));
        String current = Hawk.get(key, "");
        if (current == null) current = "";
        if (!values.contains(current)) values.add(current);
        SelectDialog.pick(mActivity, title, values, values.indexOf(current),
                new SelectDialogAdapter.SelectDialogInterface<String>() {
                    @Override
                    public void click(String value, int pos) {
                        Hawk.put(key, value);
                        summary.setText(getLanguageSummary(value));
                    }

                    @Override
                    public String getDisplay(String value) {
                        return displayLanguage(value);
                    }
                });
    }

    private String getPlayTimeStepSummary(int step) {
        return step + " 秒";
    }

    private String getPlayTimeSummary(int seconds) {
        return seconds == 0 ? "关闭" : seconds + " 秒";
    }

    /** 片头片尾改为常用档位；当前值不在档位中（旧版自定义值）时补充进列表 */
    private void showPlayTimeDialog(String key, TextView summary, String title) {
        FastClickCheckUtil.check(summary);
        int[] presets = {0, 10, 20, 30, 45, 60, 90, 120, 180, 240, 300};
        ArrayList<Integer> values = new ArrayList<>();
        for (int seconds : presets) values.add(seconds);
        int current = Hawk.get(key, 0);
        if (!values.contains(current)) {
            values.add(current);
            Collections.sort(values);
        }
        SelectDialog.pick(mActivity, title, "选择 0 秒即关闭自动跳过", values, values.indexOf(current),
                new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(key, value);
                        summary.setText(getPlayTimeSummary(value));
                    }

                    @Override
                    public String getDisplay(Integer value) {
                        return getPlayTimeSummary(value);
                    }
                });
    }

    private void showPlayTimeStepDialog() {
        FastClickCheckUtil.check(tvPlayTimeStep);
        List<Integer> steps = Arrays.asList(5, 10, 15, 20, 30);
        int current = Hawk.get(HawkConfig.PLAY_TIME_STEP, 5);
        SelectDialog.pick(mActivity, "片头片尾调整步长", "在播放页微调片头/片尾时长时每次增减的秒数",
                steps, steps.indexOf(current),
                new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_TIME_STEP, value);
                        tvPlayTimeStep.setText(getPlayTimeStepSummary(value));
                    }

                    @Override
                    public String getDisplay(Integer value) {
                        return getPlayTimeStepSummary(value);
                    }
                });
    }

    String getHomeRecName(int type) {
        if (type == 1) {
            return "站点推荐";
        } else if (type == 2) {
            return "观看历史";
        } else {
            return "豆瓣推荐";
        }
    }

    String getHomeRecDesc(int type) {
        if (type == 1) {
            return "展示主页数据源最新更新与推荐剧集";
        } else if (type == 2) {
            return "快速续播历史观看记录，换集继续看";
        } else {
            return "精选豆瓣热门电影与热播电视剧榜单";
        }
    }

    String getHomeLayoutName(int layout) {
        switch (layout) {
            case HawkConfig.HOME_LAYOUT_SIDEBAR:
                return "经典侧边栏";
            case HawkConfig.HOME_LAYOUT_APPLE_TV:
                return "Apple TV 顶栏";
            case HawkConfig.HOME_LAYOUT_DRAWER:
                return "Netflix 动态抽屉";
            case HawkConfig.HOME_LAYOUT_IMMERSIVE:
                return "极简画报沉浸";
            default:
                return "极简画报沉浸";
        }
    }

    String getSearchView(int type) {
        return type == 0 ? "文字列表" : "缩略图";
    }

    /** 搜索源管理入口右侧摘要：全部 / 已选 x/y / 未勾选 */
    String getSearchSiteSummary() {
        int total = 0;
        int enabled = 0;
        for (SiteBean bean : ApiConfig.getInstance().getSiteBeanList()) {
            if (!bean.isSearchable()) continue;
            total++;
            if (ApiConfig.getInstance().isSearchSiteEnabled(bean.getKey())) {
                enabled++;
            }
        }
        if (total == 0) {
            return "无可用源";
        }
        if (enabled == 0) {
            return "未勾选";
        }
        if (enabled == total) {
            return "全部（" + total + "）";
        }
        return enabled + "/" + total;
    }

    private void handleEasterEggClick(View source, View trigger, TextView triggerTitle) {
        long now = System.currentTimeMillis();
        if (now - lastEasterEggClick > 2000L) easterEggClicks = 0;
        lastEasterEggClick = now;
        easterEggClicks++;
        source.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        if (easterEggClicks < 7) return;
        easterEggClicks = 0;
        playEasterEgg(trigger, triggerTitle);
    }

    private void playEasterEgg(View trigger, TextView triggerTitle) {
        AnimatorSet rowAnimation = new AnimatorSet();
        rowAnimation.playTogether(
                ObjectAnimator.ofFloat(trigger, View.SCALE_X, 1f, 1.04f, 1f),
                ObjectAnimator.ofFloat(trigger, View.SCALE_Y, 1f, 1.04f, 1f),
                ObjectAnimator.ofFloat(trigger, View.ROTATION_Y, 0f, 8f, -8f, 0f),
                ObjectAnimator.ofFloat(trigger, View.ALPHA, 1f, 0.72f, 1f)
        );
        rowAnimation.setDuration(900L);
        rowAnimation.start();

        AnimatorSet versionAnimation = new AnimatorSet();
        versionAnimation.playTogether(
                ObjectAnimator.ofFloat(triggerTitle, View.SCALE_X, 1f, 1.3f, 1f),
                ObjectAnimator.ofFloat(triggerTitle, View.SCALE_Y, 1f, 1.3f, 1f),
                ObjectAnimator.ofFloat(triggerTitle, View.ROTATION, 0f, 360f)
        );
        versionAnimation.setDuration(850L);
        versionAnimation.start();

        final Dialog dialog = new Dialog(requireContext());
        FrameLayout root = new FrameLayout(requireContext());
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(28), dp(24), dp(28), dp(20));
        GradientDrawable cardBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(28, 24, 64), Color.rgb(10, 49, 78)});
        cardBackground.setCornerRadius(dp(24));
        cardBackground.setStroke(dp(1), Color.argb(180, 102, 232, 255));
        card.setBackground(cardBackground);
        card.setElevation(dp(16));

        TextView mark = new TextView(requireContext());
        mark.setText("*  *  *");
        mark.setTextColor(Color.rgb(102, 232, 255));
        mark.setTextSize(18);
        mark.setGravity(Gravity.CENTER);
        card.addView(mark, new LinearLayout.LayoutParams(-1, dp(28)));

        TextView title = new TextView(requireContext());
        title.setText("小宗的隐藏彩蛋");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView message = new TextView(requireContext());
        message.setText("感谢你使用小宗开发的软件\n\n希望你天天开心，万事如意。\n一定要开心哦！！！");
        message.setTextColor(Color.rgb(226, 244, 255));
        message.setTextSize(16);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(0, 1.15f);
        card.addView(message, new LinearLayout.LayoutParams(-1, dp(104)));

        TextView close = new TextView(requireContext());
        close.setText("继续使用");
        close.setFocusable(true);
        close.setClickable(true);
        close.setTextColor(Color.rgb(10, 35, 55));
        close.setTextSize(15);
        close.setGravity(Gravity.CENTER);
        GradientDrawable closeBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(102, 232, 255), Color.rgb(173, 133, 255)});
        closeBackground.setCornerRadius(dp(22));
        close.setBackground(closeBackground);
        card.addView(close, new LinearLayout.LayoutParams(dp(148), dp(44)));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                dp(330), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(card, cardParams);

        root.setOnClickListener(v -> dialog.dismiss());
        card.setOnClickListener(v -> dialog.dismiss());
        close.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.72f;
            window.setAttributes(attributes);
        }
        dialog.show();
        close.requestFocus();

        card.setScaleX(0.72f);
        card.setScaleY(0.72f);
        card.setAlpha(0f);
        card.setRotation(-4f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f)
                .setDuration(520L).setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();
        mark.animate().alpha(0.25f).setDuration(700L).setStartDelay(300L)
                .withEndAction(() -> mark.animate().alpha(1f).setDuration(700L).start()).start();
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 MB";
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.0f KB", Math.max(1.0, bytes / 1024.0));
        }
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private long getFolderSize(File file) {
        long size = 0;
        if (file == null || !file.exists()) return 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    size += getFolderSize(child);
                }
            }
        } else {
            size += file.length();
        }
        return size;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
