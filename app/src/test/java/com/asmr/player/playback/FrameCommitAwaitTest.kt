package com.asmr.player.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [awaitFrameCommitOrTimeout] seam 的行为守护：
 * 前台（帧回调及时触发）立即恢复；后台（回调永不触发）超时兜底并注销回调。
 * 对应「仅播放切片只在应用前台生效」的缺陷修复（上游 issue #322）。
 */
class FrameCommitAwaitTest {

    @Test
    fun commitCallbackFires_returnsTrueImmediately() = runBlocking {
        var unregisterCalls = 0
        val result = awaitFrameCommitOrTimeout(timeoutMs = 5_000L) { commit ->
            commit() // 注册即触发：模拟帧回调已就绪
            val unregister: () -> Unit = { unregisterCalls++ }
            unregister
        }
        assertTrue(result)
        assertEquals(0, unregisterCalls)
    }

    @Test
    fun callbackNeverFires_timesOutAndUnregisters() = runBlocking {
        var unregisterCalls = 0
        val result = awaitFrameCommitOrTimeout(timeoutMs = 50L) { _ ->
            val unregister: () -> Unit = { unregisterCalls++ }
            unregister
        }
        assertFalse(result)
        assertEquals(1, unregisterCalls)
    }

    @Test
    fun cancellation_unregistersCallback() = runBlocking {
        var unregisterCalls = 0
        val registered = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            awaitFrameCommitOrTimeout(timeoutMs = 60_000L) { _ ->
                val unregister: () -> Unit = { unregisterCalls++ }
                registered.complete(Unit)
                unregister
            }
        }
        registered.await()
        job.cancelAndJoin()
        assertEquals(1, unregisterCalls)
    }
}
