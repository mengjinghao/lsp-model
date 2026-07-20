package com.audioboost.pro.models

/**
 * 音量增强配置（Root 版）
 *
 * 与 NoRoot 版区别：
 *  - 新增 systemVolumeBoostEnabled（系统级音量突破上限）
 *  - 新增 audioFlingerNodeEnabled（写 /sys/class/audio 节点）
 *  - 新增 globalAudioPolicyEnabled（修改 AudioPolicy 配置）
 *  - 新增 shizukuAudioBridgeEnabled（cmd media_audio 桥接）
 *
 * 能力扩展（需 Shizuku 授权）：
 *  - 通过 Shizuku 调用 media volume --set 设置系统音量突破上限
 *  - 通过 Shizuku 写 /sys/class/audio/pcm 节点（部分设备支持）
 *  - 通过 Shizuku 修改 AudioPolicy 配置
 *  - 通过 Shizuku 执行 cmd media_audio
 */
data class AudioConfig(
    // ===== 基础（同 NoRoot） =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,
    var volumeBoostEnabled: Boolean = true,
    var bassBoostEnabled: Boolean = false,
    var equalizerEnabled: Boolean = false,

    // ===== 实验性（同 NoRoot） =====
    var speakerBoostEnabled: Boolean = false,
    var micBoostEnabled: Boolean = false,
    var audioQualityEnhanceEnabled: Boolean = false,

    // ===== Root 专属（系统级） =====
    var systemVolumeBoostEnabled: Boolean = false,   // 系统级音量突破上限（Shizuku media volume --set）
    var audioFlingerNodeEnabled: Boolean = false,    // 写 /sys/class/audio 节点（部分设备支持）

    // ===== Root 专属实验性 =====
    var globalAudioPolicyEnabled: Boolean = false,   // 修改 AudioPolicy 配置
    var shizukuAudioBridgeEnabled: Boolean = false,  // cmd media_audio 桥接

    // ===== 参数 =====
    var boostLevel: Int = 150,
    var bassLevel: Int = 50,
    var eqBands: MutableList<Int> = mutableListOf(0, 0, 0, 0, 0),
    var micBoostLevel: Int = 150,
    var targetSampleRate: Int = 48000,
    var targetBitDepth: Int = 16,
    var speakerBoostMax: Int = 15,
    // Root 专属参数
    var systemVolumeMaxBoost: Int = 50,     // 系统音量额外提升百分比（%）
    var pcmNodePath: String = "/sys/class/audio/pcm",  // PCM 节点路径
    var audioPolicySampleRate: Int = 48000, // AudioPolicy 目标采样率

    var lastModified: Long = 0L
)
