package com.gameunlocker.pro.utils

import com.gameunlocker.pro.models.DeviceProfile

/**
 * 内置主流旗舰机型参数库
 * 所有Build属性值均取自真实机型参数，用于伪装时精确还原
 * 包含：小米15、iQOO13、ROG9、iPhone16、三星S25、一加13、红魔10
 */
object DeviceProfileDatabase {

    /** 所有内置机型 */
    val BUILT_IN_PROFILES: List<DeviceProfile> = listOf(
        // ==================== 小米15 ====================
        DeviceProfile(
            id = "xiaomi15",
            displayName = "小米 15",
            brand = "Xiaomi",
            model = "24129PN74C",
            manufacturer = "Xiaomi",
            device = "haotian",
            board = "kalama",
            hardware = "qcom",
            product = "haotian",
            cpuModel = "Snapdragon 8 Elite",
            cpuCores = 8,
            cpuMaxFreq = "4300000",
            gpuModel = "Adreno 830",
            androidVersion = "15",
            sdkVersion = 35,
            screenWidth = 1200,
            screenHeight = 2670,
            densityDpi = 460,
            hasHighRefreshRate = true,
            maxRefreshRate = 120f,
            fingerprint = "Xiaomi/haotian/haotian:15/AP3A.240905.015.A1/OS2.0.17.0.VOBCNXM:user/release-keys",
            description = "高通骁龙8 Elite，120Hz高刷，适合王者/吃鸡极致帧率"
        ),

        // ==================== iQOO 13 ====================
        DeviceProfile(
            id = "iqoo13",
            displayName = "iQOO 13",
            brand = "vivo",
            model = "V2408A",
            manufacturer = "vivo",
            device = "PD2408",
            board = "kalama",
            hardware = "qcom",
            product = "PD2408",
            cpuModel = "Snapdragon 8 Elite",
            cpuCores = 8,
            cpuMaxFreq = "4300000",
            gpuModel = "Adreno 830",
            androidVersion = "15",
            sdkVersion = 35,
            screenWidth = 1440,
            screenHeight = 3200,
            densityDpi = 560,
            hasHighRefreshRate = true,
            maxRefreshRate = 144f,
            fingerprint = "vivo/PD2408/PD2408:15/AP3A.240905.015.A1/compiler11261647:user/release-keys",
            description = "游戏手机标杆，144Hz屏幕，适合吃鸡/使命召唤144帧模式"
        ),

        // ==================== ROG 9 ====================
        DeviceProfile(
            id = "rog9",
            displayName = "ROG Phone 9",
            brand = "asus",
            model = "ASUS_AI2401",
            manufacturer = "asus",
            device = "ASUS_AI2401",
            board = "kalama",
            hardware = "qcom",
            product = "WW_AI2401",
            cpuModel = "Snapdragon 8 Elite",
            cpuCores = 8,
            cpuMaxFreq = "4300000",
            gpuModel = "Adreno 830",
            androidVersion = "15",
            sdkVersion = 35,
            screenWidth = 1080,
            screenHeight = 2448,
            densityDpi = 480,
            hasHighRefreshRate = true,
            maxRefreshRate = 165f,
            fingerprint = "asus/WW_AI2401/ASUS_AI2401:15/AP3A.240905.015.A1/34.0820.0820.407-0:user/release-keys",
            description = "电竞旗舰，165Hz最高刷新率，适合全游戏极限帧率"
        ),

        // ==================== iPhone 16（伪装iOS设备标识） ====================
        DeviceProfile(
            id = "iphone16",
            displayName = "iPhone 16 Pro Max",
            brand = "Apple",
            model = "iPhone17,1",
            manufacturer = "Apple",
            device = "iPhone17,1",
            board = "D84AP",
            hardware = "apple",
            product = "iPhone17,1",
            cpuModel = "A18 Pro",
            cpuCores = 6,
            cpuMaxFreq = "4040000",
            gpuModel = "Apple A18 Pro GPU 6-core",
            androidVersion = "18",
            sdkVersion = 35,
            screenWidth = 1290,
            screenHeight = 2796,
            densityDpi = 460,
            hasHighRefreshRate = true,
            maxRefreshRate = 120f,
            fingerprint = "Apple/iPhone17,1/iPhone:18/A18Pro/release-keys",
            description = "伪装苹果设备，适用于对Android设备有苛刻限制的游戏"
        ),

        // ==================== 三星 S25 Ultra ====================
        DeviceProfile(
            id = "samsung_s25",
            displayName = "Samsung Galaxy S25 Ultra",
            brand = "samsung",
            model = "SM-S9380",
            manufacturer = "samsung",
            device = "g3q",
            board = "e3q",
            hardware = "qcom",
            product = "g3qzcx",
            cpuModel = "Snapdragon 8 Elite for Galaxy",
            cpuCores = 8,
            cpuMaxFreq = "4400000",
            gpuModel = "Adreno 830",
            androidVersion = "15",
            sdkVersion = 35,
            screenWidth = 1440,
            screenHeight = 3120,
            densityDpi = 560,
            hasHighRefreshRate = true,
            maxRefreshRate = 120f,
            fingerprint = "samsung/g3qzcx/g3q:15/AP3A.240905.015.A1/S9380ZCS2AYC1:user/release-keys",
            description = "三星旗舰，120Hz高刷，高分辨率适合原神高清材质"
        ),

        // ==================== 一加 13 ====================
        DeviceProfile(
            id = "oneplus13",
            displayName = "一加 13",
            brand = "OnePlus",
            model = "PJZ110",
            manufacturer = "OnePlus",
            device = "waffle",
            board = "kalama",
            hardware = "qcom",
            product = "PJZ110",
            cpuModel = "Snapdragon 8 Elite",
            cpuCores = 8,
            cpuMaxFreq = "4300000",
            gpuModel = "Adreno 830",
            androidVersion = "15",
            sdkVersion = 35,
            screenWidth = 1440,
            screenHeight = 3168,
            densityDpi = 560,
            hasHighRefreshRate = true,
            maxRefreshRate = 120f,
            fingerprint = "OnePlus/PJZ110/OP5D05L1:15/AP3A.240905.015.A1/V.3.0.0.PZ01_1:user/release-keys",
            description = "性能旗舰，120Hz LTPO屏幕，优秀游戏体验"
        ),

        // ==================== 红魔 10 Pro ====================
        DeviceProfile(
            id = "redmagic10",
            displayName = "红魔 10 Pro",
            brand = "nubia",
            model = "NX789J",
            manufacturer = "nubia",
            device = "NX789J",
            board = "kalama",
            hardware = "qcom",
            product = "NX789J_EEA",
            cpuModel = "Snapdragon 8 Elite Leading Version",
            cpuCores = 8,
            cpuMaxFreq = "4360000",
            gpuModel = "Adreno 830",
            androidVersion = "15",
            sdkVersion = 35,
            screenWidth = 1116,
            screenHeight = 2480,
            densityDpi = 480,
            hasHighRefreshRate = true,
            maxRefreshRate = 144f,
            fingerprint = "nubia/NX789J_EEA/NX789J:15/AP3A.240905.015.A1/REDMAGICOS10.0.2_EEA:user/release-keys",
            description = "专业电竞游戏手机，144Hz高刷，内置风扇散热"
        ),

        // ==================== 华为 Mate 60 Pro（备用） ====================
        DeviceProfile(
            id = "huawei_mate60pro",
            displayName = "华为 Mate 60 Pro",
            brand = "HUAWEI",
            model = "ALN-AL80",
            manufacturer = "HUAWEI",
            device = "HWALN",
            board = "kiran9000s",
            hardware = "kirin9000s",
            product = "ALN-AL80",
            cpuModel = "Kirin 9000S",
            cpuCores = 8,
            cpuMaxFreq = "2620000",
            gpuModel = "Maleoon 910",
            androidVersion = "14",
            sdkVersion = 34,
            screenWidth = 1260,
            screenHeight = 2720,
            densityDpi = 560,
            hasHighRefreshRate = true,
            maxRefreshRate = 120f,
            fingerprint = "HUAWEI/ALN-AL80/HWALN:14/SP2ADEV.240800.005/ALN-AL80_4.2.0.123C00:user/release-keys",
            description = "华为旗舰，120Hz高刷，覆盖鸿蒙游戏生态"
        )
    )

    /** 根据ID查找机型 */
    fun findById(id: String): DeviceProfile? {
        return BUILT_IN_PROFILES.find { it.id == id }
    }

    /** 根据显示名模糊查找 */
    fun findByDisplayName(name: String): DeviceProfile? {
        return BUILT_IN_PROFILES.find { it.displayName.contains(name, ignoreCase = true) }
    }

    /** 获取所有机型ID列表 */
    fun getAllIds(): List<String> = BUILT_IN_PROFILES.map { it.id }

    /** 获取所有显示名列表 */
    fun getAllDisplayNames(): List<String> = BUILT_IN_PROFILES.map { it.displayName }

    /** 获取自定义机型与内置机型的合并列表 */
    fun getAllProfiles(customProfiles: List<DeviceProfile> = emptyList()): List<DeviceProfile> {
        return BUILT_IN_PROFILES + customProfiles
    }
}
