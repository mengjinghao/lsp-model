package com.batteryopt.pro.ui.screens

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.ui.components.FeatureCard
import com.batteryopt.pro.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: BatteryConfig, onConfigChange: (BatteryConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("基础功能（应用层）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard("WakeLock 优化", "超长持有自动释放 + 拦截冗余 SDK 统计类",
            cfg.wakeLockEnabled,
            { val nc = cfg.copy(wakeLockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) })
        Spacer(Modifier.height(8.dp))

        FeatureCard("Alarm 闹钟优化", "高频精确闹钟降级为 setWindow",
            cfg.alarmEnabled,
            { val nc = cfg.copy(alarmEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) })
        Spacer(Modifier.height(8.dp))

        FeatureCard("Sync 同步降频", "requestSync 节流，周期同步最小 30 分钟",
            cfg.syncEnabled,
            { val nc = cfg.copy(syncEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) })
        Spacer(Modifier.height(8.dp))

        FeatureCard("JobScheduler 限频", "Job 最小周期 15 分钟 + requireDeviceIdle",
            cfg.jobEnabled,
            { val nc = cfg.copy(jobEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) })
        Spacer(Modifier.height(8.dp))

        FeatureCard("Location 定位降频", "最小间隔 30s，后台高频 GPS 降级 NETWORK",
            cfg.locationEnabled,
            { val nc = cfg.copy(locationEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) })
        Spacer(Modifier.height(8.dp))

        FeatureCard("Animation 动画优化", "scale=0 关闭动画省 GPU（默认关闭）",
            cfg.animationEnabled,
            { val nc = cfg.copy(animationEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) })
        Spacer(Modifier.height(8.dp))

        FeatureCard("Sensor 传感器降频", ">50Hz 高频传感器降频至 5Hz",
            cfg.sensorEnabled,
            { val nc = cfg.copy(sensorEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) })

        Spacer(Modifier.height(20.dp))
        Text("实验性功能（应用层）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard("蓝牙扫描降频", "Hook BluetoothLeScanner.startScan，最小 60s",
            cfg.bluetoothScanThrottleEnabled,
            { val nc = cfg.copy(bluetoothScanThrottleEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("后台相机阻断", "Hook Camera2/Camera.open，APP 在后台时阻止",
            cfg.cameraBackgroundBlockEnabled,
            { val nc = cfg.copy(cameraBackgroundBlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("振动器限频", "Hook Vibrator.vibrate，最小触发间隔 1s",
            cfg.vibratorThrottleEnabled,
            { val nc = cfg.copy(vibratorThrottleEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true)

        Spacer(Modifier.height(8.dp))
        FeatureCard("App 休眠管理", "Shizuku am force-stop + pm disable，延迟强制休眠后台APP",
            cfg.hibernationEnabled,
            { val nc = cfg.copy(hibernationEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true)
        Spacer(Modifier.height(8.dp))
        FeatureCard("网络省电", "Shizuku cmd netpolicy + iptables 限制后台数据连接",
            cfg.networkPowerSaveEnabled,
            { val nc = cfg.copy(networkPowerSaveEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true)
        Spacer(Modifier.height(8.dp))
        FeatureCard("屏幕调暗", "Hook WindowManager 亮度降至系统最低以下",
            cfg.screenDimmerEnabled,
            { val nc = cfg.copy(screenDimmerEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true)
        Spacer(Modifier.height(8.dp))
        FeatureCard("自动任务杀死", "Shizuku top -n 1 扫描，CPU 超阈值自动 kill -9",
            cfg.taskKillerEnabled,
            { val nc = cfg.copy(taskKillerEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true)

        Spacer(Modifier.height(20.dp))
        Text("系统级功能（需 Shizuku）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(8.dp))

        FeatureCard("系统 Doze 强制", "dumpsys deviceidle force-idle deep，屏幕关闭后延迟触发",
            cfg.dozeEnabled,
            { val nc = cfg.copy(dozeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            systemLevel = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("后台 APP 冻结", "am force-stop 黑名单，屏幕关闭 N 分钟后批量冻结",
            cfg.freezeEnabled,
            { val nc = cfg.copy(freezeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            systemLevel = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("CPU 调度策略", "echo governor > /sys/.../scaling_governor，屏幕关闭切 powersave",
            cfg.cpuGovernorEnabled,
            { val nc = cfg.copy(cpuGovernorEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            systemLevel = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("孤儿 WakeLock 清理", "dumpsys power 文本分析，识别孤儿 wake lock",
            cfg.greenifyEnabled,
            { val nc = cfg.copy(greenifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            systemLevel = true)

        Spacer(Modifier.height(20.dp))
        Text("实验性功能（系统级）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(8.dp))

        FeatureCard("低电量模式自动切换", "settings put global low_power，电量低于阈值自动开启",
            cfg.lowPowerModeAutoEnabled,
            { val nc = cfg.copy(lowPowerModeAutoEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true, systemLevel = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("电量统计重置", "dumpsys batterystats --reset，充电 N 秒后重置",
            cfg.batteryStatsResetEnabled,
            { val nc = cfg.copy(batteryStatsResetEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true, systemLevel = true)

        Spacer(Modifier.height(20.dp))
        Text("Root v1.1.0 系统级（需 Shizuku）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(8.dp))

        FeatureCard("GPU Governor 省电", "Shizuku echo powersave > /sys/class/kgsl/kgsl-3d0/devfreq/governor + min_pwrlevel 0",
            cfg.gpuGovBatteryEnabled,
            { val nc = cfg.copy(gpuGovBatteryEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            systemLevel = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("AppOps 后台限制", "Shizuku cmd appops set RUN_IN_BACKGROUND deny + WAKE_LOCK deny + standby-bucket rare",
            cfg.appOpsManagerEnabled,
            { val nc = cfg.copy(appOpsManagerEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            systemLevel = true)
        Spacer(Modifier.height(8.dp))

        FeatureCard("I/O 调度器省电", "Shizuku echo noop > /sys/block/mmcblk0/queue/scheduler + 禁用 low_latency",
            cfg.ioMgmtEnabled,
            { val nc = cfg.copy(ioMgmtEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            systemLevel = true)

        Spacer(Modifier.height(20.dp))
        Text("参数调整", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Text("WakeLock 最大持有: ${cfg.wakeLockMaxHoldSec}s", style = MaterialTheme.typography.bodySmall)
        val wakeLockMaxHoldSecState = remember(cfg) { mutableFloatStateOf(cfg.wakeLockMaxHoldSec.toFloat()) }
            Slider(
                value = wakeLockMaxHoldSecState.floatValue,
                onValueChange = { wakeLockMaxHoldSecState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(wakeLockMaxHoldSec = wakeLockMaxHoldSecState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 10f..300f
            )

        Text("Alarm 最小间隔: ${cfg.alarmMinIntervalMin} 分钟", style = MaterialTheme.typography.bodySmall)
        val alarmMinIntervalMinState = remember(cfg) { mutableFloatStateOf(cfg.alarmMinIntervalMin.toFloat()) }
            Slider(
                value = alarmMinIntervalMinState.floatValue,
                onValueChange = { alarmMinIntervalMinState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(alarmMinIntervalMin = alarmMinIntervalMinState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 1f..60f
            )

        Text("Doze 进入延迟: ${cfg.dozeDelaySec}s", style = MaterialTheme.typography.bodySmall)
        val dozeDelaySecState = remember(cfg) { mutableFloatStateOf(cfg.dozeDelaySec.toFloat()) }
            Slider(
                value = dozeDelaySecState.floatValue,
                onValueChange = { dozeDelaySecState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(dozeDelaySec = dozeDelaySecState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 0f..600f
            )

        Text("后台冻结延迟: ${cfg.freezeDelayMin} 分钟", style = MaterialTheme.typography.bodySmall)
        val freezeDelayMinState = remember(cfg) { mutableFloatStateOf(cfg.freezeDelayMin.toFloat()) }
            Slider(
                value = freezeDelayMinState.floatValue,
                onValueChange = { freezeDelayMinState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(freezeDelayMin = freezeDelayMinState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 1f..60f
            )

        Text("Greenify 清理周期: ${cfg.greenifyIntervalSec}s", style = MaterialTheme.typography.bodySmall)
        val greenifyIntervalSecState = remember(cfg) { mutableFloatStateOf(cfg.greenifyIntervalSec.toFloat()) }
            Slider(
                value = greenifyIntervalSecState.floatValue,
                onValueChange = { greenifyIntervalSecState.floatValue = it },
                onValueChangeFinished = {
                    val nc = cfg.copy(greenifyIntervalSec = greenifyIntervalSecState.floatValue.toInt())
                    ConfigManager.saveGlobalConfig(nc)
                    onConfigChange(nc)
                },
                valueRange = 60f..3600f
        )

        Spacer(Modifier.height(40.dp)
            )
    }
}
