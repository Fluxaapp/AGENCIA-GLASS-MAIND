package com.glassmaind.slidevol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class SlideVolService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String STOP = "com.glassmaind.slidevol.STOP";
    static final String TOGGLE_MUTE = "com.glassmaind.slidevol.TOGGLE_MUTE";
    private WindowManager window;
    private WindowManager.LayoutParams barParams, muteParams;
    private VolumeBarView bar;
    private MuteButtonView muteButton;
    private SharedPreferences prefs;
    private AudioManager audio;
    private MuteEngine muteEngine;
    private Handler mainHandler;
    private Runnable holdTask, fadeTask;
    private int displayW, displayH, startVolume, initialWindowX, initialWindowY;
    private float startFraction;
    private float downX, downY;
    private boolean touching, moved, movingWindow, swiping;

    private int dp(float number) {
        return (int)(number*getResources().getDisplayMetrics().density+.5f);
    }
    private float activeAlpha() {
        return Math.max(.15f,Math.min(1f,prefs.getInt("activeOpacity",94)/100f));
    }
    private float idleAlpha() {
        return Math.max(0f,Math.min(1f,prefs.getInt("idleOpacity",6)/100f));
    }
    @Override public void onCreate() {
        super.onCreate();
        window=(WindowManager)getSystemService(WINDOW_SERVICE);
        prefs=getSharedPreferences(SlideVolActivity.PREFS,MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        muteEngine=new MuteEngine(this);
        mainHandler=new Handler(Looper.getMainLooper());
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
        window.getDefaultDisplay().getRealMetrics(dm);
        displayW=dm.widthPixels;displayH=dm.heightPixels;
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
            new NotificationChannel("slidevol","SlideVol em execução",NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (intent!=null && STOP.equals(intent.getAction())) {
            stopSelf();return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();return START_NOT_STICKY;
        }
        if (intent!=null && TOGGLE_MUTE.equals(intent.getAction()) && bar!=null) {
            onMuteClicked();
            return START_STICKY;
        }
        PendingIntent activity=PendingIntent.getActivity(this,1,new Intent(this,SlideVolActivity.class),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,SlideVolService.class).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification note=new Notification.Builder(this,"slidevol")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("SlideVol ativo")
            .setContentText("Arraste a barra na lateral para alterar o volume")
            .setContentIntent(activity)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,"Desativar",stop).build())
            .build();
        startForeground(24,note);
        if (bar==null) showBar();
        return START_STICKY;
    }

    private void showBar() {
        bar=new VolumeBarView(this,prefs,audio);
        barParams=new WindowManager.LayoutParams(
            dp(prefs.getInt("width",30)),dp(prefs.getInt("height",162)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        barParams.gravity=Gravity.TOP|Gravity.LEFT;
        computePosition();
        bar.setOnTouchListener((view,e)->handleGesture(e));
        bar.setAlpha(idleAlpha());
        try {
            window.addView(bar,barParams);
            syncMuteButton();
        } catch (Exception ex) {
            bar=null;stopSelf();
        }
    }

    private int maxVertical() {
        int extra = prefs.getBoolean("showMute",true)
            ? dp(prefs.getInt("muteSize",38)+prefs.getInt("muteSpacing",9)+14)
            : dp(14);
        return Math.max(0,displayH-barParams.height-extra);
    }
    private void computePosition() {
        String side=prefs.getString("side","right");
        int maxX=Math.max(0,displayW-barParams.width);
        if("free".equals(side)) {
            barParams.x=Math.max(0,Math.min(maxX,Math.round(prefs.getFloat("freeX",.9f)*maxX)));
            barParams.y=Math.max(0,Math.min(maxVertical(),
                Math.round(prefs.getFloat("freeY",.34f)*maxVertical())));
        } else {
            int margin = dp(Math.max(0,Math.min(120,prefs.getInt("edgeDistance",10))));
            barParams.x="left".equals(side)
                ? Math.max(0,Math.min(maxX,margin))
                : Math.max(0,maxX-margin);
            barParams.y=Math.round(Math.max(0,Math.min(100,prefs.getInt("vertical",30)))/100f * maxVertical());
        }
    }
    private void positionMuteButton() {
        if(muteParams==null||barParams==null) return;
        muteParams.x=Math.max(0,Math.min(displayW-muteParams.width,
            barParams.x+(barParams.width-muteParams.width)/2));
        muteParams.y=Math.max(0,Math.min(displayH-muteParams.height-dp(5),
            barParams.y+barParams.height+
                dp(Math.max(0,Math.min(75,prefs.getInt("muteSpacing",9))))));
    }
    private void syncMuteButton() {
        if(bar==null) return;
        if(!prefs.getBoolean("showMute",true)) {
            if(muteButton!=null) {
                try{window.removeView(muteButton);}catch(Exception ignored){}
                muteButton=null;muteParams=null;
            }
            return;
        }
        if(muteButton==null) {
            muteButton=new MuteButtonView(this,prefs,muteEngine);
            int size=dp(Math.max(28,Math.min(76,prefs.getInt("muteSize",38))));
            muteParams=new WindowManager.LayoutParams(size,size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
            muteParams.gravity=Gravity.TOP|Gravity.LEFT;
            positionMuteButton();
            muteButton.setOnClickListener(view->onMuteClicked());
            muteButton.setAlpha(idleAlpha());
            try {window.addView(muteButton,muteParams);}
            catch(Exception ignored){muteButton=null;muteParams=null;}
        } else {
            int size=dp(Math.max(28,Math.min(76,prefs.getInt("muteSize",38))));
            muteParams.width=size;
            muteParams.height=size;
            positionMuteButton();
            try{window.updateViewLayout(muteButton,muteParams);}catch(Exception ignored){}
            muteButton.invalidate();
        }
    }
    private void showActive() {
        if(fadeTask!=null)mainHandler.removeCallbacks(fadeTask);
        if(bar!=null) {
            bar.animate().cancel();
            bar.setAlpha(activeAlpha());
            bar.setGestureActive(true);
        }
        if(muteButton!=null) {
            muteButton.animate().cancel();
            muteButton.setAlpha(activeAlpha());
        }
    }
    private void fadeToIdle() {
        if(fadeTask!=null)mainHandler.removeCallbacks(fadeTask);
        final int delay=prefs.getInt("fadeDelay",2);
        fadeTask=()->{
            if(bar!=null){
                bar.setGestureActive(false);
                bar.animate().alpha(idleAlpha()).setDuration(400).start();
            }
            if(muteButton!=null)muteButton.animate().alpha(idleAlpha()).setDuration(400).start();
        };
        mainHandler.postDelayed(fadeTask,Math.max(0,delay)*1000L);
    }

    private boolean handleGesture(MotionEvent e) {
        float x=e.getRawX(),y=e.getRawY();
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touching=true; moved=false; movingWindow=false; swiping=false;
                downX=x;downY=y;
                initialWindowX=barParams.x;initialWindowY=barParams.y;
                startVolume=muteEngine.rememberedMediaVolume();
                int maxAtStart=Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                startFraction=Math.max(0f,Math.min(1f,startVolume/(float)maxAtStart));
                holdTask=()->{if(touching && !moved) movingWindow=true;};
                mainHandler.postDelayed(holdTask,700);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx=x-downX,dy=y-downY;
                if(!moved && (Math.abs(dx)>dp(6)||Math.abs(dy)>dp(6))){
                    moved=true;
                    if(holdTask!=null)mainHandler.removeCallbacks(holdTask);
                }
                if(movingWindow){
                    showActive();
                    barParams.x=Math.max(0,Math.min(displayW-barParams.width,
                        initialWindowX+Math.round(dx)));
                    barParams.y=Math.max(0,Math.min(maxVertical(),
                        initialWindowY+Math.round(dy)));
                    try{window.updateViewLayout(bar,barParams);}catch(Exception ignored){}
                    syncMuteButton();
                }else if(Math.abs(dy)>dp(4) || swiping){
                    if(!swiping) {
                        swiping=true;
                        showActive();
                        if(muteEngine.isMuted()){
                            muteEngine.restore();
                            startVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                            int restoredMax=Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                            startFraction=startVolume/(float)restoredMax;
                            if(muteButton!=null)muteButton.invalidate();
                        }
                    }
                    // Draw the percentage continuously even though Android exposes
                    // discrete media-volume steps (typically 15-25).
                    int max=Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                    int gestureRange=Math.max(140,Math.min(600,prefs.getInt("gestureRange",350)));
                    float progress=Math.max(0f,Math.min(1f,
                        startFraction-dy/(float)dp(gestureRange)));
                    int next=Math.round(progress*max);
                    try {
                        if(next!=audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC,next,0);
                    }catch(Exception ignored){}
                    if(bar!=null)bar.setDragFraction(progress);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching=false;
                if(holdTask!=null)mainHandler.removeCallbacks(holdTask);
                if(movingWindow){
                    float xx=barParams.x/(float)Math.max(1,displayW-barParams.width);
                    float yy=barParams.y/(float)Math.max(1,maxVertical());
                    prefs.edit().putString("side","free")
                        .putFloat("freeX",xx).putFloat("freeY",yy).apply();
                }
                if(swiping && bar!=null)bar.animateToSystemLevel();
                if(swiping||movingWindow)fadeToIdle();
                // A simple tap is deliberately not a volume action. Android still
                // consumes it inside the narrow overlay touch region.
                return true;
            default:return true;
        }
    }
    private void onMuteClicked() {
        muteEngine.toggle();
        if(muteButton!=null)muteButton.invalidate();
        showActive();
        if(bar!=null)bar.animateToSystemLevel();
        fadeToIdle();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key) {
        if(bar==null||barParams==null) return;
        if(key.equals("side")||key.equals("vertical")||key.equals("freeX")||
            key.equals("freeY")||key.equals("width")||key.equals("height")||
            key.equals("showMute")||key.equals("edgeDistance")||
            key.equals("muteSize")||key.equals("muteSpacing")) {
            barParams.width=dp(prefs.getInt("width",30));
            barParams.height=dp(prefs.getInt("height",162));
            computePosition();
            try{window.updateViewLayout(bar,barParams);}catch(Exception ignored){}
            syncMuteButton();
        }
        if(key.equals("idleOpacity") && !swiping && !movingWindow){
            if(bar!=null)bar.setAlpha(idleAlpha());
            if(muteButton!=null)muteButton.setAlpha(idleAlpha());
        }
        if(bar!=null)bar.refresh();
        if(muteButton!=null)muteButton.invalidate();
    }
    @Override public void onDestroy() {
        if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(this);
        if(mainHandler!=null){
            if(holdTask!=null)mainHandler.removeCallbacks(holdTask);
            if(fadeTask!=null)mainHandler.removeCallbacks(fadeTask);
        }
        if(muteButton!=null){
            try{window.removeView(muteButton);}catch(Exception ignored){}
            muteButton=null;
        }
        if(bar!=null){
            try{window.removeView(bar);}catch(Exception ignored){}
            bar=null;
        }
        // Never leave alarms or app sound muted when the controller is disabled.
        if(muteEngine!=null && muteEngine.isMuted())muteEngine.restore();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
