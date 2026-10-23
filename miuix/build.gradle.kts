// Nexio课程表 - 小米风格 UI 组件库（fork 自 top.yukonga.miuix.kmp:miuix-ui 0.9.3，KMP）
// fork 而非依赖 artifact：项目对 Miuix 有 3,177 行定制（ListPopup / NavigationRail / DynamicColors / SearchBar 等），定制住在源码里，才能分别落到 KMP 的平台源集上。
// 源集分工与 :backdrop 同构：commonMain 走 CMP 组件主体，androidMain 原生实现，skikoMain 出 iOS/Desktop 侧 actual（SkSL 模糊描边、系统取色降级）。

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    // CMP 1.6.10 起必须显式应用 Kotlin 的 Compose 编译器插件
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.haooz.chedule.miuix"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    // ── iOS target：**默认关闭**，-Pnexio.ios=true 开启 ──────────────────────
    // 默认关（实测）：声明 iOS target 后 `:miuix:check` 会去解析 iOS 的 klib 依赖，而门禁是 --offline 跑的，本地没有这些变体缓存，直接失败。
    // 已实测全部 5 个官方 artifact 都有 -iosarm64 变体，依赖解析无风险；本模块 fork 的 miuix-ui 靠 skikoMain 的 actual 兜底 iOS 侧。
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        iosArm64()
        // 模拟器变体也要：只声明 iosArm64 时，下游（:ui-shared）编模拟器解析不到本模块 klib
        iosSimulatorArm64()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            // 液态玻璃：绘制路径与能力探测（isRenderEffectSupported 等）都在这里
            api(project(":backdrop"))
            // Material Color（miuix theme 的 Monet 取色依赖）
            api(libs.materialKolor.utilities)
            // AGLS/SkSL 运行时的编译期注解
            api("org.jetbrains:annotations:26.1.0")

            // 本项目只 fork 了 miuix-ui 这一个模块，其余子库仍是官方 artifact；坐标用**不带 -android 后缀的根坐标**：KMP 模块由 Gradle 按目标解析变体，写死 -android 会让 commonMain 编不过（app 模块那边才是 -android）。
            //
            // 🔑 官方子库传递依赖 `top.yukonga.miuix.kmp:miuix-ui`，而本模块就是它的
            // **同包同名 fork**，必须 exclude 官方那份，否则两份 klib 同时存在：
            //   - Android/JVM：类路径按名去重，悄悄用其中一个（所以一直没暴露）
            //   - Kotlin/Native：链接期硬错误
            //     `IrPropertySymbolImpl is already bound. Signature: .../ButtonDefaults$stableprop`
            //
            // 本模块的 fork 是**自包含**的（补齐了上游缺失文件，不再依赖官方 miuix-ui）。
            // ⚠ 以后从上游同步时必须把新文件一起搬进来 —— 缺文件会以
            // 「Unresolved reference」当场报出，这正是自包含的红线。
            implementation("top.yukonga.miuix.kmp:miuix-squircle:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            implementation("top.yukonga.miuix.kmp:miuix-icons:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            implementation("top.yukonga.miuix.kmp:miuix-blur:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            // ⚠ `miuix-preference` **已 fork 进本模块**（preference/，官方 artifact 已移除）：官方版链接的是官方 miuix-ui 的 ABI，与本 fork 的 ArrowPreference 签名对不上，iOS 链接期报 Undefined symbols（别退回去用官方 artifact）。
            // 只 fork 了 preference/：同 jar 里的 popup/ 与 menu/ 和本模块 Liquid* 定制重名，不能并入。
            implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui:${libs.versions.miuixNavigation3.get()}")
        }
        androidMain.dependencies {
            // SearchBar / BottomSheet 的返回键处理，CMP 无对应库，Android 侧走原生实现
            implementation(libs.navigationevent.compose)
        }
        // jvm 复用 skikoMain（Skia 实现）：用 srcDir 而不是拷贝一份，同一份 SkSL 代码跨 Skia 后端共享（iOS 见下方条件块）。
        jvmMain {
            kotlin.srcDir("src/skikoMain/kotlin")
        }
        // 必须条件式：iOS target 默认关闭，此时 iosMain 源集不存在，无条件引用会配置失败（Unresolved reference 'iosMain'）。
        if (enableIos) {
            iosMain {
                kotlin.srcDir("src/skikoMain/kotlin")
            }
        }
    }
}
