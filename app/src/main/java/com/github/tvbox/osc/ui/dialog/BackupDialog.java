package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.ui.adapter.BackupAdapter;
import com.github.tvbox.osc.util.FileUtils;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.lcstudio.commonsurport.L;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.studio.osc.R;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class BackupDialog extends BaseDialog {
    private final BackupAdapter adapter;
    private final TvRecyclerView listView;
    private final View emptyView;
    private final TextView statusView;
    private final TextView permissionBtn;

    public BackupDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_backup);
        listView = findViewById(R.id.list);
        emptyView = findViewById(R.id.tvBackupEmpty);
        statusView = findViewById(R.id.tvBackupStatus);
        permissionBtn = findViewById(R.id.storagePermission);

        adapter = new BackupAdapter();
        listView.setAdapter(adapter);
        refreshBackups();

        adapter.setOnItemChildClickListener((adp, view, position) -> {
            String target = (String) adp.getItem(position);
            if (target == null) return;
            if (view.getId() == R.id.tvNameRow) {
                TipDialog.confirm(getContext(), "确认还原备份",
                        "确定要从备份 [" + target + "] 恢复数据吗？\n当前配置与记录将被覆盖。",
                        "确定恢复", () -> restore(target));
            } else if (view.getId() == R.id.tvDel) {
                TipDialog.confirm(getContext(), "删除本地备份",
                        "确定要删除备份 [" + target + "] 吗？删除后无法找回。",
                        "删除", () -> {
                            deleteBackupDir(target);
                            refreshBackups();
                        });
            }
        });

        findViewById(R.id.backupNow).setOnClickListener(v -> {
            backup();
            refreshBackups();
        });

        findViewById(R.id.btnBackupClose).setOnClickListener(v -> dismiss());

        permissionBtn.setOnClickListener(v -> {
            if (XXPermissions.isGranted(getContext(), Permission.Group.STORAGE)) {
                Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
            } else {
                XXPermissions.with(getContext())
                        .permission(Permission.Group.STORAGE)
                        .request(new OnPermissionCallback() {
                            @Override
                            public void onGranted(List<String> permissions, boolean all) {
                                if (all) {
                                    refreshBackups();
                                    Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onDenied(List<String> permissions, boolean never) {
                                if (never && getContext() instanceof Activity) {
                                    Toast.makeText(getContext(), "获取存储权限失败，请在系统设置中开启", Toast.LENGTH_SHORT).show();
                                    XXPermissions.startPermissionActivity((Activity) getContext(), permissions);
                                } else {
                                    Toast.makeText(getContext(), "获取存储权限失败", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });
    }

    private void refreshBackups() {
        List<String> backups = allBackup();
        adapter.setNewData(backups);
        boolean empty = backups.isEmpty();
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);

        boolean granted = XXPermissions.isGranted(getContext(), Permission.Group.STORAGE);
        permissionBtn.setText(granted ? "存储权限已授权" : "授权存储权限");
        statusView.setText((granted ? "存储权限：已授权" : "存储权限：未授权")
                + " · " + (empty ? "暂无本地备份" : "共 " + backups.size() + " 份本地备份"));
    }

    @Override
    public void show() {
        super.show();
        View backupNow = findViewById(R.id.backupNow);
        if (backupNow != null) {
            backupNow.post(backupNow::requestFocus);
        }
    }

    public static int getBackupCount() {
        try {
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            File dir = new File(root + "/tvbox_backup/");
            if (!dir.exists()) return 0;
            File[] files = dir.listFiles();
            if (files == null) return -1;
            int count = 0;
            for (File f : files) {
                if (f.isDirectory()) count++;
            }
            return count;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    List<String> allBackup() {
        ArrayList<String> result = new ArrayList<>();
        try {
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            File file = new File(root + "/tvbox_backup/");
            if (!file.exists()) {
                return result;
            }
            File[] list = file.listFiles();
            if (list == null || list.length == 0) {
                return result;
            }
            Arrays.sort(list, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    if (o1.isDirectory() && o2.isFile()) return -1;
                    return o1.isFile() && o2.isDirectory() ? 1 : o2.getName().compareTo(o1.getName());
                }
            });
            for (File f : list) {
                if (f.isDirectory()) {
                    result.add(f.getName());
                }
            }
        } catch (Throwable e) {
            L.e(e);
        }
        return result;
    }

    private void deleteBackupDir(String dir) {
        try {
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            File target = new File(root + "/tvbox_backup/" + dir);
            if (target.exists()) {
                FileUtils.recursiveDelete(target);
                Toast.makeText(getContext(), "已删除备份 " + dir, Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable e) {
            L.e(e);
            Toast.makeText(getContext(), "删除备份失败", Toast.LENGTH_SHORT).show();
        }
    }

    /** 恢复数据后重启应用，让内存中的缓存（Hawk 等）重新从存储读取 */
    private void restartApp() {
        Context context = getContext();
        android.content.Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        }
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    void restore(String dir) {
        try {
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            File backup = new File(root + "/tvbox_backup/" + dir);
            if (backup.exists()) {
                File db = new File(backup, "sqlite");
                if (AppDataManager.restore(db)) {
                    byte[] data = FileUtils.readSimple(new File(backup, "hawk"));
                    if (data != null) {
                        String hawkJson = new String(data, "UTF-8");
                        JSONObject jsonObject = new JSONObject(hawkJson);
                        Iterator<String> it = jsonObject.keys();
                        SharedPreferences sharedPreferences = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE);
                        while (it.hasNext()) {
                            String key = it.next();
                            String value = jsonObject.getString(key);
                            if (key.equals("cipher_key")) {
                                App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE).edit().putString(key, value).commit();
                            } else {
                                sharedPreferences.edit().putString(key, value).commit();
                            }
                        }
                        TipDialog.confirm(getContext(), "恢复成功",
                                "备份数据已恢复，需要重启应用后完全生效，现在重启吗？",
                                "立即重启", this::restartApp);
                    } else {
                        Toast.makeText(getContext(), "配置文件读取失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "数据库恢复失败", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Throwable e) {
            L.e(e);
            Toast.makeText(getContext(), "恢复失败", Toast.LENGTH_SHORT).show();
        }
    }

    void backup() {
        try {
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            File file = new File(root + "/tvbox_backup/");
            if (!file.exists()) {
                file.mkdirs();
            }
            Date now = new Date();
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US);
            File backup = new File(file, f.format(now));
            backup.mkdirs();
            File db = new File(backup, "sqlite");
            if (AppDataManager.backup(db)) {
                SharedPreferences sharedPreferences = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE);
                JSONObject jsonObject = new JSONObject();
                for (String key : sharedPreferences.getAll().keySet()) {
                    jsonObject.put(key, sharedPreferences.getString(key, ""));
                }
                sharedPreferences = App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE);
                for (String key : sharedPreferences.getAll().keySet()) {
                    jsonObject.put(key, sharedPreferences.getString(key, ""));
                }
                if (!FileUtils.writeSimple(jsonObject.toString().getBytes("UTF-8"), new File(backup, "hawk"))) {
                    backup.delete();
                    Toast.makeText(getContext(), "备份配置失败", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "备份成功", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "数据库不存在，备份失败", Toast.LENGTH_SHORT).show();
                backup.delete();
            }
        } catch (Throwable e) {
            L.e(e);
            Toast.makeText(getContext(), "备份失败，请检查存储权限", Toast.LENGTH_SHORT).show();
        }
    }
}
