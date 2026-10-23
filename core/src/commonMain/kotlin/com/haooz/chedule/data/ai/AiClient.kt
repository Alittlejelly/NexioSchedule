package com.haooz.chedule.data.ai

import com.haooz.chedule.data.HttpService
import com.haooz.chedule.data.StatsReporter
import com.haooz.chedule.data.jsonToPlainValue
import com.haooz.chedule.data.optInt
import com.haooz.chedule.data.optJsonArray
import com.haooz.chedule.data.optJsonObject
import com.haooz.chedule.data.optString
import com.haooz.chedule.data.parseJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 云端 AI 能力概览。[tasks] 是服务端当前注册的任务名，App 据此决定露出哪些入口。 */
data class AiCapabilities(
    val enabled: Boolean,
    val tasks: List<String>,
)

/**
 * AI 调用的结果。
 *
 * 刻意不用「失败一律 null」：这条链路跨了加密、HTTP、服务端、模型四段，
 * 全都返回 null 时用户只看到一句「失败」，分不清是没网、没配公钥、服务端拒了
 * 还是模型没按格式回。每种失败都有自己的说法，界面直接显示 [Error.message]。
 */
sealed class AiResult<out T> {
    /** @param remaining 今天还剩几次；服务端没给就是 null */
    data class Ok<T>(val value: T, val remaining: Int? = null) : AiResult<T>()
    data class Error(val message: String) : AiResult<Nothing>()
}

/**
 * 云端 AI 客户端。
 *
 * ## 为什么请求都发给自己的服务器
 *
 * apikey 只存服务端（加密落盘），App 从不接触，所以没有「配置 AI 地址/模型」的入口
 * —— 那些都在服务端改，改完所有客户端立刻生效，不用发版。也因此 `/api/ai/task`
 * 只接受**预设任务**（prompt 模板在云端），不能拿它当通用 chat 代理。
 *
 * ## 加密
 *
 * 每条请求都现开一个 [AiCryptoSession]：会话密钥一次性，请求与响应共用同一把。
 */
class AiClient(
    private val http: HttpService,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    /**
     * 缩略态文案精简：把课程名和上课地点一起交给云端 AI，拿回一张「原名=缩写」对照。
     *
     * 两类条目混在一张表里：客户端做的是纯文本替换，键互不重叠，没必要分两张表。
     */
    suspend fun shrinkLabels(
        classrooms: List<String>,
        courses: List<String>,
    ): AiResult<List<Pair<String, String>>> {
        val cleanRooms = classrooms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val cleanCourses = courses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanRooms.isEmpty() && cleanCourses.isEmpty()) {
            return AiResult.Error("课表里还没有课程名和地点")
        }

        val body = buildJsonObject {
            put("task", TASK_SHRINK_CLASSROOMS)
            // 服务端按这个 ID 算每天用量（与统计上报同一个 ID）
            put("device_id", StatsReporter.ensureDeviceId())
            put("args", buildJsonObject {
                put("courses", buildJsonArray { cleanCourses.forEach { add(JsonPrimitive(it)) } })
                put("classrooms", buildJsonArray { cleanRooms.forEach { add(JsonPrimitive(it)) } })
            })
        }

        val plain = when (val r = roundTrip("$baseUrl/task", body)) {
            is AiResult.Ok -> r.value
            is AiResult.Error -> return r
        }

        val obj = runCatching { parseJsonObject(plain) }
            .getOrElse { return AiResult.Error("服务端响应不是 JSON") }
        if (!obj.optString("ok").toBoolean()) {
            return AiResult.Error(obj.optString("error").ifEmpty { "服务端说这次没成" })
        }
        // lines 存在但为空是正常结果：课表里的楼名本来就短（「博105」「安503」），
        // 没什么可缩的。这跟「服务端没给结果」要分开。
        val lines = obj.optJsonObject("result")?.optJsonArray("lines")
            ?: return AiResult.Error("服务端没返回对照表")

        val pairs = lines.mapNotNull { element ->
            val line = jsonToPlainValue(element) as? String ?: return@mapNotNull null
            val sep = line.indexOf('=')
            if (sep <= 0) return@mapNotNull null
            val from = line.substring(0, sep).trim()
            val to = line.substring(sep + 1).trim()
            if (from.isEmpty() || to.isEmpty()) null else from to to
        }
        val remaining = obj.optInt("remaining", -1).takeIf { it >= 0 }
        return AiResult.Ok(pairs, remaining)
    }

    /**
     * 发一次加密请求并解开响应。
     *
     * 响应体既可能是 `{iv, ct}`（正常），也可能是服务端的明文错误体（信封都解不开时）。
     * 后者一律当失败 —— 拿不到会话密钥就没有可读内容。
     */
    private suspend fun roundTrip(path: String, body: JsonObject): AiResult<String> {
        if (!AiServerKey.isConfigured()) {
            return AiResult.Error("这个版本还没内置服务端公钥")
        }

        // 会话密钥一次性：加密请求与解密响应必须是同一个 session
        val session = try {
            AiCryptoSession(AiServerKey.PUBLIC_KEY_PEM)
        } catch (e: Exception) {
            return AiResult.Error("加密会话创建失败：${e.describe()}")
        }
        val envelope = try {
            session.encrypt(body.toString())
        } catch (e: Exception) {
            return AiResult.Error("请求加密失败：${e.describe()}")
        }

        val payload = buildJsonObject {
            put("ek", envelope.ek)
            put("iv", envelope.iv)
            put("ct", envelope.ct)
        }.toString()

        val result = try {
            http.post(path, payload)
        } catch (e: Exception) {
            return AiResult.Error("网络请求失败：${e.describe()}")
        }
        if (!result.isSuccessful) {
            return AiResult.Error("服务端返回 ${result.code}")
        }

        val response = runCatching { parseJsonObject(result.text) }.getOrNull()
            ?: return AiResult.Error("响应不是 JSON")
        val iv = response.optString("iv")
        val ct = response.optString("ct")
        if (iv.isEmpty() || ct.isEmpty()) {
            // 走到这里说明服务端是明文回的错误体，把它带出来
            val serverError = response.optString("error")
            return AiResult.Error(serverError.ifEmpty { "响应里没有密文" })
        }
        return try {
            AiResult.Ok(session.decrypt(iv, ct))
        } catch (e: Exception) {
            AiResult.Error("响应解密失败：${e.describe()}")
        }
    }

    private fun Exception.describe(): String = message ?: this::class.simpleName ?: "未知错误"

    companion object {
        /** 与 `NoticeFetcher` / `StatsReporter` 等服务地址同源。 */
        const val DEFAULT_BASE_URL = "http://182.92.193.223:3000/api/ai"

        /** 服务端 `AI_TASKS` 里上课地点精简的任务名，逐字不能改。 */
        const val TASK_SHRINK_CLASSROOMS = "shrink_classrooms"
    }
}
