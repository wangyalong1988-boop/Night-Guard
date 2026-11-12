package cn.nightguard;

import java.util.*;

/** Conservative local suggestions; an application can always be recategorized. */
final class AppCategory {
    static final String ALL="全部", MEDIA="影音", SOCIAL="社交", GAME="游戏", BROWSER="浏览器", TOOL="工具", OTHER="待分类";
    static final List<String> CATEGORIES=List.of(MEDIA,SOCIAL,GAME,BROWSER,TOOL,OTHER);
    private static final Map<String,String> KNOWN=new HashMap<>();
    static {
        add(MEDIA,"com.ss.android.ugc.aweme","com.ss.android.ugc.aweme.lite","com.smile.gifmaker","com.kuaishou.nebula","tv.danmaku.bili","com.bilibili.app.in","com.tencent.qqlive","com.qiyi.video","com.youku.phone","com.google.android.youtube","com.netflix.mediaclient","com.spotify.music","com.netease.cloudmusic","com.tencent.qqmusic","com.kugou.android","com.kugou.android.lite","com.zhiliaoapp.musically","com.ss.android.article.video","com.ss.android.ugc.live","com.ss.android.ugc.aweme.lite","com.twitch.android.app");
        add(SOCIAL,"com.xingin.xhs","com.sina.weibo","com.zhihu.android","com.baidu.tieba","com.tencent.mm","com.tencent.mobileqq","com.instagram.android","com.facebook.katana","com.twitter.android","com.reddit.frontpage","org.telegram.messenger","com.whatsapp","com.discord");
        add(GAME,"com.tencent.tmgp.sgame","com.tencent.tmgp.pubgmhd","com.miHoYo.Yuanshen","com.miHoYo.hkrpg","com.supercell.clashofclans");
    }
    private static void add(String group,String... packages) { for(String pkg:packages)KNOWN.put(pkg,group); }
    static String classify(String pkg,int systemCategory,boolean browser,String override) {
        if(browser)return BROWSER;
        if(override!=null&&CATEGORIES.contains(override)&&!BROWSER.equals(override))return override;
        if(KNOWN.containsKey(pkg))return KNOWN.get(pkg);
        // Android ApplicationInfo category constants: game=0, audio=1, video=2, image=3.
        switch(systemCategory) {
            case 0:return GAME;
            case 1:case 2:return MEDIA;
            case 4:return SOCIAL;
            case 6:case 7:return TOOL;
            default:return OTHER;
        }
    }
    static boolean matches(String label,String pkg,String category,String filter,String query) {
        String q=query.trim().toLowerCase(Locale.ROOT);
        return (ALL.equals(filter)||category.equals(filter)) &&
                (label.toLowerCase(Locale.ROOT).contains(q)||pkg.toLowerCase(Locale.ROOT).contains(q));
    }
}
