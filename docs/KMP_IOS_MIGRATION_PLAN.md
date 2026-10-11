# Nexio 课程表 · iOS（KMP）迁移计划

> ## 📏 维护纪律（改这份文档前先读）
>
> 本文档**只回答三件事**：① 现在在哪 ② 下一步做什么 ③ 红线是什么。
>
> | ✅ 能加 | ❌ 不能加 |
> |---|---|
> | 数字刷新（规模表 / 模块现状 / 风险表） | 「当时怎么想的」过程叙述 |
> | 新发现的**可复用**坑（一句话 + commit） | 已完成批次的逐条拆解 |
> | 下一步的顺序调整 | 调研过程、已被推翻的判断 |
>
> - **已完成的工作只占「里程碑表」一行**：`commit` + 一句话。要看细节 `git show <commit>`。
> - **踩坑细节**写 `.workbuddy/memory/YYYY-MM-DD.md`，不写这里。
> - 里程碑表**只留最近 ~15 条**，更老的删掉（信息在 commit 里）。
> - **全文上限 600 行**。超了就先砍「里程碑」和「阶段路线」，再砍对话式解释。
> - ⚠ 冲刺期会有**多个会话并发改同一个仓库**，提交前先 `git status` 对齐，别把别人的半成品扫进去。

---

## 1. 接手须知（30 秒版）

**代码状态**：`master` = **`23a7a369`**（2026-10-11）。工作区**干净**，动手前 `git status --short` 对齐。
🟡 冲刺期**多会话并发**改同一仓库。

**一句话现状**：数据层与 UI 框架层已 100% 跨平台；**主页双屏/排班/节假日/偏好/AI 导入/关于页/设置宿主外壳/
提醒设置页/更新设置页/本地备份页/课表导入导出备份页已迁**；`:ui-shared` 已迁入 93 文件 / 40.4k 行；
`:androidApp` 剩 54 文件 / 28.3k 行（其中待迁 29 / 20.2k）。
**应用代码完成度约 72%**（口径见 §3）。
**R2 序列化全部完成**（读+写）——备份路径 Gson 清零。**能力注入抽象已建 8 个**：DiagnosticRecorder/
ScheduleEffects/PlatformToast/FeatureLog/UpdateController/ReminderController/BackupFileController/
**ScheduleFileController**（SAF 选文件读 / 另存为）。
**androidPane 的 8 个留守 dest 已全部清零**（课表导入/导出/备份页是本轮最后一个）。
下一步是 **SettingsScreen 本体**（它只剩「androidPane 渲染留守页」这一条阻塞，现已解除）。iOS target 默认关闭。

### 本机环境（不看会白踩半天）

| 事项 | 说明 |
|---|---|
| PowerShell | **必须 pwsh 7**；5.1 会把中文输出弄成乱码 |
| `JAVA_HOME` | `D:\JDK` |
| `JAVA_TOOL_OPTIONS` | 跑 `java.exe` 前先 `Remove-Item Env:\JAVA_TOOL_OPTIONS`，否则 `-Dfile.encoding` 被污染 |
| Python | PATH 里**没有** `python`，用 `C:\Users\43908\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe` |
| 内嵌中文的 pwsh 脚本 | 存成 UTF-8 **带 BOM**，否则 pwsh 7 可能按 ANSI 读 |
| `.workbuddy/` | 在 `.gitignore` 里（本地长期笔记，换机器就没了） |

### 已定型、别推翻的决定

| 决定 | 原因 |
|---|---|
| 存储 / 文件用**接口 + 启动注入**，不用 expect/actual | 让 `:core` 的 commonMain 保持零平台实现，`linuxX64` 门禁才编得过 |
| **不引入 Ktor** | 任何现代 Ktor 都会把 `kotlinx-coroutines` 从 1.9.0 顶到 1.10.2+/1.11.0，而提醒·闹钟·同步·小组件全压在协程上。真要引入请**单独评估、单独提交** |
| 偏好文件名**不做集中常量表** | 手抄错名字 = 老用户数据静默读不出来（曾把 `holiday_settings` 误写成 `holiday_prefs`）。项目共 **17 个**偏好文件名 |
| Android actual 必须继续走 SharedPreferences / `java.io.File` | 换实现（如 DataStore）= 存量用户数据全读不出来。**评估过，不迁** |
| 不引入 `withHostTest {}` | 实测会让 8 个测试因 `Build.MODEL` 为 null 而失败 |
| `wearable/` 不动 | 不由用户维护 |
| 版本号由用户自己管 | 审查时不提醒 |

### 测试

`core/src/commonTest/`（11）+ `core/src/jvmTest/`（15）共 **256 个用例已入库**。
跑法 `./gradlew :core:jvmTest --rerun-tasks`（26 个测试类 / 0 失败）。
夹具 `jvmTest/resources/school_index.pb` 是**真实 248 校生产索引**（若仓库要公开需评估脱敏）。

---

## 2. 门禁（每次改完必跑）

```bash
# 默认态（不加任何 property）—— 红线
./gradlew :core:check :miuix:check :backdrop:check --offline
./gradlew :androidApp:assembleDebug                  # Android 零回归，不许破

# 拆开跑（等价）
./gradlew :core:compileKotlinLinuxX64 :core:compileTestKotlinLinuxX64  # commonMain/Test 平台中立
./gradlew checkKmpPurity                             # 已知 JVM 专有 API 静态扫描（当前 268 文件）
./gradlew :core:jvmTest --rerun-tasks                # 256 个用例

# 动了 commonMain / skikoMain 时额外跑（需联网 + PATH 有 Node）
./gradlew -Pnexio.wasm=true checkKmpWasmJs

# ── iOS（需 macOS + Xcode；首次会联网拉 iOS klib，**不能加 --offline**）──────
# ⚠ 与默认态 check **分开跑**：声明 iOS target 后 check 会去解析 iOS klib 变体，
#   离线门禁会失败（这也是 iOS target 默认关闭的原因）。
./gradlew -Pnexio.ios=true \
  :core:compileKotlinIosArm64 :miuix:compileKotlinIosArm64 \
  :backdrop:compileKotlinIosArm64 :ui-shared:compileKotlinIosArm64
# 模拟器目标（日常开发真正跑的）：把上面的 IosArm64 换成 IosSimulatorArm64
# 在 iOS 模拟器上真跑 :core 的跨平台测试（编译 + 链接 + 运行）
./gradlew -Pnexio.ios=true :core:iosSimulatorArm64Test
```

