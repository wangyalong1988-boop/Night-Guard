package cn.nightguard;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.content.Intent;
public class GuardService extends AccessibilityService {
 static boolean settingsVisible=false;
 static volatile boolean connected=false;
 private final Handler handler=new Handler(Looper.getMainLooper()); private Store store; private String current=""; private java.util.Set<String> browserCache=java.util.Set.of(); private long browserScan=-60000; private GuardNotification notification; private TextView coverCountdown; private View cover; private WindowManager wm;
 private final Runnable tick=new Runnable(){ public void run(){ check();if(notification!=null)notification.refresh();handler.postDelayed(this,1000); } };
 @Override public void onServiceConnected(){connected=true;store=new Store(this);notification=new GuardNotification(this);wm=getSystemService(WindowManager.class);handler.post(tick);}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){ if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName()!=null){String pkg=e.getPackageName().toString();for(android.view.inputmethod.InputMethodInfo input:getSystemService(android.view.inputmethod.InputMethodManager.class).getEnabledInputMethodList())if(pkg.equals(input.getPackageName()))return;if(!pkg.equals(getPackageName()))current=pkg;check();} }
 private void check(){if(store==null)return; long now=SystemClock.elapsedRealtime();if(now-browserScan>=60000){browserCache=Apps.browsers(this);browserScan=now;} boolean block=!settingsVisible&&store.night()&&!Apps.safe(this,current)&&(store.selected().contains(current)||browserCache.contains(current))&&!store.allowed(current);if(block){show();if(coverCountdown!=null){long seconds=Math.max(0,java.time.Duration.between(java.time.ZonedDateTime.now(),Policy.nextBoundary(java.time.ZonedDateTime.now())).getSeconds());coverCountdown.setText(String.format(java.util.Locale.ROOT,"距离解除  %02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60));}}else hide();}
 private void show(){
  if(cover!=null)return;
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BlueUi.PAGE);
  LinearLayout page=BlueUi.column(this);page.setGravity(Gravity.CENTER);int pad=BlueUi.dp(this,24);page.setPadding(pad,pad*2,pad,pad*2);scroll.addView(page);
  LinearLayout card=BlueUi.column(this);card.setPadding(pad,pad,pad,pad);card.setBackground(BlueUi.background(this,android.graphics.Color.WHITE,0,20));page.addView(card,new LinearLayout.LayoutParams(-1,-2));
  ImageView appIcon=new ImageView(this);appIcon.setImageDrawable(AppCatalog.icon(this,current));LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(BlueUi.dp(this,48),BlueUi.dp(this,48));iconLp.gravity=Gravity.CENTER;card.addView(appIcon,iconLp);BlueUi.gap(card,20);
  TextView title=BlueUi.label(this,"当前应用已限制",23,BlueUi.INK,true);title.setGravity(Gravity.CENTER);card.addView(title);BlueUi.gap(card,10);
  String appName=current;try{appName=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(current,0)).toString();}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
  TextView name=BlueUi.label(this,appName,14,BlueUi.MUTED,false);name.setGravity(Gravity.CENTER);card.addView(name);BlueUi.gap(card,24);
  coverCountdown=BlueUi.label(this,"",18,BlueUi.BLUE,true);coverCountdown.setGravity(Gravity.CENTER);card.addView(coverCountdown);BlueUi.gap(card,10);
  TextView schedule=BlueUi.label(this,"每日01:00–08:00 · 到时自动解除",12,BlueUi.MUTED,false);schedule.setGravity(Gravity.CENTER);card.addView(schedule);BlueUi.gap(card,24);
  Button home=BlueUi.button(this,"返回桌面",true,()->{performGlobalAction(GLOBAL_ACTION_HOME);current="";hide();});card.addView(home,new LinearLayout.LayoutParams(-1,-2));BlueUi.gap(card,12);
  Button unlock=BlueUi.button(this,"申请临时使用",false,()->{String target=current;current="";hide();startActivity(new Intent(this,MainActivity.class).putExtra("target",target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));});card.addView(unlock,new LinearLayout.LayoutParams(-1,-2));BlueUi.gap(card,12);
  TextView rules=BlueUi.label(this,"需等待10分钟，验证后仅开放5分钟",12,BlueUi.MUTED,false);rules.setGravity(Gravity.CENTER);card.addView(rules);
  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.OPAQUE);
  try{wm.addView(scroll,lp);cover=scroll;}catch(RuntimeException ignored){coverCountdown=null;performGlobalAction(GLOBAL_ACTION_HOME);}
 }
 private void hide(){if(cover!=null){wm.removeView(cover);cover=null;coverCountdown=null;}}
 @Override public void onInterrupt(){hide();}
 @Override public void onDestroy(){connected=false;handler.removeCallbacks(tick);if(notification!=null)notification.cancel();hide();super.onDestroy();}
}
