package cn.nightguard;
import android.app.*;
import android.os.*;
import android.content.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import java.util.*;
public class MainActivity extends Activity {
 private Store store; private LinearLayout root; private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable timer=new Runnable(){public void run(){if(countdown!=null)refreshCountdown();handler.postDelayed(this,1000);}}; private TextView countdown; private String target;
 @Override public void onCreate(Bundle b){super.onCreate(b);store=new Store(this);render();handler.post(timer);}
 @Override public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);render();}
 @Override public void onResume(){super.onResume();GuardService.settingsVisible=true;if(store!=null)render();}
 @Override public void onPause(){GuardService.settingsVisible=false;super.onPause();}
 @Override public void onDestroy(){handler.removeCallbacks(timer);super.onDestroy();}
 private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(229,235,252));t.setPadding(0,12,0,12);root.addView(t);return t;}
 private Button button(String value,Runnable action){Button b=new Button(this);b.setText(value);root.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->action.run());return b;}
 private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.LTGRAY);root.addView(e);return e;}
 private void render(){countdown=null;ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,48,32,48);root.setBackgroundColor(0xff111b32);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
 text("☾ 夜间守护",32);text("每天 01:00 — 08:00",24);text(store.night()?"当前处于夜间限制时段":"白天自由使用 · 凌晨自动进入限制",16);
 String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);boolean active=enabled!=null&&enabled.contains(getPackageName()+"/");text(active?"✓ 无障碍权限已启用（实际效果需真机检查）":"尚未启用无障碍服务，限制不会生效",16);
 button("设置守护权限",()->new AlertDialog.Builder(this).setTitle("无障碍权限用途").setMessage("仅观察前台应用包名、显示拦截遮罩，不读取屏幕文字、网页或聊天内容。数据留在手机。您可随时在系统设置关闭权限。华为手机还需允许应用后台运行。是否前往系统设置？").setPositiveButton("前往设置",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("取消",null).show());
 text("所有浏览器在夜间自动限制。请在下方另外勾选娱乐、游戏及流媒体应用；应用分类无法保证自动识别完整。",16);
 button("选择需要限制的应用",this::selectApps);
 target=getIntent().getStringExtra("target");if(target==null)target=store.p.getString("request",null);
 if(target!=null&&!Apps.safe(this,target)){text("临时使用申请",24);text("目标："+target+"\n仅开放此应用5分钟；浏览器也需经过同样流程。",15);
 if(!target.equals(store.p.getString("request",""))){EditText reason=input("输入使用原因（至少10字）");button("开始10分钟冷静期",()->{String s=reason.getText().toString().trim();if(s.length()<10){reason.setError("请填写至少10字的具体原因");return;}store.begin(target,s);render();});}
 else{countdown=text("",18);refreshCountdown();text("冷静期结束后，手动输入下方整句：",15);TextView challenge=text(store.p.getString("phrase",""),20);challenge.setTextIsSelectable(false);EditText phrase=input("完整输入长句与随机数字");phrase.setLongClickable(false);phrase.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);button("完成验证，开放5分钟",()->{if(store.unlock(target,phrase.getText().toString())){String pkg=target;getIntent().removeExtra("target");target=null;render();Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);if(launch!=null)startActivity(launch);}else Toast.makeText(this,"冷静期未结束，或输入内容不一致",Toast.LENGTH_LONG).show();});button("取消申请",()->{store.p.edit().remove("request").remove("start").remove("phrase").commit();getIntent().removeExtra("target");target=null;render();});}}
 text("使用说明",24);text("夜间减少限制的设置会在08:00后生效。新装浏览器自动检查；其他新装娱乐应用请补充勾选。\n\n请在华为应用启动管理中允许后台运行。普通安装无法防止卸载、强行停止、关闭权限或修改系统时间。应用内网页由宿主应用决定是否拦截；多窗口和画中画需要真机验证。",15);if(store.p.contains("pending"))text("有一份应用名单调整将在早上08:00后生效。",16);text("最近一次临时解锁：\n"+store.p.getString("last","暂无记录"),14);
 }
 private void refreshCountdown(){long start=store.p.getLong("start",-1);long remain=Math.max(0,Policy.WAIT_MS-(SystemClock.elapsedRealtime()-start));countdown.setText(remain==0?"冷静期已结束，请输入验证句":"冷静期剩余 "+(remain/60000)+"分"+((remain/1000)%60)+"秒");}
 private void selectApps(){Map<String,String> apps=Apps.launchers(this);String[] pkgs=apps.keySet().toArray(new String[0]);String[] labels=new String[pkgs.length];boolean[] flags=new boolean[pkgs.length];Set<String> selected=store.selected();Set<String> browsers=Apps.browsers(this);for(int n=0;n<pkgs.length;n++){labels[n]=apps.get(pkgs[n])+"\n"+pkgs[n]+(browsers.contains(pkgs[n])?"（浏览器自动限制）":"");flags[n]=selected.contains(pkgs[n])||browsers.contains(pkgs[n]);}
 new AlertDialog.Builder(this).setTitle("娱乐与流媒体应用名单").setMultiChoiceItems(labels,flags,(d,n,checked)->flags[n]=checked).setPositiveButton("保存",(d,w)->{Set<String> values=new HashSet<>();for(int n=0;n<pkgs.length;n++)if(flags[n])values.add(pkgs[n]);store.save(values);render();}).setNegativeButton("取消",null).show(); }
}
