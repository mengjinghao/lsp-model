# 微X增强模块 — 常见问题排错指南

## 一、模块不显示/无法识别

### 症状：LSPatch管理页面看不到「微X增强」模块
**原因**: 模块APK未被正确识别为Xposed模块

**修复步骤**:
1. 检查 `xposed_init` 文件是否正确放置在 `assets/` 目录下
2. 确认 `xposed_init` 内容为 `com.microx.enhancer.MainHook`（无多余空格、换行）
3. 检查 `AndroidManifest.xml` 中是否有以下meta-data：
   ```xml
   <meta-data android:name="xposedmodule" android:value="true" />
   <meta-data android:name="xposedminversion" android:value="82" />
   ```
4. 在LSPatch中点击「+」→「从存储目录选择APK」重新导入模块
5. 如果使用旧版LSPatch（<0.5），升级到最新版

### 症状：模块已勾选但功能不生效
**原因**: 模块未正确注入到目标进程

**修复步骤**:
1. 检查LSPatch作用域配置：确保模块勾选在微信/QQ的作用域下
2. 确认微信包名为 `com.tencent.mm`（非修改版/企业微信）
3. 确认QQ包名为 `com.tencent.mobileqq`（非Tim/轻聊版）
4. 查看Xposed日志（LSPatch → 日志）是否有 `MicroXEnhancer` 相关输出
5. 尝试完全卸载修补后的微信/QQ，重新修补安装

---

## 二、应用闪退/崩溃

### 症状：修补后的微信/QQ启动即闪退
**原因**: 模块代码异常、Hook目标类不存在、或安全检测触发

**修复步骤**:
1. **临时关闭所有功能**：
   - 使用adb命令清除模块配置：
   ```bash
   adb shell pm clear com.microx.enhancer
   ```
   - 或手动删除SharedPreferences：`/data/data/com.tencent.mm/shared_prefs/microx_enhancer_config.xml`

2. **检查微信版本兼容性**：
   - 本模块适配微信 8.0+ (版本号范围: 0x2800xxxx)
   - 可以通过adb查看微信版本：
   ```bash
   adb shell dumpsys package com.tencent.mm | grep versionName
   ```

3. **获取崩溃日志**：
   ```bash
   adb logcat -s MicroXEnhancer:* Xposed:*
   ```
   - 查看崩溃前的错误信息
   - 常见错误：「ClassNotFoundError」— 说明某个Hook目标类在当前微信版本中不存在
   - 在模块设置中逐个关闭功能，排查是哪个功能导致的崩溃

4. **LSPatch版本不兼容**：
   - 确认LSPatch版本 >= 0.6
   - 尝试使用LSPatch的「便携模式」替代「集成模式」

### 症状：打开特定页面时闪退
**原因**: 某个特定Hook功能导致该页面异常

**修复步骤**:
1. 先关闭所有功能，逐一开启测试
2. 常见问题功能：
   - 朋友圈页面闪退 → 关闭「朋友圈精简」和「防删朋友圈」
   - 聊天页面闪退 → 关闭「消息防撤回」和「隐藏正在输入」
   - 公众号页面闪退 → 关闭「拦截公众号广告」
3. 确认问题功能后，暂时关闭该功能，等待模块更新

---

## 三、微信安全弹窗

### 症状：「检测到非官方版本」或「账号异常」提示
**原因**: 微信检测到Xposed/LSPatch框架的特征

**修复步骤**:
1. **确认「绕过安全检测」已开启**（设置→安全适配→绕过安全检测）
2. 如果已经开启：
   - 检查LSPatch版本，某些旧版有更明显的特征
   - 确认使用的是LSPatch官方Release版（非魔改版）
   - 尝试更换LSPatch的隐蔽模式

3. **额外措施**：
   - 不使用模块时，在LSPatch中移除微信的作用域
   - 使用「集成模式」而非「本地模式」（特征更少）
   - 不定期更新LSPatch到最新版
   - 避免在微信官方内测版上使用

4. **降低风险的注意事项**：
   - 不要同时使用多个Xposed模块在微信上
   - 避免高频操作（如批量添加好友、群发消息等）
   - 不使用模块的其他高危功能（如自动抢红包、刷步数等）

---

## 四、Hook失效（某功能不工作）

### 症状：防撤回无效，消息被撤回后仍然消失
**原因**: 微信更新后撤回逻辑改变，Hook目标类名或方法名变更

**修复步骤**:
1. 确认「消息防撤回」开关已打开
2. 查看Xposed日志，搜索 `[防撤回]` 关键词：
   ```bash
   adb logcat | grep "防撤回"
   ```
3. 如果日志显示「所有候选类名均未找到」：
   - 说明当前微信版本的关键类名已改变
   - 需要更新模块的候选类名列表
4. 临时解决方案：
   - 获取当前微信版本的撤回相关类名
   - 使用MT管理器/NP管理器反编译微信
   - 搜索 `onRevokeMsg` 或 `revoke` 关键词定位新类名
   - 在 `AntiRecallHook.kt` 中添加新的候选类名并重新编译

