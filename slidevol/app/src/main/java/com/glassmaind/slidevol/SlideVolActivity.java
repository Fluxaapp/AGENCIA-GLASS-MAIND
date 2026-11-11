package com.glassmaind.slidevol;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class SlideVolActivity extends Activity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String PREFS="slidevol_prefs";
    private static final int BACKGROUND=Color.rgb(10,15,20);
    private static final int PANEL=Color.rgb(21,29,35);
    private static final int TEXT=Color.rgb(241,247,245);
    private static final int MUTED_TEXT=Color.rgb(164,178,179);
    private static final int[] PALETTE = {
        Color.rgb(110,215,183), Color.rgb(119,180,245),
        Color.rgb(185,160,247), Color.rgb(255,255,255),
        Color.rgb(247,151,129), Color.rgb(238,205,120),
        Color.rgb(143,232,132)
    };
    private SharedPreferences prefs;
    private MuteEngine muteEngine;
    private TextView overlayStatus, dndStatus, muteStatus, colorCaption;
    private VolumeBarView preview;
    private MuteButtonView mutePreview;
    private LinearLayout swatchRow;
    private Button muteAction;
    private final ArrayList<SeekBar> sliders=new ArrayList<>();
    private boolean permissionPending;

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private int accent(){return prefs.getInt("accentColor",PALETTE[0]);}

    private GradientDrawable surface(int color,int round){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(round));
        return d;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView v=new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT,1);
        return v;
    }
    private LinearLayout column(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout panel(LinearLayout root,String heading,String description){
        LinearLayout p=column();
        p.setPadding(dp(18),dp(17),dp(18),dp(18));
        p.setBackground(surface(PANEL,20));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=dp(13);
        root.addView(p,params);
        TextView label=text(heading,17,TEXT,true);
        p.addView(label);
        if(description!=null && !description.isEmpty()){
            TextView subtitle=text(description,12,MUTED_TEXT,false);
            LinearLayout.LayoutParams subp=new LinearLayout.LayoutParams(-1,-2);
            subp.topMargin=dp(7);
            subp.bottomMargin=dp(12);
            p.addView(subtitle,subp);
        }
        return p;
    }
    private void note(LinearLayout card,String copy){
        TextView t=text(copy,12,MUTED_TEXT,false);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(7);
        card.addView(t,p);
    }
    private void action(LinearLayout card,String label,boolean primary,View.OnClickListener listener){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(primary ? BACKGROUND : TEXT);
        b.setBackground(surface(primary ? accent():Color.rgb(47,59,65),13));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));
        p.topMargin=dp(10);
        card.addView(b,p);
        b.setOnClickListener(listener);
    }
    private void setting(LinearLayout card,String title,String key,
        int min,int max,int initial,String suffix) {
        LinearLayout line=new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        TextView titleText=text(title,14,TEXT,false);
        TextView valueText=text("",13,accent(),true);
        line.addView(titleText,new LinearLayout.LayoutParams(0,-2,1));
        line.addView(valueText);
        LinearLayout.LayoutParams lineParam=new LinearLayout.LayoutParams(-1,-2);
        lineParam.topMargin=dp(16);
        card.addView(line,lineParam);
        SeekBar seek=new SeekBar(this);
        seek.setMax(max-min);
        seek.setProgress(Math.max(0,Math.min(max-min,prefs.getInt(key,initial)-min)));
        seek.setProgressTintList(ColorStateList.valueOf(accent()));
        seek.setThumbTintList(ColorStateList.valueOf(accent()));
        int init=min+seek.getProgress();
        valueText.setText(init+suffix);
        card.addView(seek);
        sliders.add(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int progress,boolean changedByUser){
                int now=min+progress;
                valueText.setText(now+suffix);
                if(changedByUser){
                    SharedPreferences.Editor editor=prefs.edit().putInt(key,now);
                    if("edgeDistance".equals(key)&&"free".equals(prefs.getString("side","right"))){
                        editor.putString("side",prefs.getFloat("freeX",.9f)<.5f?"left":"right");
                    }
                    editor.apply();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
    }
    private void toggle(LinearLayout card,String name,String setting,boolean fallback){
        Switch s=new Switch(this);
        s.setText(name);
        s.setTextColor(TEXT);
        s.setTextSize(14);
        s.setPadding(0,dp(10),0,dp(8));
        s.setChecked(prefs.getBoolean(setting,fallback));
        s.setThumbTintList(new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
            new int[]{accent(),Color.rgb(152,162,164)}));
        card.addView(s);
        s.setOnCheckedChangeListener((button,enabled)->
            prefs.edit().putBoolean(setting,enabled).apply());
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        muteEngine=new MuteEngine(this);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout root=column();
        root.setPadding(dp(20),dp(26),dp(20),dp(45));
        scroll.addView(root);

        TextView eyeline=text("SLIDEVOL  /  1.4",12,accent(),true);
        root.addView(eyeline);
        TextView headline=text("Seu volume.\nSeu controle.",31,TEXT,true);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin=dp(10);
        root.addView(headline,hp);
        TextView lead=text("Arraste na lateral para mudar o volume. Toque nos três pontos da barra para abrir mídia, notificações e despertador.",14,MUTED_TEXT,false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(7);
        root.addView(lead,lp);

        LinearLayout viewCard=panel(root,"PRÉVIA","Barra e botão redesenhados com visual translúcido, preenchimento suave e ícones centralizados.");
        LinearLayout center=new LinearLayout(this);
        center.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerParams=new LinearLayout.LayoutParams(-1,dp(204));
        viewCard.addView(center,centerParams);
        LinearLayout previewStack=column();
        previewStack.setGravity(Gravity.CENTER_HORIZONTAL);
        center.addView(previewStack);
        preview=new VolumeBarView(this,prefs,(AudioManager)getSystemService(AUDIO_SERVICE));
        preview.setDemoMode();
        LinearLayout.LayoutParams prevParams=new LinearLayout.LayoutParams(
            dp(Math.min(78,prefs.getInt("width",38))),
            dp(Math.min(146,prefs.getInt("height",170))));
        previewStack.addView(preview,prevParams);
        mutePreview=new MuteButtonView(this,prefs,muteEngine);
        LinearLayout.LayoutParams mutePreviewParams=new LinearLayout.LayoutParams(
            dp(Math.min(48,prefs.getInt("muteSize",38))),dp(Math.min(48,prefs.getInt("muteSize",38))));
        mutePreviewParams.topMargin=dp(9);
        previewStack.addView(mutePreview,mutePreviewParams);
        mutePreview.setVisibility(prefs.getBoolean("showMute",true)?View.VISIBLE:View.GONE);

        LinearLayout control=panel(root,"ATIVAÇÃO","Mantenha a barra disponível sobre os aplicativos.");
        overlayStatus=text("",13,accent(),false);
        control.addView(overlayStatus);
        action(control,"Ativar controle flutuante",true,v->enableOverlay());
        action(control,"Desativar controle",false,v->{
            stopService(new Intent(this,SlideVolService.class));
            Toast.makeText(this,"Barra desativada",Toast.LENGTH_SHORT).show();
            updateStatuses();
        });

        LinearLayout appearance=panel(root,"COR & ESTILO",
            "Escolha uma cor ou digite qualquer código hexadecimal.");
        colorCaption=text("",13,accent(),true);
        appearance.addView(colorCaption);
        HorizontalScrollView horizontal=new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        swatchRow=new LinearLayout(this);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setPadding(0,dp(12),0,dp(8));
        horizontal.addView(swatchRow);
        appearance.addView(horizontal);
        populateSwatches();
        action(appearance,"Usar cor personalizada (#HEX)",false,v->promptColor());
        toggle(appearance,"Mostrar botão de silenciar", "showMute",true);
        setting(appearance,"Largura da barra","width",24,90,38," dp");
        setting(appearance,"Altura da barra","height",90,300,170," dp");
        setting(appearance,"Tamanho do botão de silêncio","muteSize",28,76,38," dp");
        setting(appearance,"Espaço entre barra e botão","muteSpacing",0,48,9," dp");

        LinearLayout behavior=panel(root,"TRANSPARÊNCIA & GESTOS",
            "O controle aparece ao deslizar e volta a ficar discreto após o movimento. Os três pontos da barra abrem o painel expandido.");
        setting(behavior,"Opacidade durante o uso","activeOpacity",20,100,94,"%");
        setting(behavior,"Opacidade em repouso","idleOpacity",0,100,6,"%");
        setting(behavior,"Retorno à transparência","fadeDelay",0,10,2," s");
        setting(behavior,"Distância do deslize (0–100%)","gestureRange",140,600,350," dp");
        setting(behavior,"Suavização visual","animationMs",0,400,170," ms");
        note(behavior,"Quanto maior a distância, mais preciso fica o ajuste. A barra acompanha o dedo continuamente; o áudio ainda usa os níveis discretos do Android.");
        note(behavior,"0% em repouso torna a barra totalmente invisível. Mesmo assim, a faixa sensível continua capturando toques nessa pequena área.");
        note(behavior,"Um toque simples não modifica o volume. Para não bloquear outros botões, use uma barra estreita e coloque-a na borda.");

        LinearLayout placement=panel(root,"POSIÇÃO","Escolha a lateral ou segure por 0,7 s para arrastar livremente.");
        LinearLayout sideRow=new LinearLayout(this);
        Button left=new Button(this),right=new Button(this);
        left.setText("Esquerda");right.setText("Direita");
        left.setAllCaps(false);right.setAllCaps(false);
        left.setTextColor(TEXT);right.setTextColor(TEXT);
        left.setBackground(surface(Color.rgb(40,57,64),12));
        right.setBackground(surface(Color.rgb(40,57,64),12));
        LinearLayout.LayoutParams leftP=new LinearLayout.LayoutParams(0,dp(45),1);
        leftP.rightMargin=dp(5);
        LinearLayout.LayoutParams rightP=new LinearLayout.LayoutParams(0,dp(45),1);
        rightP.leftMargin=dp(5);
        sideRow.addView(left,leftP);sideRow.addView(right,rightP);
        placement.addView(sideRow);
        left.setOnClickListener(v->{
            prefs.edit().putString("side","left").apply();
            left.setBackground(surface(accent(),12));right.setBackground(surface(Color.rgb(40,57,64),12));
        });
        right.setOnClickListener(v->{
            prefs.edit().putString("side","right").apply();
            right.setBackground(surface(accent(),12));left.setBackground(surface(Color.rgb(40,57,64),12));
        });
        if("left".equals(prefs.getString("side","right")))left.setBackground(surface(accent(),12));
        else right.setBackground(surface(accent(),12));
        setting(placement,"Posição vertical","vertical",0,100,30,"%");
        setting(placement,"Distância da borda","edgeDistance",0,120,10," dp");
        note(placement,"Ao ajustar a distância, o SlideVol fixa a barra na lateral mais próxima, mesmo se ela foi posicionada livremente.");

        LinearLayout sound=panel(root,"SILENCIAR TUDO",
            "O botão sob a barra tenta zerar mídia, toque, alarmes, notificações e sons do sistema.");
        muteStatus=text("",13,accent(),true);
        sound.addView(muteStatus);
        muteAction=new Button(this);
        muteAction.setAllCaps(false);
        muteAction.setTextColor(BACKGROUND);
        muteAction.setBackground(surface(accent(),12));
        LinearLayout.LayoutParams muteLp=new LinearLayout.LayoutParams(-1,dp(48));
        muteLp.topMargin=dp(10);
        sound.addView(muteAction,muteLp);
        muteAction.setOnClickListener(v->{
            muteEngine.toggle();
            updateStatuses();
        });
        dndStatus=text("",12,MUTED_TEXT,false);
        LinearLayout.LayoutParams statusP=new LinearLayout.LayoutParams(-1,-2);
        statusP.topMargin=dp(12);
        sound.addView(dndStatus,statusP);
        action(sound,"Autorizar Não Perturbe (opcional)",false,v->
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        note(sound,"Atenção: essa função também pode silenciar despertadores. Durante chamadas em andamento ou com aplicativos que ignoram o volume do Android, o silêncio absoluto não é garantido.");

        LinearLayout reset=panel(root,"RESTAURAR","Voltar às cores, dimensões e efeitos padrão.");
        action(reset,"Restaurar personalização",false,v->resetAppearance());
        note(reset,"Sem anúncios, assinaturas ou conexão com a internet.");

        setContentView(scroll);
        updateStatuses();
    }
    private void populateSwatches(){
        if(swatchRow==null)return;
        swatchRow.removeAllViews();
        int chosen=accent();
        for(int c:PALETTE){
            View v=new View(this);
            GradientDrawable circle=new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(c);
            circle.setStroke(dp(c==chosen?3:1),
                c==chosen?Color.rgb(15,21,27):Color.rgb(64,78,83));
            v.setBackground(circle);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(37),dp(37));
            p.rightMargin=dp(9);
            swatchRow.addView(v,p);
            v.setOnClickListener(view->prefs.edit().putInt("accentColor",c).apply());
        }
        if(colorCaption!=null){
            colorCaption.setText(String.format(java.util.Locale.US,
                "COR ATIVA  #%06X", chosen & 0xffffff));
            colorCaption.setTextColor(chosen);
        }
    }
    private void promptColor(){
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setText(String.format(java.util.Locale.US,"#%06X",accent()&0xffffff));
        input.setSelectAllOnFocus(true);
        LinearLayout container=column();
        container.setPadding(dp(22),dp(6),dp(22),0);
        container.addView(input);
        new AlertDialog.Builder(this).setTitle("Cor personalizada")
            .setMessage("Digite um código RGB, como #3F7FFF.")
            .setView(container)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Aplicar",(dialog,which)->{
                try {
                    String raw=input.getText().toString().trim();
                    if(!raw.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException();
                    prefs.edit().putInt("accentColor",Color.parseColor(raw)).apply();
                }catch(Exception e){
                    Toast.makeText(this,"Use um código válido: #RRGGBB",Toast.LENGTH_LONG).show();
                }
            }).show();
    }
    private void resetAppearance(){
        prefs.edit()
            .putInt("accentColor",PALETTE[0])
            .putInt("width",38)
            .putInt("height",170)
            .putInt("muteSize",38)
            .putInt("muteSpacing",9)
            .putInt("edgeDistance",10)
            .putInt("gestureRange",350)
            .putInt("animationMs",170)
            .putInt("activeOpacity",94)
            .putInt("idleOpacity",6)
            .putInt("fadeDelay",2)
            .putInt("sensitivity",14)
            .putInt("vertical",30)
            .putString("side","right")
            .putBoolean("showMute",true).apply();
        recreate();
    }
    private void enableOverlay(){
        if(!Settings.canDrawOverlays(this)){
            permissionPending=true;
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName())));
            } catch(Exception e){
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            }
            return;
        }
        permissionPending=false;
        try {
            startForegroundService(new Intent(this,SlideVolService.class));
            Toast.makeText(this,"Controle SlideVol ativado",Toast.LENGTH_SHORT).show();
        }catch(Exception ex){
            Toast.makeText(this,"Não foi possível ativar: "+ex.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
    private void updateStatuses(){
        if(overlayStatus!=null){
            overlayStatus.setText(Settings.canDrawOverlays(this)
                ? "Permissão para flutuar: autorizada"
                : "Permissão para flutuar: pendente");
        }
        if(muteStatus!=null){
            muteStatus.setText(muteEngine.isMuted() ?
                "Estado atual: silenciamento ativado" :
                "Estado atual: volume normal");
        }
        if(muteAction!=null){
            muteAction.setText(muteEngine.isMuted() ?
                "Restaurar todos os volumes" :
                "Silenciar agora");
        }
        if(dndStatus!=null){
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            dndStatus.setText(nm!=null && nm.isNotificationPolicyAccessGranted() ?
                "Permissão Não Perturbe: autorizada" :
                "Permissão Não Perturbe: não autorizada. Alguns alertas podem permanecer audíveis.");
        }
    }
    @Override protected void onResume(){
        super.onResume();
        prefs.registerOnSharedPreferenceChangeListener(this);
        if(permissionPending && Settings.canDrawOverlays(this))enableOverlay();
        updateStatuses();
    }
    @Override protected void onPause(){
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences shared,String key){
        if(preview!=null){
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)preview.getLayoutParams();
            if(lp!=null && ("width".equals(key)||"height".equals(key))){
                lp.width=dp(Math.min(78,prefs.getInt("width",38)));
                lp.height=dp(Math.min(146,prefs.getInt("height",170)));
                preview.setLayoutParams(lp);
            }
            preview.invalidate();
        }
        if(mutePreview!=null){
            if("muteSize".equals(key)){
                LinearLayout.LayoutParams mp=(LinearLayout.LayoutParams)mutePreview.getLayoutParams();
                mp.width=dp(Math.min(48,prefs.getInt("muteSize",38)));
                mp.height=mp.width;
                mutePreview.setLayoutParams(mp);
            }
            if("showMute".equals(key))mutePreview.setVisibility(
                prefs.getBoolean("showMute",true)?View.VISIBLE:View.GONE);
            mutePreview.invalidate();
        }
        if("accentColor".equals(key)){
            populateSwatches();
            for(SeekBar s:sliders){
                s.setProgressTintList(ColorStateList.valueOf(accent()));
                s.setThumbTintList(ColorStateList.valueOf(accent()));
            }
        }
        if("allMuted".equals(key))updateStatuses();
    }
}
