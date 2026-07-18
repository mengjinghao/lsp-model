# Xposed module entry points - keep
-keep class com.example.shizukulistfix.MainHook { *; }
-keep class com.example.shizukulistfix.scene.** { *; }
-keep class com.example.shizukulistfix.shizuku.** { *; }
-keep class com.example.shizukulistfix.utils.** { *; }

# Keep Xposed API
-keep class de.robv.android.xposed.** { *; }
-keep class dalvik.system.DexClassLoader { *; }