### 症状：开屏广告、朋友圈广告仍然出现
**原因**: 广告SDK更新了展示逻辑

**修复步骤**:
1. 检查日志中 `[开屏广告]` 或 `[朋友圈广告]` 的输出
2. 如果日志显示Hook成功但广告仍在：
   - 广告可能在Hook之前已经加载（时序问题）
   - 尝试在更早的时机Hook（Application.attach）
3. 如果日志显示Hook目标类未找到：
   - 微信更新了广告类名，需要在 `AdBlockHook.kt` 中添加新的候选类名

### 症状：某个功能在QQ上不生效
**原因**: QQ端的Hook实现较少（部分功能仅实现了微信端）

**修复步骤**:
1. 确认该功能在设置中是否标注了QQ支持
2. QQ端功能清单：
   - ✅ 广告净化（开屏、信息流、聊天广告）
   - ✅ 消息防撤回
   - ✅ 界面美化（气泡、红点、Tab）
   - ✅ 隐私增强（正在输入、已读状态）
   - ✅ 自动回复（基础支持）
   - ❌ 防删朋友圈（QQ空间机制不同）
   - ❌ 批量管理（需后续版本添加）
3. 未实现的功能可在后续更新中添加

---

## 五、LSPatch相关问题

### 症状：LSPatch修补后的APK无法安装
**原因**: 签名冲突（与原版微信签名不同）

**修复步骤**:
1. **必须**卸载原版微信后再安装修补版
2. 微信聊天记录备份：
   - 使用微信自带的「聊天记录迁移」功能先备份
   - 或使用钛备份/Swift Backup备份数据
3. 如果安装后闪退：
   - 可能是Android版本过高，LSPatch兼容性问题
   - 尝试降级LSPatch或使用Shizuku模式

### 症状：LSPatch后台被系统杀死，模块失效
**原因**: 系统省电策略杀死了LSPatch后台服务

**修复步骤**:
1. 在系统设置中为LSPatch：
   - 关闭电池优化
   - 允许自启动
   - 锁定后台任务
2. 对于MIUI/ColorOS等系统：
   - 开发者选项 → 「暂停执行已缓存的应用」→ 关闭
   - 省电策略 → 无限制
3. **终极方案**：使用「集成模式」打包

### 症状：LSPatch更新后模块失效
**原因**: LSPatch版本更新可能导致模块加载方式改变

**修复步骤**:
1. 重新在LSPatch中导入模块APK
2. 重新修补目标应用
3. 如果仍不生效，降级LSPatch到之前可用的版本

---

## 六、编译问题

### 症状：Gradle编译失败
**修复步骤**:
1. 确认JDK版本 >= 17：
   ```bash
   java -version
   ```
2. 确认Android SDK已安装（API 34）：
   ```bash
   # 检查SDK目录
   echo %ANDROID_HOME%
   ```
3. 清理Gradle缓存：
   ```bash
   gradlew clean
   ```
4. 如果 `XposedBridge` 类找不到：
   - 这是正常的，因为使用 `compileOnly` 依赖
   - 编译应该可以成功
5. 检查 `local.properties` 是否正确配置了SDK路径：
   ```
   sdk.dir=C\:\\Users\\xxxx\\AppData\\Local\\Android\\Sdk
   ```

### 症状：APK体积过大
**说明**:
- 正常体积约 2-5 MB（AndroidX库占用）
- 可使用 `minifyEnabled = true` 开启混淆减小体积
- 但混淆可能导致Xposed类查找失败，**建议不加混淆**

---

## 七、获取日志方法

### Xposed日志（最重要）
```bash
# 实时查看Xposed模块日志
adb logcat -s Xposed:V

# 只查看本模块日志
adb logcat -s MicroXEnhancer:V

# 过滤特定功能日志
adb logcat | grep "防撤回\|广告\|安全绕过"
```

### LSPatch日志
```bash
adb logcat -s LSPatch:V LSPosed:V
```

### 崩溃日志
```bash
adb logcat -b crash
# 或
adb logcat *:E
```

---

## 八、紧急恢复

如果模块导致微信完全无法使用：

### 方法1：通过adb卸载修补的微信
```bash
adb uninstall com.tencent.mm
# 然后重新安装原版微信
```

### 方法2：清除模块配置
```bash
adb shell run-as com.tencent.mm rm -rf /data/data/com.tencent.mm/shared_prefs/microx_enhancer_config.xml
```

### 方法3：在LSPatch中移除作用域
1. 打开LSPatch
2. 找到修补的微信
3. 移除「微X增强」的勾选
4. 重启微信（模块将完全不生效）

---

## 九、反馈问题模板

提交Issue时请提供以下信息：

```
### 设备信息
- 手机型号：
- Android版本：
- LSPatch版本：

### 目标应用信息
- 微信/QQ版本：
- 是从哪个渠道下载的：

### 模块信息
- 模块版本：
- 运行模式（本地/集成）：

### 问题描述
（详细描述问题表现）

### 日志
（附上 adb logcat 日志，特别是 MicroXEnhancer 和 Xposed 标签的日志）

### 已尝试的解决方案
（已执行的排查步骤）
```
