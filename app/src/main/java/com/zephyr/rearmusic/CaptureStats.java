package com.zephyr.rearmusic;

final class CaptureStats {
    private final boolean waveform;
    private long frames, total, nonzeroFrames, values, nonzero, squares, sum, changed;
    private int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, peak, selectedPeak, rate, size;
    private byte[] previous;
    CaptureStats(boolean waveform) { this.waveform = waveform; }
    synchronized void add(byte[] data, int sampleRate) {
        if (data == null) return;
        frames++; total++; rate = sampleRate; size = data.length;
        if (previous != null && !java.util.Arrays.equals(previous, data)) changed++;
        previous = data.clone();
        boolean signal = false;
        for (int i = 0; i < data.length; i++) {
            int value = waveform ? (data[i] & 255) - 128 : data[i];
            min = Math.min(min, value); max = Math.max(max, value);
            peak = Math.max(peak, Math.abs(value));
            if (i >= 2 && i < 130) selectedPeak = Math.max(selectedPeak, Math.abs(value));
            if (value != 0) { nonzero++; signal = true; }
            sum += value; squares += (long) value * value; values++;
        }
        if (signal) nonzeroFrames++;
    }
    synchronized String drain() {
        String out = "frames=" + frames + " total=" + total + " nonzeroFrames=" + nonzeroFrames
            + " changed=" + changed + " bytes=" + size + " sampleRateMilliHz=" + rate
            + " nonzeroValues=" + nonzero + "/" + values + " min=" + (values == 0 ? 0 : min)
            + " max=" + (values == 0 ? 0 : max) + " absPeak=" + peak + " selectedBinComponentPeak=" + selectedPeak
            + " mean=" + (values == 0 ? 0 : (double) sum / values)
            + " rms=" + (values == 0 ? 0 : Math.sqrt((double) squares / values));
        frames = nonzeroFrames = values = nonzero = squares = sum = changed = 0;
        min = Integer.MAX_VALUE; max = Integer.MIN_VALUE; peak = selectedPeak = 0;
        return out;
    }
}
