package com.haooz.chedule.data.platform

import kotlin.concurrent.Volatile
import kotlin.random.Random

/**
 * 应用自身的元信息。版本号要平台 API，宿主启动时 [init] 一次
 * （与 [com.haooz.chedule.data.storage.AppStorage.init] 同一套注入模式），未初始化时是 `"unknown"`。
 */
object AppInfo {
    @Volatile
    private var appVersion: String = "unknown"

    fun init(version: String) {
        appVersion = version
    }

    val version: String get() = appVersion
}

/**
 * UUID v4（8-4-4-4-12）。只用于匿名统计的设备标识，不需要密码学强度；
 * 格式必须严格是 v4，后端按 UUID 校验/索引。
 */
fun randomUuidV4(): String {
    val bytes = ByteArray(16)
    Random.nextBytes(bytes)
    // version 4
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    // variant 10xx
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

    val sb = StringBuilder(36)
    for (i in 0 until 16) {
        val v = bytes[i].toInt() and 0xFF
        sb.append(HEX_DIGITS[v ushr 4]).append(HEX_DIGITS[v and 0x0F])
        // 8-4-4-4-12：在第 4、6、8、10 字节后插入连字符
        if (i == 3 || i == 5 || i == 7 || i == 9) sb.append('-')
    }
    return sb.toString()
}

private const val HEX_DIGITS = "0123456789abcdef"
