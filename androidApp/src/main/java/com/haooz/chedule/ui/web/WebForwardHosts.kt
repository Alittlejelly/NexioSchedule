/** 按站点记录「普通静态资源是否走 OkHttp 转发」 */
package com.haooz.chedule.ui.web

import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.NexioLog
import com.haooz.chedule.i18n.AndroidMiscStr

/**
 * 「静态资源是否走 OkHttp 转发（剥离 `X-Requested-With`）」的站点开关，默认关。
 *
 * 必须按站点开，因为两类站点需求相反：
 * - 一类站点因 WebView 自带的 `X-Requested-With: <包名>` 被判"非浏览器"，
 *   降级剥掉 jQuery 等脚本 → `$ is not defined`、页面只剩文字。这头是网络层加的，
 *   JS 补丁碰不到，只能靠 OkHttp 重发剔除。
 * - 另一类站点的 WAF 会因 OkHttp 重发的请求缺完整浏览器属性直接拦截 —— 那类绝不能转发。
 *
 * 默认关（零回归）；检测到脚本缺失时自动开启并按 host 记住。
 */
object WebForwardHosts {

    private const val FILE = "web_forward_hosts"
    private const val KEY = "hosts"
    private const val TAG = "WebForwardHosts"

    private val hosts: MutableSet<String> by lazy {
        AppStorage.store(FILE).getStringSet(KEY, emptySet()).toMutableSet()
    }

    private fun normalize(host: String?): String =
        host?.lowercase()?.removePrefix("www.").orEmpty()

    /** 该 host 是否已开启转发 */
    fun isEnabled(host: String?): Boolean {
        val h = normalize(host)
        return h.isNotEmpty() && h in hosts
    }

    /**
     * 开启并持久化。返回 true 表示本次是新开启（调用方据此决定是否重载页面）；
     * 已开过返回 false，调用方不该再触发重载。
     */
    fun enable(host: String?): Boolean {
        val h = normalize(host)
        if (h.isEmpty() || !hosts.add(h)) return false
        AppStorage.store(FILE).edit { putStringSet(KEY, hosts) }
        NexioLog.w(TAG, AndroidMiscStr.logForwardEnabledN(h))
        return true
    }
}
