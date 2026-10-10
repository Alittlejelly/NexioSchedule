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

kotlin {
    android {
        namespace = "com.haooz.chedule.uishared"
        compileSdk = 37
        minSdk = 26
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
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
        }
    }
}
