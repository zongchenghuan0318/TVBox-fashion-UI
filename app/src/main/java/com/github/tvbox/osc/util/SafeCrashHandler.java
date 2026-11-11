package com.github.tvbox.osc.util;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 应用自己的崩溃处理器：只把崩溃现场落盘，绝不自动重启应用。
 *
 * <p>背景：工程引用的 uiSupport-release.aar 里自带 com.lcstudio.commonsurport.CrashHandler，
 * 它会用 {@link Thread#setDefaultUncaughtExceptionHandler} 覆盖全局处理器，并在任意线程抛出
 * 未捕获异常时：
 * <ol>
 *   <li>把崩溃计数写入 SharedPreferences；</li>
 *   <li>只要 24 小时内崩溃不足 30 次，就通过 AlarmManager 在 2 秒后重新拉起启动页，
 *       然后 killProcess + System.exit。</li>
 * </ol>
 * 一旦「设置页 → 返回首页」链路里存在偶发崩溃（配置重载 / 爬虫 jar / 首页片段线程池等），
 * 应用就会表现为「无限重启」。本处理器替换掉它：记录日志后交给系统默认处理器正常终止进程，
 * 进程不会自动拉起，从根源上杜绝无限重启。
 */
public final class SafeCrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "SafeCrashHandler";

    private final Context mContext;
    private final Thread.UncaughtExceptionHandler mDefaultHandler;

    private SafeCrashHandler(Context context, Thread.UncaughtExceptionHandler defaultHandler) {
        mContext = context.getApplicationContext();
        mDefaultHandler = defaultHandler;
    }

    /** 安装处理器；重复调用不会叠加。 */
    public static void install(Context context) {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof SafeCrashHandler) return;
        Thread.setDefaultUncaughtExceptionHandler(new SafeCrashHandler(context, current));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            saveCrashLog(thread, throwable);
        } catch (Throwable ignored) {
            // 记录日志失败也不能影响后续处理
        }
        if (mDefaultHandler != null) {
            try {
                // 交给系统默认处理器：正常终止进程，绝不自动拉起启动页。
                mDefaultHandler.uncaughtException(thread, throwable);
                return;
            } catch (Throwable ignored) {
                // 默认处理器自身异常时走兜底终止
            }
        }
        Process.killProcess(Process.myPid());
        System.exit(10);
    }

    private void saveCrashLog(Thread thread, Throwable throwable) {
        StringWriter stack = new StringWriter();
        PrintWriter writer = new PrintWriter(stack);
        if (thread != null) {
            writer.println("thread=" + thread.getName());
        }
        if (throwable != null) {
            throwable.printStackTrace(writer);
        }
        writer.close();
        String content = stack.toString();

        try {
            File dir = new File(mContext.getFilesDir(), "crash");
            if (dir.exists() || dir.mkdirs()) {
                String name = "crash_" + new SimpleDateFormat("HHmmss", Locale.US).format(new Date())
                        + "_" + System.currentTimeMillis() + ".log";
                File file = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(file);
                try {
                    fos.write(content.getBytes("UTF-8"));
                } finally {
                    fos.close();
                }
                Log.e(TAG, "crash saved to " + file.getAbsolutePath());
            }
        } catch (Throwable ignored) {
            // 外部存储不可用或写入失败时，仍然靠 logcat 保留现场
        }
        Log.e(TAG, "uncaught exception:\n" + content);
    }
}