> ✅ 2026-10-11 实测（macOS 27 / Xcode 27 / Kotlin/Native 2.4.10）：
> 四个模块的 `iosArm64` + `iosSimulatorArm64` **全部编译通过**；
> `:core:iosSimulatorArm64Test` **109 个用例 0 失败**（在模拟器里真跑）。
> 🔴 唯一过不去的是 **link**（`:ui-shared:linkDebugFramework*`）—— 见风险 **R10**。

> ⚠ **wasmJs 门禁与 `check` 互斥**：`-Pnexio.wasm=true` 期间 Kotlin/Wasm 插件会在配置阶段
> 注册 Distributions 仓库，与 `FAIL_ON_PROJECT_REPOS` 冲突 → `check` 直接失败。
> **必须分两次跑**（先默认态 check，再 wasm）。
> ⚠ **PowerShell 里 `-P` 参数要加引号**：`.\gradlew.bat '-Pnexio.wasm=true' checkKmpWasmJs`。
>
> 🔴 **「能编过」必须问清楚是哪个目标能编过。** 踩过大坑：`:core` 一度只有 android + jvm
> 两个 **JVM** 目标，commonMain 从未被平台中立 stdlib 检查过，6 类 JVM 专有 API 共 13 处
> 一路绿灯，真正编 iOS 时会全部失败。

---

## 3. 现状与进度（2026-10-11 晚按 `23a7a369` 实测，本表为准）

### 模块现状

| 模块 | 文件 | 行数 | 源集 | 状态 |
|---|---:|---:|---|---|
| `:core` | 105 (+28 测试) | 17.9k | common **50** / android 8 / ios 2 / jvm 8 / jvmShared 2 / native 6 / **linuxX64 1（门禁）** | **数据层 100%**；14 套跨平台能力 + R2 导入编解码 + ICS 解析 |
| `:backdrop` | 64 | 6.3k | common 54 / android 5 / skiko 5 | KMP 化；skikoMain 已过 **wasmJs + iOS 编译** |
| `:miuix` | 108 | 28.2k | common **92** / android 9 / skiko 7 | KMP 化；公共组件全进 commonMain；官方子库 **0.9.4** |
| `:ui-shared` | **95** | **40.4k** | common **93** / android 1 / jvm 1 | 阶段 5 进行中（**只剩设置链本体 + 壁纸/WebView/宿主层**） |
| `:desktopApp` | 1 | ~300 | jvm | 桌面验证壳，能真跑 |
| `:androidApp` | **54** | **28.3k** | android | 待迁 **29 / 20.2k** + 留守 **25 / 8.1k**；单 Activity（Manifest 23→2） |

**`:core` 的 14 套跨平台能力**：`NexioLog` / `KeyValueStore`+`AppStorage` / `HttpService` /
`JsonSupport` / `PlatformInfo` / `AppFile`+`AppFiles` / `ioDispatcher`（不用 `Dispatchers.IO`，
它在 Native 上是 `internal`）/ `synchronizedOn`（Native 暂直通）/ `PlatformToast` /
`FeatureLog`（⚠ 别加 `inline`，跨模块 JVM target 21 vs 17 不一致会炸）/ `compareByChineseCollator` /
`DayScheduleResolver`（提醒时刻算法，第十五批）/ `ScheduleEffects`（课程变更的平台副作用出口，第十六批）/
**`ScheduleFileController`**（SAF 选文件读 + 另存为，第二十三批）。

### `androidApp` 54 文件的归属拆分

| 归属 | 文件 | 行数 | 说明 |
|---|---:|---:|---|
| **永不迁移** | **25** | **8.1k** | `reminder/` 10 · `widget/` 7 · `ui/web/` 3 · `shizuku/` 2 · `wearable/` 2 · `provider/` 1 |
| **待迁** | **29** | **20.2k** | `ui/activities` 2 · `ui/screens` 5 · `ui/utils` 12 · 其余 10 |

**完成度**（引用时注明口径）：
- **只算应用代码**：已迁 `:core` commonMain 11.1k + `:ui-shared` commonMain 40.4k = 51.5k，
  待迁 20.2k ⇒ **约 72%**
- **含 `:miuix` + `:backdrop`**：86k / 106k ⇒ **约 81%**（那两层是第三方 fork，通常不计）

### 剩余工作量

| 项 | 数量 | 备注 |
|---|---|---|
| `android.*` import | **281 处 / 53 文件** | 换口径重数（多在留守文件里：reminder 46 / widget 56 / ui-web 21 / provider 8 = 131 处本就永不迁） |
| `androidx.*`（不含 compose） | **26 类** | 同上口径 |
| `androidx.compose.*` | — | 跨平台，**不算阻塞** |
| ~~`java.time`~~ | ✅ 已完成 | 22 文件全换 |
| **`LocalConfiguration`** | ✅ **0 处（已清零）** | 换成 `screenWidthDp()` / `isTabletWidth()` |
| **`LocalContext`** | **46 处** | 仍是页面级阻塞之首，但已被 VM 解耦大幅削弱 |
| Gson（风险 **R2**） | **7 文件** | 清单见下 |
| `java.io` | 29 处 / 12 文件 | 多为导入导出 |
| `res` → `composeResources` | ✅ **26 个 drawable 已迁**；`R.drawable.*` 仍在少量页面 | 见 F 节 |
| ~~`androidx.navigationevent`~~（页面侧） | ✅ **4→1 文件** | 第十九批 `CourseDetail`/`CourseEdit`，第二十批 `TimeConfigEdit`；只剩 `AppNavHost`（导航骨架，随宿主改造处理） |
| ~~`String.format`~~ | ✅ **共享侧清零** | 第二十批在 TimeConfigEditScreen 抓到 17 处；共享文件进 commonMain 前必查（checkKmpPurity FORBIDDEN） |
| ~~`SimpleDateFormat` / `Calendar`~~ | ✅ **共享侧清零** | 第二十三批：`WebDavSettingsScreen` 与课表导出各抓到一处，改 `:core` 的 `formatDateTimeMinute` / `formatIcsTimestamp` |

**Gson 7 个文件**：`data/ScheduleAppearance` · `ui/screens/EducationalImportScreen` ·
`ui/screens/TodayAssistant` · `ui/utils/ScheduleExport` · `ui/utils/ScheduleImport` ·
`ui/utils/UpdateChecker` · `ui/web/AndroidBridge`
（`BackupAndMigrationScreen` / `LocalBackupScreen` 已随页面迁出 `:androidApp`）

