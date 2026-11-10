package com.glassmaind.slidevol;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Symmetrically centered, 24x24 vector speaker / mute icon. */
final class MuteButtonView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path icon = new Path();
    private final RectF oval = new RectF();
    private final SharedPreferences prefs;
    private final float density;
    private final MuteEngine engine;

    MuteButtonView(Context context, SharedPreferences preferences, MuteEngine mute) {
        super(context);
        prefs = preferences;
        engine = mute;
        density = getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Silenciar ou restaurar todos os volumes");
    }
    private float dp(float x) { return x*density; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(), h=getHeight(), centerX=w*.5f, centerY=h*.5f;
        if(w<dp(18)||h<dp(18))return;
        int accent=prefs.getInt("accentColor",Color.rgb(110,215,183));
        boolean muted=engine.isMuted();
        float radius=Math.min(w,h)*.47f;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0,0,0,h,
                Color.rgb(46,60,69),Color.rgb(21,30,37),Shader.TileMode.CLAMP));
        oval.set(dp(1.3f),dp(1.3f),w-dp(1.3f),h-dp(1.3f));
        canvas.drawRoundRect(oval,radius,radius,paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(muted ? Color.argb(185,254,153,153):Color.argb(114,206,234,228));
        canvas.drawRoundRect(oval,radius,radius,paint);

        // Draw at an independent, centered 24x24 coordinate system:
        // the speaker and its wave/cross never rely on the window's rectangular layout.
        canvas.save();
        float glyph = Math.min(w,h)*.59f;
        float scale = glyph/24f;
        canvas.translate(centerX-12f*scale,centerY-12f*scale);
        canvas.scale(scale,scale);

        paint.setColor(muted ? Color.rgb(255,152,152) : accent);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        icon.reset();
        icon.moveTo(11,5.0f);
        icon.lineTo(6,9.0f);
        icon.lineTo(3.5f,9.0f);
        icon.lineTo(3.5f,15.0f);
        icon.lineTo(6,15.0f);
        icon.lineTo(11,19.0f);
        icon.close();
        canvas.drawPath(icon,paint);

        if(muted) {
            canvas.drawLine(16,9,21,15,paint);
            canvas.drawLine(21,9,16,15,paint);
        } else {
            icon.reset();
            icon.moveTo(15,8.8f);
            icon.cubicTo(17.2f,10.5f,17.2f,13.5f,15,15.2f);
            canvas.drawPath(icon,paint);
            icon.reset();
            icon.moveTo(18,5.4f);
            icon.cubicTo(22.3f,8.7f,22.3f,15.3f,18,18.6f);
            canvas.drawPath(icon,paint);
        }
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
    }
}
