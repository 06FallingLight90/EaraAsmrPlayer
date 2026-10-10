package com.asmr.player.ui.library

import android.app.Application
import com.asmr.player.util.BulkPhase
import com.asmr.player.util.BulkProgressStore
import com.asmr.player.util.MessageManager
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LibraryTaskCoordinator 批量任务门钉测试：T11 扫描下沉 CoroutineWorker 后，
 * deleteAlbum/tryRegisterAlbumJob 预检消费的 [LibraryTaskCoordinator.isBulkTaskRunning]
 * 必须对「扫描 Worker 持应用级 bulkProgress」同样判定为批量任务进行中（扫描进行中删除专辑仍被拦）。
 * Robolectric：BulkProgress 构造默认参数触达 SystemClock（与 BulkProgressStoreTest 同因）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LibraryTaskCoordinatorTest {

    @Test
    fun isBulkTaskRunning_blockedWhileScanWorkerHoldsBulkProgress_andFreeAfterFinish() {
        val progressStore = BulkProgressStore { 0L }
        val coordinator = LibraryTaskCoordinator(MessageManager(), progressStore)
        assertFalse(coordinator.isBulkTaskRunning())

        // 模拟扫描 Worker 运行期持批量信号（LibraryScanWorker.startBulkProgress / finally finishBulkProgress）。
        progressStore.startBulkProgress(phase = BulkPhase.ScanningLocal, total = 3)
        assertTrue(coordinator.isBulkTaskRunning())
        // 预检同源：deleteAlbum 与 tryRegisterAlbumJob 走同一判定，扫描持门期间一律拒绝。
        assertFalse(coordinator.tryRegisterAlbumJob(albumId = 1L, taskName = "本地同步"))

        // Worker 收尾后恢复空闲。
        progressStore.finishBulkProgress()
        assertFalse(coordinator.isBulkTaskRunning())
        assertTrue(coordinator.tryRegisterAlbumJob(albumId = 1L, taskName = "本地同步"))
    }

    @Test
    fun isBulkTaskRunning_stillTrueForVmLevelBulkJob() {
        val coordinator = LibraryTaskCoordinator(MessageManager(), BulkProgressStore { 0L })

        val job = Job()
        coordinator.bulkJob = job
        assertTrue(coordinator.isBulkTaskRunning())

        job.cancel()
        assertFalse(coordinator.isBulkTaskRunning())
    }
}
