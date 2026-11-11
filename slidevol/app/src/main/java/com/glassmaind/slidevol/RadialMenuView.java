package com.glassmaind.slidevol;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * Translucent white, radial control modeled after the four-stage reference:
 * handle -> plus -> rotated X -> curved volume + mute + alarm.
 * All shapes are drawn in one transparent Canvas, no opaque background.
 */
final class RadialMenuView extends View {
    static final int NONE = 0, VOLUME = 1, MUTE = 2, ALARM = 3, HUB = 4;
    interface Actions {
        void onVolume(float fraction);
        void onMute();
        void onAlarm();
        void onDismiss();
        void onTouchState(boolean touching);
    }

    private final AudioManager audio;
    private final MuteEngine mute;
    private final SharedPreferences preferences;
    private final Actions actions;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arc = new Path();
    private final Path segment = new Path();
    private final Path glyph = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final RectF bounds = new RectF();
    private final float density;
    private ValueAnimator motion;
    private float expansion = 0f;
    private float visualVolume = -1f;
    private int selection = NONE;
    private boolean animatingOut;
    private boolean touchMoving;
    private boolean mirrored;

    RadialMenuView(Context context, AudioManager audio, MuteEngine mute,
                   SharedPreferences settings, Actions actions) {
        super(context);
        this.audio=audio;
        this.mute=mute;
        this.preferences=settings;
        this.actions=actions;
        density=getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setContentDescription("Controle lateral: alarme, volume e silêncio");
    }
    float dp(float size) { return size*density; }
    private float sat(float number) { return Math.max(0,Math.min(1,number)); }
    private float reveal(float start,float end) {
        return sat((expansion-start)/(end-start));
    }
    private int rgba(int alpha) {
        return Color.argb(Math.max(0,Math.min(255,alpha)),250,253,255);
    }
    private float hx() { return getWidth()-dp(37); }
    private float hy() { return getHeight()*.50f; }
    float alarmX() { return hx(); }
    float alarmY() { return hy()-dp(112); }
    float muteX() { return hx(); }
    float muteY() { return hy()+dp(112); }
    boolean isOpen() { return expansion > .03f && !animatingOut; }
    void select(int value) { selection=value; invalidate(); }
    void setMirrored(boolean left){ mirrored=left; invalidate(); }
    void setVolumePreview(float f) { visualVolume=sat(f);invalidate(); }
    void resetVolumePreview() { visualVolume=-1;invalidate(); }
    void invalidateAudio() { invalidate(); }
    void open() { animateTo(1f, preferences.getInt("openMs",330), null); }
    void close(Runnable finish) { animatingOut=true;animateTo(0f, preferences.getInt("closeMs",260), finish); }
    private void animateTo(float target,long millis,Runnable finish) {
        if(motion!=null)motion.cancel();
        animatingOut=target==0;
        motion=ValueAnimator.ofFloat(expansion,target);
        motion.setDuration(millis);
        motion.setInterpolator(new PathInterpolator(.18f,0f,.16f,1f));
        motion.addUpdateListener(a->{expansion=(float)a.getAnimatedValue();invalidate();});
        if(finish!=null)motion.addListener(new android.animation.AnimatorListenerAdapter(){
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a){cancelled=true;}
            @Override public void onAnimationEnd(android.animation.Animator a){if(!cancelled)finish.run();}
        });
        motion.start();
    }
    @Override protected void onDetachedFromWindow(){
        if(motion!=null)motion.cancel();
        super.onDetachedFromWindow();
    }

    /** Hit-test by actual pointer location, not sequential button taps. */
    int targetAt(float x,float y) {
        if(mirrored)x=getWidth()-x;
        float centerX=hx(),centerY=hy();
        if(distance(x,y,alarmX(),alarmY())<dp(34))return ALARM;
        if(distance(x,y,muteX(),muteY())<dp(34))return MUTE;
        if(distance(x,y,centerX,centerY)<dp(31))return HUB;
        if(x<centerX-dp(12) && x>centerX-dp(152) &&
            y>centerY-dp(100) && y<centerY+dp(101))return VOLUME;
        return NONE;
    }
    private float distance(float x,float y,float xx,float yy) {
        return (float)Math.hypot(x-xx,y-yy);
    }
    float valueAt(float y) {
        // Upright operation across the gracefully curved track.
        return sat((hy()+dp(82)-y)/dp(164));
    }
    private float currentVolume(){
        if(visualVolume>=0)return visualVolume;
        try{
            int max=Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            return sat(audio.getStreamVolume(AudioManager.STREAM_MUSIC)/(float)max);
        }catch(Exception ignored){return .5f;}
    }
    private void createArc(){
        float x=hx(),y=hy();
        arc.reset();
        // Start at bottom, trace a curved segment toward the upper node.
        arc.moveTo(x-dp(43),y+dp(74));
        arc.cubicTo(x-dp(112),y+dp(45),x-dp(114),y-dp(38),
                    x-dp(43),y-dp(82));
    }
    private void lineCircle(Canvas c,float x,float y,float radius,int alpha) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb((int)(alpha*.16f),235,247,252));
        c.drawCircle(x,y,radius,p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(.95f));
        p.setColor(rgba(alpha));
        c.drawCircle(x,y,radius,p);
        p.setStyle(Paint.Style.FILL);
    }
    private void dot(Canvas c,float x,float y,boolean active,float factor) {
        if(!active)return;
        p.setColor(rgba((int)(230*factor)));
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(x+dp(20),y-dp(19),dp(2.4f),p);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if(mirrored) {
            c.save();
            c.translate(getWidth(),0);
            c.scale(-1f,1f);
        }
        float centerX=hx(),centerY=hy();
        float hubProgress=reveal(0f,.32f);
        float trackProgress=reveal(.16f,.73f);
        float muteProgress=reveal(.34f,.8f);
        float alarmProgress=reveal(.55f,1f);
        float size = dp(20)*hubProgress;
        p.reset();p.setAntiAlias(true);

        // Radius grows from a near-invisible point, showing no square backdrop.
        lineCircle(c,centerX,centerY,Math.max(dp(1),size),(int)(200*hubProgress));
        c.save();
        c.translate(centerX,centerY);
        c.rotate(45f*hubProgress);
        p.setColor(rgba((int)(235*hubProgress)));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(dp(1.35f));
        c.drawLine(-dp(5.3f),0,dp(5.3f),0,p);
        c.drawLine(0,-dp(5.3f),0,dp(5.3f),p);
        c.restore();

        if(trackProgress>.01f) {
            createArc();
            measure.setPath(arc,false);
            float length=measure.getLength();
            segment.reset();
            measure.getSegment(0,length*trackProgress,segment,true);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(dp(22));
            p.setColor(Color.argb((int)(31*trackProgress),237,247,251));
            c.drawPath(segment,p);

            // Fine external outline and internal fill obey the exact angular curvature.
            p.setStrokeWidth(dp(24));
            p.setColor(Color.argb((int)(76*trackProgress),242,248,252));
            c.drawPath(segment,p);
            p.setStrokeWidth(dp(20.5f));
            p.setColor(Color.argb((int)(45*trackProgress),235,245,251));
            c.drawPath(segment,p);
            p.setStrokeWidth(dp(19));
            p.setShader(new LinearGradient(0,centerY+dp(94),0,centerY-dp(90),
                new int[]{preferences.getInt("accentColor",Color.WHITE), Color.argb(228,244,252,255), Color.argb(82,244,251,255)},
                new float[]{0f,.54f,1f},Shader.TileMode.CLAMP));
            segment.reset();
            measure.getSegment(0,length*trackProgress*currentVolume(),segment,true);
            c.drawPath(segment,p);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            // Discrete white point marking the current level along the curve.
            float[] point=new float[2], tangent=new float[2];
            if(measure.getPosTan(Math.max(0.01f,Math.min(length-.01f,
                    length*currentVolume()*trackProgress)),point,tangent)){
                p.setColor(Color.argb((int)(210*trackProgress),255,255,255));
                c.drawCircle(point[0],point[1],dp(2.2f),p);
            }
            dot(c,centerX-dp(72),centerY-dp(2),
                    selection==VOLUME,trackProgress);
        }
        if(muteProgress>.01f) {
            float x=muteX(),y=muteY();
            lineCircle(c,x,y,dp(18.5f)*muteProgress,(int)(175*muteProgress));
            c.save();c.translate(x,y);
            c.scale(muteProgress,muteProgress);
            p.setColor(rgba(226));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(dp(1.5f));
            glyph.reset();
            glyph.moveTo(-dp(8),-dp(3.2f));
            glyph.lineTo(-dp(4.2f),-dp(3.2f));
            glyph.lineTo(dp(1),-dp(7.5f));
            glyph.lineTo(dp(1),dp(7.5f));
            glyph.lineTo(-dp(4.2f),dp(3.2f));
            glyph.lineTo(-dp(8),dp(3.2f));
            glyph.close();
            c.drawPath(glyph,p);
            if(mute.isMuted()) {
                c.drawLine(dp(4),-dp(4),dp(10),dp(4),p);
                c.drawLine(dp(10),-dp(4),dp(4),dp(4),p);
            } else {
                glyph.reset();
                glyph.moveTo(dp(5),-dp(5));
                glyph.quadTo(dp(11),0,dp(5),dp(5));
                c.drawPath(glyph,p);
            }
            c.restore();
            dot(c,x,y,selection==MUTE||mute.isMuted(),muteProgress);
        }
        if(alarmProgress>.01f) {
            float x=alarmX(),y=alarmY();
            lineCircle(c,x,y,dp(18.5f)*alarmProgress,(int)(175*alarmProgress));
            c.save();c.translate(x,y);
            c.scale(alarmProgress,alarmProgress);
            p.setColor(rgba(226));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(dp(1.5f));
            bounds.set(-dp(6.5f),-dp(6.5f),dp(6.5f),dp(6.5f));
            c.drawOval(bounds,p);
            c.drawLine(0,-dp(4),0,0,p);
            c.drawLine(0,0,dp(3.8f),dp(2.1f),p);
            c.drawLine(-dp(8),-dp(9),-dp(4),-dp(12),p);
            c.drawLine(dp(8),-dp(9),dp(4),-dp(12),p);
            c.restore();
            boolean disabled = false;
            try{disabled=audio.getStreamVolume(AudioManager.STREAM_ALARM)==0;}
            catch(Exception ignored){}
            dot(c,x,y,selection==ALARM||disabled,alarmProgress);
        }
        if(mirrored)c.restore();
    }

    private boolean isTapMoved;
    private float touchX,touchY;
    private int pressedTarget=NONE;
    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX(),y=e.getY();
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchX=x;touchY=y;
                isTapMoved=false;
                pressedTarget=targetAt(x,y);
                select(pressedTarget);
                actions.onTouchState(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(Math.abs(x-touchX)>dp(5)||Math.abs(y-touchY)>dp(5))isTapMoved=true;
                int found=targetAt(x,y);
                if(found!=NONE) {
                    select(found);
                    if(found==VOLUME) {
                        setVolumePreview(valueAt(y));
                        actions.onVolume(valueAt(y));
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                actions.onTouchState(false);
                int up=targetAt(x,y);
                if(up==HUB && !isTapMoved) actions.onDismiss();
                else if(up==MUTE) actions.onMute();
                else if(up==ALARM) actions.onAlarm();
                else if(up==VOLUME) {
                    setVolumePreview(valueAt(y));
                    actions.onVolume(valueAt(y));
                }
                pressedTarget=NONE;
                resetVolumePreview();
                select(NONE);
                return true;
            case MotionEvent.ACTION_CANCEL:
                actions.onTouchState(false);
                pressedTarget=NONE;
                resetVolumePreview();
                select(NONE);
                return true;
            case MotionEvent.ACTION_OUTSIDE:
                actions.onTouchState(false);
                return true;
            default:return true;
        }
    }
}
