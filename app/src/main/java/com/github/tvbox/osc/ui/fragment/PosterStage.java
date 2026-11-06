package com.github.tvbox.osc.ui.fragment;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import com.github.tvbox.osc.ui.tv.widget.HomeCardRecyclerView;
import com.studio.osc.R;

/** A visual timeline for the poster row. Positions and focus remain owned by the list. */
final class PosterStage {
    private ValueAnimator clock;
    private Drawable bands;
    private Runnable restore;
    private HomeCardRecyclerView row;
    private HomeCardMotion mode;
    private int from, to, direction;
    private float t;

    void start(HomeCardRecyclerView list, HomeCardMotion style, int old, int next, Runnable redraw) {
        stop();
        if (old == next || (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) return;
        row = list;
        restore = redraw;
        mode = style;
        from = old;
        to = next;
        direction = Integer.compare(next, old);
        t = 0;
        if (style == HomeCardMotion.FAULT) addBands(list.getLayoutManager().findViewByPosition(old));
        clock = ValueAnimator.ofFloat(0, 1);
        clock.setDuration(style == HomeCardMotion.STARS ? 860 : 820);
        clock.setInterpolator(new LinearInterpolator());
        clock.addUpdateListener(a -> {
            t = (float)a.getAnimatedValue();
            if (t >= 1) { stop(); return; }
            redraw.run();
            if (bands != null) bands.invalidateSelf();
        });
        clock.start();
    }

    void stop() {
        if (clock != null) {
            clock.removeAllUpdateListeners();
            clock.cancel();
            clock = null;
        }
        if (bands != null) row.getOverlay().remove(bands);
        bands = null;
        row = null;
        if (restore != null) {
            Runnable redraw = restore;
            restore = null;
            redraw.run();
        }
    }

    private static float smooth(float v) {
        v = Math.max(0, Math.min(1, v));
        return v*v*(3-2*v);
    }

    void apply(View card, int position) {
        if (row == null || (position != from && position != to)) return;
        boolean outgoing = position == from;
        float arch = (float)Math.sin(Math.PI * t);
        float w = card.getWidth(), h = card.getHeight();
        float dx = 0, dy = 0;
        if (mode == HomeCardMotion.PULL && outgoing) {
            float peak = t < .44f ? smooth(t/.44f) : 1-smooth((t-.44f)/.56f);
            dx = -direction*w*.57f*peak;
            dy = -h*.15f*peak;
            card.setRotation(card.getRotation()-direction*16*peak);
            if (t < .44f) card.setTranslationZ(16);
        } else if (mode == HomeCardMotion.STARS) {
            dx = direction*w*(outgoing ? -.46f : .20f)*arch;
            dy = h*(outgoing ? .21f : -.27f)*arch;
            card.setRotation(card.getRotation()+direction*(outgoing ? -11 : 8)*arch);
            if (outgoing && t < .49f) card.setTranslationZ(16);
        } else if (mode == HomeCardMotion.DROP && outgoing) {
            float fall = smooth(t/.72f);
            if (t < .72f) {
                card.setPivotY(h);
                card.setRotationX(-94*fall);
                dy = h*.19f*fall;
                card.setTranslationZ(16);
                card.setAlpha(card.getAlpha()*(1-fall));
            } else card.setAlpha(card.getAlpha()*smooth((t-.72f)/.28f));
        } else if (mode == HomeCardMotion.FAULT && outgoing && bands != null) {
            card.setAlpha(card.getAlpha()*smooth((t-.38f)/.52f));
        }
        card.setTranslationX(card.getTranslationX()+dx);
        float top = card.getTop()+card.getTranslationY();
        dy = Math.max(-top, Math.min(row.getHeight()-top-h,dy));
        card.setTranslationY(card.getTranslationY()+dy);
    }

    private void addBands(View old) {
        if (old == null) return;
        ImageView thumb = old.findViewById(R.id.ivThumb);
        Drawable original = thumb.getDrawable();
        if (original == null || original.getConstantState() == null) return;
        Drawable art = original.getConstantState().newDrawable().mutate();
        int w = thumb.getWidth(), h = thumb.getHeight();
        if (w <= 0 || h <= 0) return;
        float scale = Math.max(w/(float)Math.max(1,art.getIntrinsicWidth()), h/(float)Math.max(1,art.getIntrinsicHeight()));
        int aw = Math.round(art.getIntrinsicWidth()*scale), ah = Math.round(art.getIntrinsicHeight()*scale);
        art.setBounds((w-aw)/2,(h-ah)/2,(w+aw)/2,(h+ah)/2);
        final float left = old.getX() + (old.getWidth()-w)/2f;
        final float top = old.getY() + (old.getHeight()-h)/2f;
        bands = new Drawable() {
            @Override public void draw(Canvas c) {
                for (int i=0;i<3;i++) {
                    float p=smooth((t-i*.095f)/.74f);
                    c.save();
                    c.translate(left+(i==1?1:-1)*direction*w*1.1f*p, top+(i-1)*h*.16f*p);
                    c.rotate((i-1)*8*p,w*.5f,h*.5f);
                    c.clipRect(0,h*i/3,w,h*(i+1)/3);
                    art.setAlpha(Math.round(255*(1-p)));
                    art.draw(c);
                    c.restore();
                }
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(ColorFilter filter) {}
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
        bands.setBounds(0,0,row.getWidth(),row.getHeight());
        row.getOverlay().add(bands);
    }
}
