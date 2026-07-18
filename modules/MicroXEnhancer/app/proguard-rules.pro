# 混淆规则：LSPatch模块不混淆
# 保留所有Xposed入口类和方法
-keep class com.microx.enhancer.MainHook { *; }
-keep class com.microx.enhancer.hooks.** { *; }
-keep class com.microx.enhancer.utils.** { *; }
-keep class com.microx.enhancer.ui.** { *; }

# 保留Xposed API
-keep class de.robv.android.xposed.** { *; }

# 保留Kotlin反射
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*
-keepclassmembers class ** {
    @de.robv.android.xposed.XposedBridge *;
}
