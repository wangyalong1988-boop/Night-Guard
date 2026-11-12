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

    @Test public void phrasesAreStableForTheSameNumber() {
        assertEquals(Policy.phrase(123456), Policy.phrase(123456)); assertTrue(Policy.phrase(7).endsWith(" 0007"));
    }
}
