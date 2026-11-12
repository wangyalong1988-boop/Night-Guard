package cn.nightguard;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.telecom.TelecomManager;
import java.util.*;
final class Apps {
 static Set<String> browsers(Context c){ Set<String> r=new HashSet<>(Arrays.asList("com.android.chrome","com.huawei.browser","com.android.browser","org.mozilla.firefox","com.microsoft.emmx","com.UCMobile","com.quark.browser","com.tencent.mtt","com.opera.browser","com.brave.browser")); Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE); for(ResolveInfo a:c.getPackageManager().queryIntentActivities(i,PackageManager.MATCH_ALL)) if(a.handleAllWebDataURI) r.add(a.activityInfo.packageName); return r; }
 static boolean safe(Context c,String pkg){ if(pkg.equals(c.getPackageName()) || pkg.equals("com.android.settings") || pkg.equals("com.android.systemui") || pkg.equals("com.android.emergency"))return true;
 PackageManager pm=c.getPackageManager(); for(ResolveInfo a:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY)) if(pkg.equals(a.activityInfo.packageName))return true;
 TelecomManager telecom=c.getSystemService(TelecomManager.class); if(telecom!=null && pkg.equals(telecom.getDefaultDialerPackage()))return true;
 return pkg.equals("com.android.deskclock")||pkg.equals("com.huawei.deskclock")||pkg.equals("com.android.phone")||pkg.equals("com.android.contacts"); }
 static Map<String,String> launchers(Context c){ Map<String,String> r=new TreeMap<>(); for(ResolveInfo a:c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){String pkg=a.activityInfo.packageName;if(!safe(c,pkg))r.put(pkg,a.loadLabel(c.getPackageManager()).toString());}return r; }
}
