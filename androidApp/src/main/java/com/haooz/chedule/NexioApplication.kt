package com.haooz.chedule

import android.app.Application
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.NexioLog
import com.haooz.chedule.data.SqliteStorage
import com.haooz.chedule.data.asKeyValueStore
import com.haooz.chedule.platform.AndroidScheduleEffects
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Application 阶段初始化存储与预热。
 *
 * 存储：走 SQLite（[SqliteStorage]），每个偏好名映射成表里的一个命名空间，启动时一次性从
 * 同名 SharedPreferences 导入并自校验。**任何异常都回退 SharedPreferences** —— 存储层坏掉
 * 不该让用户开不了应用。
 *
 * 预热：命名空间第一次被访问要读一遍全量行，冷启动主线程同步读几个大命名空间（课程 JSON
 * 可达数百 KB）会被卡住。提前在 IO 线程把热路径的命名空间读进内存，主线程首次读取即纯内存操作。
 */
class NexioApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 必须最先初始化：之后 AppStorage.store(...) 才可用；工厂按名转发各自命名空间，不在本处汇总以免抄错键名。
        com.haooz.chedule.data.AppStorage.init { name ->
            runCatching {
                // mirror：过渡期把写入同步回 SharedPreferences，供尚未改走 AppStorage 的直读点
                // （提醒/灵动岛/勿扰那几个 Helper）使用。这些点全部迁移完成后传 null 即可彻底断开 SP。
                SqliteStorage.store(
                    context = this,
                    namespace = name,
                    mirror = getSharedPreferences(name, MODE_PRIVATE).asKeyValueStore(),
                )
            }.getOrElse { e ->
                NexioLog.w(TAG, "SQLite 键值存储不可用，$name 回退 SharedPreferences", e)
                getSharedPreferences(name, MODE_PRIVATE).asKeyValueStore()
            }
        }
        // 语言镜像尽早就位：小部件/通知在首帧前取词（DisplayLang）依赖它；与 CourseScheduleApp 里的恢复同值。
        com.haooz.chedule.i18n.AppLanguage.init(com.haooz.chedule.i18n.LanguageStore.load())
        // :core 只认 showToast(...)，Android 在此接线。用 applicationContext 避免 Toast 挂住 Activity 实例；时长语义与迁移前一致。
        com.haooz.chedule.data.initToast { message, long ->
            android.widget.Toast.makeText(
                this,
                message,
                if (long) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
        // :core 无法读 PackageManager，启动时注入版本号；上报负载的 app_version 依赖它，漏掉会成 "unknown"。
        com.haooz.chedule.data.AppInfo.init(
            runCatching {
                packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull() ?: "unknown",
        )
        // :core 只认接口，Android 用 File 实现。readAsset 走 assets 供内置学校索引首启引导；失败让它抛，由 SchoolRepository 打日志跳过引导。
        com.haooz.chedule.data.AppFiles.init(
            root = com.haooz.chedule.data.FileAppFile(filesDir),
            readAsset = { name -> assets.open(name).use { it.readBytes() } },
        )
        // 尽早初始化，主界面首帧可读取壁纸与卡片参数。
        com.haooz.chedule.data.ScheduleAppearance.init(this)
        // WallpaperStore 走「接口 + 启动注入」，Android 原样委托 ScheduleAppearance（降采样/LruCache/WebP 80 口径不变）。必须在 ScheduleAppearance.init 之后。
        val wallpaperBackend = com.haooz.chedule.data.AndroidWallpaperBackend()
        com.haooz.chedule.data.WallpaperStore.backend = wallpaperBackend
        val wallpaperPreload = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "nexio-wallpaper-preload")
        }.submit(java.util.concurrent.Callable {
            com.haooz.chedule.data.AppearanceStore.currentStyle()
            runCatching { wallpaperBackend.loadNow() }.getOrNull()
        })
        com.haooz.chedule.data.WallpaperStore.synchronousLoader = {
            runCatching { wallpaperPreload.get(3, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull()
        }
        // 课程变更后的平台副作用
        com.haooz.chedule.data.ScheduleEffects.init(AndroidScheduleEffects(this))
        // 同步预测性返回动画开关到全局单例。
        PredictiveBackSettings.enabled = AppStorage.store("app_preferences")
            .getBoolean(PredictiveBackSettings.KEY_PREDICTIVE_BACK_ANIMATION, true)
        // 加载应用材质档位。
        com.haooz.chedule.ui.utils.AppMaterialSettings.load()
        com.haooz.chedule.ui.utils.CrashLogHelper.install(this)
        // FeatureLog 已抽 :core，轨迹记录在此接回 CrashLogHelper。必须在 install 之后；未注入静默丢弃但录制轨迹会丢。
        com.haooz.chedule.ui.utils.FeatureLog.init(
            trace = { feature, event, detail ->
                com.haooz.chedule.ui.utils.CrashLogHelper.trace(feature, event, detail)
            },
            recording = { feature, event, detail ->
                com.haooz.chedule.ui.utils.CrashLogHelper.traceRecording(feature, event, detail)
            },
            isRecording = { com.haooz.chedule.ui.utils.CrashLogHelper.isRecording },
        )
        warmUpStorage()
        cleanupTransientFiles()
        initWearableSync()
        enableWebViewRemoteDebugging()
        // 调休映射迁移：旧版存「周次+星期」相对课表会错位，换算成绝对日期后按当前课表实时折算。后台线程执行避免拖慢冷启动。
        Thread {
            // 日期解析在此注入（:core 不能反向依赖 :app 的 CourseRepository）。仓储构造失败则整段跳过，与原实现一致。
            runCatching {
                val repository = com.haooz.chedule.data.CourseRepository()
                com.haooz.chedule.data.HolidayManager.migrateLegacyFollowDates { week, weekday ->
                    runCatching { repository.dateForTeachingWeekDay(week, weekday) }.getOrNull()
                }
            }
        }.start()
    }

    /** 仅 debuggable 包开启 WebView 远程调试（chrome://inspect），排查资源加载问题时可直接看到每个请求状态码/MIME。 */
    private fun enableWebViewRemoteDebugging() {
        runCatching {
            val debuggable =
                (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (debuggable) android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    /** 手表课表推送：启动注册，课程变更走 CourseRepository 监听 */
    private fun initWearableSync() {
        runCatching {
            com.haooz.chedule.wearable.WearableScheduleSync.init(this)
            val repo = com.haooz.chedule.data.CourseRepository.getInstance()
            repo.addCourseChangedListener { _, _ ->
                com.haooz.chedule.wearable.WearableScheduleSync.onScheduleChanged("course-change")
            }
        }
    }

    /** 异步预热高频命名空间（不读具体值，只让它把行载入内存）；IO 线程执行，不阻塞主线程。 */
    private fun warmUpStorage() {
        // 按启动路径上的命中顺序排列：最大的课程数据优先
        val names = arrayOf(
            "appearance_settings",     // 壁纸变换 + 卡片参数
            "course_schedule_prefs",   // 课程 / 设置（体积最大）
            "app_preferences",         // 隐藏后台等主界面开关
            "course_reminder_prefs",   // 提醒开关（startReminderService 会读）
            "app_theme_prefs"          // 主题模式（首帧计算深浅色需要）
        )
        CoroutineScope(Dispatchers.IO).launch {
            names.forEach { name ->
                runCatching {
                    // 走 AppStorage 才有意义：直连 SharedPreferences 就绕过了新存储，白预热一次。
                    // 首次调用会顺带完成该命名空间的一次性导入（含自校验）。
                    AppStorage.store(name).all()
                }
            }
        }
    }

    /** 清理分享卡片与旧版更新 APK，避免 cache/files 无界膨胀 */
    private fun cleanupTransientFiles() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val now = System.currentTimeMillis()
                val dayMs = 24L * 60 * 60 * 1000
                File(cacheDir, "share").listFiles()?.forEach { file ->
                    if (file.isFile &&
                        file.name.startsWith("share_") &&
                        now - file.lastModified() > dayMs
                    ) {
                        file.delete()
                    }
                }
                // APK 沿用检查更新策略：按 latest_tag 保留完整包，不按 mtime 只留最新。
                com.haooz.chedule.ui.utils.UpdateChecker.cleanupTransientApks(this@NexioApplication)
            }
        }
    }

    private companion object {
        const val TAG = "NexioApplication"
    }
}
