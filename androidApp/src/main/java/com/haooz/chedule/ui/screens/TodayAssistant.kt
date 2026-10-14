package com.haooz.chedule.ui.screens

// ════════════════════════════════════════════════════════════════════════
//  今日助手卡 · Android 侧数据来源
//
//  卡片本体与纯逻辑已迁 `:ui-shared` 的 `TodayAssistantCard.kt`（同包，不同文件）。
//  本文件只剩**平台专有部分**：定位（LocationManager / Geocoder / 权限）、
//  天气 HTTP（OkHttp）、JSON（Gson）、缓存与刷新节流。
//
//  宿主（MainActivity）用法：
//      todayAssistantCard = { d ->
//          val (weather, hasPerm, request) = rememberWeather()
//          TodayAssistantCard(weather, hasPerm, request, d.courses, …,
//                              fireworksOverlay = { t, m -> FireworksOverlay(t, m) })
//      }
// ════════════════════════════════════════════════════════════════════════

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.gson.Gson
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.getStringOrNull
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// 供设置页作废缓存，下次进入今日页按新设置重拉
fun invalidateWeatherCache() {
    lastWeatherFetchTime = 0L
    cachedWeather = null
}
private val httpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .build()
private var lastWeatherFetchTime = 0L
private var cachedWeather: WeatherData? = null
private const val WEATHER_REFRESH_INTERVAL = 2 * 60 * 1000L

// assets/city_code.json 懒加载后进程内缓存
private var cityCodeMap: Map<String, String>? = null
private val cityCodeMapLock = Any()
private fun getCityCodeMap(context: Context): Map<String, String> {
    cityCodeMap?.let { return it }
    synchronized(cityCodeMapLock) {
        cityCodeMap?.let { return it }
        val map = mutableMapOf<String, String>()
        try {
            context.assets.open("city_code.json").bufferedReader().use { reader ->
                @Suppress("UNCHECKED_CAST")
                val root = Gson().fromJson(reader.readText(), Map::class.java) as? Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val provinces = root?.get("城市代码") as? List<Map<String, Any>>
                provinces?.forEach { province ->
                    @Suppress("UNCHECKED_CAST")
                    (province["市"] as? List<Map<String, Any>>)?.forEach { city ->
                        val name = city["市名"] as? String
                        val code = city["编码"] as? String
                        if (!name.isNullOrBlank() && !code.isNullOrBlank()) map[name] = code
                    }
                }
            }
        } catch (_: Exception) { }
        cityCodeMap = map
        return map
    }
}
// GPS 优先，回退 NETWORK；无权限/未开定位/无记录返回 null
@Suppress("MissingPermission")
private fun getLastKnownLocation(context: Context, useLocation: Boolean): android.location.Location? {
    if (!useLocation) return null
    val fineGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted && !coarseGranted) return null
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val gps = if (fineGranted && lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
        val net = if (coarseGranted && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
        gps ?: net
    } catch (_: Exception) {
        null
    }
}

// 记录上次定位，供关闭定位权限后回退
private fun saveLastLocation(
    context: Context,
    name: String,
    cityCode: String? = null,
    lngLat: String? = null
) {
    AppStorage.store("weather_prefs").edit {
        putString("last_location_name", name)
        if (cityCode != null) putString("last_city_code", cityCode)
        if (lngLat != null) putString("last_lnglat", lngLat)
    }
}

// 上次定位经纬度（无权限时彩云回退用）
private fun readLastLngLat(context: Context): Pair<Double, Double>? {
    val s = AppStorage.store("weather_prefs")
        .getStringOrNull("last_lnglat") ?: return null
    val parts = s.split(",")
    if (parts.size != 2) return null
    val lng = parts[0].toDoubleOrNull() ?: return null
    val lat = parts[1].toDoubleOrNull() ?: return null
    return Pair(lng, lat)
}

