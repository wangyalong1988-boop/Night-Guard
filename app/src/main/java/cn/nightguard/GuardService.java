package cn.nightguard;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.content.Intent;
public class GuardService extends AccessibilityService {
 static boolean settingsVisible=false;
 private final Handler handler=new Handler(Looper.getMainLooper()); private Store store; private String current=""; private View cover; private WindowManager wm;
 private final Runnable tick=new Runnable(){ public void run(){ check();handler.postDelayed(this,1000); } };
 @Override public void onServiceConnected(){store=new Store(this);wm=getSystemService(WindowManager.class);handler.post(tick);}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){ if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName()!=null){String pkg=e.getPackageName().toString();if(!pkg.equals(getPackageName()))current=pkg;check();} }
 private void check(){if(store==null)return; boolean block=!settingsVisible&&store.night()&&!Apps.safe(this,current)&&(store.selected().contains(current)||Apps.browsers(this).contains(current))&&!store.allowed(current);if(block)show();else hide();}
 private void show(){if(cover!=null)return; LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(48,100,48,48);box.setGravity(Gravity.CENTER);box.setBackgroundColor(0xff111b32);
 TextView title=new TextView(this);title.setText("今晚先休息\n01:00 — 08:00\n此应用处于限制时段");title.setTextSize(26);title.setTextColor(0xffeeeeff);box.addView(title);
 Button home=new Button(this);home.setText("回到桌面");home.setOnClickListener(v->{performGlobalAction(GLOBAL_ACTION_HOME);current="";hide();});box.addView(home);
 Button unlock=new Button(this);unlock.setText("申请临时使用 · 等待10分钟");unlock.setOnClickListener(v->{String target=current;current="";hide();startActivity(new Intent(this,MainActivity.class).putExtra("target",target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));});box.addView(unlock);
 WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.OPAQUE); try{wm.addView(box,lp);cover=box;}catch(RuntimeException ignored){performGlobalAction(GLOBAL_ACTION_HOME);} }
 private void hide(){if(cover!=null){wm.removeView(cover);cover=null;}}
 @Override public void onInterrupt(){hide();}
 @Override public void onDestroy(){handler.removeCallbacks(tick);hide();super.onDestroy();}
}
