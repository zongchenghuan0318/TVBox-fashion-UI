package com.github.tvbox.osc.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.view.KeyEvent;
import android.view.Window;

import androidx.appcompat.view.WindowCallbackWrapper;

import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;

public final class RemoteKeySound {
    private static SoundPool soundPool;
    private static int moveSound;
    private static int confirmSound;

    private RemoteKeySound() {
    }

    public static void preload(Context context) {
        if (soundPool != null) {
            return;
        }
        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        Context appContext = context.getApplicationContext();
        moveSound = soundPool.load(appContext, R.raw.tv_menu_move, 1);
        confirmSound = soundPool.load(appContext, R.raw.tv_menu_confirm, 1);
    }

    public static void attach(Window window, Context context) {
        if (Hawk.get(HawkConfig.REMOTE_KEY_SOUND, true)) {
            preload(context);
        }
        window.setCallback(new WindowCallbackWrapper(window.getCallback()) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (soundPool != null && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getRepeatCount() == 0
                        && Hawk.get(HawkConfig.REMOTE_KEY_SOUND, true)) {
                    switch (event.getKeyCode()) {
                        case KeyEvent.KEYCODE_DPAD_UP:
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            soundPool.play(moveSound, 1f, 1f, 1, 0, 1f);
                            break;
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                            soundPool.play(confirmSound, 1f, 1f, 1, 0, 1f);
                            break;
                    }
                }
                return super.dispatchKeyEvent(event);
            }
        });
    }
}
