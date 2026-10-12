// Nexio课程表 - 跨平台共享模块（KMP）
//
// AGP 9 起的 KMP 写法与旧版不同：
//   - 插件是 com.android.kotlin.multiplatform.library，不是 com.android.library
//   - Android 目标配置写在 kotlin { android { ... } } 里，不是顶层的 android { }

import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "com.haooz.chedule.core"
        compileSdk = 37
        minSdk = 26
        // 刻意**不**开 withHostTest {}：会让 commonTest 跑在 Android 宿主机测试上，
        // 而那里的 android.jar 是桩实现（`Build.MODEL` 为 null），currentDeviceInfo()
        // 直接崩，PlatformInfoTest 等 8 个用例失败。commonTest 的平台中立性
        // 已由 compileTestKotlinLinuxX64 编译门禁保证。
    }
    jvm()
    // ── iOS 目标：**默认关闭**，用 -Pnexio.ios=true 显式开启 ──────────────────
    // 默认关的原因：声明 iOS target 后 `:core:check` 会去解析 iOS 的 klib 依赖，
    // 而门禁是 `--offline` 跑的，本地没这些变体缓存，直接失败。
    // 开启后请改用联网构建（去掉 --offline）：
    //   ./gradlew -Pnexio.ios=true :core:compileKotlinIosArm64
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        iosArm64()
        iosSimulatorArm64()
    }
    // 仅作**编译门禁**：linuxX64 同为 Kotlin/Native 目标，没有 `kotlin.jvm.*`
    // 默认导入，能拦下 @Volatile / synchronized / System.currentTimeMillis /
    // String.format 这类「Android/JVM 全绿但 iOS 编不过」的 commonMain 污染。
    // 不产出发布物，只跑 compileKotlinLinuxX64。
    linuxX64()

    sourceSets {
        // 「Android 与 JVM 桌面共用」的实现（java.io.File 系）：FileAppFile 等。
        // 与 :miuix/:backdrop 的 skikoMain 同一招 —— 用 srcDir 共享同一份源码，
        // 不复制两份（commonMain 里放不了 java.*，linuxX64 门禁会挂）。
        androidMain {
            kotlin.srcDir("src/jvmShared/kotlin")
        }
        jvmMain {
            kotlin.srcDir("src/jvmShared/kotlin")
        }

        commonMain.dependencies {
            // java.time 的跨平台替代。必须 api —— LocalDate 出现在 :core 公开签名里
            // （Course / HolidayEntry 等），implementation 不传递给下游。
            api(libs.kotlinx.datetime)
            // 协程：StateFlow 出现在公开签名里，同理必须 api。
            api(libs.kotlinx.coroutines.core)
            // JSON：替代 org.json。api —— JsonSupport 的扩展函数挂在
            // kotlinx.serialization.json.JsonObject 上，类型会出现在下游调用点。
            api(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            // Android 侧网络仍走 OkHttp —— 与迁移前同一套引擎、同一套超时语义。
            // 刻意不引入 Ktor，会连带把 kotlinx-coroutines 从 1.9.0 顶到 1.10+。
            implementation(libs.okhttp)
        }

        // round-trip 回归测试跑在 commonTest：KeyValueStore 的读写语义必须在
        // 所有平台一致，用 InMemory 实现即可验证，不需要 Android 设备。
        commonTest.dependencies {
            implementation(kotlin("test"))
            // HttpService 是 suspend 接口，契约测试需要协程测试运行时。
            implementation(libs.kotlinx.coroutines.test)
        }

        // 仅 JVM 测试用：拉真实 Gson 对拍 ScheduleCodec，确保换序列化实现后
        // 输出与 Gson 一致。不进任何产物。
        jvmTest.dependencies {
            implementation(libs.gson)
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
