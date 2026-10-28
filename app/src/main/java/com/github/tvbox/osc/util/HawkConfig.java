package com.github.tvbox.osc.util;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class HawkConfig {
    public static final String API_URL = "api_url";
    public static final String API_HISTORY = "api_history";
    public static final String API_SAVED_CONFIGS = "api_saved_configs";
    public static final String[] DEFAULT_API_SAVED_CONFIGS = {
            "https://tv.xn--yhqu5zs87a.top",
            "http://xn--z7x900a.net/tv",
            "https://9280.kstore.vip/newwex.json",
            "http://肥猫.net/",
            "http://xhztv.top/xhz"
    };
    public static final String HOME_API = "home_api";
    public static final String USER_HOME_API = "user_home_api";
    public static final String EXTRA_SITE_VER = "extra_site_ver";//内置采集源版本，升级时清掉一次旧的自动首页源缓存
    public static final String DEFAULT_PARSE = "parse_default";
    public static final String DEBUG_OPEN = "debug_open";
    public static final String SENIOR_MODE = "senior_mode";
    public static final String PARSE_WEBVIEW = "parse_webview"; // true 系统 false xwalk
    public static final String IJK_CODEC = "ijk_codec";
    public static final String PLAY_TYPE = "play_type";//0 系统 1 ijk 2 exo 10 MXPlayer
    public static final int PLAY_TYPE_SYSTEM = 0;
    public static final int PLAY_TYPE_IJK = 1;
    public static final int PLAY_TYPE_EXO = 2;
    /**
     * 点播默认内核：Exo，自带磁盘缓存（拖动回看命中本地缓存）。
     * 个别老片源/特殊封装 Exo 放不了时，播放页会自动回退 IJK 重播，不需要用户手动切换。
     */
    public static final int PLAY_TYPE_DEFAULT = PLAY_TYPE_EXO;
    /** 默认内核迁移标记：老版本默认停在 IJK 的配置只切换一次，之后用户手动选择不再覆盖 */
    public static final String PLAY_TYPE_DEFAULT_APPLIED = "play_type_default_exo";
    public static final String PLAY_RENDER = "play_render"; // 0 TextureView, 1 SurfaceView
    /** 电视端播放渲染控件默认值：1 = SurfaceView，直通硬件叠加层，消除 4K/60fps 掉帧并降低约 30% GPU 开销 */
    public static final int PLAY_RENDER_DEFAULT = 1;
    public static final String PLAY_SCALE = "play_scale"; //0 texture 2
    /**
     * 画面比例默认值：0 = 默认，保持视频宽高比并适配播放区域；
     * 未设置过的机器上播放默认就是自适应比例
     */
    public static final int PLAY_SCALE_DEFAULT = 0;
    public static final String PLAY_SCALE_DEFAULT_RESET = "play_scale_default_reset";
    public static final String PLAY_TIME_STEP = "play_time_step"; //0 texture 2
    public static final String PLAY_TIME_START = "play_time_start";
    public static final String PLAY_TIME_END = "play_time_end";
    public static final String AUDIO_LANGUAGE = "audio_language";
    public static final String SUBTITLE_LANGUAGE = "subtitle_language";
    public static final String SUBTITLE_OFF = "subtitle_off";
    public static final String DOH_URL = "doh_url";
    public static final String HOME_LOW_POWER = "home_low_power"; // false 当前首页，true 低压力首页
    public static final String HOME_REC = "home_rec"; // 0 豆瓣热播 1 数据源推荐 2 历史
    public static final String HOME_DOUBAN_CACHE = "home_douban_cache"; // 最近一次成功的豆瓣热播列表，限流时兜底
    public static final String DOU_BAN_BID = "dou_ban_bid"; // 豆瓣 bid Cookie，首次生成后固定，降低被判定异常请求的概率
    public static final String SEARCH_VIEW = "search_view"; // 0 列表 1 缩略图
    public static final String SEARCH_SITE_DISABLED = "search_site_disabled"; // 不参与搜索的站点 key 列表（缺省=全部参与）
    public static final String SEARCH_SITE_ORDER = "search_site_order"; // 参与搜索的站点 key 顺序：勾选先后即所有搜索界面的展示顺序
    public static final String SEARCH_SITE_VER = "search_site_ver"; // 搜索源默认值版本，升级时把搜索源恢复到「默认全选 + 文采秒播最高」
    public static final String LIVE_CHANNEL = "last_live_channel_name";
    public static final String LIVE_SOURCE_URL = "live_source_urls";
    public static final String LIVE_SOURCE_MANAGER = "live_source_manager_v1";
    public static final String LIVE_SOURCE_UNIFIED = "live_source_unified_v1";
    public static final String DEFAULT_LIVE_SOURCE_URLS =
            "193.123.86.190:14888/TV/iptv.php\n"
                    + "sub.ottiptv.cc/huyayqk.m3u\n"
                    + "sub.ottiptv.cc/douyuyqk.m3u\n"
                    + "https://iptv-org.github.io/iptv/countries/cn.m3u\n"
                    + "https://cdn.jsdelivr.net/gh/Guovin/iptv-api@gd/output/result.m3u\n"
                    + "https://cdn.jsdelivr.net/gh/Guovin/iptv-api@gd/output/ipv6/result.m3u\n"
                    + "https://cdn.jsdelivr.net/gh/Guovin/iptv-api@gd/output/ipv4/result.m3u";
    public static final String LIVE_CHANNEL_REVERSE = "live_channel_reverse";
    public static final String LIVE_CROSS_GROUP = "live_cross_group";
    public static final String LIVE_CONNECT_TIMEOUT = "live_connect_timeout";
    public static final String LIVE_SHOW_NET_SPEED = "live_show_net_speed";
    public static final String LIVE_SHOW_TIME = "live_show_time";
}
