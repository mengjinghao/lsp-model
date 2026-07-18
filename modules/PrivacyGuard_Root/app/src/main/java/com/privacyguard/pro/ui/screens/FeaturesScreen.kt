package com.privacyguard.pro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.ui.components.FeatureCard
import com.privacyguard.pro.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: PrivacyConfig, onConfigChange: (PrivacyConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("基础功能（应用层）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "设备ID伪造", "IMEI/AndroidID/MAC/Serial 等设备标识随机伪造",
            cfg.deviceIdSpoofEnabled,
            { cfg.deviceIdSpoofEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "剪贴板保护", "监控并可选阻断应用读取剪贴板",
            cfg.clipboardGuardEnabled,
            { cfg.clipboardGuardEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "剪贴板读取拦截", "完全阻断应用读取剪贴板（可能影响粘贴功能）",
            cfg.clipboardBlockRead,
            { cfg.clipboardBlockRead = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "权限检查欺骗", "让应用误以为危险权限未授予，触发降级行为",
            cfg.permissionSpoofEnabled,
            { cfg.permissionSpoofEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "GPS位置伪造", "伪造经纬度坐标（下方可调）",
            cfg.locationSpoofEnabled,
            { cfg.locationSpoofEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "传感器伪造", "加速度/陀螺仪返回静态或加噪数据，防指纹",
            cfg.sensorFakerEnabled,
            { cfg.sensorFakerEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "广告ID屏蔽", "屏蔽 Google Advertising ID 获取",
            cfg.advertisingIdBlockEnabled,
            { cfg.advertisingIdBlockEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) }
        )

        Spacer(Modifier.height(20.dp))
        Text("应用层实验性功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "已安装应用可见性伪装", "从查询结果中隐藏 Xposed/Shizuku/Magisk 等敏感应用",
            cfg.packageVisibilitySpoofEnabled,
            { cfg.packageVisibilitySpoofEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "网络信息伪造", "伪造本机IP/DNS/MAC，防网络指纹追踪",
            cfg.networkInfoSpoofEnabled,
            { cfg.networkInfoSpoofEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "屏幕参数防指纹", "伪造分辨率/密度/刷新率，防屏幕特征追踪",
            cfg.screenMetricsSpoofEnabled,
            { cfg.screenMetricsSpoofEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "存储路径混淆", "混淆外部存储路径查询结果",
            cfg.storagePathSpoofEnabled,
            { cfg.storagePathSpoofEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))
        Text("Root 系统级功能（需 Shizuku）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "系统属性伪造", "Shizuku setprop 修改 ro.serialno/ro.product.* 等系统属性",
            cfg.systemPropSpoofEnabled,
            { cfg.systemPropSpoofEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "全局权限回收", "Shizuku pm revoke 真实回收指定APP危险权限（影响所有功能）",
            cfg.globalPermissionHookEnabled,
            { cfg.globalPermissionHookEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "网络标识伪造", "Shizuku 修改网卡MAC（写 /sys/class/net/wlan0/address 或 ip link set）",
            cfg.networkIdentifierHookEnabled,
            { cfg.networkIdentifierHookEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Shizuku 桥接", "Shizuku settings put 重置广告ID + pm clear 清理追踪数据",
            cfg.shizukuBridgeEnabled,
            { cfg.shizukuBridgeEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            rootLevel = true
        )

        Spacer(Modifier.height(20.dp))
        Text("Root 实验性功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "SELinux 上下文伪造", "Hook android.os.SELinux + /proc/self/attr/current 返回伪造上下文",
            cfg.selinuxContextSpoofEnabled,
            { cfg.selinuxContextSpoofEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true, rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "内核 cmdline 隐藏", "Hook 读取 /proc/cmdline 返回混淆内容，干扰 Root 检测",
            cfg.kernelCmdlineHideEnabled,
            { cfg.kernelCmdlineHideEnabled = it; ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
            experimental = true, rootLevel = true
        )

        Spacer(Modifier.height(20.dp))
        if (cfg.locationSpoofEnabled) {
            Text("位置参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("纬度: ${"%.4f".format(cfg.spoofLatitude)}", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = cfg.spoofLatitude.toFloat(),
                onValueChange = { cfg.spoofLatitude = it.toDouble(); ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
                valueRange = -90f..90f
            )
            Text("经度: ${"%.4f".format(cfg.spoofLongitude)}", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = cfg.spoofLongitude.toFloat(),
                onValueChange = { cfg.spoofLongitude = it.toDouble(); ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
                valueRange = -180f..180f
            )
        }

        if (cfg.sensorFakerEnabled) {
            Spacer(Modifier.height(16.dp))
            Text("传感器噪声级别", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val levels = listOf("静态", "加噪1", "加噪2", "加噪3")
            Text("当前: ${levels[cfg.sensorNoiseMode]}", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = cfg.sensorNoiseMode.toFloat(),
                onValueChange = { cfg.sensorNoiseMode = it.toInt(); ConfigManager.saveGlobalConfig(cfg); onConfigChange(cfg) },
                valueRange = 0f..3f,
                steps = 2
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}
