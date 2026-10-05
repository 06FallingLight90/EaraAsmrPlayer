package com.asmr.player.util

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * R3-B5c：从 ui/common/cover/PreviewImageGallerySaver.kt 的 withPreviewImageInput
 * http 分支迁入 data 层（消 ui→okhttp3 方向违规：远程图片流打开属网络能力，归 data）。
 * 纯搬迁：header 透传、HTTP 状态判定与 contentType 提取未改；block 在流存活期内执行。
 */
@Singleton
class PreviewImageRemoteStream @Inject constructor(
    @Named("image") private val httpClient: OkHttpClient
) {
    fun <T> withRemoteInput(
        location: String,
        headers: Map<String, String>,
        block: (InputStream, String?) -> T
    ): T {
        val requestBuilder = Request.Builder().url(location)
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }
        return httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("图片下载失败：HTTP ${response.code}")
            val body = response.body ?: throw IOException("图片下载结果为空")
            body.byteStream().use { input ->
                block(input, body.contentType()?.toString())
            }
        }
    }
}
