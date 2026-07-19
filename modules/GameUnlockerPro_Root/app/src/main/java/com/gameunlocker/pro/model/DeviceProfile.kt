package com.gameunlocker.pro.model

/**
 * 设备机型伪装参数
 * 用于应用内 Build 属性伪装 + Root 版 Shizuku setprop 系统级修改
 *
 * 注意：Root 版可通过 Shizuku setprop 修改 ro.product.* 属性，
 * 但 ro.* 属性原生不可写，setprop 非持久化，重启后失效。
 */
data class DeviceProfile(
    val id: String,
    val displayName: String,
    val brand: String,
    val model: String,
    val manufacturer: String,
    val device: String,
    val board: String = "",
    val hardware: String = "",
    val product: String = "",
    val cpuModel: String = "Snapdragon 8 Elite",
    val gpuModel: String = "Adreno 750",
    val androidVersion: String = "14",
    val sdkVersion: Int = 34,
    val screenWidth: Int = 1440,
    val screenHeight: Int = 3200,
    val densityDpi: Int = 560,
    val maxRefreshRate: Float = 144f,
    val fingerprint: String = "",
    val isCustom: Boolean = false
)
