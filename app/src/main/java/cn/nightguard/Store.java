package cn.nightguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Local, private state. Nothing here leaves the phone. */
final class Store {
    static final int HISTORY_LIMIT = 30;
    final Context c;
    final SharedPreferences p;

    Store(Context c) { this.c = c; p = c.getSharedPreferences("guard", 0); resetOnBoot(); firstNight(); }

    void resetOnBoot() {
        int boot = Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (p.getInt("boot", -2) != boot)
            p.edit().putInt("boot", boot).remove("grant").remove("until").remove("request").remove("start").remove("phrase").remove("wait").commit();
    }

    // ---- Schedule -------------------------------------------------------------------------

    Policy.Schedule schedule() {
        if (p.contains("pendingSched") && System.currentTimeMillis() >= p.getLong("pendingSchedAt", Long.MAX_VALUE)) {
            String[] parts = p.getString("pendingSched", "").split(",");
            SharedPreferences.Editor e = p.edit().remove("pendingSched").remove("pendingSchedAt");
            try {
                int s = Integer.parseInt(parts[0]), t = Integer.parseInt(parts[1]);
                if (Policy.Schedule.valid(s, t)) e.putInt("sched", s).putInt("schedEnd", t);
            } catch (RuntimeException ignored) { }
            e.commit();
        }
        int s = p.getInt("sched", Policy.DEFAULT.start), t = p.getInt("schedEnd", Policy.DEFAULT.end);
        return Policy.Schedule.valid(s, t) ? new Policy.Schedule(s, t) : Policy.DEFAULT;
    }

