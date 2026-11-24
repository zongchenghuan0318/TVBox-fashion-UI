package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.ui.tv.widget.SafeTvLayoutManagers;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.SpiderActionHelper;
import com.lcstudio.commonsurport.L;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.studio.osc.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 通用爬虫 Action 弹窗：支持网盘扫码二维码、图片预览、Token/Cookie 单行与多行输入、
 * 选项菜单、消息提示与超时倒计时，视觉与 Z 影视设置/配置弹窗保持统一。
 */
public class SpiderActionDialog extends BaseDialog {

    private static final class OptionEntry {
        final String name;
        final String action;
        final String value;

        OptionEntry(String name, String action, String value) {
            this.name = name;
            this.action = action;
            this.value = value;
        }
    }

    private final Activity activity;
    private final String sourceKey;
    private final JSONObject actionObj;
    private final String fallbackTitle;
    private final Runnable onCompleted;

    private final TextView tvTitle;
    private final TextView tvCountdown;
    private final View llQrPane;
    private final ImageView ivQr;
    private final ProgressBar pbQr;
    private final TextView tvQrCaption;
    private final View llContentPane;
    private final TextView tvMsg;
    private final ScrollView svForm;
    private final LinearLayout llInputs;
    private final TvRecyclerView rvOptions;
    private final View llButtons;
    private final TextView btnReset;
    private final TextView btnCancel;
    private final TextView btnConfirm;

    private final LinkedHashMap<String, EditText> inputFields = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int remainingSeconds;
    private boolean keepOpen;
    private String actionId;
    private String singleInputKey;

