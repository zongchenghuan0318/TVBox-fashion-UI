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
    FILM("胶片走带", .87f, .76f), STACK("桌面抽牌", .72f, .70f), ORBIT("星轨环游", .72f, .63f),
    LIFT("垂直升降", 1.42f, .66f), WHEEL("3D 海报转盘", .74f, .73f), PORTAL("穿过海报", .77f, .73f),
    SLIT("光闸切片", .80f, .75f), FAN("折扇选片", .72f, .64f), FLAT("平直横移", 1.15f, .77f);

    final String label;
    final float aspect, heightFraction;
    HomeCardMotion(String label, float aspect, float heightFraction) {
        this.label = label;
        this.aspect = aspect;
        this.heightFraction = heightFraction;
    }

    static HomeCardMotion current() {
        String saved = Hawk.get(HawkConfig.HOME_CARD_STYLE, WHEEL.name());
        for (HomeCardMotion style : values()) if (style.name().equals(saved)) return style;
        return WHEEL;
    }

    private final android.graphics.RectF bounds = new android.graphics.RectF();

    void apply(View card, float offset, float n, boolean selected, float steps, float displacement) {
        float distance = Math.min(1f, Math.abs(offset));
        float width = card.getWidth(), height = card.getHeight();
        float x = 0, y = 0, roll = 0, yaw = 0, pitch = 0, pivot = .5f;
        float scale = 1f - distance * .07f, alpha = 1f - distance * .19f;
        boolean layered = this == STACK || this == FAN;
        float rank = Math.abs(steps);
        float layer = Math.copySign(Math.min(rank, 4f), steps);
        card.setVisibility(View.VISIBLE);
        switch (this) {
            case FILM: scale = 1f; alpha = 1f - distance * .32f; break;
            case STACK:
                x = layer * width * .56f - displacement;
                roll = layer * 10f;
                y = Math.min(4f, Math.abs(steps)) * height * .028f;
                scale = Math.max(.69f, 1f - Math.abs(steps) * .065f);
                break;
            case ORBIT: {
                float angle = Math.min(1.45f, Math.abs(steps) * .36f);
                float radius = Math.min(width * 3.65f, ((View)card.getParent()).getWidth() * .46f);
                x = Math.signum(steps) * radius * (float)Math.sin(angle) - displacement;
                y = -Math.min(Math.abs(steps), 4f) * height * .115f;
                roll = -Math.max(-4f, Math.min(4f, steps)) * 7f;
                scale = 1.08f / (1f + Math.abs(steps) * .13f);
                alpha = Math.max(.4f, 1f - Math.abs(steps) * .16f);
                break;
            }
            case LIFT:
                scale = Math.max(.76f, 1f - rank * .16f);
                float rowHeight = ((View)card.getParent()).getHeight();
                y = steps * (rowHeight * .50f + height * .10f) - displacement;
                alpha = Math.max(.32f, 1f - rank * .43f);
                break;
            case WHEEL:
                float side = Math.max(-3f, Math.min(3f, steps));
                yaw = -side * 19f;
                scale = 1.075f / (1f + Math.abs(steps) * .13f);
                x = steps * width * .82f - displacement;
                y = Math.abs(side) * height * .045f;
                alpha = Math.max(.42f, 1f - Math.abs(steps) * .17f);
                break;
            case PORTAL: yaw = n * 22; scale = 1.08f - distance * .23f; break;
            case SLIT: yaw = n * 12; break;
            case FAN:
                pivot = 1.18f;
                roll = layer * 12f;
                x = layer * width * .34f - displacement;
                scale = Math.max(.74f, 1f - Math.abs(steps) * .045f);
                break;
            case FLAT: scale = 1.04f - distance * .04f; break;
        }
        if (layered && rank > 3f) {
            alpha *= Math.max(0f, Math.min(1f, (3.5f - rank) * 2f));
        }
        if (this != LIFT) {
            View row = (View)card.getParent();
            y += (row.getHeight() - height) * .5f - card.getTop();
        }
        card.setPivotX(width / 2); card.setPivotY(height * pivot);
        card.setTranslationX(x); card.setTranslationY(y);
        card.setRotation(roll); card.setRotationX(pitch); card.setRotationY(yaw);
        card.setScaleX(scale); card.setScaleY(scale); card.setAlpha(alpha);
        if (this != LIFT && card.getParent() instanceof View) {
            bounds.set(0, 0, width, height);
            card.getMatrix().mapRect(bounds);
            float top = card.getTop() + bounds.top;
            float bottom = card.getTop() + bounds.bottom;
            float rowHeight = ((View)card.getParent()).getHeight();
            if (bottom > rowHeight) card.setTranslationY(y - (bottom - rowHeight));
            else if (top < 0) card.setTranslationY(y - top);
        }
        card.setTranslationZ(12f / (1f + Math.abs(steps)) + (selected ? .1f : 0f));
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
