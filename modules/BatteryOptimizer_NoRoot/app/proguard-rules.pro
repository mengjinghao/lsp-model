# ============================================
# BatteryOptimizer NoRoot ProGuard 混淆规则
# ============================================

# 保留Xposed模块入口
-keep class com.batteryopt.noroot.MainHook { *; }
-keep class com.batteryopt.noroot.hooks.** { *; }
-keep class com.batteryopt.noroot.utils.** { *; }
-keep class com.batteryopt.noroot.models.** { *; }

# 保留Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# 保留Gson序列化类
-keepclassmembers class com.batteryopt.noroot.models.** { *; }

# 保留Kotlin序列化
-keepattributes *Annotation*,Signature,EnclosingMethod