    private final Runnable countdownTick = new Runnable() {
        @Override
        public void run() {
            if (!isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
            remainingSeconds--;
            if (remainingSeconds <= 0) {
                dismiss();
                return;
            }
            tvCountdown.setText("剩余 " + remainingSeconds + " 秒");
            handler.postDelayed(this, 1000L);
        }
    };

    public SpiderActionDialog(@NonNull Activity activity, @NonNull String sourceKey,
                              @NonNull JSONObject actionObj, String fallbackTitle,
                              @Nullable Runnable onCompleted) {
        super(activity, R.style.CustomDialogStyleDim);
        this.activity = activity;
        this.sourceKey = sourceKey;
        this.actionObj = actionObj;
        this.fallbackTitle = fallbackTitle;
        this.onCompleted = onCompleted;

        setContentView(R.layout.dialog_spider_action);
        setCanceledOnTouchOutside(false);

        tvTitle = findViewById(R.id.tvActionTitle);
        tvCountdown = findViewById(R.id.tvActionCountdown);
        llQrPane = findViewById(R.id.llActionQrPane);
        ivQr = findViewById(R.id.ivActionQr);
        pbQr = findViewById(R.id.pbActionQr);
        tvQrCaption = findViewById(R.id.tvActionQrCaption);
        llContentPane = findViewById(R.id.llActionContentPane);
        tvMsg = findViewById(R.id.tvActionMsg);
        svForm = findViewById(R.id.svActionForm);
        llInputs = findViewById(R.id.llActionInputs);
        rvOptions = findViewById(R.id.rvActionOptions);
        llButtons = findViewById(R.id.llActionButtons);
        btnReset = findViewById(R.id.btnActionReset);
        btnCancel = findViewById(R.id.btnActionCancel);
        btnConfirm = findViewById(R.id.btnActionConfirm);

        bindContent();
    }

    private void bindContent() {
        actionId = actionObj.optString("actionId", "").trim();
        keepOpen = actionObj.optBoolean("keep", false);
        String type = actionObj.optString("type", "").trim();
        String title = actionObj.optString("title", "").trim();
        if (TextUtils.isEmpty(title)) {
            title = TextUtils.isEmpty(fallbackTitle) ? "网盘扫码登录" : fallbackTitle;
        }
        tvTitle.setText(title);

        String msg = actionObj.optString("msg", "").trim();
        if (TextUtils.isEmpty(msg)) {
            msg = actionObj.optString("desc", "").trim();
        }
        if (TextUtils.isEmpty(msg)) {
            msg = actionObj.optString("text", "").trim();
        }
        if (!TextUtils.isEmpty(msg)) {
            tvMsg.setText(msg);
            tvMsg.setVisibility(View.VISIBLE);
        }

        // 1. 二维码 / 图片区
        String qrText = firstNonEmpty(
                actionObj.optString("qrcode", ""),
                actionObj.optString("qr", ""),
                actionObj.optString("qrCode", ""));
        String imageUrl = actionObj.optString("imageUrl", "").trim();
        String webUrl = actionObj.optString("url", "").trim();
        if (TextUtils.isEmpty(qrText) && ("webview".equalsIgnoreCase(type) || "url".equalsIgnoreCase(type))
                && !TextUtils.isEmpty(webUrl)) {
            qrText = SpiderActionHelper.resolveLanUrl(webUrl);
        }
        boolean hasQrOrImage = !TextUtils.isEmpty(qrText) || !TextUtils.isEmpty(imageUrl);
        if (hasQrOrImage) {
            llQrPane.setVisibility(View.VISIBLE);
            bindQrOrImage(qrText, imageUrl);
            if (TextUtils.isEmpty(msg) && !TextUtils.isEmpty(qrText)) {
                tvMsg.setText("请使用手机对应网盘 App 或浏览器扫描左侧二维码完成登录，完成后点击「确定」即可生效。");
                tvMsg.setVisibility(View.VISIBLE);
            }
        }

        // 2. 选项列表（menu / select）
        JSONArray optionsArr = actionObj.optJSONArray("option");
        boolean isMultiInput = "multiInput".equalsIgnoreCase(type) || "multiInputX".equalsIgnoreCase(type);
        boolean isEdit = "edit".equalsIgnoreCase(type) || "input".equalsIgnoreCase(type);
        boolean isMenu = ("menu".equalsIgnoreCase(type) || "select".equalsIgnoreCase(type))
                || (!isMultiInput && !isEdit && optionsArr != null && optionsArr.length() > 0);

        if (isMultiInput && optionsArr != null && optionsArr.length() > 0) {
            svForm.setVisibility(View.VISIBLE);
            for (int i = 0; i < optionsArr.length(); i++) {
                JSONObject item = optionsArr.optJSONObject(i);
                if (item == null) continue;
                String fieldId = item.optString("id", "value_" + i).trim();
                String fieldName = item.optString("name", "").trim();
                String fieldTip = item.optString("tip", item.optString("hint", "")).trim();
                String fieldValue = item.optString("value", "").trim();
                addInputField(fieldId, fieldName, fieldTip, fieldValue, i > 0);
            }
        } else if (isEdit) {
            svForm.setVisibility(View.VISIBLE);
            singleInputKey = actionObj.optString("id", "value").trim();
            if (singleInputKey.isEmpty()) singleInputKey = "value";
            String tip = actionObj.optString("tip", actionObj.optString("hint", "请输入内容或扫码获取")).trim();
            String value = actionObj.optString("value", "").trim();
            String label = actionObj.optString("label", "").trim();
            addInputField(singleInputKey, label, tip, value, false);
        } else if (isMenu && optionsArr != null && optionsArr.length() > 0) {
            bindOptionMenu(optionsArr);
        }

        // 若右侧内容区完全空白（纯二维码弹窗），补充一句友好指引，视觉更平衡
        if (tvMsg.getVisibility() != View.VISIBLE
                && svForm.getVisibility() != View.VISIBLE
                && rvOptions.getVisibility() != View.VISIBLE) {
            if (hasQrOrImage) {
                tvMsg.setText("请使用手机扫码完成操作，完成后按确定键确认或返回关闭。");
                tvMsg.setVisibility(View.VISIBLE);
            } else {
                llContentPane.setVisibility(View.GONE);
            }
        }

        // 3. 按钮配置（兼容 TVBox button 字段：0=确定+取消, 1=仅确定, 2=仅取消, 3=重置+取消+确定）
        int buttonMode = actionObj.optInt("button", 0);
        if (isMenu && !actionObj.has("button")) {
            // 菜单选择场景默认只保留取消/关闭按钮
            buttonMode = 2;
        }
        String okText = actionObj.optString("okText", "确定").trim();
        String cancelText = actionObj.optString("cancelText", buttonMode == 2 ? "关闭" : "取消").trim();
        btnConfirm.setText(TextUtils.isEmpty(okText) ? "确定" : okText);
        btnCancel.setText(TextUtils.isEmpty(cancelText) ? "取消" : cancelText);

        btnReset.setVisibility(buttonMode == 3 ? View.VISIBLE : View.GONE);
        btnConfirm.setVisibility(buttonMode == 2 ? View.GONE : View.VISIBLE);
        btnCancel.setVisibility(buttonMode == 1 ? View.GONE : View.VISIBLE);

        btnCancel.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            dismiss();
            if (onCompleted != null) onCompleted.run();
        });