    /** The schedule the user chose, including a change that waits for the morning. */
    Policy.Schedule desiredSchedule() {
        Policy.Schedule now = schedule();
        if (!p.contains("pendingSched")) return now;
        try {
            String[] parts = p.getString("pendingSched", "").split(",");
            return new Policy.Schedule(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (RuntimeException e) { return now; }
    }

    /** @return true when the change applies now; false when it waits for the end of tonight. */
    boolean saveSchedule(Policy.Schedule next) {
        if (night()) {
            if (next.equals(schedule())) { p.edit().remove("pendingSched").remove("pendingSchedAt").commit(); return true; }
            p.edit().putString("pendingSched", next.start + "," + next.end).putLong("pendingSchedAt", nightEndMillis()).commit();
            return false;
        }
        p.edit().putInt("sched", next.start).putInt("schedEnd", next.end).remove("pendingSched").remove("pendingSchedAt").commit();
        return true;
    }

    boolean night() { return Policy.night(java.time.LocalTime.now(), schedule()); }

    long nightEndMillis() { return Policy.nightEnd(ZonedDateTime.now(), schedule()).toInstant().toEpochMilli(); }

    // ---- Restricted apps --------------------------------------------------------------------

    Set<String> selected() {
        if (p.contains("pending") && System.currentTimeMillis() >= p.getLong("pendingAt", Long.MAX_VALUE)) {
            Set<String> pending = new HashSet<>(p.getStringSet("pending", Set.of()));
            p.edit().putStringSet("apps", pending).remove("pending").remove("pendingAt").commit();
        }
        return new HashSet<>(p.getStringSet("apps", Set.of()));
    }

    Set<String> desired() { selected(); return new HashSet<>(p.getStringSet(p.contains("pending") ? "pending" : "apps", Set.of())); }

    /** Adding restrictions applies at once; removing them during the night waits for the morning. */
    void save(Set<String> apps) {
        if (night()) {
            Set<String> immediate = selected(); immediate.addAll(apps);
            p.edit().putStringSet("apps", immediate).putStringSet("pending", new HashSet<>(apps)).putLong("pendingAt", nightEndMillis()).commit();
        } else p.edit().putStringSet("apps", new HashSet<>(apps)).remove("pending").remove("pendingAt").commit();
    }

    /**
     * Browsers are suggested once: a newly found browser is added to the list, and after that the
     * user's choice is respected, so a wrongly detected app can simply be unticked.
     * @return the browsers that were newly added
     */
    Set<String> syncBrowsers(Set<String> detected) {
        Set<String> seen = new HashSet<>(p.getStringSet("browserSeen", Set.of()));
        Set<String> fresh = new HashSet<>(detected); fresh.removeAll(seen);
        if (fresh.isEmpty()) return fresh;
        // An app the user already marked as "not a browser" is remembered but never auto-added.
        Set<String> add = new HashSet<>(fresh);
        add.removeIf(pkg -> { String own = p.getString("category:" + pkg, null); return own != null && !AppCategory.BROWSER.equals(own); });
        seen.addAll(fresh);
        Set<String> apps = selected(); apps.addAll(add);
        SharedPreferences.Editor e = p.edit().putStringSet("browserSeen", seen).putStringSet("apps", apps);
        if (p.contains("pending")) { Set<String> pending = new HashSet<>(p.getStringSet("pending", Set.of())); pending.addAll(add); e.putStringSet("pending", pending); }
        e.commit();
        return add;
    }

    // ---- Temporary unlock -------------------------------------------------------------------

    boolean allowed(String pkg) { return Policy.granted(pkg, p.getString("grant", null), p.getLong("until", 0), SystemClock.elapsedRealtime()); }

    long grantRemainingMs() { return Math.max(0, p.getLong("until", 0) - SystemClock.elapsedRealtime()); }

    long waitForNextRequest() { return Policy.waitMs(unlocksTonight()); }

    void begin(String pkg, String reason) {
        p.edit().putString("request", pkg).putString("reason", reason.trim()).putLong("start", SystemClock.elapsedRealtime())
                .putLong("wait", waitForNextRequest()).putString("phrase", Policy.phrase(new SecureRandom().nextInt(1000000))).commit();
    }

    long requestWait() { return p.getLong("wait", Policy.WAIT_MS); }

    long requestRemainingMs() { return Math.max(0, requestWait() - (SystemClock.elapsedRealtime() - p.getLong("start", SystemClock.elapsedRealtime()))); }

    void cancelRequest() { p.edit().remove("request").remove("start").remove("phrase").remove("wait").commit(); }

    boolean unlock(String pkg, String phrase) {
        if (!pkg.equals(p.getString("request", "")) || !Policy.ready(p.getLong("start", -1), SystemClock.elapsedRealtime(), requestWait())
                || !phrase.trim().equals(p.getString("phrase", ""))) return false;
        String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) + "\t" + pkg + "\t" + p.getString("reason", "").replace('\n', ' ').replace('\t', ' ');
        List<String> history = history(); history.add(0, line);
        while (history.size() > HISTORY_LIMIT) history.remove(history.size() - 1);
        String key = nightKey();
        p.edit().putString("grant", pkg).putLong("until", SystemClock.elapsedRealtime() + Policy.GRANT_MS)
                .putString("history", String.join("\n", history)).putInt("n:" + key + ":u", p.getInt("n:" + key + ":u", 0) + 1)
                .remove("request").remove("start").remove("phrase").remove("wait").commit();
        return true;
    }

    /** Newest first; each line is "time \t package \t reason". */
    List<String> history() {
        String raw = p.getString("history", "");
        List<String> result = new ArrayList<>();
        if (!raw.isEmpty()) result.addAll(Arrays.asList(raw.split("\n")));
        return result;
    }

    // ---- Night log ----------------------------------------------------------------------------

    String nightKey() { return Policy.nightKey(ZonedDateTime.now(), schedule()).toString(); }

    /** Records start with the first night after installation; a night already over is not counted against anyone. */
    private void firstNight() {
        if (p.contains("firstNight")) return;
        LocalDate key = Policy.nightKey(ZonedDateTime.now(), schedule());
        p.edit().putString("firstNight", (night() ? key : key.plusDays(1)).toString()).apply();
    }

    int unlocksTonight() { return night() ? p.getInt("n:" + nightKey() + ":u", 0) : 0; }

    int blocksTonight() { return p.getInt("n:" + nightKey() + ":b", 0); }

    void recordBlock() { String k = nightKey(); p.edit().putInt("n:" + k + ":b", p.getInt("n:" + k + ":b", 0) + 1).putBoolean("n:" + k + ":g", true).apply(); }

    /** Marks that the guard was actually running tonight, so an unguarded night never earns a star. */
    void markGuarded() { String k = "n:" + nightKey() + ":g"; if (!p.getBoolean(k, false)) p.edit().putBoolean(k, true).apply(); }

    /** One finished night: whether it was guarded, how often apps were stopped and unlocked. */
    static final class Night {
        final LocalDate date; final boolean guarded, recorded; final int blocks, unlocks;
        Night(LocalDate date, boolean recorded, boolean guarded, int blocks, int unlocks) { this.date = date; this.recorded = recorded; this.guarded = guarded; this.blocks = blocks; this.unlocks = unlocks; }
        boolean calm() { return guarded && unlocks == 0; }
    }

    /** The most recent finished nights, newest last. Nights before installation are not recorded. */
    List<Night> recentNights(int count) {
        Policy.Schedule s = schedule(); ZonedDateTime now = ZonedDateTime.now();
        LocalDate last = Policy.nightKey(now, s);
        if (Policy.night(now.toLocalTime(), s)) last = last.minusDays(1);
        LocalDate first = parse(p.getString("firstNight", last.toString()), last);
        List<Night> nights = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            LocalDate d = last.minusDays(i); String k = "n:" + d;
            nights.add(new Night(d, !d.isBefore(first), p.getBoolean(k + ":g", false), p.getInt(k + ":b", 0), p.getInt(k + ":u", 0)));
        }
        return nights;
    }

    /** Consecutive calm nights ending with the most recent finished one. */
    int calmStreak() {
        List<Night> nights = recentNights(60); int streak = 0;
        for (int i = nights.size() - 1; i >= 0 && nights.get(i).recorded && nights.get(i).calm(); i--) streak++;
        return streak;
    }

    boolean once(String key) { if (p.getBoolean(key, false)) return false; p.edit().putBoolean(key, true).apply(); return true; }

    /** Drops night records older than 60 days. */
    void prune() {
        LocalDate cutoff = LocalDate.now().minusDays(60); SharedPreferences.Editor e = null;
        for (String key : new ArrayList<>(p.getAll().keySet())) {
            String date = key.startsWith("n:") && key.length() > 12 ? key.substring(2, 12) : key.startsWith("tip:") && key.length() > 14 ? key.substring(key.length() - 10) : null;
            if (date != null && parse(date, LocalDate.now()).isBefore(cutoff)) { if (e == null) e = p.edit(); e.remove(key); }
        }
        if (e != null) e.apply();
    }

    private static LocalDate parse(String value, LocalDate fallback) { try { return LocalDate.parse(value); } catch (RuntimeException e) { return fallback; } }
}
