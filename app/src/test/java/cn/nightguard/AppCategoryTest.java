package cn.nightguard;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppCategoryTest {
    @Test public void detectedBrowserWinsOverSystemCategory(){assertEquals(AppCategory.BROWSER,AppCategory.classify("browser",0,true,null));}
    @Test public void userCanCorrectAWrongBrowserGuess(){
        assertEquals(AppCategory.TOOL,AppCategory.classify("office.app",-1,true,AppCategory.TOOL));
        assertEquals(AppCategory.BROWSER,AppCategory.classify("missed.browser",-1,false,AppCategory.BROWSER));
    }
    @Test public void knownAppsHaveUsefulCategories(){
        assertEquals(AppCategory.MEDIA,AppCategory.classify("tv.danmaku.bili",-1,false,null));
        assertEquals(AppCategory.SOCIAL,AppCategory.classify("com.tencent.mm",-1,false,null));
        assertEquals(AppCategory.GAME,AppCategory.classify("com.tencent.tmgp.sgame",-1,false,null));
    }
    @Test public void overridesPersistAndInvalidOnesAreIgnored(){
        assertEquals(AppCategory.TOOL,AppCategory.classify("com.tencent.mm",4,false,AppCategory.TOOL));
        assertEquals(AppCategory.OTHER,AppCategory.classify("unknown",-1,false,"bad"));
    }
    @Test public void androidCategoryAndUnknownFallback(){
        assertEquals(AppCategory.MEDIA,AppCategory.classify("new.video",2,false,null));
        assertEquals(AppCategory.MEDIA,AppCategory.classify("new.audio",1,false,null));
        assertEquals(AppCategory.OTHER,AppCategory.classify("new.photo",3,false,null));
    }
    @Test public void searchAndCategoryWorkTogether(){
        assertTrue(AppCategory.matches("哔哩哔哩","tv.danmaku.bili",AppCategory.MEDIA,AppCategory.ALL," 哔哩 "));
        assertTrue(AppCategory.matches("Video","test.video",AppCategory.MEDIA,AppCategory.MEDIA,"VIDEO"));
        assertFalse(AppCategory.matches("Video","test.video",AppCategory.MEDIA,AppCategory.GAME,"video"));
    }
}