        btnReset.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            for (EditText edit : inputFields.values()) {
                edit.setText("");
            }
            submitAction(true);
        });

        btnConfirm.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            submitAction(false);
        });

        // 4. 倒计时（timeout 秒）
        int timeout = actionObj.optInt("timeout", 0);
        if (timeout > 0) {
            remainingSeconds = timeout;
            tvCountdown.setText("剩余 " + remainingSeconds + " 秒");
            tvCountdown.setVisibility(View.VISIBLE);
        }
    }

    private void bindQrOrImage(String qrText, String imageUrl) {
        final int qrSize = Math.max(220, AutoSizeUtils.mm2px(activity, 220));
        if (!TextUtils.isEmpty(qrText)) {
            String resolved = SpiderActionHelper.resolveLanUrl(qrText);
            renderQrBitmap(resolved, qrSize);
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
                tvQrCaption.setText(resolved);
                tvQrCaption.setVisibility(View.VISIBLE);
            } else {
                tvQrCaption.setText("请用手机对应 App 扫描二维码");
                tvQrCaption.setVisibility(View.VISIBLE);
            }
            return;
        }

        String resolvedImg = SpiderActionHelper.resolveLanUrl(imageUrl);
        if (resolvedImg.startsWith("data:image/") && resolvedImg.contains(",")) {
            try {
                String base64Part = resolvedImg.substring(resolvedImg.indexOf(',') + 1);
                byte[] decoded = Base64.decode(base64Part, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                if (bmp != null) {
                    ivQr.setImageBitmap(bmp);
                    tvQrCaption.setText("请用手机扫描上方二维码");
                    tvQrCaption.setVisibility(View.VISIBLE);
                    return;
                }
            } catch (Throwable th) {
                L.w("decode base64 qr failed: " + th.getMessage());
            }
        }

        if (resolvedImg.startsWith("http://") || resolvedImg.startsWith("https://")) {
            pbQr.setVisibility(View.VISIBLE);
            tvQrCaption.setText("二维码加载中…");
            tvQrCaption.setVisibility(View.VISIBLE);
            Glide.with(activity)
                    .load(resolvedImg)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    Target<Drawable> target, boolean isFirstResource) {
                            pbQr.setVisibility(View.GONE);
                            // 若该 URL 实际为扫码登录网页链接而非图片，自动转为二维码展示
                            renderQrBitmap(resolvedImg, qrSize);
                            tvQrCaption.setText(resolvedImg);
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model,
                                                       Target<Drawable> target, DataSource dataSource,
                                                       boolean isFirstResource) {
                            pbQr.setVisibility(View.GONE);
                            tvQrCaption.setText("请用手机对应 App 扫描二维码");
                            return false;
                        }
                    })
                    .into(ivQr);
        } else {
            renderQrBitmap(resolvedImg, qrSize);
            tvQrCaption.setText("请用手机扫描上方二维码");
            tvQrCaption.setVisibility(View.VISIBLE);
        }
    }

    private void renderQrBitmap(String content, int sizePx) {
        Bitmap bmp = QRCodeGen.generateBitmap(content, sizePx, sizePx, 2);
        if (bmp != null) {
            ivQr.setImageBitmap(bmp);
        }
    }

    private void addInputField(String key, String label, String hint, String value, boolean addTopSpace) {
        if (!TextUtils.isEmpty(label)) {
            TextView tvLabel = new TextView(activity);
            tvLabel.setText(label);
            tvLabel.setTextColor(activity.getResources().getColor(R.color.settings_text_secondary));
            tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, AutoSizeUtils.mm2px(activity, 14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (addTopSpace) lp.topMargin = AutoSizeUtils.mm2px(activity, 12);
            lp.bottomMargin = AutoSizeUtils.mm2px(activity, 6);
            llInputs.addView(tvLabel, lp);
        }

        EditText edit = new EditText(activity);
        edit.setBackgroundResource(R.drawable.input_dialog_api_input);
        edit.setTextColor(activity.getResources().getColor(R.color.color_FFFFFF));
        edit.setHintTextColor(activity.getResources().getColor(R.color.settings_text_muted));
        edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, AutoSizeUtils.mm2px(activity, 16));
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        edit.setGravity(Gravity.CENTER_VERTICAL);
        int padH = AutoSizeUtils.mm2px(activity, 14);
        edit.setPadding(padH, 0, padH, 0);
        if (!TextUtils.isEmpty(hint)) edit.setHint(hint);
        if (!TextUtils.isEmpty(value)) {
            edit.setText(value);
            edit.setSelection(value.length());
        }
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AutoSizeUtils.mm2px(activity, 44));
        if (addTopSpace && TextUtils.isEmpty(label)) {
            editLp.topMargin = AutoSizeUtils.mm2px(activity, 10);
        }
        llInputs.addView(edit, editLp);
        inputFields.put(key, edit);
    }

    private void bindOptionMenu(JSONArray optionsArr) {
        List<OptionEntry> items = new ArrayList<>();
        for (int i = 0; i < optionsArr.length(); i++) {
            JSONObject opt = optionsArr.optJSONObject(i);
            if (opt == null) continue;
            String name = opt.optString("name", "").trim();
            String act = opt.optString("action", "").trim();
            String val = opt.optString("value", act).trim();
            if (TextUtils.isEmpty(name)) name = "选项 " + (i + 1);
            items.add(new OptionEntry(name, act, val));
        }
        if (items.isEmpty()) return;

        rvOptions.setVisibility(View.VISIBLE);
        rvOptions.setLayoutManager(SafeTvLayoutManagers.linear(activity, 1, false));
        OptionAdapter adapter = new OptionAdapter();
        adapter.setNewData(items);
        rvOptions.setAdapter(adapter);
        adapter.setOnItemClickListener((adp, view, pos) -> {
            FastClickCheckUtil.check(view);
            if (pos < 0 || pos >= items.size()) return;
            onOptionPicked(items.get(pos));
        });
        rvOptions.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (position >= 0 && position < items.size()) {
                    onOptionPicked(items.get(position));
                }
            }
        });
    }

    private void onOptionPicked(OptionEntry entry) {
        if (!keepOpen) dismiss();
        if (!TextUtils.isEmpty(entry.action)) {
            SpiderActionHelper.executeAction(activity, sourceKey, entry.action, entry.name, onCompleted);
            return;
        }
        if (!TextUtils.isEmpty(actionId)) {
            try {
                JSONObject resp = new JSONObject();
                resp.put("actionId", actionId);
                resp.put("value", entry.value);
                SpiderActionHelper.invokeSpiderAction(activity, sourceKey, resp.toString(), entry.name, onCompleted);
                return;
            } catch (Throwable ignored) {
            }
        }
        if (onCompleted != null) onCompleted.run();
    }

    private void submitAction(boolean reset) {
        if (TextUtils.isEmpty(actionId)) {
            if (!keepOpen) dismiss();
            if (onCompleted != null) onCompleted.run();
            return;
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("actionId", actionId);
            if (reset) {
                payload.put("reset", true);
            }
            if (inputFields.size() == 1 && !TextUtils.isEmpty(singleInputKey)) {
                EditText only = inputFields.get(singleInputKey);
                String val = only != null && only.getText() != null ? only.getText().toString().trim() : "";
                payload.put(singleInputKey, val);
                if (!"value".equals(singleInputKey)) {
                    payload.put("value", val);
                }
            } else {
                for (Map.Entry<String, EditText> entry : inputFields.entrySet()) {
                    String val = entry.getValue().getText() == null ? "" : entry.getValue().getText().toString().trim();
                    payload.put(entry.getKey(), val);
                }
            }
            if (!keepOpen) dismiss();
            SpiderActionHelper.invokeSpiderAction(activity, sourceKey, payload.toString(), tvTitle.getText().toString(), onCompleted);
        } catch (Throwable th) {
            L.w("submitAction error: " + th.getMessage());
            Toast.makeText(activity, "提交失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void show() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        super.show();
        if (remainingSeconds > 0) {
            handler.removeCallbacks(countdownTick);
            handler.postDelayed(countdownTick, 1000L);
        }
        String initAction = actionObj.optString("initAction", "").trim();
        if (!TextUtils.isEmpty(initAction)) {
            SpiderActionHelper.invokeSpiderAction(activity, sourceKey, initAction, tvTitle.getText().toString(), null);
        }
        if (rvOptions.getVisibility() == View.VISIBLE) {
            rvOptions.post(() -> {
                if (isShowing()) {
                    rvOptions.setSelection(0);
                    rvOptions.requestFocus();
                }
            });
        } else if (btnConfirm.getVisibility() == View.VISIBLE) {
            btnConfirm.post(() -> {
                if (isShowing()) btnConfirm.requestFocus();
            });
        } else if (btnCancel.getVisibility() == View.VISIBLE) {
            btnCancel.post(() -> {
                if (isShowing()) btnCancel.requestFocus();
            });
        }
    }

    @Override
    public void dismiss() {
        handler.removeCallbacksAndMessages(null);
        super.dismiss();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private static final class OptionAdapter extends BaseQuickAdapter<OptionEntry, BaseViewHolder> {
        OptionAdapter() {
            super(R.layout.item_action_option, new ArrayList<>());
        }

        @Override
        protected void convert(BaseViewHolder helper, OptionEntry item) {
            helper.setText(R.id.tvActionOptionName, item.name);
        }
    }
}
