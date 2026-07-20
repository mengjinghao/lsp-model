package com.audioboost.pro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.ui.components.FeatureCard
import com.audioboost.pro.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: AudioConfig, onConfigChange: (AudioConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("基础功能（同 NoRoot）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "音量增强", "Hook AudioTrack/MediaPlayer 增强音量输出",
            cfg.volumeBoostEnabled,
            { val nc = cfg.copy(volumeBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "低音增强", "Hook AudioEffect Equalizer 增强低频段",
            cfg.bassBoostEnabled,
            { val nc = cfg.copy(bassBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "均衡器", "Hook AudioEffect 自定义 5 段均衡器",
            cfg.equalizerEnabled,
            { val nc = cfg.copy(equalizerEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("实验性功能（同 NoRoot）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "扬声器增强", "Hook getStreamMaxVolume 返回放大值（仅显示）",
            cfg.speakerBoostEnabled,
            { val nc = cfg.copy(speakerBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "麦克风增益", "Hook MediaRecorder/AudioRecord 提高录音增益",
            cfg.micBoostEnabled,
            { val nc = cfg.copy(micBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "音质增强", "Hook AudioTrack 采样率/位深拦截提升",
            cfg.audioQualityEnhanceEnabled,
            { val nc = cfg.copy(audioQualityEnhanceEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("Root 专属（需 Shizuku）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "系统音量突破上限", "Shizuku media volume --set 设置系统音量超出最大值",
            cfg.systemVolumeBoostEnabled,
            { val nc = cfg.copy(systemVolumeBoostEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "AudioFlinger 节点写入", "Shizuku 写 /sys/class/audio/pcm 节点（部分设备支持）",
            cfg.audioFlingerNodeEnabled,
            { val nc = cfg.copy(audioFlingerNodeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            rootLevel = true
        )

        Spacer(Modifier.height(20.dp))
        Text("Root 实验性", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "AudioPolicy 修改", "Shizuku 修改 AudioPolicy 配置采样率/通道",
            cfg.globalAudioPolicyEnabled,
            { val nc = cfg.copy(globalAudioPolicyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true, rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Shizuku 音频桥接", "Shizuku cmd media_audio 执行系统音频命令",
            cfg.shizukuAudioBridgeEnabled,
            { val nc = cfg.copy(shizukuAudioBridgeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true, rootLevel = true
        )

        Spacer(Modifier.height(20.dp))
        Text("Root v1.1.0 系统级（需 Shizuku）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "tinymix ALSA 硬件控制", "Shizuku tinymix 直接控制硬件增益（RX/DAC/ADC/HP/SPK）",
            cfg.tinymixEnabled,
            { val nc = cfg.copy(tinymixEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Audio Effects XML 配置", "Shizuku 修改 audio_effects.xml 注入 compressor/limiter + mount --bind + killall audioserver",
            cfg.audioFxXmlEnabled,
            { val nc = cfg.copy(audioFxXmlEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            rootLevel = true
        )

        Spacer(Modifier.height(20.dp))
        Text("参数调整", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Text("增强等级: ${cfg.boostLevel}%", style = MaterialTheme.typography.bodySmall)
        val boostLevelState = remember(cfg) { mutableFloatStateOf(cfg.boostLevel.toFloat()) }
        Slider(
            value = boostLevelState.floatValue,
            onValueChange = { boostLevelState.floatValue = it },
            onValueChangeFinished = {
                val nc = cfg.copy(boostLevel = boostLevelState.floatValue.toInt())
                ConfigManager.saveGlobalConfig(nc)
                onConfigChange(nc)
            },
            valueRange = 100f..200f
        )

        Spacer(Modifier.height(16.dp))
        Text("低音等级: ${cfg.bassLevel}%", style = MaterialTheme.typography.bodySmall)
        val bassLevelState = remember(cfg) { mutableFloatStateOf(cfg.bassLevel.toFloat()) }
        Slider(
            value = bassLevelState.floatValue,
            onValueChange = { bassLevelState.floatValue = it },
            onValueChangeFinished = {
                val nc = cfg.copy(bassLevel = bassLevelState.floatValue.toInt())
                ConfigManager.saveGlobalConfig(nc)
                onConfigChange(nc)
            },
            valueRange = 0f..100f
        )

        Spacer(Modifier.height(16.dp))
        Text("系统音量额外提升: ${cfg.systemVolumeMaxBoost}%", style = MaterialTheme.typography.bodySmall)
        val systemVolState = remember(cfg) { mutableFloatStateOf(cfg.systemVolumeMaxBoost.toFloat()) }
        Slider(
            value = systemVolState.floatValue,
            onValueChange = { systemVolState.floatValue = it },
            onValueChangeFinished = {
                val nc = cfg.copy(systemVolumeMaxBoost = systemVolState.floatValue.toInt())
                ConfigManager.saveGlobalConfig(nc)
                onConfigChange(nc)
            },
            valueRange = 0f..100f
        )

        Spacer(Modifier.height(32.dp))
    }
}