// 定位 → Geocoder → citykey；无定位时回退上次 citykey
private fun resolveCityCode(context: Context, useLocation: Boolean): String? {
    val map = getCityCodeMap(context)
    val loc = getLastKnownLocation(context, useLocation)
    if (loc == null) {
        return AppStorage.store("weather_prefs")
            .getStringOrNull("last_city_code")
    }
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
        val addr = addresses?.firstOrNull()
        // locality > subAdminArea > adminArea（直辖市兜底）
        val candidates = listOfNotNull(
            addr?.locality, addr?.subAdminArea, addr?.adminArea
        ).filter { it.isNotBlank() }
        for (raw in candidates) {
            val cleaned = raw.removeSuffix("市")
                .removeSuffix("地区").removeSuffix("自治州").removeSuffix("盟")
            // 直辖市 locality 可能是区名，剥「区」后也能匹配区码
            val cleanedDistrict = if (cleaned.endsWith("区") && cleaned.length > 2)
                cleaned.removeSuffix("区") else cleaned
            val code = map[cleanedDistrict] ?: map[cleaned] ?: map[raw]
            if (code != null) {
                saveLastLocation(context, raw, cityCode = code)
                return code
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

// 彩云源取经纬度；无定位时回退上次经纬度
private fun resolveCoordinates(context: Context, useLocation: Boolean): Pair<Double, Double>? {
    val loc = getLastKnownLocation(context, useLocation)
    if (loc == null) return readLastLngLat(context)
    val lng = loc.longitude
    val lat = loc.latitude
    val name = try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        val addr = addresses?.firstOrNull()
        listOfNotNull(addr?.locality, addr?.subAdminArea, addr?.adminArea)
            .firstOrNull { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
    saveLastLocation(context, name ?: "%.2f,%.2f".format(lat, lng), lngLat = "$lng,$lat")
    return Pair(lng, lat) // 彩云 URL 经度在前
}

@Composable
fun rememberWeather(): Triple<WeatherData, Boolean, () -> Unit> {
    val context = LocalContext.current
    val weatherPrefs = remember { AppStorage.store("weather_prefs") }
    val weatherSource = weatherPrefs.getString("weather_source", "caiyun")
    // 合规：可观察的隐私同意状态；未同意前不定位/不查询，同意后自动重新执行
    val privacyConsented by com.haooz.chedule.data.PrivacyConsent.consented.collectAsState()

    var weather by remember { mutableStateOf(cachedWeather ?: WeatherData()) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants.values.any { it }
        if (hasLocationPermission) lastWeatherFetchTime = 0L // 授权后强制刷新
    }

    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(hasLocationPermission, weatherSource, refreshTrigger, privacyConsented) {
        // 合规：用户同意隐私政策前，不进行定位与天气数据获取；同意后会因 key 变化自动重跑
        if (!privacyConsented) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - lastWeatherFetchTime < WEATHER_REFRESH_INTERVAL && cachedWeather != null) {
            weather = cachedWeather!!
            return@LaunchedEffect
        }
        lastWeatherFetchTime = now
        withContext(Dispatchers.IO) {
            when (weatherSource) {
                "caiyun" -> {
                    val coords = resolveCoordinates(context, hasLocationPermission)
                    if (coords == null) {
                        weather = WeatherData(needsLocation = true, loaded = true)
                        return@withContext
                    }
                    try {
                        val (lng, lat) = coords
                        val url = "https://v1.apizero.cn/api/weather?location=$lng,$lat"
                        val request = Request.Builder().url(url).build()
                        httpClient.newCall(request).execute().use { resp ->
                            val body = resp.body?.string() ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val json = Gson().fromJson(body, Map::class.java) as? Map<String, Any> ?: return@use
                            val code = (json["code"] as? Number)?.toInt() ?: -1
                            if (code != 0) {
                                val errorData = WeatherData(loaded = true, apiError = true)
                                cachedWeather = errorData
                                weather = errorData
                                return@use
                            }
                            @Suppress("UNCHECKED_CAST")
                            val data = json["data"] as? Map<String, Any> ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val summary = data["summary"] as? Map<String, Any> ?: return@use
                            val temp = (summary["temperature"] as? Number)?.toFloat() ?: Float.NaN
                            val type = (summary["skycon"] as? String).orEmpty()
                            val notice = (data["forecast_keypoint"] as? String).orEmpty()
                            @Suppress("UNCHECKED_CAST")
                            val daily = data["daily"] as? Map<String, Any> ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val astroList = daily["astro"] as? List<Map<String, Any>> ?: emptyList()
                            val astro = astroList.firstOrNull() ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val sunriseObj = astro["sunrise"] as? Map<String, Any>
                            @Suppress("UNCHECKED_CAST")
                            val sunsetObj = astro["sunset"] as? Map<String, Any>
                            val sunrise = (sunriseObj?.get("time") as? String).orEmpty()
                            val sunset = (sunsetObj?.get("time") as? String).orEmpty()
                            val newData = WeatherData(temp, type, sunset, sunrise, notice, true)
                            cachedWeather = newData
                            weather = newData
                        }
                    } catch (_: Exception) {
                        val errorData = WeatherData(loaded = true, apiError = true)
                        cachedWeather = errorData
                        weather = errorData
                    }
                }
                else -> {
                    // 中国天气网：定位 → citykey → 拉取
                    val cityCode = resolveCityCode(context, hasLocationPermission)
                    if (cityCode == null) {
                        weather = WeatherData(needsLocation = true, loaded = true)
                        return@withContext
                    }
                    try {
                        val url = "http://t.weather.itboy.net/api/weather/city/$cityCode"
                        val request = Request.Builder().url(url).build()
                        httpClient.newCall(request).execute().use { resp ->
                            val body = resp.body?.string() ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val json = Gson().fromJson(body, Map::class.java) as? Map<String, Any> ?: return@use
                            @Suppress("UNCHECKED_CAST")
                            val data = json["data"] as? Map<String, Any> ?: return@use
                            val wendu = (data["wendu"] as? String).orEmpty()
                            val temp = wendu.substringBefore("℃").trim().toFloatOrNull() ?: Float.NaN
                            @Suppress("UNCHECKED_CAST")
                            val forecast = data["forecast"] as? List<Map<String, Any>> ?: emptyList()
                            val today = forecast.firstOrNull() ?: return@use
                            val type = (today["type"] as? String).orEmpty()
                            val sunrise = (today["sunrise"] as? String).orEmpty()
                            val sunset = (today["sunset"] as? String).orEmpty()
                            val notice = (today["notice"] as? String).orEmpty()
                            val newData = WeatherData(temp, type, sunset, sunrise, notice, true)
                            cachedWeather = newData
                            weather = newData
                        }
                    } catch (_: Exception) {
                        val errorData = WeatherData(loaded = true, apiError = true)
                        cachedWeather = errorData
                        weather = errorData
                    }
                }
            }
        }
    }
    // 合规：未同意隐私政策前不申请定位权限，也不触发天气查询
    val onRequestWeather: () -> Unit = {
        if (privacyConsented) {
            if (!hasLocationPermission) {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            } else {
                lastWeatherFetchTime = 0L
                weather = WeatherData(loaded = false)
                refreshTrigger++
            }
        }
    }
    return Triple(weather, hasLocationPermission, onRequestWeather)
}
