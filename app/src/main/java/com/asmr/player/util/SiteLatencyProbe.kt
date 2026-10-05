package com.asmr.player.util

import android.os.SystemClock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * R3-B5c：从 ui/drawer/DrawerStatusViewModel.kt 的 measure 原样迁入 data 层
 * （消 ui→okhttp3 方向违规：站点连通延迟探测属网络能力，归 data）。
 * 纯搬迁，超时参数、header 与"非 404 即通"判定未改。
 */
@Singleton
class SiteLatencyProbe @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    fun measure(url: String, suppressAutomaticError: Boolean = false): Long? {
        val client = okHttpClient.newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Cache-Control", "no-cache")
            .header("Accept", "application/json, text/plain, */*")
        if (suppressAutomaticError) {
            requestBuilder.header(
                NetworkHeaders.HEADER_SILENT_IO_ERROR,
                NetworkHeaders.SILENT_IO_ERROR_ON
            )
        }
        val request = requestBuilder.build()
        val start = SystemClock.elapsedRealtime()
        return runCatching {
            client.newCall(request).execute().use { resp ->
                // 只要不是 404 或网络错误，都认为通了（即使是空搜索结果）
                if (!resp.isSuccessful && resp.code != 404) return@use null
                SystemClock.elapsedRealtime() - start
            }
        }.getOrNull()
    }
}
