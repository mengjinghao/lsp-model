# ============================================
# PrivacyGuard Pro ProGuard 混淆规则
# ============================================

# 保留Xposed模块入口
-keep class com.privacyguard.pro.MainHook { *; }
-keep class com.privacyguard.pro.hooks.** { *; }
-keep class com.privacyguard.pro.utils.** { *; }
-keep class com.privacyguard.pro.models.** { *; }
-keep class com.privacyguard.pro.ui.** { *; }

# 保留Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# 保留Shizuku反射类
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# 保留Gson序列化类
-keepclassmembers class com.privacyguard.pro.models.** { *; }

# 保留Kotlin序列化
-keepattributes *Annotation*,Signature,EnclosingMethod
