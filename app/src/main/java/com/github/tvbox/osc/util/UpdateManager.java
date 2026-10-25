package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.github.tvbox.osc.ui.dialog.UpdateDialog;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.BuildConfig;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** One foreground update session, owned by the page that started it. */
public final class UpdateManager {
    public static final int INSTALL_PERMISSION_REQUEST = 4201;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static UpdateManager active;
    private final Activity activity;
    private final Lifecycle lifecycle;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LifecycleEventObserver observer = (owner, event) -> onLifecycle(event);
    private UpdateClient client;
    private UpdateDialog dialog;
    private UpdateClient.Info info;
    private File apk;
    private boolean busy;
    private boolean waitingPermission;
    private boolean closed;
    private Runnable pendingResult;

    private UpdateManager(Activity activity) {
        this.activity = activity;
        lifecycle = ((LifecycleOwner) activity).getLifecycle();
    }

    public static void check(Activity activity, boolean manual) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (active != null) {
            if (manual) Toast.makeText(activity, "正在检查或下载更新，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        long now = System.currentTimeMillis();
        long last = Hawk.get(HawkConfig.UPDATE_LAST_CHECK, 0L);
        if (!manual && now >= last && now - last < TimeUnit.HOURS.toMillis(24)) return;
        Hawk.put(HawkConfig.UPDATE_LAST_CHECK, now);
        UpdateManager session = new UpdateManager(activity);
        active = session;
        session.lifecycle.addObserver(session.observer);
        session.startCheck(manual);
    }

    public static void cancelFor(Activity activity) {
        if (active != null && active.activity == activity) active.finish(false);
    }

    private void onLifecycle(Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            if (waitingPermission) {
                waitingPermission = false;
                if (Build.VERSION.SDK_INT < 26 || activity.getPackageManager().canRequestPackageInstalls()) install();
                else if (dialog != null) dialog.installNotice("未获得安装授权，可授权后重试", "继续安装", this::install);
            } else if (pendingResult != null) {
                Runnable result = pendingResult;
                pendingResult = null;
                result.run();
            }
        } else if (event == Lifecycle.Event.ON_DESTROY
                || (event == Lifecycle.Event.ON_STOP && !waitingPermission)) {
            finish(false);
        }
    }

    private void present(Runnable result) {
        if (closed) return;
        if (lifecycle.getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) result.run();
        else pendingResult = result;
    }

    private void ensureDialog() {
        if (dialog != null) return;
        dialog = new UpdateDialog(activity);
        dialog.setOnDismissListener(d -> finish(false));
        dialog.show();
    }

    private void startCheck(boolean manual) {
        if (busy || closed) return;
        busy = true;
        client = new UpdateClient();
        if (manual) { ensureDialog(); dialog.checking(); }
        WORKER.execute(() -> {
            try {
                UpdateClient.Info found = client.fetchInfo();
                main.post(() -> present(() -> {
                    busy = false;
                    if (!found.isNewerThan(BuildConfig.VERSION_CODE)) {
                        if (manual) dialog.checked("当前已是最新版本", null);
                        else finish(false);
                        return;
                    }
                    info = found;
                    ensureDialog();
                    dialog.available(info, this::startDownload, this::openBrowser);
                }));
            } catch (IOException e) {
                main.post(() -> present(() -> {
                    busy = false;
                    if (manual) dialog.checked(e.getMessage(), () -> startCheck(true));
                    else finish(false);
                }));
            }
        });
    }

    private void startDownload() {
        if (busy || closed) return;
        busy = true;
        client = new UpdateClient();
        dialog.progress(0, info.size, 0);
        WORKER.execute(() -> {
            try {
                File directory = new File(activity.getCacheDir(), "updates");
                // The single worker has finished any previous cancelled writer before cleanup.
                File[] old = directory.listFiles();
                if (old != null) for (File file : old) if (file.getName().startsWith("update-")) file.delete();
                File downloaded = client.download(info, directory,
                        (received, total, route) -> main.post(() -> {
                            if (!closed) dialog.progress(received, total, route);
                        }), this::verifyArchive);
                main.post(() -> {
                    if (closed) { downloaded.delete(); return; }
                    apk = downloaded;
                    busy = false;
                    dialog.ready(this::install);
                    install();
                });
            } catch (IOException e) {
                main.post(() -> {
                    if (closed) return;
                    busy = false;
                    dialog.result(e.getMessage(), "重新下载", this::startDownload);
                });
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void verifyArchive(File file) throws Exception {
        PackageManager manager = activity.getPackageManager();
        PackageInfo candidate = manager.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        PackageInfo installed = manager.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
        validatePackage(info, installed, candidate);
    }

    @SuppressWarnings("deprecation")
    static void validatePackage(UpdateClient.Info info, PackageInfo installed, PackageInfo candidate) throws IOException {
        if (candidate == null || !installed.packageName.equals(candidate.packageName)) {
            throw new IOException("安装包包名不符");
        }
        if (candidate.versionCode != info.versionCode || !info.versionName.equals(candidate.versionName)
                || !info.isNewerThan(installed.versionCode)) throw new IOException("安装包版本不符");
        Signature[] current = installed.signatures;
        Signature[] next = candidate.signatures;
        if (current == null || current.length == 0 || next == null || next.length == 0
                || !new HashSet<>(Arrays.asList(current)).equals(new HashSet<>(Arrays.asList(next)))) {
            throw new IOException("安装包签名不符，无法覆盖升级");
        }
    }

    private void install() {
        if (closed || apk == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                waitingPermission = true;
                dialog.installNotice("请在系统设置中允许本应用安装更新", "打开安装授权", this::install);
                activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())), INSTALL_PERMISSION_REQUEST);
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk),
                    "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
            finish(true);
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e) {
            waitingPermission = false;
            dialog.installNotice("无法打开系统安装界面，请重试或使用浏览器下载", "继续安装", this::install);
        }
    }

    private void openBrowser() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)));
            finish(false);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "本设备没有可用浏览器，请使用应用内下载", Toast.LENGTH_LONG).show();
        }
    }

    private void finish(boolean keepApk) {
        if (closed) return;
        closed = true;
        pendingResult = null;
        if (client != null) client.cancel();
        lifecycle.removeObserver(observer);
        if (dialog != null) dialog.dismiss();
        if (!keepApk && apk != null) apk.delete();
        if (active == this) active = null;
    }
}
