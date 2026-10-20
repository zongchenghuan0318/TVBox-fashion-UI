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
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 描述
 *
 * @author pj567
 * @since 2020/12/27
 */
public class ApiDialog extends BaseDialog {
    /** 校验中提示色 */
    private static final int STATUS_INFO = 0xFF333333;
    /** 校验成功提示色 */
    private static final int STATUS_OK = 0xFF2E7D32;
    /** 校验失败提示色 */
    private static final int STATUS_FAIL = 0xFFD32F2F;

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
    private SPDataUtil mSpDataUtil;
    private Activity mAct;
    private OnListener mListener = null;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_API_URL_CHANGE) {
            apiEdit.setText((String) event.obj);
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
                final ApiConfig.VerifyResult result = ApiConfig.verifyConfig(configAddress, "");
                mAct.runOnUiThread(() -> onVerifyDone(configAddress, result));
            }
        }.start();
    }

    private void onVerifyDone(String newApi, ApiConfig.VerifyResult result) {
        setVerifying(false);
        if (result.ok) {
            showStatus("✓ " + result.message, STATUS_OK);
            saveConfig(newApi, result.json);
            mHandler.postDelayed(() -> {
                if (isShowing()) dismiss();
            }, 800);
        } else {
            showStatus("✗ 校验失败：" + result.message, STATUS_FAIL);
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
        Hawk.put(HawkConfig.API_URL, api); //空也保存
        mSpDataUtil.saveStringValue(ApiConfig.PRE_KEY_CONFIG, content);
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
                    ApiConfig.VerifyResult result = ApiConfig.verifyConfig(address, "");
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

        findViewById(R.id.apiHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> history = Hawk.get(HawkConfig.API_SAVED_CONFIGS, new ArrayList<String>());
                if (history.isEmpty()) return;
                String current = Hawk.get(HawkConfig.API_URL, "");
                int idx = history.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip("已保存配置 · 点击立即切换");
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String value) {
                        apiEdit.setText(value);
                        dialog.dismiss();
                        verifyAndApply(value);
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        Hawk.put(HawkConfig.API_SAVED_CONFIGS, data);
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
        tvAddress.setText(String.format("描二维码或浏览器访问：%s", address));
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
