// Nexio课程表 - 桌面客户端（Compose Multiplatform Desktop / JVM）
//
// 与 :androidApp 共用 :ui-shared 的页面与 :core 的数据层。
// 本模块是**平台宿主**（同 :androidApp），commonMain 规则不适用于它，
// 但经它编译的 :ui-shared / :core 代码必须过 checkKmpPurity。

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        jvmMain.dependencies {
            // 共享 UI / 数据层 / 组件库与液态玻璃
            implementation(project(":ui-shared"))
            implementation(project(":core"))
            implementation(project(":miuix"))
            implementation(project(":backdrop"))
            // 桌面 Compose 运行时（Window / application）
            implementation(compose.desktop.currentOs)
            // 共享页面普遍用 `viewModel()` 自取 ViewModel，桌面端没有 Android 的
            // ViewModelStoreOwner，宿主必须自己补，否则点开就崩。
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.6")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
            // ⚠ 必须显式声明：:core 虽用 `api(libs.kotlinx.datetime)`，但没传到桌面端的
            // **运行时** classpath —— 打开用了它的页面会在运行时抛
            // ClassNotFoundException，编译期不报错。
            implementation(libs.kotlinx.datetime)
        }
    }
}

// ⚠ 强制 kotlinx-datetime 回 0.6.2（与 gradle/libs.versions.toml 一致）。
// Compose Desktop 的依赖树会把它提升到 0.7.1，而 0.7 起 Clock 被移出
// kotlinx.datetime、LocalDate.monthNumber 也改名，运行时才炸（编译期能解析符号）。
// Android 侧依赖树不同、没被提升，所以这个坑只在桌面端出现。
configurations.all {
    resolutionStrategy.force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
}

compose.desktop {
    application {
        mainClass = "com.haooz.chedule.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Nexio Schedule"
            packageVersion = "1.0.0"
            description = "Nexio 课程表桌面版"
        }
    }
}
