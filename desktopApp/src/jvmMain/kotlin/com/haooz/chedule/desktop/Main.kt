// Nexio课程表 - 桌面客户端入口（Compose Multiplatform Desktop / JVM）
//
// 目的：**验证 KMP 迁移成果能真跑**。与 :androidApp 共用 :ui-shared 的页面、
// :core 的数据层；Android 门禁保持零回归（本模块不参与 --offline 红线）。
//
// 平台中立性提醒：本模块是**平台宿主**（同 :androidApp），commonMain 规则不适用于它，
// 但通过它编译的 :ui-shared / :core 代码必须过 checkKmpPurity。
package com.haooz.chedule.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.haooz.chedule.data.AppFiles
import com.haooz.chedule.data.AppInfo
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.FileAppFile
import com.haooz.chedule.data.FileKeyValueStore
import com.haooz.chedule.ui.activities.ChangelogScreen
import com.haooz.chedule.ui.activities.CommunicationScreen
import com.haooz.chedule.ui.activities.LicenseScreen
import com.haooz.chedule.ui.activities.PrivacyPolicyScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import java.io.File
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 桌面端当前阶段的形态：**迁移验证壳**。
 *
 * 每迁入一批页面，这里就多一批可点开的真实页面 —— 它是「迁移可用、可以跑」的
 * 直接证据。全部页面迁完后，本壳由正式的路由（AppRouter）替换。
 */
fun main() {
    // ── 启动注入（对应 Android 的 NexioApplication.onCreate 三个 init）──────────
    // 1. 存储：按名字分文件（17 个偏好名，绝不合并），JSON + 类型标签持久化
    val dataDir = File(
        System.getProperty("user.home"),
        ".nexio-chedule",   // 桌面数据目录；Android 的 17 个偏好文件名原样沿用
    )
    dataDir.mkdirs()
    AppStorage.init { name -> FileKeyValueStore(File(dataDir, "$name.json")) }

    // 2. 版本号：桌面读不到 PackageManager，直接给（上报的 app_version 用）
    AppInfo.init("1.6.4-desktop")

    // 3. 文件系统 + 内置资源：root 指向应用数据目录；readAsset 从 classpath 读
    //    （Android 侧读 assets，桌面读 resources —— 同一契约，平台各自实现）
    AppFiles.init(
        root = FileAppFile(dataDir),
        readAsset = { path ->
            val stream = object {}.javaClass.classLoader.getResourceAsStream(path)
                ?: throw IllegalArgumentException("内置资源缺失: $path")
            stream.use { it.readBytes() }
        },
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Nexio 课程表",
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            CourseScheduleTheme {
                DesktopShell()
            }
        }
    }
}

/** 页面栈（极简：首页 + 若干详情页，够当前验证阶段用）。 */
private enum class DesktopPage(val title: String) {
    Home("Nexio 课程表 · 桌面验证"),
    Changelog("更新日志"),
    License("开源协议"),
    PrivacyPolicy("隐私政策"),
    Communication("交流与反馈"),
}

@Composable
private fun DesktopShell() {
    var page by remember { mutableStateOf(DesktopPage.Home) }

    Scaffold(
        topBar = {},
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (page) {
                DesktopPage.Home -> HomeContent(onOpen = { page = it })
                DesktopPage.Changelog -> PageContainer(onBack = { page = DesktopPage.Home }) {
                    ChangelogScreen()
                }
                DesktopPage.License -> PageContainer(onBack = { page = DesktopPage.Home }) {
                    LicenseScreen()
                }
                DesktopPage.PrivacyPolicy -> PageContainer(onBack = { page = DesktopPage.Home }) {
                    PrivacyPolicyScreen()
                }
                DesktopPage.Communication -> PageContainer(onBack = { page = DesktopPage.Home }) {
                    CommunicationScreen()
                }
            }
        }
    }
}

/**
 * 首页：列出**已迁入 :ui-shared 的页面**。
 *
 * 每个入口渲染的都是与 Android 端完全相同的一份代码（同一个 composable 函数）——
 * 点开能显示，就证明这批迁移在第二个平台上真实可用。
 */
@Composable
private fun HomeContent(onOpen: (DesktopPage) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "已迁移到 :ui-shared 的页面",
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        Text(
            text = "以下每个入口渲染的都是与 Android 端共用的同一份代码。" +
                "迁移批次推进时，这里会同步增加入口。",
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
        DesktopPage.entries
            .filter { it != DesktopPage.Home }
            .forEach { target ->
                Button(
                    onClick = { onOpen(target) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = target.title)
                }
            }
    }
}

/** 详情页容器：内容之上浮一个返回按钮（正式版由 DocumentPageScaffold 接管）。 */
@Composable
private fun PageContainer(onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        Button(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            Text(text = "返回")
        }
    }
}
