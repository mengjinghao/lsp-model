# LSP-Model

LSPatch/Xposed 模块合集

## 模块列表

### 1. ShizukuSceneFix
修复 Scene工具箱 (com.omarea.vtools) 在 Shizuku 授权列表中不显示的问题。
- 包名: `com.example.shizukulistfix`
- 类型: Xposed 模块 (Java)
- 作用域: com.omarea.vtools, moe.shizuku.privileged.api, rikka.shizuku.manager

### 2. GameUnlockerPro (Root 版)
游戏性能解锁模块，通过 Shizuku 实现系统级刷新率/温控/GPU 优化。
- 包名: `com.gameunlocker.pro`
- 类型: Xposed 模块 (Kotlin)
- 依赖: Shizuku API (adb 级授权)
- 目标: 13 款主流手游

### 3. GameUnlockerPro (NoRoot 版)
免 Root 版游戏性能解锁，LSPatch 本地模式专用，无 Shizuku 依赖。
- 包名: `com.gameunlocker.noroot`
- 类型: Xposed 模块 (Kotlin)
- 依赖: 无 (Shizuku 可选)
- 目标: 10 款主流手游

### 4. MicroXEnhancer
微信/QQ 增强模块，免 Root LSPatch 本地模式。
- 包名: `com.microx.enhancer`
- 类型: Xposed 模块 (Kotlin)
- 功能: 去广告、防撤回、隐私保护、UI 美化等
- 目标: com.tencent.mm, com.tencent.mobileqq

## 使用方式

1. 安装 LSPatch 或 LSPosed
2. 将需要的模块 APK 通过 LSPatch 注入目标应用
3. GameUnlockerPro Root 版需额外安装 Shizuku 并授权

## 免责声明

仅供学习和研究使用，使用本模块产生的任何后果由使用者自行承担。
