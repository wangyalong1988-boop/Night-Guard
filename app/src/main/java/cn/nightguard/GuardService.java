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
 * Watches which apps are on screen and covers them when they should rest: restricted apps during
 * the night or a focus session, and any app that used up its daily limit.
 *
 * <p>It looks at every visible app window, so split screen and picture-in-picture are covered too.
 * Only each window's package name is read, never text. It also measures how long each app is in
 * the foreground while the phone is unlocked. System queries are cached, and the check runs on
 * events plus a slow timer, and every second only while a cover's countdown is on screen.</p>
 */
public class GuardService extends AccessibilityService {
    static volatile boolean connected = false;
    private static final long CACHE_MS = 10 * 60 * 1000L, QUIET_AFTER_HOME_MS = 1200, WIDGET_MS = 60000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::check, soon = this::check;
    private Store store; private GuardNotification notification; private WindowManager wm;
    private String current = "", coverPkg, coverReason, appPkg; private NightCover cover;
    private Set<String> safe = Set.of(), systemOverlays = Set.of();
    private long cachedAt = -CACHE_MS, quietUntil = 0, lastNavigation = 0, coverShownAt = 0, appSince = 0, widgetAt = -WIDGET_MS;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            String action = intent.getAction() == null ? "" : intent.getAction(); long now = System.currentTimeMillis();
            if (action.startsWith("android.intent.action.PACKAGE_")) cachedAt = -CACHE_MS;
            if (Intent.ACTION_USER_PRESENT.equals(action)) { store.screenUse(true, now); trackApp(now); }
            if (Intent.ACTION_SCREEN_OFF.equals(action)) { store.screenUse(false, now); trackApp(now); TodayWidget.update(GuardService.this, store); return; }
            check();
        }
    };

    @Override public void onServiceConnected() {
        connected = true; store = new Store(this); notification = new GuardNotification(this); wm = getSystemService(WindowManager.class);
        IntentFilter system = new IntentFilter();
        for (String a : new String[]{Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED}) system.addAction(a);
        IntentFilter packages = new IntentFilter(); packages.addDataScheme("package");
        for (String a : new String[]{Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_REPLACED}) packages.addAction(a);
        registerReceiver(receiver, system, Context.RECEIVER_EXPORTED); registerReceiver(receiver, packages, Context.RECEIVER_EXPORTED); // system broadcasts
        // Start recording use from now: an unlocked, lit screen counts as "in use".
        android.app.KeyguardManager keyguard = getSystemService(android.app.KeyguardManager.class);
        boolean using = getSystemService(PowerManager.class).isInteractive() && (keyguard == null || !keyguard.isKeyguardLocked());
        store.p.edit().remove("useAt").apply(); // a session left open by a killed service cannot be trusted
        store.screenUse(using, System.currentTimeMillis());
        store.prune(); check();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName() != null) {
            String pkg = e.getPackageName().toString();
            refreshCaches();
            if (!systemOverlays.contains(pkg) && !pkg.equals(current)) { // keyboards and the shade sit on top of apps
                current = pkg; lastNavigation = SystemClock.elapsedRealtime(); trackApp(System.currentTimeMillis());
            }
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

    /**
     * Credits the time since the last call to the app that was in front, then starts timing the
     * current one. Time behind a cover, on the launcher or in system screens is not counted.
     */
    private void trackApp(long now) {
        if (appPkg != null && !appPkg.equals(coverPkg)) store.addAppTime(appPkg, appSince, now);
        appPkg = store.inUse() && !current.isEmpty() && !safe.contains(current) && !systemOverlays.contains(current) ? current : null;
        appSince = now;
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

    private String reason(String pkg, Set<String> selected) {
        if (safe.contains(pkg) || systemOverlays.contains(pkg) || store.allowed(pkg)) return null;
        return store.blockReason(pkg, selected);
    }

    private void check() {
        if (store == null) return;
        handler.removeCallbacks(tick);
        refreshCaches();
        long nowMs = System.currentTimeMillis(); trackApp(nowMs);
        ZonedDateTime now = ZonedDateTime.now(); Policy.Schedule schedule = store.schedule();
        boolean night = Policy.night(now.toLocalTime(), schedule);
        if (night) store.markGuarded(); else store.finalizeNight();
        String target = null, why = null; int windows = 0;
        if (night || store.focusing() || !store.limits().isEmpty()) {
            Set<String> selected = store.selected(); List<String> visible = visibleApps(); windows = visible.size();
            // The cover itself can hide the app's window from the list, so keep it until the user goes somewhere else.
            if (cover != null && (visible.contains(coverPkg) || lastNavigation <= coverShownAt)) { why = reason(coverPkg, selected); if (why != null) target = coverPkg; }
            for (String pkg : visible) if (target == null) { why = reason(pkg, selected); if (why != null) target = pkg; }
        }
        if (target != null && SystemClock.elapsedRealtime() < quietUntil && !target.equals(coverPkg)) target = null;
        if (target == null) hide();
        else if (cover == null || !target.equals(coverPkg) || !why.equals(coverReason)) { hide(); show(target, why, schedule, windows > 1); }
        else cover.tick(now);
        if (notification != null) notification.refresh(store, schedule, now);
        if (SystemClock.elapsedRealtime() - widgetAt >= WIDGET_MS) { widgetAt = SystemClock.elapsedRealtime(); TodayWidget.update(this, store); }
        handler.postDelayed(tick, nextDelay(night, now, schedule));
    }

    private long nextDelay(boolean night, ZonedDateTime now, Policy.Schedule schedule) {
        long delay = cover != null ? 1000 : night ? 15000 : 60000;
        delay = Math.min(delay, Math.max(300, java.time.Duration.between(now, Policy.nextBoundary(now, schedule)).toMillis() + 300));
        long grant = store.grantRemainingMs(); if (grant > 0) delay = Math.min(delay, grant + 300);
        long focus = store.focusRemainingMs(); if (focus > 0) delay = Math.min(delay, Math.max(300, focus + 300));
        if (appPkg != null) { // wake up right when the app in front reaches its limit
            Integer limit = store.limits().get(appPkg);
            if (limit != null) { Long used = store.appTimes(java.time.LocalDate.now()).get(appPkg); long left = limit * 60000L - (used == null ? 0 : used); if (left > 0) delay = Math.min(delay, left + 500); }
        }
        return delay;
    }

    private void show(String pkg, String why, Policy.Schedule schedule, boolean split) {
        store.recordBlock();
        NightCover.Mode mode = "focus".equals(why) ? NightCover.Mode.FOCUS : "limit".equals(why) ? NightCover.Mode.LIMIT : NightCover.Mode.NIGHT;
        ZonedDateTime now = ZonedDateTime.now();
        long endsAt = mode == NightCover.Mode.NIGHT ? Policy.nightEnd(now, schedule).toInstant().toEpochMilli()
                : mode == NightCover.Mode.FOCUS ? System.currentTimeMillis() + store.focusRemainingMs()
                : now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()).toInstant().toEpochMilli();
        Integer limit = store.limits().get(pkg);
        NightCover next = NightCover.build(this, mode, AppCatalog.icon(this, pkg), Apps.label(this, pkg), endsAt,
                mode == NightCover.Mode.NIGHT ? schedule.endText() : null, limit == null ? 0 : limit, store.blocksToday(mode == NightCover.Mode.NIGHT),
                store.waitForNextRequest() / 60000, split,
                this::goHome,
                () -> { goHome(); handler.postDelayed(() -> startActivity(new Intent(this, MainActivity.class).putExtra("target", pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)), 350); });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.OPAQUE);
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        try { wm.addView(next.root, lp); cover = next; coverPkg = pkg; coverReason = why; coverShownAt = SystemClock.elapsedRealtime(); }
        catch (RuntimeException e) { performGlobalAction(GLOBAL_ACTION_HOME); }
    }

    private void goHome() { current = ""; quietUntil = SystemClock.elapsedRealtime() + QUIET_AFTER_HOME_MS; hide(); trackApp(System.currentTimeMillis()); performGlobalAction(GLOBAL_ACTION_HOME); }

    private void hide() {
        if (cover != null) { try { wm.removeView(cover.root); } catch (RuntimeException ignored) { } cover = null; coverPkg = null; coverReason = null; }
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        connected = false; handler.removeCallbacksAndMessages(null);
        if (store != null) trackApp(System.currentTimeMillis());
        try { unregisterReceiver(receiver); } catch (RuntimeException ignored) { }
        if (notification != null) notification.cancel();
        hide(); super.onDestroy();
    }
}
