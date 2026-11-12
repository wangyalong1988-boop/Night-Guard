package cn.nightguard;

import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class PolicyTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static ZonedDateTime at(int day, int h, int m) { return ZonedDateTime.of(2026, 11, day, h, m, 0, 0, SHANGHAI); }

    @Test public void scheduleBoundaries() {
        assertFalse(Policy.night(LocalTime.of(0, 59, 59))); assertTrue(Policy.night(LocalTime.of(1, 0)));
        assertTrue(Policy.night(LocalTime.of(7, 59, 59))); assertFalse(Policy.night(LocalTime.of(8, 0))); assertFalse(Policy.night(LocalTime.NOON));
    }

    @Test public void cooldownDoesNotAcceptMissingOrBackwardTime() {
        assertFalse(Policy.ready(-1, 999999)); assertFalse(Policy.ready(100, 99)); assertFalse(Policy.ready(100, 600099)); assertTrue(Policy.ready(100, 600100));
    }

    @Test public void grantsAreScopedAndExpireExactly() {
        assertTrue(Policy.granted("browser", "browser", 200, 199)); assertFalse(Policy.granted("game", "browser", 200, 199));
        assertFalse(Policy.granted("browser", "browser", 200, 200)); assertFalse(Policy.granted(null, null, 200, 100));
    }

    @Test public void countdownSwitchesAtBothBoundaries() {
        ZonedDateTime before = ZonedDateTime.of(2026, 11, 12, 0, 59, 59, 0, SHANGHAI);
        assertEquals(1, Duration.between(before, Policy.nextBoundary(before)).getSeconds());
        ZonedDateTime start = before.plusSeconds(1);
        assertEquals(7 * 3600, Duration.between(start, Policy.nextBoundary(start)).getSeconds());
        ZonedDateTime end = start.plusHours(7);
        assertEquals(17 * 3600, Duration.between(end, Policy.nextBoundary(end)).getSeconds());
    }

    @Test public void countdownUsesRealDurationAcrossDaylightSaving() {
        ZonedDateTime start = ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, ZoneId.of("America/New_York"));
        assertEquals(6 * 3600, Duration.between(start, Policy.nextBoundary(start)).getSeconds());
    }

    @Test public void scheduleCanCrossMidnight() {
        Policy.Schedule s = new Policy.Schedule(23 * 60 + 30, 7 * 60);
        assertFalse(Policy.night(LocalTime.of(23, 29), s)); assertTrue(Policy.night(LocalTime.of(23, 30), s));
        assertTrue(Policy.night(LocalTime.MIDNIGHT, s)); assertTrue(Policy.night(LocalTime.of(6, 59), s)); assertFalse(Policy.night(LocalTime.of(7, 0), s));
        assertEquals(at(13, 7, 0), Policy.nextBoundary(at(12, 23, 45), s));
        assertEquals(at(13, 7, 0), Policy.nextBoundary(at(13, 2, 0), s));
        assertEquals(at(13, 23, 30), Policy.nextBoundary(at(13, 9, 0), s));
        assertEquals(450, s.minutes());
    }

    @Test public void nightKeyIsTheDateTheNightStarted() {
        Policy.Schedule late = new Policy.Schedule(23 * 60, 7 * 60);
        assertEquals(LocalDate.of(2026, 11, 12), Policy.nightKey(at(12, 23, 10), late));
        assertEquals(LocalDate.of(2026, 11, 12), Policy.nightKey(at(13, 3, 0), late));
        assertEquals(LocalDate.of(2026, 11, 12), Policy.nightKey(at(13, 12, 0), late));
        assertEquals(LocalDate.of(2026, 11, 12), Policy.nightKey(at(12, 1, 0), Policy.DEFAULT));
        assertEquals(LocalDate.of(2026, 11, 11), Policy.nightKey(at(12, 0, 30), Policy.DEFAULT));
        assertEquals(at(12, 8, 0), Policy.nightEnd(at(12, 3, 0), Policy.DEFAULT));
        assertEquals(at(13, 7, 0), Policy.nightEnd(at(12, 23, 30), late));
    }

    @Test public void progressRunsFromZeroToOne() {
        assertEquals(0, Policy.progress(at(12, 1, 0), Policy.DEFAULT), 1e-9);
        assertEquals(0.5, Policy.progress(at(12, 4, 30), Policy.DEFAULT), 1e-9);
        assertEquals(-1, Policy.progress(at(12, 9, 0), Policy.DEFAULT), 1e-9);
    }

    @Test public void windDownCountsMinutesUntilStart() {
        assertEquals(15, Policy.minutesUntilStart(at(12, 0, 45), Policy.DEFAULT));
        assertEquals(-1, Policy.minutesUntilStart(at(12, 2, 0), Policy.DEFAULT));
    }

    @Test public void cooldownGrowsWithEachUnlockAndIsCapped() {
        assertEquals(10 * 60000, Policy.waitMs(0)); assertEquals(15 * 60000, Policy.waitMs(1)); assertEquals(20 * 60000, Policy.waitMs(2));
        assertEquals(30 * 60000, Policy.waitMs(9));
        assertFalse(Policy.ready(0, 14 * 60000, Policy.waitMs(1))); assertTrue(Policy.ready(0, 15 * 60000, Policy.waitMs(1)));
    }

    @Test public void lazyReasonsAreRefused() {
        assertNotNull(Policy.reasonProblem("看一下")); assertNotNull(Policy.reasonProblem("啊啊啊啊啊啊啊啊啊啊啊"));
        assertNotNull(Policy.reasonProblem("1111111111")); assertNull(Policy.reasonProblem("要回复老板发来的明天会议时间"));
    }

    @Test public void invalidSchedulesAreRejected() {
        assertFalse(Policy.Schedule.valid(60, 60)); assertFalse(Policy.Schedule.valid(-1, 60)); assertFalse(Policy.Schedule.valid(60, 1440));
        assertThrows(IllegalArgumentException.class, () -> new Policy.Schedule(60, 60));
    }

    @Test public void restCountsPickupsAndLongestStretch() {
        long m = 60000; // window 0..420 minutes (a 7-hour night)
        java.util.List<long[]> sessions = java.util.List.of(new long[]{-30 * m, 10 * m}, new long[]{100 * m, 110 * m}, new long[]{105 * m, 120 * m}, new long[]{400 * m, 500 * m});
        Policy.Rest r = Policy.rest(sessions, 0, 420 * m);
        assertEquals(3, r.pickups);                       // in use at the start, once at 1:40 (two overlapping records), once near the end
        assertEquals((10 + 20 + 20) * m, r.usedMs);
        assertEquals(280 * m, r.longestRestMs);           // 120 to 400 minutes
        assertEquals(370 * m, r.restMs());
        Policy.Rest none = Policy.rest(java.util.List.of(), 0, 420 * m);
        assertEquals(0, none.pickups); assertEquals(420 * m, none.longestRestMs);
        assertEquals(0, Policy.rest(sessions, 10, 10).windowMs);
    }

    @Test public void durationsReadNaturally() {
        assertEquals("不到1分钟", Policy.duration(30000)); assertEquals("45分钟", Policy.duration(45 * 60000));
        assertEquals("6小时12分", Policy.duration((6 * 60 + 12) * 60000L)); assertEquals("7小时", Policy.duration(7 * 3600000L));
    }

    @Test public void sessionsAreSplitAtMidnight() {
        ZonedDateTime start = ZonedDateTime.of(2026, 11, 11, 23, 30, 0, 0, SHANGHAI);
        java.util.List<long[]> pieces = Policy.splitByDay(start.toInstant().toEpochMilli(), start.plusMinutes(50).toInstant().toEpochMilli(), SHANGHAI);
        assertEquals(2, pieces.size());
        assertEquals(LocalDate.of(2026, 11, 11).toEpochDay(), pieces.get(0)[0]); assertEquals(30 * 60000L, pieces.get(0)[1]);
        assertEquals(LocalDate.of(2026, 11, 12).toEpochDay(), pieces.get(1)[0]); assertEquals(20 * 60000L, pieces.get(1)[1]);
        assertEquals(0, Policy.splitByDay(10, 10, SHANGHAI).size());
    }

    @Test public void goalStepsDownWeeklyUntilTarget() {
        assertEquals(300, Policy.goalFor(300, 180, 10, 0)); assertEquals(300, Policy.goalFor(300, 180, 10, 6));
        assertEquals(290, Policy.goalFor(300, 180, 10, 7)); assertEquals(180, Policy.goalFor(300, 180, 10, 400));
        assertEquals(180, Policy.goalFor(300, 180, 0, 0)); assertEquals(180, Policy.goalFor(150, 180, 10, 0));
        assertEquals(240, Policy.roundUpTen(233)); assertEquals(240, Policy.roundUpTen(240));
    }

    @Test public void changeIsAPercentage() {
        assertEquals(Integer.valueOf(-20), Policy.change(300, 240)); assertEquals(Integer.valueOf(10), Policy.change(200, 220));
        assertNull(Policy.change(0, 100)); assertNull(Policy.change(Double.NaN, 100));
    }

    @Test public void appTimesRoundTrip() {
        java.util.Map<String, Long> m = Policy.parseTimes("com.a=60000;bad;com.b=x;com.a=1000;=5;com.c=0");
        assertEquals(Long.valueOf(61000), m.get("com.a")); assertNull(m.get("com.b")); assertEquals(2, m.size());
        assertEquals("com.a=61000;com.c=0", Policy.formatTimes(m));
        assertEquals(0, Policy.parseTimes(null).size()); assertEquals(0, Policy.parseTimes("").size());
    }

    @Test public void stricterLimitsApplyNowLooserOnesTomorrow() {
        java.util.Map<String, Integer> old = java.util.Map.of("video", 30, "game", 60);
        java.util.Map<String, Integer> next = java.util.Map.of("video", 60, "game", 20, "social", 45);
        java.util.Map<String, Integer> now = Policy.immediateLimits(old, next);
        assertEquals(Integer.valueOf(30), now.get("video")); assertEquals(Integer.valueOf(20), now.get("game")); assertEquals(Integer.valueOf(45), now.get("social"));
        assertEquals(Integer.valueOf(30), Policy.immediateLimits(old, java.util.Map.of("game", 60)).get("video")); // removal waits
        assertEquals(java.util.Map.of("video", 30), Policy.parseLimits("video=30;none=0"));
    }

    @Test public void limitIsReachedAtTheMinute() {
        assertFalse(Policy.limitReached(29 * 60000L, 30)); assertTrue(Policy.limitReached(30 * 60000L, 30));
        assertFalse(Policy.limitReached(999999999L, null)); assertFalse(Policy.limitReached(999999999L, 0));
    }

    @Test public void phrasesAreStableForTheSameNumber() {
        assertEquals(Policy.phrase(123456), Policy.phrase(123456)); assertTrue(Policy.phrase(7).endsWith(" 0007"));
    }
}
