# Entrypoints are loaded by name from Xposed metadata, outside Android's manifest.
-keep class com.zephyr.rearmusic.LegacyEntry { public <init>(); public *; }
-keep class com.zephyr.rearmusic.ModernEntry { public <init>(); public *; }
# DexKit passes its models to JNI; retain its upstream reflection contract.
-keep class org.luckypray.dexkit.** { *; }
-dontwarn io.github.libxposed.**
-dontwarn de.robv.android.xposed.**
