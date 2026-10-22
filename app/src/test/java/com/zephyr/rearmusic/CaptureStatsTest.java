package com.zephyr.rearmusic;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaptureStatsTest {
    @Test public void unsignedWaveSilenceIsCentered() {
        CaptureStats stats = new CaptureStats(true);
        stats.add(new byte[] {(byte)128, (byte)128}, 48000000);
        String text = stats.drain();
        assertTrue(text.contains("nonzeroValues=0/2"));
        assertTrue(text.contains("rms=0.0"));
    }
    @Test public void fftSeparatesExcludedDcFromSelectedBins() {
        CaptureStats stats = new CaptureStats(false);
        stats.add(new byte[] {100, 0, 0, 0}, 48000000);
        String text = stats.drain();
        assertTrue(text.contains("absPeak=100"));
        assertTrue(text.contains("selectedBinComponentPeak=0"));
    }
    @Test public void countsChangesAndResetsWindowButRetainsTotal() {
        CaptureStats stats = new CaptureStats(false);
        stats.add(new byte[] {0,0,3,4}, 48000000);
        stats.add(new byte[] {0,0,3,4}, 48000000);
        stats.add(new byte[] {0,0,0,0}, 48000000);
        String text = stats.drain();
        assertTrue(text.contains("frames=3 total=3 nonzeroFrames=2 changed=1"));
        assertTrue(stats.drain().contains("frames=0 total=3"));
    }
}
