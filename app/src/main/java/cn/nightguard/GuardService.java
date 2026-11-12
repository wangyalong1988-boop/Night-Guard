package cn.nightguard;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Watches which apps are on screen and covers restricted ones during the night.
 *
 * <p>It looks at every visible app window, so split screen and picture-in-picture are covered too.
 * Only each window's package name is read, never text. System queries are cached, and the check
 * runs on events plus a slow timer (every minute by day, every 15 seconds at night), and every
 * second only while the cover's countdown is on screen.</p>
 */
public class GuardService extends AccessibilityService {
    static volatile boolean connected = false;
    private static final long CACHE_MS = 10 * 60 * 1000L, QUIET_AFTER_HOME_MS = 1200;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::check, soon = this::check;
    private Store store; private GuardNotification notification; private WindowManager wm;
    private String current = "", coverPkg; private NightCover cover;
    private Set<String> safe = Set.of(), systemOverlays = Set.of(); private long cachedAt = -CACHE_MS, quietUntil = 0, lastNavigation = 0, coverShownAt = 0;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            String action = intent.getAction() == null ? "" : intent.getAction();
            if (action.startsWith("android.intent.action.PACKAGE_")) cachedAt = -CACHE_MS;
            check();
        }
    };

    @Override public void onServiceConnected() {
        connected = true; store = new Store(this); notification = new GuardNotification(this); wm = getSystemService(WindowManager.class);
        IntentFilter system = new IntentFilter();
        for (String a : new String[]{Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED}) system.addAction(a);
        IntentFilter packages = new IntentFilter(); packages.addDataScheme("package");
        for (String a : new String[]{Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_REPLACED}) packages.addAction(a);
        register(system); register(packages);
        store.prune(); check();
    }

    private void register(IntentFilter filter) {
        registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED); // system broadcasts; the flag is ignored before Android 13
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName() != null) {
            String pkg = e.getPackageName().toString();
            refreshCaches();
            if (!systemOverlays.contains(pkg) && !pkg.equals(current)) { current = pkg; lastNavigation = SystemClock.elapsedRealtime(); } // keyboards and the shade sit on top of apps
            check();
        } else if (e.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            handler.removeCallbacks(soon); handler.postDelayed(soon, 150); // windows change in bursts
        }
    }

    private void refreshCaches() {
        long now = SystemClock.elapsedRealtime();
        if (now - cachedAt < CACHE_MS) return;
        cachedAt = now; safe = Apps.safeSet(this);
        Set<String> overlays = new HashSet<>(Set.of("com.android.systemui"));
        for (android.view.inputmethod.InputMethodInfo input : getSystemService(android.view.inputmethod.InputMethodManager.class).getEnabledInputMethodList()) overlays.add(input.getPackageName());
        systemOverlays = overlays;
        try { store.syncBrowsers(Apps.browsers(this)); } catch (RuntimeException ignored) { }
    }

    /** Package names of every app window on screen, plus the last app reported by an event. */
    private List<String> visibleApps() {
        List<String> apps = new ArrayList<>();
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null) continue;
                CharSequence pkg = root.getPackageName();
                if (pkg != null && !apps.contains(pkg.toString())) apps.add(pkg.toString());
                if (Build.VERSION.SDK_INT < 33) root.recycle(); // Android 12 still pools node objects
            }
        } catch (RuntimeException ignored) { }
        if (!current.isEmpty() && !apps.contains(current)) apps.add(current);
        return apps;
    }

    private boolean restricted(String pkg, Set<String> selected) {
        return !safe.contains(pkg) && !systemOverlays.contains(pkg) && selected.contains(pkg) && !store.allowed(pkg);
    }

    private void check() {
        if (store == null) return;
        handler.removeCallbacks(tick);
        refreshCaches();
        ZonedDateTime now = ZonedDateTime.now(); Policy.Schedule schedule = store.schedule();
        boolean night = Policy.night(now.toLocalTime(), schedule);
        if (night) store.markGuarded();
        String target = null; int windows = 0;
        if (night) {
            Set<String> selected = store.selected(); List<String> visible = visibleApps(); windows = visible.size();
            // The cover itself can hide the app's window from the list, so keep it until the user goes somewhere else.
            if (cover != null && restricted(coverPkg, selected) && (visible.contains(coverPkg) || lastNavigation <= coverShownAt)) target = coverPkg;
            for (String pkg : visible) if (target == null && restricted(pkg, selected)) target = pkg;
        }
        if (target != null && SystemClock.elapsedRealtime() < quietUntil && !target.equals(coverPkg)) target = null;
        if (target == null) hide();
        else if (cover == null || !target.equals(coverPkg)) { hide(); show(target, schedule, windows > 1); }
        else cover.tick(now);
        if (notification != null) notification.refresh(store, schedule, now);
        handler.postDelayed(tick, nextDelay(night, now, schedule));
    }

    private long nextDelay(boolean night, ZonedDateTime now, Policy.Schedule schedule) {
        long delay = cover != null ? 1000 : night ? 15000 : 60000;
        long untilBoundary = java.time.Duration.between(now, Policy.nextBoundary(now, schedule)).toMillis() + 300;
        delay = Math.min(delay, Math.max(300, untilBoundary));
        long grant = store.grantRemainingMs();
        if (grant > 0) delay = Math.min(delay, grant + 300);
        return delay;
    }

    private void show(String pkg, Policy.Schedule schedule, boolean split) {
        store.recordBlock();
        NightCover next = NightCover.build(this, pkg, AppCatalog.icon(this, pkg), Apps.label(this, pkg), schedule, store.blocksTonight(),
                store.waitForNextRequest() / 60000, split,
                () -> { goHome(); },
                () -> { goHome(); handler.postDelayed(() -> startActivity(new Intent(this, MainActivity.class).putExtra("target", pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)), 350); });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.OPAQUE);
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        try { wm.addView(next.root, lp); cover = next; coverPkg = pkg; coverShownAt = SystemClock.elapsedRealtime(); }
        catch (RuntimeException e) { performGlobalAction(GLOBAL_ACTION_HOME); }
    }

    private void goHome() { current = ""; quietUntil = SystemClock.elapsedRealtime() + QUIET_AFTER_HOME_MS; hide(); performGlobalAction(GLOBAL_ACTION_HOME); }

    private void hide() {
        if (cover != null) { try { wm.removeView(cover.root); } catch (RuntimeException ignored) { } cover = null; coverPkg = null; }
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        connected = false; handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(receiver); } catch (RuntimeException ignored) { }
        if (notification != null) notification.cancel();
        hide(); super.onDestroy();
    }
}
