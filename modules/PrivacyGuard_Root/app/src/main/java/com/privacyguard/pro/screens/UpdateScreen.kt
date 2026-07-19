package com.privacyguard.pro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privacyguard.pro.XposedLoader
import com.privacyguard.pro.utils.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UpdateScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var checking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("热更新", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "检查 GitHub Release 是否有新版本，支持直接下载安装更新",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        // 当前版本
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("当前版本", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("v${XposedLoader.VERSION}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))

        // 检查更新按钮
        Button(
            onClick = {
                checking = true
                error = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        UpdateChecker.checkUpdate(XposedLoader.VERSION)
                    }
                    checking = false
                    if (result != null) {
                        info = result
                    } else {
                        error = "检查更新失败，请检查网络后重试"
                    }
                }
            },
            enabled = !checking && !downloading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(4.dp))
                Text("  检查中...")
            } else {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(Modifier.height(4.dp))
                Text("  检查更新")
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(error!!, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        info?.let { ui ->
            Spacer(Modifier.height(16.dp))

            // 更新状态卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (ui.hasUpdate) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        if (ui.hasUpdate) Icons.Default.NewReleases else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (ui.hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (ui.hasUpdate) "发现新版本" else "已是最新版本",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("最新版本: v${ui.latestVersion}  |  发布: ${ui.publishDate.take(10)}",
                             style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 更新说明
            if (ui.releaseNotes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("更新说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(ui.releaseNotes.take(500), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 下载安装
            if (ui.hasUpdate) {
                Spacer(Modifier.height(12.dp))
                val apk = UpdateChecker.findMatchingApk(ui, "PrivacyGuard_Root")
                if (apk != null) {
                    Text("下载: ${apk.name} (${"%.2f".format(apk.sizeBytes / 1024.0 / 1024.0)} MB)", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    if (downloading) {
                        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Button(
                            onClick = {
                                downloading = true
                                progress = 0f
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        com.privacyguard.pro.utils.ApkDownloader.downloadAndInstall(
                                            ctx, apk.downloadUrl, apk.name
                                        ) { p -> progress = p }
                                    }
                                    downloading = false
                                    if (!ok) error = "下载失败，请重试或手动下载"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Text("  下载并安装")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse(ui.releaseUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(i)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("在浏览器打开 Release 页面")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth(),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("说明", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("• 更新源: github.com/mengjinghao/lsp-model/releases", style = MaterialTheme.typography.bodySmall)
                Text("• 下载完成后会自动弹出安装界面（需允许\"安装未知应用\"权限）", style = MaterialTheme.typography.bodySmall)
                Text("• 模块更新后需在 LSPosed/LSPatch 重新启用并重启目标 APP", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
