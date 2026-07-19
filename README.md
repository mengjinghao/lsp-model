# LSP-Model

LSPatch / LSPosed 模块合集。涵盖游戏性能、隐私保护、广告拦截、省电优化、社交增强、Shizuku 修复等场景。

## 模块总览

| # | 模块 | 类型 | 包名 | 依赖 |
|---|------|------|------|------|
| 1 | ShizukuSceneFix | 通用 (Root+NoRoot) | com.example.shizukulistfix | 无 |
| 2 | GameUnlockerPro (Root) | LSPosed | com.gameunlocker.pro | Shizuku |
| 3 | GameUnlockerPro (NoRoot) | LSPatch | com.gameunlocker.noroot | 无 (Shizuku 可选) |
| 4 | MicroXEnhancer | LSPatch | com.microx.enhancer | 无 |
| 5 | PrivacyGuard (NoRoot) | LSPatch | com.privacyguard.noroot | 无 |
| 6 | PrivacyGuard (Root) | LSPosed | com.privacyguard.pro | Shizuku |
| 7 | AdBlockerX (NoRoot) | LSPatch | com.adblockerx.noroot | 无 |
| 8 | AdBlockerX (Root) | LSPosed | com.adblockerx.pro | Shizuku |
| 9 | BatteryOptimizer (NoRoot) | LSPatch | com.batteryopt.noroot | 无 |
| 10 | BatteryOptimizer (Root) | LSPosed | com.batteryopt.pro | Shizuku |

---

## 模块详情

### 1. ShizukuSceneFix
修复 Scene 工具箱 (com.omarea.vtools) 在 Shizuku 授权列表中不显示的问题。
- 包名: `com.example.shizukulistfix`
- 语言: Java
- 作用域: com.omarea.vtools, moe.shizuku.privileged.api, rikka.shizuku.manager

### 2. GameUnlockerPro (Root 版)
游戏性能解锁模块，通过 Shizuku 实现系统级刷新率/温控/GPU 优化。
- 包名: `com.gameunlocker.pro`
- 语言: Kotlin
- 依赖: Shizuku API (adb 级授权)
- 功能: 机型伪装、帧率解锁、温控屏蔽、GPU 调度、分辨率伪装、环境隐藏、Shizuku 系统属性修改
- 目标: 13 款主流手游

### 3. GameUnlockerPro (NoRoot 版)
免 Root 版游戏性能解锁，LSPatch 本地模式专用。
- 包名: `com.gameunlocker.noroot`
- 语言: Kotlin
- 依赖: 无 (Shizuku 可选)
- 功能: 机型伪装、帧率解锁、进程优化、分辨率伪装、环境隐藏
- **硬性限制**: 不含温控屏蔽、不含 GPU 调频、不修改系统属性
- 目标: 10 款主流手游

### 4. MicroXEnhancer
微信/QQ 增强模块，免 Root LSPatch 本地模式。
- 包名: `com.microx.enhancer`
- 语言: Kotlin
- 功能: 去广告、防撤回、防删朋友圈、界面美化、隐私增强、批量管理、自动回复
- 目标: com.tencent.mm, com.tencent.mobileqq

### 5. PrivacyGuard (NoRoot 版)
应用层隐私保护模块，伪造设备标识防止追踪。
- 包名: `com.privacyguard.noroot`
- 语言: Kotlin
- 功能:
  - 设备 ID 伪造 (IMEI / Android ID / MAC / Serial 等)
  - 剪贴板读取监控与阻断
  - 权限检查欺骗 (仅欺骗 APP 自身检查)
  - GPS 位置伪造
  - 传感器数据伪造 (防指纹追踪)
  - 广告 ID 屏蔽
- **硬性限制**: 仅应用进程内 Java 层 Hook，不修改系统属性、不全局拦截权限、不调用 Shizuku
- 目标: 微信/QQ/支付宝/淘宝/抖音/快手等 13 款 APP

### 6. PrivacyGuard (Root 版)
系统级隐私保护模块，包含 NoRoot 全部功能 + 系统级能力。
- 包名: `com.privacyguard.pro`
- 语言: Kotlin
- 依赖: Shizuku API
- 额外功能:
  - 系统属性伪造 (setprop 修改 ro.serialno / ro.product.* 等)
  - 全局权限回收 (pm revoke 真实回收危险权限)
  - 网卡 MAC 修改 (/sys/class/net/wlan0/address)
  - Shizuku 桥接 (settings put / pm clear 清理追踪数据)

