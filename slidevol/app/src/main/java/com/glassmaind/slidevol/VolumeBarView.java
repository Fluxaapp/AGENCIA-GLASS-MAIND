package com.glassmaind.slidevol;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.view.View;

final class VolumeBarView extends View {
    private final SharedPreferences prefs;
    private final AudioManager audio;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final float density;
    private boolean gestureActive = false;
    private boolean demoMode = false;

    VolumeBarView(Context context, SharedPreferences preferences, AudioManager manager) {
        super(context);
        prefs = preferences;
        audio = manager;
        density = getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }
    void setGestureActive(boolean enabled) {
        gestureActive = enabled;
        invalidate();
    }
    void setDemoMode() {
        demoMode = true;
        gestureActive = true;
        setAlpha(1);
        invalidate();
    }
    private float dp(float x) { return x * density; }
    private int accent() { return prefs.getInt("accentColor", Color.rgb(110, 215, 183)); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float width = getWidth(), height = getHeight();
        if (width <= 1 || height <= 1) return;
        final float radius = Math.min(width / 2f, dp(26));
        final int color = accent();
        int volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float fraction = demoMode ? .68f : (max > 0 ? volume / (float) max : 0f);

        rect.set(dp(.6f), dp(.6f), width-dp(.6f), height-dp(.6f));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(233, 20, 30, 34));
        canvas.drawRoundRect(rect,radius,radius,paint);

        paint.setColor(Color.argb(48, 230, 245, 241));
        float inset = dp(3);
        rect.set(inset, inset, width-inset, height-inset);
        canvas.drawRoundRect(rect, Math.max(2,radius-inset), Math.max(2,radius-inset),paint);

        float fill = Math.max(0, height-2*inset)*fraction;
        if(fill > 0f) {
            clip.reset();
            clip.addRoundRect(rect,Math.max(2,radius-inset),Math.max(2,radius-inset),Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            paint.setColor(color);
            canvas.drawRect(inset,height-inset-fill,width-inset,height-inset,paint);
            if(gestureActive){
                paint.setColor(Color.argb(50,255,255,255));
                canvas.drawRect(inset,height-inset-fill,width-inset,height-inset-fill+dp(5),paint);
            }
            canvas.restore();
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(104,200,238,223));
        rect.set(dp(.6f),dp(.6f),width-dp(.6f),height-dp(.6f));
        canvas.drawRoundRect(rect,radius,radius,paint);
        paint.setStyle(Paint.Style.FILL);

        if(gestureActive && width >= dp(56)) {
            paint.setColor(fraction < .5f ? Color.WHITE : Color.rgb(18,30,35));
            paint.setTextSize(Math.min(dp(13),width*.25f));
            paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(Math.round(fraction*100)+"%",width/2f,height*.49f,paint);
        }
    }
}
