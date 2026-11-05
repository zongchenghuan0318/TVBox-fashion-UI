package com.github.tvbox.osc.ui.fragment;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.ui.activity.LiveSourceManagerActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.dialog.ApiDialog;
import com.github.tvbox.osc.ui.dialog.BackupDialog;
import com.github.tvbox.osc.ui.dialog.SearchSiteDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.view.CapsuleSwitch;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;
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

/**
 * 设置页：左侧四个分组导航（播放 / 首页与搜索 / 数据与缓存 / 关于），右侧一次只显示当前分组。
 *
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class ModelSettingFragment extends BaseLazyFragment {
    private static final int GROUP_PLAY = 0;
    private static final int GROUP_HOME = 1;
    private static final int GROUP_DATA = 2;
    private static final int GROUP_ABOUT = 3;

    private static final String[] LANGUAGE_CODES = {"", "zh", "en", "ja", "ko", "fr", "de", "es"};
    private static final String[] LANGUAGE_NAMES = {"自动选择", "中文 zh", "英文 en", "日语 ja",
            "韩语 ko", "法语 fr", "德语 de", "西班牙语 es"};

    private View[] navItems;
    private View[] groups;
    private TextView tvDebugOpen;
    private TextView tvMediaCodec;
    private TextView tvPlay;
    private TextView tvRender;
    private TextView tvScale;
    private TextView tvApi;
    private TextView tvLiveSource;
    private TextView tvHomeApi;
    private TextView tvHomeMode;
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
    private CapsuleSwitch switchSubtitle;
    private CapsuleSwitch switchAutoUpdate;
    private CapsuleSwitch switchSeniorMode;
    private ApiDialog mApiDialog = null;
    private int easterEggClicks;
    private long lastEasterEggClick;

    public static ModelSettingFragment newInstance() {
        return new ModelSettingFragment().setArguments();
    }

    public ModelSettingFragment setArguments() {
        return this;
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
        SettingActivity.callback = new SettingActivity.DevModeCallback() {
            @Override
            public void onChange() {
                findViewById(R.id.llDebug).setVisibility(View.VISIBLE);
            }
        };
    }

    private void bindViews() {
        tvDebugOpen = findViewById(R.id.tvDebugOpen);
        tvMediaCodec = findViewById(R.id.tvMediaCodec);
        tvPlay = findViewById(R.id.tvPlay);
        tvRender = findViewById(R.id.tvRenderType);
        tvScale = findViewById(R.id.tvScaleType);
        tvApi = findViewById(R.id.tvApi);
        tvLiveSource = findViewById(R.id.tvLiveSource);
        tvHomeApi = findViewById(R.id.tvHomeApi);
        tvHomeMode = findViewById(R.id.tvHomeMode);
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
        switchSubtitle = findViewById(R.id.switchSubtitle);
        switchAutoUpdate = findViewById(R.id.switchAutoUpdate);
        switchSeniorMode = findViewById(R.id.switchSeniorMode);
        navItems = new View[]{findViewById(R.id.navPlay), findViewById(R.id.navHome),
                findViewById(R.id.navData), findViewById(R.id.navAbout)};
        groups = new View[]{findViewById(R.id.groupPlay), findViewById(R.id.groupHome),
                findViewById(R.id.groupData), findViewById(R.id.groupAbout)};
    }

    /** 左侧导航：焦点或点击切换分组，右侧只显示当前分组 */
    private void bindNavigation() {
        // 每个导航项的“向右”焦点明确指向本分组第一行，不依赖系统的焦点猜测
        int[] firstRows = {R.id.llPlay, R.id.llHomeApi, R.id.llApi, R.id.llAbout};
        for (int i = 0; i < navItems.length; i++) {
            final int index = i;
            navItems[i].setNextFocusRightId(firstRows[i]);
            navItems[i].setOnClickListener(v -> showGroup(index));
            navItems[i].setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) showGroup(index);
            });
        }
        showGroup(GROUP_PLAY);
    }

    private void showGroup(int index) {
        for (int i = 0; i < groups.length; i++) {
            groups[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            navItems[i].setSelected(i == index);
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
        tvMediaCodec.setText(Hawk.get(HawkConfig.IJK_CODEC, HawkConfig.IJK_CODEC_DEFAULT));
        tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
        tvApi.setText(Hawk.get(HawkConfig.API_URL, ApiConfig.DEFAULT_URL));
        tvLiveSource.setText("打开管理");
        tvHomeRec.setText(getHomeRecName(Hawk.get(HawkConfig.HOME_REC, 0)));
        tvHomeLayout.setText(getHomeLayoutName(Hawk.get(HawkConfig.HOME_LAYOUT_STYLE, HawkConfig.HOME_LAYOUT_DEFAULT)));
        tvHomeMode.setText(Hawk.get(HawkConfig.HOME_LOW_POWER, false) ? "低压力首页" : "当前首页");
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

        View seniorRow = findViewById(R.id.llSeniorMode);
        switchSeniorMode.setChecked(SeniorMode.isEnabled());
        seniorRow.setContentDescription("长辈模式，" + (SeniorMode.isEnabled() ? "已开启" : "已关闭"));
        seniorRow.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            boolean enabled = !SeniorMode.isEnabled();
            Hawk.put(HawkConfig.SENIOR_MODE, enabled);
            switchSeniorMode.setChecked(enabled);
            v.setContentDescription("长辈模式，" + (enabled ? "已开启" : "已关闭"));
            SeniorMode.apply(getView());
            v.announceForAccessibility(enabled ? "长辈模式已开启" : "长辈模式已关闭");
        });

        // 字幕开关显示的是“显示字幕”，内部存储的是“关闭字幕”（subtitle_off）
        switchSubtitle.setChecked(!Hawk.get(HawkConfig.SUBTITLE_OFF, false));
        findViewById(R.id.llSubtitleOff).setOnClickListener(v -> {
            boolean showSubtitle = !switchSubtitle.isChecked();
            Hawk.put(HawkConfig.SUBTITLE_OFF, !showSubtitle);
            switchSubtitle.setChecked(showSubtitle);
        });

        findViewById(R.id.llDebug).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            Hawk.put(HawkConfig.DEBUG_OPEN, !Hawk.get(HawkConfig.DEBUG_OPEN, false));
            tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
        });
    }

    private void bindPlayback() {
        findViewById(R.id.llPlay).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<Integer> players = Arrays.asList(0, 1, 2);
            SelectDialog.pick(mActivity, "请选择默认播放器", players,
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
                    });
        });

        findViewById(R.id.llMediaCodec).setOnClickListener(v -> {
            if (!isIjkPlayer()) {
                android.widget.Toast.makeText(mActivity, "解码方式仅 IJK 播放器可用，请先在「播放器」中切换", android.widget.Toast.LENGTH_SHORT).show();
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
            SelectDialog.pick(mActivity, "请选择IJK解码", ijkCodes, defaultPos,
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
                    });
        });

        findViewById(R.id.llScale).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<Integer> scales = Arrays.asList(0, 1, 2, 3, 4, 5);
            SelectDialog.pick(mActivity, "请选择默认画面缩放", scales,
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
            // 添加“自动选择”，避免弹窗重复打开时累加
            if (sites.isEmpty() || !"".equals(sites.get(0).getKey())) {
                SiteBean auto = new SiteBean();
                auto.setKey("");
                auto.setName("自动选择");
                auto.setApi("api");
                auto.setSearchable(0);
                sites.add(0, auto);
            }
            SelectDialog.pick(mActivity, "请选择首页数据源", sites,
                    sites.indexOf(ApiConfig.getInstance().getHomeBean()),
                    new SelectDialogAdapter.SelectDialogInterface<SiteBean>() {
                        @Override
                        public void click(SiteBean value, int pos) {
                            // 选中“自动选择”时相当于清掉了 USER_HOME_API
                            ApiConfig.getInstance().setUserHomeBean(value);
                            tvHomeApi.setText(value.getName());
                        }

                        @Override
                        public String getDisplay(SiteBean val) {
                            return val.getName();
                        }
                    });
        });

        TextView cardStyle = findViewById(R.id.tvCardStyle);
        cardStyle.setText(HomeCardMotion.current().label);
        findViewById(R.id.llCardStyle).setOnClickListener(v ->
                SelectDialog.pick(mActivity, "海报切换样式", Arrays.asList(HomeCardMotion.values()),
                        HomeCardMotion.current().ordinal(), new SelectDialogAdapter.SelectDialogInterface<HomeCardMotion>() {
                            @Override public void click(HomeCardMotion style, int pos) {
                                Hawk.put(HawkConfig.HOME_CARD_STYLE, style.name());
                                cardStyle.setText(style.label);
                            }
                            @Override public String getDisplay(HomeCardMotion style) { return style.label; }
                        }));
        findViewById(R.id.llHomeMode).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<Boolean> modes = Arrays.asList(false, true); // false 当前首页，true 低压力首页
            SelectDialog.pick(mActivity, "首页模式（返回首页后生效）", modes,
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
                            return val ? "静态背景、手动切换" : "完整背景特效与自动轮播";
                        }
                    });
        });

        findViewById(R.id.llHomeRec).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<Integer> types = Arrays.asList(0, 1, 2);
            SelectDialog.pick(mActivity, "请选择首页列表数据", types,
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
            List<Integer> layouts = Arrays.asList(HawkConfig.HOME_LAYOUT_SIDEBAR, HawkConfig.HOME_LAYOUT_APPLE_TV,
                    HawkConfig.HOME_LAYOUT_DRAWER, HawkConfig.HOME_LAYOUT_IMMERSIVE);
            SelectDialog.pick(mActivity, "请选择首页布局风格", layouts,
                    layouts.indexOf(Hawk.get(HawkConfig.HOME_LAYOUT_STYLE, HawkConfig.HOME_LAYOUT_DEFAULT)),
                    new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                        @Override
                        public void click(Integer value, int pos) {
                            Hawk.put(HawkConfig.HOME_LAYOUT_STYLE, value);
                            tvHomeLayout.setText(getHomeLayoutName(value));
                            android.widget.Toast.makeText(mActivity, "已切换为：" + getHomeLayoutName(value) + "（返回首页即生效）", android.widget.Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public String getDisplay(Integer val) {
                            return getHomeLayoutName(val);
                        }

                        @Override
                        public String getSubDisplay(Integer val) {
                            return getHomeLayoutDesc(val);
                        }
                    });
        });

        findViewById(R.id.llSearchView).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<Integer> types = Arrays.asList(0, 1);
            SelectDialog.pick(mActivity, "请选择搜索视图", types,
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
            mApiDialog.setOnListener(api -> tvApi.setText(api));
            mApiDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    ((BaseActivity) mActivity).hideSysBar();
                    EventBus.getDefault().unregister(dialog);
                }
            });
            mApiDialog.show();
        });

        findViewById(R.id.llLiveSource).setOnClickListener(v ->
                startActivity(new Intent(mActivity, LiveSourceManagerActivity.class)));

        findViewById(R.id.llClearCache).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            tvClearCache.setText("清理中...");
            com.github.tvbox.osc.util.AppExecutors.io().execute(() -> {
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
                        tvClearCache.setText("已清理");
                        // 几秒后恢复按钮文字，方便再次清理
                        tvClearCache.postDelayed(() -> tvClearCache.setText("一键释放"), 3000L);
                        String freedStr = totalFreed > 1024 * 1024
                                ? String.format(java.util.Locale.getDefault(), "%.1f MB", totalFreed / (1024.0 * 1024.0))
                                : "已释放空间";
                        android.widget.Toast.makeText(requireContext(), "缓存清理完成 (" + freedStr + ")", android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        findViewById(R.id.llBackup).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            new BackupDialog(getActivity()).show();
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
        easterEggTrigger.setOnClickListener(v -> handleEasterEggClick(v, easterEggTrigger, easterEggTitle));
    }

    @Override
    public void onDestroyView() {
        UpdateManager.cancelFor(getActivity());
        super.onDestroyView();
        SettingActivity.callback = null;
    }

    private boolean isIjkPlayer() {
        return Hawk.get(HawkConfig.PLAY_TYPE, HawkConfig.PLAY_TYPE_DEFAULT) == HawkConfig.PLAY_TYPE_IJK;
    }

    /** 解码方式只对 IJK 播放器生效：非 IJK 时整行变淡，点击给出说明 */
    private void refreshMediaCodecRow() {
        View row = findViewById(R.id.llMediaCodec);
        if (row == null) return;
        boolean ijk = isIjkPlayer();
        row.setAlpha(ijk ? 1f : 0.4f);
        row.setContentDescription(ijk ? "解码方式" : "解码方式，仅 IJK 播放器可用");
    }

    private String getLanguageSummary(String language) {
        return displayLanguage(language);
    }

    private String displayLanguage(String code) {
        if (code == null || code.trim().isEmpty()) return "自动选择";
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            if (LANGUAGE_CODES[i].equals(code)) return LANGUAGE_NAMES[i];
        }
        return code; // 旧版本自由输入的自定义代码，原样保留显示
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
        SelectDialog.pick(mActivity, title + "（0 秒为关闭）", values, values.indexOf(current),
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
        SelectDialog.pick(mActivity, "请选择片头片尾调整步长", steps, steps.indexOf(current),
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
                return "Apple TV 顶栏";
        }
    }

    String getHomeLayoutDesc(int layout) {
        switch (layout) {
            case HawkConfig.HOME_LAYOUT_SIDEBAR:
                return "经典左侧多级导航，分类切换直观稳定";
            case HawkConfig.HOME_LAYOUT_APPLE_TV:
                return "顶部磨砂玻璃导航，宽幅画报与平滑视差聚焦";
            case HawkConfig.HOME_LAYOUT_DRAWER:
                return "左侧悬浮动态收折，滑入展开分类，大屏视野开阔";
            case HawkConfig.HOME_LAYOUT_IMMERSIVE:
                return "全屏超大画报无干扰，静置自动轮播，按上键呼出顶栏";
            default:
                return null;
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
