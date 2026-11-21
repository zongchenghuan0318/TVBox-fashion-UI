package com.github.tvbox.osc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.util.AppManager;

import org.greenrobot.eventbus.EventBus;

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
public class SearchReceiver extends BroadcastReceiver {
    public static String action = "android.content.movie.search.Action";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (action.equals(intent.getAction()) && intent.getExtras() != null) {
            String rawTitle = intent.getExtras().getString("title");
            String title = rawTitle == null ? "" : rawTitle.trim();
            if (TextUtils.isEmpty(title)) {
                return;
            }
            if (AppManager.getInstance().getActivity(SearchActivity.class) != null) {
                AppManager.getInstance().backActivity(SearchActivity.class);
                EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_SEARCH, title));
            } else {
                Intent newIntent = new Intent(context, SearchActivity.class);
                newIntent.putExtra("title", title);
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(newIntent);
            }
        }
    }
}