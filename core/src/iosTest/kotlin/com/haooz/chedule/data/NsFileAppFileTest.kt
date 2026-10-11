package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [NsFileAppFile] 的**运行时**契约测试（只在 iOS 模拟器上跑）。
 *
 * 为什么不能只靠 `compileKotlinIos*` 交差：编译只能证明 Foundation 的签名对得上，
 * 证明不了 `NSFileManager` 的**路径语义**。最要紧的是 [AppFile.writeBytes] 会自动
 * 建父目录这条 —— 调用点全都默认它成立，一旦不成立，症状是「学校索引 / 课程数据
 * 写不进去」，而且**不报错**，极难定位。
 *
 * 跑法：`./gradlew -Pnexio.ios=true :core:iosSimulatorArm64Test`
 */
class NsFileAppFileTest {

    /** 每个用例一个独立子目录，互不干扰（iOS 模拟器不会自动清理 Documents）。 */
    private fun sandbox(name: String): AppFile {
        val dir = NsFileAppFile(defaultDocumentsDir()).resolve("apptest_$name")
        dir.mkdirs()
        return dir
    }

    @Test
    fun writeBytesCreatesMissingParentDirectories() {
        val root = sandbox("parents")
        // 逐级 resolve：模拟 SchoolRepository 的 repo/index/school_index.pb 三层
        val deep = root.resolve("repo").resolve("index").resolve("school_index.pb")
        deep.writeBytes(byteArrayOf(1, 2, 3))

        assertTrue(deep.exists(), "父目录未自动创建")
        assertContentEquals(byteArrayOf(1, 2, 3), deep.readBytes())
    }

    @Test
    fun resolveAcceptsMultiSegmentRelativePath() {
        val root = sandbox("multiseg")
        val deep = root.resolve("a/b/c.bin")
        deep.writeBytes(byteArrayOf(9))

        assertTrue(deep.exists())
        assertContentEquals(byteArrayOf(9), deep.readBytes())
    }

    @Test
    fun readMissingFileThrows() {
        val root = sandbox("missing")
        assertFalse(root.resolve("nope.bin").exists())
        // 与 FileAppFile 对齐：读缺失文件抛异常，不是返回空数组 ——
        // 返回空会让调用方把「没读到」当成「读到空内容」。
        assertFails { root.resolve("nope.bin").readBytes() }
    }

    @Test
    fun emptyAndLargePayloadRoundTrip() {
        val root = sandbox("payload")

        val empty = root.resolve("empty.bin")
        empty.writeBytes(ByteArray(0))
        assertTrue(empty.exists(), "空文件也应落盘")
        assertContentEquals(ByteArray(0), empty.readBytes())

        // 1 MB：顺带验证 toNSData / toByteArray 在大数据量下没截断
        val big = ByteArray(1_000_000) { (it % 251).toByte() }
        val bigFile = root.resolve("big.bin")
        bigFile.writeBytes(big)
        assertContentEquals(big, bigFile.readBytes())
    }

    @Test
    fun overwriteReplacesContent() {
        val root = sandbox("overwrite")
        val f = root.resolve("same.bin")
        f.writeBytes(byteArrayOf(1, 1, 1, 1))
        f.writeBytes(byteArrayOf(2, 2))
        assertContentEquals(byteArrayOf(2, 2), f.readBytes())
    }

    @Test
    fun readTextDecodesUtf8() {
        val root = sandbox("utf8")
        val f = root.resolve("text.txt")
        f.writeBytes("课程表 🎉".encodeToByteArray())
        assertEquals("课程表 🎉", f.readText())
    }

    @Test
    fun mkdirsMakesDirectoryVisible() {
        val root = sandbox("mkdirs")
        val dir = root.resolve("x/y")
        dir.mkdirs()
        // 与 File.exists() 对齐：目录也算存在
        assertTrue(dir.exists())
    }
}
