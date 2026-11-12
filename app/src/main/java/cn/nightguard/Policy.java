package cn.nightguard;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Pure time and unlock rules. Everything here is unit tested without Android. */
public final class Policy {
    public static final long GRANT_MS = 5 * 60 * 1000L;
    /** First unlock request of a night waits 10 minutes; each further request waits 5 minutes more, up to 30. */
    public static final long WAIT_MS = 10 * 60 * 1000L;
    public static final long WAIT_STEP_MS = 5 * 60 * 1000L;
    public static final long WAIT_MAX_MS = 30 * 60 * 1000L;
    public static final int WIND_DOWN_MINUTES = 15;
    public static final Schedule DEFAULT = new Schedule(60, 480);

    private Policy() {}

    /** A daily restricted period in minutes after midnight. It may cross midnight, e.g. 23:30–07:00. */
    public static final class Schedule {
        public final int start, end;
        public Schedule(int start, int end) {
            if (start < 0 || start >= 1440 || end < 0 || end >= 1440 || start == end)
                throw new IllegalArgumentException("Invalid schedule " + start + "-" + end);
            this.start = start; this.end = end;
        }
        public static boolean valid(int start, int end) { return start >= 0 && start < 1440 && end >= 0 && end < 1440 && start != end; }
        public LocalTime startTime() { return LocalTime.of(start / 60, start % 60); }
        public LocalTime endTime() { return LocalTime.of(end / 60, end % 60); }
        public int minutes() { return Math.floorMod(end - start, 1440); }
        public String startText() { return clock(start); }
        public String endText() { return clock(end); }
        public String text() { return clock(start) + "–" + clock(end); }
        @Override public boolean equals(Object o) { return o instanceof Schedule && ((Schedule) o).start == start && ((Schedule) o).end == end; }
        @Override public int hashCode() { return start * 1440 + end; }
        @Override public String toString() { return text(); }
    }

    public static String clock(int minutes) { return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60); }

    public static boolean night(LocalTime time) { return night(time, DEFAULT); }

    public static boolean night(LocalTime time, Schedule s) {
        LocalTime start = s.startTime(), end = s.endTime();
        if (s.start < s.end) return !time.isBefore(start) && time.isBefore(end);
        return !time.isBefore(start) || time.isBefore(end);
    }

    public static ZonedDateTime nextBoundary(ZonedDateTime now) { return nextBoundary(now, DEFAULT); }

    /** End of the current restricted period, or the start of the next one. */
    public static ZonedDateTime nextBoundary(ZonedDateTime now, Schedule s) {
        return night(now.toLocalTime(), s) ? nightEnd(now, s) : nextStart(now, s);
    }

    public static ZonedDateTime nextStart(ZonedDateTime now, Schedule s) {
        ZonedDateTime today = now.toLocalDate().atTime(s.startTime()).atZone(now.getZone());
        return now.isBefore(today) ? today : now.toLocalDate().plusDays(1).atTime(s.startTime()).atZone(now.getZone());
    }

    /** When the night that most recently started (the current one, if any) ends. */
    public static ZonedDateTime nightEnd(ZonedDateTime now, Schedule s) {
        LocalDate key = nightKey(now, s);
        LocalDate endDay = s.start < s.end ? key : key.plusDays(1);
        return endDay.atTime(s.endTime()).atZone(now.getZone());
    }

    /** The calendar date on which the current or most recent restricted period began. */
    public static LocalDate nightKey(ZonedDateTime now, Schedule s) {
        LocalDate day = now.toLocalDate();
        return now.toLocalTime().isBefore(s.startTime()) ? day.minusDays(1) : day;
    }

    /** Fraction of the current night already passed, 0..1, or -1 outside the restricted period. */
    public static double progress(ZonedDateTime now, Schedule s) {
        if (!night(now.toLocalTime(), s)) return -1;
        ZonedDateTime end = nightEnd(now, s);
        ZonedDateTime start = nightKey(now, s).atTime(s.startTime()).atZone(now.getZone());
        double total = Duration.between(start, end).toMillis(), passed = Duration.between(start, now).toMillis();
        return total <= 0 ? 0 : Math.max(0, Math.min(1, passed / total));
    }

    /** Minutes until the next restricted period starts, or -1 during it. */
    public static long minutesUntilStart(ZonedDateTime now, Schedule s) {
        if (night(now.toLocalTime(), s)) return -1;
        return (long) Math.ceil(Duration.between(now, nextStart(now, s)).getSeconds() / 60.0);
    }

    /** Cooling-off time for the next request, given how many unlocks already succeeded tonight. */
    public static long waitMs(int unlocksTonight) {
        return Math.min(WAIT_MAX_MS, WAIT_MS + WAIT_STEP_MS * Math.max(0, unlocksTonight));
    }

    public static boolean ready(long started, long now) { return ready(started, now, WAIT_MS); }

    public static boolean ready(long started, long now, long waitMs) { return started >= 0 && now >= started && now - started >= waitMs; }

    public static boolean granted(String target, String active, long expiry, long now) { return target != null && target.equals(active) && now < expiry; }

    /** A reason needs at least 10 characters and 6 different ones, so "啊啊啊啊啊啊啊啊啊啊" is refused. */
    public static String reasonProblem(String reason) {
        String value = reason == null ? "" : reason.trim();
        if (value.codePointCount(0, value.length()) < 10) return "请写至少10个字的具体原因";
        Set<Integer> distinct = new HashSet<>();
        value.codePoints().filter(c -> !Character.isWhitespace(c) && Character.isLetterOrDigit(c)).forEach(distinct::add);
        if (distinct.size() < 6) return "原因看起来是重复的字，请写清楚具体要做什么";
        return null;
    }

    static final String[] PHRASES = {
        "我知道现在是休息时间，只用五分钟",
        "用完这五分钟我就放下手机睡觉",
        "明天的我会感谢现在早睡的自己",
        "这次是真的需要，五分钟后自觉停止",
        "我选择暂时使用并在五分钟后停止",
    };

    /** The phrase to type, chosen by the caller's random number so it can be tested. */
    public static String phrase(int random) {
        int r = Math.floorMod(random, 1000000);
        return PHRASES[r % PHRASES.length] + " " + String.format(Locale.ROOT, "%04d", r % 10000);
    }
}
