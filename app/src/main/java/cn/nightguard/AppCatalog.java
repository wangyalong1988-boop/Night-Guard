package cn.nightguard;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import java.util.*;

final class AppCatalog {
    static final class Entry {
        final String pkg,label; final boolean browser; final int systemCategory;
        String category; Drawable icon;
        Entry(String pkg,String label,String category,boolean browser,int systemCategory){
            this.pkg=pkg;this.label=label;this.category=category;this.browser=browser;this.systemCategory=systemCategory;
        }
    }
    static List<Entry> load(Context c){
        PackageManager pm=c.getPackageManager();Set<String> browsers=Apps.browsers(c);
        Map<String,String> installed=new HashMap<>(Apps.launchers(c));
        for(String pkg:browsers)try{
            ApplicationInfo info=pm.getApplicationInfo(pkg,0);
            if(!Apps.safe(c,pkg))installed.put(pkg,pm.getApplicationLabel(info).toString());
        }catch(PackageManager.NameNotFoundException ignored){}
        List<Entry> result=new ArrayList<>();
        android.content.SharedPreferences p=c.getSharedPreferences("guard",0);
        for(Map.Entry<String,String> item:installed.entrySet())try{
            ApplicationInfo info=pm.getApplicationInfo(item.getKey(),0);
            boolean browser=browsers.contains(item.getKey());
            String category=AppCategory.classify(item.getKey(),info.category,browser,p.getString("category:"+item.getKey(),null));
            Entry entry=new Entry(item.getKey(),item.getValue(),category,browser,info.category);
            entry.icon=pm.getApplicationIcon(info);result.add(entry);
        }catch(PackageManager.NameNotFoundException ignored){}
        Collator collator=Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        result.sort((a,b)->{int order=collator.compare(a.label,b.label);return order!=0?order:a.pkg.compareTo(b.pkg);});
        return result;
    }
    static Drawable icon(Context c,String pkg){
        try{return c.getPackageManager().getApplicationIcon(pkg);}
        catch(PackageManager.NameNotFoundException ignored){return c.getPackageManager().getDefaultActivityIcon();}
    }
}
