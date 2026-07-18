package com.privacyguard.pro.utils

import java.util.WeakHashMap

/**
 * 实例标签工具（替代 XposedHelpers.setAdditionalInstanceProperty/getAdditionalInstanceProperty）
 *
 * 用 WeakHashMap 存储 对象→标签 映射，GC 时自动清理。
 * 用于在 Hook 间传递实例状态（如标记某 FileInputStream 正在读取 /proc/cmdline）。
 */
object InstanceTagger {

    private val store: WeakHashMap<Any, MutableMap<String, Any?>> = WeakHashMap()

    @Synchronized
    fun setTag(obj: Any, key: String, value: Any?) {
        store.getOrPut(obj) { mutableMapOf() }[key] = value
    }

    @Synchronized
    fun getTag(obj: Any, key: String): Any? {
        return store[obj]?.get(key)
    }

    @Synchronized
    fun removeTag(obj: Any, key: String) {
        store[obj]?.remove(key)
    }

    @Synchronized
    fun clear() {
        store.clear()
    }
}
