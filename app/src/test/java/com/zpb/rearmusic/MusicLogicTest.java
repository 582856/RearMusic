package com.zpb.rearmusic;

import org.junit.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class MusicLogicTest {
    @Test public void silenceNeverCreatesFakeRhythm() {
        assertEquals(0, RhythmMath.level(new byte[256]), 0);
        assertEquals(0, RhythmMath.level(null), 0);
        assertEquals(0, RhythmMath.level(new byte[2]), 0);
    }
    @Test public void louderSignalIncreasesBoundedLevel() {
        byte[] quiet = new byte[256], loud = new byte[256];
        for (int i = 2; i < 130; i++) { quiet[i] = 5; loud[i] = -128; }
        assertTrue(RhythmMath.level(quiet) > 0);
        assertTrue(RhythmMath.level(loud) > RhythmMath.level(quiet));
        assertTrue(RhythmMath.level(loud) <= 1);
    }
    @Test public void dcDoesNotProduceMusicEnergy() {
        byte[] dc = new byte[256]; dc[0] = 127; dc[1] = 127;
        assertEquals(0, RhythmMath.level(dc), 0);
    }
    @Test public void removalAndDisableRestoreOriginalRegistry() {
        Map<String, String> original = Map.of("vendor.music", "music", "other.app", "travel");
        var active = WhitelistPatch.apply(original, true, Set.of("new.player", "other.app"));
        assertEquals("music", active.get("new.player"));
        assertEquals("music", active.get("other.app"));
        assertEquals("travel", original.get("other.app"));
        assertFalse(WhitelistPatch.apply(original, true, Set.of()).containsKey("new.player"));
        assertEquals(original, WhitelistPatch.apply(original, false, Set.of("new.player")));
    }
    @Test public void fixedProfileIsSymmetricAndBounded() {
        for (int i = 0; i <= 12; i++) {
            assertEquals(RhythmMath.height(i), RhythmMath.height(-i), 0);
            assertTrue(RhythmMath.height(i) > 0 && RhythmMath.height(i) <= 1);
        }
        assertTrue(RhythmMath.height(0) < RhythmMath.height(12));
    }
}
