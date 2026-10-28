package com.github.tvbox.osc.base;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.kingja.loadsir.core.LoadSir;
import com.lcstudio.commonsurport.CrashHandler;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.MLog;
import com.orhanobut.hawk.Hawk;
import com.whl.quickjs.android.QuickJSLoader;

import androidx.multidex.MultiDexApplication;
import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 *  * tvbox交流QQ群：879811030
 */
public class App extends MultiDexApplication {
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        MLog.setContext(getApplicationContext());
        MLog.setLogEnable(false);
        L.setUp(false);//打开日志
        CrashHandler.getInstance().init(getApplicationContext());
        initParams();
        // OKGo
        OkGoHelper.init();
        // QuickJS 原生库（JS 爬虫引擎）
        QuickJSLoader.init();

        //初始化数据库
        AppDataManager.init();
        LoadSir.beginBuilder()
                .addCallback(new EmptyCallback())
                .addCallback(new LoadingCallback())
                .commit();
        AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                .setSupportDP(false)
                .setSupportSP(false)
                .setSupportSubunits(Subunits.MM);
        PlayerHelper.init();
    }

    private void initParams() {
        // Hawk
        Hawk.init(this).build();
        // 升级后统一恢复一次自适应比例，此后的手动选择继续保留。
        if (!Hawk.get(HawkConfig.PLAY_SCALE_DEFAULT_RESET, false)) {
            Hawk.put(HawkConfig.PLAY_SCALE, HawkConfig.PLAY_SCALE_DEFAULT);
            Hawk.put(HawkConfig.PLAY_SCALE_DEFAULT_RESET, true);
        }
        Hawk.put(HawkConfig.DEBUG_OPEN, false);
        // 点播默认内核切到 Exo（磁盘缓存、拖动秒回）；只迁移一次仍停留在旧默认 IJK 的配置，
        // 用户手动选过的系统播放器等其他内核保持不动，之后也仍可在设置里随意切换。
        if (!Hawk.get(HawkConfig.PLAY_TYPE_DEFAULT_APPLIED, false)) {
            if (!Hawk.contains(HawkConfig.PLAY_TYPE)) {
                Hawk.put(HawkConfig.PLAY_TYPE, HawkConfig.PLAY_TYPE_DEFAULT);
            } else if (Hawk.get(HawkConfig.PLAY_TYPE, HawkConfig.PLAY_TYPE_DEFAULT) == HawkConfig.PLAY_TYPE_IJK) {
                Hawk.put(HawkConfig.PLAY_TYPE, HawkConfig.PLAY_TYPE_DEFAULT);
            }
            Hawk.put(HawkConfig.PLAY_TYPE_DEFAULT_APPLIED, true);
        }
        // 固化默认配置地址：未配置过（首次安装/清除数据/手动清空）时预置，
        // 装好即有内容，用户仍可在设置里改成自己的接口
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        if (apiUrl == null || apiUrl.trim().length() == 0) {
            Hawk.put(HawkConfig.API_URL, ApiConfig.DEFAULT_URL);
        }
    }

    public static App getInstance() {
        return instance;
    }
}