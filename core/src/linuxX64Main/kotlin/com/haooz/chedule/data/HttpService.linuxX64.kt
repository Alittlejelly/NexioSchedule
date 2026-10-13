package com.haooz.chedule.data

/**
 * Linux/Native 的 HTTP 实现 —— **占位：调用即抛异常**。
 *
 * `linuxX64` 只作编译门禁（拦截 commonMain 混入 JVM 专有 API），不承载线上流量。
 *
 * ⚠ iOS 与 Linux 不能共存同一个 actual：`iosMain` 已有真正的 `NSURLSession` 实现
 * （见 `iosMain/HttpService.ios.kt`），若本占位放在 `nativeMain`，iOS 目标会同时看到
 * 两个 actual 而编译失败。新增非 Apple 的 Native 目标会继承本占位，需另补平台实现。
 */
actual fun createHttpService(timeouts: HttpTimeouts): HttpService =
    LinuxUnimplementedHttpService(timeouts)

private class LinuxUnimplementedHttpService(
    @Suppress("unused") private val timeouts: HttpTimeouts,
) : HttpService {
    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBytes: Long,
    ): HttpResult = fail()

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>,
    ): HttpResult = fail()

    override suspend fun request(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
    ): HttpResult = fail()

    private fun fail(): Nothing = throw NotImplementedError(
        "linuxX64 仅作编译门禁目标，不承载网络调用：OkHttp 无 Native 版本，" +
            "iOS 侧见 iosMain/HttpService.ios.kt（NSURLSession）。详见本文件顶部说明。"
    )
}
