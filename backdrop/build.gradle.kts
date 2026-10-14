// Nexio课程表 - 液态玻璃效果库（fork 自 io.github.kyant0:backdrop 2.0.1）
//
// fork 而非依赖 Maven artifact：本项目对 backdrop 有 671 行定制
// （DrawBackdropModifier / LayerBackdropModifier / RuntimeShader 等 14 个文件），
// 定制直接住在源码里，才方便在 KMP 的两个平台源集上分别落地。
//
// 源集分工：
//   commonMain  —— 上游 2.0.1 的跨平台逻辑 + 本项目的定制
//   androidMain —— Android 侧 actual（RenderEffect / AGSL / asFrameworkPaint）
//   skikoMain   —— iOS / Desktop / Web 侧 actual（Skia RuntimeEffect + SkSL）
//
// 上游 2.0.1 本身就是 KMP 库，SkSL 那半边现成，本项目只需在 skikoMain 上补齐定制。
// AGP 9 下 KMP 模块的 Android 目标用 com.android.kotlin.multiplatform.library，
// 配置写在 kotlin { android { } } 里。

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    // CMP 1.6.10 起必须显式应用 Kotlin 的 Compose 编译器插件
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.haooz.chedule.backdrop"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    // ── wasmJs 编译门禁：**默认关闭**，用 -Pnexio.wasm=true 显式开启 ───────────
    // 目的：skikoMain 此前只被 jvm 目标复用，从未针对非 JVM 目标编译过，
    // 静态检查挡不住未知 API。wasmJs 同为非 JVM 目标，能拦 @Volatile /
    // synchronized / Dispatchers.IO 这类「JVM 全绿但 Native 编不过」的污染。
    //
    // ⚠ 默认关：只要 wasmJs 目标存在于构建中，Kotlin/Wasm 插件就在**配置阶段**
    // 为 `kotlinWasmNodeJsSetup` 注册 Distributions 仓库，与 settings.gradle.kts
    // 的 FAIL_ON_PROJECT_REPOS 冲突，连 `--offline` 的 Android 门禁都跑不了。
    // 把 EnvSpec.download 置 false 也没用（注册早于读取 download）。
    // 用法：./gradlew -Pnexio.wasm=true checkKmpWasmJs
    val enableWasm = (project.findProperty("nexio.wasm") as? String)?.toBoolean() ?: false
    if (enableWasm) {
        wasmJs {
            browser()
        }
    }
    // ── iOS target：**默认关闭**，用 -Pnexio.ios=true 显式开启 ──────────────────
    // 同理：声明 iOS target 会让 `:backdrop:check` 去解析 iOS 的 klib 依赖，
    // 而门禁是 `--offline` 跑的，本地没这些变体缓存，直接失败。
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        iosArm64()
        // 模拟器变体：日常开发跑模拟器，只声明 iosArm64 会让下游解析不到。
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
            // 上游用来做形状运算的库，本项目 ui/effects/shapes 与之同源
            api(libs.kyant.shapes)
            // @Language("AGSL") 注解（RuntimeShader / Shaders 等共 8 处）。wasmJs 目标
            // 上没有该依赖时报 Unresolved reference 'intellij'，故显式声明。
            api("org.jetbrains:annotations:26.1.0")
        }
        // jvm 复用 skikoMain（Skia 实现）。用 srcDir 而非拷贝，
        // 同一份 SkSL 代码跨 Skia 后端共享。
        jvmMain {
            kotlin.srcDir("src/skikoMain/kotlin")
        }
        // 必须条件式：wasmJs / iOS target 默认关闭，无条件引用会配置失败。
        if (enableWasm) {
            wasmJsMain {
                kotlin.srcDir("src/skikoMain/kotlin")
            }
        }
        if (enableIos) {
            iosMain {
                kotlin.srcDir("src/skikoMain/kotlin")
            }
        }
    }
}
