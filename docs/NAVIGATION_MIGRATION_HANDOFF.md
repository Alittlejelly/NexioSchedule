# 交接文档：

> KMP 迁移已完成。本文只保留**长期仍然成立**的部分：导航架构、踩过的坑、数据层约定、门禁命令。
> 迁移期的分批清单、执行记录、进度快照已删除（细节见 `git show`）。
> 宏观口径与风险登记见 `docs/KMP_IOS_MIGRATION_PLAN.md`。



**单 Activity、单路由根**。页面一律放 `:ui-shared` commonMain（Android / 桌面 / iOS 共用），
只有平台专有「能力」留在宿主层，通过注入解耦。




## 数据层约定

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

## 门禁命令（每次收工必跑）

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