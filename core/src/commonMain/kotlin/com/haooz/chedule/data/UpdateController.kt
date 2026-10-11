package com.haooz.chedule.data

/**
 * 应用更新控制器 —— 平台能力抽象（注入模式，同 `DiagnosticRecorder` / `PlatformToast` / `ScheduleEffects`）。
 *
 * 真实实现是 `:androidApp` 的 `AndroidUpdateController`（包装全局单例 `UpdateChecker` /
 * `UpdateInstaller`，外加 packageManager 版本号）。检查更新（HTTP 拉 Release 列表）、
 * APK 下载 / 校验 / 安装全是 Android 专有能力（桌面/iOS 没有 APK 概念）——
 * 故 `:ui-shared` 的「应用更新」页只依赖本接口，由宿主注入实现；桌面/iOS 用 [NoopUpdateController]。
 *
 * ## 约定
 *
 * - ⚙ **不带 Context 参数**：Android 包装在构造时捕获 context（同 `AndroidDiagnosticRecorder`），
 *   接口层保持纯跨平台签名，`:core` commonMain 里没有任何 android / java import。
 * - ⚙ **阻塞约定**：[checkForUpdate] / [hasValidApk] / [resolveApkUrl] 是阻塞 IO，
 *   commonMain 没有 `Dispatchers.IO`，调用方用 `withContext(ioDispatcher)` 包住再调
 *   （与原实现「页面用 withContext(Dispatchers.IO) 包 UpdateChecker 调用」一一对应）；
 *   [downloadApk] 是 suspend，实现内部自行切调度器。
 * - ⚙ **APK 句柄用路径字符串**：原页面持有 `java.io.File`（下载结果 + 安装入参），
 *   commonMain 无 File，统一改成绝对路径 `String`，Android 包装负责 File ↔ String 互转。
 */
interface UpdateController {
    /**
     * 当前安装的版本号；取不到时为 `"未知"`。
     * （原实现：`context.packageManager.getPackageInfo(...).versionName ?: "未知"` + try/catch）
     */
    val currentVersion: String

    /**
     * 检查更新。返回 `hasUpdate` 与最新 [UpdateRelease]（无新版本 / 检查失败时 release 为 null）。
     *
     * ⚙ 阻塞（同步 HTTP），需在 `withContext(ioDispatcher)` 内调用。
     * 语义与原 `UpdateChecker.checkForUpdate` 一致：失败返回 `(false, null)`，不抛异常。
     */
    fun checkForUpdate(source: String, channel: String): Pair<Boolean, UpdateRelease?>

    /**
     * 把一次检查到的 release 落到 `update_settings` 缓存（弹窗与设置页共用同一份，
     * 原样缓存 apkUrl 即使为空 —— 逐字保留原 `UpdateChecker.persistRelease` 行为）。
     */
    fun persistRelease(release: UpdateRelease)

    /** 记录该 tag 安装包的期望校验值（sha256 / 字节数），供后续复核使用 */
    fun rememberApkDigest(tag: String, sha256: String?, sizeBytes: Long?)

    /** 回读 [rememberApkDigest] 记下的 SHA-256；无则 null */
    fun rememberedApkSha256(tag: String): String?

    /** 回读 [rememberApkDigest] 记下的字节数；无则 null */
    fun rememberedApkSize(tag: String): Long?

    /**
     * 本地是否已有一份**可安装**的安装包（校验不过时 Android 侧会顺手删掉损坏包）。
     * ⚙ 阻塞（需解析 APK），需在 `withContext(ioDispatcher)` 内调用。
     */
    fun hasValidApk(tag: String): Boolean

    /**
     * 该 tag 对应本地安装包的**绝对路径**（对应原 `UpdateInstaller.apkFile(...).absolutePath`）。
     * 不判断存在性 —— 调用方先用 [hasValidApk] 判定，无效时自行置 null。
     */
    fun localApkPath(tag: String): String

