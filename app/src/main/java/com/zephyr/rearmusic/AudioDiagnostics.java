package com.zephyr.rearmusic;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Visualizer;
import android.os.SystemClock;

final class AudioDiagnostics {
    private long nextPoll;
    private boolean inventory;
    private final CaptureStats fft = new CaptureStats(false);
    private final CaptureStats wave = new CaptureStats(true);

    void capture(boolean waveform, byte[] data, int rate) {
        if (!BuildConfig.DIAGNOSTIC) return;
        (waveform ? wave : fft).add(data, rate);
    }

    void poll(Context context) {
        if (!BuildConfig.DIAGNOSTIC || SystemClock.elapsedRealtime() < nextPoll) return;
        nextPoll = SystemClock.elapsedRealtime() + 2000;
        DebugLog.i(Settings.TAG, "fft-raw " + fft.drain());
        DebugLog.i(Settings.TAG, "wave-raw " + wave.drain());
        try {
            AudioManager am = context.getSystemService(AudioManager.class);
            DebugLog.i(Settings.TAG, "audio-route mode=" + am.getMode() + " musicActive=" + am.isMusicActive()
                + " volume=" + am.getStreamVolume(AudioManager.STREAM_MUSIC)
                + "/" + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                + " muted=" + am.isStreamMute(AudioManager.STREAM_MUSIC)
                + " recordPermission=" + context.checkSelfPermission("android.permission.RECORD_AUDIO")
                + " audioSettingsPermission=" + context.checkSelfPermission("android.permission.MODIFY_AUDIO_SETTINGS"));
            StringBuilder outputs = new StringBuilder();
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                outputs.append("id=").append(d.getId()).append(" type=").append(d.getType())
                    .append(" rates=").append(java.util.Arrays.toString(d.getSampleRates())).append("; ");
            DebugLog.i(Settings.TAG, "connected-outputs (not necessarily active) " + outputs);
            var players = am.getActivePlaybackConfigurations();
            DebugLog.i(Settings.TAG, "active-audio-players count=" + players.size() + " (may be anonymized)");
            for (var player : players) {
                AudioDeviceInfo d = player.getAudioDeviceInfo();
                var attrs = player.getAudioAttributes();
                DebugLog.i(Settings.TAG, "audio-player usage=" + attrs.getUsage() + " content=" + attrs.getContentType()
                    + " flags=" + attrs.getFlags() + " capturePolicy=" + attrs.getAllowedCapturePolicy()
                    + " device=" + (d == null ? "unknown" : d.getId() + ":type=" + d.getType()));
            }
            if (!inventory) {
                inventory = true;
                for (String pkg : new String[] {Settings.HOST, Settings.SYSUI, "com.netease.cloudmusic"}) {
                    try {
                        var info = context.getPackageManager().getPackageInfo(pkg, 0);
                        DebugLog.i(Settings.TAG, "package-version " + pkg + " name=" + info.versionName + " code=" + info.getLongVersionCode());
                    } catch (Exception e) { DebugLog.w(Settings.TAG, "Package version " + pkg, e); }
                }
                for (var d : AudioEffect.queryEffects())
                    DebugLog.i(Settings.TAG, "available-effect name=" + d.name + " implementor=" + d.implementor
                        + " type=" + d.type + " uuid=" + d.uuid + " mode=" + d.connectMode);
            }
        } catch (Exception e) { DebugLog.w(Settings.TAG, "Audio diagnostics query unavailable", e); }
    }

    void visualizer(Visualizer v) {
        if (!BuildConfig.DIAGNOSTIC) return;
        try {
            Visualizer.MeasurementPeakRms measure = new Visualizer.MeasurementPeakRms();
            int result = v.getMeasurementPeakRms(measure);
            DebugLog.sample("visualizer-state", "enabled=" + v.getEnabled() + " captureSize=" + v.getCaptureSize()
                + " scaling=" + v.getScalingMode() + " measurement=" + v.getMeasurementMode()
                + " sampleRate=" + v.getSamplingRate() + " peakRmsResult=" + result
                + " peakMb=" + measure.mPeak + " rmsMb=" + measure.mRms);
        } catch (Exception e) { DebugLog.w(Settings.TAG, "Visualizer diagnostics unavailable", e); }
    }
}
