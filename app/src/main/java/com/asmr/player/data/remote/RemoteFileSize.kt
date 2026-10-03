package com.asmr.player.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * R2-C4b-3：从 ui/library/albumdetail/AlbumDetailViewModelSupport 迁入 data 层，
 * 供 OnlineContentRepository 使用（消除 data → ui 依赖方向违规）。纯搬迁，逻辑未改。
 */
internal fun requestRemoteFileSize(url: String, client: OkHttpClient? = null): Long? {
    if (client == null) return null
    fun execute(request: Request): Long? {
        return runCatching {
            client.newCall(request).execute().use(::extractRemoteFileSize)
        }.getOrNull()
    }

    val headRequest = Request.Builder()
        .url(url)
        .head()
        .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
        .build()
    execute(headRequest)?.let { return it }

    val rangeRequest = Request.Builder()
        .url(url)
        .get()
        .header("Range", "bytes=0-0")
        .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
        .build()
    return execute(rangeRequest)
}

internal fun extractRemoteFileSize(response: Response): Long? {
    if (!response.isSuccessful) return null
    val contentRange = response.header("Content-Range").orEmpty()
    val totalFromRange = contentRange.substringAfterLast('/', "").toLongOrNull()
    if (totalFromRange != null && totalFromRange > 0L) return totalFromRange
    val contentLength = response.header("Content-Length")?.toLongOrNull()
    if (contentLength != null && contentLength > 0L) return contentLength
    val bodyLength = response.body?.contentLength()
    return bodyLength?.takeIf { it > 0L }
}
