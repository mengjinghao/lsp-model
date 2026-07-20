package com.stepmod.pro.ui.screens

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
import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.ui.components.FeatureCard
import com.stepmod.pro.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: StepConfig, onConfigChange: (StepConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("基础功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "步数修改总开关", "总开关，关闭后下方三项基础 Hook 全部失效",
            cfg.stepModifyEnabled,
            { val nc = cfg.copy(stepModifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "步数传感器 Hook", "拦截 SensorManager 注册监听 + onSensorChanged 修改步数读数",
            cfg.stepModifyEnabled,
            { val nc = cfg.copy(stepModifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "步数上报 Hook", "拦截各运动APP的步数上报方法（支付宝/微信/小米/华为等）",
            cfg.stepModifyEnabled,
            { val nc = cfg.copy(stepModifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "StepCounter Hook", "Hook android.hardware.StepCounter/StepDetector 类",
            cfg.stepModifyEnabled,
            { val nc = cfg.copy(stepModifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("实验性功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "步数传感器阻断", "完全阻断应用注册 TYPE_STEP_COUNTER/DETECTOR（激进方案）",
            cfg.sensorBlockEnabled,
            { val nc = cfg.copy(sensorBlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "多APP步数同步", "Hook 跨APP步数查询（ContentResolver/Provider）",
            cfg.multiAppSyncEnabled,
            { val nc = cfg.copy(multiAppSyncEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "步数历史伪造", "Hook SQLite/SharedPreferences 读取，伪造步数历史趋势",
            cfg.stepHistoryFakeEnabled,
            { val nc = cfg.copy(stepHistoryFakeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("Root 专属：系统级 Hook", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "系统传感器节点写入", "Shizuku 写 /sys/class/sensors/step_counter/value 等系统节点",
            cfg.systemSensorEnabled,
            { val nc = cfg.copy(systemSensorEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "健康服务系统级写入", "Shizuku 调用 Google Fit / 华为健康 / 小米健康 API 系统级写入步数",
            cfg.healthServiceEnabled,
            { val nc = cfg.copy(healthServiceEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("Root 实验性", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "内核节点注入", "Shizuku 写 /proc/step_counter /dev/step_counter 内核节点（高风险，机型相关）",
            cfg.kernelStepInjectEnabled,
            { val nc = cfg.copy(kernelStepInjectEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Shizuku 步数广播桥接", "Shizuku 执行 am broadcast 广播步数到各健康APP（8 个候选 Action）",
            cfg.shizukuStepBridgeEnabled,
            { val nc = cfg.copy(shizukuStepBridgeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("Root v1.1.0 系统级（需 Shizuku）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "传感器 HAL 直接注入", "Shizuku 写 /dev/iio:device*/in_step_counter_raw + find iio/step 设备节点",
            cfg.sensorHalDirectEnabled,
            { val nc = cfg.copy(sensorHalDirectEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "健康数据库直接操作", "Shizuku sqlite3 Google Fit / 华为健康 / 小米健康 SharedPrefs 直接写入",
            cfg.healthDbDirectEnabled,
            { val nc = cfg.copy(healthDbDirectEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))
        Text("步数参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("目标步数: ${cfg.customSteps} 步", style = MaterialTheme.typography.bodySmall)
        val stepsState = remember(cfg) { mutableFloatStateOf(cfg.customSteps.toFloat()) }
        Slider(
            value = stepsState.floatValue,
            onValueChange = { stepsState.floatValue = it },
            onValueChangeFinished = {
                val nc = cfg.copy(customSteps = stepsState.floatValue.toInt())
                ConfigManager.saveGlobalConfig(nc)
                onConfigChange(nc)
            },
            valueRange = 1000f..50000f
        )

        Spacer(Modifier.height(16.dp))
        Text("随机波动: ±${cfg.randomFluctuation} 步", style = MaterialTheme.typography.bodySmall)
        val flState = remember(cfg) { mutableFloatStateOf(cfg.randomFluctuation.toFloat()) }
        Slider(
            value = flState.floatValue,
            onValueChange = { flState.floatValue = it },
            onValueChangeFinished = {
                val nc = cfg.copy(randomFluctuation = flState.floatValue.toInt())
                ConfigManager.saveGlobalConfig(nc)
                onConfigChange(nc)
            },
            valueRange = 0f..1000f
        )
        Spacer(Modifier.height(32.dp))
    }
}
