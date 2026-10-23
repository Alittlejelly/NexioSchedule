// Nexio课程表 - 顶层构建配置：除插件声明外，还挂了 KMP 迁移相关的强制配置（平台纯净度检查、kotlinx-datetime 版本钉死），原因写在对应位置。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// ─────────────────────────────────────────────────────────────────────────────
// commonMain / skikoMain 平台纯净度检查（静态兜底）
// 拦「Android/JVM 全绿、iOS 编不过」：等 iOS 编译才发现太迟，正则扫描在编译前就能定位到行号。iOS 真编译门禁 opt-in（-Pnexio.ios=true，默认关的原因见对应模块注释），常态下静态检查是主力；已知盲区：正则查不出未知 API。
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 一条禁止规则。[appliesTo] 用于排除文件级的已知例外。
 * 两个坑（已实测）：1. `@Volatile` 只有在文件未导入 `kotlin.concurrent.Volatile` 时才是问题，否则误报；2. `@JvmInline` / `import kotlin.jvm.JvmInline` 合法，value class 专用。
 */
class Forbidden(
    val name: String,
    val regex: Regex,
    val fix: String,
    val appliesTo: (hasKotlinConcurrentVolatile: Boolean) -> Boolean = { true },
)

abstract class CheckKmpPurityTask : DefaultTask() {

    /** 待扫描的 .kt 文件。 */
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    /** 仓库根路径，仅用于把绝对路径显示成相对路径。 */
    @get:Input
    abstract val rootPath: Property<String>

    @TaskAction
    fun check() {
        val root = rootPath.get()
        val violations = mutableListOf<String>()
        var scanned = 0

        // 必须用 asFileTree 展开目录：ConfigurableFileCollection 里放的是目录本身，直接取 .files 只会拿到目录对象（曾因此扫描到 0 个文件却报通过 —— 假绿）。
        val ktFiles = sources.asFileTree.matching { include("**/*.kt") }.files
            .sortedBy { it.absolutePath }

        for (src in ktFiles) {
            val text = src.readText()
            scanned++
            val hasKotlinConcurrentVolatile = text.contains("import kotlin.concurrent.Volatile")
            val rel = src.absolutePath.removePrefix(root).trimStart('\\', '/')

            text.lines().forEachIndexed { index, line ->
                // 跳过注释行，避免把 KDoc 里的「反例说明」当成违规
                val trimmed = line.trimStart()
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    return@forEachIndexed
                }
                for (f in FORBIDDEN) {
                    if (f.appliesTo(hasKotlinConcurrentVolatile) && f.regex.containsMatchIn(line)) {
                        violations += "$rel:${index + 1}  [${f.name}]  ${line.trim()}\n        修法：${f.fix}"
                    }
                }
            }
        }

