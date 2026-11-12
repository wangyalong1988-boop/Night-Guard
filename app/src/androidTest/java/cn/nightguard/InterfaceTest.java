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
            capture("apps-all.png");
            scenario.onActivity(a->{View search=findDescription(a.getWindow().getDecorView(),"搜索应用");((EditText)search).setText("视频");});
            settle();scenario.onActivity(a->{assertNotNull(findText(a.getWindow().getDecorView(),"云端视频"));View row=findDescriptionPrefix(a.getWindow().getDecorView(),"云端视频，");assertNotNull(row);row.performClick();assertTrue(a.selection.checked("fixture.video"));});
            capture("apps-search.png");
            scenario.onActivity(a->{((EditText)findDescription(a.getWindow().getDecorView(),"搜索应用")).setText("");findText(a.getWindow().getDecorView(),"只看已选").performClick();});
            settle();scenario.onActivity(a->{assertNotNull(findText(a.getWindow().getDecorView(),"云端视频"));assertNull(findText(a.getWindow().getDecorView(),"轻音音乐"));findText(a.getWindow().getDecorView(),"清空结果").performClick();assertTrue(a.selection.checked("fixture.browser"));assertFalse(a.selection.checked("fixture.video"));});
            scenario.onActivity(a->{findText(a.getWindow().getDecorView(),"✓ 只看已选").performClick();View chip=findText(a.getWindow().getDecorView(),"影音 2");assertNotNull(chip);chip.performClick();});
            settle();scenario.onActivity(a->{assertNotNull(findText(a.getWindow().getDecorView(),"轻音音乐"));assertNull(findText(a.getWindow().getDecorView(),"好友社交"));});
            capture("apps-media.png");
            scenario.onActivity(a->{findDescription(a.getWindow().getDecorView(),"调整云端视频分类").performClick();});
        }
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
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
        File file=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),name);
        try(FileOutputStream out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    private static View findText(View view,String text){if(view instanceof TextView&&((TextView)view).getText().toString().equals(text))return view;return children(view,v->findText(v,text));}
    private static View findDescription(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;return children(view,v->findDescription(v,text));}
    private static View findDescriptionPrefix(View view,String text){if(view.getContentDescription()!=null&&view.getContentDescription().toString().startsWith(text))return view;return children(view,v->findDescriptionPrefix(v,text));}
    private interface Lookup {View find(View v);}
    private static View children(View v,Lookup lookup){if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View result=lookup.find(((ViewGroup)v).getChildAt(i));if(result!=null)return result;}return null;}
}