**按目录看 `android.*`（决定先啃哪块）**

| 目录 | 处/文件 | 归属 |
|---|---:|---|
| `ui/activities` | 8 / 2 | **待迁** —— 只剩 `EducationalImportScreen` + `MainActivity`（宿主） |
| `ui/screens` | 35 / 5 | **待迁** —— 主战场：`SettingsScreen` / `CustomizeScheduleScreen` / `WebViewScreen` / `TodayAssistant` / `ClassEndEffects` |
| `ui/utils` | 69 / 12 | **逐个判归属**：`CrashLogHelper` / `UpdateChecker` / `UpdateInstaller` / `ActivitySystemBars` / `ApiCompat` 本就平台专有 |
| `ui/components` | 8 / 3 | 待迁（薄） |
| `ui/navigation` | 11 / 2 | 待迁（`AppNavHost` / `AppRouteContent`） |
| `ui/effects` | 3 / 1 | 待迁（薄，`MiuixVisualEffectsPolicy` —— 本就平台专有） |
| `data` | 6 / 1 | 待迁（`ScheduleAppearance`） |
| `reminder` | 46 / 10 | 永不迁移 —— ✅ 提醒时刻算法**已抠进 `:core`**（`DayScheduleResolver`，第十五批） |
| `widget` | 56 / 7 | 永不迁移 |
| `ui/web` | 21 / 3 | 永不迁移 |
| `shizuku` / `wearable` / `provider` | 6/2 · 2/2 · 8/1 | 永不迁移 |

### ⚠️ 判断「平台专有」时要先分清**层面**（本轮踩过）

同一个「快照位图」问题，判断错层面就会把抽象建反：
- ✅ **Compose 层**（跨平台）：`rememberGraphicsLayer()` / `GraphicsLayer.record {}` / `toImageBitmap()`
  —— CMP 一样支持，**桌面/iOS 会有同样的 screen 转场**。
- ❌ **Android 专有**：只有 `toImageBitmap().asAndroidBitmap()` → `Bitmap.createBitmap(x,y,w,h)`
  这一步**裁剪**（Compose 没有跨平台 crop API）。

所以页面签名里的快照类型应该直接用 **`androidx.compose.ui.graphics.ImageBitmap?`**
（跨平台类型，**不需要任何 expect/actual**），而不是包成平台 `Bitmap`。
> 我曾误判成「Activity 转场、桌面端永远 null」，建了个 `expect class SnapshotBitmap` 放 `:core`
> —— 双重错误：方向错（砍掉桌面端功能），位置也错（**`:core` 是纯数据层，不依赖 Compose**）。
> 已于 `820bd00` 撤销。**未迁页面各自迁移时再换契约类型**（契约边界随页面走）。

---

## 4. 下一步（收尾路线，按顺序推进）

> 🔧 **先用扫描器选批**（`.workbuddy-ai/tmp/scan_ui3.py`），每次迁移后跑 `.workbuddy-ai/tmp/dupscan.py`
> 查跨模块同名同包冲突（只查跨模块；同模块不同源集 expect/actual 同名是正常模式）。
> ⚠ 扫描器有已知误报（`androidx` 被当反向依赖符号），**报「0 阻断」也要先小规模试迁**。

**剩余待迁 29 文件 / 20.2k，其中真正的「页面」只剩 4 个**（其余是宿主层与平台专有实现体）。
迁移模式已成熟（页面上 commonMain + 能力 :core 接口注入 + Android 包装），剩余多为模板化。按顺序：

1. **SettingsScreen 本体**（1377 行）—— 迁 `d6e31bec` 之后它唯一的阻塞是「androidPane 渲染留守页」，
   而 8 个留守 dest **已全部迁完**（第二十三批清零）→ 现在可以直接搬：
   androidPane 槽位（`TabletSettingsScreen` 的参数）在宿主侧接线，页面本体进 commonMain。
   迁完 → **整条设置链贯通 commonMain**。
2. **CustomizeScheduleScreen**（1895 行，壁纸/快照重灾区）—— 卡 `snapshot`/`wallpaperBitmap` 是
   `android.graphics.Bitmap` + ScheduleAppearance 耦合。快照 crop 跨平台方案已定（见下「快照 crop」注），
   壁纸存储走 `ScheduleAppearance`（留守能力，注入）。
3. **WebViewScreen 外壳**（1171 行）—— iOS 要 WKWebView 重写 webview 本体，但**页面外壳迁 + 槽位注入
   webview 宿主**（同 todayAssistantCard 模式）。`EducationalImportScreen` 的 WebView 分支同理。
4. **宿主层**（最后）—— `AppNavHost`（navigationevent 最后残留）/ `AppRouteContent` / `MainActivity` /
   `NexioApplication`。这些是宿主壳，iOS 会重写入口，Android 保留。
5. **iOS 工程**（应用代码迁完后）—— A0 三项（binaries.framework / Info.plist CADisableMinimumFrameDurationOnPhone /
   shared scheme）+ 启动注入（A4）+ 真机验证（见 §6 交接清单）。

> ✅ **已完成**：`BackupAndMigrationScreen`（第二十三批，`23a7a369`）—— 见「里程碑」表。

> ✅ **快照 crop 跨平台化已解决**（第二十批）：页内裁剪用 `GraphicsLayer.record + drawImage`
> （⚠ record 便捷重载只在 DrawScope 内可见，协程里要显式传 `density`/`layoutDirection` 成员版）；
> 页外快照走「宿主裁好 → 页面收 `ImageBitmap?`」。两种场景都有落地方案，后续页面照抄。

### 🔑 迁移框架铁律（用户 10-11 拍板）

**页面 UI 一律迁 `:ui-shared` commonMain**（否则 iOS/桌面要重写一遍）；**只有 Android 专有「能力」留
androidApp，通过注入（回调/槽位/接口）解耦**。没有「页面留守」。真正只留 androidApp 的三类：
- **纯能力实现体**：`CrashLogHelper` + 7 个 Android 包装（DiagnosticRecorder/UpdateController/
  ReminderController/BackupFileController/ScheduleEffects/PlatformToast 等）、`ApiCompat`/
  `ActivitySystemBars`/`MiuixVisualEffectsPolicy`/`UpdateChecker`/`UpdateInstaller`、`ScheduleAppearance`
  本体（Bitmap/LruCache）、`ClassEndEffects` 震动/烟花实现、`TodayAssistant` 天气抓取器、`AndroidScheduleEffects`
