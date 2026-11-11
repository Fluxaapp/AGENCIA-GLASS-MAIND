package com.glassmaind.slidevol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * A narrow, discreet edge hit area captures the FIRST finger down.
 * A separate radial window shows the menu and receives subsequent interactions.
 * During the originating finger gesture it is FLAG_NOT_TOUCHABLE so the handle
 * continues receiving MOVE and UP, enabling a full one-gesture operation.
 */
public class SlideVolService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String STOP = "com.glassmaind.slidevol.STOP";
    static final String TOGGLE_MUTE = "com.glassmaind.slidevol.TOGGLE_MUTE";
    private WindowManager window;
    private WindowManager.LayoutParams handleParams, menuParams;
    private EdgeHandle handle;
    private RadialMenuView radial;
    private SharedPreferences prefs;
    private AudioManager audio;
    private MuteEngine muteEngine;
    private Handler ui;
    private Runnable closeTimer;
    private int screenW,screenH;
    private boolean holding, duringInitialGesture, draggingVolume, closing, volumeHover;
    private float startX,startY,previousX,previousY, volumeHoverStartY;
    private int pointerTarget;
    private int locationX, locationY;

    private int dp(float n) {
        return (int)(n*getResources().getDisplayMetrics().density+.5f);
    }
    private float idleAlpha(){
        return Math.max(0f,Math.min(1f,prefs.getInt("idleOpacity",20)/100f));
    }
    private float activeAlpha(){
        return Math.max(.15f,Math.min(1f,prefs.getInt("activeOpacity",100)/100f));
    }
    @Override public void onCreate(){
        super.onCreate();
        window=(WindowManager)getSystemService(WINDOW_SERVICE);
        prefs=getSharedPreferences(SlideVolActivity.PREFS,MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        muteEngine=new MuteEngine(this);
        ui=new Handler(Looper.getMainLooper());
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
        window.getDefaultDisplay().getRealMetrics(dm);
        screenW=dm.widthPixels;
        screenH=dm.heightPixels;
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
            new NotificationChannel("slidevol","SlideVol",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && STOP.equals(intent.getAction())){
            stopSelf();
            return START_NOT_STICKY;
        }
        if(!Settings.canDrawOverlays(this)){
            stopSelf();
            return START_NOT_STICKY;
        }
        if(intent!=null && TOGGLE_MUTE.equals(intent.getAction())&&handle!=null){
            toggleMute();
            return START_STICKY;
        }
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,SlideVolActivity.class),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,
            new Intent(this,SlideVolService.class).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"slidevol")
            .setSmallIcon(R.drawable.ic_status_slidevol)
            .setContentTitle("SlideVol ativo")
            .setContentText("Deslize na lateral para controlar o volume")
            .setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,"Desativar",stop).build())
            .build();
        startForeground(24,n);
        if(handle==null)showHandle();
        return START_STICKY;
    }
    private void setPosition() {
        if(handleParams==null)return;
        int gap=dp(Math.max(0,Math.min(120,prefs.getInt("edgeDistance",10))));
        String side=prefs.getString("side","right");
        if("free".equals(side)){
            handleParams.x=Math.max(0,Math.min(screenW-handleParams.width,
                Math.round(prefs.getFloat("freeX",.9f)*(screenW-handleParams.width))));
            handleParams.y=Math.max(0,Math.min(screenH-handleParams.height,
                Math.round(prefs.getFloat("freeY",.34f)*(screenH-handleParams.height))));
        }else{
            handleParams.x="left".equals(side)?
                Math.min(screenW-handleParams.width,gap):
                Math.max(0,screenW-handleParams.width-gap);
            handleParams.y=Math.max(0,Math.min(screenH-handleParams.height,
                Math.round(prefs.getInt("vertical",30)/100f*(screenH-handleParams.height))));
        }
    }
    private void showHandle(){
        handle=new EdgeHandle(this);
        handleParams=new WindowManager.LayoutParams(dp(35),dp(86),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        handleParams.gravity=Gravity.TOP|Gravity.LEFT;
        setPosition();
        handle.setAlpha(idleAlpha());
        handle.setOnTouchListener((v,e)->onHandleTouch(e));
        try{window.addView(handle,handleParams);}
        catch(Exception ignored){handle=null;stopSelf();}
    }
    private int menuWidth(){return dp(235);}
    private int menuHeight(){return dp(350);}
    private void placeMenu(){
        if(menuParams==null||handleParams==null)return;
        // Popup is aligned with the side handle but does not cover the whole screen.
        boolean right=handleParams.x>screenW*.5f;
        int targetX=right ?
            handleParams.x-menuParams.width+handleParams.width+dp(3):
            handleParams.x-dp(3);
        menuParams.x=Math.max(0,Math.min(screenW-menuParams.width,targetX));
        menuParams.y=Math.max(0,Math.min(screenH-menuParams.height,
            handleParams.y+handleParams.height/2-menuParams.height/2));
    }
    private void openRadial(boolean firstGesture){
        if(radial!=null || handle==null)return;
        if(closeTimer!=null)ui.removeCallbacks(closeTimer);
        closing=false;
        duringInitialGesture=firstGesture;
        radial=new RadialMenuView(this,audio,muteEngine,prefs,
            new RadialMenuView.Actions(){
                @Override public void onVolume(float level){adjustMedia(level);}
                @Override public void onMute(){toggleMute();}
                @Override public void onAlarm(){toggleAlarm();}
                @Override public void onDismiss(){requestClose();}
                @Override public void onTouchState(boolean touched){
                    holding=touched;
                    if(touched)cancelClose();
                    else scheduleClose();
                }
            });
        radial.setMirrored(handleParams.x<=screenW*.5f);
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        if(firstGesture)flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        menuParams=new WindowManager.LayoutParams(menuWidth(),menuHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags,
            PixelFormat.TRANSLUCENT);
        menuParams.gravity=Gravity.TOP|Gravity.LEFT;
        placeMenu();
        try{
            window.addView(radial,menuParams);
            radial.setAlpha(activeAlpha());
            radial.open();
            handle.setAlpha(0f);
        }catch(Exception ignored){
            radial=null;menuParams=null;duringInitialGesture=false;
            handle.setAlpha(idleAlpha());
        }
    }
    private void unlockMenuTouch(){
        if(radial==null||menuParams==null)return;
        menuParams.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try{window.updateViewLayout(radial,menuParams);}
        catch(Exception ignored){}
        duringInitialGesture=false;
    }
    private void cancelClose(){
        if(closeTimer!=null){
            ui.removeCallbacks(closeTimer);
            closeTimer=null;
        }
    }
    private void scheduleClose(){
        cancelClose();
        closeTimer=()->{if(!holding)requestClose();};
        ui.postDelayed(closeTimer,2000);
    }
    private void requestClose(){
        if(radial==null||closing)return;
        closing=true;
        cancelClose();
        final RadialMenuView closingView=radial;
        closingView.close(()->{
            if(radial!=closingView)return;
            try{window.removeView(closingView);}catch(Exception ignored){}
            radial=null;menuParams=null;closing=false;
            if(handle!=null){
                handle.animate().cancel();
                handle.setAlpha(0f);
                handle.animate().alpha(idleAlpha()).setDuration(250).start();
            }
        });
    }
    private float rawToX(float raw){return raw-menuParams.x;}
    private float rawToY(float raw){return raw-menuParams.y;}
    private boolean onHandleTouch(MotionEvent e){
        float x=e.getRawX(),y=e.getRawY();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                holding=true;
                startX=x;startY=y;previousX=x;previousY=y;
                draggingVolume=false;volumeHover=false;pointerTarget=RadialMenuView.NONE;
                cancelClose();
                openRadial(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                previousX=x;previousY=y;
                if(radial!=null && menuParams!=null){
                    float px=rawToX(x),py=rawToY(y);
                    int target=radial.targetAt(px,py);
                    // Reach any target with the same continuous gesture.
                    if(target!=RadialMenuView.NONE && target!=RadialMenuView.HUB){
                        pointerTarget=target;
                        radial.select(target);
                        if(target==RadialMenuView.VOLUME){
                            if(!volumeHover){
                                volumeHover=true;
                                volumeHoverStartY=py;
                            }
                            // Do not alter audio just by passing over the volume arc.
                            // Commit only after vertical movement within its track.
                            if(Math.abs(py-volumeHoverStartY)>dp(7)){
                                draggingVolume=true;
                                float value=radial.valueAt(py);
                                radial.setVolumePreview(value);
                                adjustMedia(value);
                            }
                        } else {
                            volumeHover=false;
                        }
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                holding=false;
                if(radial!=null && menuParams!=null){
                    float px=rawToX(x),py=rawToY(y);
                    int target=radial.targetAt(px,py);
                    if(target==RadialMenuView.NONE||target==RadialMenuView.HUB)target=pointerTarget;
                    if(e.getActionMasked()==MotionEvent.ACTION_UP){
                        if(target==RadialMenuView.MUTE)toggleMute();
                        else if(target==RadialMenuView.ALARM)toggleAlarm();
                        // Volume changes only during vertical drag, not by a simple tap.
                    }
                    radial.resetVolumePreview();
                    radial.select(RadialMenuView.NONE);
                    unlockMenuTouch();
                    scheduleClose();
                }
                pointerTarget=RadialMenuView.NONE;
                draggingVolume=false;
                volumeHover=false;
                return true;
            default:return true;
        }
    }
    private void adjustMedia(float fraction) {
        if(muteEngine.isMuted())muteEngine.restore();
        int max=Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int level=Math.max(0,Math.min(max,Math.round(fraction*max)));
        try{
            if(audio.getStreamVolume(AudioManager.STREAM_MUSIC)!=level)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC,level,0);
        }catch(Exception ignored){}
        if(radial!=null)radial.invalidateAudio();
    }
    private void toggleMute(){
        muteEngine.toggle();
        if(radial!=null)radial.invalidateAudio();
        scheduleClose();
    }
    private void toggleAlarm(){
        try {
            int max=audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int current=audio.getStreamVolume(AudioManager.STREAM_ALARM);
            audio.setStreamVolume(AudioManager.STREAM_ALARM,current>0?0:max,0);
        }catch(Exception blocked){
            Toast.makeText(this,"O Android restringiu o alarme. Verifique o acesso a Não Perturbe.",
                Toast.LENGTH_LONG).show();
        }
        if(radial!=null)radial.invalidateAudio();
        scheduleClose();
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences changed,String key){
        if(handle==null||handleParams==null)return;
        if("edgeDistance".equals(key)||"vertical".equals(key)||
            "side".equals(key)||"freeX".equals(key)||"freeY".equals(key)){
            setPosition();
            try{window.updateViewLayout(handle,handleParams);}catch(Exception ignored){}
            if(radial!=null&&menuParams!=null){
                placeMenu();
                radial.setMirrored(handleParams.x<=screenW*.5f);
                try{window.updateViewLayout(radial,menuParams);}catch(Exception ignored){}
            }
        }
        if("idleOpacity".equals(key)&&radial==null)
            handle.setAlpha(idleAlpha());
        if("activeOpacity".equals(key)&&radial!=null)
            radial.setAlpha(activeAlpha());
        if(radial!=null)radial.invalidateAudio();
        if(handle!=null)handle.invalidate();
    }
    @Override public void onDestroy(){
        cancelClose();
        if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(this);
        if(radial!=null){
            try{window.removeView(radial);}catch(Exception ignored){}
            radial=null;
        }
        if(handle!=null){
            try{window.removeView(handle);}catch(Exception ignored){}
            handle=null;
        }
        if(muteEngine!=null&&muteEngine.isMuted())muteEngine.restore();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}

    /** Barely visible crescent, not a filled rectangle. */
    private static final class EdgeHandle extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval=new RectF();
        private final float scale;
        EdgeHandle(Service service){
            super(service);
            scale=getResources().getDisplayMetrics().density;
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            setContentDescription("Arraste para abrir o controle de volume");
        }
        private float d(float n){return n*scale;}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            float centerY=h*.5f;
            float x=w-d(6);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(d(5.8f));
            paint.setColor(Color.argb(145,245,249,253));
            oval.set(x-d(15),centerY-d(29),x+d(18),centerY+d(29));
            canvas.drawArc(oval,114,132,false,paint);
            paint.setStrokeWidth(d(1));
            paint.setColor(Color.argb(160,255,255,255));
            oval.inset(d(3),d(2));
            canvas.drawArc(oval,116,128,false,paint);
        }
    }
}
