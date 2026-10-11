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
    // ── iOS target：**默认关闭**，用 -Pnexio.ios=true 显式开启（同 :core / :miuix / :backdrop）──
    //
    // 为什么默认关：一旦声明 iOS target，`:ui-shared:check` 就会去解析 iOS 的 klib 依赖，
    // 而第八节门禁是 `--offline` 跑的 —— 本地没有这些变体的缓存，`:ui-shared:check`
    // 直接失败，把「Android 零回归」这条红线弄脏（与 :core 完全同因）。
    //
    // 2026-10-11 首次开启并实测通过（macOS + Xcode 27）：
    //   ./gradlew -Pnexio.ios=true :ui-shared:compileKotlinIosArm64
    // 这一条是**整个迁移的终检** —— 23 批页面的「iOS 能不能编」此前全靠
    // checkKmpPurity 静态扫描 + :core 的 linuxX64 目标间接保证，本模块从未被
    // Kotlin/Native 真正编译过。
    //
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        iosArm64()
        // 模拟器变体：日常开发真正跑的是模拟器（arm64 Mac 上就是 iosSimulatorArm64）。
        // 三个上游模块（:core / :miuix / :backdrop）同步声明，否则解析不到模拟器 klib。
        iosSimulatorArm64()

        // ── framework 产物（交接清单 §6 A0 第 1 项）──────────────────────────
        // 目的有二：
        //  1. 让 Xcode 侧有东西可嵌（iOS App 的 Compose 界面全部来自本模块）
        //  2. **编译 ≠ 链接**：klib 图的版本冲突（如 kotlinx-datetime 0.6 vs 0.7）
        //     只有在链接期才真正合账。声明 framework 才有 link 任务可跑，
        //     否则「编过了」永远差最后一步验证。
        // isStatic = true：与 JetBrains 的 CMP 模板一致（静态链接把整张图拉进来，
        // 验证最彻底，也不会在 iOS 侧多出一个要签名的 dylib）。
        // ⚠ baseName 是**暂定**的：iOS App 工程落地时按实际 Xcode target 命名再定。
        //
        // 🔴 **当前链接会失败**（2026-10-11 实测）：`:miuix` 是 miuix-ui 的「部分 fork」，
        // 与官方 artifact 同包同名，两份 klib 进链接期报
        // `IrPropertySymbolImpl is already bound`。`compileKotlinIos*` 全绿，
        // 只有 link 这一步过不去 —— 卡点与解法见 miuix/build.gradle.kts 的注释（风险 R7）。
        iosArm64 { binaries.framework { baseName = "NexioShared"; isStatic = true } }
        iosSimulatorArm64 { binaries.framework { baseName = "NexioShared"; isStatic = true } }
    }

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
            // ⚠ 必须 exclude 官方 `miuix-ui`：`:miuix` 是它的同包同名 fork，
            // 两份 klib 一起进 iOS 链接期会报 `IrPropertySymbolImpl is already bound`。
            // 理由详见 miuix/build.gradle.kts 的注释。
            implementation("top.yukonga.miuix.kmp:miuix-preference:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            implementation("top.yukonga.miuix.kmp:miuix-icons:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            implementation("top.yukonga.miuix.kmp:miuix-blur:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            // `top.yukonga.miuix.kmp.squircle.*`（squircleClip / squircleBorder / squircleSurface…）
            // 是**独立 artifact**。原先靠官方 miuix-ui 传递进来，排除它之后就断了 ——
            // 本模块自己直接用它，就该显式声明（:androidApp 早就显式声明了）。
            implementation("top.yukonga.miuix.kmp:miuix-squircle:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
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
            // `viewModel()` 这个 **@Composable 工厂** 在单独的 artifact 里（`-compose` 后缀）。
            // 页面迁进 commonMain 后默认用 `viewModel: XxxViewModel = viewModel()` 拿实例
            // （AndroidX 默认工厂只认无参构造，所以 VM 都保持无参），没有这条就编不过。
            // 版本与上面保持一致（同属 JetBrains 的 KMP 移植线）。
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
        }
    }
}
