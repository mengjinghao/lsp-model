package com.batteryopt.pro.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.batteryopt.pro.R
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.ConfigManager

/**
 * BatteryOptimizer Pro 主界面（Root 版）
 *
 * 模块自身 APK 进程内的配置 Activity。
 * 用户在此界面修改各 APP 的省电优化开关，下次目标 APP 启动时生效。
 *
 * 界面结构：
 *  - 顶部：当前选中 APP 包名
 *  - 中部：7 个应用层优化开关 + 4 个系统级优化开关
 *  - 底部：作用域 APP 列表 + 自定义包名输入
 *
 * 注意：系统级开关需 Shizuku 授权才能实际生效
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvPkg: TextView
    private lateinit var appSwitchContainer: LinearLayout
    private lateinit var sysSwitchContainer: LinearLayout
    private lateinit var appListLayout: LinearLayout
    private lateinit var etCustomPkg: EditText
    private lateinit var btnAddCustom: Button
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button

    /** 当前选中 APP 的包名（用于配置编辑） */
    private var currentPkg: String = "com.tencent.mm"

    /** 作用域 APP 预设列表 */
    private val scopeApps = listOf(
        "com.tencent.mm" to "微信",
        "com.tencent.mobileqq" to "QQ",
        "com.ss.android.ugc.aweme" to "抖音",
        "com.smile.gifmaker" to "快手",
        "com.taobao.taobao" to "淘宝",
        "com.jingdong.app.mall" to "京东",
        "com.xunmeng.pinduoduo" to "拼多多",
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.netease.cloudmusic" to "网易云音乐",
        "com.tencent.wmusic" to "腾讯音乐",
        "com.zhihu.android" to "知乎",
        "com.sina.weibo" to "微博",
        "com.netease.mail" to "网易邮箱",
        "com.tencent.androidqqmail" to "QQ邮箱"
    )

    /** 当前编辑的配置（与 currentPkg 绑定） */
    private var currentConfig: BatteryConfig = BatteryConfig(packageName = currentPkg)

    /** 11 个开关的视图引用（key 与 BatteryConfig 字段对应） */
    private val switchMap = LinkedHashMap<String, SwitchCompat>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ConfigManager.init(this)

        tvPkg = findViewById(R.id.tvPkg)
        appSwitchContainer = findViewById(R.id.appSwitchContainer)
        sysSwitchContainer = findViewById(R.id.sysSwitchContainer)
        appListLayout = findViewById(R.id.appListLayout)
        etCustomPkg = findViewById(R.id.etCustomPkg)
        btnAddCustom = findViewById(R.id.btnAddCustom)
        btnSave = findViewById(R.id.btnSave)
        btnReset = findViewById(R.id.btnReset)

        buildSwitches()
        buildAppList()
        loadConfig(currentPkg)

        btnAddCustom.setOnClickListener {
            val pkg = etCustomPkg.text.toString().trim()
            if (pkg.isEmpty()) {
                Toast.makeText(this, R.string.toast_pkg_empty, Toast.LENGTH_SHORT).show()
            } else {
                appListLayout.addView(createAppItem(pkg, pkg))
                Toast.makeText(this, getString(R.string.toast_added, pkg), Toast.LENGTH_SHORT).show()
                etCustomPkg.text.clear()
            }
        }

        btnSave.setOnClickListener {
            currentConfig.wakeLockOptEnabled = switchMap["wakelock"]?.isChecked ?: true
            currentConfig.alarmOptEnabled = switchMap["alarm"]?.isChecked ?: true
            currentConfig.syncOptEnabled = switchMap["sync"]?.isChecked ?: true
            currentConfig.jobOptEnabled = switchMap["job"]?.isChecked ?: true
            currentConfig.locationOptEnabled = switchMap["location"]?.isChecked ?: true
            currentConfig.animationOptEnabled = switchMap["animation"]?.isChecked ?: false
            currentConfig.sensorOptEnabled = switchMap["sensor"]?.isChecked ?: true
            currentConfig.dozeEnabled = switchMap["doze"]?.isChecked ?: false
            currentConfig.freezeEnabled = switchMap["freeze"]?.isChecked ?: false
            currentConfig.cpuGovernorEnabled = switchMap["cpu"]?.isChecked ?: false
            currentConfig.greenifyEnabled = switchMap["greenify"]?.isChecked ?: false
            ConfigManager.saveAppConfig(currentConfig)
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_LONG).show()
        }

        btnReset.setOnClickListener {
            ConfigManager.deleteAppConfig(currentPkg)
            currentConfig = ConfigManager.createDefault(currentPkg)
            syncSwitches()
            Toast.makeText(this, R.string.toast_reset, Toast.LENGTH_SHORT).show()
        }
    }

    /** 构建 11 个开关（7 应用层 + 4 系统级） */
    private fun buildSwitches() {
        appSwitchContainer.removeAllViews()
        sysSwitchContainer.removeAllViews()
        switchMap.clear()

        val appItems = listOf(
            "wakelock" to (getString(R.string.opt_wakelock) to getString(R.string.opt_wakelock_sub)),
            "alarm" to (getString(R.string.opt_alarm) to getString(R.string.opt_alarm_sub)),
            "sync" to (getString(R.string.opt_sync) to getString(R.string.opt_sync_sub)),
            "job" to (getString(R.string.opt_job) to getString(R.string.opt_job_sub)),
            "location" to (getString(R.string.opt_location) to getString(R.string.opt_location_sub)),
            "animation" to (getString(R.string.opt_animation) to getString(R.string.opt_animation_sub)),
            "sensor" to (getString(R.string.opt_sensor) to getString(R.string.opt_sensor_sub))
        )

        val sysItems = listOf(
            "doze" to (getString(R.string.opt_doze) to getString(R.string.opt_doze_sub)),
            "freeze" to (getString(R.string.opt_freeze) to getString(R.string.opt_freeze_sub)),
            "cpu" to (getString(R.string.opt_cpu) to getString(R.string.opt_cpu_sub)),
            "greenify" to (getString(R.string.opt_greenify) to getString(R.string.opt_greenify_sub))
        )

        for ((key, pair) in appItems) {
            val (title, sub) = pair
            val switch = SwitchCompat(this).apply {
                text = title
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                isChecked = true
            }
            val tvSub = TextView(this).apply {
                text = sub
                textSize = 11f
                setTextColor(0x99FFFFFF.toInt())
                setPadding(switch.paddingLeft, 0, switch.paddingRight, 0)
            }
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                addView(switch)
                addView(tvSub)
            }
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(0x22FFFFFF.toInt())
            }
            appSwitchContainer.addView(container)
            appSwitchContainer.addView(divider)
            switchMap[key] = switch
        }

        for ((key, pair) in sysItems) {
            val (title, sub) = pair
            val switch = SwitchCompat(this).apply {
                text = title
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                isChecked = false
            }
            val tvSub = TextView(this).apply {
                text = sub
                textSize = 11f
                setTextColor(0xFFFFAB91.toInt())
                setPadding(switch.paddingLeft, 0, switch.paddingRight, 0)
            }
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                addView(switch)
                addView(tvSub)
            }
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(0x22FFFFFF.toInt())
            }
            sysSwitchContainer.addView(container)
            sysSwitchContainer.addView(divider)
            switchMap[key] = switch
        }
    }

    /** 构建作用域 APP 列表 */
    private fun buildAppList() {
        appListLayout.removeAllViews()
        for ((pkg, name) in scopeApps) {
            appListLayout.addView(createAppItem(pkg, name))
        }
    }

    /** 创建单个 APP 条目 */
    private fun createAppItem(pkg: String, name: String): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvName = TextView(this).apply {
            text = name
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
        }
        val tvPkgView = TextView(this).apply {
            text = pkg
            textSize = 11f
            setTextColor(0x99FFFFFF.toInt())
        }
        item.addView(tvName)
        item.addView(tvPkgView)
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { topMargin = 16 }
            setBackgroundColor(0x22FFFFFF.toInt())
        }
        item.addView(divider)

        item.setOnClickListener {
            currentPkg = pkg
            loadConfig(pkg)
            Toast.makeText(this, "已切换至 $name", Toast.LENGTH_SHORT).show()
        }
        return item
    }

    /** 加载指定 APP 的配置到 UI */
    private fun loadConfig(pkg: String) {
        currentPkg = pkg
        currentConfig = ConfigManager.getAppConfig(pkg)
        tvPkg.text = "当前配置: $pkg"
        syncSwitches()
    }

    /** 把 currentConfig 同步到 UI 开关 */
    private fun syncSwitches() {
        switchMap["wakelock"]?.isChecked = currentConfig.wakeLockOptEnabled
        switchMap["alarm"]?.isChecked = currentConfig.alarmOptEnabled
        switchMap["sync"]?.isChecked = currentConfig.syncOptEnabled
        switchMap["job"]?.isChecked = currentConfig.jobOptEnabled
        switchMap["location"]?.isChecked = currentConfig.locationOptEnabled
        switchMap["animation"]?.isChecked = currentConfig.animationOptEnabled
        switchMap["sensor"]?.isChecked = currentConfig.sensorOptEnabled
        switchMap["doze"]?.isChecked = currentConfig.dozeEnabled
        switchMap["freeze"]?.isChecked = currentConfig.freezeEnabled
        switchMap["cpu"]?.isChecked = currentConfig.cpuGovernorEnabled
        switchMap["greenify"]?.isChecked = currentConfig.greenifyEnabled
    }
}
