package com.haooz.chedule.data

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 课程备注的持久化回归。
 *
 * 备注是 `Course` 的后加字段，而 [CourseRepository] 读盘时会用 `sanitizeCourses` **显式构造**
 * 重建对象（UnsafeAllocator 可能给出 null 字段）。那里漏一个字段，表现就是
 * 「重启后备注全丢」—— 2026-10-29 实际踩过一次。
 *
 * 这里用「清内存缓存 → 重新解码」模拟重启，锁住这条路径。
 */
class NotePersistenceTest {

    @BeforeTest
    fun setUp() {
        AppStorage.init { InMemoryKeyValueStore() }
        CourseRepository.getInstance().invalidateAllCaches()
    }

    @Test
    fun `备注与单周备注经过一次读盘后仍在`() {
        val repository = CourseRepository.getInstance()
        val course = Course(
            id = "c1",
            name = "高等数学",
            classroom = "A101",
            teacher = "张老师",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = Course.WEEK_TYPE_ALL,
            colorRes = 0L,
            note = "带计算器",
            weekNotes = mapOf(3 to "调课到 302"),
        )
        repository.saveCourses(listOf(course), notify = false)

        // 模拟重启：丢掉内存缓存，强制从 prefs 重新解码 + sanitize
        repository.invalidateAllCaches()

        val loaded = repository.getAllCourses().single()
        assertEquals("带计算器", loaded.note)
        assertEquals(mapOf(3 to "调课到 302"), loaded.weekNotes)
        // 该周实际生效的备注：单周覆盖优先
        assertEquals("调课到 302", loaded.noteForWeek(3))
        assertEquals("带计算器", loaded.noteForWeek(5))
    }
}
