// Nexio课程表 - 跨平台共享模块（KMP）
// AGP 9 起的 KMP 写法：插件是 com.android.kotlin.multiplatform.library（不是 com.android.library），Android 目标配置写在 kotlin { android { } } 里。

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
        // 开宿主机测试：:core 的 Android 实现（SharedPreferencesStore 等）。
        // ⚠ 代价：commonTest 有 3 个类依赖真实设备字段或 Application 生命周期，宿主机上是桩 android.jar，实测 16 个用例必挂 —— 在 testAndroidHostTest 里显式排除，断言未改弱，仍由 jvmTest + iOS 门禁覆盖。
        withHostTest {}
    }
    jvm()
    // ── iOS 目标：**默认关闭**，-Pnexio.ios=true 开启 ────────────────────────
    // 开启后请改用联网构建：./gradlew -Pnexio.ios=true :core:compileKotlinIosArm64
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        val iosTargets = listOf(iosArm64(), iosSimulatorArm64())
        iosTargets.forEach { target ->
            target.compilations.getByName("main") {
                cinterops.create("applesysctl") {
                    defFile = project.file("src/nativeInterop/cinterop/apple-sysctl.def")
                    // 默认包名取 def 文件名（"apple-sysctl"），带连字符不是合法包名，显式指定
                    packageName = "applesysctl"
                }
            }
        }
    }

    sourceSets {
        // 「Android 与 JVM 桌面共用」的实现（java.io.File 系，FileAppFile 等）：与 :miuix/:backdrop 的 skikoMain 同一招 —— 用 srcDir 共享同一份源码
        androidMain {
            kotlin.srcDir("src/jvmShared/kotlin")
        }
        jvmMain {
            kotlin.srcDir("src/jvmShared/kotlin")
        }

        commonMain.dependencies {
            // java.time 的跨平台替代。必须 api —— LocalDate 出现在 :core 公开签名里（Course / HolidayEntry 等），implementation 不传递给下游。
            api(libs.kotlinx.datetime)
            // 协程：StateFlow 出现在公开签名里，同理必须 api。
            api(libs.kotlinx.coroutines.core)
            // JSON：替代 org.json。api —— JsonSupport 的扩展函数挂在 kotlinx.serialization.json.JsonObject 上，类型会出现在下游调用点。
            api(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            // Android 侧网络仍走 OkHttp（与迁移前同一套引擎、同一套超时语义）；刻意不引入 Ktor，会连带把 kotlinx-coroutines 从 1.9.0 顶到 1.10+。
            implementation(libs.okhttp)
        }

        // round-trip 回归测试跑在 commonTest：KeyValueStore 的读写语义必须在所有平台一致，用 InMemory 实现即可验证，不需要 Android 设备。
        commonTest.dependencies {
            implementation(kotlin("test"))
            // HttpService 是 suspend 接口，契约测试需要协程测试运行时。
            implementation(libs.kotlinx.coroutines.test)
        }

        // 仅 JVM 测试用：拉真实 Gson 对拍 ScheduleCodec，确保换序列化实现后输出与 Gson 一致。不进任何产物。
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

// ── Android 宿主机测试（withHostTest）的排除项 ────────────────────────────────
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    filter {
        excludeTestsMatching("com.haooz.chedule.data.PlatformInfoTest")
        excludeTestsMatching("com.haooz.chedule.data.StatsReporterPayloadTest")
        excludeTestsMatching("com.haooz.chedule.data.DataFetcherLogicTest")
    }
}