- **宿主层**：`MainActivity` / `AppNavHost` / `AppRouteContent` / `NexioApplication`
- **WebView 内容宿主**：webview 本体（iOS 要 WKWebView；页面外壳仍迁 + 槽位注入）

**已建的能力注入抽象（:core 接口 + Android 包装，8 个）**：DiagnosticRecorder / ScheduleEffects /
PlatformToast / FeatureLog / UpdateController / ReminderController / BackupFileController /
**ScheduleFileController**（SAF 选文件读 + 另存为）。
新页面遇 Android 能力照这套模板抽接口。

⚠ **两条分工线别混**：
- 「**应用自己的备份目录**」→ `BackupFileController`（固定目录、列表扫描、读/写/删）
- 「**用户选定的任意位置**」→ `ScheduleFileController`（导入时选一个文件读、导出时另存为新文件）
两者都是文件 I/O，但生命周期与失败文案完全不同，**不要合并**。

**并行子代理流水线**（多批验证，2~3 路同时跑）：文件集不相交的批次可并行——每路改自己的页面+调用方；
**子代理不跑 gradle**（锁冲突），合并后统一门禁。父代理最后统一接线宿主调用点（避免多代理改同一文件）。
⚠ **纯数据类若声明在 android 专有文件里（如 AppearanceConfig 曾在 Gson 文件中），页面迁入后会
Unresolved** —— 先把数据类拆到 `:core`（依赖 Bitmap 的工厂改 companion 扩展留宿主）。

每批结束必须：`:androidApp:assembleDebug` 过 + 门禁绿 + **真机过一遍受影响的界面**。
（用户 10-11 拍板：安卓端回归已验证即放行；**桌面端实机点开推迟到全部迁移完毕后统一修**。）

---

## 5. 里程碑（已完成 · 倒序 · 只看一行，细节 `git show`）

| commit | 一句话 | 日期 |
|---|---|---|
| `23a7a369` | **第二十三批**：`BackupAndMigrationScreen` 迁 `:ui-shared` —— **androidPane 留守 dest 清零**；抽 `ScheduleFileController`；`parseIcsFile`→`:core`、`parseShare*`+`applyScheduleData`→`:ui-shared`；修 `WebDavSettingsScreen` 全限定名 `SimpleDateFormat` 漏报 | 10-11 |
| `4dc2139d` `9c2cba60` `33154249` | **R2 读侧 Gson→kotlinx**：导入通道 + 恢复门禁 + 扫描统计，真实备份 round-trip 回归 9 用例 | 10-11 |
| `b35aeaa6` | `AboutScreen` 进 `:ui-shared`；抽 `DiagnosticRecorder` 平台抽象；SettingsScreen 清 Calendar/UUID | 10-11 |
| `7ce095fe` | `TabletSettingsScreen` 外壳迁 `:ui-shared`（设置宿主两栏）；8 dest androidPane 槽位过渡 | 10-11 |
| `d6790c6b` | 槽位页 UI 迁移首批：`UpdateSettingsScreen`/`CourseReminderScreen` 迁 `:ui-shared`；抽 UpdateController/ReminderController 接口 | 10-11 |
| `0b755b75` | **R2 写侧完成**：备份 JSON encodeBackupJson（skip-nulls+pretty），**备份路径 Gson 清零** | 10-11 |
| `d6e31bec` | `LocalBackupScreen` 迁 `:ui-shared`；文件 I/O 能力注入首例（BackupFileController） | 10-11 |
| `c4373883` | `AiImportScreen` 进 `:ui-shared`（剪贴板注入）+ 备份夹具入库 | 10-11 |
| `6ca87004` | 第二十二批：`ShiftScheduleScreen`/`HolidaySettingsScreen` 进 `:ui-shared`（两路并行）；自由形态窗口/节假日回调注入 | 10-11 |
| `5fb7c04d` | 第二十一批：主页双屏 + 偏好设置进 `:ui-shared`（三路并行）；助手卡槽位注入；AppearanceConfig 拆 :core | 10-11 |
| `1e6b8bf3` | 第二十批：`SwitchScheduleScreen`/`TabletSwitchSchedulePane`/`TimeConfigEditScreen` 进 `:ui-shared`（并行子代理）；页内 crop 跨平台化 | 10-11 |
| `2aa310e1` | 第十九批：`CourseDetail`/`CourseEdit`/`TabletCourseManagePane` 进 `:ui-shared`；navigationevent 4→2 文件 | 10-11 |
| `465aec7e` | 桌面端补 `ViewModelStoreOwner` —— 修共享页 `viewModel()` 点开崩 | 10-11 |
| `8f7992b1` | 第十八批：`WebDavSettingsScreen` 迁移 + 修扫描器同包反向依赖漏报 | 10-11 |
| `820bd00` | 撤掉建错的 `SnapshotBitmap`：快照契约改用 Compose 的 `ImageBitmap` | 10-11 |
| `167ac846` | `CourseManageScreen` + 加 `lifecycle-viewmodel-compose`；修扫描器同包盲区 | 10-11 |
| `5cdf676` | **三个 ViewModel 全部去 Android 化**（窄接口 `ScheduleEffects`），进 `:ui-shared` | 10-11 |
| `6c251907` | 提醒时刻算法抠进 `:core`（`DayScheduleResolver`）+ `SettingsViewModel` 去 Android 化 | 10-11 |
| `6c251907` | 提醒时刻算法抠进 `:core`（`DayScheduleResolver`）+ `SettingsViewModel` 去 Android 化 | 10-11 |
| `53349ed` | 第十~十四批：资源迁移 + `AppreciateAuthor` / `AddCourseDialog` / `WidgetIntro` | 10-10 |

