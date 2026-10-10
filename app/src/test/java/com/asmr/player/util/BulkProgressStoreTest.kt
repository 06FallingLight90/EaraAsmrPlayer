package com.asmr.player.util

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T11：应用级批量进度通道（BulkProgressStore）行为钉测。
 * 批量扫描下沉 CoroutineWorker 后进度状态挂应用级单例（VM 侧 LibraryTaskCoordinator 委托至此，
 * Worker 直接注入）；字段更新口径须与原 VM 级实现逐一对应：
 * start 重置节流与 currentFile、update 只动 current/title、currentFile 120ms 节流、finish 清空。
 * 时钟经构造注入手控（Robolectric PAUSED 模式虚拟时钟冻结在 0，ShadowSystemClock.setNanoTime
 * 不可用；真实设备 elapsedRealtime 开机后恒大于节流窗，首次更新必放行——测试以 200ms 起步模拟）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BulkProgressStoreTest {
    private var clockMs = 0L
    private lateinit var store: BulkProgressStore

    private fun advanceClockTo(ms: Long) {
        clockMs = ms
    }

    @Before
    fun setUp() {
        clockMs = 200L
        store = BulkProgressStore { clockMs }
    }

    @Test
    fun startBulkProgress_setsPhaseAndTotalAndResetsCurrentFileThrottle() {
        store.startBulkProgress(BulkPhase.ScanningLocal, total = 3)
        var p = store.bulkProgress.value!!
        assertEquals(BulkPhase.ScanningLocal, p.phase)
        assertEquals(0, p.current)
        assertEquals(3, p.total)
        assertEquals("", p.currentFile)

        // 首次文件更新放行（now=200ms > 120ms 窗）
        store.maybeUpdateBulkCurrentFile("a.mp3")
        assertEquals("a.mp3", store.bulkProgress.value!!.currentFile)

        // start 归零节流基准与 currentFile：重置后立即可再更新（b.mp3 放行）
        store.startBulkProgress(BulkPhase.ScanningLocal, total = 5)
        assertEquals("", store.bulkProgress.value!!.currentFile)
        advanceClockTo(210L)
        store.maybeUpdateBulkCurrentFile("b.mp3")
        assertEquals("b.mp3", store.bulkProgress.value!!.currentFile)
    }

    @Test
    fun updateBulkAlbumProgress_onlyTouchesCurrentAndTitle() {
        store.startBulkProgress(BulkPhase.ScanningLocal, total = 2, currentAlbumTitle = "first")
        store.maybeUpdateBulkCurrentFile("x.mp3")
        assertEquals("x.mp3", store.bulkProgress.value!!.currentFile)

        store.updateBulkAlbumProgress(current = 1, currentAlbumTitle = "second")

        val p = store.bulkProgress.value!!
        assertEquals(1, p.current)
        assertEquals("second", p.currentAlbumTitle)
        assertEquals(2, p.total)
        // update 不触碰 currentFile（原实现口径）
        assertEquals("x.mp3", p.currentFile)
    }

    @Test
    fun maybeUpdateBulkCurrentFile_throttlesWithin120ms() {
        store.startBulkProgress(BulkPhase.ScanningLocal, total = 1)

        // 首调放行（now=200ms, 基准=0）
        store.maybeUpdateBulkCurrentFile("a.mp3")
        assertEquals("a.mp3", store.bulkProgress.value!!.currentFile)

        // 50ms 后再调（<120ms 窗）→ 吞
        advanceClockTo(250L)
        store.maybeUpdateBulkCurrentFile("b.mp3")
        assertEquals("a.mp3", store.bulkProgress.value!!.currentFile)

        // 窗口过后（距上次放行 200ms）→ 放行
        advanceClockTo(450L)
        store.maybeUpdateBulkCurrentFile("c.mp3")
        assertEquals("c.mp3", store.bulkProgress.value!!.currentFile)
    }

    @Test
    fun updateIgnoredWhenNoProgressAndFinishClears() {
        // 无进行中进度时 update 系列为 no-op
        assertNull(store.bulkProgress.value)
        store.updateBulkAlbumProgress(current = 1, currentAlbumTitle = "x")
        store.maybeUpdateBulkCurrentFile("y.mp3")
        assertNull(store.bulkProgress.value)

        store.startBulkProgress(BulkPhase.SyncingCloud, total = 4)
        assertEquals(BulkPhase.SyncingCloud, store.bulkProgress.value!!.phase)
        store.finishBulkProgress()
        assertNull(store.bulkProgress.value)
    }
}
