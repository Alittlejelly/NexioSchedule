// Nexio课程表 - Gradle 模块配置
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Nexio课程表"
include(":androidApp")
include(":backdrop")
include(":core")
include(":miuix")
// 共享 UI 模块（android + jvm 双目标），Android 与桌面端共用同一批页面。
include(":ui-shared")
include(":desktopApp")
 

