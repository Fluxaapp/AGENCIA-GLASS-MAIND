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
import android.media.AudioManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

/** Floating three-stream Android mixer. Built as one canvas so it remains lightweight. */
final class ExpandedVolumePanel extends View {
    interface Listener {
        void onClose();
        void onInteraction();
    }

    private static final int[] STREAMS = {
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_ALARM
    };
    private static final String[] LABELS = {"Mídia", "Notific.", "Alarme"};
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final SharedPreferences prefs;
    private final AudioManager audio;
    private final MuteEngine mute;
    private final Listener listener;
    private final float density;
    private final float[] fractions = new float[] {-1, -1, -1};
    private int selected = -1;
    private boolean closingPress = false;
    private boolean showedRestriction = false;

    ExpandedVolumePanel(Context ctx, SharedPreferences prefs, AudioManager audio,
                        MuteEngine mute, Listener listener) {
        super(ctx);
        this.prefs=prefs;
        this.audio=audio;
        this.mute=mute;
        this.listener=listener;
        this.density=getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setContentDescription("Controle expandido de mídia, notificações e alarme");
    }
    private float dp(float x) { return x*density; }
    private float clamp(float x) { return Math.max(0f,Math.min(1f,x)); }
    private int accent() { return prefs.getInt("accentColor", Color.rgb(110,215,183)); }
    private int color(int idx) {
        if(idx==0) return accent();
        if(idx==1) return Color.rgb(135,195,247);
        return Color.rgb(255,199,129);
    }
    private int level(int idx) {
        try { return audio.getStreamVolume(STREAMS[idx]); }
        catch(Exception ignored) { return 0; }
    }
    private int maximum(int idx) {
        try { return Math.max(1,audio.getStreamMaxVolume(STREAMS[idx])); }
        catch(Exception ignored) { return 1; }
    }
    private float fraction(int idx) {
        return fractions[idx]>=0f ? clamp(fractions[idx]) : clamp(level(idx)/(float)maximum(idx));
    }
    private float centerX(int index) {
        return getWidth()*(index+.5f)/3f;
    }
    private float trackTop() {return dp(67);}
    private float trackBottom() {return getHeight()-dp(75);}
    private float trackWidth() {return Math.min(dp(58),getWidth()*.185f);}

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        if(w<dp(170)||h<dp(240))return;
        p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.FILL);
        p.setShadowLayer(dp(5),0,dp(3),Color.argb(85,0,0,0));
        p.setColor(Color.argb(240,20,31,40));
        oval.set(dp(1.3f),dp(1.3f),w-dp(1.3f),h-dp(1.3f));
        canvas.drawRoundRect(oval,dp(27),dp(27),p);
        p.clearShadowLayer();

        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));
        p.setColor(Color.argb(100,211,230,226));
        canvas.drawRoundRect(oval,dp(27),dp(27),p);
        p.setStyle(Paint.Style.FILL);

        p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        p.setTextSize(dp(12));p.setTextAlign(Paint.Align.LEFT);
        p.setColor(Color.argb(235,236,247,244));
        canvas.drawText("ÁUDIO",dp(22),dp(34),p);
        p.setColor(Color.argb(170,188,207,208));
        p.setTextSize(dp(9));p.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("✕",w-dp(21),dp(34),p);

        for(int i=0;i<3;i++) {
            float center=centerX(i);
            float left=center-trackWidth()*.5f;
            float right=center+trackWidth()*.5f;
            float top=trackTop(), bottom=trackBottom();
            float radius=trackWidth()*.48f;
            oval.set(left,top,right,bottom);
            p.setColor(Color.argb(115,54,70,78));
            canvas.drawRoundRect(oval,radius,radius,p);

            path.reset();
            path.addRoundRect(oval,radius,radius,Path.Direction.CW);
            canvas.save();canvas.clipPath(path);
            float fill=clamp(fraction(i))*(bottom-top);
            if(fill>0) {
                int c=color(i);
                int light=Color.rgb(
                    (int)(Color.red(c)*.7f+255*.3f),
                    (int)(Color.green(c)*.7f+255*.3f),
                    (int)(Color.blue(c)*.7f+255*.3f));
                p.setShader(new LinearGradient(left,bottom,right,bottom-fill,
                    c,light,Shader.TileMode.CLAMP));
                canvas.drawRect(left,bottom-fill,right,bottom,p);
                p.setShader(null);
                p.setColor(Color.argb(76,255,255,255));
                canvas.drawRect(left,bottom-fill,right,bottom-fill+dp(1.5f),p);
            }
            canvas.restore();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(Color.argb(110,210,233,230));
            canvas.drawRoundRect(oval,radius,radius,p);
            p.setStyle(Paint.Style.FILL);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
            p.setTextSize(dp(11));
            p.setColor(Color.WHITE);
            canvas.drawText(Math.round(fraction(i)*100)+"%",center,top+dp(25),p);
            paintIcon(canvas,i,center,bottom+dp(22));
            p.setTextSize(dp(11));
            p.setColor(Color.argb(222,229,239,238));
            canvas.drawText(LABELS[i],center,bottom+dp(52),p);
        }
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(9));
        p.setColor(Color.argb(158,185,201,204));
        canvas.drawText("Deslize para ajustar cada volume",w*.5f,h-dp(9),p);
    }
    private void paintIcon(Canvas canvas,int index,float cx,float cy) {
        canvas.save();
        canvas.translate(cx,cy);
        float scale=dp(1.0f);
        canvas.scale(scale,scale);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(1.8f);
        p.setColor(index==0?accent():Color.rgb(222,237,237));
        path.reset();
        if(index==0) {
            path.moveTo(-9,-3);path.lineTo(-5,-3);path.lineTo(0,-7);
            path.lineTo(0,7);path.lineTo(-5,3);path.lineTo(-9,3);path.close();
            canvas.drawPath(path,p);
            path.reset();path.moveTo(4,-5);path.cubicTo(8,-2,8,2,4,5);canvas.drawPath(path,p);
        } else if(index==1) {
            path.moveTo(-7,4);path.lineTo(-4,1);path.lineTo(-4,-3);
            path.cubicTo(-4,-10,4,-10,4,-3);
            path.lineTo(4,1);path.lineTo(7,4);path.close();
            canvas.drawPath(path,p);
            canvas.drawLine(-8,6,8,6,p);
            path.reset();path.moveTo(-2,9);path.quadTo(0,11,2,9);canvas.drawPath(path,p);
        } else {
            oval.set(-7,-7,7,7);canvas.drawOval(oval,p);
            canvas.drawLine(0,-5,0,0,p);
            canvas.drawLine(0,0,4,2,p);
            canvas.drawLine(-9,-9,-5,-12,p);
            canvas.drawLine(9,-9,5,-12,p);
        }
        p.setStyle(Paint.Style.FILL);
        canvas.restore();
    }
    private int trackAt(float x) {
        return Math.max(0,Math.min(2,(int)(x*3f/Math.max(1,getWidth()))));
    }
    private void adjust(int index,float y) {
        if(mute.isMuted()) mute.restore();
        float f=clamp((trackBottom()-y)/Math.max(dp(40),trackBottom()-trackTop()));
        fractions[index]=f;
        int desired=Math.round(maximum(index)*f);
        try {
            if(level(index)!=desired)audio.setStreamVolume(STREAMS[index],desired,0);
        } catch(SecurityException blocked) {
            if(!showedRestriction) {
                showedRestriction=true;
                Toast.makeText(getContext(),"O Android bloqueou esse volume. Verifique o acesso a Não Perturbe.",Toast.LENGTH_LONG).show();
            }
        } catch(Exception ignored) { }
        invalidate();
        listener.onInteraction();
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if(e.getY()<dp(56)) {
                    closingPress=e.getX()>getWidth()-dp(60);
                    selected=-1;
                    return true;
                }
                closingPress=false;
                selected=trackAt(e.getX());
                adjust(selected,e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if(selected>=0) adjust(selected,e.getY());
                return true;
            case MotionEvent.ACTION_UP:
                if(closingPress&&e.getX()>getWidth()-dp(65)&&e.getY()<dp(60)){
                    closingPress=false;
                    listener.onClose();
                    return true;
                }
                if(selected>=0){
                    adjust(selected,e.getY());
                    fractions[selected]=-1;
                    selected=-1;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                selected=-1;closingPress=false;
                for(int i=0;i<fractions.length;i++)fractions[i]=-1;
                invalidate();
                return true;
            default:return true;
        }
    }
    void refresh() {invalidate();}
}
