package com.glassmaind.slidevol;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.FingerprintGestureController;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

public class SlideVolActivity extends Activity {
    public static final String PREFS = "slidevol_prefs";
    private static final int BG = Color.rgb(12, 19, 22);
    private static final int ACCENT = Color.rgb(167, 239, 201);
    private static final int SUBTEXT = Color.rgb(183, 199, 197);
    private SharedPreferences prefs;
    private TextView overlayStatus, fingerprintStatus;
    private boolean permissionPending = false;

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private TextView label(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, 1);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }
    private void actionButton(LinearLayout box, String text, int color, View.OnClickListener click) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setBackground(rounded(color, 13));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.topMargin = dp(9);
        box.addView(button, p);
        button.setOnClickListener(click);
    }
    private void addSlider(LinearLayout box, String title, String key, int min, int max,
                           int fallback, String unit, boolean invert) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView name=label(title,15,Color.WHITE,false);
        TextView value=label("",14,ACCENT,true);
        row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(value);
        box.addView(row);
        SeekBar seek=new SeekBar(this);
        seek.setMax(max-min);
        int start=prefs.getInt(key,fallback);
        seek.setProgress(Math.max(0,Math.min(max-min,start-min)));
        value.setText(start+unit);
        box.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int progress, boolean fromUser){
                int n=min+progress; value.setText(n+unit);
                if(fromUser) prefs.edit().putInt(key,n).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
    }
    private void section(LinearLayout root, String title) {
        TextView head=label(title,17,ACCENT,true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(18);
        root.addView(head,p);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(30),dp(22),dp(36));
        scroll.addView(root);
        root.addView(label("SlideVol",32,ACCENT,true));
        root.addView(label("Controle flutuante por gestos",16,Color.WHITE,false));
        root.addView(label("Arraste diretamente sobre a barra para mudar o volume. Segure por 0,7 segundo sem mover para reposicioná-la.",13,SUBTEXT,false));

        section(root,"Controle");
        overlayStatus=label("",13,ACCENT,false);
        root.addView(overlayStatus);
        actionButton(root,"Ativar barra flutuante",Color.rgb(25,108,82),v->startOverlay());
        actionButton(root,"Desativar barra",Color.rgb(51,61,64),v->stopService(new Intent(this,VolumeService.class)));

        section(root,"Aparência");
        Switch compact=new Switch(this);
        compact.setText("Barra discreta, estilo nativo");
        compact.setTextColor(Color.WHITE);
        compact.setTextSize(15);
        compact.setChecked(prefs.getBoolean("compact",true));
        compact.setOnCheckedChangeListener((b,checked)->prefs.edit().putBoolean("compact",checked).apply());
        root.addView(compact);
        root.addView(label("Desativado: mostra setas e porcentagem, como na versão anterior.",12,SUBTEXT,false));

        addSlider(root,"Opacidade","opacity",15,100,64,"%",false);
        root.addView(label("Quanto menor a opacidade, mais transparente fica a barra.",12,SUBTEXT,false));
        addSlider(root,"Largura da barra","width",24,100,34," dp",false);
        addSlider(root,"Altura da barra","height",90,300,150," dp",false);
        addSlider(root,"Sensibilidade do gesto","sensitivity",6,36,14," dp",false);

        section(root,"Posição na tela");
        LinearLayout sides=new LinearLayout(this);
        sides.setOrientation(LinearLayout.HORIZONTAL);
        Button right=new Button(this),left=new Button(this);
        right.setText("Direita");right.setAllCaps(false);
        left.setText("Esquerda");left.setAllCaps(false);
        right.setTextColor(Color.WHITE);left.setTextColor(Color.WHITE);
        right.setBackground(rounded(Color.rgb(25,108,82),10));
        left.setBackground(rounded(Color.rgb(51,61,64),10));
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(45),1);
        a.rightMargin=dp(6);
        sides.addView(right,a);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(45),1);
        b.leftMargin=dp(6);
        sides.addView(left,b);
        root.addView(sides);
        right.setOnClickListener(v->{
            prefs.edit().putString("side","right").apply();
            right.setBackground(rounded(Color.rgb(25,108,82),10));
            left.setBackground(rounded(Color.rgb(51,61,64),10));
        });
        left.setOnClickListener(v->{
            prefs.edit().putString("side","left").apply();
            left.setBackground(rounded(Color.rgb(25,108,82),10));
            right.setBackground(rounded(Color.rgb(51,61,64),10));
        });
        if("left".equals(prefs.getString("side","right"))){
            left.setBackground(rounded(Color.rgb(25,108,82),10));
            right.setBackground(rounded(Color.rgb(51,61,64),10));
        }
        addSlider(root,"Altura na tela","vertical",0,100,28,"%",false);
        actionButton(root,"Restaurar aparência padrão",Color.rgb(51,61,64),v->{
            prefs.edit().putBoolean("compact",true).putInt("opacity",64).putInt("width",34)
                .putInt("height",150).putInt("sensitivity",14).putString("side","right")
                .putInt("vertical",28).apply();
            recreate();
        });

        section(root,"Leitor de digitais — experimental");
        root.addView(label("Só funcionará se a Xiaomi disponibilizar os gestos do sensor ao Android. Esta opção não lê sua digital e não exige root.",13,SUBTEXT,false));
        Switch finger=new Switch(this);
        finger.setText("Controlar volume com a digital");
        finger.setTextSize(15);
        finger.setTextColor(Color.WHITE);
        finger.setChecked(prefs.getBoolean("fingerEnabled",false));
        finger.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean("fingerEnabled",checked).apply());
        root.addView(finger);
        fingerprintStatus=label("",13,ACCENT,false);
        root.addView(fingerprintStatus);
        actionButton(root,"Configurar serviço de acessibilidade",Color.rgb(42,69,75),
            v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        actionButton(root,"Atualizar diagnóstico do sensor",Color.rgb(51,61,64),v->updateStatus());
        root.addView(label("Se os gestos forem suportados: deslizar para cima = +1 nível; deslizar para baixo = -1 nível. A captura é feita por um serviço de acessibilidade restrito aos gestos da digital.",12,SUBTEXT,false));

        setContentView(scroll);
        updateStatus();
    }
    private boolean accessibilityEnabled() {
        AccessibilityManager am=(AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        for(AccessibilityServiceInfo s:am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)){
            String id=s.getId();
            if(id!=null && id.contains(getPackageName()) && id.contains("FingerprintService")) return true;
        }
        return false;
    }
    private void updateStatus(){
        if(overlayStatus!=null) overlayStatus.setText(Settings.canDrawOverlays(this)
            ? "Permissão de sobreposição: autorizada" : "Permissão de sobreposição: pendente");
        if(fingerprintStatus!=null){
            boolean active=accessibilityEnabled();
            boolean available=prefs.getBoolean("fingerAvailable",false);
            long last=prefs.getLong("fingerLast",0);
            String state=active ? (available ? "Sensor: gestos disponíveis pelo Android." :
                "Sensor: detecção indisponível ou não suportada.") :
                "Serviço de acessibilidade ainda não ativado.";
            if(last>0) state+="\nÚltimo gesto capturado: "+
                DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
                   .format(new Date(last));
            fingerprintStatus.setText(state);
        }
    }
    private void startOverlay(){
        if(!Settings.canDrawOverlays(this)){
            permissionPending=true;
            try{
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName())));
            }catch(Exception e){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));}
            return;
        }
        permissionPending=false;
        try{startForegroundService(new Intent(this,VolumeService.class));}
        catch(Exception e){ if(overlayStatus!=null) overlayStatus.setText("Erro ao ativar: "+e.getMessage());}
    }
    @Override protected void onResume(){
        super.onResume();
        if(permissionPending && Settings.canDrawOverlays(this)) startOverlay();
        updateStatus();
    }

    public static class VolumeService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
        private WindowManager wm;
        private WindowManager.LayoutParams params;
        private VolumeView overlay;
        private SharedPreferences prefs;
        private AudioManager audio;
        private Handler handler;
        private int screenWidth,screenHeight;
        private boolean touched, moved, reposition;
        private float initialX,initialY;
        private int startVol,originX,originY;
        private Runnable longPress;
        private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
        @Override public void onCreate(){
            super.onCreate();
            wm=(WindowManager)getSystemService(WINDOW_SERVICE);
            audio=(AudioManager)getSystemService(AUDIO_SERVICE);
            prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(this);
            handler=new Handler(Looper.getMainLooper());
            android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            screenWidth=dm.widthPixels;screenHeight=dm.heightPixels;
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
                new NotificationChannel("slidevol","SlideVol",NotificationManager.IMPORTANCE_LOW));
        }
        @Override public int onStartCommand(Intent intent,int flags,int startId){
            if(intent!=null && "STOP".equals(intent.getAction())){
                stopSelf();return START_NOT_STICKY;
            }
            if(!Settings.canDrawOverlays(this)){
                stopSelf();return START_NOT_STICKY;
            }
            PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,SlideVolActivity.class),
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stop=PendingIntent.getService(this,2,
                new Intent(this,VolumeService.class).setAction("STOP"),
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n=new Notification.Builder(this,"slidevol")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("SlideVol ativo")
                .setContentText("Controle flutuante de volume por gestos")
                .setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,
                    "Desativar",stop).build()).build();
            startForeground(24,n);
            if(overlay==null) showOverlay();
            return START_STICKY;
        }
        private void calculatePosition(){
            int w=params.width,h=params.height;
            int limitY=Math.max(0,screenHeight-h-dp(20));
            String side=prefs.getString("side","right");
            if("free".equals(side)){
                params.x=Math.round(prefs.getFloat("freeX",.75f)*Math.max(0,screenWidth-w));
                params.y=Math.round(prefs.getFloat("freeY",.28f)*limitY);
            }else{
                params.x="left".equals(side)?dp(5):Math.max(0,screenWidth-w-dp(6));
                params.y=Math.round(prefs.getInt("vertical",28)/100f*limitY);
            }
        }
        private void showOverlay(){
            overlay=new VolumeView(this,prefs,audio);
            params=new WindowManager.LayoutParams(dp(prefs.getInt("width",34)),
                dp(prefs.getInt("height",150)),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
            params.gravity=Gravity.TOP|Gravity.LEFT;
            calculatePosition();
            overlay.setOnTouchListener((v,event)->handleTouch(event));
            try{wm.addView(overlay,params);}
            catch(Exception e){overlay=null;stopSelf();}
        }
        private boolean handleTouch(MotionEvent e){
            if(overlay==null)return false;
            float x=e.getRawX(),y=e.getRawY();
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    touched=true;moved=false;reposition=false;
                    initialX=x;initialY=y;originX=params.x;originY=params.y;
                    startVol=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                    longPress=()->{if(touched && !moved)reposition=true;};
                    handler.postDelayed(longPress,700);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=x-initialX,dy=y-initialY;
                    if(!moved && (Math.abs(dx)>dp(5)||Math.abs(dy)>dp(5))){
                        moved=true;
                        handler.removeCallbacks(longPress);
                    }
                    if(reposition){
                        params.x=Math.max(0,Math.min(screenWidth-params.width,originX+Math.round(dx)));
                        params.y=Math.max(0,Math.min(screenHeight-params.height,originY+Math.round(dy)));
                        try{wm.updateViewLayout(overlay,params);}catch(Exception ignored){}
                    }else{
                        int sensitivity=prefs.getInt("sensitivity",14);
                        int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int next=Math.max(0,Math.min(max,startVol-Math.round(dy/(float)dp(sensitivity))));
                        if(next!=audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC,next,0);
                        overlay.invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    touched=false;
                    if(longPress!=null) handler.removeCallbacks(longPress);
                    if(reposition){
                        prefs.edit().putString("side","free")
                            .putFloat("freeX",params.x/(float)Math.max(1,screenWidth-params.width))
                            .putFloat("freeY",params.y/(float)Math.max(1,screenHeight-params.height-dp(20)))
                            .apply();
                    }
                    overlay.invalidate();return true;
                default:return true;
            }
        }
        @Override public void onSharedPreferenceChanged(SharedPreferences s,String key){
            if(overlay==null||params==null)return;
            if("opacity".equals(key)||"compact".equals(key)){
                overlay.invalidate();return;
            }
            if("width".equals(key)||"height".equals(key)||"side".equals(key)||
                    "vertical".equals(key)||"freeX".equals(key)||"freeY".equals(key)){
                params.width=dp(prefs.getInt("width",34));
                params.height=dp(prefs.getInt("height",150));
                calculatePosition();
                try{wm.updateViewLayout(overlay,params);}catch(Exception ignored){}
                overlay.invalidate();
            }
        }
        @Override public void onDestroy(){
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            if(longPress!=null)handler.removeCallbacks(longPress);
            if(overlay!=null){
                try{wm.removeView(overlay);}catch(Exception ignored){}
                overlay=null;
            }
            super.onDestroy();
        }
        @Override public IBinder onBind(Intent i){return null;}
    }

    public static class VolumeView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect=new RectF();
        private final SharedPreferences prefs;
        private final AudioManager audio;
        private final float density;
        VolumeView(Context ctx, SharedPreferences pref, AudioManager manager){
            super(ctx);prefs=pref;audio=manager;
            density=getResources().getDisplayMetrics().density;
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        }
        private float dp(float d){return d*density;}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight(),cx=w/2f;
            int transparency=Math.max(15,Math.min(100,prefs.getInt("opacity",64)));
            int alpha=Math.round(255f*transparency/100f);
            float corner=Math.min(w/2f,dp(22));
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(alpha,12,24,28));
            rect.set(0,0,w,h);
            canvas.drawRoundRect(rect,corner,corner,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));
            p.setColor(Color.argb(Math.min(255,alpha+20),115,171,148));
            canvas.drawRoundRect(rect,corner,corner,p);
            p.setStyle(Paint.Style.FILL);
            int level=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            float ratio=max==0?0f:level/(float)max;
            if(prefs.getBoolean("compact",true)){
                float trackW=Math.min(dp(7),w*.32f);
                float top=dp(16),bottom=h-dp(16);
                if(bottom<top+dp(4)){top=dp(5);bottom=h-dp(5);}
                rect.set(cx-trackW/2f,top,cx+trackW/2f,bottom);
                p.setColor(Color.argb(Math.min(245,alpha+60),155,175,170));
                canvas.drawRoundRect(rect,trackW/2f,trackW/2f,p);
                float filled=Math.max(0,(bottom-top)*ratio);
                if(filled>0){
                    rect.set(cx-trackW/2f,bottom-filled,cx+trackW/2f,bottom);
                    p.setColor(Color.rgb(167,239,201));
                    canvas.drawRoundRect(rect,trackW/2f,trackW/2f,p);
                }
            }else{
                float text=Math.min(dp(18),w*.36f);
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(Color.rgb(167,239,201));
                p.setTextSize(text);
                canvas.drawText("\u25b2",cx,h*.25f,p);
                p.setTextSize(Math.min(dp(18),w*.29f));
                canvas.drawText(Math.round(ratio*100)+"%",cx,h*.52f,p);
                p.setTextSize(text);
                canvas.drawText("\u25bc",cx,h*.83f,p);
            }
        }
    }

    public static class FingerprintService extends AccessibilityService {
        private FingerprintGestureController controller;
        private FingerprintGestureController.FingerprintGestureCallback callback;
        private SharedPreferences prefs;
        private AudioManager audio;
        @Override protected void onServiceConnected(){
            super.onServiceConnected();
            prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
            audio=(AudioManager)getSystemService(AUDIO_SERVICE);
            controller=getFingerprintGestureController();
            if(controller==null)return;
            prefs.edit().putBoolean("fingerAvailable",controller.isGestureDetectionAvailable()).apply();
            callback=new FingerprintGestureController.FingerprintGestureCallback(){
                @Override public void onGestureDetectionAvailabilityChanged(boolean available){
                    prefs.edit().putBoolean("fingerAvailable",available).apply();
                }
                @Override public void onGestureDetected(int gesture){
                    if(!prefs.getBoolean("fingerEnabled",false))return;
                    int direction;
                    if(gesture==FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP)
                        direction=AudioManager.ADJUST_RAISE;
                    else if(gesture==FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN)
                        direction=AudioManager.ADJUST_LOWER;
                    else return;
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,direction,0);
                    prefs.edit().putLong("fingerLast",System.currentTimeMillis()).apply();
                }
            };
            controller.registerFingerprintGestureCallback(callback,null);
        }
        @Override public void onAccessibilityEvent(AccessibilityEvent event){}
        @Override public void onInterrupt(){}
        @Override public void onDestroy(){
            if(controller!=null&&callback!=null)
                controller.unregisterFingerprintGestureCallback(callback);
            if(prefs!=null)prefs.edit().putBoolean("fingerAvailable",false).apply();
            super.onDestroy();
        }
    }
}
