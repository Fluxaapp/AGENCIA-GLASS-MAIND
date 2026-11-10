package com.glassmaind.slidevol;

import android.app.Activity;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class SlideVolActivity extends Activity {
    private boolean waiting = false;
    private int dp(float x) { return (int)(x * getResources().getDisplayMetrics().density + .5f); }
    private TextView info;
    private TextView label(String s, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(dp(8), dp(12), dp(8), dp(12));
        return v;
    }
    private GradientDrawable pill(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }
    private void showButton(LinearLayout root, String title, int color, View.OnClickListener click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextColor(Color.WHITE);
        b.setBackground(pill(color, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(12), 0, 0);
        root.addView(b, lp);
        b.setOnClickListener(click);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9,14,18));
        getWindow().setNavigationBarColor(Color.rgb(9,14,18));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(26),dp(52),dp(26),dp(25));
        root.setBackgroundColor(Color.rgb(9,14,18));
        TextView title = label("SlideVol", 38, Color.rgb(172,239,194));
        root.addView(title);
        root.addView(label("Controle o volume com um único gesto.",17,Color.WHITE));
        root.addView(label("Encoste na barra flutuante e deslize.\nPara cima: aumenta. Para baixo: diminui.\nSem clicar e sem abrir painéis.",15,Color.rgb(185,200,203)));
        info=label("",14,Color.rgb(172,239,194));
        root.addView(info);
        showButton(root,"Ativar controle flutuante", Color.rgb(25,111,88), v->start());
        showButton(root,"Desativar controle", Color.rgb(46,54,64), v->{
            stopService(new Intent(this,VolumeService.class));
            Toast.makeText(this,"SlideVol desativado",Toast.LENGTH_SHORT).show();
        });
        root.addView(label("Sensibilidade do gesto",16,Color.WHITE));
        SeekBar bar=new SeekBar(this);
        bar.setMax(20);
        bar.setProgress(getPreferences(MODE_PRIVATE).getInt("step",12)-8);
        root.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
           public void onProgressChanged(SeekBar b,int p,boolean fromUser) {
              if(fromUser) getPreferences(MODE_PRIVATE).edit().putInt("step",p+8).apply();
           }
           public void onStartTrackingTouch(SeekBar b){}
           public void onStopTrackingTouch(SeekBar b){}
        });
        root.addView(label("Para mover a barra, segure por 0,7 segundo e arraste.\nGrátis • Sem anúncios • Sem internet.",13,Color.rgb(162,190,178)));
        setContentView(root);
    }
    private void start() {
        if (!Settings.canDrawOverlays(this)) {
            waiting = true;
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()));
            try { startActivity(i); } catch(Exception e){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));}
            return;
        }
        waiting=false;
        try {
            startForegroundService(new Intent(this,VolumeService.class));
            Toast.makeText(this,"SlideVol ativado",Toast.LENGTH_SHORT).show();
        } catch(Exception e) {Toast.makeText(this,"Não foi possível iniciar: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override protected void onResume() {
        super.onResume();
        if (waiting && Settings.canDrawOverlays(this)) start();
        if (info != null) info.setText(Settings.canDrawOverlays(this) ? "Permissão para flutuar: autorizada" : "Permissão para flutuar: pendente");
    }

    public static class VolumeService extends Service {
        private WindowManager wm;
        private WindowManager.LayoutParams lp;
        private TextView handle;
        private AudioManager audio;
        private int max;
        private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+.5f); }
        @Override public void onCreate() {
            super.onCreate();
            wm=(WindowManager)getSystemService(WINDOW_SERVICE);
            audio=(AudioManager)getSystemService(AUDIO_SERVICE);
            max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
                new NotificationChannel("slidevol","SlideVol",NotificationManager.IMPORTANCE_LOW));
        }
        @Override public int onStartCommand(Intent intent,int flags,int startId) {
            if (intent!=null && "STOP".equals(intent.getAction())) {
                stopSelf(); return START_NOT_STICKY;
            }
            if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
            Intent open=new Intent(this,SlideVolActivity.class);
            PendingIntent p=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Intent stop=new Intent(this,VolumeService.class).setAction("STOP");
            PendingIntent sp=PendingIntent.getService(this,2,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(this,"slidevol")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("SlideVol ativo")
                .setContentText("Deslize na barra para ajustar o volume")
                .setContentIntent(p).setOngoing(true)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Desativar",sp).build())
                .build();
            startForeground(24,n);
            if(handle==null) show();
            return START_STICKY;
        }
        private void refresh() {
            if(handle==null) return;
            int vol=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int per=max>0?Math.round(vol*100f/max):0;
            handle.setText("▲\n\n"+per+"%\n\n▼");
        }
        private void show(){
            handle=new TextView(this);
            handle.setGravity(Gravity.CENTER);
            handle.setTextSize(15f);
            handle.setTextColor(Color.rgb(172,239,194));
            GradientDrawable bg=new GradientDrawable();
            bg.setColor(Color.argb(240,17,30,35));
            bg.setCornerRadius(dp(26));
            bg.setStroke(dp(1),Color.rgb(63,98,90));
            handle.setBackground(bg);
            lp=new WindowManager.LayoutParams(dp(54),dp(190),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
            lp.gravity=Gravity.TOP|Gravity.LEFT;
            android.util.DisplayMetrics d=new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(d);
            lp.x=d.widthPixels-dp(60);
            lp.y=(d.heightPixels-dp(190))/2;
            final float[] sy={0},sx={0};
            final int[] starting={0},posX={0},posY={0};
            final long[] down={0};
            final boolean[] reposition={false},moved={false};
            handle.setOnTouchListener((v,e)->{
                float x=e.getRawX(), y=e.getRawY();
                switch(e.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        sx[0]=x; sy[0]=y;
                        starting[0]=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                        posX[0]=lp.x; posY[0]=lp.y;
                        down[0]=SystemClock.uptimeMillis();
                        moved[0]=false; reposition[0]=false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx=x-sx[0], dy=y-sy[0];
                        if(!moved[0] && (Math.abs(dx)>dp(4)||Math.abs(dy)>dp(4))) moved[0]=true;
                        if(!moved[0] && SystemClock.uptimeMillis()-down[0]>700) reposition[0]=true;
                        if(reposition[0]){
                            lp.x=Math.max(0,Math.min(d.widthPixels-lp.width,posX[0]+Math.round(dx)));
                            lp.y=Math.max(0,Math.min(d.heightPixels-lp.height,posY[0]+Math.round(dy)));
                            wm.updateViewLayout(handle,lp);
                        }else{
                            int step=getSharedPreferences("SlideVolActivity",MODE_PRIVATE).getInt("step",12);
                            int desired=Math.max(0,Math.min(max,starting[0]-Math.round(dy/(float)dp(step))));
                            if(audio.getStreamVolume(AudioManager.STREAM_MUSIC)!=desired)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC,desired,0);
                            refresh();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: refresh(); return true;
                    default: return true;
                }
            });
            try { wm.addView(handle,lp); refresh(); }
            catch(Exception e){handle=null;stopSelf();}
        }
        @Override public void onDestroy() {
            if(handle!=null){try{wm.removeView(handle);}catch(Exception ignored){} handle=null;}
            super.onDestroy();
        }
        @Override public IBinder onBind(Intent i){return null;}
    }
}