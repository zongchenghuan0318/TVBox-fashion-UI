package com.github.tvbox.osc.ui.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.app.AlertDialog;
import android.app.Dialog;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.activity.LiveSourceManagerActivity;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.ApiDialog;
import com.github.tvbox.osc.ui.dialog.SearchSiteDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.PhoneParams;
import com.lcstudio.commonsurport.util.SystemUitl;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;
import com.uisupport.UiConstans;
import com.github.tvbox.osc.util.UpdateManager;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import okhttp3.HttpUrl;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class ModelSettingFragment extends BaseLazyFragment {
    private TextView tvDebugOpen;
    private TextView tvMediaCodec;
    private TextView tvParseWebView;
    private TextView tvPlay;
    private TextView tvRender;
    private TextView tvScale;
    private TextView tvApi;
    private TextView tvLiveSource;
    private TextView tvHomeApi;
    private TextView tvDns;
    private TextView tvHomeRec;
    private TextView tvSearchView;
    private TextView tvSearchSite;
    private TextView tvPlayTimeStep;
    private TextView tvPlayTimeStart;
    private TextView tvPlayTimeEnd;
    private TextView tvAudioLanguage;
    private TextView tvSubtitleLanguage;
    private TextView tvSubtitleOff;
    private ApiDialog mApiDialog =null;
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
        mApiDialog.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void init() {
        tvDebugOpen = findViewById(R.id.tvDebugOpen);
        tvParseWebView = findViewById(R.id.tvParseWebView);
        tvMediaCodec = findViewById(R.id.tvMediaCodec);
        tvPlay = findViewById(R.id.tvPlay);
        tvRender = findViewById(R.id.tvRenderType);
        tvScale = findViewById(R.id.tvScaleType);
        tvApi = findViewById(R.id.tvApi);
        tvLiveSource = findViewById(R.id.tvLiveSource);
        tvHomeApi = findViewById(R.id.tvHomeApi);
        tvDns = findViewById(R.id.tvDns);
        tvHomeRec = findViewById(R.id.tvHomeRec);
        tvSearchView = findViewById(R.id.tvSearchView);
        tvSearchSite = findViewById(R.id.tvSearchSite);
        tvPlayTimeStep = findViewById(R.id.tvPlayTimeStep);
        tvPlayTimeStart = findViewById(R.id.tvPlayTimeStart);
        tvPlayTimeEnd = findViewById(R.id.tvPlayTimeEnd);
        tvAudioLanguage = findViewById(R.id.tvAudioLanguage);
        tvSubtitleLanguage = findViewById(R.id.tvSubtitleLanguage);
        tvSubtitleOff = findViewById(R.id.tvSubtitleOff);
        tvSearchSite.setText(getSearchSiteSummary());
        tvPlayTimeStep.setText(getPlayTimeStepSummary(Hawk.get(HawkConfig.PLAY_TIME_STEP, 5)));
        tvPlayTimeStart.setText(getPlayTimeSummary(Hawk.get(HawkConfig.PLAY_TIME_START, 0)));
        tvPlayTimeEnd.setText(getPlayTimeSummary(Hawk.get(HawkConfig.PLAY_TIME_END, 0)));
        tvAudioLanguage.setText(getLanguageSummary(Hawk.get(HawkConfig.AUDIO_LANGUAGE, "")));
        tvSubtitleLanguage.setText(getLanguageSummary(Hawk.get(HawkConfig.SUBTITLE_LANGUAGE, "")));
        tvSubtitleOff.setText(Hawk.get(HawkConfig.SUBTITLE_OFF, false) ? "关闭" : "自动选择");
        final View aboutRow = findViewById(R.id.llAbout);
        final View easterEggTrigger = requireActivity().findViewById(R.id.settingEasterEggTrigger);
        final TextView easterEggTitle = requireActivity().findViewById(R.id.settingEasterEggTitle);
        easterEggTrigger.setOnClickListener(v -> handleEasterEggClick(v, easterEggTrigger, easterEggTitle));
        final TextView seniorModeState = findViewById(R.id.tvSeniorModeState);
        View seniorModeRow = findViewById(R.id.llSeniorMode);
        boolean seniorModeEnabled = com.github.tvbox.osc.util.SeniorMode.isEnabled();
        seniorModeState.setText(seniorModeEnabled ? "已开启" : "已关闭");
        seniorModeRow.setContentDescription(
                "长辈模式，" + (seniorModeEnabled ? "已开启" : "已关闭"));
        seniorModeRow.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                    View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Switch.class.getName());
                info.setCheckable(true);
                info.setChecked(com.github.tvbox.osc.util.SeniorMode.isEnabled());
            }
        });
        seniorModeRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean enabled = !com.github.tvbox.osc.util.SeniorMode.isEnabled();
                Hawk.put(HawkConfig.SENIOR_MODE, enabled);
                seniorModeState.setText(enabled ? "已开启" : "已关闭");
                v.setContentDescription("长辈模式，" + (enabled ? "已开启" : "已关闭"));
                com.github.tvbox.osc.util.SeniorMode.apply(getView());
                v.requestFocus();
                v.announceForAccessibility(enabled ? "长辈模式已开启" : "长辈模式已关闭");
            }
        });
        tvMediaCodec.setText(Hawk.get(HawkConfig.IJK_CODEC, ""));
        tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
        tvParseWebView.setText("系统自带");
        tvApi.setText(Hawk.get(HawkConfig.API_URL, ApiConfig.DEFAULT_URL));
        tvLiveSource.setText("打开管理");
        tvDns.setText(OkGoHelper.dnsHttpsList.get(Hawk.get(HawkConfig.DOH_URL, 0)));
        tvHomeRec.setText(getHomeRecName(Hawk.get(HawkConfig.HOME_REC, 0)));
        tvSearchView.setText(getSearchView(Hawk.get(HawkConfig.SEARCH_VIEW, 1)));
        tvHomeApi.setText(ApiConfig.getInstance().getHomeBean().getName());
        tvScale.setText(PlayerHelper.getScaleName(Hawk.get(HawkConfig.PLAY_SCALE, HawkConfig.PLAY_SCALE_DEFAULT)));
        tvPlay.setText(PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, HawkConfig.PLAY_TYPE_DEFAULT)));
        tvRender.setText(PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, 0)));
        findViewById(R.id.llDebug).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.DEBUG_OPEN, !Hawk.get(HawkConfig.DEBUG_OPEN, false));
                tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
            }
        });
        findViewById(R.id.llParseWebVew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.PARSE_WEBVIEW, true);
                tvParseWebView.setText("系统自带");
            }
        });
        findViewById(R.id.llBackup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                SystemUitl.share(getActivity(), "分享", "我发现个很好用的免费视频APP，官网:"+ UiConstans.GO_BROWSER_URL);
            }
        });
        aboutRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                UpdateManager.check(getActivity(), true);
            }
        });
        ((TextView) findViewById(R.id.versionTV)).setText(PhoneParams.getAppSelfVersionName(getContext()));
        findViewById(R.id.llHomeApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                List<SiteBean> sites = ApiConfig.getInstance().getSiteBeanList();
                if (!NullUtil.isNull(sites)) {
                    //添加自动选择
                    SiteBean auto= new SiteBean();
                    auto.setKey("");
                    auto.setName("自动选择");
                    auto.setApi("api");
                    auto.setSearchable(0);
                    sites.add(0, auto);

                    SelectDialog<SiteBean> dialog = new SelectDialog<>(mActivity);
                    dialog.setTip("请选择首页数据源");
                    dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SiteBean>() {
                        @Override
                        public void click(SiteBean value, int pos) {
                            //选中自动选择时，相当于清掉了USER_HOME_API
                            ApiConfig.getInstance().setUserHomeBean(value);
                            tvHomeApi.setText(value.getName());
                            dialog.dismiss();
                        }

                        @Override
                        public String getDisplay(SiteBean val) {
                            return val.getName();
                        }
                    }, new DiffUtil.ItemCallback<SiteBean>() {
                        @Override
                        public boolean areItemsTheSame(@NotNull SiteBean oldItem, @NotNull SiteBean newItem) {
                            return oldItem == newItem;
                        }

                        @Override
                        public boolean areContentsTheSame(@NotNull SiteBean oldItem, @NotNull SiteBean newItem) {
                            return oldItem.getKey().equals(newItem.getKey());
                        }
                    }, sites, sites.indexOf(ApiConfig.getInstance().getHomeBean()));
                    dialog.show();
                }
            }
        });
        findViewById(R.id.llDns).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int dohUrl = Hawk.get(HawkConfig.DOH_URL, 0);

                SelectDialog<String> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择安全DNS");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
                    @Override
                    public void click(String value, int pos) {
                        tvDns.setText(OkGoHelper.dnsHttpsList.get(pos));
                        Hawk.put(HawkConfig.DOH_URL, pos);
                        String url = OkGoHelper.getDohUrl(pos);
                        OkGoHelper.dnsOverHttps.setUrl(url.isEmpty() ? null : HttpUrl.get(url));
                        IjkMediaPlayer.toggleDotPort(pos > 0);
                    }

                    @Override
                    public String getDisplay(String val) {
                        return val;
                    }
                }, new DiffUtil.ItemCallback<String>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                        return oldItem.equals(newItem);
                    }
                }, OkGoHelper.dnsHttpsList, dohUrl);
                dialog.show();
            }
        });
        findViewById(R.id.llApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                if (mApiDialog != null) {
                    mApiDialog.dismiss();
                    mApiDialog = null;
                }
                mApiDialog = new ApiDialog(mActivity);
                EventBus.getDefault().register(mApiDialog);
                mApiDialog.setOnListener(new ApiDialog.OnListener() {
                    @Override
                    public void onchange(String api) {
                        tvApi.setText(api);
                    }
                });
                mApiDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        ((BaseActivity) mActivity).hideSysBar();
                        EventBus.getDefault().unregister(dialog);
                    }
                });
                mApiDialog.show();
            }
        });
        findViewById(R.id.llLiveSource).setOnClickListener(v ->
                startActivity(new Intent(mActivity, LiveSourceManagerActivity.class)));
        findViewById(R.id.llMediaCodec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<IJKCode> ijkCodes = ApiConfig.getInstance().getmIjkCodes();
                if (ijkCodes == null || ijkCodes.size() == 0)
                    return;
                FastClickCheckUtil.check(v);

                int defaultPos = 0;
                String ijkSel = Hawk.get(HawkConfig.IJK_CODEC, "");
                for (int j = 0; j < ijkCodes.size(); j++) {
                    if (ijkSel.equals(ijkCodes.get(j).getName())) {
                        defaultPos = j;
                        break;
                    }
                }

                SelectDialog<IJKCode> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择IJK解码");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<IJKCode>() {
                    @Override
                    public void click(IJKCode value, int pos) {
                        value.selected(true);
                        tvMediaCodec.setText(value.getName());
                    }

                    @Override
                    public String getDisplay(IJKCode val) {
                        return val.getName();
                    }
                }, new DiffUtil.ItemCallback<IJKCode>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull IJKCode oldItem, @NonNull @NotNull IJKCode newItem) {
                        return oldItem == newItem;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull IJKCode oldItem, @NonNull @NotNull IJKCode newItem) {
                        return oldItem.getName().equals(newItem.getName());
                    }
                }, ijkCodes, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llPlayTimeStep).setOnClickListener(v -> showPlayTimeStepDialog());
        findViewById(R.id.llPlayTimeStart).setOnClickListener(
                v -> showPlayTimeDialog(HawkConfig.PLAY_TIME_START, tvPlayTimeStart, "片头跳过时间"));
        findViewById(R.id.llPlayTimeEnd).setOnClickListener(
                v -> showPlayTimeDialog(HawkConfig.PLAY_TIME_END, tvPlayTimeEnd, "片尾跳过时间"));
        findViewById(R.id.llAudioLanguage).setOnClickListener(
                v -> showLanguageDialog(HawkConfig.AUDIO_LANGUAGE, tvAudioLanguage, "默认音轨语言"));
        findViewById(R.id.llSubtitleLanguage).setOnClickListener(
                v -> showLanguageDialog(HawkConfig.SUBTITLE_LANGUAGE, tvSubtitleLanguage, "默认字幕语言"));
        findViewById(R.id.llSubtitleOff).setOnClickListener(v -> {
            boolean off = !Hawk.get(HawkConfig.SUBTITLE_OFF, false);
            Hawk.put(HawkConfig.SUBTITLE_OFF, off);
            tvSubtitleOff.setText(off ? "关闭" : "自动选择");
        });
        findViewById(R.id.llScale).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_SCALE, HawkConfig.PLAY_SCALE_DEFAULT);
                ArrayList<Integer> players = new ArrayList<>();
                players.add(0);
                players.add(1);
                players.add(2);
                players.add(3);
                players.add(4);
                players.add(5);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认画面缩放");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_SCALE, value);
                        tvScale.setText(PlayerHelper.getScaleName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getScaleName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, players, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llPlay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_TYPE, HawkConfig.PLAY_TYPE_DEFAULT);
                ArrayList<Integer> players = new ArrayList<>();
                players.add(0);
                players.add(1);
                players.add(2);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认播放器");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_TYPE, value);
                        tvPlay.setText(PlayerHelper.getPlayerName(value));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getPlayerName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, players, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llRender).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_RENDER, 0);
                ArrayList<Integer> renders = new ArrayList<>();
                renders.add(0);
                renders.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认渲染方式");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
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
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            }
        });
        final TextView homeMode = findViewById(R.id.tvHomeMode);
        homeMode.setText(Hawk.get(HawkConfig.HOME_LOW_POWER, false) ? "低压力首页" : "当前首页");
        findViewById(R.id.llHomeMode).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            new androidx.appcompat.app.AlertDialog.Builder(mActivity)
                    .setTitle("首页模式（返回首页后生效）")
                    .setSingleChoiceItems(new String[]{"当前首页：完整背景特效与自动轮播", "低压力首页：静态背景、手动切换"},
                            Hawk.get(HawkConfig.HOME_LOW_POWER, false) ? 1 : 0,
                            (dialog, which) -> {
                                Hawk.put(HawkConfig.HOME_LOW_POWER, which == 1);
                                homeMode.setText(which == 1 ? "低压力首页" : "当前首页");
                                dialog.dismiss();
                            })
                    .setNegativeButton("取消", null)
                    .show();
        });
        findViewById(R.id.llHomeRec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HOME_REC, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择首页列表数据");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HOME_REC, value);
                        tvHomeRec.setText(getHomeRecName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getHomeRecName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llSearchView).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.SEARCH_VIEW, 1);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择搜索视图");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.SEARCH_VIEW, value);
                        tvSearchView.setText(getSearchView(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getSearchView(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llSearchSite).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                SearchSiteDialog dialog = new SearchSiteDialog(mActivity);
                dialog.setOnChanged(new SearchSiteDialog.OnChanged() {
                    @Override
                    public void onChanged() {
                        tvSearchSite.setText(getSearchSiteSummary());
                    }
                });
                dialog.init();
                dialog.show();
            }
        });
        SettingActivity.callback = new SettingActivity.DevModeCallback() {
            @Override
            public void onChange() {
                findViewById(R.id.llDebug).setVisibility(View.VISIBLE);
            }
        };
    }

    @Override
    public void onDestroyView() {
        UpdateManager.cancelFor(getActivity());
        super.onDestroyView();
        SettingActivity.callback = null;
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

    String getSearchView(int type) {
        if (type == 0) {
            return "文字列表";
        } else {
            return "缩略图";
        }
    }

    private void showLiveSourceDialog() {
        EditText input = new EditText(mActivity);
        input.setSingleLine(false);
        input.setMinLines(5);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("每行填写一个直播配置地址（可带端口）");
        String liveSources = getMigratedLiveSources();
        input.setText(liveSources);
        input.setSelection(input.length());

        new AlertDialog.Builder(new androidx.appcompat.view.ContextThemeWrapper(mActivity, R.style.DarkAlertDialog))
                .setTitle("设置直播源地址")
                .setMessage("每行一个地址，支持 M3U 和 TVBox JSON；多个源会同时合并显示。留空则使用总配置中的直播源。保存后下次进入直播页生效。")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    Hawk.put(HawkConfig.LIVE_SOURCE_URL, url);
                    tvLiveSource.setText(getLiveSourceSummary(url));
                })
                .setNeutralButton("清除", (dialog, which) -> {
                    Hawk.put(HawkConfig.LIVE_SOURCE_URL, "");
                    tvLiveSource.setText("使用总配置");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String getMigratedLiveSources() {
        String saved = Hawk.get(HawkConfig.LIVE_SOURCE_URL, "");
        StringBuilder result = new StringBuilder();
        String firstSource = "193.123.86.190:14888/TV/iptv.php";
        result.append(firstSource);
        if (saved != null) {
            for (String line : saved.split("\\r?\\n")) {
                String value = line.trim();
                if (value.isEmpty()
                        || value.equals("https://raw.githubusercontent.com/Kimentanm/aptv/master/m3u/iptv.m3u")
                        || value.equals(firstSource)
                        || result.toString().contains(value)) {
                    continue;
                }
                if (result.length() > 0) result.append('\n');
                result.append(value);
            }
        }
        for (String line : HawkConfig.DEFAULT_LIVE_SOURCE_URLS.split("\\r?\\n")) {
            String value = line.trim();
            if (value.isEmpty() || result.toString().contains(value)) continue;
            if (result.length() > 0) result.append('\n');
            result.append(value);
        }
        String migrated = result.toString();
        Hawk.put(HawkConfig.LIVE_SOURCE_URL, migrated);
        return migrated;
    }

    private String getLiveSourceSummary(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "使用默认";
        }
        return url.trim();
    }

    private String getPlayTimeStepSummary(int step) {
        return step + " 秒";
    }

    private String getPlayTimeSummary(int seconds) {
        return seconds == 0 ? "关闭" : seconds + " 秒";
    }

    private String getLanguageSummary(String language) {
        return language == null || language.trim().isEmpty() ? "自动选择" : language;
    }

    private void showLanguageDialog(String key, TextView summary, String title) {
        EditText input = new EditText(mActivity);
        input.setSingleLine(true);
        input.setHint("例如 zh、en、ja；留空为自动选择");
        input.setText(Hawk.get(key, ""));
        input.setSelection(input.length());
        new AlertDialog.Builder(new androidx.appcompat.view.ContextThemeWrapper(
                mActivity, R.style.DarkAlertDialog))
                .setTitle(title)
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                    Hawk.put(key, value);
                    summary.setText(getLanguageSummary(value));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showPlayTimeDialog(String key, TextView summary, String title) {
        FastClickCheckUtil.check(summary);
        ArrayList<Integer> values = new ArrayList<>();
        values.add(0);
        for (int seconds = 5; seconds <= 600; seconds += 5) values.add(seconds);
        int current = Hawk.get(key, 0);
        int defaultPos = values.indexOf(current);
        if (defaultPos < 0) defaultPos = 0;
        SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
        dialog.setTip(title + "（0 秒为关闭）");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
            @Override
            public void click(Integer value, int pos) {
                Hawk.put(key, value);
                summary.setText(getPlayTimeSummary(value));
                dialog.dismiss();
            }

            @Override
            public String getDisplay(Integer value) {
                return getPlayTimeSummary(value);
            }
        }, new DiffUtil.ItemCallback<Integer>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem,
                                           @NonNull @NotNull Integer newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem,
                                              @NonNull @NotNull Integer newItem) {
                return oldItem.equals(newItem);
            }
        }, values, defaultPos);
        dialog.show();
    }

    private void showPlayTimeStepDialog() {
        FastClickCheckUtil.check(tvPlayTimeStep);
        ArrayList<Integer> steps = new ArrayList<>();
        steps.add(5);
        steps.add(10);
        steps.add(15);
        steps.add(20);
        steps.add(30);
        int current = Hawk.get(HawkConfig.PLAY_TIME_STEP, 5);
        int defaultPos = steps.indexOf(current);
        if (defaultPos < 0) defaultPos = 0;

        SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
        dialog.setTip("请选择片头片尾调整步长");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
            @Override
            public void click(Integer value, int pos) {
                Hawk.put(HawkConfig.PLAY_TIME_STEP, value);
                tvPlayTimeStep.setText(getPlayTimeStepSummary(value));
                dialog.dismiss();
            }

            @Override
            public String getDisplay(Integer value) {
                return getPlayTimeStepSummary(value);
            }
        }, new DiffUtil.ItemCallback<Integer>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem,
                                           @NonNull @NotNull Integer newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem,
                                              @NonNull @NotNull Integer newItem) {
                return oldItem.equals(newItem);
            }
        }, steps, defaultPos);
        dialog.show();
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
