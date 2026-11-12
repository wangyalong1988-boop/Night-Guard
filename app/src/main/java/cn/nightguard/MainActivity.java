package cn.nightguard;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private Store store;private LinearLayout root,section,preview;private TextView countdown,clock,phase,appSummary,badge;
    private String target;
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable timer=new Runnable(){public void run(){if(countdown!=null)refreshCountdown();refreshClock();handler.postDelayed(this,1000);}};
    private int dp(float value){return BlueUi.dp(this,value);}
    @Override public void onCreate(Bundle state){super.onCreate(state);store=new Store(this);}
    @Override public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);render();}
    @Override public void onResume(){super.onResume();GuardService.settingsVisible=true;render();handler.removeCallbacks(timer);handler.post(timer);}
    @Override public void onPause(){GuardService.settingsVisible=false;handler.removeCallbacks(timer);super.onPause();}
    @Override public void onDestroy(){loader.shutdownNow();handler.removeCallbacks(timer);super.onDestroy();}
    private TextView text(String value,int size){TextView t=BlueUi.label(this,value,size,size>=18?BlueUi.INK:BlueUi.MUTED,size>=18);section.addView(t);BlueUi.gap(section,10);return t;}
    private void button(String value,boolean primary,Runnable action){BlueUi.gap(section,10);section.addView(BlueUi.button(this,value,primary,action),new LinearLayout.LayoutParams(-1,-2));}
    private void card(){
        section=BlueUi.column(this);section.setPadding(dp(18),dp(18),dp(18),dp(18));section.setBackground(BlueUi.background(this,Color.WHITE,0,18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(14);root.addView(section,lp);
    }
    private EditText input(String hint){
        EditText e=new EditText(this);e.setHint(hint);e.setTextSize(15);e.setTextColor(BlueUi.INK);e.setHintTextColor(BlueUi.MUTED);
        e.setPadding(dp(14),dp(14),dp(14),dp(14));e.setBackground(BlueUi.background(this,BlueUi.PAGE,BlueUi.LINE,10));section.addView(e);return e;
    }
    private boolean guardEnabled(){String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);return enabled!=null&&enabled.contains(getPackageName()+"/");}
    private void render(){
        countdown=null;ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);BlueUi.page(this,scroll);
        root=BlueUi.column(this);root.setPadding(dp(22),dp(24),dp(22),dp(24));scroll.addView(root);section=root;
        LinearLayout header=BlueUi.row(this);LinearLayout titles=BlueUi.column(this);
        titles.addView(BlueUi.label(this,"夜间守护",26,BlueUi.INK,true));BlueUi.gap(titles,7);titles.addView(BlueUi.label(this,"计划、应用与守护状态",12,BlueUi.MUTED,false));
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));badge=BlueUi.label(this,"",11,BlueUi.BLUE,true);badge.setPadding(dp(10),dp(6),dp(10),dp(6));badge.setBackground(BlueUi.background(this,BlueUi.PALE,0,8));header.addView(badge);root.addView(header);
        target=getIntent().getStringExtra("target");if(target==null)target=store.p.getString("request",null);if(target!=null&&!Apps.safe(this,target))renderUnlock();
        card();section.setBackground(BlueUi.background(this,0xffdfeafa,0,18));
        LinearLayout heroHead=BlueUi.row(this);heroHead.addView(BlueUi.label(this,"每日限制时段",13,BlueUi.BLUE,true),new LinearLayout.LayoutParams(0,-2,1));heroHead.addView(new BlueUi.Glyph(this,"moon",BlueUi.BLUE),new LinearLayout.LayoutParams(dp(22),dp(22)));section.addView(heroHead);BlueUi.gap(section,20);
        TextView schedule=BlueUi.label(this,"01:00  –  08:00",32,BlueUi.INK,true);schedule.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));section.addView(schedule);BlueUi.gap(section,14);
        LinearLayout timeline=BlueUi.row(this);View blue=new View(this);blue.setBackground(BlueUi.background(this,BlueUi.BLUE,0,2));timeline.addView(blue,new LinearLayout.LayoutParams(0,dp(3),7));View pale=new View(this);pale.setBackgroundColor(0xffc9d9ee);timeline.addView(pale,new LinearLayout.LayoutParams(0,dp(3),3));section.addView(timeline);BlueUi.gap(section,14);
        LinearLayout counter=BlueUi.row(this);phase=BlueUi.label(this,"",12,BlueUi.MUTED,false);counter.addView(phase,new LinearLayout.LayoutParams(0,-2,1));clock=BlueUi.label(this,"",17,BlueUi.INK,true);clock.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));counter.addView(clock);section.addView(counter);refreshClock();
        card();LinearLayout range=BlueUi.row(this);range.addView(BlueUi.label(this,"限制清单",17,BlueUi.INK,true),new LinearLayout.LayoutParams(0,-2,1));range.addView(BlueUi.link(this,"管理应用  ›",()->startActivity(new Intent(this,AppPickerActivity.class))));section.addView(range);
        appSummary=BlueUi.label(this,"正在读取已选应用…",13,BlueUi.MUTED,false);section.addView(appSummary);BlueUi.gap(section,14);preview=BlueUi.row(this);section.addView(preview);
        if(store.p.contains("pending")){BlueUi.gap(section,12);text("名单调整已保存，减少的限制将在08:00后生效。",12);}
        loader.execute(()->{try{List<AppCatalog.Entry> entries=AppCatalog.load(this);runOnUiThread(()->{if(!isDestroyed())showPreview(entries);});}catch(RuntimeException e){runOnUiThread(()->{if(!isDestroyed())appSummary.setText("点击管理应用，检查限制清单");});}});
        card();text("守护状态",17);
        statusRow("shield","应用拦截",GuardService.connected?"服务已连接，夜间按清单执行":guardEnabled()?"权限已开启，等待服务连接":"开启后才能限制应用",GuardService.connected?"已连接":"待检查",this::accessibilitySettings);
        BlueUi.divider(section);
        statusRow("bell","倒计时通知","仅在01:00–08:00显示解除倒计时",GuardNotification.permitted(this)?"已允许":"未开启",this::notificationSettings);
        BlueUi.divider(section);
        statusRow("clock","华为后台运行",store.p.getBoolean("backgroundConfirmed",false)?"已由你确认，系统仍可能限制后台":"允许自启动、关联启动及后台活动",store.p.getBoolean("backgroundConfirmed",false)?"已确认":"去设置",this::backgroundHelp);
        card();statusRow("lock","临时解锁规则","填写原因 · 等待10分钟 · 限时5分钟","查看",this::unlockInfo);
        LinearLayout bottom=BlueUi.row(this);bottom.addView(BlueUi.link(this,"规则与说明",this::rules),new LinearLayout.LayoutParams(0,dp(52),1));bottom.addView(BlueUi.link(this,"最近解锁记录",()->new AlertDialog.Builder(this).setTitle("最近一次临时解锁").setMessage(store.p.getString("last","暂无记录")).setPositiveButton("关闭",null).show()),new LinearLayout.LayoutParams(0,dp(52),1));root.addView(bottom);
    }
    private void showPreview(List<AppCatalog.Entry> entries){
        Set<String> selected=store.selected();List<AppCatalog.Entry> chosen=new ArrayList<>();int browserCount=0;
        for(AppCatalog.Entry e:entries){if(e.browser)browserCount++;if(e.browser||selected.contains(e.pkg))chosen.add(e);}
        appSummary.setText(chosen.size()+" 个应用纳入限制 · 其中 "+browserCount+" 个浏览器自动守护");preview.removeAllViews();
        if(chosen.isEmpty()){preview.addView(BlueUi.link(this,"添加娱乐、社交或游戏应用  →",()->startActivity(new Intent(this,AppPickerActivity.class))));return;}
        for(int i=0;i<Math.min(chosen.size(),5);i++){
            AppCatalog.Entry e=chosen.get(i);ImageView icon=new ImageView(this);icon.setImageDrawable(AppCatalog.icon(this,e.pkg));icon.setContentDescription(e.label);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(38),dp(38));lp.rightMargin=dp(12);preview.addView(icon,lp);
        }
        if(chosen.size()>5)preview.addView(BlueUi.label(this,"+"+(chosen.size()-5),14,BlueUi.MUTED,true));
    }
    private void statusRow(String icon,String title,String detail,String state,Runnable action){
        LinearLayout row=BlueUi.row(this);row.setPadding(0,dp(14),0,dp(14));row.setMinimumHeight(dp(76));row.setBackground(BlueUi.ripple(this,Color.TRANSPARENT,10));
        row.addView(new BlueUi.Glyph(this,icon,BlueUi.BLUE),new LinearLayout.LayoutParams(dp(21),dp(21)));
        LinearLayout labels=BlueUi.column(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(13),0,dp(6),0);row.addView(labels,lp);
        labels.addView(BlueUi.label(this,title,15,BlueUi.INK,true));BlueUi.gap(labels,6);labels.addView(BlueUi.label(this,detail,11,BlueUi.MUTED,false));
        row.addView(BlueUi.label(this,state,11,BlueUi.BLUE,true));row.addView(new BlueUi.Glyph(this,"next",BlueUi.MUTED),new LinearLayout.LayoutParams(dp(17),dp(17)));row.setOnClickListener(v->action.run());section.addView(row);
    }
    private void refreshClock(){
        if(clock==null)return;ZonedDateTime now=ZonedDateTime.now();boolean night=Policy.night(now.toLocalTime());
        long seconds=Math.max(0,Duration.between(now,Policy.nextBoundary(now)).getSeconds());
        phase.setText(night?"距离解除限制":"距离下次开始");clock.setText(String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60));
        badge.setText(GuardService.connected?(night?"限制时段":"等待夜间"):"尚未就绪");
    }
    private void accessibilitySettings(){new AlertDialog.Builder(this).setTitle("开启应用拦截").setMessage("在系统无障碍设置中开启“夜间守护”。仅观察前台应用包名并显示遮罩，不读取网页、聊天或密码。")
        .setPositiveButton("前往设置",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("取消",null).show();}
    private void backgroundHelp(){new AlertDialog.Builder(this).setTitle("检查华为后台运行").setMessage("在手机设置 → 应用和服务 → 应用启动管理中找到夜间守护，关闭自动管理，并允许自启动、关联启动和后台活动。\n\n不同系统入口可能不同；本应用不能自动确认这些选项。")
        .setPositiveButton("打开应用设置",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))))
        .setNeutralButton("我已检查",(d,w)->{store.p.edit().putBoolean("backgroundConfirmed",true).apply();render();}).setNegativeButton("取消",null).show();}
    private void unlockInfo(){new AlertDialog.Builder(this).setTitle("临时解锁规则").setMessage("在被拦截的应用页面申请临时使用。填写至少10字的原因，等待10分钟，再手动输入随机验证句。完成后仅开放该应用5分钟。\n\n重启会取消临时授权；夜间减少限制要等到08:00后生效。")
        .setPositiveButton("知道了",null).show();}
    private void rules(){new AlertDialog.Builder(this).setTitle("规则与说明").setMessage("每日按手机本地时间01:00–08:00执行。浏览器自动限制；电话、系统闹钟、桌面及系统设置保留。\n\n分类是本地识别建议，可在应用列表右侧菜单调整；社交类也可能包含必要通讯应用，请按需选择。\n\n应用内网页需要限制其宿主应用。普通安装不能阻止卸载、强行停止、撤销权限或更改时间；华为后台、多窗口和画中画效果需真机检查。\n\n数据仅存手机，不上传。")
        .setPositiveButton("关闭",null).show();}
    private void notificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},41);
        } else {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if (requestCode == 41) {
            if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this,"通知未开启，可在系统应用通知设置中允许",Toast.LENGTH_LONG).show();
            render();
        }
    }
    private void renderUnlock() {
        card(); text("给自己十分钟，再做决定",21);
        String name = target;
        try { name = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(target,0)).toString(); }
        catch (PackageManager.NameNotFoundException ignored) { }
        text(name+" · 验证后仅开放5分钟",14);
        if (!target.equals(store.p.getString("request",""))) {
            EditText reason = input("写下具体使用原因（至少10字）");
            button("开始10分钟冷静期",true,()->{
                String value = reason.getText().toString().trim();
                if(value.length()<10) { reason.setError("请填写至少10字的具体原因");return; }
                store.begin(target,value);render();
            });
        } else {
            countdown = text("",18);refreshCountdown();
            text("冷静期结束后，手动输入完整验证句：",14);
            TextView challenge = text(store.p.getString("phrase",""),18);challenge.setTextIsSelectable(false);
            EditText phrase = input("完整输入长句与随机数字");phrase.setLongClickable(false);
            phrase.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            button("完成验证，临时开放",true,()->{
                if(store.unlock(target,phrase.getText().toString())) {
                    String pkg=target;getIntent().removeExtra("target");target=null;render();
                    Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);if(launch!=null)startActivity(launch);
                } else Toast.makeText(this,"冷静期未结束，或验证句不一致",Toast.LENGTH_LONG).show();
            });
            button("取消申请，继续休息",false,()->{
                store.p.edit().remove("request").remove("start").remove("phrase").commit();
                getIntent().removeExtra("target");target=null;render();
            });
        }
    }
    private void refreshCountdown() {
        long start=store.p.getLong("start",-1);
        long remaining=Math.max(0,Policy.WAIT_MS-(SystemClock.elapsedRealtime()-start));
        countdown.setText(remaining==0?"冷静期已结束，请输入验证句":"冷静期剩余 "+(remaining/60000)+"分"+((remaining/1000)%60)+"秒");
    }

}
