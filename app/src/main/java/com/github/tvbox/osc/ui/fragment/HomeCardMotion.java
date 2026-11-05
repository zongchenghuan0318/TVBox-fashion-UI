package com.github.tvbox.osc.ui.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;

enum HomeCardMotion {
    FILM("胶片走带"), STACK("桌面抽牌"), ORBIT("星轨环游"), LIFT("垂直升降"),
    WHEEL("3D 海报转盘"), PORTAL("穿过海报"), SLIT("光闸切片"), FAN("折扇选片"), FLAT("平直横移");

    final String label;
    HomeCardMotion(String label) { this.label = label; }

    static HomeCardMotion current() {
        String saved = Hawk.get(HawkConfig.HOME_CARD_STYLE, WHEEL.name());
        for (HomeCardMotion style : values()) if (style.name().equals(saved)) return style;
        return WHEEL;
    }

    void apply(View card, float offset, float n, boolean selected) {
        float distance = Math.min(1f, Math.abs(offset));
        float width = card.getWidth(), height = card.getHeight();
        float x = 0, y = 0, roll = 0, yaw = 0, pitch = 0, pivot = .5f;
        float scale = 1f - distance * .07f, alpha = 1f - distance * .19f;
        switch (this) {
            case FILM: scale = 1f; alpha = 1f - distance * .32f; break;
            case STACK: x = -n * width * .32f; roll = n * 12; y = distance * height * .055f; break;
            case ORBIT: x = -n * width * .20f; y = -distance * height * .11f;
                yaw = n * 30; scale -= distance * .12f; break;
            case LIFT: pitch = -n * 18; scale -= distance * .10f; break;
            case WHEEL: yaw = n * 42; y = distance * height * .10f; roll = -n * 3; break;
            case PORTAL: yaw = n * 22; scale = 1.08f - distance * .23f; break;
            case SLIT: yaw = n * 12; break;
            case FAN: pivot = 1f; roll = n * 16; x = -n * width * .22f; break;
            case FLAT: scale = 1.04f - distance * .04f; break;
        }
        card.setPivotX(width / 2); card.setPivotY(height * pivot);
        card.setTranslationX(x); card.setTranslationY(y);
        card.setRotation(roll); card.setRotationX(pitch); card.setRotationY(yaw);
        card.setScaleX(scale); card.setScaleY(scale); card.setAlpha(alpha);
        card.setTranslationZ((1f - distance + (selected ? .15f : 0f)) * 8f);
        card.setSelected(selected);
    }

    static void slice(View from, View to) {
        ImageView source = from.findViewById(R.id.ivThumb), target = to.findViewById(R.id.ivThumb);
        Drawable original = source.getDrawable();
        if (original == null || original.getConstantState() == null || target.getWidth() == 0) return;
        Drawable picture = original.getConstantState().newDrawable().mutate();
        int w = target.getWidth(), h = target.getHeight();
        float ratio = Math.max(w / (float)Math.max(1,picture.getIntrinsicWidth()), h / (float)Math.max(1,picture.getIntrinsicHeight()));
        int pw = Math.round(picture.getIntrinsicWidth()*ratio), ph = Math.round(picture.getIntrinsicHeight()*ratio);
        picture.setBounds((w-pw)/2,(h-ph)/2,(w+pw)/2,(h+ph)/2);
        ValueAnimator animator = ValueAnimator.ofFloat(0,1);
        Drawable overlay = new Drawable() {
            @Override public void draw(Canvas c) {
                float progress = animator.getAnimatedFraction();
                for (int i=0; i<5; i++) {
                    float travel = Math.max(0, Math.min(1, progress*1.4f-i*.1f));
                    c.save(); c.clipRect(i*w/5,0,(i+1)*w/5,h);
                    c.translate(0,(i%2==0?1:-1)*travel*h); picture.draw(c); c.restore();
                }
            }
            @Override public void setAlpha(int a) {}
            @Override public void setColorFilter(ColorFilter f) {}
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
        overlay.setBounds(0,0,w,h);
        ValueAnimator previous = (ValueAnimator) target.getTag(R.id.ivThumb);
        if (previous != null) previous.cancel();
        target.setTag(R.id.ivThumb, animator);
        animator.addUpdateListener(a -> overlay.invalidateSelf());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                target.getOverlay().remove(overlay); target.setTag(R.id.ivThumb,null);
            }
        });
        target.getOverlay().add(overlay); animator.setDuration(320); animator.start();
    }
}
