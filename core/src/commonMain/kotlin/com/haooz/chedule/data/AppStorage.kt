package com.haooz.chedule.data

import kotlin.concurrent.Volatile

/**
 * 全局存储入口。启动时 [init] 一次，业务代码用 [store] 按偏好文件名取读写通道。
 *
 * 项目有十几个偏好文件名，每个名字对应一个独立文件，绝不能合并成一个 ——
 * 合并等于改掉存量数据的落盘位置。所以这里持有的是**工厂**而不是单个实例。
 *
 * 未初始化就读取会抛异常而不是静默返回空数据，静默失败会让「设置全丢」极难定位。
 */
object AppStorage {
    // 用 kotlin.concurrent.Volatile：不限定名的 @Volatile 依赖 JVM 专有的默认导入，
    // 在 Kotlin/Native 与 JS 上直接 Unresolved reference。
    @Volatile
    private var factory: ((String) -> KeyValueStore)? = null

    /** 已创建的实例缓存。copy-on-write 不可变 Map：每个名字一生只写入一次，重建开销可忽略，换来无锁读取。 */
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

    /** 取某个偏好文件的读写入口。 */
    fun store(name: String): KeyValueStore {
        cache[name]?.let { return it }
        val f = factory ?: error(
            "AppStorage 未初始化：请在 Application.onCreate() 中调用 AppStorage.init { name -> … }"
        )
        // 并发下可能创建两次，但结果等价，后写入者胜出，不加锁。
        val created = f(name)
        cache = cache + (name to created)
        return created
    }
}

/*
 * 刻意不提供偏好文件名常量表：集中成表会让各文件已有的正确常量与表里手抄的常量并存，
 * 而名字写错的后果是用户数据静默读不出来。各文件继续用它自己的 private 常量即可。
 */
