# 交接文档：单宿主导航改造 + 数据层 KMP 收口

> KMP 迁移已完成。本文只保留**长期仍然成立**的部分：导航架构、踩过的坑、数据层约定、门禁命令。
> 迁移期的分批清单、执行记录、进度快照已删除（细节见 `git show`）。
> 宏观口径与风险登记见 `docs/KMP_IOS_MIGRATION_PLAN.md`。

## 一、导航架构（已定型，别推翻）

```
MainActivity.setContent
└─ CourseScheduleTheme
   ├─ CompositionLocalProvider(LocalAppRouter provides router)   ← 最外层 provide
   │  └─ Box
   │     ├─ CourseScheduleApp(...)        ← 【常驻底座】永远组合，不参与路由 save/restore
   │     ├─ AppNavHost(router) { AppRouteContent(router, it) }   ← 子页叠加层（栈空=不渲染）
   │     └─ if (!privacyAgreed) PrivacyConsentScreen(...)
   └─ LaunchedEffect(pendingRoute) { router.navigate(it) }       ← 消费 Intent 深链
```

**单 Activity、单路由根**。页面一律放 `:ui-shared` commonMain（Android / 桌面 / iOS 共用），
只有平台专有「能力」留在宿主层，通过注入解耦。

## 二、踩过的坑（每条都真实付过学费）

1. **能编译 ≠ 能用 —— CompositionLocal 未提供只在运行时崩**
   「课表备份页闪退」就是这么来的：`BackupAndMigrationScreen` 读 `LocalAppRouter`，
   但当时它还被残留的 `BackupAndMigrationActivity` 渲染，那个 Activity 没 provide，
   默认值是 `error(...)` → 一点开就抛。
   **教训**：给 Screen 加「读某个 CompositionLocal」前，排查**所有**渲染它的路径
   （路由 / 平板 pane / 残留 Activity / 预览）。

2. **主界面必须是常驻底座，不能进路由**
   若把 `CourseScheduleApp` 塞进 `AnimatedContent` + `SaveableStateHolder`，
   `rememberSaveable` 能救回来，但 `remember { mutableStateOf }` 救不回来
   （pager 位置 / 壁纸映射 /…），丢了就是「切到设置再回来 pager 位置变了」。

3. **返回上一页滚动状态丢失** → `AppNavHost` 里必须用 `SaveableStateHolder`：
   `stateHolder.SaveableStateProvider(route.id) { content(route) }`。
   但**语义要自己收口**：holder 只在「在栈期间」有用（整栈常驻保住了栈内页），
   一旦出栈，组合销毁时 holder 会把那一层的 `rememberSaveable` 全存下来，
   重开同一页就回到上次位置。`AppRouter` 允许同一路由重复入栈，用户要的明明是
   「在栈里保状态、出栈即重置」（二级页保一级、三级页保一级+二级）。
   **出栈当帧就 `stateHolder.removeState(key)`**，别等退出动画播完 ——
   中断路径（出场没播完又 push 了别的页）会让那一层提前离开组合，
   那时 `SaveableStateHolderImpl` 里的 registry 已经摘掉、`onDispose` 不会再写回，想清也清不掉。
   key 要自己复算（`exitLayerKey`）且**必须与 `layerKeys` 完全一致**，
   否则销掉的是另一个 key，出栈页的滚动位置照样被恢复。

4. **转场别用 fadeIn/fadeOut**：子页全屏不透明，转场带透明度会让主界面透出来
   （用户报「动画那么奇怪」）。只滑不淡。

5. **同包同名 fork 会静默失败**：Android dex 按类名去重，版本错配不报错、只在走某条路径时炸
   （`NoSuchMethodError`）；Kotlin/Native 才是链接期硬错误。往 `:ui-shared` 搬文件前
   必须手工查撞名 —— 编译全绿、dex 只留一个类。

6. **`remember { mutableStateOf }` 存不了跨进程状态**：iOS `.onOpenURL` 收到外部分享后
   必须写进 Compose state，否则不重组。

7. **Manifest 别用脚本批量删**：改单 Activity 时容易把 receiver / permission 属性行
   当孤儿一起删掉。

## 三、数据层约定

- 存储 / 文件用**接口 + 启动注入**，不用 expect/actual —— 让 `:core` 的 commonMain
  保持零平台实现，`linuxX64` 门禁才编得过。
- **不引入 Ktor**：任何现代 Ktor 都会把 `kotlinx-coroutines` 从 1.9.0 顶到 1.10+，
  而提醒·闹钟·同步·小组件全压在协程上。真要引入请单独评估、单独提交。
- Android actual 必须继续走 SharedPreferences / `java.io.File`：换实现（如 DataStore）
  = **存量用户数据全读不出来**。已评估，不迁。
- 偏好文件名**不做集中常量表**：手抄错名字 = 老用户数据静默读不出来。
- 序列化从 Gson 换成 `JsonSupport` 后，4 条通道（单课表备份 / 分享码 / 教务导入 / 全量备份）
  必须逐条 round-trip 回归。
- `:core` 内禁止 `android.*`、`java.io.File`、`java.time.*`、反射式序列化；
  `androidx.compose.*` 允许。由 `checkKmpPurity` + `linuxX64` 编译门禁强制。

## 四、门禁命令（每次收工必跑）

```bash
# 默认态 —— 红线
./gradlew :core:check :miuix:check :backdrop:check --offline
./gradlew :androidApp:assembleDebug
./gradlew :core:jvmTest --rerun-tasks

# 动了 commonMain / skikoMain 额外跑（需联网 + PATH 有 Node）
./gradlew -Pnexio.wasm=true checkKmpWasmJs

# iOS（需 macOS + Xcode；首次联网拉 iOS klib，不能加 --offline）
./gradlew -Pnexio.ios=true \
  :core:compileKotlinIosArm64 :miuix:compileKotlinIosArm64 \
  :backdrop:compileKotlinIosArm64 :ui-shared:compileKotlinIosArm64
./gradlew -Pnexio.ios=true :ui-shared:linkDebugFrameworkIosSimulatorArm64
```

⚠ macOS 没有全局 JDK，需 `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`。

⚠ `wasmJs` 门禁与 `--offline` 互斥（Kotlin/Wasm 插件在配置阶段注册 Distributions 仓库，
与 `FAIL_ON_PROJECT_REPOS` 冲突），所以 wasmJs 目标默认关闭、门禁独立跑。