> 🔑 八条**仍在生效**的经验（踩过才有的，别重蹈）：
> 1. **Windows 上就能编译 iOS klib**（`-Pnexio.ios=true :core:compileKotlinIosArm64`），
>    只有链接与运行需要 macOS。
> 2. **同名文件会静默失效**：`:androidApp` 曾有两个 `DialogContentLayout`，搬错那个 → 71 个
>    调用点的模糊与描边消失。改签名前先确认调用方 import 的是谁。
> 3. **抽象会改变实例边界**：expect 封装把「1 个 state 喂两处」拆成「2 个函数各自 remember」
>    → 预测性返回进度恒为空。改动前后数一数底层平台对象被创建了几次。
> 4. **判断「平台专有」先分清层面**（Activity / Compose / 数据）。本轮把 Compose 层的 screen 转场
>    当成 Activity 转场，直接推错了整个抽象方向，还把它放进了不依赖 Compose 的 `:core`。
> 5. **进 commonMain 后才暴露的 JVM 专有 API**：任何大文件迁移前先自查这五类 ——
>    `Dispatchers.IO`→`ioDispatcher`、`synchronized`→`synchronizedOn`（⚠ 非 inline，
>    `?: return` 要写成 `?: return@synchronizedOn`）、`UUID.randomUUID()`→`randomUuidV4()`、
>    `@Volatile`→`kotlin.concurrent.Volatile`、**`String.format`→`padStart`**（第二十批在
>    一个文件里挖出 17 处，checkKmpPurity 会硬失败）。
> 6. **`GraphicsLayer.record` 的便捷重载只在 DrawScope 接收者内可见**：`layer.record(IntSize){}`
>    写在 `draw()` 里能编（backdrop 里的用法），写在协程里会解析到成员版
>    `record(Density, LayoutDirection, IntSize, block)` → 报 `IntSize but Density expected`。
>    协程里显式传 `density` + `LayoutDirection.Ltr`（`1e6b8bf3` 踩的）。
> 7. **KDoc 里别写「星号 + 斜杠 + 星号」**（例如描述 SAF 的通配 mime）：那正好是块注释结束符，
>    会提前闭合注释，报一堆 `Expecting member declaration`，位置还指在下一行的正常代码上。
>    要提就写「全匹配通配」（`23a7a369` 连踩两次）。
> 8. **checkKmpPurity 抓不到「全限定名」的 java API**：它的规则是 `import java.*` 正则，
>    所以 `java.text.SimpleDateFormat` / `java.util.Date` 写成全限定名时一路绿灯
>    （android/jvm 目标都能编过），**真编 iOS 才炸**。大文件迁移时除了查 import，
>    还要 grep 一遍源码里的 `java.` 字面量（`23a7a369` 在 WebDavSettingsScreen 抓到一处）。

---

## 6. 🤝 给 iOS 开发者的交接清单

### A0. ⚠ 三项工程配置 —— 不做则 App 起不来 / 调不到 Kotlin 代码

**A0-1 `:core` 必须声明 `binaries.framework`**，否则 Swift 侧无法 `import Shared`
（`compileKotlinIosArm64` 绿了**不代表能用**）：

```kotlin
// core/build.gradle.kts 的 kotlin { } 内
val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
if (enableIos) {
    iosArm64(); iosSimulatorArm64()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework { baseName = "Shared"; isStatic = true }
    }
}
```

**A0-2 `Info.plist` 必须设 `CADisableMinimumFrameDurationOnPhone = true`**，否则 iOS 一启动就崩
（CMP 1.7.3 起强制检查，`enforceStrictPlistSanityCheck` 默认开）：

```xml
<key>CADisableMinimumFrameDurationOnPhone</key>
<true/>
```

**A0-3 Xcode 的 shared scheme 要提交**，否则 CI 报 `does not contain a scheme named YourApp`
（自动生成的在 `xcuserdata`，通常被 gitignore）。手动提交
`YourApp.xcodeproj/xcshareddata/xcschemes/`。

### A1. `HttpService` —— ✅ 已实现（`iosMain/HttpService.ios.kt`）

`NSURLSession` + delegate 流式读，零第三方依赖。编译已过（Windows 上 iOS 双目标），
**运行时仍需真机**：WebDAV `PROPFIND`/`MKCOL`、脚本下载 8MB 截断、断网应抛异常而非被吞。

| 项 | 约定（别改签名） |
|---|---|
| 方法 | `get(url, headers, maxBytes)` / `post(url, body, contentType, headers)` / `request(method, url, body, contentType, headers)` |
| 返回值 | `HttpResult(code, bytes, truncated = false)`；`isSuccessful` = `code in 200..299`；`maxBytes > 0` 时超限返回 `truncated = true`（**不是抛异常**） |
| 异常 | **网络异常照常抛出，不要吞** —— 调用点普遍自带 `try/catch` |
| 响应体 | 一次性读入内存；`maxBytes` 必须在**读取过程中**生效（这正是选 delegate 而非 completionHandler 的原因） |
| 超时 | `HttpTimeouts(connectSeconds, readSeconds, callSeconds)`；`callSeconds <= 0` 表示不设整体超时 |
| 自定义方法 | WebDAV 用到 `PROPFIND` / `MKCOL` |

> 为什么是 delegate：`completionHandler` 要等整个响应体收完才回调，大响应会先吃满内存。
> 实现走 `NSURLSessionDataDelegate` 三件套，`didReceiveResponse` 预检声明长度（超限直接 cancel、
> 一个字节不收，**与 Android「声明超限 → 空字节 + truncated」逐字对齐**）。

### A2. `KeyValueStore` —— ✅ 已写好（`iosMain/UserDefaultsStore.kt`，待 macOS 验证）

| 项 | 约定（每条都对应一个 Android 行为，别想当然） |
|---|---|
| ⚠ **按名字分文件** | **17 个**偏好名，每个名字必须**独立**存储，**绝不合并** —— 合并 = 篡改存量数据的落盘位置 |
| ⚠ 名字来源 | 各业务文件自带的 `private const val PREFS = "…"`，**别建集中常量表** |
| `getStringSet` | 必须返回**副本**（Android 上就地改会抛 `UnsupportedOperationException`） |
| 类型不符 | 返回默认值（Android 侧是抛 `ClassCastException`、调用方用 `runCatching` 兜底，两种都要能工作） |
| `edit {}` | 语义 = 立即提交（等价 `apply()`） |
| `all()` | 返回**拷贝**（备份/迁移依赖它） |

> 🔴 **`all()` 的类型必须精确 —— iOS 侧最容易埋雷的一处**：
> `CourseRepository` 用 `when (value) { is Int -> putInt … is Long -> putLong … }` 复制课表，
> 而 plist 里所有数值都是 `NSNumber`，**读出来分不清 Int 与 Long**。若统一按 Long 还原，
> 复制课表会把 Int 键写成 Long → Android 侧 `getInt` 抛 `ClassCastException` → 被 `runCatching`
> 兜底成默认值 → **用户设置静默丢失**。
> 故 `UserDefaultsStore` 把类型记在独立域 **`suiteName.__kvtypes`**（必须独立：`all()` 会被遍历，
> 标记键同域会被当成业务数据复制/导出）。**别删这个域。**

