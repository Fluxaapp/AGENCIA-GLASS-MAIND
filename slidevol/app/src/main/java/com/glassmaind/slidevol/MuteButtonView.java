package com.glassmaind.slidevol;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

final class MuteButtonView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final SharedPreferences prefs;
    private final float density;
    private final MuteEngine muteEngine;

    MuteButtonView(Context context, SharedPreferences preferences, MuteEngine mute) {
        super(context);
        prefs=preferences;
        muteEngine=mute;
        density=getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }
    private float dp(float x) { return density*x; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(), h=getHeight(), cx=w/2, cy=h/2;
        int color=prefs.getInt("accentColor",Color.rgb(110,215,183));
        boolean muted=muteEngine.isMuted();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(244,22,31,36));
        oval.set(dp(1),dp(1),w-dp(1),h-dp(1));
        canvas.drawRoundRect(oval,Math.min(w,h)*.47f,Math.min(w,h)*.47f,paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(Color.argb(110,180,211,203));
        canvas.drawRoundRect(oval,Math.min(w,h)*.47f,Math.min(w,h)*.47f,paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(muted ? Color.rgb(252,145,141) : color);
        float u=Math.min(w,h)/38f;
        path.reset();
        path.moveTo(cx-9*u,cy-4*u);
        path.lineTo(cx-5*u,cy-4*u);
        path.lineTo(cx+1*u,cy-9*u);
        path.lineTo(cx+1*u,cy+9*u);
        path.lineTo(cx-5*u,cy+4*u);
        path.lineTo(cx-9*u,cy+4*u);
        path.close();
        canvas.drawPath(path,paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2.1f*u);
        if(muted){
            canvas.drawLine(cx+5*u,cy-6*u,cx+12*u,cy+6*u,paint);
            canvas.drawLine(cx+12*u,cy-6*u,cx+5*u,cy+6*u,paint);
        }else{
            oval.set(cx-1*u,cy-7*u,cx+13*u,cy+7*u);
            canvas.drawArc(oval,-57,114,false,paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
}
