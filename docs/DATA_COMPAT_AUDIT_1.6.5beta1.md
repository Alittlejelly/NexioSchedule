# 1.6.5beta1 → 当前代码 数据兼容性审查

**审查对象**：`867efeb1`（v1.6.5beta1，2026-10-08 19:09）→ `133b3cc9`（master HEAD）
**差异规模**：223 个提交；`app/` → `androidApp/` + `core/commonMain` 模块重整
**审查目标**：老用户覆盖安装新版后，本地数据是否可能异常丢失

**结论：未发现会导致数据丢失的改动。** 存储引擎、文件名、键名、数据模型、备份格式逐项对齐，
且删除类逻辑在新版比 beta1 更保守。发现 1 个测试覆盖缺口（已补）与 1 个发版配置问题（需手动处理）。

---

## 一、存储底座：未变

| 项 | beta1 | HEAD | 判定 |
|---|---|---|---|
| applicationId | `com.haooz.chedule` | `com.haooz.chedule` | ✅ 未变，数据目录不变 |
| 存储引擎 | SharedPreferences | SharedPreferences（`SharedPreferencesStore` 原样包装） | ✅ 未换引擎 |
| 私有文件根 | `filesDir` | `AppFiles.root = filesDir` | ✅ 未变 |

`SharedPreferencesStore.kt` 里明确写了红线注释（不得改 DataStore / Room / 自建文件），实现也确实是
薄包装，`getString` / `edit` / `getStringSet` 语义与平台一致。

## 二、偏好文件名：17 个全保留

脚本抽取两版全部调用点（含常量溯源）后比对：

| beta1 的 17 个文件 | HEAD |
|---|---|
| `course_schedule_prefs`、`app_preferences`、`app_theme_prefs`、`appearance_settings`、`course_reminder_prefs`、`holiday_settings`、`notice_prefs`、`stats_prefs`、`edu_import_prefs`、`update_settings`、`weather_prefs`、`webdav_config`、`crash_log_prefs`、`countdown_state`、`day_change_state`、`reminder_alarm_registry`、`reminder_sent_history` | **全部存在，名字逐字未变** |

- 后 5 个（`crash_log_prefs` / `countdown_state` / `day_change_state` / `reminder_alarm_registry` /
  `reminder_sent_history`）在 HEAD 里仍由 Android 侧直连 `getSharedPreferences` 使用，未迁到
  `AppStorage`，属正常，不是丢失。
- HEAD 新增 2 个：`web_forward_hosts`（新功能）、`ios_smoke_test`（iOS 专用），均为新数据。

## 三、键名与数据模型：逐字一致

- `CourseRepository` 28 个 `KEY_*` / `PREFS_NAME` 常量：完全一致。
- `Course` 数据类：17 个字段，名称与**顺序**完全一致（顺序影响 Gson 反序列化语义）。
- `TimeConfig`：`TimeConfig`(25) / `TimeRoutine`(23) / `SpecialBlock`(5) / `SpecialItem`(4) 全部一致。
- `HolidayManager` 存储常量、`PrivacyConsent`、`NoticeFetcher`、`ScriptRepository`、`ScheduleBackup`
  （`webdav_config` 4 键）全部一致。
- 节假日侧曾报缺 `schema_version` / `rules` 两个常量 —— 复查为误报：它们随
  `TeachingWeekReorganization` 拆到独立文件，值未变。

## 四、序列化：Gson → 手写 codec，语义对齐（重点项）

`core/commonMain` 不能用 Gson，改为 `ScheduleCodec` 手写编解码。核对结果：

- `Course.toJsonMap()` 的 17 个字段名 = Gson 反射产物字段名。
- `scheduleJson` 配置 `encodeDefaults=true`、`serializeNulls` 语义由 `toJsonElementWithoutNulls`
  递归实现（对象字段 null 省略、数组元素 null 保留），与 Gson 一致。
- `JsonSupport` 复刻 org.json 取值语义（数字归一 Double、显式 null 当无值、不抛异常）。
- `sanitizeCourses` / `coursesJsonLooksValid` / `getAllCourses` 逻辑与 beta1 **逐字相同**。

实测验证见第五节。

## 五、测试覆盖缺口（已修补）

**发现**：`ScheduleCodecGsonParityTest` 里 6 个最关键的对拍用例（「真实 courses 解码结果与 Gson
完全一致」等）依赖 `C:/Users/43908/Downloads/全部备份_20261009_155825.json`。该文件**不在仓库**，
本机与 CI 上都没有，这些用例走 `requireBackupOrSkip()` **静默自跳过**——即这条最重要的兼容红线
此前从未被真正验证过。

**处理**：新增 `core/src/jvmTest/.../GsonParitySyntheticTest.kt`（9 条，不依赖任何外部文件），
用本机现场生成的 Gson 产物做等价对拍：

- Gson 序列化的课程 JSON 能被新解码器原样读回 ✅
- 新编码结果与 Gson 语义一致 ✅（含中文、`& < > ' "` 转义）
- 课程 / time_config / schedule_names / schedule_folders 往返稳定 ✅
- null 字段省略（serializeNulls=false）✅
- **字段顺序被打乱（R8 重排）仍能正确解码** ✅
- 缺字段的老 JSON 不会静默变空课表 ✅

**验证结果**：

```
:core:jvmTest    314 tests, 0 failures, 0 errors   （原 305 + 新增 9）
:androidApp:assembleDebug   BUILD SUCCESSFUL
```

## 六、删除类逻辑：新版更保守

唯一会删用户数据的 `pruneOrphanTimeConfigsIfNeeded`（清理孤儿作息配置）在 HEAD 新增了两道保险：

1. 只要有课表还没写下显式绑定 → 整体跳过（beta1 无此判断，历史上曾误删在用配置）；
2. 孤儿数 ≥ 配置总数 → 判据可疑，不处理。

其余删除路径（`restoreAllPreferences` 的覆盖式恢复、`deleteTimeConfig`、`deleteRoutine`）与 beta1
逻辑一致。全部启动迁移（`migrateSchedulesIntoDefaultFolder`、`migrateLegacyAppearanceIfNeeded`、
`migrateLegacyFollowDates`）都是幂等 + 标记位守门，与 beta1 相同。

## 七、需手动处理的问题

### 1. 发版前必须 bump 版本号 ⚠️

`androidApp/build.gradle.kts` 当前仍是：

```kotlin
versionCode = 164
versionName = "1.6.4-1008"
```

与 beta1 完全相同。若以此构建发布，用户侧要么装不上、要么不会触发更新。**发版前务必改为
`versionCode = 165`（或更高）与对应的 versionName。**

### 2. 低风险提示（无需处理）

`AppStorage.store()` 在未 `init` 时会抛异常（刻意设计，避免静默返回空数据）。
`TodayCoursesProvider` 是 ContentProvider，其 `onCreate()` 早于 `Application.onCreate()`，
但它只返回 `context != null`、不碰存储，实际 `query()` 也在 `Application.onCreate()` 之后执行，
当前无风险。后续若往 ContentProvider 的 `onCreate()` 里加读存储的代码，会直接崩。

---

## 审查方法

脚本化抽取两版全部 `getSharedPreferences(...)` / `AppStorage.store(...)` 调用点并做常量溯源比对；
对 `Course` / `TimeConfig` 系列做字段级 AST 级比对；对新增的 `clear/remove/delete` 行逐条过目；
跑 `:core:jvmTest` 全量 + `:androidApp:assembleDebug` 门禁。
