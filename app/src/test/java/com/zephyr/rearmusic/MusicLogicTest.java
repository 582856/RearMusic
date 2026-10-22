package com.zephyr.rearmusic;

import org.junit.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class MusicLogicTest {
    @Test public void logarithmicLevelsAreQuantizedAndBounded() {
        byte[] strong = new byte[256], stronger = new byte[256];
        for (int i = 2; i < 130; i++) { strong[i] = 20; stronger[i] = 110; }
        assertTrue(RhythmMath.level(strong) < 1f);
        assertTrue(RhythmMath.level(stronger) >= RhythmMath.level(strong));
        assertTrue(RhythmMath.level(stronger) <= 1f);
        assertEquals(Math.round(RhythmMath.level(strong) * 12), RhythmMath.level(strong) * 12, 0.00001);
    }
    @Test public void flipLogarithmicReferenceStages() {
        assertEquals(0, RhythmMath.flipStage(1));
        assertEquals(4, RhythmMath.flipStage(10));
        assertEquals(8, RhythmMath.flipStage(100));
        assertEquals(12, RhythmMath.flipStage(1000));
        assertEquals(12, RhythmMath.flipStage(10000));
        assertEquals(4, RhythmMath.flipStage(0.1));
        assertEquals(0, RhythmMath.flipStage(0));
        assertEquals(0, RhythmMath.flipStage(Double.NaN));
    }
    @Test public void barsStayBetweenCircularLenses() {
        for (int x = -120; x <= 120; x++) {
            float gap = RhythmMath.cameraGap(x, 125, 298, 6);
            assertEquals(gap, RhythmMath.cameraGap(-x, 125, 298, 6), 0);
            assertTrue(gap / 2 + Math.sqrt(125 * 125 - x * x) <= 149 - 6 + 0.001);
        }
    }
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
    @Test public void transientExpandsAcrossMostBars() {
        byte[] quiet = new byte[256], beat = new byte[256];
        quiet[4] = 5;
        beat[4] = 100;
        assertTrue(RhythmMath.level(beat) - RhythmMath.level(quiet) > 0.3f);
        assertTrue(RhythmMath.level(beat) <= 1f);
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