**首次运行必须验证的三点**（编译器查不出来）：
1. Kotlin 的 `Int/Long/Float/Boolean` 传给 `setObject(_:forKey:)` 是否自动装箱成 `NSNumber`
2. `persistentDomainForName` 对自定义 suite 是否返回非空（`all()` 依赖它）
3. `raw is String` 对 plist 里的 `NSString` 是否成立

### A3. `AppFile`（`NSFileManager`）+ 启动注入 —— ⚠ 待写（约 30 行，照抄 `FileAppFile.kt`）

契约：`writeBytes` **必须自动创建父目录**；`resolve(relative)` 以 `/` 分隔；`root` 指向沙盒私有目录。
**落盘路径必须逐字保持**：`repo/index/school_index.pb`、`repo/schools/resources`（抄错 = 学校索引引导失效）。

### A4. 启动注入（3 个 `init`，缺任何一个都会在首次使用时抛异常）

```kotlin
// iOS 入口（AppDelegate / @main，越早越好；对应 NexioApplication.onCreate）
val version = NSBundle.mainBundle
    .objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown"

AppInfo.init(version = version)
AppStorage.init { name -> UserDefaultsStore(name) }            // A2，已实现
AppFiles.init(
    root = NsFileAppFile(defaultDocumentsDir()),               // A3
    readAsset = { path ->                                      // 内置学校索引引导
        // 失败要抛异常 —— SchoolRepository 会捕获、打日志、跳过引导（原逻辑如此）
        val p = NSBundle.mainBundle.pathForResource(path, null)
            ?: throw IOException("内置资源缺失: $path")
        NSData.dataWithContentsOfFile(p)!!.toByteArray()
    },
)
```

对照实现：`androidApp/.../NexioApplication.kt` 的 `onCreate` 开头三行。

### B. 已经能直接用

| 能力 | 位置 | iOS 现状 |
|---|---|---|
| 日志 `platformLog` | `core/src/nativeMain/.../PlatformLog.native.kt` | ✅ 已实现（println） |
| `ioDispatcher` | `core/src/nativeMain/.../IoDispatcher.native.kt` | ✅ `Dispatchers.Default` |
| 设备信息 `currentDeviceInfo()` | `core/src/nativeMain/.../PlatformInfo.native.kt` | ⚠ 只是 `Platform.osFamily` 兜底，**建议改成 `UIDevice`**；`sdkLevel` 保持 0 |
| 学校索引解析 | `core/src/commonMain/.../school/SchoolIndex.kt` | ✅ 手写 protobuf 解析，已用真实 248 校索引做过等价性对比 |
| `:miuix` / `:backdrop` 的 Skia 实现 | `*/src/skikoMain/` | ✅ wasmJs 编译已验证；运行层仍需真机 |

### C. 加 iOS target 的步骤

1. ✅ 目标已声明（`iosArm64()` + `iosSimulatorArm64()`），但**默认关闭**，`-Pnexio.ios=true` 开启。
   默认关的原因：声明后 `:core:check` 会去解析 iOS klib 变体，而门禁是 `--offline` → 直接失败，
   把「Android 零回归」红线弄脏。macOS 上首次需联网：
   `./gradlew -Pnexio.ios=true :core:compileKotlinIosArm64`
2. ✅ `skikoMain` → `iosMain` 接线已做好，且**必须条件式**（target 默认关时源集不存在，
   无条件引用会配置失败）：
   ```kotlin
   jvmMain  { kotlin.srcDir("src/skikoMain/kotlin") }
   if (enableWasm) { wasmJsMain { kotlin.srcDir("src/skikoMain/kotlin") } }
   if (enableIos)  { iosMain    { kotlin.srcDir("src/skikoMain/kotlin") } }
   ```
3. ✅ 风险 R6 已排除：官方 miuix 5 个 artifact **都有 `-iosarm64` 变体**（Windows 上纯依赖解析
   即可验证）：`./gradlew -Pnexio.ios=true :miuix:dependencies --configuration iosArm64CompileKlibraries`
4. ⚠ A0-1 `binaries.framework` **还没声明**
5. ⚠ A0-2 `Info.plist` 键**还没加**
6. 跑 `./gradlew -Pnexio.ios=true :core:compileKotlinIosArm64`，**不要只跑 android / jvm**

### D. 验证边界（请勿高估）

| 说法 | 真实程度 |
|---|---|
| `:core/commonMain` 平台中立 | ✅ **编译器验证**（`linuxX64` Native 目标通过） |
| `:miuix`/`:backdrop` commonMain + skikoMain 中立 | ✅ **编译器验证（wasmJs）** |
| 用 linuxX64 给 `:miuix`/`:backdrop` 做门禁 | ❌ **不可能**：CMP 1.12.0 不发任何 linux 目标 |
| skikoMain 能在 iOS 跑 | ⚠ 编译已验证，**运行未验证**（Wasm 与 Native 的 stdlib 仍有差异） |
| `KeyValueStore` iOS 实现 | ✅ 编译已验证；**运行时**三点待真机（A2） |
| iOS 网络层 | ✅ 已实现并编译通过；运行时待验证 |
| Windows 能否编译 iOS | ✅ **能编 klib，不能链接** |
| iOS 已可用 | ❌ **无 Xcode 工程**；A3 未写；A0-1 / A0-2 未做 |

**wasmJs 门禁**：同为非 JVM 目标（没有 `kotlin.jvm.*` 默认导入），能拦 `@Volatile` /
`synchronized` / `Dispatchers.IO` / `System.currentTimeMillis` 这类污染。
它抓到过 3 个静态检查查不出的问题（漏声明 `org.jetbrains:annotations`；未使用的
`asImageBitmap` import 在 wasmJs 上不存在；`skikoMain` 未接线 → 10 处 `no actual declaration`）。

### E. 提交前请跑这几条

```bash
./gradlew :core:compileKotlinLinuxX64 :core:compileTestKotlinLinuxX64
./gradlew checkKmpPurity
./gradlew :androidApp:assembleDebug
# 或合并：./gradlew :core:check :miuix:check :backdrop:check --offline
# 动了 skikoMain / commonMain：./gradlew -Pnexio.wasm=true checkKmpWasmJs（需联网）
```

### F. ⚠ 资源迁移（`res` → `composeResources`）—— 独立的隐藏工作量

