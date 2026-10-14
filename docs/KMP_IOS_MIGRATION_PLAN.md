# Nexio 课程表 · iOS（KMP）迁移档案

> **迁移已完成。** 本文不再是「计划」，而是**收尾档案**：现状快照、门禁命令、
> 架构约定、里程碑与踩过的坑。迁移期的路线图与执行记录已删除（细节见 `git show`）。

> ## 📏 维护纪律（改这份文档前先读）
>
> | ✅ 能加 | ❌ 不能加 |
> |---|---|
> | 数字刷新（规模表 / 门禁现状） | 「当时怎么想的」过程叙述 |
> | 新发现的**可复用**坑（一句话 + commit） | 已完成批次的逐条拆解 |
>
> - **已完成的工作只占「里程碑表」一行**：`commit` + 一句话。要看细节 `git show <commit>`。
> - **踩坑细节**写 `.workbuddy/memory/YYYY-MM-DD.md`，不写这里。
> - 里程碑表**只留最近 ~15 条**，更老的删掉（信息在 commit 里）。
> - ⚠ 多人协作仓库，提交前先 `git status` 对齐，别把别人的半成品扫进去。

---

## 1. 接手须知（30 秒版）

**代码状态**：`master`，迁移已完成。§5 里程碑表是最新快照。
🟡 冲刺期**多会话并发**改同一仓库（`mtzwidget/*` 常被另一会话占用，提交前 `git status` 对齐）。

**iOS 状态（2026-10-12 真机/模拟器验证通过）**：四个模块的 `iosArm64` +
`iosSimulatorArm64` **编译通过**；`:core` 跨平台测试在模拟器上 **116 用例 0 失败**（含 iOS 专属的 `NsFileAppFileTest` 7 个）；
`:ui-shared` 的 **`NexioShared.framework` 两个目标都链接成功**（`linkDebugFrameworkIos{Arm64,SimulatorArm64}`）。
原挡住 link 的 R10（miuix 部分 fork）**已解** —— `:miuix` 补成完整 fork（119 文件、自包含）。
**iOS 代码侧能力已齐**（A1 网络 / A2 偏好 / A3 文件三个 actual 都写好并编译通过）。
**第四十三批的主界面迁移已过运行时验证**（真主界面在 iOS 上跑起来了）。
**第四十四/四十五批（六项平台能力 + 今日助手）已过「构建 + 启动」验证**：
`xcodebuild` 端到端构建成功（含 Swift 壳与 `NexioShared.framework` 嵌入），
装进 iPhone 18 Pro Max 模拟器启动后**真课表界面完整渲染、无崩溃**。
⚠ 交互路径（选文件 / 相册 / 分享面板 / 定位授权）仍需人工点一遍 —— 无法自动点按。

