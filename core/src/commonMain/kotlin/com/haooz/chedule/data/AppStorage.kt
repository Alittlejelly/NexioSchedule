package com.haooz.chedule.data

import kotlin.concurrent.Volatile

/**
 * 全局存储入口：启动时 [init] 一次，业务用 [store] 按偏好文件名取读写通道。
 * ⚠ 每个偏好名对应独立文件，绝不合并（合并会改掉存量数据落盘位置）；未初始化读取抛异常而非静默返回空数据，便于定位「设置全丢」。
 */
object AppStorage {
    // ⚠ 必须用 kotlin.concurrent.Volatile：裸 @Volatile 依赖 JVM 专有默认导入，在 Kotlin/Native 与 JS 上会 Unresolved reference。
    @Volatile
    private var factory: ((String) -> KeyValueStore)? = null

    /** 实例缓存。copy-on-write 不可变 Map：每个名字一生只写入一次，重建开销可忽略，换来无锁读取。 */
    @Volatile
    private var cache: Map<String, KeyValueStore> = emptyMap()

    /** 是否已初始化。用于初始化前可能被调用的容错分支。 */
    val isReady: Boolean get() = factory != null

    /**
     * @param factory 由平台侧提供：Android 传
     *   `{ name -> context.getSharedPreferences(name, MODE_PRIVATE).asKeyValueStore() }`
     */
    fun init(factory: (String) -> KeyValueStore) {
        this.factory = factory
        cache = emptyMap()
    }

    /**     * 两个实例 = 两份互相看不见的快照，一边写另一边读旧值、后写者覆盖前者 ——
     * 表现为「设置改了偶发不生效」这种最难查的形态。本对象在 commonMain，
     * 没有跨平台的锁可用，所以唯一性的保证下沉到平台工厂（见 `SqliteStorage.store`）。
     */
    fun store(name: String): KeyValueStore {
        cache[name]?.let { return it }
        val f = factory ?: error(
            "AppStorage 未初始化：请在 Application.onCreate() 中调用 AppStorage.init { name -> … }"
        )
        // 并发下可能调用两次工厂，但工厂返回同一实例，后写入者胜出，结果等价。
        val created = f(name)
        cache = cache + (name to created)
        return created
    }
}

/*
 * 刻意不提供偏好文件名常量表：集中成表会与各文件已有常量并存，名字写错会导致用户数据静默读不出来。
 */
