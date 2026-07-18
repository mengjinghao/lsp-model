package com.gameunlocker.pro.models

import com.google.gson.annotations.SerializedName

/**
 * 设备机型伪装配置
 * 包含主流旗舰机型的完整Build属性参数
 */
data class DeviceProfile(
    @SerializedName("id") val id: String,           // 唯一标识
    @SerializedName("displayName") val displayName: String, // 显示名称
    @SerializedName("brand") val brand: String,     // 品牌: Xiaomi, vivo, ASUS, Samsung...
    @SerializedName("model") val model: String,     // 型号: 24129PN74C, iQOO 13...
    @SerializedName("manufacturer") val manufacturer: String, // 制造商
    @SerializedName("device") val device: String,   // 设备代号
    @SerializedName("board") val board: String,     // 主板
    @SerializedName("hardware") val hardware: String,      // 硬件代号
    @SerializedName("product") val product: String,        // 产品名称
    @SerializedName("cpuModel") val cpuModel: String,      // CPU型号字符串
    @SerializedName("cpuCores") val cpuCores: Int = 8,     // CPU核心数
    @SerializedName("cpuMaxFreq") val cpuMaxFreq: String = "3300000", // CPU最大频率(KHz)
    @SerializedName("gpuModel") val gpuModel: String = "Adreno 750",  // GPU型号
    @SerializedName("androidVersion") val androidVersion: String = "14",  // Android版本
    @SerializedName("sdkVersion") val sdkVersion: Int = 34,               // SDK版本
    @SerializedName("screenWidth") val screenWidth: Int = 1440,           // 屏幕分辨率宽
    @SerializedName("screenHeight") val screenHeight: Int = 3200,         // 屏幕分辨率高
    @SerializedName("densityDpi") val densityDpi: Int = 560,              // 屏幕密度
    @SerializedName("hasHighRefreshRate") val hasHighRefreshRate: Boolean = true, // 是否有高刷屏
    @SerializedName("maxRefreshRate") val maxRefreshRate: Float = 144f,   // 最大刷新率
    @SerializedName("fingerprint") val fingerprint: String = "",          // 指纹字符串
    @SerializedName("description") val description: String = "",          // 描述
    @SerializedName("isCustom") val isCustom: Boolean = false             // 是否为用户自定义
)
