package com.github.tvbox.osc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.AppUtil;

public class BootBroadcastReceiver extends BroadcastReceiver {

    private static final String ACTION = "android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        L.d("onReceive: " + intent.getAction());
        if (intent.getAction().equals(ACTION)) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
//                    String cmd = "am start -n com.hbiot/com.hbiot.activitys.MainActivity \n";
//                    RootCmd.execRootCmdSilent(cmd);

                    AppUtil.startApp(context, context.getPackageName());
                }
            }, 500);
        }
    }

}
