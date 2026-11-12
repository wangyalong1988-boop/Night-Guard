package cn.nightguard;
import org.junit.Test;
import java.time.LocalTime;
import static org.junit.Assert.*;
public class PolicyTest {
 @Test public void scheduleBoundaries(){ assertFalse(Policy.night(LocalTime.of(0,59,59)));assertTrue(Policy.night(LocalTime.of(1,0)));assertTrue(Policy.night(LocalTime.of(7,59,59)));assertFalse(Policy.night(LocalTime.of(8,0)));assertFalse(Policy.night(LocalTime.NOON)); }
 @Test public void cooldownDoesNotAcceptMissingOrBackwardTime(){assertFalse(Policy.ready(-1,999999));assertFalse(Policy.ready(100,99));assertFalse(Policy.ready(100,600099));assertTrue(Policy.ready(100,600100));}
 @Test public void grantsAreScopedAndExpireExactly(){assertTrue(Policy.granted("browser","browser",200,199));assertFalse(Policy.granted("game","browser",200,199));assertFalse(Policy.granted("browser","browser",200,200));assertFalse(Policy.granted(null,null,200,100));}
}
