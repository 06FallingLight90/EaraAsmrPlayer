package com.asmr.player.util

/** 网络流量统计出口；R3-B4 环7 接口倒置（data.remote 拦截器依赖此端口而非 data.repository 实现）。 */
interface NetworkTrafficSink {
    suspend fun addNetworkTraffic(bytes: Long)
}
