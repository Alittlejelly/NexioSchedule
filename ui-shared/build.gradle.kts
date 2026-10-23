// Nexio课程表 - 共享 UI 模块：平台中立的 Compose 页面放 commonMain，Android 与桌面（:desktopApp）共用，iOS 通过 framework 产物接入。
// 源集分工：commonMain 平台中立页面（禁 android.*/java.*）｜androidMain Android 专有差异｜jvmMain 桌面侧差异。
// ⚠ commonMain 必须纳入根项目 checkKmpPurity 扫描（purityModules）。

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    // CMP 1.6.10 起必须显式应用 Kotlin 的 Compose 编译器插件
    alias(libs.plugins.kotlin.compose)
}

// composeResources 的生成类包名必须显式指定：不配的话 CMP 会拿 **Gradle 项目名**拼包名 —— 本仓库 rootProject.name 是中文，生成的包会变成 `nexio课程表.ui_shared.generated.resources`（又怪又不可控）。
compose.resources {
    packageOfResClass = "com.haooz.chedule.uishared.resources"
    // ⚠ 默认生成的 Res 是 internal（只在 :ui-shared 内部可见），但 :androidApp 的页面（WebViewScreen 等）还在 Android 模块里直接引用它，不开 public 会报 "Cannot access 'object Res : Any': it is internal in file"。
    publicResClass = true
}

kotlin {
    android {
        namespace = "com.haooz.chedule.uishared"
        compileSdk = 37
        minSdk = 26
        // ⚠ CMP 1.8.0 起 androidLibrary 目标必须显式开资源，否则访问 composeResources 会抛 MissingResourceException（见 docs/KMP_IOS_MIGRATION_PLAN.md F 节）。
        androidResources.enable = true
    }
    jvm()
    // ── iOS target：**默认关闭**，-Pnexio.ios=true 开启（同 :core / :miuix / :backdrop）──
    // 默认关：声明 iOS target 后 `check` 会去解析 iOS 的 klib 依赖，而门禁是 --offline 跑的，本地没这些变体缓存，直接失败（完整原因见 :miuix 注释）。
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        iosArm64()
        // 模拟器变体：日常开发真正跑的是模拟器（arm64 Mac 上就是 iosSimulatorArm64）；三个上游模块（:core / :miuix / :backdrop）须同步声明，否则解析不到模拟器 klib。
        iosSimulatorArm64()

        // ── framework 产物 ────────────────────────────────────────
        // 让 Xcode 侧有东西可嵌（iOS App 的 Compose 界面全部来自本模块）。**编译 ≠ 链接**：klib 图的版本冲突只在链接期才真正合账，声明 framework 才有 link 任务可跑，否则「编过了」永远差最后一步验证。
        iosArm64 { binaries.framework { baseName = "NexioShared"; isStatic = true } }
        iosSimulatorArm64 { binaries.framework { baseName = "NexioShared"; isStatic = true } }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            // composeResources：跨平台资源。生成类是 `Res`（不是 `R`），资源放 src/commonMain/composeResources/。
            // ⚠ 必须 api 而不是 implementation：`Res` 与 `painterResource` 被 :androidApp 的页面直接引用，implementation 不传递会报 Unresolved reference。
            api(compose.components.resources)
            // 数据层与跨平台抽象（AppStorage / AppFiles / HttpService / ioDispatcher…）
            api(project(":core"))
            // 页面骨架与组件（Scaffold / CollapsibleTopAppBar / OverlayDialog…）
            api(project(":miuix"))
            // 液态玻璃采样（DocumentPageScaffold 的 liquidGlassBackdrop）
            api(project(":backdrop"))

            // ⚠ 官方 miuix 子库：`:miuix` 里是 implementation 不传递，页面直接 import 它们（ArrowPreference / MiuixIcons / layerBackdrop）就各欠各的。
            // 用**不带 -android 后缀的根坐标**：KMP 模块由 Gradle 按目标解析变体，写死 -android 会让 jvm 目标编不过。
            // ⚠ 每个都必须 exclude 官方 `miuix-ui` —— 与 `:miuix` 的同包同名 fork 一起进 iOS 链接期会报 `IrPropertySymbolImpl is already bound`（完整原因见 miuix/build.gradle.kts）。
            implementation("top.yukonga.miuix.kmp:miuix-icons:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            implementation("top.yukonga.miuix.kmp:miuix-blur:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            // squircle 是**独立 artifact**，原先靠官方 miuix-ui 传递进来，排除后即断，本模块自己直接用就该显式声明。
            implementation("top.yukonga.miuix.kmp:miuix-squircle:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            // KMP 版 lifecycle：ViewModel / viewModelScope 的跨平台实现。选 jetbrains 组：转发到 androidx 2.9.4，而 :androidApp 侧已被 CMP 的 compose-ui 顶到 2.10.0，取高版本，对 Android 侧依赖版本零影响。
            // ⚠ 首次拉取需联网（jvm 变体本地缓存没有），拉过一次后 --offline 可跑。
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.6")
            // `viewModel()` 这个 @Composable 工厂在单独的 artifact 里（-compose 后缀）；AndroidX 默认工厂只认无参构造，所以各 VM 都保持无参构造。
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
        }
    }
}
