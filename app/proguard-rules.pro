# Entrypoints are loaded by name from Xposed metadata, outside Android's manifest.
-keep class com.zpb.rearmusic.LegacyEntry { public <init>(); public *; }
-keep class com.zpb.rearmusic.ModernEntry { public <init>(); public *; }
# DexKit passes its models to JNI; retain its upstream reflection contract.
-keep class org.luckypray.dexkit.** { *; }
-dontwarn io.github.libxposed.**
-dontwarn de.robv.android.xposed.**