### 7. AdBlockerX (NoRoot 版)
应用层广告拦截模块，拦截 APP 进程内网络请求。
- 包名: `com.adblockerx.noroot`
- 语言: Kotlin
- 功能:
  - WebView 广告拦截 (shouldInterceptRequest / URL 过滤 / JS 注入)
  - OkHttp 请求拦截 (多候选类名容错)
  - URLConnection 拦截
  - 内存 hosts 黑名单 (内置 85 条常见广告域名)
  - 广告 SDK View 隐藏 (21 个候选 SDK 类)
- **硬性限制**: 仅拦截本 APP 进程内网络请求，不修改 /system/etc/hosts、不修改系统 DNS、不设置全局 Private DNS
- 目标: 浏览器/微信/QQ/抖音/电商等 16 款 APP

### 8. AdBlockerX (Root 版)
系统级广告拦截模块，包含 NoRoot 全部功能 + 系统级能力。
- 包名: `com.adblockerx.pro`
- 语言: Kotlin
- 依赖: Shizuku API
- 额外功能:
  - 系统 hosts 文件修改 (Magisk overlay 路径 /data/adb/modules/adblockerx/system/etc/hosts)
  - 系统 Private DNS 设置 (settings put global private_dns_*)
  - 系统 DNS 解析 Hook (对广告域名返回 127.0.0.1)
  - Shizuku 桥接 (刷新 DNS 缓存)

### 9. BatteryOptimizer (NoRoot 版)
应用层省电优化模块，优化目标 APP 自身耗电行为。
- 包名: `com.batteryopt.noroot`
- 语言: Kotlin
- 功能:
  - WakeLock 优化 (超长持有自动释放 + 拦截冗余 SDK 统计类)
  - Alarm 闹钟优化 (高频精确闹钟降级为 setWindow)
  - Sync 同步优化 (非必要同步降频)
  - JobScheduler 优化 (非紧急 Job 追加 idle 约束)
  - Location 定位优化 (高频定位降频)
  - Animation 动画关闭
  - Sensor 传感器降频
- **硬性限制**: 仅优化当前 APP 自身，不冻结其他 APP、不修改系统 Doze、不修改内核调度
- 目标: 微信/QQ/抖音/电商/音乐等 14 款 APP

### 10. BatteryOptimizer (Root 版)
系统级省电优化模块，包含 NoRoot 全部功能 + 系统级能力。
- 包名: `com.batteryopt.pro`
- 语言: Kotlin
- 依赖: Shizuku API
- 额外功能:
  - 系统 Doze 强制 (dumpsys deviceidle force-idle deep)
  - 后台 APP 冻结 (am force-stop 黑名单)
  - CPU 调度策略 (屏幕关闭切换 powersave governor)
  - 孤儿 WakeLock 清理 (dumpsys power 分析释放)

---

## Root 版 vs NoRoot 版功能边界

| 能力 | NoRoot 版 | Root 版 |
|------|-----------|---------|
| 应用进程内 Java 层 Hook | ✅ | ✅ |
| 修改系统属性 (setprop) | ❌ | ✅ (Shizuku) |
| 修改 /system /sys 文件 | ❌ | ✅ (Shizuku) |
| 全局权限拦截 / pm 操作 | ❌ | ✅ (Shizuku) |
| 系统 Doze / 冻结其他 APP | ❌ | ✅ (Shizuku) |
| 修改系统 hosts / DNS | ❌ | ✅ (Shizuku) |
| 内核温控 / CPU/GPU 调频 | ❌ | ✅ (Shizuku) |

> NoRoot 版严格遵守 LSPatch 本地模式权限边界，不包含任何需要 Root/系统权限的功能。

## 使用方式

1. 安装 LSPatch (免 Root) 或 LSPosed (需 Root + Magisk)
2. Root 版模块需额外安装并授权 Shizuku
3. 将需要的模块 APK 通过 LSPatch 注入目标应用，或在 LSPosed 中勾选作用域
4. 重启目标 APP 生效

## 构建

每个模块为独立 Gradle 工程，进入对应目录执行：
```bash
./gradlew :app:assembleRelease
```
环境要求: JDK 17+, Android SDK 34, Gradle 8.2

## 目录结构

```
modules/
├── ShizukuSceneFix/          # 通用
├── GameUnlockerPro_Root/     # LSPosed
├── GameUnlockerPro_NoRoot/   # LSPatch
├── MicroXEnhancer/           # LSPatch
├── PrivacyGuard_NoRoot/      # LSPatch
├── PrivacyGuard_Root/        # LSPosed
├── AdBlockerX_NoRoot/        # LSPatch
├── AdBlockerX_Root/          # LSPosed
├── BatteryOptimizer_NoRoot/  # LSPatch
└── BatteryOptimizer_Root/    # LSPosed
```

## 免责声明

仅供学习和研究使用。使用本模块产生的任何后果（包括但不限于封号、设备损坏、数据丢失）由使用者自行承担。请遵守当地法律法规，勿用于商业或非法用途。
