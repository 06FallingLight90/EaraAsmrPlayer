package com.asmr.player.playback

import android.content.ComponentName
import android.content.Context

/**
 * 播放会话连接面：隔离 playback 层对具体 MediaSessionService 类型的引用。
 * 由 service 侧实现（见 [com.asmr.player.service.PlaybackServiceController]），
 * 后续 service 边界重构时在此长出真实控制方法。
 */
interface PlaybackController {
    /** Media3 会话服务的组件名，用于建立 MediaController 连接。 */
    fun sessionServiceComponent(context: Context): ComponentName
}
