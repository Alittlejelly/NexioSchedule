@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.HttpService
import com.haooz.chedule.data.HttpTimeouts
import com.haooz.chedule.data.PrivacyConsent
import com.haooz.chedule.data.createHttpService
import com.haooz.chedule.data.ioDispatcher
import com.haooz.chedule.data.optDouble
import com.haooz.chedule.data.optInt
import com.haooz.chedule.data.optJsonArray
import com.haooz.chedule.data.optJsonObject
import com.haooz.chedule.data.optString
import com.haooz.chedule.data.parseJsonObject
import com.haooz.chedule.ui.activities.today.WeatherData
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS 的今日助手天气 + 定位（对应 Android `TodayAssistant.kt` 里的 `rememberWeather`）。
 *
 * ## 与 Android 的分工
 *
 * 卡片本体与展示逻辑已迁 `:ui-shared` 的 `TodayAssistantCard.kt`；本文件只负责**取数**：
 * CoreLocation 拿坐标 → 共享 `HttpService`（NSURLSession）拉天气 → 包成共享 [WeatherData]。
 * 合约与 Android 一致：`Triple<WeatherData, hasLocationPermission, onRequestLocation>`。
 *
 * ## 与 Android 的行为差异（有意为之）
 *
 * Android 有两套源（`caiyun` 按经纬度 / 中国天气网按城市码）。**iOS 只做经纬度那套**：
 * 城市码源依赖 `getCityCodeMap(context)` 读 Android assets 城市表，iOS 没有这份资产；且默认源
 * 就是 `caiyun`、精度更高 —— 故 iOS 恒走经纬度源，`weather_source` 在 iOS 不参与分支。
 *
 * ## 合规
 *
 * 与 Android 同一条铁律：**用户同意隐私政策前不定位、不请求**；`PrivacyConsent.consented`
 * 是 StateFlow，同意后自动重跑（在 `LaunchedEffect` 的 key 里）。
 */

/** 天气缓存与刷新节流：与 Android 的 `WEATHER_REFRESH_INTERVAL` 同值（30 分钟） */
private const val WEATHER_REFRESH_INTERVAL = 30L * 60L * 1000L
private var lastFetchTime = 0L
private var cachedWeatherIos: WeatherData? = null

private val http: HttpService by lazy {
    createHttpService(HttpTimeouts(connectSeconds = 12, readSeconds = 20))
}

/** 设置页改天气源后作废缓存（与 Android `invalidateWeatherCache` 对应） */
fun invalidateWeatherCacheIos() {
    lastFetchTime = 0L
    cachedWeatherIos = null
}

/**
 * 取天气 + 定位权限状态。
 *
 * @return `(weather, hasLocationPermission, onRequestLocation)`
 */
