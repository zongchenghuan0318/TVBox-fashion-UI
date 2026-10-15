package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
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
import com.github.tvbox.osc.ui.activity.UiHelper;
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
import com.lcstudio.commonsurport.util.UIUtil;
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
    private ImageView qrCodeImg;
    private TextView tvAddress;
    private EditText apiEdit;
    private EditText etContent;
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
        etContent = findViewById(R.id.et_content);
        String api = Hawk.get(HawkConfig.API_URL);
        if (NullUtil.isNull(api)) {
            apiEdit.setHint(ApiConfig.DEFAULT_URL);
        } else {
            apiEdit.setText(api);
            apiEdit.setSelection(api.length());
        }
        new Thread() {
            @Override
            public void run() {
                super.run();
                String content = mSpDataUtil.getStringValue(ApiConfig.PRE_KEY_CONFIG, "");
                if (!NullUtil.isNull(content)) {
                    mAct.runOnUiThread(() -> etContent.setText(content));
                }
            }
        }.start();

        initClick();
        refreshQRCode();
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
                            etContent.setText(content);
                            if (!NullUtil.isNull(content) && !UiHelper.isJson(content)) {
                                UIUtil.showToast(getContext(), "配置文件存在错误！");
                            }
                        });
                    }
                });
            }
        }
    }

    String url ="";
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
        findViewById(R.id.tv_clear).setOnClickListener(v -> {
            //清空
            etContent.setText("");
        });

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
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    url = apiEdit.getText().toString();
                    L.d("apiEdit url=" + url);
                    String r = "";
                    if (url.trim().startsWith("http")) {
                        //配置接口按 UA 分流，必须用 okhttp UA 才拿到 JSON 配置体
                        r = ApiConfig.getWithOkhttpUA(url);
                    } else if (url.trim().startsWith("clan")) {
                        url = UiHelper.clanToAddress(url);
                        L.d("url=" + url);
                        r = ApiConfig.getWithOkhttpUA(url);
                    } else {
                        r = FileUtil.readFile(url);
                    }
                    String finalR = r;
                    mAct.runOnUiThread(() -> {
                        if (!NullUtil.isNull(finalR)) {
                            etContent.setText(finalR);
                            if (!UiHelper.isJson(finalR)) {
                                UIUtil.showToast(getContext(), "配置内容json格式错误!");
                            }
                        } else {
                            UIUtil.showToast(getContext(), "网络或接口不通!");
                            etContent.setText("");
                        }
                    });
                }
            }.start();
        });

        findViewById(R.id.inputSubmit).setOnClickListener(v -> {
            //确定，保存
            dismiss();
            String newApi = apiEdit.getText().toString();
            if (!newApi.isEmpty()) {
                ArrayList<String> historyList = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
                if (!historyList.contains(newApi)) {
                    historyList.add(0, newApi);
                }
                if (historyList.size() > 10) {
                    historyList.remove(10);
                }
                Hawk.put(HawkConfig.API_HISTORY, historyList);
            } else {
                //UIUtil.showToast(getContext(), "配置地址不能为空");
            }
            mListener.onchange(newApi);
            Hawk.put(HawkConfig.API_URL, newApi); //空也保存

            String content = etContent.getText().toString();
            mSpDataUtil.saveStringValue(ApiConfig.PRE_KEY_CONFIG, content);
        });

        findViewById(R.id.apiHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
                if (history.isEmpty())
                    return;
                String current = Hawk.get(HawkConfig.API_URL, "");
                int idx = 0;
                if (history.contains(current))
                    idx = history.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip("历史配置列表");
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String value) {
                        apiEdit.setText(value);
                        mListener.onchange(value);
                        dialog.dismiss();
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        Hawk.put(HawkConfig.API_HISTORY, data);
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
