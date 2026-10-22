package com.zephyr.rearmusic;

final class RhythmMath {
    private RhythmMath() { }
    static float level(byte[] fft) {
        if (fft == null || fft.length < 4) return 0;
        double peak = 0;
        int bins = 0;
        // Skip DC/Nyquist; low and mid frequencies provide a stable musical envelope.
        for (int i = 2; i + 1 < Math.min(fft.length, 130); i += 2) {
            double real = fft[i], imaginary = fft[i + 1];
            peak = Math.max(peak, Math.hypot(real, imaginary));
            bins++;
        }
        if (bins == 0) return 0;
        // A narrow transient must not disappear into the average of otherwise quiet bins.
        double energy = peak;
        // Signed FFT components can reach 128 each; preserve headroom instead of clipping ordinary peaks.
        if (energy == 0) return 0;
        double amplitude = Math.max(1, energy / Math.sqrt(2 * 128 * 128) * 1000);
        return flipStage(amplitude) / 12f;
    }

    static int flipStage(double amplitude) {
        if (!(amplitude > 0) || !Double.isFinite(amplitude)) return 0;
        double db = 20 * Math.log10(amplitude);
        return (int) Math.ceil(Math.abs(Math.max(-1, Math.min(1, db / 60)) * 12));
    }

    static float height(int index) {
        return (float) (0.09 + 0.91 * Math.pow(Math.min(12, Math.abs(index)) / 12.0, 2.2));
    }

    static float cameraGap(float x, float radius, float separation, float clearance) {
        return Math.max(0, separation - 2 * (float) Math.sqrt(Math.max(0, radius * radius - x * x)) - 2 * clearance);
    }
}
