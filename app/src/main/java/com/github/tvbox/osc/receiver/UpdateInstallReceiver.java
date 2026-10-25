package com.github.tvbox.osc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

/** Receives only this app's explicit PackageInstaller callback. */
public class UpdateInstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                try {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                    return;
                } catch (RuntimeException e) {
                    Log.e("UpdateInstall", "Cannot open install confirmation", e);
                }
            }
            Toast.makeText(context, "电视未能打开安装确认，请通过电视卫士使用 U 盘安装", Toast.LENGTH_LONG).show();
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            Log.e("UpdateInstall", "Install failed: " + status + ": "
                    + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            String message;
            switch (status) {
                case PackageInstaller.STATUS_FAILURE_ABORTED:
                    message = "已取消安装更新";
                    break;
                case PackageInstaller.STATUS_FAILURE_BLOCKED:
                    message = "电视系统禁止此次安装，请检查安装授权或通过电视卫士使用 U 盘安装";
                    break;
                case PackageInstaller.STATUS_FAILURE_STORAGE:
                    message = "电视存储空间不足，请清理空间后重试";
                    break;
                case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                    message = "系统报告安装包不兼容，请通过电视卫士使用 U 盘安装并查看具体提示";
                    break;
                default:
                    message = "系统安装失败（错误码 " + status + "），请通过电视卫士使用 U 盘安装";
                    break;
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }
}
