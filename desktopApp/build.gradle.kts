// Nexio课程表 - 桌面客户端（Compose Multiplatform Desktop / JVM）
//
// 目的：**验证 KMP 迁移成果能真跑**。与 :androidApp 共用 :ui-shared 的页面、
// :core 的数据层；Android 门禁保持零回归（本模块不参与 --offline 红线）。
//
// 平台中立性提醒：本模块是**平台宿主**（同 :androidApp），commonMain 规则不适用于它，
// 但通过它编译的 :ui-shared / :core 代码必须过 checkKmpPurity。

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
            // 共享 UI（页面 / 主题）
            implementation(project(":ui-shared"))
            // 数据层（AppStorage / AppFiles / CourseRepository…）
            implementation(project(":core"))
            // 组件库与液态玻璃
            implementation(project(":miuix"))
            implementation(project(":backdrop"))
            // 桌面 Compose 运行时（Window / application）
            implementation(compose.desktop.currentOs)
            // ⚠ 必须显式声明：:core 虽用 `api(libs.kotlinx.datetime)`，但**没有传到桌面端的运行时
            // classpath** —— 打开用了它的页面（AddEditCourseBottomSheet / CourseTimeSettingsScreen 等）
            // 会直接崩：
            //   ClassNotFoundException: kotlinx.datetime.Clock$System / LocalDate
            // 编译期不报错（能解析符号），只有运行时才炸，所以很容易漏。
            implementation(libs.kotlinx.datetime)
        }
    }
}

// ⚠ 强制 kotlinx-datetime 回 0.6.2（与 gradle/libs.versions.toml 一致）。
// 桌面端的 Compose Desktop 依赖树会把 datetime 提升到 **0.7.1**，而 0.7 起
// `Clock` 被移出 kotlinx.datetime（改挂 kotlin.time），`LocalDate.monthNumber` 也改名了 ——
// 本项目代码仍按 0.6.2 的 API 写，于是运行时直接崩：
//   ClassNotFoundException: kotlinx.datetime.Clock$System
// **编译期不报错**（符号能解析），只有点开用了它的页面（课表节数与时间 / 添加课程）才炸。
// Android 侧依赖树不同、没被提升，所以真机不崩 —— 这个坑只在桌面端出现。
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
