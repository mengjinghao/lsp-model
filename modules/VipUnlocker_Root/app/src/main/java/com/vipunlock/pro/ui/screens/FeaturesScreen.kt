package com.vipunlock.pro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vipunlock.pro.models.VipConfig
import com.vipunlock.pro.ui.components.FeatureCard
import com.vipunlock.pro.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: VipConfig, onConfigChange: (VipConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        // ===== 音乐类 =====
        Text("音乐类 VIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "网易云音乐 黑胶VIP", "Hook VIP 状态查询方法，返回已订阅黑胶VIP",
            cfg.netEaseVipEnabled,
            { val nc = cfg.copy(netEaseVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "QQ音乐 豪华绿钻", "Hook 绿钻状态查询，返回已开通豪华绿钻",
            cfg.qqMusicVipEnabled,
            { val nc = cfg.copy(qqMusicVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "酷狗音乐 豪华VIP", "通用候选类名 Hook，返回已订阅",
            cfg.kugouVipEnabled,
            { val nc = cfg.copy(kugouVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "酷我音乐 SVIP", "通用候选类名 Hook，返回已订阅",
            cfg.kuwoVipEnabled,
            { val nc = cfg.copy(kuwoVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))

        // ===== 视频类 =====
        Text("视频类 VIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "爱奇艺 黄金会员", "Hook VIP 状态查询方法，返回黄金会员",
            cfg.iqiyiVipEnabled,
            { val nc = cfg.copy(iqiyiVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "优酷 VIP会员", "通用候选类名 Hook，返回 VIP 已开通",
            cfg.youkuVipEnabled,
            { val nc = cfg.copy(youkuVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "腾讯视频 SVIP", "通用候选类名 Hook，返回 SVIP",
            cfg.tencentVideoVipEnabled,
            { val nc = cfg.copy(tencentVideoVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "哔哩哔哩 大会员", "Hook 大会员状态查询方法，返回年度大会员",
            cfg.biliVipEnabled,
            { val nc = cfg.copy(biliVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))

        // ===== 阅读/资讯类 =====
        Text("阅读/资讯类 VIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "喜马拉雅 VIP", "通用候选类名 Hook，返回已订阅 VIP",
            cfg.ximalayaVipEnabled,
            { val nc = cfg.copy(ximalayaVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "今日头条 关键功能", "通用候选类名 Hook，解锁部分付费功能",
            cfg.toutiaoVipEnabled,
            { val nc = cfg.copy(toutiaoVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "知乎 盐选会员", "通用候选类名 Hook，返回盐选会员已开通",
            cfg.zhihuVipEnabled,
            { val nc = cfg.copy(zhihuVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))

        // ===== 工具类 =====
        Text("工具类 VIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "百度网盘 SVIP", "通用候选类名 Hook，返回 SVIP",
            cfg.baiduNetdiskVipEnabled,
            { val nc = cfg.copy(baiduNetdiskVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "WPS 超级会员", "通用候选类名 Hook，返回超级会员已开通",
            cfg.wpsVipEnabled,
            { val nc = cfg.copy(wpsVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "微信读书 无限卡", "通用候选类名 Hook，返回无限卡已订阅",
            cfg.wereadVipEnabled,
            { val nc = cfg.copy(wereadVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))

        // ===== 应用层实验性 =====
        Text("应用层实验性功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "通用 VIP 尝试", "Hook isVip/isPremium/getVipLevel 等通用方法名，跨APP通用",
            cfg.universalVipTryEnabled,
            { val nc = cfg.copy(universalVipTryEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "通用去广告", "Hook 穿山甲/GDT/百度/快手/Mintegral 等广告 SDK",
            cfg.removeAdsEnabled,
            { val nc = cfg.copy(removeAdsEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "绕过签名/完整性校验", "Hook APP 自实现校验方法 + 拦截 su/Root 检测命令",
            cfg.bypassVerifyEnabled,
            { val nc = cfg.copy(bypassVerifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))

        // ===== Root 系统级 =====
        Text("Root 系统级功能（需 Shizuku）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "系统属性伪装VIP", "Shizuku setprop 修改 ro.product.model 等伪装高端机型（部分APP据此开放VIP）",
            cfg.systemPropVipEnabled,
            { val nc = cfg.copy(systemPropVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Google License 授权", "Hook LicenseChecker/Policy/LicenseCheckerCallback 返回已授权",
            cfg.licenseVerifyEnabled,
            { val nc = cfg.copy(licenseVerifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            rootLevel = true
        )

        Spacer(Modifier.height(20.dp))

        // ===== Root 实验性 =====
        Text("Root 实验性功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Shizuku 权限桥接", "Shizuku 执行 pm grant 授予隐藏权限（如 INSTALL_PACKAGES / WRITE_MEDIA_STORAGE）",
            cfg.shizukuVipBridgeEnabled,
            { val nc = cfg.copy(shizukuVipBridgeEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true, rootLevel = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "全局广告屏蔽 hosts", "Shizuku 修改 /system/etc/hosts 全局屏蔽广告域名（影响所有APP）",
            cfg.globalAdBlockEnabled,
            { val nc = cfg.copy(globalAdBlockEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true, rootLevel = true
        )
    }
}
