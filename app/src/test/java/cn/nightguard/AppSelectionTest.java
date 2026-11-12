package cn.nightguard;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AppSelectionTest {
    @Test public void filteredBulkChangesPreserveHiddenAndUninstalledSelections(){
        AppSelection s=new AppSelection(Set.of("video","hidden","uninstalled"),Set.of("video","hidden","uninstalled"),Set.of("browser"));
        s.setAll(List.of("video"),false);
        assertEquals(Set.of("hidden","uninstalled","browser"),s.snapshot());
        assertEquals(2,s.visibleCount(List.of("hidden","browser","video")));
    }
    @Test public void browserCannotBeDeselectedEvenByClearAll(){
        AppSelection s=new AppSelection(Set.of(),Set.of(),Set.of("browser"));
        assertFalse(s.dirty());s.setAll(List.of("browser"),false);assertTrue(s.checked("browser"));assertFalse(s.dirty());
    }
    @Test public void draftSurvivesRestoreAndCanBeReverted(){
        AppSelection s=new AppSelection(Set.of("video"),Set.of("game"),Set.of());assertTrue(s.dirty());
        s.set("game",false);s.set("video",true);assertFalse(s.dirty());
        Set<String> exported=s.snapshot();exported.clear();assertTrue(s.checked("video"));
    }
}