        if (violations.isEmpty()) {
            logger.lifecycle("checkKmpPurity: 扫描 $scanned 个跨平台源文件，未发现 JVM 专有 API ✅")
        } else {
            throw GradleException(
                "commonMain/skikoMain 存在 ${violations.size} 处 JVM 专有 API，" +
                    "这些代码在 Android/JVM 上能编过但在 iOS(Kotlin/Native) 上会失败：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    companion object {
        // 必须放伴生对象：@TaskAction 捕获 build script 实例会让配置缓存报 "cannot serialize Gradle script object references"。
        val FORBIDDEN = listOf(
            Forbidden(
                "@Volatile（无限定）",
                Regex("""(?<![\w.])@Volatile\b"""),
                "改用 import kotlin.concurrent.Volatile（JVM 上是 kotlin.jvm.Volatile 的 typealias，行为不变）",
                appliesTo = { hasKotlinConcurrentVolatile -> !hasKotlinConcurrentVolatile },
            ),
            Forbidden(
                "synchronized",
                Regex("""(?<![\w.])synchronized\s*[({]"""),
                "Kotlin/Native 没有 synchronized；读多写少的缓存改用 copy-on-write，或抽 expect/actual",
            ),
            Forbidden(
                "Dispatchers.IO",
                Regex("""Dispatchers\s*\.\s*IO\b"""),
                "在 Kotlin/Native 上是 internal；抽 expect val ioDispatcher（Native 用 Dispatchers.Default）",
            ),
            Forbidden(
                "java.* 导入",
                Regex("""(?m)^\s*import\s+java\.""", RegexOption.MULTILINE),
                "java.* 只存在于 JVM；换 kotlin.* 或抽 expect/actual",
            ),
            Forbidden(
                "kotlin.jvm.* 导入（JvmInline 除外）",
                Regex("""(?m)^\s*import\s+kotlin\.jvm\.(?!JvmInline\b)\w+""", RegexOption.MULTILINE),
                "kotlin.jvm.* 只存在于 JVM；@JvmInline 是唯一例外",
            ),
            Forbidden(
                "System.currentTimeMillis / nanoTime / getProperty",
                Regex("""(?<![\w.])System\s*\.\s*(currentTimeMillis|nanoTime|getProperty|getenv|arraycopy)\b"""),
                "改 kotlinx-datetime 的 Clock.System.now()，或抽 expect/actual",
            ),
            Forbidden(
                "String.format",
                Regex("""(?<![\w.])String\s*\.\s*format\b"""),
                "JVM 专有，且会按 Locale 数码字形输出（阿拉伯语 locale 下 \"08:00\" 变非 ASCII）；改 padStart",
            ),
            Forbidden(
                "字符串字面量 .format(...)（String.format 的扩展形式）",
                // 只匹配字符串字面量的扩展调用（`"%.1f".format(x)`）；变量形式
                // （`fmt.format(x)`）静态区分不了，靠 iOS 编译门禁兜底。
                Regex("\"[^\"]*\"\\s*\\.\\s*format\\("),
                "String.format 的扩展形式，同样是 JVM 专有；改 padStart 或 :core 的 formatOneDecimal",
            ),
            Forbidden(
                "java.util.UUID",
                Regex("""java\s*\.\s*util\s*\.\s*UUID"""),
                "用 kotlin.random.Random 自建 UUID v4 格式（见 PlatformInfo.kt 的 randomUuidV4）",
            ),
            Forbidden(
                "String.toByteArray()",
                // 只匹配**字符串字面量**：`Collection<Byte>.toByteArray()` 是 common API，不能误伤。
                // 变量形式（`s.toByteArray()`）静态区分不了，靠 iOS 编译门禁兜底。
                Regex("\"[^\"]*\"\\s*\\.\\s*toByteArray\\(\\)"),
                "String.toByteArray() 是 JVM 专有；跨平台用 encodeToByteArray()",
            ),
            Forbidden(
                "::class.java",
                Regex("""::\s*class\s*\.\s*java\b"""),
                "JVM 反射不存在于 Native；测试里尤其常见，需换平台中立写法",
            ),
            Forbidden(
                "java.io.*",
                Regex("""(?<![\w.])java\s*\.\s*io\s*\.\s*\w+"""),
                "java.io 不存在于 Native；改用 ByteArray + 下标，或抽 expect/actual 文件接口",
            ),
            Forbidden(
                "Thread / ThreadLocal",
                Regex("""(?<![\w.])(Thread|ThreadLocal)\s*[(<]"""),
                "Native 不支持 JVM 线程模型；用协程调度器替代",
            ),
        )
    }
}

/** 需要扫描的源集：commonMain 是「必须跨平台」，skikoMain 是「jvm 与 iOS 共享」。 */
val puritySourceSets = listOf("commonMain", "skikoMain")
val purityModules = listOf("core", "backdrop", "miuix", "ui-shared")

tasks.register<CheckKmpPurityTask>("checkKmpPurity") {
    group = "verification"
    description = "检查 commonMain/skikoMain 是否混入 JVM 专有 API（这些代码在 iOS 上编不过）"
    rootPath.set(projectDir.absolutePath)
    sources.from(
        purityModules.flatMap { module ->
            puritySourceSets.map { set -> file("$module/src/$set") }
        },
    )
}

// 让各模块的 `check` 自动带上纯净度检查（否则很容易被忘掉）；根项目没有 java 插件、没有 check 任务，所以挂在子项目上。
val purityCheckTask = tasks.named("checkKmpPurity")

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(purityCheckTask)
    }
}

// kotlinx-datetime 版本钉死 —— 全应用只允许一个版本
// CMP material3 在 **iOS 变体**上依赖 kotlinx-datetime 0.7.1，会把本项目锁定的 0.6.2 顶掉，而 Android/JVM 侧走 AndroidX material3（不拉 datetime）仍是 0.6.2。结果是同一个 iOS 二进制里 :core 按 0.6.2 编、:ui-shared 按 0.7.1 编 —— 两版 LocalDate 是不同类，链接期必炸（症状：Clock.System 报 Unresolved reference）。
// 升级时必须全项目一起升（含 monthNumber → month、Clock → kotlin.time.Clock），然后删掉这条 force。
subprojects {
    configurations.configureEach {
        resolutionStrategy.force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    }
}
