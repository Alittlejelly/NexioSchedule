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
        }
    }
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
