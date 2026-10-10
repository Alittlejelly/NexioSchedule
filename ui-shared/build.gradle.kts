// Nexio课程表 - 共享 UI 模块（阶段 5 的归宿）
//
// 迁移方向：:androidApp 的 Compose 页面分批搬进 commonMain，Android 与桌面
// （:desktopApp）共用同一批页面。iOS 将来也复用（本模块暂不挂 iOS 目标，
// 与 :core 一样默认关、需要时再开 —— 但注意本模块 commonMain 的平台中立性
// 必须从第一天就守住，否则重蹈「只有 JVM 目标编过、编 iOS 才炸」的覆辙）。
//
// 源集分工：
//   commonMain  —— 平台中立的页面（禁 android.*/java.*/LocalConfiguration）
//   androidMain —— Android 专有差异（首版暂为空，页面里的平台差异用
//                  expect/actual 或 :miuix 现成抽象消化）
//   jvmMain     —— 桌面侧差异（同上）
//
// ⚠ 本模块 commonMain 必须纳入根项目 checkKmpPurity 扫描（purityModules）。
// ⚠ 不加 wasmJs/iOS 目标：加了会撞 FAIL_ON_PROJECT_REPOS（见 :miuix 注释），
//   平台中立性靠 checkKmpPurity + jvm 目标编译 + :core 的 linuxX64 三道闸。

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    // CMP 1.6.10 起必须显式应用 Kotlin 的 Compose 编译器插件
    alias(libs.plugins.kotlin.compose)
}

// composeResources 的生成类包名必须显式指定。
// 不配的话 CMP 会拿 **Gradle 项目名**拼包名 —— 本仓库 rootProject.name = "Nexio课程表"，
// 生成出来的包会变成 `nexio课程表.ui_shared.generated.resources`（中文 + 下划线，又怪又不可控）。
compose.resources {
    packageOfResClass = "com.haooz.chedule.uishared.resources"
    // ⚠ 默认生成的 Res 是 internal —— 只在 :ui-shared 内部可见。
    // 但 :androidApp 的页面（WebViewScreen 等）还在 Android 模块里直接引用它，
    // 不开 public 会报 "Cannot access 'object Res : Any': it is internal in file"。
    publicResClass = true
}

kotlin {
    android {
        namespace = "com.haooz.chedule.uishared"
        compileSdk = 37
        minSdk = 26
        // ⚠ CMP 1.8.0 起 androidLibrary 目标必须显式开资源，否则访问 composeResources
        // 会抛 MissingResourceException（见 docs/KMP_IOS_MIGRATION_PLAN.md F 节）。
        androidResources.enable = true
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            // composeResources：跨平台资源（R.drawable → Res.drawable 的归宿）。
            // 生成类为 `Res`（不是 `R`），资源放 src/commonMain/composeResources/。
            //
            // ⚠ 必须用 api 而不是 implementation：`Res` 类与 `painterResource` 会被
            // :androidApp 的页面直接引用（页面还在 Android 模块里），implementation 不传递，
            // 调用方会报 "Unresolved reference 'resources'" / "Unresolved reference 'Res'"。
            api(compose.components.resources)
            // 数据层与跨平台抽象（AppStorage / AppFiles / HttpService / ioDispatcher…）
            api(project(":core"))
            // 页面骨架与组件（Scaffold / CollapsibleTopAppBar / OverlayDialog…）
            api(project(":miuix"))
            // 液态玻璃采样（DocumentPageScaffold 的 liquidGlassBackdrop）
            api(project(":backdrop"))

            // ⚠ 官方 miuix 子库：`:miuix` 里是 implementation **不传递**，页面直接
            // import 它们（ArrowPreference / MiuixIcons / layerBackdrop）就各欠各的。
            // 用**不带 -android 后缀的根坐标** —— KMP 模块由 Gradle 按目标解析变体，
            // 写死 -android 会让 jvm 目标编不过（同 miuix/build.gradle.kts 的注释）。
            implementation("top.yukonga.miuix.kmp:miuix-preference:${libs.versions.miuix.get()}")
            implementation("top.yukonga.miuix.kmp:miuix-icons:${libs.versions.miuix.get()}")
            implementation("top.yukonga.miuix.kmp:miuix-blur:${libs.versions.miuix.get()}")
            // KMP 版 lifecycle：ViewModel / viewModelScope 的跨平台实现（页面迁入本模块后要用）。
            //
            // 选 2.9.6（jetbrains 组）而不是 AndroidX 组直连：它转发到 androidx.lifecycle 2.9.4，
            // 而 :androidApp 侧的 lifecycle-viewmodel 本来就被 CMP 的 compose-ui 顶到 2.10.0，
            // 冲突解析取高版本 —— **实测对本项目 Android 侧的依赖版本零影响**
            //（`dependencyInsight` 核对过，见 2026-10-10 迁移记录）。
            //
            // ⚠ 首次拉取需要联网（`lifecycle-viewmodel-desktop` 等 jvm 变体本地缓存没有）；
            //   拉过一次之后 `--offline` 门禁可正常跑。
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.6")
        }
    }
}
