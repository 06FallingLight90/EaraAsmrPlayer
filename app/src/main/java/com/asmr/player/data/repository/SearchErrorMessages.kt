package com.asmr.player.data.repository

import org.jsoup.HttpStatusException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * R3-B5c：从 ui/search/SearchViewModel.kt 的 toUserMessage 原样迁入 data 层
 * （消 ui→retrofit2 方向违规：异常类型判断需要 net-stack 类型，归 data）。
 * 纯搬迁，文案与分支未改。
 */
internal fun searchErrorUserMessage(e: Throwable): String {
    val raw = e.message.orEmpty()
    if (raw.contains("请先登录")) return "请先登录后再使用\"已购\"搜索"
    return when (e) {
        is SocketTimeoutException -> "连接超时，请稍后重试"
        is IOException -> "网络连接失败，请检查网络后重试"
        is HttpException -> {
            val code = e.code()
            when {
                code == 401 -> "登录已过期，请重新登录"
                code == 403 -> "访问受限，请稍后再试"
                code in 500..599 -> "服务器开小差了，请稍后重试"
                else -> "请求失败，请稍后重试"
            }
        }
        is HttpStatusException -> {
            when (e.statusCode) {
                403, 429 -> "访问受限或触发风控，请稍后再试"
                in 500..599 -> "服务器开小差了，请稍后重试"
                else -> "请求失败，请稍后重试"
            }
        }

        else -> "搜索失败，请稍后重试"
    }
}
