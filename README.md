# LSP-Model · LSP 模块合集总仓库

> 20 个 Xposed 模块统一仓库（11 NoRoot + 9 Root）· AI 开发指南 · 体检脚本 · Web 工具

<p align="center">
  <img src="https://img.shields.io/badge/Modules-20-blue" />
  <img src="https://img.shields.io/badge/NoRoot-11-emerald" />
  <img src="https://img.shields.io/badge/Root-9-rose" />
  <img src="https://img.shields.io/badge/AI_Guide-v1.2.0-blue" />
  <img src="https://img.shields.io/badge/Health_Check-Python-blue" />
</p>

## 仓库定位

本仓库是 LSP 模块的**总仓库**，统一管理 NoRoot + Root 两个版本，并提供 AI 开发指南与体检工具。

- **NoRoot 版独立仓库**：[LSPatch-Noroot-modle](https://github.com/AceGuru-mjh/LSPatch-Noroot-modle)
- **Root 版独立仓库**：[LSPosed-root-modle](https://github.com/AceGuru-mjh/LSPosed-root-modle)
- **本仓库（合集）**：lsp-model，含 20 个模块 + AI 开发指南 + 体检脚本

## 模块列表（20 个）

### NoRoot 版（11 个，基于 LSPatch）

| 模块 | 功能 |
|------|------|
| AdBlockerX_NoRoot | 广告拦截 |
| PrivacyGuard_NoRoot | 隐私保护 |
| GameUnlockerPro_NoRoot | 游戏加速 |
| BatteryOptimizer_NoRoot | 省电优化 |
| MicroXEnhancer | 微信 QQ 增强 |
| VipUnlocker_NoRoot | VIP 解锁 |
| VideoSaver_NoRoot | 视频下载 |
| StepModifier_NoRoot | 步数修改 |
| AudioBoost_NoRoot | 音量增强 |
| NotifyMaster_NoRoot | 通知管理 |
| ShizukuSceneFix | Shizuku 修复 |

### Root 版（9 个，基于 LSPosed + Magisk）

| 模块 | 功能 |
|------|------|
| AdBlockerX_Root | 广告拦截（系统级） |
| PrivacyGuard_Root | 隐私保护（系统级） |
| GameUnlockerPro_Root | 游戏加速（温控 + GPU） |
| BatteryOptimizer_Root | 省电优化（Doze） |
| NotifyMaster_Root | 通知管理 |
| VipUnlocker_Root | VIP 解锁 |
| VideoSaver_Root | 视频下载 |
| StepModifier_Root | 步数修改 |
| AudioBoost_Root | 音量增强 |

## 仓库结构

```
lsp-model/
├── modules/                          # 20 个独立 Gradle 工程
│   ├── keystore/mjh-release.jks      # 统一签名
│   ├── AdBlockerX_NoRoot/            # LSPatch 免Root
│   ├── AdBlockerX_Root/              # LSPosed Root
│   └── ...（共 20 个）
├── scripts/
│   └── healthcheck.py                # 体检脚本（扫描铁律违反）
├── web/                              # Web 体检工具
├── AI_DEV_GUIDE.md                   # AI 开发指南（项目宪法）
└── README.md                         # 本文件
```

## AI 开发指南

[AI_DEV_GUIDE.md](AI_DEV_GUIDE.md) 是给 AI Agent 阅读的项目宪法，包含：

- **元规则**：版本推进、体检优先、禁止空壳、NoRoot/Root 边界
- **架构图谱**：仓库结构、单模块结构、core/ui 隔离
- **三大铁律**：XposedLoader 强制规则（零 import + 反射 + 双分支）
- **Hook 编写规范**：findAndHookMethod 范式、异常处理、日志
- **IPC 规范**：ContentProvider + ConfigClient，禁用 MODE_WORLD_READABLE
- **CI 规范**：GitHub Actions matrix、签名、Release
- **体检脚本**：healthcheck.py 使用方法

每次介入本项目前**必须完整阅读** AI_DEV_GUIDE.md。

## 体检脚本

```bash
python3 scripts/healthcheck.py
```

扫描所有模块，检查：
- 铁律 1 违反（import hooks/*）
- 铁律 2 违反（String + classLoader）
- 空壳 Hook 文件
- MODE_WORLD_READABLE 残留
- prefs 名一致性
- 版本号统一

输出 `health-report.json`。

## 三大铁律

详见 [AI_DEV_GUIDE.md](AI_DEV_GUIDE.md) §1.4.2，或查看 [LSPatch-Noroot-modle](https://github.com/AceGuru-mjh/LSPatch-Noroot-modle) README。

1. **铁律 1**：XposedLoader 禁止 import hooks/*
2. **铁律 2**：Hook 必须用 Class.forName() 反射调用
3. **铁律 3**：进程双分支（自身进程走 UI，宿主进程走 Hook）

## 构建

每个模块是独立 Gradle 工程：

```bash
cd modules/GameUnlockerPro_NoRoot
gradle wrapper --gradle-version 8.2
./gradlew :app:assembleRelease
```

GitHub Actions matrix 并行构建全部 20 个模块。

## 技术栈

- **框架**：LSPatch（NoRoot）+ LSPosed（Root）
- **语言**：Kotlin 1.9.20
- **UI**：Jetpack Compose + Material 3
- **构建**：AGP 8.2.0 + Gradle 8.2 + JDK 17
- **minSdk**：26 / targetSdk：34
- **体检**：Python 3 + healthcheck.py

## 相关链接

- **NoRoot 仓库**：https://github.com/AceGuru-mjh/LSPatch-Noroot-modle
- **Root 仓库**：https://github.com/AceGuru-mjh/LSPosed-root-modle
- **LSPatch 框架**：https://github.com/LSPosed/LSPatch
- **LSPosed 框架**：https://github.com/LSPosed/LSPosed

## 开发者

**MJH** - [@AceGuru-mjh](https://github.com/AceGuru-mjh)

## License

MIT
