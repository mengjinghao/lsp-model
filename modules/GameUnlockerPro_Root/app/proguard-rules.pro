# ============================================
# Game-Unlocker Pro ProGuard 混淆规则
# ============================================

# 保留Xposed模块入口
-keep class com.gameunlocker.pro.MainHook { *; }
-keep class com.gameunlocker.pro.hooks.** { *; }
-keep class com.gameunlocker.pro.utils.** { *; }
-keep class com.gameunlocker.pro.models.** { *; }

# 保留Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# 保留Shizuku反射类
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# 保留Gson序列化类
-keep class com.gameunlocker.pro.models.** { *; }
-keepclassmembers class com.gameunlocker.pro.models.** { *; }

# 保留Kotlin序列化
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod
