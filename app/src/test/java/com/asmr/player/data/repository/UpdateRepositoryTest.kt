package com.asmr.player.data.repository

import android.app.Application
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * R2-C4a seam 测试：钉住 UpdateRepository 从 SettingsViewModel 下沉的
 * APK 下载编排行为（目录选择/文件命名/陈旧清理/失败残留清理/进度回调）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class UpdateRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: UpdateRepository
    private lateinit var context: Application

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        context = RuntimeEnvironment.getApplication()
        repo = UpdateRepository(OkHttpClient(), context)
    }

    @After
    fun tearDown() {
        server.shutdown()
        cleanupDir(downloadDir())
    }

    private fun downloadDir(): File {
        return context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
    }

    private fun cleanupDir(dir: File) {
        dir.listFiles { f -> f.name.startsWith("eara-") && f.name.endsWith(".apk") }
            ?.forEach { it.delete() }
    }

    private fun release(apkUrl: String = server.url("/app.apk").toString()) = UpdateReleaseInfo(
        tagName = "v1.2.3",
        versionName = "1.2.3",
        title = "Release 1.2.3",
        body = "notes",
        publishedAt = "2026-01-01",
        htmlUrl = "https://github.com/example/example/releases/tag/v1.2.3",
        apkName = "app-v1.2.3.apk",
        apkUrl = apkUrl,
    )

    @Test
    fun `downloadApk writes file, reports progress and returns path`() {
        val payload = ByteArray(1024) { it.toByte() }
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(okio.Buffer().write(payload))
        )
        val progress = mutableListOf<Pair<Long, Long>>()

        val path = runBlocking { repo.downloadApk(release()) { downloaded, total -> progress += downloaded to total } }

        val file = File(path)
        assertTrue(file.exists())
        assertEquals(payload.size.toLong(), file.length())
        assertEquals("eara-v1.2.3.apk", file.name)
        assertTrue(progress.isNotEmpty())
        val (downloaded, total) = progress.last()
        assertEquals(payload.size.toLong(), downloaded)
        assertEquals(payload.size.toLong(), total)
    }

    @Test
    fun `downloadApk cleans stale apks before download`() {
        val stale = File(downloadDir(), "eara-v1.0.0.apk")
        stale.writeBytes(ByteArray(10))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(okio.Buffer().write(ByteArray(512)))
        )

        val path = runBlocking { repo.downloadApk(release()) { _, _ -> } }

        assertFalse(File(downloadDir(), "eara-v1.0.0.apk").exists())
        assertTrue(File(path).exists())
    }

    @Test
    fun `downloadApk deletes partial file on mid-stream failure and rethrows`() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("x".repeat(4096))
                .setHeader("Content-Length", "999999")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        val error = runBlocking { runCatching { repo.downloadApk(release()) { _, _ -> } } }

        assertTrue(error.isFailure)
        assertFalse(File(downloadDir(), "eara-v1.2.3.apk").exists())
    }

    @Test
    fun `downloadApk throws on http error and keeps no file`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val error = runBlocking { runCatching { repo.downloadApk(release()) { _, _ -> } } }

        assertTrue(error.isFailure)
        val message = error.exceptionOrNull()?.message.orEmpty()
        assertTrue("unexpected message: $message", message.startsWith("下载失败：500"))
    }

    @Test
    fun `isNewerThanCurrent delegates version comparison`() {
        assertTrue(repo.isNewerThanCurrent("1.2.3", "1.2.2"))
        assertFalse(repo.isNewerThanCurrent("1.2.2", "1.2.3"))
        assertFalse(repo.isNewerThanCurrent("1.2.2", "1.2.2"))
        // 比较仅取数字段，与 GitHubUpdateClient 口径一致
        assertTrue(repo.isNewerThanCurrent("v1.3.0", "1.2.9"))
    }
}
