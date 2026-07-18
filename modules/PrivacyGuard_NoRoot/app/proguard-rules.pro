# ============================================
# PrivacyGuard NoRoot ProGuard 混淆规则
# ============================================

# 保留Xposed模块入口
-keep class com.privacyguard.noroot.MainHook { *; }
-keep class com.privacyguard.noroot.hooks.** { *; }
-keep class com.privacyguard.noroot.utils.** { *; }
-keep class com.privacyguard.noroot.models.** { *; }
-keep class com.privacyguard.noroot.ui.** { *; }

# 保留Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# 保留Shizuku反射类
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# 保留Gson序列化类
-keepclassmembers class com.privacyguard.noroot.models.** { *; }

# 保留Kotlin序列化
-keepattributes *Annotation*,Signature,EnclosingMethod