**一句话现状**：数据层与 UI 框架层已 100% 跨平台；**主页双屏/排班/节假日/偏好/AI 导入/关于页/设置链
（含本体）/课表导入导出备份页/外观自定义页/教务导入（外壳+本体）/小部件引导/课程提醒/WebDAV/
课表导入导出备份路由/**全部 20 条路由**/AppNavHost/**CourseScheduleApp（真主界面）**已迁**；
`:ui-shared` 119 文件 / 54.9k 行；`:androidApp` 剩 49 文件 / 16.2k 行（全是留守：宿主装配 + 永不迁能力）。
**应用代码完成度约 92%**（口径见 §3）。**真正的「页面」「路由」「主界面」已全部迁完** ——
`androidApp` 只剩 `MainActivity`（838 行，宿主装配层）/`NexioApplication` 与永不迁的能力实现体。
**iOS 入口已换真主界面**（第四十三批：`NexioIosRoot` 主底座 = 真 `CourseScheduleApp()`，
临时路由导航台已删，注入自检降级为启动日志）。
**R2 序列化全部完成**（读+写）——备份路径 Gson 清零。**能力注入抽象已建 8 个**：DiagnosticRecorder/
ScheduleEffects/PlatformToast/FeatureLog/UpdateController/ReminderController/BackupFileController/
**ScheduleFileController**（SAF 选文件读 / 另存为）。
**androidPane 的 8 个留守 dest 已全部清零**，设置页本体 / 外观自定义页 / 教务导入网页外壳都已迁。
**iOS 入口已接真导航**（第三十二批：`AppNavHost` 迁 `:ui-shared`，iOS 与 Android 同一个导航根）。

🔴 **待真机验证**：教务导入整条链是**整段搬运**，编译期验证不了 —— ① `WebViewScreen` 网页本体
（PixelCopy 采样 / UA 切桌面版重取页 / 脚本注入 / Cookie 跨页保存）；② `EducationalImportScreen`
的脚本索引更新（`EduImportUpdater`：7 天节流 + 手动更新 Toast）。需走一遍完整导入流程 + 顶栏手动更新。

### 本机环境（macOS）

⚠ 没有全局 JDK，直接 `./gradlew` 会报 *Unable to locate a Java Runtime*
（`/usr/bin/java` 只是 macOS 的安装 stub），需带：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew …
```

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

`core/src/commonTest/`（11）+ `core/src/jvmTest/`（16）共 **291 个用例已入库**，0 失败。
跑法 `./gradlew :core:jvmTest --rerun-tasks`。
夹具 `jvmTest/resources/school_index.pb` 是**真实 248 校生产索引**（若仓库要公开需评估脱敏）。

---

## 2. 门禁（每次改完必跑）

```bash
# 默认态（不加任何 property）—— 红线
./gradlew :core:check :miuix:check :backdrop:check --offline
./gradlew :androidApp:assembleDebug                  # Android 零回归，不许破

# 拆开跑（等价）
./gradlew :core:compileKotlinLinuxX64 :core:compileTestKotlinLinuxX64  # commonMain/Test 平台中立
./gradlew checkKmpPurity                             # 已知 JVM 专有 API 静态扫描
./gradlew :core:jvmTest --rerun-tasks                # 291 个用例

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
# 出 framework（compile 绿 ≠ 能 link，这一条必须单独跑）
./gradlew -Pnexio.ios=true :ui-shared:linkDebugFrameworkIosSimulatorArm64
```

> ✅ 2026-10-11 实测（macOS 27 / Xcode 27 / Kotlin/Native 2.4.10）：
> 四个模块的 `iosArm64` + `iosSimulatorArm64` **全部编译通过**；
> `:core:iosSimulatorArm64Test` **116 个用例 0 失败**（在模拟器里真跑，含 iOS 专属 7 个）；
> `:ui-shared` 的 framework **两个目标都链接成功**（`NexioShared.framework`，静态，~267 MB）。

> ⚠ **wasmJs 门禁与 `check` 互斥**：`-Pnexio.wasm=true` 期间 Kotlin/Wasm 插件会在配置阶段
> 注册 Distributions 仓库，与 `FAIL_ON_PROJECT_REPOS` 冲突 → `check` 直接失败，**必须分两次跑**。
> ⚠ **PowerShell 里 `-P` 参数要加引号**：`.\gradlew.bat '-Pnexio.wasm=true' checkKmpWasmJs`。
>
> 🔴 **「能编过」必须问清楚是哪个目标能编过。** 踩过大坑：`:core` 一度只有 android + jvm
> 两个 **JVM** 目标，commonMain 从未被平台中立 stdlib 检查过，6 类 JVM 专有 API 共 13 处
> 一路绿灯，真正编 iOS 时会全部失败。

---

## 3. 模块现状

### 模块现状

| 模块 | 文件 | 行数 | 源集 | 状态 |
|---|---:|---:|---|---|
| `:core` | 116 (+28 测试) | 18.7k | common **56** / android 8 / ios 2 / jvm 8 / jvmShared 2 / native 6 / **linuxX64 1（门禁）** | **数据层 100%**；14 套跨平台能力 + R2 导入编解码 + ICS 解析 |
| `:backdrop` | 64 | 6.3k | common 54 / android 5 / skiko 5 | KMP 化；skikoMain 已过 **wasmJs + iOS 编译** |
| `:miuix` | **129** | 31.9k | common **98** / android 10 / skiko 9 | KMP 化；**完整 fork、自包含**（不再依赖官方 miuix-ui）；官方子库 **0.9.4** |
| `:ui-shared` | **129** | **57.4k** | common **108** / android 3 / jvm 3 / ios 5 | 路由 20/20 + **主界面 `CourseScheduleApp`** 全迁完（阶段 5 ✅） |
| `:desktopApp` | 1 | ~306 | jvm | 桌面客户端，能真跑并打包 |
| `:androidApp` | **49** | **15.5k** | android | **无待迁**；全是留守（宿主装配 + 永不迁能力）；单 Activity（Manifest 23→2） |

**`:core` 的 14 套跨平台能力**：`NexioLog` / `KeyValueStore`+`AppStorage` / `HttpService` /
`JsonSupport` / `PlatformInfo` / `AppFile`+`AppFiles` / `ioDispatcher`（不用 `Dispatchers.IO`，
它在 Native 上是 `internal`）/ `synchronizedOn`（Native 暂直通）/ `PlatformToast` /
`FeatureLog`（⚠ 别加 `inline`，跨模块 JVM target 21 vs 17 不一致会炸）/ `compareByChineseCollator` /
`DayScheduleResolver`（提醒时刻算法，第十五批）/ `ScheduleEffects`（课程变更的平台副作用出口，第十六批）/
**`ScheduleFileController`**（SAF 选文件读 + 另存为，第二十三批）。

### `androidApp` 49 文件的归属拆分（无「待迁」）

| 归属 | 文件 | 行数 | 说明 |
|---|---:|---:|---|
| **宿主装配层** | 2 | ~1.3k | `MainActivity`（838 行：Activity + Android 壁纸 helper + `AndroidCourseScheduleApp` 装配）· `NexioApplication` |
| **永不迁移** | **26** | **8.6k** | `reminder/` 10 · `widget/` 7 · `ui/web/` 4 · `shizuku/` 2 · `wearable/` 2 · `provider/` 1 |
| **留守能力实现体** | ~21 | ~6.3k | `AndroidSettingsScreen`（8 dest 装配）· `Android*Controller` 系列 · `ScheduleAppearance`（Bitmap/LruCache 本体 + 旧数据迁移）· `decodeWallpaperSampled` 等 · `TodayAssistant`（天气/定位）· `ApiCompat`/`ActivitySystemBars` |

**完成度**（引用时注明口径）：
- **只算应用代码**：已迁 `:core` commonMain 11.9k + `:ui-shared` commonMain 54.9k = 66.8k，
  androidApp 无待迁 ⇒ **100%（迁移完成）**；剩下的都是平台能力实现体，本就不过界
- **含 `:miuix` + `:backdrop`**：101.8k / 109k ⇒ **约 93%**（那两层是第三方 fork，通常不计）

### 迁移收尾状态

页面迁移已全部完成：共享层 20/20 路由 + `CourseScheduleApp` 全部落地 `:ui-shared`。

**剩余的 `android.*` 引用全部是「永不迁移」的留守归属**，不是待办：

| 目录 | 归属 |
|---|---|
| `reminder` / `widget` / `shizuku` / `wearable` / `provider` | 平台专有，永不迁（提醒时刻算法已抠进 `:core` 的 `DayScheduleResolver`） |
| `ui/web` | `AndroidWebHost` 本体，平台专有 |
| `ui/utils` | `CrashLogHelper` / `UpdateChecker` / `ApiCompat` 等本就平台专有 |
| `data/ScheduleAppearance` · `TodayAssistant` 等 7 个 Gson 文件 | 全在留守文件里，不阻塞迁移 |
| `res` → `composeResources` | 26 个 drawable 已迁，剩余 `R.drawable.*` 在 Android 侧页面 |

### ⚠️ 判断「平台专有」时要先分清**层面**

同一个「快照位图」问题，判断错层面就会把抽象建反：

- ✅ **Compose 层**（跨平台）：`rememberGraphicsLayer()` / `toImageBitmap()` —— CMP 一样支持。
- ❌ **Android 专有**：只有 `asAndroidBitmap()` → `Bitmap.createBitmap` 这一步**裁剪**
  （Compose 没有跨平台 crop API）。

所以页面签名里的快照类型应该用 **`androidx.compose.ui.graphics.ImageBitmap?`**
（跨平台类型，不需要 expect/actual），裁剪用 `Canvas.drawImageRect` 重绘
（`cropImageBitmapClamped`）。

---

## 4. 架构约定（迁移已完成，新代码沿用）

**页面 UI 一律放 `:ui-shared` commonMain**；只有 Android 专有「能力」留 androidApp，
通过注入（回调/槽位/接口）解耦。没有「页面留守」。真正只留 androidApp 的三类：

- **纯能力实现体**：`CrashLogHelper` + Android 包装（DiagnosticRecorder / UpdateController /
  ReminderController / BackupFileController / ScheduleEffects / PlatformToast 等）、
  `ApiCompat` / `ActivitySystemBars` / `MiuixVisualEffectsPolicy` / `UpdateChecker` /
  `UpdateInstaller`、`ScheduleAppearance` 本体（Bitmap/LruCache）、`ClassEndEffects` 震动/烟花实现
- **宿主层**：`MainActivity` / `AppRouteContent` / `NexioApplication`
- **WebView 内容宿主**：webview 本体（iOS 要 WKWebView；页面外壳仍迁 + 槽位注入）

⚠ **两条文件 I/O 分工线别混**：

- 「**应用自己的备份目录**」→ `BackupFileController`（固定目录、列表扫描、读/写/删）
- 「**用户选定的任意位置**」→ `ScheduleFileController`（导入时选一个文件读、导出时另存为新文件）
两者都是文件 I/O，但生命周期与失败文案完全不同，**不要合并**。

**已建的能力注入抽象（`:core` 接口 + Android 包装，8 个）**：DiagnosticRecorder / ScheduleEffects /
PlatformToast / FeatureLog / UpdateController / ReminderController / BackupFileController /
ScheduleFileController。新页面遇 Android 能力照这套模板抽接口。

---

## 5. 里程碑（已完成 · 倒序 · 只看一行，细节 `git show`）

| commit | 一句话 | 日期 |
|---|---|---|
| `afc40de6` | **第四十五批**：今日助手卡拆分 —— 卡片本体/纯逻辑（669 行）迁 `:ui-shared`，`androidApp/TodayAssistant.kt` 1047→330 行只剩定位/HTTP/缓存；天气与定位改为参数注入，烟花走 `fireworksOverlay` 槽位；iOS 接 CoreLocation + 共享 HttpService，`skycon` 英文枚举**必须转中文**（图标按中文匹配） | 10-12 |
| `dea8a3b3` | **第四十四批**：iOS 六项平台能力原生实现 —— ① 文件选择器 UIDocumentPicker ② PHPicker 选壁纸（必须 iOS 侧降采样）③ 分享课表（HttpService 上传 + skia 绘口令 + UIActivityViewController）④ `.onOpenURL` 接收外部分享（Compose `mutableStateOf` 存，否则不重组）⑤ 状态栏样式（Kotlin 持状态、Swift 调废弃全局 API）⑥ 检查更新去掉。附带补 iOS 原生 toast HUD | 10-12 |
| `ebc209ad` | **第四十三批**：`CourseScheduleApp` 迁 `:ui-shared` —— 真主界面跨平台，`MainActivity.kt` 6070→838 行（只留 `AndroidCourseScheduleApp` 装配层）；iOS 入口换真首页、删临时导航台。12 个注入参数承接 Android 能力。**位图 ImageBitmap 化后 `recycle()` 只能靠注入**（共享层无 recycle，22 处回收点走 `bitmapRecycler`） | 10-12 |
| `e32f494b` | **第四十二批**：外观存储跨平台化 —— `AppearanceStore`/`AppearanceStyle`/`AppearanceStyleCodec` 进 `:core`，`ScheduleAppearance` 降级为迁移桥；**枚举落盘是 ordinal 不是 name**（差点静默丢用户设置，7 个金样测试固化） | 10-12 |
| `ad0c2105` | **第四十一批**：壁纸缓存（`MainActivity.cachedWallpaper*` 三件套）下沉 `WallpaperStore`，companion 改只读转发；offset/scale **必须成对写** | 10-12 |
| `f64531d6` | **修三个崩**：① 两模块同包同名 `AppRouteContent.kt` → facade 类撞 `AppRouteContentKt`，dex 只留一个 → 全路由 NoSuchMethodError（共享层改名 `AppRoutes.kt`）② `viewModel()` 在 Kotlin/Native 无实现（新建跨平台 VM 工厂，iOS 进程级单例）③ iOS 根漏 provide `LocalAppRouter`。**三个都是编译期不报、运行期才炸** | 10-12 |
| `a6f04c7a` | 用户的应用图标接入（`nexioschedule.icon` + 工程同步） | 10-12 |
| `3c938827` | **第四十批**：壁纸层跨平台化 —— `WallpaperStore`（commonMain）+ `WallpaperBackend` 注入；Android actual 委托 `ScheduleAppearance` 零回归；iOS 走 skia（CMP 无 ImageBitmap⇄UIImage 双向转换），存 JPEG（文件名仍 `.webp`，按内容识别）；`AppFile` 补 `delete()`。⚠ iOS 字节序未真机验证 | 10-12 |
| `6091d8f2` | **第三十八批**：**最后两个路由** `CourseTimeSettingsRoute` / `CourseManageRoute` 迁 `:ui-shared` —— **Route 20/20 全部迁完**；新增跨平台 `cropImageBitmapClamped`（替 `Bitmap.createBitmap`）；`renderEffect` 换 `:backdrop` 的 `BlurEffect`；`BackHandler` 换 `:miuix` 的 `NavigationBackHandlerFor` | 10-12 |
| `1e66ed67` | **第三十七批**：课表导入/导出/备份三个路由迁 `:ui-shared`（四条 Android 能力注入 + 跨平台默认值）；**iOS 宿主接线 18/20 路由**，首页升级为「注入自检 + 路由导航台」 | 10-12 |
| `9140a7ad` | **第三十六批**：小部件引导 / 课程提醒 / WebDAV 三个路由壳迁 `:ui-shared`（平台能力全参数注入；测试通知回调传 null = 该端无此能力、不渲染按钮），Route 进度 **15/20** | 10-12 |
| `d23c3bb8` | **第三十五批**：`HolidaySettingsRoute` 迁 `:ui-shared` —— HttpURLConnection 改 `:core` HttpService（**该文件最后一个 java.net 触点清零**），Route 进度 **12/20** | 10-12 |
| `ea266cd8` | **第三十四批**：4 个带注入的文档壳路由（偏好/更新/本地备份/AI 导入）迁 `:ui-shared`，Route 进度 **11/20**；iOS 剪贴板注入 UIPasteboard | 10-12 |
| `846415f4` | **第三十三批**：5 个纯文档壳路由（更新日志/协议/隐私/交流/捐赠）迁 `:ui-shared`，Route 进度 7/20；iOS 入口同步接线 | 10-12 |
| `071d7883` | **第三十二批**：**`AppNavHost` 迁 `:ui-shared`**（navigationevent → `:miuix` 的 `rememberNavigationBack`；屏幕圆角 expect/actual）；**iOS 入口接真导航**（与 Android 同一个导航根） | 10-12 |
| `cf0d5ee5` | **第三十一批**：**R13 解** —— `miuix-preference` fork 进 `:miuix`（官方 artifact 三处移除）；iOS 入口首屏接真实页面 `AboutRoute`，**模拟器实跑通过** | 10-12 |
| `a1f7897d` | **第三十批**：宿主层开工 —— `EducationalImportRoute` / `AboutRoute` 进 `:ui-shared`（Android 侧改 `Android*` 注入包装）；修 Xcode 脚本 phase 未声明 Output Files（改了 Kotlin 不重新链接）；暴露 R13 | 10-12 |

> 🔑 八条**仍在生效**的经验（踩过才有的，别重蹈）：
> 1. **Windows 上就能编译 iOS klib**（`-Pnexio.ios=true :core:compileKotlinIosArm64`），
>    只有链接与运行需要 macOS。
> 2. **同名文件会静默失效**：`:androidApp` 曾有两个 `DialogContentLayout`，搬错那个 → 71 个
>    调用点的模糊与描边消失。改签名前先确认调用方 import 的是谁。
>    ⚠ 反向情形（第二十四批）：两模块的**同名顶层声明**合并进同一个包后，不再静默 ——
>    会硬报 `Conflicting overloads`（`SettingsScreen` 与 `TabletSettingsScreen` 各带一份
>    `parseDate` / `getDaysInMonth` 的「私有副本」）。合并同包时先搜一遍同名 helper。
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
> 8. **正则永远不如编译器完备** —— checkKmpPurity 是正则扫描，已知会漏两种写法，
>    两次都是「页面迁进 commonMain 真编 iOS 才炸」：
>    ① **全限定名**的 java API（规则是 `import java.*`）：`java.text.SimpleDateFormat` 一路绿灯
>       （`23a7a369` 在 WebDavSettingsScreen 抓到一处）；
>    ② **`String.format` 的扩展形式** `"%.1f".format(x)`（规则只认 `String.format(`）——
>       `bf06b6f9` 抓到后已补规则 `"[^"]*"\s*\.\s*format\(`。
>    ⇒ 迁移大文件时自己也要 grep 这两类字面量；**最终裁决权在 iOS 编译**，门禁绿 ≠ 跨平台干净。
> 9. **搬进 `:ui-shared` 的声明，只要宿主层还要用就不能是 `internal`** —— 第二十七批 `EduImportUpdater`（连同
>    `isUpdating`/`dataVersion`）跟着 Screen 一起搬，`:ui-shared` 内部全绿，只有 `:androidApp` 引用时才炸 9 处。
> 10. **`renderEffect` / `drawImage` / `BackHandler` 进了 commonMain 会换 API**（第三十八批）：
>     - 模糊别用 `androidx.compose.ui.graphics.rendereffect.*`（skiko 不在那）——
>       用 `androidx.compose.ui.graphics.{BlurEffect,TileMode}`，配 `:backdrop` 的 `isRenderEffectSupported()`；
>     - 裁剪别照抄 `DrawScope.drawImage(srcOffset/srcSize/dstSize)` —— `Canvas.drawImageRect`
>       是另一套签名（多一个 `dstOffset`，且 `paint` 必填）；
>     - `androidx.activity.compose.BackHandler` 是 Android 专有 → `:miuix` 的
>       `NavigationBackHandlerFor`（skiko 侧有 actual，iOS/桌面空实现）。
> 11. **两模块同包同名 `.kt` = facade 类撞名，编译全绿、运行全崩**（`f64531d6`）：
>     `:ui-shared` 与 `:androidApp` 都有 `ui/navigation/AppRouteContent.kt` → 两边
>     facade 都是 `AppRouteContentKt`，dex 打包只留一个 → 所有 `*Route` 运行期
>     `NoSuchMethodError`。共享层那份改名 `AppRoutes.kt` 即解（同包零 import 改动）。
>     ⇒ **往 `:ui-shared` 加文件前，先查 `:androidApp` 同包有无同名**（`dupscan.py` 只查
>     函数撞名，查不到 facade 撞名）；排查法 `dexdump base.apk | grep "Class descriptor.*Kt;"`。
> 12. **`viewModel()` 在 Kotlin/Native 上无实现**（`f64531d6`）：klib 有符号、编译过，
>     运行抛 `Factory.create 未实现`。共享层统一走 `:ui-shared` 的 `ViewModelFactory`
>     expect/actual（Android/jvm 原样 `viewModel()`，iOS 进程级单例 —— 别图省事改成
>     `remember{VM()}`，那会把 Activity 级共享降级成每页一实例）。
> 13. **外观枚举落盘是 ordinal 不是 name**（`e32f494b`）：`CardAppearance.kt` 顶部明文
>     「落盘格式 = ordinal，枚举顺序不能改」。Gson 默认按 name 序列化枚举，但本项目
>     存量数据是 ordinal —— 序列化实现换库时**以存量数据为准**，别照 Gson 默认行为抄。
>     金样测试 `AppearanceStyleCodecTest`（7 用例）守着。
> 14. **CMP iOS 图像编解码走 skia 不走 UIKit**（第三十~四十二批）：没有
>     `ImageBitmap ⇄ UIImage` 双向转换；`readPixels(IntArray)` 吃像素 stride、
>     `Bitmap.installPixels` 吃 ByteArray、`CGRect` 读字段要 `useContents{}`、
>     UIKit cinterop 要文件级 `@OptIn(ExperimentalForeignApi::class)`。
>     ⚠ iOS 壁纸存/读/缩放的字节序推断**未真机验证**。
> 15. **`ImageBitmap` 没有 `recycle()`**（`ebc209ad`）：`CourseScheduleApp` 原有 22 处
>     `recycleIndependentBitmap`（位图在 native 堆，低端机 OOM 主因），换 `ImageBitmap`
>     后跨平台侧无回收 API。解法 = **共享层留 `bitmapRecycler: ((ImageBitmap) -> Unit)?`
>     全局注入**，Android 宿主在装配层塞 `{ it.asAndroidBitmap().takeUnless(Bitmap::isRecycled)?.recycle() }`，
>     iOS/桌面留 null 交给 GC。**别直接删回收**——Android 位图峰值会顶爆（manifest 没开 largeHeap）。
>     ⚠ 与之配套的 `snap.copy(ARGB_8888)` 可删（`toImageBitmap()` 读回本就是独立快照）。

---

## 6. 🤝 给 iOS 开发者的交接清单

### A0. ⚠ 四项工程配置 —— 不做则 App 起不来 / 调不到 Kotlin 代码 / 后端的接口全废

**A0-1 `binaries.framework`** —— ✅ `:ui-shared` 已声明并**链接成功**（`NexioShared`，
`isStatic = true`，`iosArm64` + `iosSimulatorArm64`；App 的 Compose 界面全来自这里）。
`:core` 若也要给 Swift 用，按同一写法补。
⚠ 教训：**`compileKotlinIos*` 绿了不代表能 link** —— klib 图的版本/重名冲突只在链接期合账。

**A0-2 `Info.plist` 必须设 `CADisableMinimumFrameDurationOnPhone = true`** —— ✅ `iosApp/iosApp/Info.plist` 已设；否则 iOS 一启动就崩
（CMP 1.7.3 起强制检查，`enforceStrictPlistSanityCheck` 默认开）：

```xml
<key>CADisableMinimumFrameDurationOnPhone</key>
<true/>
```

**A0-3 Xcode 的 shared scheme 要提交** —— ✅ `iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` 已入库；否则 CI 报 `does not contain a scheme named YourApp`
（自动生成的在 `xcuserdata`，通常被 gitignore）。手动提交
`YourApp.xcodeproj/xcshareddata/xcschemes/`。

**A0-4 `Info.plist` 必须放行明文 HTTP（ATS）** —— ✅ 已设 `NSAllowsArbitraryLoads = true`（2026-10-12）。

```xml
<key>NSAppTransportSecurity</key>
<dict>
	<key>NSAllowsArbitraryLoads</key>
	<true/>
</dict>
```

不设的后果：**后端接口在 iOS 上全部静默失效**，日志是
`NSURLErrorDomain Code=-1022 … App Transport Security policy requires the use of a secure connection`：

| 调用点 | 地址 |
|---|---|
| `:core` `NoticeFetcher` / `StatsReporter` / `AppreciationFetcher`（2 条） | `http://182.92.193.223:3000/api/…` |
| `:ui-shared` `iosMain` `IosScheduleShare.kt` 的 `ShareCodeApi`（分享口令） | `http://182.92.193.223:3000/api/share` |
| 用户自填的 WebDAV 地址（多为局域网明文 HTTP） | 用户输入 |

⚠ **为什么不是「只给这个域名开例外」**：ATS 的 `NSExceptionDomains` **只匹配域名，对 IP 字面量不生效**
（[SO 实例](https://stackoverflow.com/questions/32695030/nsexceptionalallowsinsecurehttploads-not-working-for-ip-addresses)：iOS 9 起就是域名才管用），
所以后端只有 IP 时只能整体放行。这与 Android 侧
`androidApp/src/main/res/xml/network_security_config.xml` 的 `<base-config cleartextTrafficPermitted="true">`
是**同一条策略**（那边也是全局允许明文）。
**App Store 提交时需要在审核备注里说明理由**（2017 年起 ATS 例外要写 justification）。

⚠ 以后补 `IosWebHost`（WKWebView 的教务导入，学校站点基本是明文 http）时注意：
`NSAllowsArbitraryLoads` **不管 WebView 里的内容**，要另加 `NSAllowsArbitraryLoadsInWebContent = true`。

**想收紧成「ATS 全绿」的替代方案**（未采用，供以后决定）：后端其实有域名
`https://nexioschedule.icu`（解析到同一台 182.92.193.223，证书 Let's Encrypt、
TLS 1.3、四个端点实测都 200），把 `:core` 的 base URL 换成它即可，零 ATS 例外。
没直接换的原因：`androidApp/…/ScheduleExport.kt` 的 `ShareCodeApi` 与 `:core` 的
`NoticeFetcher.kt` 顶部都留了同一句作者注释「HTTPS(443) 在部分运营商网络下 TLS 握手被干扰
（Connection reset），改用 3000 直达后端」—— 那是**网络可达性**上的取舍，
iOS 用户会撞同一堵墙，所以没擅自改运行时地址。真要走这条路，建议用 expect/actual 按平台给 base URL
（Android/桌面维持 3000 直连，iOS 走域名 HTTPS），别五个地址一起换。

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
> `didReceiveResponse` 预检声明长度（超限直接 cancel、一个字节不收，与 Android 逐字对齐）。

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

### A3. `AppFile`（`NSFileManager`）—— ✅ 已实现且**运行时已验证**（`iosMain/NsFileAppFile.kt` + `iosTest/NsFileAppFileTest.kt`）

`AppFiles.init(root = NsFileAppFile(defaultDocumentsDir()), readAsset = { … })`。
契约：`writeBytes` **自动创建父目录**；`resolve(relative)` 以 `/` 分隔；读缺失文件**抛**（不返回空）；`defaultDocumentsDir()` 取
**Documents**（对应 `filesDir`；⚠ 别用 Caches / tmp，系统会在空间不足时清空）。
**落盘路径必须逐字保持**：`repo/index/school_index.pb`、`repo/schools/resources`（抄错 = 学校索引引导失效）。
配套 `FoundationBytes.kt` 提供 `NSData ⇄ ByteArray`（⚠ 空数组不能取 `addressOf(0)`，走 `null + 0` 分支）。

### A4. 启动注入 —— ✅ 已接线并验证（`ui-shared/iosMain/IosAppEntry.kt` 的 `bootstrapNexioIos()`）
> ⚠ 顶层函数名**别以 `init` 开头**：Kotlin/Native 导出时按构造器处理，Swift 侧 `has no member`。

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

1. ✅ 四个模块（`:core` / `:miuix` / `:backdrop` / `:ui-shared`）的 `iosArm64()` +
   `iosSimulatorArm64()` 都已声明，但**默认关闭**，`-Pnexio.ios=true` 开启。
   默认关的原因：声明后 `check` 会去解析 iOS klib 变体，而门禁是 `--offline` → 直接失败，
   把「Android 零回归」红线弄脏。命令见 §2。
2. ✅ `skikoMain` → `iosMain` 接线已做好，且**必须条件式**（target 默认关时源集不存在，
   无条件引用会配置失败）：
   ```kotlin
   jvmMain  { kotlin.srcDir("src/skikoMain/kotlin") }
   if (enableWasm) { wasmJsMain { kotlin.srcDir("src/skikoMain/kotlin") } }
   if (enableIos)  { iosMain    { kotlin.srcDir("src/skikoMain/kotlin") } }
   ```
3. ✅ 风险 R6 已排除：官方 miuix 5 个 artifact **都有 `-iosarm64` 变体**（Windows 上纯依赖解析
   即可验证）：`./gradlew -Pnexio.ios=true :miuix:dependencies --configuration iosArm64CompileKlibraries`
4. ✅ A0-1 `binaries.framework` **已声明并链接成功**（`:ui-shared`，`NexioShared` 静态，
   设备 + 模拟器两目标；第二十七批实测）
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
| iOS 文件层（`AppFile`） | ✅ **已实现，且 7 个用例在模拟器上真跑通**（第二十八批） |
| Windows 能否编译 iOS | ✅ **能编 klib，不能链接** |
| iOS 已可用 | 🟢 **真主界面已就位并跑通**（第四十三批：`CourseScheduleApp` 迁 `:ui-shared`，iOS 首页 = 真课表界面，运行时已验证）；与 Android 同一导航根（`AppNavHost`），**20/20 路由接线**；framework 链接全绿。文件选择器/选壁纸/分享/外部分享/状态栏/今日助手卡**均已原生实现**（第四十四/四十五批）。⚠ `IosWebHost`（WKWebView）未写 → 教务导入仍不可用；壁纸字节序仍未单独验证 |

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
  整段 `when` 分支要改）；其余是零散的 `ic_github`/`ic_gitee`、`theme_preview_light/night`、
  `ic_widget_*`、`ic_phone_pc` 等

**两条 CMP 变更，阶段 5 会用到**：

- **`LocalLifecycleOwner` 换包**：CMP 1.7.3 起移到 lifecycle 包。当前 0 处使用，阶段 5 大概率会用到
- **`@Preview` 用哪个包**：CMP 1.10.0 起统一，commonMain 直接用
  `androidx.compose.ui.tooling.preview.Preview`。**别按旧指南走 `org.jetbrains.compose` 那个**（已废弃）

> ✅ 第三十八批收尾时确认：**要迁的资源已全部随页面迁完** —— 20 个天气图标等已换成
> `composeResources`（`Res.drawable.*`）；`:androidApp` 剩余 `R.drawable.*` 只在永不迁的
> `widget/` / `reminder/` 里。
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

## 8. 迁移结果

| 阶段 | 内容 | 状态 |
|---|---|---|
| 0 | 技术预研与地基（backdrop KMP 化 / Miuix 改动量 / datetime 等价性） | ✅ |
| 1 | 纯逻辑下沉（`:core` 建立，模型 / 时间 / 节假日 / 仓储） | ✅ |
| 2 | 时间与序列化换血（`java.time` → kotlinx-datetime） | ✅ |
| 3 | 平台能力抽象 + 仓储下沉（8 套接口 + 启动注入） | ✅ |
| 4 | iOS 逻辑层贯通 —— 网络层 / UserDefaults / AppFile | ✅ |
| 5 | UI 首版（`:ui-shared` + iOS 壳，framework 链接成功） | ✅ 路由 20/20 + 真主界面已迁，运行时验证通过 |

**分层铁律**：`:core` 内禁止 `android.*`、`java.io.File`、`java.time.*`、反射式序列化。
`androidx.compose.*` 允许。由 `checkKmpPurity` + `linuxX64` 编译门禁强制。

## 9. 踩过的坑（机制值得记住）

**Gson → JsonSupport 换序列化**（R2）：4 条通道（单课表备份 / 分享码 / 教务导入 / 全量备份）
必须逐条 round-trip 回归，旧格式读取路径不能丢。

**fork 边界**：官方 miuix 0.9.4 与 fork commonMain 重叠 85 文件（30 个有改动、约 4414 行），
fork 另有 21 个官方没有的文件。两条纪律：

1. **从上游同步 miuix-ui 时必须把新文件一起搬进来** —— 自包含后缺文件会当场报
   `Unresolved reference`（这是好事，别用「加回官方 artifact」来消错）。
2. **不要在 fork 里改「别人会调的内部签名」**（这次是 `DialogContentLayout` 少了三个参数）——
   改了必须连带适配调用方。

**kotlinx-datetime 版本混用**：CMP material3 在 iOS 变体上依赖 0.7.x，会顶掉项目锁定的版本；
两版的 `LocalDate` 是不同类，链接期必炸。升级必须全项目一起升（详见 root `build.gradle.kts`）。

**`Info.plist` 缺键**：CMP 1.7.3 起强制 `CADisableMinimumFrameDurationOnPhone`，漏了**一启动就崩**。

**wasmJs 门禁与 `--offline` 互斥**：只要 wasmJs 目标存在于构建中，插件就在配置阶段注册
Distributions 仓库，与 `FAIL_ON_PROJECT_REPOS` 冲突 —— 所以该目标默认关闭、门禁独立跑。

**Java 名义上跨平台、实则 JVM 专有**：`String.format` 会按 Locale 输出数码字形（阿拉伯语 locale 下
`"08:00"` 变非 ASCII），`System.currentTimeMillis` 在 Native 上不存在。`checkKmpPurity` 拦这些。

**同包同名 fork 的静默失败**：Android dex 按类名去重，版本错配不报错、只在走某条路径时炸
（`NoSuchMethodError`）；Kotlin/Native 才是链接期硬错误。往 `:ui-shared` 搬文件前必须手工查撞名。


## 10. 红线原则

1. **不打断 1.6.x 开发。** 每阶段结束时 `:androidApp` 必须能正常编译、打包、发布。
   任何阶段中途停下都能照常发版。
2. **数据兼容不可谈判。** 存量 SharedPreferences 数据在每步之后都必须正确读出。
3. **每步可回退。** `:core` 出问题就退回单模块。
4. **先验证再推广。**
5. **不要让 fork 变枷锁。** Miuix fork 解决了定制问题，但会阻断跨平台。

> 安全网：`master` 上有永久标签 `backup/pre-merge-20261009`（合并前状态）。
> `git branch <名字> backup/pre-merge-20261009` 即可恢复 —— 标签不过期，比 reflog（90 天）可靠。
