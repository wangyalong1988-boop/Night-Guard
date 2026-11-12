package cn.nightguard;
import java.time.LocalTime;
public final class Policy {
    public static final long WAIT_MS = 10 * 60 * 1000L;
    public static final long GRANT_MS = 5 * 60 * 1000L;
    public static boolean night(LocalTime time) { return !time.isBefore(LocalTime.of(1,0)) && time.isBefore(LocalTime.of(8,0)); }
    public static java.time.ZonedDateTime nextBoundary(java.time.ZonedDateTime now) {
        java.time.LocalDate day = now.toLocalDate();
        if (night(now.toLocalTime())) return day.atTime(8, 0).atZone(now.getZone());
        if (!now.toLocalTime().isBefore(LocalTime.of(1, 0))) day = day.plusDays(1);
        return day.atTime(1, 0).atZone(now.getZone());
    }
    public static boolean ready(long started, long now) { return started >= 0 && now >= started && now - started >= WAIT_MS; }
    public static boolean granted(String target, String active, long expiry, long now) { return target != null && target.equals(active) && now < expiry; }
}