| 官方提醒 | 本项目实际数量 |
|---|---|
| 目录 `res` → `composeResources` | drawable **38** + drawable-night **9** |
| 访问类叫 `Res` 不是 `R` | 约 **30 处** `R.drawable.*` 落在会进共享模块的页面 |
| XML 里 `@android:color` 换十六进制 | `themes.xml:10-11` × 2 |
| CMP 1.8.0 起 `androidLibrary` 要显式开资源 | 建 `:ui-shared` 时须写 `android { androidResources.enable = true }`，否则 `MissingResourceException` |

**按归属，真正要迁的是一小部分**：

- ✅ **不用迁**（留 `:androidApp`）：`widget/` + `reminder/` 约 **40 处** `R.mipmap.ic_launcher` /
  `R.layout.*` / `R.drawable.widget_*`（RemoteViews 与 Notification 是 Android 专有）
- ⚠ **要迁**：`TodayAssistant.kt:75-89` 天气图标 **20 个** `R.drawable.icon_*`（唯一有规模的，
  整段 `when` 分支要改）；`AboutScreen` `ic_github`/`ic_gitee`；`AppreciateAuthorScreen`
  `ic_anonymous_avatar`/`zanshangma`；`PreferenceSettingsScreen` `theme_preview_light/night`；
  `WidgetIntroScreen` `ic_miuix_logo`/`ic_widget_clock`/`ic_widget_location`；`WebViewScreen` `ic_phone_pc`

**两条 CMP 变更，阶段 5 会用到**：

- **`@Preview` 用哪个包**：CMP 1.10.0 起统一，commonMain 直接用
  `androidx.compose.ui.tooling.preview.Preview`。**别按旧指南走 `org.jetbrains.compose` 那个**（已废弃）
- **`LocalLifecycleOwner` 换包**：CMP 1.7.3 起移到 lifecycle 包。当前 0 处使用，阶段 5 大概率会用到

---

## 7. iOS 功能落差（先定哪些能要）

| 功能 | Android 实现 | iOS 方案 | 结论 |
|---|---|---|---|
| 上课/下课提醒 | AlarmManager + 精确闹钟 | 本地通知 `UNCalendarNotificationTrigger`，**滚动预约** | ⚠️ 降级 |
| 课前提醒（提前 N 分钟） | 同上 | 同上，占用通知配额 | ⚠️ 降级 |
| 上课自动免打扰 | AudioManager / 通知策略 | **系统禁止第三方控制** | ❌ 砍掉 |
| 下课烟花 / 常驻倒计时 | 前台计算 | CMP 内可算；退后台**无法自动触发** | ⚠️ 仅前台 |
| 桌面小部件 | AppWidget（7 文件 1.5k 行） | WidgetKit（Swift 重写） | 🔶 延后 |
| 课表/备份导入导出 | 文件 + FileProvider | `UIDocumentPicker` + 沙盒 | ✅ 需平台代码 |
| 壁纸 / 主题外观 | App 内背景 | 纯 CMP | ✅ 直接复用 |
| 教务导入 | WebView + `@JavascriptInterface` | WKWebView + `WKScriptMessageHandler` | 🔶 桥接层重写 |
| Shizuku / 小米穿戴 | AIDL / 穿戴 SDK | 无对应 | ❌ 砍掉 |
| 分享码 / 云同步 | OkHttp + 服务端 | 网络层可复用 | ✅ |
| 平板双栏布局 | `LocalConfiguration` | `isTabletWidth()` / `screenWidthDp()` | ✅ 已改造 |

**能拿到约 80% 功能**，砍掉的都是「系统级控制」类（免打扰、精确闹钟、小部件、穿戴）。

**两个必须接受的事实**：
1. UI 只能走 CMP —— 50.1k 行 Compose UI 用 SwiftUI 重写不叫迁移，叫写第二个 App
2. 提醒会实质性降级 —— `reminder/`（4.5k 行）整个建立在 `AlarmManager` + `BroadcastReceiver`
   之上，iOS **一个都没有**；只能预约本地通知，且**待触发通知上限 64 条**

**iOS 走 Kotlin/Native，不是 JVM**（这一条决定全部工作量）：Gson（运行时反射）❌ 不存在；
`java.time.*` ❌ 换 kotlinx-datetime；OkHttp ❌ 无 Native 版本；`java.io.File` ❌ 不存在；
精确闹钟 ❌ 完全不存在；后台执行代码 ❌ 不存在。

---

## 8. 分阶段路线

| 阶段 | 内容 | 状态 |
|---|---|---|
| 0 | 技术预研与地基（backdrop KMP 化 / Miuix 改动量 / kotlinx-datetime 等价性） | ✅ 完成 |
| 1 | 纯逻辑下沉（`:core` 建立，模型 / 时间 / 节假日 / 仓储） | ✅ 完成 |
| 2 | 时间与序列化换血（`java.time` → kotlinx-datetime） | ✅ 完成 |
| 3 | 平台能力抽象 + 仓储下沉（8 套 expect/接口 + 启动注入） | ✅ 完成 |
| 4 | iOS 逻辑层贯通（**先不碰 UI**）—— 网络层 / UserDefaults / AppFile | 🟡 A1 A2 已实现，**A3 待写**；Native 线程模型（R4）要等真机 |
| 5 | UI 首版（`:ui-shared` + iOS 壳） | 🟡 **进行中**：`:ui-shared` 已挂 iOS 目标并编译通过（2026-10-11）；**链接卡在 R10** |
| 6 | 全量 UI 迁移与功能补齐 | 长期轨道 |

**分层铁律**：`:core` 内禁止 `android.*`、`java.io.File`、`java.time.*`、反射式序列化。
`androidx.compose.*` 允许。

**目标架构**

```
:core          KMP 共享模块
  commonMain/  领域模型 · 时间计算 · 节假日 · 课程仓储 · 备份序列化 + 平台接口
  androidMain/ actual：SharedPreferences / java.io.File / OkHttp
  iosMain/     actual：NSUserDefaults / NSFileManager / NSURLSession
:ui-shared     CMP 共享 UI（commonMain 93 / android 1 / jvm 1 / ios 1）
:androidApp    Android 壳（保留 reminder / widget / shizuku / wearable）
:iosApp        Xcode 工程（待建）
```

---

## 9. 风险登记表

