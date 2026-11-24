package com.github.tvbox.osc.api;

import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.lcstudio.commonsurport.util.NullUtil;
import com.orhanobut.hawk.Hawk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SearchSitePreferences {
    private static final ExecutorService PERSIST_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "search-site-prefs");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, SiteBean> mSiteBeanMap;
    private volatile Map<String, Integer> mSearchSiteOrderIndexCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> mScopedDisabledMemory = new HashMap<>();
    private final Map<String, List<String>> mScopedOrderMemory = new HashMap<>();
    private volatile String mConfigScope = "";
    private volatile List<String> mDisabledCache;
    private volatile Set<String> mDisabledSetCache;
    private volatile List<String> mOrderCache;

    SearchSitePreferences(Map<String, SiteBean> siteBeanMap) {
        mSiteBeanMap = siteBeanMap;
    }

    static String normalizeConfigScope(String rawApiUrl) {
        if (NullUtil.isNull(rawApiUrl)) return "";
        String normalized = ApiConfig.normalizeUrl(rawApiUrl);
        if (NullUtil.isNull(normalized)) return "";
        String trimmed = normalized.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1 && !trimmed.endsWith("://")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String scopedKey(String baseKey, String scope) {
        if (NullUtil.isNull(scope)) return baseKey;
        return baseKey + "_" + md5Hex(scope);
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                int v = b & 0xff;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * 按点播配置地址切换搜索源偏好作用域：每套点播源独立记住自己的勾选与排序；
     * 未自定义过的点播源默认按 粉猪 > 文采/文才 > 非凡 > 量子 > 其余 排序。
     */
    synchronized void setConfigScope(String rawApiUrl) {
        String nextScope = normalizeConfigScope(rawApiUrl);
        if (!nextScope.equals(mConfigScope)) {
            mConfigScope = nextScope;
            mDisabledCache = null;
            mDisabledSetCache = null;
            mOrderCache = null;
        }
        invalidateSearchSiteOrderCache();
    }

    private Set<String> ensureDisabledSetCache() {
        Set<String> set = mDisabledSetCache;
        if (set != null) return set;
        synchronized (this) {
            set = mDisabledSetCache;
            if (set != null) return set;
            String scope = mConfigScope;
            List<String> loaded = mScopedDisabledMemory.get(scope);
            if (loaded == null) {
                try {
                    if (Hawk.isBuilt()) {
                        loaded = Hawk.get(scopedKey(HawkConfig.SEARCH_SITE_DISABLED, scope),
                                new ArrayList<String>());
                    }
                } catch (Throwable ignored) {
                }
            }
            List<String> list = loaded == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(loaded));
            set = Collections.unmodifiableSet(new HashSet<>(list));
            mDisabledCache = list;
            mDisabledSetCache = set;
            return set;
        }
    }

    private List<String> ensureOrderCache() {
        List<String> list = mOrderCache;
        if (list != null) return list;
        synchronized (this) {
            list = mOrderCache;
            if (list != null) return list;
            String scope = mConfigScope;
            List<String> loaded = mScopedOrderMemory.get(scope);
            if (loaded == null) {
                try {
                    if (Hawk.isBuilt()) {
                        loaded = Hawk.get(scopedKey(HawkConfig.SEARCH_SITE_ORDER, scope),
                                new ArrayList<String>());
                    }
                } catch (Throwable ignored) {
                }
            }
            list = loaded == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(loaded));
            mOrderCache = list;
            return list;
        }
    }

    /**
     * 用户设置的不参与搜索的站点 key 列表。
     * 存“被取消勾选”的 key：缺省为空 → 全部站点参与搜索，
     * 这样配置源更新后新增的站点会自动参与，不会漏掉。
     */
    ArrayList<String> getSearchSiteDisabled() {
        ensureDisabledSetCache();
        List<String> list = mDisabledCache;
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    void setSearchSiteDisabled(List<String> keys) {
        final ArrayList<String> snapshot = keys == null ? new ArrayList<>() : new ArrayList<>(keys);
        final String storageKey;
        synchronized (this) {
            List<String> immutable = Collections.unmodifiableList(new ArrayList<>(snapshot));
            mScopedDisabledMemory.put(mConfigScope, immutable);
            mDisabledCache = immutable;
            mDisabledSetCache = Collections.unmodifiableSet(new HashSet<>(snapshot));
            storageKey = scopedKey(HawkConfig.SEARCH_SITE_DISABLED, mConfigScope);
        }
        invalidateSearchSiteOrderCache();
        PERSIST_EXECUTOR.execute(() -> {
            try {
                if (Hawk.isBuilt()) {
                    Hawk.put(storageKey, snapshot);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /** 该站点是否被允许参与搜索（未设置或未被取消勾选 → 允许） */
    boolean isSearchSiteEnabled(String key) {
        if (NullUtil.isNull(key)) return true;
        return !ensureDisabledSetCache().contains(key);
    }

    /**
     * 用户在「搜索源管理」里勾选搜索源的先后顺序，勾选顺序即所有搜索界面的搜索与展示顺序。
     * 未登记过顺序的源（配置新增或默认启用）仍按默认关键词优先级接在最后。
     */
    ArrayList<String> getSearchSiteOrder() {
        return new ArrayList<>(ensureOrderCache());
    }

    void setSearchSiteOrder(List<String> keys) {
        final ArrayList<String> snapshot = keys == null ? new ArrayList<>() : new ArrayList<>(keys);
        final String storageKey;
        synchronized (this) {
            List<String> immutable = Collections.unmodifiableList(new ArrayList<>(snapshot));
            mScopedOrderMemory.put(mConfigScope, immutable);
            mOrderCache = immutable;
            storageKey = scopedKey(HawkConfig.SEARCH_SITE_ORDER, mConfigScope);
        }
        invalidateSearchSiteOrderCache();
        PERSIST_EXECUTOR.execute(() -> {
            try {
                if (Hawk.isBuilt()) {
                    Hawk.put(storageKey, snapshot);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * 默认搜索源顺序（用户没自定义时使用）：名字命中 DefaultConfig.SEARCH_SITE_PRIORITY
     * 关键词的源排在最前（粉猪 > 文采/文才 > 非凡 > 量子），其余保持站点表顺序。
     */
    ArrayList<String> getDefaultSearchSiteOrder() {
        List<SiteBean> searchable = new ArrayList<>();
        for (SiteBean bean : mSiteBeanMap.values()) {
            if (bean != null && bean.isSearchable() && !NullUtil.isNull(bean.getKey())) {
                searchable.add(bean);
            }
        }
        ArrayList<String> ordered = new ArrayList<>(searchable.size());
        for (SiteBean bean : orderByDefaultPriority(searchable)) {
            ordered.add(bean.getKey());
        }
        return ordered;
    }

    /**
     * 当前配置生效的搜索源顺序：
     * - 未自定义（或旧顺序对当前站点表完全不命中）时，按 粉猪 > 文采/文才 > 非凡 > 量子 > 其余；
     * - 已自定义时尊重当前配置的自定义顺序，配置新增或改名的未登记站点仍按默认关键词优先级接在后面。
     */
    ArrayList<String> getSearchSiteOrderOrDefault() {
        ArrayList<String> saved = getSearchSiteOrder();
        if (saved.isEmpty()) {
            return getDefaultSearchSiteOrder();
        }
        LinkedHashMap<String, SiteBean> remaining = new LinkedHashMap<>();
        for (SiteBean bean : mSiteBeanMap.values()) {
            if (bean != null && bean.isSearchable() && !NullUtil.isNull(bean.getKey())) {
                remaining.put(bean.getKey(), bean);
            }
        }
        ArrayList<String> resolved = new ArrayList<>(remaining.size());
        for (String key : saved) {
            if (remaining.remove(key) != null) {
                resolved.add(key);
            }
        }
        if (resolved.isEmpty()) {
            return getDefaultSearchSiteOrder();
        }
        if (!remaining.isEmpty()) {
            for (SiteBean bean : orderByDefaultPriority(remaining.values())) {
                resolved.add(bean.getKey());
            }
        }
        return resolved;
    }

    /** 参与搜索的站点，按当前配置的排序优先级排列（已过滤禁用源） */
    List<SiteBean> getOrderedSearchSiteList() {
        Set<String> disabled = ensureDisabledSetCache();
        LinkedHashMap<String, SiteBean> pending = new LinkedHashMap<>();
        for (SiteBean bean : mSiteBeanMap.values()) {
            if (bean != null && bean.isSearchable()
                    && (NullUtil.isNull(bean.getKey()) || !disabled.contains(bean.getKey()))) {
                pending.put(bean.getKey(), bean);
            }
        }
        List<SiteBean> ordered = new ArrayList<>(pending.size());
        for (String key : getSearchSiteOrderOrDefault()) {
            SiteBean hit = pending.remove(key);
            if (hit != null) {
                ordered.add(hit);
            }
        }
        if (!pending.isEmpty()) {
            ordered.addAll(orderByDefaultPriority(pending.values()));
        }
        return ordered;
    }

    private List<SiteBean> orderByDefaultPriority(Collection<SiteBean> sites) {
        List<SiteBean> rest = new ArrayList<>(sites);
        List<SiteBean> ordered = new ArrayList<>(rest.size());
        for (String keyword : DefaultConfig.SEARCH_SITE_PRIORITY) {
            for (int i = 0; i < rest.size(); i++) {
                SiteBean bean = rest.get(i);
                if (bean != null && !NullUtil.isNull(bean.getName()) && bean.getName().contains(keyword)) {
                    ordered.add(rest.remove(i));
                    i--;
                }
            }
        }
        ordered.addAll(rest);
        return ordered;
    }

    void invalidateSearchSiteOrderCache() {
        mSearchSiteOrderIndexCache = new ConcurrentHashMap<>();
    }

    /** 切换配置或迁移后切换作用域、丢弃内存缓存并重新执行版本迁移 */
    void reloadAndMigrate() {
        reloadAndMigrate(mConfigScope);
    }

    void reloadAndMigrate(String rawApiUrl) {
        synchronized (this) {
            mConfigScope = normalizeConfigScope(rawApiUrl);
            mOrderCache = null;
            mDisabledCache = null;
            mDisabledSetCache = null;
        }
        invalidateSearchSiteOrderCache();
        migrate();
    }

    /** 站点在搜索结果里的排序键：勾选顺序在前，未登记的按默认优先级接在最后（O(1) 缓存查询，消除排序时的遍历开销） */
    int getSearchSiteOrderIndex(String key) {
        if (NullUtil.isNull(key)) return Integer.MAX_VALUE;
        Map<String, Integer> cache = mSearchSiteOrderIndexCache;
        Integer cached = cache.get(key);
        if (cached != null) return cached;

        List<SiteBean> ordered = getOrderedSearchSiteList();
        for (int i = 0; i < ordered.size(); i++) {
            String orderedKey = ordered.get(i).getKey();
            if (!NullUtil.isNull(orderedKey)) {
                cache.put(orderedKey, i);
            }
        }
        Integer hit = cache.get(key);
        if (hit != null) return hit;

        int pos = ordered.size();
        for (SiteBean bean : mSiteBeanMap.values()) {
            if (bean != null && key.equals(bean.getKey())) {
                cache.put(key, pos);
                return pos;
            }
            pos++;
        }
        cache.put(key, Integer.MAX_VALUE);
        return Integer.MAX_VALUE;
    }

    void migrate() {
        if (!Hawk.isBuilt()) return;
        // 搜索源默认值改版：新装用户保持空顺序，动态按 DefaultConfig.SEARCH_SITE_PRIORITY
        // （名字含 粉猪 > 文采/文才 > 非凡 > 量子 > 其余）排序
        if (Hawk.get(HawkConfig.SEARCH_SITE_VER, 0) < 1) {
            Hawk.delete(HawkConfig.SEARCH_SITE_DISABLED);
            Hawk.delete(HawkConfig.SEARCH_SITE_ORDER);
            synchronized (this) {
                mScopedDisabledMemory.clear();
                mScopedOrderMemory.clear();
                mDisabledCache = null;
                mDisabledSetCache = null;
                mOrderCache = null;
            }
            invalidateSearchSiteOrderCache();
            Hawk.put(HawkConfig.SEARCH_SITE_VER, 1);
            Hawk.put(HawkConfig.SEARCH_PIN_VER, 3);
            return;
        }
        // v3 迁移：清掉旧全局顺序，改为按点播配置地址独立保存搜索源偏好，
        // 未自定义的配置自动按 粉猪 > 文采/文才 > 非凡 > 量子 > 其余 排序
        if (Hawk.get(HawkConfig.SEARCH_PIN_VER, 0) < 3) {
            Hawk.delete(HawkConfig.SEARCH_SITE_ORDER);
            synchronized (this) {
                mOrderCache = null;
            }
            invalidateSearchSiteOrderCache();
            Hawk.put(HawkConfig.SEARCH_PIN_VER, 3);
        }
    }
}
