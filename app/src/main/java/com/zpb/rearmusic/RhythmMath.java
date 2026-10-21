package com.zpb.rearmusic;

final class RhythmMath {
    private RhythmMath() { }
    static float level(byte[] fft) {
        if (fft == null || fft.length < 4) return 0;
        double sum = 0;
        int bins = 0;
        // Skip DC/Nyquist; low and mid frequencies provide a stable musical envelope.
        for (int i = 2; i + 1 < Math.min(fft.length, 130); i += 2) {
            double real = fft[i], imaginary = fft[i + 1];
            sum += real * real + imaginary * imaginary;
            bins++;
        }
        if (bins == 0) return 0;
        return (float) Math.max(0, Math.min(1, Math.log1p(Math.sqrt(sum / bins)) / Math.log(129)));
    }

    static float height(int index) {
        return (float) (0.09 + 0.91 * Math.pow(Math.min(12, Math.abs(index)) / 12.0, 2.2));
    }
}
