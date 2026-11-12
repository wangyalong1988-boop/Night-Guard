package cn.nightguard;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class InterfaceTest {
    @Test public void homeIsRenderedAndCaptured() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            settle();scenario.onActivity(a->{assertNotNull(findText(a.getWindow().getDecorView(),"限制清单"));assertNotNull(findText(a.getWindow().getDecorView(),"守护状态"));});
            capture("home.png");
        }
    }
    @Test public void pickerSearchSelectionAndBrowserProtection() throws Exception {
        try(ActivityScenario<AppPickerActivity> scenario=ActivityScenario.launch(AppPickerActivity.class)){
            long deadline=android.os.SystemClock.elapsedRealtime()+15000;boolean[] ready={false};
            while(!ready[0]&&android.os.SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->ready[0]=a.selection!=null);if(!ready[0])Thread.sleep(100);}
            assertTrue("Installed application list loads",ready[0]);
            scenario.onActivity(a->{new Store(a).p.edit().remove("apps").remove("pending").apply();a.showEntries(fixtures());});
            scenario.onActivity(a->{View title=findText(a.getWindow().getDecorView(),"限制应用");android.graphics.Rect bounds=new android.graphics.Rect();assertNotNull(title);assertTrue("Picker title is visible",title.getGlobalVisibleRect(bounds)&&bounds.width()>0);});
            capture("apps-all.png");
            scenario.onActivity(a->{ListView list=findList(a.getWindow().getDecorView());list.setSelection(list.getCount()-1);});settle();
            scenario.onActivity(a->{View browser=findDescriptionPrefix(a.getWindow().getDecorView(),"浏览器，");assertNotNull("Browser row is shown",browser);
                browser.performClick();assertTrue("A browser can be ticked",a.selection.checked("fixture.browser"));
                findDescriptionPrefix(a.getWindow().getDecorView(),"浏览器，").performClick();assertFalse("A wrongly detected browser can be unticked",a.selection.checked("fixture.browser"));});
            scenario.onActivity(a->a.getWindow().getDecorView().post(()->findList(a.getWindow().getDecorView()).setSelection(0)));settle();
            int[] tap=new int[2];
            scenario.onActivity(a->{View search=findDescription(a.getWindow().getDecorView(),"搜索应用");android.graphics.Rect rect=new android.graphics.Rect();assertTrue(search.getGlobalVisibleRect(rect));tap[0]=rect.centerX();tap[1]=rect.centerY();});
            long eventTime=android.os.SystemClock.uptimeMillis();
            InstrumentationRegistry.getInstrumentation().sendPointerSync(android.view.MotionEvent.obtain(eventTime,eventTime,android.view.MotionEvent.ACTION_DOWN,tap[0],tap[1],0));
            InstrumentationRegistry.getInstrumentation().sendPointerSync(android.view.MotionEvent.obtain(eventTime,eventTime+50,android.view.MotionEvent.ACTION_UP,tap[0],tap[1],0));
            boolean[] imeVisible={false};long imeDeadline=android.os.SystemClock.elapsedRealtime()+5000;
            while(!imeVisible[0]&&android.os.SystemClock.elapsedRealtime()<imeDeadline){settle();scenario.onActivity(a->imeVisible[0]=a.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.ime()));}
            scenario.onActivity(a->{assertTrue("Software keyboard is actually visible",a.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.ime()));android.graphics.Rect bounds=new android.graphics.Rect();View save=findText(a.getWindow().getDecorView(),"保存限制清单");assertTrue("Save remains visible with keyboard",save.getGlobalVisibleRect(bounds)&&bounds.height()>0);ListView list=findList(a.getWindow().getDecorView());assertNotNull(list);assertTrue("Search results retain usable space",list.getHeight()>=BlueUi.dp(a,70));});
            capture("apps-keyboard.png");
            scenario.onActivity(a->a.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(a.getWindow().getDecorView().getWindowToken(),0));
            settle();
            scenario.onActivity(a->{View search=findDescription(a.getWindow().getDecorView(),"搜索应用");((EditText)search).setText("视频");});
            settle();scenario.onActivity(a->{assertNotNull(findText(a.getWindow().getDecorView(),"云端视频"));View row=findDescriptionPrefix(a.getWindow().getDecorView(),"云端视频，");assertNotNull(row);row.performClick();assertTrue(a.selection.checked("fixture.video"));});
            capture("apps-search.png");
            scenario.onActivity(a->{((EditText)findDescription(a.getWindow().getDecorView(),"搜索应用")).setText("");findText(a.getWindow().getDecorView(),"只看已选").performClick();});
            settle();scenario.onActivity(a->{assertNotNull(findText(a.getWindow().getDecorView(),"云端视频"));assertNull(findText(a.getWindow().getDecorView(),"轻音音乐"));findText(a.getWindow().getDecorView(),"清空结果").performClick();assertFalse(a.selection.checked("fixture.video"));});            scenario.onActivity(a->{findText(a.getWindow().getDecorView(),"✓ 只看已选").performClick();View chip=findText(a.getWindow().getDecorView(),"影音 2");assertNotNull(chip);chip.performClick();});
            settle();scenario.onActivity(a->{assertNotNull(findText(a.getWindow().getDecorView(),"轻音音乐"));assertNull(findText(a.getWindow().getDecorView(),"好友社交"));});
            capture("apps-media.png");
            scenario.onActivity(a->{findDescription(a.getWindow().getDecorView(),"调整云端视频分类").performClick();});
        }
    }
    @Test public void nightCoverIsRenderedAndCaptured() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            settle();
            scenario.onActivity(a->{Drawable icon=fixtures().get(0).icon;NightCover cover=NightCover.build(a,"fixture.video",icon,"云端视频",Policy.DEFAULT,3,15,false,()->{},()->{});a.setContentView(cover.root);
                assertNotNull(findText(cover.root,"云端视频 已经睡了"));assertNotNull(findText(cover.root,"今晚已帮你挡下 3 次"));});
            capture("cover.png");
        }
    }
    @Test public void homeFollowsDarkMode() throws Exception {
        shell("cmd uimode night yes");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            settle();scenario.onActivity(a->{assertTrue("Dark palette is used",BlueUi.dark);assertNotNull(findText(a.getWindow().getDecorView(),"限制清单"));});
            capture("home-dark.png");
        } finally { shell("cmd uimode night no"); }
    }
    @Test public void pendingChoiceIsNotReplacedByActiveNightSelection(){
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store store=new Store(c);
        store.p.edit().putStringSet("apps",Set.of("old","new")).putStringSet("pending",Set.of("new")).putLong("pendingAt",System.currentTimeMillis()+3600000).commit();
        assertEquals(Set.of("new"),store.desired());assertEquals(Set.of("old","new"),store.selected());
        store.p.edit().remove("pending").remove("pendingAt").remove("apps").commit();
    }
    private static List<AppCatalog.Entry> fixtures(){
        return List.of(entry("fixture.video","云端视频",AppCategory.MEDIA,0xff6b92c6,"play"),entry("fixture.music","轻音音乐",AppCategory.MEDIA,0xff5caaa0,"music"),entry("fixture.social","好友社交",AppCategory.SOCIAL,0xff7c9f76,"chat"),entry("fixture.game","方块游戏",AppCategory.GAME,0xffa48dc6,"game"),entry("fixture.browser","浏览器",AppCategory.BROWSER,0xff619cce,"web"),entry("fixture.notes","随手记",AppCategory.TOOL,0xffc29c60,"note"),entry("fixture.other","我的应用",AppCategory.OTHER,0xff9babc0,"grid"));
    }
    private static AppCatalog.Entry entry(String pkg,String name,String category,int color,String symbol){
        AppCatalog.Entry e=new AppCatalog.Entry(pkg,name,category,category.equals(AppCategory.BROWSER),-1);
        e.icon=new Drawable(){
            public void draw(Canvas c){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/48f,b.height()/48f);p.setColor(color);c.drawRoundRect(0,0,48,48,12,12,p);p.setColor(Color.WHITE);p.setStrokeWidth(2);p.setStyle(Paint.Style.STROKE);
                if(symbol.equals("play")){Path path=new Path();path.moveTo(19,14);path.lineTo(35,24);path.lineTo(19,34);path.close();p.setStyle(Paint.Style.FILL);c.drawPath(path,p);}
                else if(symbol.equals("web")){c.drawCircle(24,24,14,p);c.drawOval(17,10,31,38,p);c.drawLine(10,24,38,24,p);}
                else if(symbol.equals("music")){c.drawLine(20,15,20,32,p);c.drawLine(20,15,33,12,p);c.drawLine(33,12,33,29,p);c.drawOval(13,28,20,34,p);c.drawOval(26,25,33,31,p);}
                else {c.drawRoundRect(12,13,36,35,4,4,p);c.drawLine(17,20,31,20,p);c.drawLine(17,27,28,27,p);}c.restore();}
            public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };return e;
    }
    private static void settle()throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(250);}
    private static void capture(String name)throws Exception{
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(350);
        // Gradle removes the tested APK after the suite; export as shell-owned files first.
        String path="/data/local/tmp/nightguard-"+name;
        shell("screencap -p "+path);
        byte[] bytes=shell("cat "+path);
        assertTrue("Screenshot is a PNG",bytes.length>8&&bytes[0]==(byte)0x89&&bytes[1]=='P'&&bytes[2]=='N'&&bytes[3]=='G');
    }
    private static byte[] shell(String command)throws Exception{
        try(android.os.ParcelFileDescriptor descriptor=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
            InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
            ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
            while((count=in.read(buffer))!=-1)output.write(buffer,0,count);
            return output.toByteArray();
        }
    }
    private static ListView findList(View v){if(v instanceof ListView)return (ListView)v;return (ListView)children(v,InterfaceTest::findList);}
    private static View findText(View view,String text){if(view instanceof TextView&&((TextView)view).getText().toString().equals(text))return view;return children(view,v->findText(v,text));}
    private static View findDescription(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;return children(view,v->findDescription(v,text));}
    private static View findDescriptionPrefix(View view,String text){if(view.getContentDescription()!=null&&view.getContentDescription().toString().startsWith(text))return view;return children(view,v->findDescriptionPrefix(v,text));}
    private interface Lookup {View find(View v);}
    private static View children(View v,Lookup lookup){if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View result=lookup.find(((ViewGroup)v).getChildAt(i));if(result!=null)return result;}return null;}
}
