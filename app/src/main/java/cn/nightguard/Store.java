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

    /** Night unlocks count per night; daytime extensions (limits, focus) count per day. */
    long waitForNextRequest() { return Policy.waitMs(night() ? unlocksTonight() : p.getInt("d:" + LocalDate.now() + ":x", 0)); }

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
        String counter = night() ? "n:" + nightKey() + ":u" : "d:" + LocalDate.now() + ":x";
        p.edit().putString("grant", pkg).putLong("until", SystemClock.elapsedRealtime() + Policy.GRANT_MS)
                .putString("history", String.join("\n", history)).putInt(counter, p.getInt(counter, 0) + 1)
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

    /** Counts a stop: at night towards tonight's record, by day towards today's. */
    void recordBlock() {
        if (night()) { String k = nightKey(); p.edit().putInt("n:" + k + ":b", p.getInt("n:" + k + ":b", 0) + 1).putBoolean("n:" + k + ":g", true).apply(); }
        else { String k = "d:" + LocalDate.now() + ":b"; p.edit().putInt(k, p.getInt(k, 0) + 1).apply(); }
    }

    int blocksToday(boolean nightCount) { return nightCount ? blocksTonight() : p.getInt("d:" + LocalDate.now() + ":b", 0); }

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

    // ---- Phone use: from unlocking the screen to turning it off ---------------------------

    /** @param using true when the screen is unlocked, false when it turns off */
    void screenUse(boolean using, long now) {
        if (!p.contains("screenSince")) p.edit().putLong("screenSince", now).apply();
        if (using) { if (!p.contains("useAt")) p.edit().putLong("useAt", now).apply(); return; }
        if (!p.contains("useAt")) return;
        long start = p.getLong("useAt", now);
        List<String> log = new ArrayList<>();
        for (String s : p.getString("useLog", "").split(";")) if (!s.isEmpty()) try {
            if (Long.parseLong(s.substring(s.indexOf('-') + 1)) > now - 3 * 86400000L) log.add(s);
        } catch (RuntimeException ignored) { }
        if (now > start) log.add(start + "-" + now);
        SharedPreferences.Editor e = p.edit().putString("useLog", String.join(";", log)).remove("useAt");
        if (now > start) addUse(e, start, now);
        e.apply();
    }

    /** Adds a finished session to the per-day totals, split at midnight; the pickup counts on the day it began. */
    private void addUse(SharedPreferences.Editor e, long start, long end) {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        for (long[] piece : Policy.splitByDay(start, end, zone)) {
            String k = "d:" + LocalDate.ofEpochDay(piece[0]) + ":u";
            e.putLong(k, p.getLong(k, 0) + piece[1]);
        }
        String pk = "d:" + java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate() + ":p";
        e.putInt(pk, p.getInt(pk, 0) + 1);
    }

    /** Milliseconds since the current unlock, or 0 when the phone is not in use. */
    long currentSessionMs(long now) { return p.contains("useAt") ? Math.max(0, now - p.getLong("useAt", now)) : 0; }

    long currentSessionStart() { return p.getLong("useAt", -1); }

    // ---- Daily screen time ------------------------------------------------------------------

    /** One calendar day of use. {@code recorded} is false for days before recording started. */
    static final class Day {
        final LocalDate date; final long useMs; final int pickups; final boolean recorded;
        Day(LocalDate date, long useMs, int pickups, boolean recorded) { this.date = date; this.useMs = useMs; this.pickups = pickups; this.recorded = recorded; }
    }

    LocalDate recordingSince() {
        return p.contains("screenSince") ? java.time.Instant.ofEpochMilli(p.getLong("screenSince", 0)).atZone(java.time.ZoneId.systemDefault()).toLocalDate() : null;
    }

    /** The last {@code count} days, oldest first, today last; today includes the session going on now. */
    List<Day> days(int count) {
        long now = System.currentTimeMillis(); LocalDate today = LocalDate.now(), since = recordingSince();
        List<Day> r = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i); long use = p.getLong("d:" + d + ":u", 0); int pick = p.getInt("d:" + d + ":p", 0);
            if (i == 0 && p.contains("useAt")) {
                long start = p.getLong("useAt", now);
                for (long[] piece : Policy.splitByDay(start, now, java.time.ZoneId.systemDefault())) if (piece[0] == d.toEpochDay()) use += piece[1];
                if (!java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate().isBefore(d)) pick++;
            }
            r.add(new Day(d, use, pick, since != null && !d.isBefore(since)));
        }
        return r;
    }

    Day today() { List<Day> d = days(1); return d.get(0); }

    // ---- Per-app time -----------------------------------------------------------------------

    boolean inUse() { return p.contains("useAt"); }

    Map<String, Long> appTimes(LocalDate day) { return Policy.parseTimes(p.getString("a:" + day, "")); }

    /** Adds foreground time for one app, split at midnight. Stored as one "a:<date>" entry per day. */
    void addAppTime(String pkg, long from, long to) {
        if (pkg == null || pkg.isEmpty() || to <= from || to - from > 6 * 3600000L) return;
        SharedPreferences.Editor e = p.edit();
        for (long[] piece : Policy.splitByDay(from, to, java.time.ZoneId.systemDefault())) {
            String k = "a:" + LocalDate.ofEpochDay(piece[0]);
            Map<String, Long> m = Policy.parseTimes(p.getString(k, "")); m.merge(pkg, piece[1], Long::sum);
            e.putString(k, Policy.formatTimes(m));
        }
        e.apply();
    }

    /** Total foreground time per app over the last {@code days} days including today. */
    Map<String, Long> appTotals(int days) {
        Map<String, Long> r = new HashMap<>(); LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) for (Map.Entry<String, Long> e : appTimes(today.minusDays(i)).entrySet()) r.merge(e.getKey(), e.getValue(), Long::sum);
        return r;
    }

    // ---- Daily limits per app ---------------------------------------------------------------

    /** Limits in force today, in minutes. A looser change waits until tomorrow. */
    Map<String, Integer> limits() {
        if (p.contains("limitsPending") && !LocalDate.now().isBefore(LocalDate.parse(p.getString("limitsPendingDay", "2000-01-01"))))
            p.edit().putString("limits", p.getString("limitsPending", "")).remove("limitsPending").remove("limitsPendingDay").commit();
        return Policy.parseLimits(p.getString("limits", ""));
    }

    Map<String, Integer> desiredLimits() { limits(); return Policy.parseLimits(p.getString(p.contains("limitsPending") ? "limitsPending" : "limits", "")); }

    /** @param minutes 0 removes the limit. @return true when the change applies now. */
    boolean setLimit(String pkg, int minutes) {
        Map<String, Integer> next = desiredLimits();
        if (minutes > 0) next.put(pkg, minutes); else next.remove(pkg);
        Map<String, Integer> now = Policy.immediateLimits(limits(), next);
        SharedPreferences.Editor e = p.edit().putString("limits", Policy.formatTimes(now));
        boolean immediate = now.equals(next);
        if (immediate) e.remove("limitsPending").remove("limitsPendingDay");
        else e.putString("limitsPending", Policy.formatTimes(next)).putString("limitsPendingDay", LocalDate.now().plusDays(1).toString());
        e.commit();
        return immediate;
    }

    boolean limitReached(String pkg) { Long used = appTimes(LocalDate.now()).get(pkg); return Policy.limitReached(used == null ? 0 : used, limits().get(pkg)); }

    // ---- Focus ------------------------------------------------------------------------------

    /** Blocks the restricted list for a while, from now. It cannot be ended early. */
    void startFocus(int minutes) {
        long until = Math.max(p.getLong("focusUntil", 0), System.currentTimeMillis()) + minutes * 60000L;
        String k = "d:" + LocalDate.now() + ":f";
        p.edit().putLong("focusUntil", until).putInt(k, p.getInt(k, 0) + minutes).commit();
    }

    boolean focusing() { return System.currentTimeMillis() < p.getLong("focusUntil", 0); }

    long focusRemainingMs() { return Math.max(0, p.getLong("focusUntil", 0) - System.currentTimeMillis()); }

    int focusMinutesToday() { return p.getInt("d:" + LocalDate.now() + ":f", 0); }

    /** Why this app is stopped right now, ignoring any temporary unlock: night, focus, limit, or null. */
    String blockReason(String pkg, Set<String> selected) {
        if (selected.contains(pkg) && night()) return "night";
        if (selected.contains(pkg) && focusing()) return "focus";
        if (limitReached(pkg)) return "limit";
        return null;
    }

    // ---- Goal -------------------------------------------------------------------------------

    boolean hasGoal() { return p.contains("goalTarget"); }

    /** Today's goal in minutes, or -1 when none is set. */
    int goalMinutes() { return goalMinutes(LocalDate.now()); }

    int goalMinutes(LocalDate day) {
        if (!hasGoal()) return -1;
        LocalDate start = LocalDate.parse(p.getString("goalStart", day.toString()));
        if (day.isBefore(start)) return -1;
        return Policy.goalFor(p.getInt("goalBase", 0), p.getInt("goalTarget", 0), p.getInt("goalStep", 0), java.time.temporal.ChronoUnit.DAYS.between(start, day));
    }

    int goalTarget() { return p.getInt("goalTarget", -1); }

    /** Sets a goal, starting from the recent average (rounded up) when that is higher than the target. */
    int setGoal(int targetMinutes, int stepMinutes) {
        double avg = recentAverageMs(7);
        int base = Double.isNaN(avg) ? targetMinutes : Math.max(targetMinutes, Policy.roundUpTen(Math.round(avg / 60000)));
        p.edit().putInt("goalTarget", targetMinutes).putInt("goalStep", stepMinutes).putInt("goalBase", base).putString("goalStart", LocalDate.now().toString()).apply();
        return base;
    }

    void clearGoal() { p.edit().remove("goalTarget").remove("goalStep").remove("goalBase").remove("goalStart").apply(); }

    /** Average over recorded days among the last {@code count} days before today. Needs at least 3 such days. */
    double recentAverageMs(int count) {
        List<Day> all = days(count + 1); long sum = 0; int n = 0;
        for (int i = 0; i < count; i++) { Day d = all.get(i); if (d.recorded) { sum += d.useMs; n++; } }
        return n < 3 ? Double.NaN : (double) sum / n;
    }

    // ---- Nightly results kept for history ---------------------------------------------------

    /** Saves the result of the last finished night once, so its chart survives after the raw log is trimmed. */
    void finalizeNight() {
        if (night()) return;
        LocalDate key = Policy.nightKey(ZonedDateTime.now(), schedule());
        String k = "n:" + key;
        if (p.contains(k + ":rest")) return;
        NightUse use = rest(key);
        if (use == null || use.ongoing()) return;
        p.edit().putLong(k + ":rest", use.rest.restMs()).putLong(k + ":win", use.rest.windowMs).putInt(k + ":pick", use.rest.pickups).putLong(k + ":long", use.rest.longestRestMs).apply();
    }

    /** Hours put down for each of the last {@code count} finished nights, oldest first; NaN where unknown. */
    double[] restHistory(int count, LocalDate[] dates) {
        finalizeNight();
        List<Night> nights = recentNights(count); double[] r = new double[count];
        for (int i = 0; i < count; i++) {
            Night n = nights.get(i); if (dates != null) dates[i] = n.date;
            String k = "n:" + n.date + ":rest";
            r[i] = p.contains(k) ? p.getLong(k, 0) : Double.NaN;
        }
        return r;
    }

    /** Recorded use sessions, sorted, including one still going on. */
    List<long[]> useSessions(long now) {
        List<long[]> r = new ArrayList<>();
        for (String s : p.getString("useLog", "").split(";")) if (!s.isEmpty()) try {
            int dash = s.indexOf('-'); r.add(new long[]{Long.parseLong(s.substring(0, dash)), Long.parseLong(s.substring(dash + 1))});
        } catch (RuntimeException ignored) { }
        if (p.contains("useAt")) r.add(new long[]{p.getLong("useAt", now), now});
        r.sort((a, b) -> Long.compare(a[0], b[0]));
        return r;
    }

    /**
     * How the phone was used during the night that began on {@code key}, up to now if it is still
     * going on. Returns null when use was not being recorded at that time.
     */
    NightUse rest(LocalDate key) {
        if (!p.contains("screenSince")) return null;
        Policy.Schedule s = schedule(); java.time.ZoneId zone = java.time.ZoneId.systemDefault(); long now = System.currentTimeMillis();
        long start = key.atTime(s.startTime()).atZone(zone).toInstant().toEpochMilli();
        long end = (s.start < s.end ? key : key.plusDays(1)).atTime(s.endTime()).atZone(zone).toInstant().toEpochMilli();
        long from = Math.max(start, p.getLong("screenSince", now)), to = Math.min(end, now);
        if (to - from < 10 * 60000L) return null;
        List<long[]> sessions = useSessions(now);
        return new NightUse(Policy.rest(sessions, from, to), start, end, from, to, sessions);
    }

    /** One night's phone use, with the raw sessions for drawing a timeline. */
    static final class NightUse {
        final Policy.Rest rest; final long start, end, from, to; final List<long[]> sessions;
        NightUse(Policy.Rest rest, long start, long end, long from, long to, List<long[]> sessions) { this.rest = rest; this.start = start; this.end = end; this.from = from; this.to = to; this.sessions = sessions; }
        boolean ongoing() { return to < end; }
    }

    boolean once(String key) { if (p.getBoolean(key, false)) return false; p.edit().putBoolean(key, true).apply(); return true; }

    /** Drops night, day and reminder records older than 120 days. */
    void prune() {
        LocalDate cutoff = LocalDate.now().minusDays(120); SharedPreferences.Editor e = null;
        for (String key : new ArrayList<>(p.getAll().keySet())) {
            boolean app = key.startsWith("a:");
            String date = (key.startsWith("n:") || key.startsWith("d:") || app) && key.length() >= 12 ? key.substring(2, 12) : key.startsWith("tip:") && key.length() > 14 ? key.substring(key.length() - 10) : null;
            if (date != null && parse(date, LocalDate.now()).isBefore(app ? LocalDate.now().minusDays(40) : cutoff)) { if (e == null) e = p.edit(); e.remove(key); }
        }
        if (e != null) e.apply();
    }

    private static LocalDate parse(String value, LocalDate fallback) { try { return LocalDate.parse(value); } catch (RuntimeException e) { return fallback; } }
}