| # | 风险 | 影响 | 应对 |
|---|---|---|---|
| ~~R1~~ | ~~backdrop / AGSL 在 iOS 无法复现~~ | — | **已排除**：有现成 KMP 移植（SkSL，视觉等价） |
| **R2** | **Gson 替换破坏 4 条序列化通道** | **用户数据丢失** | 分层替换 + round-trip 回归 + 旧格式读取路径。**真实备份已到手**，配置要求已量出。**单课表备份 / 分享码 / 教务导入 / 全量备份**四条通道待做 |
| R3 | iOS 本地通知 64 条上限 | 提醒漏发 | 滚动预约策略，阶段 0 原型验证 |
| R4 | Kotlin/Native 线程模型撞全局单例 | 崩溃 / 状态错乱 | 阶段 4 专项验证，**要等真机** |
| R5 | `SimpleDateFormat` → kotlinx-datetime 行为偏移 | 日期静默算错 | ✅ 已用逐点对照用例验证语义等价 |
| ~~R6~~ | ~~Miuix 是 fork 的 `-android` 变体~~ | — | **已排除**：官方 5 个 artifact 都有 `-iosarm64` 变体 |
| ~~R6b~~ | ~~fork 的 `miuix-ui` 在 iOS 侧缺 actual~~ | — | **已排除**（2026-10-11 真编确认）：`:miuix` 的 `iosArm64` / `iosSimulatorArm64` 编译通过 |
| R6c | wasmJs 门禁与 `check` 互斥 | 门禁漏跑 | 已知限制，**必须分两次跑** |
| R6d | iOS 首启即崩（`Info.plist` 缺键） | 白屏 / crash | CMP 1.7.3 起强制检查，建 Xcode 工程时一并补 |
| R7 | 云端分享白名单未同步 | 新字段静默丢弃 | 改客户端字段必须同改 `server/index.js` 并重新部署 |
| R8 | UI 迁移周期过长，拖垮主线开发 | 项目停滞 | 严格按批次，每批次结束 `:androidApp` 仍可发版 |
| R9 | ~~OkHttp 在 iOS 不可用~~ | 低 | 改法已修订：自建 `HttpService`，Android/JVM 复用 OkHttp，Native 已实现 |
| **R10** | **`:miuix` 是 miuix-ui 的「部分 fork」，与官方 artifact 同包同名** | **iOS 无法链接**（编译能过）；**Android 侧已埋 NoSuchMethodError 隐患** | 见下方专条 |
| R11 | `kotlinx-datetime` 在 iOS 变体被 CMP material3 顶到 0.7.1 | 同一二进制两个版本 → 链接必炸 | ✅ 已在根 `build.gradle.kts` force 回 0.6.2；0.7 升级要**全项目一起做** |
| R12 | checkKmpPurity 抓不到「全限定名」java API | iOS 编不过 | ✅ 已由 `:ui-shared` 的 iOS 编译兜住；大文件迁移时仍要 grep `java.` 字面量 |

### R10 详述 —— 当前唯一挡住 iOS 产物的东西

2026-10-11 首次真链接 `:ui-shared` 的 iOS framework 时暴露：

```
e: Compilation failed: IrPropertySymbolImpl is already bound. Signature:
   top.yukonga.miuix.kmp.basic/top_yukonga_miuix_kmp_basic_ButtonDefaults$stableprop
```

**成因**：官方子库（`miuix-preference` 等）自己会传递依赖 `top.yukonga.miuix.kmp:miuix-ui`，
而本仓库把 miuix-ui fork 成了 `:miuix` 模块 —— **同包同名**。两边的 klib 里都有
`top.yukonga.miuix.kmp.basic.ButtonDefaults` 这类声明：

| 平台 | 表现 |
|---|---|
| Android / JVM | 类路径按名去重，悄悄用其中一个 —— **所以一直没暴露** |
| Kotlin/Native | 链接期**硬错误**（IR 符号重复绑定） |

**已实测排除的两条捷径**：

1. ❌ `exclude(group="top.yukonga.miuix.kmp", module="miuix-ui")` —— fork 是**部分覆盖**，
   缺 `window/*`（4 个）、`theme/DynamicColors`、`layout/BottomSheetContentLayout`、
   `layout/CascadingListPopupLayout`、`utils/PagerGestureUtils` 等 **14 个 commonMain 文件**
   + 1 个 androidMain + 2 个 skikoMain，全靠官方 artifact 兜着；排掉后 `:miuix` 自己编不过。
2. ❌ 从 Maven 拉官方 `miuix-ui-0.9.4-sources.jar` 把这 14 个文件补进 fork —— fork 改过
   内部 API（如 `DialogContentLayout` 去掉了 `maxWidth/largeScreen/cornerRadius` 参数），
   官方那些文件按官方签名调用 → `Redeclaration` / `Conflicting declarations` / 参数不匹配。

**量化**（`miuix-ui` 0.9.4 官方源码 vs fork，commonMain）：
重叠 85 文件、**30 个文件有改动、差异合计约 4414 行**（最大 `basic/ListPopup.kt` 1200 行）；
fork 另有 21 个官方没有的文件。→ **这是深度分叉，不是轻量 patch**。

**两条出路**（都改架构，需拍板）：

| 方案 | 内容 | 代价 |
|---|---|---|
| A. 补成完整 fork | 补齐 14 个文件 + 逐个适配 fork 改过的内部 API；之后删掉对官方 miuix-ui 的依赖 | 一次性的适配工作；**同时消掉 Android 侧隐患**（见下） |
| B. 放弃 fork | 用纯官方 miuix，把 fork 的定制改成上游 PR / 本地覆盖层 | 30 个文件的定制要重新找落点 |

**顺带发现的 Android 隐患**（同一根因，暂未爆发）：官方 0.9.4 的 `WindowDialog` 调
`DialogContentLayout(maxWidth=…)`，运行时被 fork 的旧签名覆盖 —— 走到那条路径会
`NoSuchMethodError`。方案 A 能一并消掉。这也正是红线 §10.5「不要让 fork 变枷锁」说的情形。

---

## 10. 红线原则

1. **不打断 1.6.x 开发。** 每阶段结束时 `:androidApp` 必须能正常编译、打包、发布。
   任何阶段中途停下都能照常发版。
2. **数据兼容不可谈判。** 存量 SharedPreferences 数据在每步之后都必须正确读出。
3. **每步可回退。** `:core` 出问题就退回单模块。
4. **先验证再推广。**
5. **不要让 fork 变枷锁。** Miuix fork 解决了定制问题，但会阻断跨平台。

> 安全网：`master` 上有永久标签 `backup/pre-merge-20261009`（合并前状态）。
> `git branch <名字> backup/pre-merge-20261009` 即可恢复 —— 标签不过期，比 reflog（90 天）可靠。
