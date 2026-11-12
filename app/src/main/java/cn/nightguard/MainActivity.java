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
import android.graphics.drawable.GradientDrawable;
import java.time.*;
import java.util.*;

public class MainActivity extends Activity {
    private Store store;
    private LinearLayout root, section;
    private TextView countdown, stage;
    private BlueUi.Dial dial;
    private String target;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timer = new Runnable() {
        public void run() {
            if (countdown != null) refreshCountdown();
            refreshDial();
            handler.postDelayed(this, 1000);
        }
    };
    private int dp(float value) { return BlueUi.dp(this, value); }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); store = new Store(this); render();
    }
    @Override public void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); render(); }
    @Override public void onResume() {
        super.onResume(); GuardService.settingsVisible = true;
        if (store != null) render();
        handler.removeCallbacks(timer); handler.post(timer);
    }
    @Override public void onPause() {
        GuardService.settingsVisible = false; handler.removeCallbacks(timer); super.onPause();
    }
    @Override public void onDestroy() { handler.removeCallbacks(timer); super.onDestroy(); }
    private TextView text(String value, int size) {
        TextView t = BlueUi.text(this, value, size, size >= 20 ? BlueUi.INK : BlueUi.MUTED);
        if (size >= 20) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        section.addView(t); return t;
    }
    private void button(String value, boolean primary, Runnable action) {
        section.addView(BlueUi.button(this, value, primary, action));
    }
    private void card() {
        section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(20), dp(16), dp(20), dp(20));
        section.setBackground(BlueUi.background(this, Color.WHITE, 0xffdfecf8, 24));
        section.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16); root.addView(section, lp);
    }
    private EditText input(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(16);
        e.setTextColor(BlueUi.INK); e.setHintTextColor(BlueUi.MUTED);
        e.setPadding(dp(14),dp(14),dp(14),dp(14));
        e.setBackground(BlueUi.background(this, 0xfff4f9fe, 0xffd5e6f6, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin = dp(8); section.addView(e,lp); return e;
    }
    private boolean guardEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName()+"/");
    }
    private void render() {
        countdown = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xffedf6ff); scroll.setClipToPadding(false); scroll.setFillViewport(true);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(32)); scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        section = root;
        text("N I G H T   G U A R D",12);
        text("夜间守护",32);
        text("把夜晚留给休息，把清晨留给自己。",14);
        card();
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xfffafdff,0xffdceeff});
        gradient.setCornerRadius(dp(24)); section.setBackground(gradient);
        stage = text("",20); stage.setGravity(Gravity.CENTER);
        dial = new BlueUi.Dial(this);
        LinearLayout.LayoutParams dialParams = new LinearLayout.LayoutParams(dp(224), dp(224));
        dialParams.gravity = Gravity.CENTER; section.addView(dial,dialParams);
        TextView schedule = text("每天 01:00 — 08:00  ·  自动守护",14); schedule.setGravity(Gravity.CENTER);
        refreshDial();
        card(); text("守护设置",21);
        text(guardEnabled()?"● 无障碍权限已开启":"○ 尚未启用守护权限",15);
        text("已选择 "+store.selected().size()+" 个应用 · 浏览器自动纳入限制",14);
        button("管理限制应用",true,this::selectApps);
        button("设置守护权限",false,()->new AlertDialog.Builder(this)
            .setTitle("开启夜间守护").setMessage("仅观察前台应用包名、显示拦截遮罩，不读取网页、聊天或密码。数据仅存手机。华为手机还需允许后台运行，实际限制效果请在手机上检查。")
            .setPositiveButton("前往系统设置",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            .setNegativeButton("稍后",null).show());
        card(); text("夜间倒计时通知",21);
        text("仅在凌晨01:00至早上08:00显示距离解除限制的倒计时；白天不显示通知。",14);
        button(GuardNotification.permitted(this)?"通知已允许 · 管理通知":"开启倒计时通知",false,this::notificationSettings);
        target = getIntent().getStringExtra("target");
        if (target == null) target = store.p.getString("request",null);
        if (target != null && !Apps.safe(this,target)) renderUnlock();
        card(); text("安心使用",21);
        text("电话、闹钟等核心功能保留。夜间减少限制的设置，早上08:00后生效。新装娱乐应用请补充勾选。",14);
        if (store.p.contains("pending")) text("应用名单调整将在早上08:00后生效。",14);
        text("请在华为应用启动管理中允许后台运行。卸载、强行停止、撤销权限或更改时间可能绕过限制；应用内网页随宿主应用处理。",13);
        card(); text("最近一次临时使用",18); text(store.p.getString("last","还没有临时使用记录，继续保持。"),13);
        section = root;
        TextView footer = text("本地保存  ·  无广告  ·  无需联网",12); footer.setGravity(Gravity.CENTER);
    }
    private void refreshDial() {
        if (dial == null) return;
        ZonedDateTime now = ZonedDateTime.now();
        boolean night = Policy.night(now.toLocalTime());
        long remaining = Math.max(0,Duration.between(now,Policy.nextBoundary(now)).getSeconds());
        String time = String.format(Locale.ROOT,"%02d:%02d:%02d",remaining/3600,(remaining/60)%60,remaining%60);
        float total = (night?7:17)*3600f;
        dial.update(time, night?"距离解除限制":"距离开始守护",1-remaining/total);
        stage.setText(guardEnabled()?(night?"☾ 正在守护你的夜晚":"☀ 今天，也留一点时间给自己"):"☾ 开启权限，让夜晚安静下来");
    }
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
 private void selectApps(){Map<String,String> apps=Apps.launchers(this);String[] pkgs=apps.keySet().toArray(new String[0]);String[] labels=new String[pkgs.length];boolean[] flags=new boolean[pkgs.length];Set<String> selected=store.selected();Set<String> browsers=Apps.browsers(this);for(int n=0;n<pkgs.length;n++){labels[n]=apps.get(pkgs[n])+"\n"+pkgs[n]+(browsers.contains(pkgs[n])?"（浏览器自动限制）":"");flags[n]=selected.contains(pkgs[n])||browsers.contains(pkgs[n]);}
 new AlertDialog.Builder(this).setTitle("娱乐与流媒体应用名单").setMultiChoiceItems(labels,flags,(d,n,checked)->flags[n]=checked).setPositiveButton("保存",(d,w)->{Set<String> values=new HashSet<>();for(int n=0;n<pkgs.length;n++)if(flags[n])values.add(pkgs[n]);store.save(values);render();}).setNegativeButton("取消",null).show(); }

}
