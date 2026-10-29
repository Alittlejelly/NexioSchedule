package com.haooz.chedule.data

import kotlin.concurrent.Volatile

/**
 * 全局存储入口：启动时 [init] 一次，业务用 [store] 按偏好名取读写通道。
 *
 * 每个偏好名是一个独立命名空间（Android 侧是同一张表的 `namespace` 分区）。
 * 未初始化时读取抛异常，不静默返回空数据。
 */
object AppStorage {
    // 必须用 kotlin.concurrent.Volatile：裸 @Volatile 在 Kotlin/Native 与 JS 上无法解析
    @Volatile
    private var factory: ((String) -> KeyValueStore)? = null

    /** 实例缓存。copy-on-write：每个名字只写一次，读无锁。 */
    @Volatile
    private var cache: Map<String, KeyValueStore> = emptyMap()

    /** 是否已初始化。 */
    val isReady: Boolean get() = factory != null

    /** @param factory 由平台侧提供，见 `NexioApplication`。 */
    fun init(factory: (String) -> KeyValueStore) {
        this.factory = factory
        cache = emptyMap()
    }

    /**
     * 取某个偏好文件的读写入口。工厂对同一个名字必须返回同一个实例
     * （Android 侧由 [SqliteStorage.store] 缓存保证）；存储实现持内存快照，两个实例互相看不见对方写入。
     */
    fun store(name: String): KeyValueStore {
        cache[name]?.let { return it }
        val f = factory ?: error(
            "AppStorage 未初始化：请在 Application.onCreate() 中调用 AppStorage.init { name -> … }"
        )
        // 并发下工厂可能被调两次，返回同一实例，后写入者胜出
        val created = f(name)
        cache = cache + (name to created)
        return created
    }
}

/*
 * 刻意不提供偏好文件名常量表：集中成表会与各文件已有常量并存，名字写错会导致用户数据静默读不出来。
 */
