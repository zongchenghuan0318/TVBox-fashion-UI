package com.github.tvbox.osc.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 应用级的短耗时后台任务线程池（替代散落各处的 AsyncTask.THREAD_POOL_EXECUTOR）。
 * 有生命周期的长任务（如搜索、播放解析）仍由各自的组件持有线程池。
 */
public final class AppExecutors {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private static final ExecutorService IO = new ThreadPoolExecutor(
            4, 8, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "app-io-" + INDEX.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });

    private AppExecutors() {
    }

    public static ExecutorService io() {
        return IO;
    }
}
