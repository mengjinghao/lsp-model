package com.microx.enhancer.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import com.microx.enhancer.R
import com.microx.enhancer.hooks.BatchManagerHook
import com.microx.enhancer.utils.ConfigManager
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream

/**
 * 微X增强模块设置界面
 *
 * 功能：
 * - 模块总开关
 * - 各功能分组开关（广告净化、消息增强、界面美化、隐私增强、批量管理、安全适配）
 * - 配置导入/导出
 * - 缓存清理触发按钮
 *
 * 注意：
 * - 此Activity运行在微信/QQ进程内（LSPatch集成模式）
 * - 部分设置修改后需要重启微信/QQ才能生效
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SettingsAdapter

    /** 所有设置项的列表 */
    private val allItems = mutableListOf<SettingsItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用纯代码构建UI（避免资源ID冲突）
        // LSPatch集成模式下，模块与微信共享资源ID命名空间
        adapter = SettingsAdapter(allItems)
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            this.adapter = this@SettingsActivity.adapter
        }
        setContentView(recyclerView)

        // 初始化配置
        ConfigManager.init(applicationContext)

        // 构建设置列表
        buildSettingsList()
    }

    /** 构建设置列表 */
    private fun buildSettingsList() {
        allItems.clear()

        // ===== 标题 =====
        allItems.add(SettingsItem.Header("===== 微X增强模块 ====="))
        allItems.add(SettingsItem.Header("适配微信8.0+ / QQ最新正式版"))

        // ===== 总开关 =====
        allItems.add(SettingsItem.SwitchItem(
            title = "模块总开关",
            description = "关闭后所有功能立即失效",
            key = ConfigManager.KEY_MASTER_SWITCH,
            isChecked = ConfigManager.isMasterEnabled()
        ) { isChecked ->
            ConfigManager.setMasterEnabled(isChecked)
            showToast("总开关已${if (isChecked) "开启" else "关闭"}")
            refreshAllSwitches()
        })

        // ===== 广告净化组 =====
        allItems.add(SettingsItem.Header("---- 广告净化 ----"))
        allItems.add(SettingsItem.SwitchItem("拦截开屏广告", "拦截启动时的开屏广告和引导页",
            ConfigManager.KEY_AD_SPLASH, ConfigManager.isEnabled(ConfigManager.KEY_AD_SPLASH)) {
            ConfigManager.setEnabled(ConfigManager.KEY_AD_SPLASH, it)
        })
        allItems.add(SettingsItem.SwitchItem("拦截朋友圈广告", "过滤朋友圈信息流推广",
            ConfigManager.KEY_AD_MOMENTS, ConfigManager.isEnabled(ConfigManager.KEY_AD_MOMENTS)) {
            ConfigManager.setEnabled(ConfigManager.KEY_AD_MOMENTS, it)
        })
        allItems.add(SettingsItem.SwitchItem("拦截公众号广告", "屏蔽文章内嵌广告和底部推荐",
            ConfigManager.KEY_AD_OFFICIAL_ACCOUNT, ConfigManager.isEnabled(ConfigManager.KEY_AD_OFFICIAL_ACCOUNT)) {
            ConfigManager.setEnabled(ConfigManager.KEY_AD_OFFICIAL_ACCOUNT, it)
        })
        allItems.add(SettingsItem.SwitchItem("拦截小程序广告", "屏蔽弹窗广告和激励视频",
            ConfigManager.KEY_AD_MINI_PROGRAM, ConfigManager.isEnabled(ConfigManager.KEY_AD_MINI_PROGRAM)) {
            ConfigManager.setEnabled(ConfigManager.KEY_AD_MINI_PROGRAM, it)
        })
        allItems.add(SettingsItem.SwitchItem("移除聊天页推广", "屏蔽商品推荐、直播推送、游戏广告",
            ConfigManager.KEY_AD_CHAT_CARD, ConfigManager.isEnabled(ConfigManager.KEY_AD_CHAT_CARD)) {
            ConfigManager.setEnabled(ConfigManager.KEY_AD_CHAT_CARD, it)
        })

        // ===== 消息增强组 =====
        allItems.add(SettingsItem.Header("---- 消息增强 ----"))
        allItems.add(SettingsItem.SwitchItem("消息防撤回", "完整保存已撤回的文字/图片/语音/文件",
            ConfigManager.KEY_ANTI_RECALL, ConfigManager.isEnabled(ConfigManager.KEY_ANTI_RECALL)) {
            ConfigManager.setEnabled(ConfigManager.KEY_ANTI_RECALL, it)
        })
        allItems.add(SettingsItem.SwitchItem("防删朋友圈", "好友删除的动态本地缓存不会消失",
            ConfigManager.KEY_ANTI_DELETE_MOMENT, ConfigManager.isEnabled(ConfigManager.KEY_ANTI_DELETE_MOMENT)) {
            ConfigManager.setEnabled(ConfigManager.KEY_ANTI_DELETE_MOMENT, it)
        })
        allItems.add(SettingsItem.SwitchItem("消息本地备份", "单独导出单聊/群聊全部记录",
            ConfigManager.KEY_MESSAGE_BACKUP, ConfigManager.isEnabled(ConfigManager.KEY_MESSAGE_BACKUP)) {
            ConfigManager.setEnabled(ConfigManager.KEY_MESSAGE_BACKUP, it)
        })
        allItems.add(SettingsItem.SwitchItem("关键词自动回复", "区分好友/群聊，多组自定义话术",
            ConfigManager.KEY_AUTO_REPLY, ConfigManager.isEnabled(ConfigManager.KEY_AUTO_REPLY)) {
            ConfigManager.setEnabled(ConfigManager.KEY_AUTO_REPLY, it)
        })

        // ===== 界面美化组 =====
        allItems.add(SettingsItem.Header("---- 界面美化 ----"))
        allItems.add(SettingsItem.SwitchItem("自定义聊天气泡", "替换默认气泡样式",
            ConfigManager.KEY_CUSTOM_BUBBLE, ConfigManager.isEnabled(ConfigManager.KEY_CUSTOM_BUBBLE)) {
            ConfigManager.setEnabled(ConfigManager.KEY_CUSTOM_BUBBLE, it)
        })
        allItems.add(SettingsItem.SwitchItem("全局字体调节", "独立设置微信/QQ全局字体大小",
            ConfigManager.KEY_CUSTOM_FONT, ConfigManager.isEnabled(ConfigManager.KEY_CUSTOM_FONT)) {
            ConfigManager.setEnabled(ConfigManager.KEY_CUSTOM_FONT, it)
        })
        allItems.add(SettingsItem.SwitchItem("隐藏小红点", "屏蔽所有未读标记红点",
            ConfigManager.KEY_HIDE_RED_DOT, ConfigManager.isEnabled(ConfigManager.KEY_HIDE_RED_DOT)) {
            ConfigManager.setEnabled(ConfigManager.KEY_HIDE_RED_DOT, it)
        })
        allItems.add(SettingsItem.SwitchItem("去除多余Tab", "移除游戏、购物、视频号入口",
            ConfigManager.KEY_REMOVE_TAB, ConfigManager.isEnabled(ConfigManager.KEY_REMOVE_TAB)) {
            ConfigManager.setEnabled(ConfigManager.KEY_REMOVE_TAB, it)
        })
        allItems.add(SettingsItem.SwitchItem("朋友圈精简", "过滤营销/代购，只显示原创",
            ConfigManager.KEY_SIMPLIFY_MOMENTS, ConfigManager.isEnabled(ConfigManager.KEY_SIMPLIFY_MOMENTS)) {
            ConfigManager.setEnabled(ConfigManager.KEY_SIMPLIFY_MOMENTS, it)
        })
        allItems.add(SettingsItem.SwitchItem("自定义聊天背景", "设置聊天页背景图片",
            ConfigManager.KEY_CHAT_BACKGROUND, ConfigManager.isEnabled(ConfigManager.KEY_CHAT_BACKGROUND)) {
            ConfigManager.setEnabled(ConfigManager.KEY_CHAT_BACKGROUND, it)
        })

        // ===== 隐私增强组 =====
        allItems.add(SettingsItem.Header("---- 隐私增强 ----"))
        allItems.add(SettingsItem.SwitchItem("隐藏正在输入", "不发送正在输入提示",
            ConfigManager.KEY_HIDE_TYPING, ConfigManager.isEnabled(ConfigManager.KEY_HIDE_TYPING)) {
            ConfigManager.setEnabled(ConfigManager.KEY_HIDE_TYPING, it)
        })
        allItems.add(SettingsItem.SwitchItem("隐藏已读状态", "不发送已读回执",
            ConfigManager.KEY_HIDE_READ_STATUS, ConfigManager.isEnabled(ConfigManager.KEY_HIDE_READ_STATUS)) {
            ConfigManager.setEnabled(ConfigManager.KEY_HIDE_READ_STATUS, it)
        })
        allItems.add(SettingsItem.SwitchItem("无限制转发", "语音/原图/收藏内容自由转发",
            ConfigManager.KEY_UNLIMITED_FORWARD, ConfigManager.isEnabled(ConfigManager.KEY_UNLIMITED_FORWARD)) {
            ConfigManager.setEnabled(ConfigManager.KEY_UNLIMITED_FORWARD, it)
        })
        allItems.add(SettingsItem.SwitchItem("强制原图发送", "关闭图片自动压缩",
            ConfigManager.KEY_FORCE_ORIGINAL, ConfigManager.isEnabled(ConfigManager.KEY_FORCE_ORIGINAL)) {
            ConfigManager.setEnabled(ConfigManager.KEY_FORCE_ORIGINAL, it)
        })
        allItems.add(SettingsItem.SwitchItem("无水印保存", "图片/视频去除水印保存本地",
            ConfigManager.KEY_NO_WATERMARK_SAVE, ConfigManager.isEnabled(ConfigManager.KEY_NO_WATERMARK_SAVE)) {
            ConfigManager.setEnabled(ConfigManager.KEY_NO_WATERMARK_SAVE, it)
        })

        // ===== 批量管理组 =====
        allItems.add(SettingsItem.Header("---- 批量管理 ----"))
        allItems.add(SettingsItem.SwitchItem("一键清理缓存", "清理图片/视频缓存，保留聊天记录",
            ConfigManager.KEY_ONE_KEY_CLEAN, ConfigManager.isEnabled(ConfigManager.KEY_ONE_KEY_CLEAN)) {
            ConfigManager.setEnabled(ConfigManager.KEY_ONE_KEY_CLEAN, it)
        })
        allItems.add(SettingsItem.ButtonItem("立即清理缓存") {
            performCleanCache()
        })
        allItems.add(SettingsItem.SwitchItem("批量导出好友", "导出好友名单至文件",
            ConfigManager.KEY_EXPORT_FRIENDS, ConfigManager.isEnabled(ConfigManager.KEY_EXPORT_FRIENDS)) {
            ConfigManager.setEnabled(ConfigManager.KEY_EXPORT_FRIENDS, it)
        })
        allItems.add(SettingsItem.ButtonItem("导出好友列表到CSV") {
            performExportContacts()
        })
        allItems.add(SettingsItem.SwitchItem("僵尸粉检测", "标记单向好友",
            ConfigManager.KEY_ZOMBIE_CHECK, ConfigManager.isEnabled(ConfigManager.KEY_ZOMBIE_CHECK)) {
            ConfigManager.setEnabled(ConfigManager.KEY_ZOMBIE_CHECK, it)
        })
        allItems.add(SettingsItem.SwitchItem("群聊垃圾过滤", "批量禁言/关键词过滤",
            ConfigManager.KEY_GROUP_MANAGE, ConfigManager.isEnabled(ConfigManager.KEY_GROUP_MANAGE)) {
            ConfigManager.setEnabled(ConfigManager.KEY_GROUP_MANAGE, it)
        })

        // ===== 安全适配组 =====
        allItems.add(SettingsItem.Header("---- 安全适配 ----"))
        allItems.add(SettingsItem.SwitchItem("绕过安全检测", "隐藏Xposed/LSPatch特征",
            ConfigManager.KEY_BYPASS_DETECTION, ConfigManager.isEnabled(ConfigManager.KEY_BYPASS_DETECTION)) {
            ConfigManager.setEnabled(ConfigManager.KEY_BYPASS_DETECTION, it)
        })

        // ===== 配置管理 =====
        allItems.add(SettingsItem.Header("---- 配置管理 ----"))
        allItems.add(SettingsItem.ButtonItem("导出配置到文件") {
            performExportConfig()
        })
        allItems.add(SettingsItem.ButtonItem("从文件导入配置") {
            performImportConfig()
        })
        allItems.add(SettingsItem.ButtonItem("恢复默认设置") {
            confirmResetDefaults()
        })

        allItems.add(SettingsItem.Header(""))
        allItems.add(SettingsItem.Header("提示：部分设置需重启微信/QQ生效"))
        allItems.add(SettingsItem.Header("版本: 1.0.0 | 适配LSPatch免Root"))

        adapter.notifyDataSetChanged()
    }

    /** 总开关切换后刷新所有子开关状态 */
    private fun refreshAllSwitches() {
        buildSettingsList()
        adapter.notifyDataSetChanged()
    }

    /** 执行缓存清理 */
    private fun performCleanCache() {
        try {
            val alertDialog = AlertDialog.Builder(this)
                .setTitle("清理缓存")
                .setMessage("正在清理缓存文件，请稍候...")
                .setCancelable(false)
                .create()
            alertDialog.show()

            Thread {
                val cleaned = BatchManagerHook.performCacheClean(applicationContext)
                runOnUiThread {
                    alertDialog.dismiss()
                    val mbSize = cleaned / (1024.0 * 1024.0)
                    showToast("清理完成！释放空间: ${"%.2f".format(mbSize)} MB")
                }
            }.start()
        } catch (e: Exception) {
            showToast("清理失败: ${e.message}")
        }
    }

    /** 执行好友导出 */
    private fun performExportContacts() {
        try {
            val exportDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val exportFile = File(exportDir, "wechat_contacts_${System.currentTimeMillis()}.csv")

            FileOutputStream(exportFile).use { fos ->
                fos.write("昵称,微信号,备注,地区,签名\n".toByteArray())
                // 实际数据从ContactStorage中获取（需要Hook上下文）
                fos.write("(请先在微信中打开通讯录页面以触发数据捕获)\n".toByteArray())
            }

            showToast("好友列表已导出: ${exportFile.absolutePath}")
        } catch (e: Exception) {
            showToast("导出失败: ${e.message}")
        }
    }

    /** 导出配置到文件 */
    private fun performExportConfig() {
        try {
            val exportDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val configFile = File(exportDir, "microx_enhancer_config.json")

            val json = JSONObject()
            ConfigManager.getAllConfig().forEach { (key, value) ->
                json.put(key, value)
            }

            FileOutputStream(configFile).use { fos ->
                fos.write(json.toString(2).toByteArray())
            }

            showToast("配置已导出至: ${configFile.absolutePath}")
        } catch (e: Exception) {
            showToast("配置导出失败: ${e.message}")
        }
    }

    /** 从文件导入配置 */
    private fun performImportConfig() {
        try {
            val importDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val configFile = File(importDir, "microx_enhancer_config.json")

            if (!configFile.exists()) {
                showToast("配置文件不存在: ${configFile.absolutePath}")
                return
            }

            val content = FileInputStream(configFile).use { it.readBytes().toString(Charsets.UTF_8) }
            val json = JSONObject(content)

            val configMap = mutableMapOf<String, Boolean>()
            json.keys().forEach { key ->
                configMap[key] = json.getBoolean(key)
            }

            ConfigManager.importConfig(configMap)
            buildSettingsList()
            showToast("配置导入成功，请重启应用生效")
        } catch (e: Exception) {
            showToast("配置导入失败: ${e.message}")
        }
    }

    /** 确认恢复默认 */
    private fun confirmResetDefaults() {
        AlertDialog.Builder(this)
            .setTitle("恢复默认设置")
            .setMessage("确定恢复所有设置为默认值？")
            .setPositiveButton("确定") { _, _ ->
                ConfigManager.resetToDefaults()
                buildSettingsList()
                showToast("已恢复默认设置")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

// ================================================================
//  设置项数据模型
// ================================================================
sealed class SettingsItem {
    data class Header(val text: String) : SettingsItem()
    data class SwitchItem(
        val title: String,
        val description: String = "",
        val key: String,
        val isChecked: Boolean,
        val onChanged: (Boolean) -> Unit
    ) : SettingsItem()
    data class ButtonItem(
        val title: String,
        val onClick: () -> Unit
    ) : SettingsItem()
}

// ================================================================
//  RecyclerView适配器
// ================================================================
class SettingsAdapter(
    private val items: MutableList<SettingsItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SettingsItem.Header -> 0
            is SettingsItem.SwitchItem -> 1
            is SettingsItem.ButtonItem -> 2
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> {
                val view = inflater.inflate(
                    android.R.layout.simple_list_item_1, parent, false
                )
                HeaderHolder(view)
            }
            1 -> {
                // 使用系统Switch布局
                val view = inflater.inflate(
                    android.R.layout.simple_list_item_2, parent, false
                )
                val switch = android.widget.Switch(parent.context)
                (view as? ViewGroup)?.addView(switch)
                SwitchHolder(view)
            }
            2 -> {
                val view = inflater.inflate(
                    android.R.layout.simple_list_item_1, parent, false
                )
                ButtonHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SettingsItem.Header -> {
                (holder as HeaderHolder).bind(item.text)
            }
            is SettingsItem.SwitchItem -> {
                (holder as SwitchHolder).bind(item)
            }
            is SettingsItem.ButtonItem -> {
                (holder as ButtonHolder).bind(item)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // ---- ViewHolder ----
    class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(text: String) {
            textView.text = text
            textView.setTextColor(
                if (text.startsWith("=") || text.startsWith("-"))
                    android.graphics.Color.parseColor("#1E88E5")
                else
                    android.graphics.Color.parseColor("#666666")
            )
            textView.textSize = if (text.startsWith("=")) 18f else 14f
        }
    }

    class SwitchHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text1: TextView = itemView.findViewById(android.R.id.text1)
        private val text2: TextView = itemView.findViewById(android.R.id.text2)
        private var switch: android.widget.Switch? = null
        private var currentItem: SettingsItem.SwitchItem? = null

        init {
            // 添加Switch控件
            itemView.post {
                switch = android.widget.Switch(itemView.context).apply {
                    (itemView as? ViewGroup)?.addView(
                        this,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                    setOnCheckedChangeListener { _, isChecked ->
                        currentItem?.onChanged?.invoke(isChecked)
                    }
                }
            }
        }

        fun bind(item: SettingsItem.SwitchItem) {
            currentItem = item
            text1.text = item.title
            text2.text = item.description
            switch?.apply {
                isChecked = item.isChecked
                isEnabled = true
            }
        }
    }

    class ButtonHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(item: SettingsItem.ButtonItem) {
            textView.text = item.title
            textView.setTextColor(android.graphics.Color.parseColor("#1E88E5"))
            textView.textSize = 16f
            itemView.setOnClickListener { item.onClick() }
            itemView.setBackgroundResource(android.R.drawable.list_selector_background)
        }
    }
}