    /**
     * 解析 [tag] 对应的 APK 下载地址（缓存优先，缓存不可用回源重查），解析不到返回 null。
     * ⚙ 阻塞（缓存失效时会走网络），需在 `withContext(ioDispatcher)` 内调用。
     */
    fun resolveApkUrl(tag: String, source: String, channel: String): String?

    /**
     * 下载 APK 到本地并做完整性校验。成功返回落盘文件的**绝对路径**；
     * 失败抛 `Exception`（调用方 catch 后 toast `下载失败: ${e.message}`）。
     *
     * @param onProgress 0f..1f 进度回调，实现负责切回主线程
     */
    suspend fun downloadApk(
        apkUrl: String,
        tag: String,
        onProgress: suspend (Float) -> Unit,
    ): String

    /**
     * 清掉本地缓存的旧安装包（保留下载中/已校验完成的当前包）。
     *
     * 原实现 `UpdateChecker.cleanOldApks(context, keepTag)` 是 Android 专有的文件目录扫描，
     * 故收进本接口；调用点在检查到新版本之后（与迁移前一致）。
     *
     * @param keepTag 要保留的版本 tag；null/空白 = 全清
     */
    fun cleanOldApks(keepTag: String?)

    /**
     * 安装本地 APK（Android：优先 Shizuku 静默安装，失败回退系统安装器）。
     *
     * @param apkPath [localApkPath] / [downloadApk] 返回的绝对路径
     * @param onInstallingChanged 安装中状态回调（主线程）
     * @param onFinished 结束回调（静默成功 / 系统安装器已拉起都会触发）
     */
    fun installApk(
        apkPath: String,
        onInstallingChanged: (Boolean) -> Unit,
        onFinished: (() -> Unit)? = null,
    )
}

/**
 * 一次检查到的 Release 元数据。
 *
 * 字段与 `:androidApp` 的 `UpdateChecker.GiteeRelease` **一一对应**（那是个 internal 嵌套类，
 * 不能跨模块引用，故在 :core 复制一份数据类，由 `AndroidUpdateController` 做双向转换）。
 */
data class UpdateRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val apkUrl: String,
    val createdAt: String,
    /** Release 资产下发的 SHA-256（GitHub 为 digest="sha256:<hex>"）；拿不到时为 null */
    val apkSha256: String? = null,
    /** Release 资产声明的字节数 */
    val apkSize: Long? = null,
)

/**
 * 桌面/iOS 的空实现：应用更新是 Android 专有能力，非 Android 平台恒为「无更新能力」——
 * 检查恒 `(false, null)`（页面会提示检查失败）、无本地包、解析不到下载地址、安装空转。
 */
object NoopUpdateController : UpdateController {
    override val currentVersion: String get() = "未知"
    override fun checkForUpdate(source: String, channel: String): Pair<Boolean, UpdateRelease?> =
        Pair(false, null)
    override fun persistRelease(release: UpdateRelease) {}
    override fun rememberApkDigest(tag: String, sha256: String?, sizeBytes: Long?) {}
    override fun rememberedApkSha256(tag: String): String? = null
    override fun rememberedApkSize(tag: String): Long? = null
    override fun hasValidApk(tag: String): Boolean = false
    // hasValidApk 恒 false，正常流程走不到这里；返回空串只为满足非空签名
    override fun localApkPath(tag: String): String = ""
    override fun cleanOldApks(keepTag: String?) {}
    override fun resolveApkUrl(tag: String, source: String, channel: String): String? = null
    // resolveApkUrl 恒 null，下载按钮会在拿到 null 后提前 return，正常流程走不到这里
    override suspend fun downloadApk(
        apkUrl: String,
        tag: String,
        onProgress: suspend (Float) -> Unit,
    ): String = ""
    override fun installApk(
        apkPath: String,
        onInstallingChanged: (Boolean) -> Unit,
        onFinished: (() -> Unit)?,
    ) {}
}
