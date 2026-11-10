package com.glassmaind.slidevol;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Glass pill with continuous drag feedback; the hardware media stream remains step based. */
final class VolumeBarView extends View {
    private final SharedPreferences prefs;
    private final AudioManager audio;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final float density;
    private float fraction;
    private boolean dragging;
    private boolean previewMode;
    private ValueAnimator animator;

    VolumeBarView(Context context, SharedPreferences preferences, AudioManager manager) {
        super(context);
        prefs = preferences;
        audio = manager;
        density = getResources().getDisplayMetrics().density;
        fraction = getSystemFraction();
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }
    private float clamp(float n) { return Math.max(0f, Math.min(1f, n)); }
    private float dp(float x) { return x * density; }
    private int accent() { return prefs.getInt("accentColor", Color.rgb(110, 215, 183)); }
    private int blend(int a, int b, float factor) {
        return Color.rgb(
                Math.round(Color.red(a) * (1-factor) + Color.red(b) * factor),
                Math.round(Color.green(a) * (1-factor) + Color.green(b) * factor),
                Math.round(Color.blue(a) * (1-factor) + Color.blue(b) * factor));
    }
    private float getSystemFraction() {
        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            return max > 0 ? clamp(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max) : 0;
        } catch (Exception ignored) { return .5f; }
    }
    private void cancelAnimation() {
        if (animator != null) { animator.cancel(); animator = null; }
    }
    void setDragFraction(float value) {
        cancelAnimation();
        dragging = true;
        fraction = clamp(value);
        invalidate();
    }
    void animateToSystemLevel() {
        dragging = false;
        animateTo(getSystemFraction(), prefs.getInt("animationMs", 170));
    }
    void refresh() {
        if (!previewMode && !dragging) animateToSystemLevel();
        else invalidate();
    }
    void animateTo(float target, int durationMs) {
        cancelAnimation();
        target = clamp(target);
        if (Math.abs(target - fraction) < .001f || durationMs <= 0) {
            fraction = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(fraction, target);
        animator.setDuration(Math.max(0, Math.min(450, durationMs)));
        animator.setInterpolator(new DecelerateInterpolator(1.4f));
        animator.addUpdateListener(a -> {
            fraction = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }
    void setDemoMode() {
        previewMode = true;
        dragging = true;
        cancelAnimation();
        fraction = .68f;
        setAlpha(1);
        invalidate();
    }
    void setGestureActive(boolean active) {
        dragging = active;
        invalidate();
    }
    @Override protected void onDetachedFromWindow() {
        cancelAnimation();
        super.onDetachedFromWindow();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth(), h = getHeight();
        if (w < dp(14) || h < dp(34)) return;
        int accent = accent();
        float outer = Math.min(w * .5f, dp(31));
        float inset = dp(3.8f);
        float radius = Math.max(dp(8), outer-inset);

        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(188, 15, 24, 30));
        paint.setShadowLayer(dp(3.5f), 0, dp(1.5f), Color.argb(60,0,0,0));
        rect.set(dp(1.4f),dp(1.1f),w-dp(1.4f),h-dp(1.4f));
        canvas.drawRoundRect(rect, outer, outer, paint);
        paint.clearShadowLayer();

        rect.set(inset,inset,w-inset,h-inset);
        paint.setColor(Color.argb(108,48,62,70));
        canvas.drawRoundRect(rect,radius,radius,paint);

        // A single rounded clipping path preserves the capsule silhouette at any fill level.
        clip.reset();
        clip.addRoundRect(rect,radius,radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        float innerBottom = h-inset;
        float fullHeight = h-inset*2;
        float fillHeight = fullHeight*clamp(fraction);
        if(fillHeight > .1f) {
            int bottom = blend(accent,Color.BLACK,.13f);
            int top = blend(accent,Color.WHITE,.34f);
            paint.setShader(new LinearGradient(0,innerBottom,0,innerBottom-fillHeight,
                    bottom,top, Shader.TileMode.CLAMP));
            canvas.drawRect(inset,innerBottom-fillHeight,w-inset,innerBottom,paint);
            paint.setShader(null);

            // Soft accent highlight on the fluid surface.
            paint.setColor(Color.argb(94,255,255,255));
            float y = innerBottom-fillHeight;
            canvas.drawRect(inset,y,w-inset,Math.min(innerBottom,y+dp(1.4f)),paint);
        }
        // The subtle upper glass reflection looks good in either dark or light wallpapers.
        paint.setShader(new LinearGradient(0,0,0,h*.52f,
                Color.argb(55,255,255,255),Color.TRANSPARENT,Shader.TileMode.CLAMP));
        canvas.drawRect(inset,inset,w-inset,h*.52f,paint);
        paint.setShader(null);
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.05f));
        paint.setColor(Color.argb(132,214,229,228));
        rect.set(dp(1.6f),dp(1.6f),w-dp(1.6f),h-dp(1.6f));
        canvas.drawRoundRect(rect,outer,outer,paint);
        paint.setColor(Color.argb(38,255,255,255));
        paint.setStrokeWidth(dp(.8f));
        rect.set(inset,inset,w-inset,h-inset);
        canvas.drawRoundRect(rect,radius,radius,paint);
        paint.setStyle(Paint.Style.FILL);

        // Percentage only when a sufficiently wide control is selected.
        if (dragging && w >= dp(45) && h >= dp(105)) {
            String label = Math.round(clamp(fraction)*100)+"%";
            paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.min(dp(11),w*.24f));
            paint.setColor(Color.argb(212,255,255,255));
            float labelY=h*.53f;
            paint.setShadowLayer(dp(2f),0,dp(.5f),Color.argb(140,0,0,0));
            canvas.drawText(label,w/2f,labelY,paint);
            paint.clearShadowLayer();
        }
    }
}
