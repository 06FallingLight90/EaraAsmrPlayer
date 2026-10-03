package com.asmr.player.data.repository

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import com.asmr.player.BuildConfig
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.update.GitHubUpdateClient
import com.asmr.player.data.remote.update.UpdateRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

/** UI 层使用的版本发布信息（对 [UpdateRelease] 的同构映射，避免 UI 依赖 data.remote）。 */
data class UpdateReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val body: String,
    val publishedAt: String?,
    val htmlUrl: String,
    val apkName: String,
    val apkUrl: String,
)

/**
 * R2-C4a：从 SettingsViewModel 下沉的检查更新 + APK 下载编排。
 * 行为契约（原 SettingsViewModel 内联实现，逐行搬迁）：
 * - fetchLatestRelease 使用 BuildConfig.UPDATE_REPO_OWNER/NAME；
 * - downloadApk 落盘到 getExternalFilesDir(DIRECTORY_DOWNLOADS)（无则 filesDir），
 *   文件名 "eara-<safeTag>.apk"，下载前清理同名前缀的陈旧 APK；
 * - 下载流带 User-Agent "Eara-Android" 与静默 IO 头，进度按 200ms 节流回调；
 * - 失败时若已写入目标文件则删除残留，异常原样上抛（消息由调用方映射 UI 文案）。
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    private val updateClient = GitHubUpdateClient(okHttpClient)

    suspend fun fetchLatestRelease(): UpdateReleaseInfo {
        val release = updateClient.fetchLatestRelease(
            owner = BuildConfig.UPDATE_REPO_OWNER,
            repo = BuildConfig.UPDATE_REPO_NAME
        )
        return release.toInfo()
    }

    fun isNewerThanCurrent(latestVersionName: String, currentVersionName: String): Boolean {
        return updateClient.isNewerThanCurrent(latestVersionName, currentVersionName)
    }

    /** 返回 APK 绝对路径；进度经 onProgress(downloadedBytes, totalBytes) 回调。 */
    suspend fun downloadApk(
        release: UpdateReleaseInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): String {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val safeTag = release.tagName.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "latest" }
        val file = File(dir, "$UPDATE_APK_PREFIX$safeTag$UPDATE_APK_SUFFIX")
        var targetFile: File? = null
        var touchedTargetFile = false
        try {
            targetFile = file
            cleanupStaleUpdateApks(dir, file)
            val req = Request.Builder()
                .url(release.apkUrl)
                .header("User-Agent", "Eara-Android")
                .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("下载失败：${resp.code} ${resp.message}")
                }
                val body = resp.body ?: throw IllegalStateException("下载失败：空响应体")
                val total = body.contentLength().coerceAtLeast(0L)
                val input = body.byteStream()
                touchedTargetFile = true
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(256 * 1024)
                    var read: Int
                    var downloaded = 0L
                    var lastEmit = 0L
                    while (true) {
                        read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        downloaded += read.toLong()
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastEmit >= 200L) {
                            onProgress(downloaded, total)
                            lastEmit = now
                        }
                    }
                    out.flush()
                    onProgress(downloaded, total)
                }
            }

            val ok = file.exists() && file.length() > 0L
            if (!ok) throw IllegalStateException("下载文件无效")
            return file.absolutePath
        } catch (e: Exception) {
            if (touchedTargetFile) {
                runCatching { targetFile?.takeIf { it.exists() }?.delete() }
            }
            throw e
        }
    }

    private fun cleanupStaleUpdateApks(dir: File, keepFile: File) {
        dir.listFiles { file ->
            file.isFile &&
                file.name.startsWith(UPDATE_APK_PREFIX) &&
                file.name.endsWith(UPDATE_APK_SUFFIX) &&
                file.absolutePath != keepFile.absolutePath
        }?.forEach { staleFile ->
            runCatching { staleFile.delete() }
        }
    }

    private fun UpdateRelease.toInfo() = UpdateReleaseInfo(
        tagName = tagName,
        versionName = versionName,
        title = title,
        body = body,
        publishedAt = publishedAt,
        htmlUrl = htmlUrl,
        apkName = apkName,
        apkUrl = apkUrl,
    )

    companion object {
        private const val UPDATE_APK_PREFIX = "eara-"
        private const val UPDATE_APK_SUFFIX = ".apk"
    }
}
