package com.github.tvbox.osc.cache;

import android.os.AsyncTask;
import android.util.LruCache;

import com.github.tvbox.osc.data.AppDataManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
public class CacheManager {
    private static final LruCache<String, Object> MEMORY_CACHE = new LruCache<>(128);
    private static final AtomicInteger SAVE_COUNT = new AtomicInteger(0);
    private static final int TRIM_THRESHOLD = 100;
    private static final int MAX_DISK_CACHE_ENTRIES = 2000;
    //反序列,把二进制数据转换成java object对象
    private static Object toObject(byte[] data) {
        ByteArrayInputStream bais = null;
        ObjectInputStream ois = null;
        try {
            bais = new ByteArrayInputStream(data);
            ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (bais != null) {
                    bais.close();
                }
                if (ois != null) {
                    ois.close();
                }
            } catch (Exception ignore) {
                ignore.printStackTrace();
            }
        }
        return null;
    }

    //序列化存储数据需要转换成二进制
    private static <T> byte[] toByteArray(T body) {
        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos = null;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(body);
            oos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (baos != null) {
                    baos.close();
                }
                if (oos != null) {
                    oos.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new byte[0];
    }

    public static void delete(String key) {
        if (key == null) return;
        MEMORY_CACHE.remove(key);
        Cache cache = new Cache();
        cache.key = key;
        AppDataManager.get().getCacheDao().delete(cache);
    }

    public static <T> void delete(String key, T body) {
        delete(key);
    }

    public static <T> void save(String key, T body) {
        if (key == null) return;
        if (body != null) {
            MEMORY_CACHE.put(key, body);
        } else {
            MEMORY_CACHE.remove(key);
        }
        Cache cache = new Cache();
        cache.key = key;
        cache.data = toByteArray(body);
        AppDataManager.get().getCacheDao().save(cache);

        if (SAVE_COUNT.incrementAndGet() % TRIM_THRESHOLD == 0) {
            AsyncTask.SERIAL_EXECUTOR.execute(() -> {
                try {
                    AppDataManager.get().getCacheDao().trimCache(MAX_DISK_CACHE_ENTRIES);
                } catch (Throwable ignored) {
                }
            });
        }
    }

    public static Object getCache(String key) {
        if (key == null) return null;
        Object cached = MEMORY_CACHE.get(key);
        if (cached != null) return cached;
        Cache cache = AppDataManager.get().getCacheDao().getCache(key);
        if (cache != null && cache.data != null) {
            Object obj = toObject(cache.data);
            if (obj != null) {
                MEMORY_CACHE.put(key, obj);
            }
            return obj;
        }
        return null;
    }

    public static void clearAll() {
        MEMORY_CACHE.evictAll();
        AsyncTask.SERIAL_EXECUTOR.execute(() -> {
            try {
                AppDataManager.get().getCacheDao().clearAll();
            } catch (Throwable ignored) {
            }
        });
    }
}