@Composable
fun rememberWeatherIos(): Triple<WeatherData, Boolean, () -> Unit> {
    // 与 Android 一样读一次天气源偏好（iOS 不做分支，但保留读取以免将来对齐）
    val weatherPrefs = remember { AppStorage.store("weather_prefs") }
    remember { weatherPrefs.getString("weather_source", "caiyun") }

    // 合规：未同意前不定位、不查询；同意后 key 变化自动重跑
    val privacyConsented by PrivacyConsent.consented.collectAsState()

    val locationClient = remember { IosLocationClient() }
    var weather by remember { mutableStateOf(cachedWeatherIos ?: WeatherData()) }
    var hasLocationPermission by remember { mutableStateOf(locationClient.isAuthorized()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(hasLocationPermission, refreshTrigger, privacyConsented) {
        if (!privacyConsented) return@LaunchedEffect
        val now = Clock.System.now().toEpochMilliseconds()
        val cached = cachedWeatherIos
        if (cached != null && now - lastFetchTime < WEATHER_REFRESH_INTERVAL) {
            weather = cached
            return@LaunchedEffect
        }
        lastFetchTime = now

        val coords = if (hasLocationPermission) locationClient.requestLocation() else null
        if (coords == null) {
            weather = WeatherData(needsLocation = true, loaded = true)
            return@LaunchedEffect
        }
        val data = withContext(ioDispatcher) { fetchWeather(coords.first, coords.second) }
        cachedWeatherIos = data
        weather = data
    }

    val onRequestWeather: () -> Unit = {
        if (privacyConsented) {
            if (!hasLocationPermission) {
                locationClient.requestAuthorization { granted -> hasLocationPermission = granted }
            } else {
                lastFetchTime = 0L
                weather = WeatherData(loaded = false)
                refreshTrigger++
            }
        }
    }
    return Triple(weather, hasLocationPermission, onRequestWeather)
}

/**
 * 拉天气（和风/apizero 聚合接口，与 Android `caiyun` 分支同一 URL 与字段）。
 *
 * 失败一律返回 `apiError = true` 的已加载态（同 Android 兜底：卡片显示「获取失败」而不是一直转圈）。
 */
private suspend fun fetchWeather(lng: Double, lat: Double): WeatherData {
    val error = WeatherData(loaded = true, apiError = true)
    return try {
        val resp = http.get("https://v1.apizero.cn/api/weather?location=$lng,$lat")
        if (!resp.isSuccessful) return error
        val json = parseJsonObject(resp.text)
        if (json.optInt("code", -1) != 0) return error
        val data = json.optJsonObject("data") ?: return error
        val summary = data.optJsonObject("summary") ?: return error
        val temperature = summary.optDouble("temperature", Double.NaN).toFloat()
        val skycon = summary.optString("skycon")
        val notice = data.optString("forecast_keypoint")
        val astro = data.optJsonObject("daily")
            ?.optJsonArray("astro")
            ?.optJsonObject(0)
        val sunrise = astro?.optJsonObject("sunrise")?.optString("time").orEmpty()
        val sunset = astro?.optJsonObject("sunset")?.optString("time").orEmpty()
        WeatherData(
            temperature = temperature,
            weatherType = skyconToChinese(skycon),
            sunset = sunset,
            sunrise = sunrise,
            notice = notice,
            loaded = true,
        )
    } catch (_: Exception) {
        error
    }
}

/**
 * 和风 `skycon`（英文枚举）→ 中国天气网风格的中文描述。
 *
 * 卡片的 `getWeatherIconRes` 按**中文关键词**匹配图标，不转中文会全部落到兜底的「晴」。
 */
private fun skyconToChinese(skycon: String): String = when (skycon.uppercase()) {
    "CLEAR_DAY" -> "晴"
    "CLEAR_NIGHT" -> "晴"
    "PARTLY_CLOUDY_DAY" -> "多云"
    "PARTLY_CLOUDY_NIGHT" -> "多云"
    "CLOUDY" -> "阴"
    "LIGHT_HAZE", "MODERATE_HAZE", "HEAVY_HAZE" -> "霾"
    "LIGHT_RAIN", "MODERATE_RAIN", "HEAVY_RAIN", "STORM_RAIN" -> "雨"
    "FOG" -> "雾"
    "LIGHT_SNOW", "MODERATE_SNOW", "HEAVY_SNOW", "STORM_SNOW" -> "雪"
    "DUST", "SAND" -> "浮尘"
    "WIND" -> "晴"
    else -> skycon
}

/**
 * CoreLocation 封装。
 *
 * ⚠ `CLLocationManager.delegate` 是**弱引用**，delegate 必须由本类强引用持有，
 * 否则授权/定位回调永远不会来（表现为「点了授权没反应」）。
 */
private class IosLocationClient {

    private val manager = CLLocationManager()
    private var delegate: LocationDelegate? = null
    private var pendingLocation: ((Pair<Double, Double>?) -> Unit)? = null
    private var pendingAuth: ((Boolean) -> Unit)? = null

    init {
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
        val d = LocationDelegate(
            onLocations = { locations ->
                val loc = locations.filterIsInstance<CLLocation>().lastOrNull()
                val cb = pendingLocation
                pendingLocation = null
                cb?.invoke(loc?.let { pairOf(it) })
            },
            onError = {
                val cb = pendingLocation
                pendingLocation = null
                cb?.invoke(null)
            },
            onAuthChanged = { granted ->
                pendingAuth?.invoke(granted)
            },
        )
        delegate = d
        manager.delegate = d
    }

    fun isAuthorized(): Boolean = isGranted(CLLocationManager.authorizationStatus())

    fun requestAuthorization(onResult: (Boolean) -> Unit) {
        pendingAuth = onResult
        manager.requestWhenInUseAuthorization()
    }

    /**
     * 取一次当前位置（挂起）：先看系统缓存的 `manager.location` —— 天气只要城市级精度，
     * 缓存通常就够，省掉一次几秒的定位等待；没有才发真实请求。
     */
    suspend fun requestLocation(): Pair<Double, Double>? {
        manager.location?.let { cached ->
            val pair = pairOf(cached)
            if (pair != null) return pair
        }
        return suspendCancellableCoroutine { cont ->
            pendingLocation = { pair -> if (cont.isActive) cont.resume(pair) }
            manager.requestLocation()
        }
    }

    private fun pairOf(location: CLLocation): Pair<Double, Double>? {
        val coord = location.coordinate
        val pair = coord.useContents { latitude to longitude }
        // 0,0 是 CoreLocation 的「无效坐标」哨兵值
        if (pair.first == 0.0 && pair.second == 0.0) return null
        return pair
    }

    private fun isGranted(status: Int): Boolean =
        status == kCLAuthorizationStatusAuthorizedWhenInUse ||
            status == kCLAuthorizationStatusAuthorizedAlways
}

private class LocationDelegate(
    private val onLocations: (List<*>) -> Unit,
    private val onError: () -> Unit,
    private val onAuthChanged: (Boolean) -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        onLocations(didUpdateLocations)
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        onError()
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        val status = CLLocationManager.authorizationStatus()
        val granted = status == kCLAuthorizationStatusAuthorizedWhenInUse ||
            status == kCLAuthorizationStatusAuthorizedAlways
        onAuthChanged(granted)
    }
}
