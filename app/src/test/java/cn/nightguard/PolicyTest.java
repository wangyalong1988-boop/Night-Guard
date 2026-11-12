package cn.nightguard;
import org.junit.Test;
import java.time.LocalTime;
import static org.junit.Assert.*;
public class PolicyTest {
 @Test public void scheduleBoundaries(){ assertFalse(Policy.night(LocalTime.of(0,59,59)));assertTrue(Policy.night(LocalTime.of(1,0)));assertTrue(Policy.night(LocalTime.of(7,59,59)));assertFalse(Policy.night(LocalTime.of(8,0)));assertFalse(Policy.night(LocalTime.NOON)); }
 @Test public void cooldownDoesNotAcceptMissingOrBackwardTime(){assertFalse(Policy.ready(-1,999999));assertFalse(Policy.ready(100,99));assertFalse(Policy.ready(100,600099));assertTrue(Policy.ready(100,600100));}
 @Test public void grantsAreScopedAndExpireExactly(){assertTrue(Policy.granted("browser","browser",200,199));assertFalse(Policy.granted("game","browser",200,199));assertFalse(Policy.granted("browser","browser",200,200));assertFalse(Policy.granted(null,null,200,100));}
 @Test public void countdownSwitchesAtBothBoundaries(){
  java.time.ZoneId zone=java.time.ZoneId.of("Asia/Shanghai");
  java.time.ZonedDateTime before=java.time.ZonedDateTime.of(2026,11,12,0,59,59,0,zone);
  assertEquals(1,java.time.Duration.between(before,Policy.nextBoundary(before)).getSeconds());
  java.time.ZonedDateTime start=before.plusSeconds(1);
  assertEquals(7*3600,java.time.Duration.between(start,Policy.nextBoundary(start)).getSeconds());
  java.time.ZonedDateTime end=start.plusHours(7);
  assertEquals(17*3600,java.time.Duration.between(end,Policy.nextBoundary(end)).getSeconds());
 }
 @Test public void countdownUsesRealDurationAcrossDaylightSaving(){
  java.time.ZonedDateTime start=java.time.ZonedDateTime.of(2026,3,8,1,0,0,0,java.time.ZoneId.of("America/New_York"));
  assertEquals(6*3600,java.time.Duration.between(start,Policy.nextBoundary(start)).getSeconds());
 }
}
