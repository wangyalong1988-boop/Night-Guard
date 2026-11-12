package cn.nightguard;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.telecom.TelecomManager;
import java.util.*;

/** System queries about installed apps. Results are cheap to cache; see {@link GuardService}. */
final class Apps {
    /** Well-known browsers; they count only when installed. */
    static final Set<String> KNOWN_BROWSERS = Set.of(
            "com.android.chrome", "com.chrome.beta", "com.huawei.browser", "com.android.browser", "org.mozilla.firefox",
            "org.mozilla.focus", "com.microsoft.emmx", "com.UCMobile", "com.uc.browser.en", "com.quark.browser", "com.tencent.mtt",
            "com.opera.browser", "com.opera.mini.native", "com.brave.browser", "com.duckduckgo.mobile.android", "com.vivaldi.browser",
            "com.sec.android.app.sbrowser", "com.mi.globalbrowser", "com.heytap.browser", "com.baidu.searchbox", "com.qihoo.browser",
            "com.sogou.activity.src", "mark.via", "mark.via.gp", "com.mmbox.xbrowser");

    private static final Set<String> ALWAYS_SAFE = Set.of(
            "com.android.settings", "com.android.systemui", "com.android.emergency", "com.android.phone", "com.android.contacts",
            "com.android.deskclock", "com.huawei.deskclock", "com.android.server.telecom", "com.huawei.systemmanager",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.packageinstaller");

    /**
     * An app is treated as a browser when it opens any http and https address (not only its own
     * site) and also looks like a browser: it is the default browser, declares the browser
     * category, handles web search, or is a well-known browser. Opening links alone is not
     * enough: office, notes and cloud-drive apps do that too, which caused wrong guesses before.
     */
    static Set<String> browsers(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> generic = new HashSet<>(genericHandlers(pm, "https"));
        generic.retainAll(genericHandlers(pm, "http"));
        Set<String> evidence = new HashSet<>(KNOWN_BROWSERS);
        for (ResolveInfo a : pm.queryIntentActivities(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER), 0))
            evidence.add(a.activityInfo.packageName);
        for (ResolveInfo a : pm.queryIntentActivities(new Intent(Intent.ACTION_WEB_SEARCH), 0))
            evidence.add(a.activityInfo.packageName);
        String preferred = defaultBrowser(pm);
        if (preferred != null) evidence.add(preferred);
        Set<String> result = new HashSet<>();
        for (String pkg : generic) if (evidence.contains(pkg) && launchable(pm, pkg)) result.add(pkg);
        for (String pkg : KNOWN_BROWSERS) if (launchable(pm, pkg)) result.add(pkg);
        result.remove(c.getPackageName());
        return result;
    }

    private static Set<String> genericHandlers(PackageManager pm, String scheme) {
        Set<String> r = new HashSet<>();
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(scheme + "://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE);
        for (ResolveInfo a : pm.queryIntentActivities(i, PackageManager.MATCH_ALL | PackageManager.GET_RESOLVED_FILTER))
            if (a.filter != null && a.filter.countDataAuthorities() == 0 && a.filter.countDataPaths() == 0) r.add(a.activityInfo.packageName);
        return r;
    }

    private static String defaultBrowser(PackageManager pm) {
        ResolveInfo r = pm.resolveActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE), PackageManager.MATCH_DEFAULT_ONLY);
        if (r == null || r.activityInfo == null) return null;
        String pkg = r.activityInfo.packageName;
        return "android".equals(pkg) || pkg.contains("resolver") ? null : pkg;
    }

    private static boolean launchable(PackageManager pm, String pkg) { return pm.getLaunchIntentForPackage(pkg) != null; }

    /** Packages that are never covered: this app, the launcher, dialer, clock, settings and system screens. */
    static Set<String> safeSet(Context c) {
        Set<String> r = new HashSet<>(ALWAYS_SAFE);
        r.add(c.getPackageName());
        PackageManager pm = c.getPackageManager();
        for (ResolveInfo a : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY))
            r.add(a.activityInfo.packageName);
        TelecomManager telecom = c.getSystemService(TelecomManager.class);
        if (telecom != null && telecom.getDefaultDialerPackage() != null) r.add(telecom.getDefaultDialerPackage());
        for (android.view.inputmethod.InputMethodInfo input : c.getSystemService(android.view.inputmethod.InputMethodManager.class).getEnabledInputMethodList())
            r.add(input.getPackageName());
        return r;
    }

    static boolean safe(Context c, String pkg) { return pkg == null || pkg.isEmpty() || safeSet(c).contains(pkg); }

    static Map<String, String> launchers(Context c) {
        Map<String, String> r = new TreeMap<>(); Set<String> safe = safeSet(c);
        for (ResolveInfo a : c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            String pkg = a.activityInfo.packageName;
            if (!safe.contains(pkg)) r.put(pkg, a.loadLabel(c.getPackageManager()).toString());
        }
        return r;
    }

    static String label(Context c, String pkg) {
        try { return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (PackageManager.NameNotFoundException e) { return pkg; }
    }
}
