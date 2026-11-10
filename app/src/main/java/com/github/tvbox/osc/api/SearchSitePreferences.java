package com.github.tvbox.osc.api;

import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.lcstudio.commonsurport.util.NullUtil;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SearchSitePreferences {
    private final Map<String, SiteBean> mSiteBeanMap;
    private volatile Map<String, Integer> mSearchSiteOrderIndexCache = new ConcurrentHashMap<>();

    SearchSitePreferences(Map<String, SiteBean> siteBeanMap) {
        mSiteBeanMap = siteBeanMap;
    }

    /**
     * 用户设置的不参与搜索的站点 key 列表。
     * 存“被取消勾选”的 key：缺省为空 → 全部站点参与搜索，
     * 这样配置源更新后新增的站点会自动参与，不会漏掉。
     */
    ArrayList<String> getSearchSiteDisabled() {
        ArrayList<String> disabled = Hawk.get(HawkConfig.SEARCH_SITE_DISABLED, new ArrayList<String>());
        return disabled == null ? new ArrayList<String>() : disabled;
    }

    void setSearchSiteDisabled(List<String> keys) {
        Hawk.put(HawkConfig.SEARCH_SITE_DISABLED, keys == null ? new ArrayList<String>() : new ArrayList<>(keys));
    }

    /** 该站点是否被允许参与搜索（未设置或未被取消勾选 → 允许） */
    boolean isSearchSiteEnabled(String key) {
        if (NullUtil.isNull(key)) return true;
        return !getSearchSiteDisabled().contains(key);
    }

    /**
     * 用户在「搜索源管理」里勾选搜索源的先后顺序，勾选顺序即所有搜索界面的搜索与展示顺序。
     * 未登记过顺序的源（配置新增或默认启用）排在最后。
     */
    ArrayList<String> getSearchSiteOrder() {
        ArrayList<String> order = Hawk.get(HawkConfig.SEARCH_SITE_ORDER, new ArrayList<String>());
        return order == null ? new ArrayList<String>() : order;
    }

    void setSearchSiteOrder(List<String> keys) {
        Hawk.put(HawkConfig.SEARCH_SITE_ORDER, keys == null ? new ArrayList<String>() : new ArrayList<>(keys));
        invalidateSearchSiteOrderCache();
    }

    /**
     * 默认搜索源顺序（用户没自定义时使用）：名字命中 DefaultConfig.SEARCH_SITE_PRIORITY
     * 关键词的源排在最前（文采┃秒播 > 非凡┃影视），其余保持站点表顺序。
     */
    ArrayList<String> getDefaultSearchSiteOrder() {
        ArrayList<String> rest = new ArrayList<>();
        for (SiteBean bean : mSiteBeanMap.values()) {
            if (bean.isSearchable() && !NullUtil.isNull(bean.getKey())) {
                rest.add(bean.getKey());
            }
        }
        ArrayList<String> ordered = new ArrayList<>();
        for (String keyword : DefaultConfig.SEARCH_SITE_PRIORITY) {
            for (int i = 0; i < rest.size(); i++) {
                SiteBean bean = mSiteBeanMap.get(rest.get(i));
                if (bean != null && !NullUtil.isNull(bean.getName()) && bean.getName().contains(keyword)) {
                    ordered.add(rest.remove(i));
                    i--;
                }
            }
        }
        ordered.addAll(rest);
        return ordered;
    }

    /** 用户自定义过顺序就尊重用户，否则返回默认顺序（文采秒播 > 非凡影视 > 其余） */
    ArrayList<String> getSearchSiteOrderOrDefault() {
        ArrayList<String> order = getSearchSiteOrder();
        return order.isEmpty() ? getDefaultSearchSiteOrder() : order;
    }

    /** 参与搜索的站点，按用户勾选顺序排列；没排过序的源接在最后并保持站点表原顺序 */
    List<SiteBean> getOrderedSearchSiteList() {
        List<SiteBean> pending = new ArrayList<>();
        for (SiteBean bean : mSiteBeanMap.values()) {
            if (bean.isSearchable() && isSearchSiteEnabled(bean.getKey())) {
                pending.add(bean);
            }
        }
        List<SiteBean> ordered = new ArrayList<>();
        for (String key : getSearchSiteOrderOrDefault()) {
            for (int i = 0; i < pending.size(); i++) {
                if (key.equals(pending.get(i).getKey())) {
                    ordered.add(pending.remove(i));
                    break;
                }
            }
        }
        ordered.addAll(pending);
        return ordered;
    }

    void invalidateSearchSiteOrderCache() {
        mSearchSiteOrderIndexCache = new ConcurrentHashMap<>();
    }

    /** 站点在搜索结果里的排序键：勾选顺序在前，未登记的按站点表顺序接在最后（O(1) 缓存查询，消除排序时的遍历开销） */
    int getSearchSiteOrderIndex(String key) {
        if (NullUtil.isNull(key)) return Integer.MAX_VALUE;
        Map<String, Integer> cache = mSearchSiteOrderIndexCache;
        Integer cached = cache.get(key);
        if (cached != null) return cached;

        List<String> order = getSearchSiteOrderOrDefault();
        int index = order.indexOf(key);
        if (index >= 0) {
            cache.put(key, index);
            return index;
        }
        int pos = 0;
        for (SiteBean bean : mSiteBeanMap.values()) {
            if (key.equals(bean.getKey())) {
                int res = order.size() + pos;
                cache.put(key, res);
                return res;
            }
            pos++;
        }
        cache.put(key, Integer.MAX_VALUE);
        return Integer.MAX_VALUE;
    }

    void migrate(String pinnedKey) {
        // 搜索源默认值改版：清掉一次旧的勾选/排序记录，让搜索源回到「默认全选」，
        // 并按新默认优先级排序（文采┃秒播 > 非凡┃影视 > 其余）
        if (Hawk.get(HawkConfig.SEARCH_SITE_VER, 0) < 1) {
            Hawk.delete(HawkConfig.SEARCH_SITE_DISABLED);
            Hawk.delete(HawkConfig.SEARCH_SITE_ORDER);
            Hawk.put(HawkConfig.SEARCH_SITE_VER, 1);
        }
        // 置顶搜索源（全体用户）：把“粉猪秒播”放到搜索顺序第一位并确保启用；
        // 只移动这一项，保留用户已有的其它顺序与勾选
        if (Hawk.get(HawkConfig.SEARCH_PIN_VER, 0) < 1) {
            pinSearchSiteFirst(pinnedKey);
            Hawk.put(HawkConfig.SEARCH_PIN_VER, 1);
        }
    }

    private void pinSearchSiteFirst(String key) {
        ArrayList<String> order = new ArrayList<>(getSearchSiteOrderOrDefault());
        order.remove(key);
        order.add(0, key);
        setSearchSiteOrder(order);
        ArrayList<String> disabled = Hawk.get(HawkConfig.SEARCH_SITE_DISABLED, new ArrayList<String>());
        if (disabled != null && disabled.remove(key)) {
            Hawk.put(HawkConfig.SEARCH_SITE_DISABLED, disabled);
        }
    }
}
