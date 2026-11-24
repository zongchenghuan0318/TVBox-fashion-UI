package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.HawkConfig;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.FileUtil;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.SPDataUtil;
import com.lzy.okgo.OkGo;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;
import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 描述
 *
 * @author pj567
 * @since 2020/12/27
 */
public class ApiDialog extends BaseDialog {
    /** 校验中提示色（适配深色磨砂底板） */
    private static final int STATUS_INFO = 0xFFE2E8F0;
    /** 校验成功提示色 */
    private static final int STATUS_OK = 0xFF34D399;
    /** 校验失败提示色 */
    private static final int STATUS_FAIL = 0xFFFF6B6B;

    private ImageView qrCodeImg;
    private TextView tvAddress;
    private TextView tvVerifyStatus;
    private EditText apiEdit;
    private View btnSubmit;
    private View btnCancel;
    private View btnTest;
    private View btnFile;
    private View btnSaved;
    private View btnSaveAddress;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    /** 正在校验配置：期间禁止任何方式退出对话框 */
    private boolean mVerifying = false;
    private volatile ApiConfig mActiveCandidate = null;
    private SPDataUtil mSpDataUtil;
    private Activity mAct;
    private OnListener mListener = null;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_API_URL_CHANGE) {
            String url = (String) event.obj;
            if (url != null && apiEdit != null) {
                apiEdit.setText(url);
                apiEdit.setSelection(url.length());
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAct != null && (mAct.isFinishing() || mAct.isDestroyed())) {
            cancelPreparedCandidate(mActiveCandidate);
            mActiveCandidate = null;
        }
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    public ApiDialog(@NonNull Activity context) {
        super(context);
        mAct = context;
        setContentView(R.layout.dialog_api);
        setCanceledOnTouchOutside(false);
        mSpDataUtil = new SPDataUtil(getContext());
        qrCodeImg = findViewById(R.id.ivQRCode);
        tvAddress = findViewById(R.id.tvAddress);
        apiEdit = findViewById(R.id.ed_input);
        tvVerifyStatus = findViewById(R.id.tvVerifyStatus);
        btnSubmit = findViewById(R.id.inputSubmit);
        btnCancel = findViewById(R.id.storagePermission);
        btnTest = findViewById(R.id.tv_test_http);
        btnFile = findViewById(R.id.file_open_tv);
        btnSaved = findViewById(R.id.apiHistory);
        btnSaveAddress = findViewById(R.id.apiSave);
        ensureDefaultSavedConfigs();
        String api = Hawk.get(HawkConfig.API_URL);
        if (NullUtil.isNull(api)) {
            apiEdit.setHint(ApiConfig.DEFAULT_URL);
        } else {
            apiEdit.setText(api);
            apiEdit.setSelection(api.length());
        }
        initClick();
        refreshQRCode();
        if (btnSaved != null) {
            btnSaved.post(btnSaved::requestFocus);
        }
    }

    /**
     * 校验配置期间一律不允许退出对话框：屏蔽返回键、取消、点击外部
     */
    @Override
    public void dismiss() {
        if (mVerifying) {
            return;
        }
        super.dismiss();
    }

    @Override
    public void cancel() {
        if (mVerifying) {
            return;
        }
        super.cancel();
    }

    @Override
    public void onBackPressed() {
        if (mVerifying) {
            return;
        }
        super.onBackPressed();
    }

    private void setVerifying(boolean verifying) {
        mVerifying = verifying;
        //setCancelable(false) 从框架层拦掉返回键/取消
        setCancelable(!verifying);
        setBtnEnabled(btnSubmit, !verifying);
        setBtnEnabled(btnCancel, !verifying);
        setBtnEnabled(btnTest, !verifying);
        setBtnEnabled(btnFile, !verifying);
        setBtnEnabled(btnSaved, !verifying);
        setBtnEnabled(btnSaveAddress, !verifying);
    }

    private void setBtnEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.5f);
    }

    private void showStatus(String text, int color) {
        if (tvVerifyStatus == null) return;
        tvVerifyStatus.setVisibility(View.VISIBLE);
        tvVerifyStatus.setText(text);
        tvVerifyStatus.setTextColor(color);
    }

    /**
     * 点「确定」先校验配置：校验中不可退出，校验通过才保存并关闭，失败则留在弹窗里显示具体原因
     */
    private void verifyAndSave() {
        verifyAndApply(apiEdit.getText().toString().trim());
    }

    private void verifyAndApply(String newApi) {
        if (mVerifying) return;
        final String configAddress = normalizeConfigAddress(newApi);
        if (NullUtil.isNull(configAddress)) {
            //地址与内容都为空：沿用原逻辑，保存空配置（等价清空配置）
            saveConfig(configAddress, "");
            dismiss();
            return;
        }
        setVerifying(true);
        showStatus("正在校验配置，请稍候…", STATUS_INFO);
        new Thread() {
            @Override
            public void run() {
                super.run();
                final ApiConfig.VerifyResult result = ApiConfig.verifyConfigWithRetry(configAddress, "", 0);
                mAct.runOnUiThread(() -> onVerifyDone(configAddress, result));
            }
        }.start();
    }

    private void onVerifyDone(String newApi, ApiConfig.VerifyResult result) {
        if (!result.ok) {
            setVerifying(false);
            showStatus("校验失败：" + result.message, STATUS_FAIL);
            return;
        }
        showStatus("正在准备首页内容，请稍候…", STATUS_INFO);
        final AtomicBoolean finished = new AtomicBoolean();
        final AtomicReference<ApiConfig> candidateRef = new AtomicReference<>();
        final long recDeadline = android.os.SystemClock.elapsedRealtime() + 26000L;
        Runnable timeout = () -> {
            if (!finished.compareAndSet(false, true)) return;
            cancelPreparedCandidate(candidateRef.get());
            mActiveCandidate = null;
            setVerifying(false);
            showStatus("首页准备超时，保留原配置，请稍后重试", STATUS_FAIL);
        };
        mHandler.postDelayed(timeout, 35000);
        new Thread(() -> {
            try {
                ApiConfig candidate = ApiConfig.prepareConfiguration(newApi, result.json);
                candidateRef.set(candidate);
                mActiveCandidate = candidate;
                if (finished.get()) {
                    cancelPreparedCandidate(candidate);
                    return;
                }
                candidate.initHomeBean(true, () -> mHandler.post(() -> {
                    if (finished.get() || mAct.isFinishing() || mAct.isDestroyed()) {
                        cancelPreparedCandidate(candidate);
                        return;
                    }
                    com.github.tvbox.osc.viewmodel.SourceViewModel model =
                            new androidx.lifecycle.ViewModelProvider((androidx.lifecycle.ViewModelStoreOwner) mAct)
                                    .get(com.github.tvbox.osc.viewmodel.SourceViewModel.class);
                    List<SiteBean> candidates = candidate.getHomeRecCandidates();
                    tryPrepareHomeRec(candidate, model, candidates, 0, recDeadline,
                            finished, timeout, newApi, result.json);
                }));
            } catch (Throwable error) {
                mHandler.post(() -> {
                    if (!finished.compareAndSet(false, true)) return;
                    mHandler.removeCallbacks(timeout);
                    cancelPreparedCandidate(candidateRef.get());
                    mActiveCandidate = null;
                    setVerifying(false);
                    String msg = error.getMessage();
                    showStatus("准备失败，保留原配置：" + (NullUtil.isNull(msg) ? error.getClass().getSimpleName() : msg), STATUS_FAIL);
                });
            }
        }, "prepare-config-switch").start();
    }

    private void tryPrepareHomeRec(ApiConfig candidate,
                                   com.github.tvbox.osc.viewmodel.SourceViewModel model,
                                   List<SiteBean> candidates,
                                   int index,
                                   long recDeadline,
                                   AtomicBoolean finished,
                                   Runnable timeout,
                                   String newApi,
                                   String configJson) {
        if (finished.get() || mAct.isFinishing() || mAct.isDestroyed()) {
            cancelPreparedCandidate(candidate);
            return;
        }
        boolean budgetExhausted = index > 0 && android.os.SystemClock.elapsedRealtime() >= recDeadline;
        if (candidates == null || index >= candidates.size() || budgetExhausted) {
            if (!finished.compareAndSet(false, true)) return;
            mHandler.removeCallbacks(timeout);
            cancelPreparedCandidate(candidate);
            mActiveCandidate = null;
            setVerifying(false);
            if (mAct.isFinishing() || mAct.isDestroyed()) return;
            // 若新配置已经探测到可用分类站点（如纯盘搜/仅提供分类的源），仍允许完成切换，
            // 首页的豆瓣/热榜兜底链路会保证展示内容，不因无站点推荐列表而误拒健康配置。
            if (candidate.hasVerifiedHomeBean()) {
                ApiConfig.getInstance().commitPrepared(candidate, Collections.emptyList());
                saveConfig(newApi, configJson);
                Toast.makeText(mAct, "切换成功，首页配置已准备好", Toast.LENGTH_SHORT).show();
                if (isShowing()) dismiss();
                return;
            }
            showStatus("新源暂时无法提供首页内容，保留原配置，请重试或选择其他源", STATUS_FAIL);
            return;
        }
        SiteBean site = candidates.get(index);
        model.getHomeRecList(candidate, site, null, videos -> mHandler.post(() -> {
            if (finished.get() || mAct.isFinishing() || mAct.isDestroyed()) {
                cancelPreparedCandidate(candidate);
                return;
            }
            if (videos != null && !videos.isEmpty()) {
                if (!finished.compareAndSet(false, true)) return;
                mHandler.removeCallbacks(timeout);
                mActiveCandidate = null;
                setVerifying(false);
                if (!candidate.hasVerifiedHomeBean() || index > 0) {
                    candidate.setHomeBean(site);
                    candidate.markHomeProbeVerified();
                }
                ApiConfig.getInstance().commitPrepared(candidate, videos);
                saveConfig(newApi, configJson);
                Toast.makeText(mAct, "切换成功，首页内容已准备好", Toast.LENGTH_SHORT).show();
                if (isShowing()) dismiss();
                return;
            }
            tryPrepareHomeRec(candidate, model, candidates, index + 1, recDeadline,
                    finished, timeout, newApi, configJson);
        }));
    }

    private void cancelPreparedCandidate(ApiConfig candidate) {
        if (candidate == null) return;
        candidate.cancelHomeInit();
        try {
            OkGo.getInstance().cancelTag(candidate);
        } catch (Throwable ignored) {
        }
    }

    private void saveConfig(String api, String content) {
        if (!NullUtil.isNull(api)) {
            ArrayList<String> historyList = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
            if (!historyList.contains(api)) {
                historyList.add(0, api);
            }
            if (historyList.size() > 10) {
                historyList.remove(10);
            }
            Hawk.put(HawkConfig.API_HISTORY, historyList);
            addSavedConfig(api);
        }
        if (mListener != null) {
            mListener.onchange(api);
        }
        ApiConfig.getInstance().invalidateStartupConfigCache(mSpDataUtil);
        Hawk.put(HawkConfig.API_URL, api); //空也保存
        mSpDataUtil.saveStringValue(ApiConfig.PRE_KEY_CONFIG, content);
        EventBus.getDefault().post(new RefreshEvent(
                RefreshEvent.TYPE_API_CONFIG_CHANGED, api));
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 1) {
                Uri uri = data.getData();
                if (uri == null) return;
                uriToFilePath(uri, mAct, new MyFileCallback() {
                    @Override
                    public void saveOk(String path, String content) {
                        mAct.runOnUiThread(() -> {
                            apiEdit.setText(path);
                            apiEdit.setSelection(path.length());
                            showStatus("已选择本地配置文件，点击“应用配置”完成校验", STATUS_INFO);
                        });
                    }
                });
            }
        }
    }

    public void uriToFilePath(Uri uri, Context context, MyFileCallback callback ) {
        new Thread(){
            @Override
            public void run() {
                super.run();
                File file = null;
                L.d("uri.getScheme()="+uri.getScheme());
                if (uri.getScheme().equals(ContentResolver.SCHEME_FILE)) {
                    file = new File(uri.getPath());
                } else if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
                    //把文件复制到沙盒目录
                    ContentResolver contentResolver = context.getContentResolver();
                    String extend = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri));
                    String cacheFileName = System.currentTimeMillis() + "." + extend;
                    try {
                        InputStream is = contentResolver.openInputStream(uri);
                        File cache = new File(context.getCacheDir().getAbsolutePath(), cacheFileName);
                        FileUtil.saveAsFile(is, cache.getAbsolutePath());
                        file = cache;
                        is.close();
                    } catch (Exception e) {
                        L.e(e);
                    }
                }
                String content = FileUtil.readFile(file.getAbsolutePath());
                callback.saveOk(file.getAbsolutePath(), content);
            }
        }.start();
    }

    private void initClick() {
        findViewById(R.id.file_open_tv).setOnClickListener(v -> {
            //打开文件
            if (XXPermissions.isGranted(getContext(), Permission.Group.STORAGE)) {
                startActivityContent();
            } else {
                XXPermissions.with(getContext())
                        .permission(Permission.Group.STORAGE)
                        .request(new OnPermissionCallback() {
                            @Override
                            public void onGranted(List<String> permissions, boolean all) {
                                if (all) {
                                    Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                                    startActivityContent();
                                }
                            }

                            @Override
                            public void onDenied(List<String> permissions, boolean never) {
                                if (never) {
                                    Toast.makeText(getContext(), "获取存储权限失败,请在系统设置中开启", Toast.LENGTH_SHORT).show();
                                    XXPermissions.startPermissionActivity((Activity) getContext(), permissions);
                                } else {
                                    Toast.makeText(getContext(), "获取存储权限失败", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });


        findViewById(R.id.tv_test_http).setOnClickListener(v -> {
            String address = normalizeConfigAddress(apiEdit.getText().toString().trim());
            if (NullUtil.isNull(address)) {
                showStatus("请先输入配置地址", STATUS_FAIL);
                return;
            }
            showStatus("正在测试配置源，请稍候…", STATUS_INFO);
            setVerifying(true);
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    ApiConfig.VerifyResult result = ApiConfig.verifyConfigWithRetry(address, "", 0);
                    mAct.runOnUiThread(() -> {
                        setVerifying(false);
                        showStatus((result.ok ? "✓ " : "✗ ") + result.message,
                                result.ok ? STATUS_OK : STATUS_FAIL);
                    });
                }
            }.start();
        });

        btnSaveAddress.setOnClickListener(v -> {
            String address = apiEdit.getText().toString().trim();
            if (NullUtil.isNull(address)) {
                showStatus("请先输入配置地址", STATUS_FAIL);
                return;
            }
            addSavedConfig(address);
            apiEdit.setText(normalizeConfigAddress(address));
            showStatus("已保存配置地址，可从“已保存配置”中直接切换", STATUS_OK);
        });

        //确定：先校验配置，校验通过才保存并允许退出
        btnSubmit.setOnClickListener(v -> verifyAndSave());

        findViewById(R.id.btn_clear_input).setOnClickListener(v -> {
            if (mVerifying) return;
            apiEdit.setText("");
            apiEdit.requestFocus();
            showStatus("已清空地址，请输入新的配置源或从「配置库」选择", STATUS_INFO);
        });

        findViewById(R.id.apiHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> history = Hawk.get(HawkConfig.API_SAVED_CONFIGS, new ArrayList<String>());
                if (history.isEmpty()) return;
                String current = Hawk.get(HawkConfig.API_URL, "");
                int idx = history.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip("配置库（点播源管理）");
                dialog.setSubtitle("点击切换配置 · 置顶配置将在启动时最先尝试");
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String value) {
                        apiEdit.setText(value);
                        dialog.dismiss();
                        verifyAndApply(value);
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        if (value.equals(ApiConfig.getPinnedConfig())) {
                            Hawk.put(HawkConfig.API_PINNED_CONFIG, "");
                        }
                        Hawk.put(HawkConfig.API_SAVED_CONFIGS, data);
                    }

                    @Override
                    public void pin(String value, ArrayList<String> data) {
                        if (value.equals(ApiConfig.getPinnedConfig())) {
                            // 再次点击即取消置顶，顺序保持不变
                            Hawk.put(HawkConfig.API_PINNED_CONFIG, "");
                        } else {
                            // 置顶：放到已保存列表的第一位，并记下置顶地址
                            data.remove(value);
                            data.add(0, value);
                            Hawk.put(HawkConfig.API_PINNED_CONFIG, value);
                            Hawk.put(HawkConfig.API_SAVED_CONFIGS, data);
                        }
                    }
                }, history, idx);
                dialog.show();
            }
        });
        //取消按钮
        findViewById(R.id.storagePermission).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    private void ensureDefaultSavedConfigs() {
        ArrayList<String> saved = Hawk.get(HawkConfig.API_SAVED_CONFIGS, new ArrayList<String>());
        ArrayList<String> normalized = new ArrayList<>();
        for (String address : saved) {
            String normalizedAddress = normalizeConfigAddress(address);
            if (!NullUtil.isNull(normalizedAddress) && !normalized.contains(normalizedAddress)) {
                normalized.add(normalizedAddress);
            }
        }
        saved = normalized;
        for (String address : HawkConfig.DEFAULT_API_SAVED_CONFIGS) {
            if (!saved.contains(address)) saved.add(address);
        }
        Hawk.put(HawkConfig.API_SAVED_CONFIGS, saved);
    }

    private void addSavedConfig(String address) {
        address = normalizeConfigAddress(address);
        ArrayList<String> saved = Hawk.get(HawkConfig.API_SAVED_CONFIGS, new ArrayList<String>());
        saved.remove(address);
        saved.add(0, address);
        Hawk.put(HawkConfig.API_SAVED_CONFIGS, saved);
    }

    private String normalizeConfigAddress(String address) {
        if (NullUtil.isNull(address)) return address == null ? "" : address.trim();
        String normalized = address.trim();
        if (normalized.contains("://") || normalized.contains("clan") || new File(normalized).exists()) {
            return normalized;
        }
        return "http://" + normalized;
    }

    private void startActivityContent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (intent.resolveActivity(mAct.getPackageManager()) != null) {
            mAct.startActivityForResult(intent, 1);
        }
//        else {
//            intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
//            mAct.startActivityForResult(intent, 1);
//        }
    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(String.format("扫描二维码或浏览器访问：%s", address));
        Bitmap bmp = QRCodeGen.generateBitmap(address, AutoSizeUtils.mm2px(getContext(), 300), AutoSizeUtils.mm2px(getContext(), 300));
        if (null != bmp) {
            qrCodeImg.setImageBitmap(bmp);
        }
    }

    public void setOnListener(OnListener listener) {
        this.mListener = listener;
    }

    public interface OnListener {
        void onchange(String api);
    }
    public interface MyFileCallback {
        void saveOk(String path, String content);
    }
}
