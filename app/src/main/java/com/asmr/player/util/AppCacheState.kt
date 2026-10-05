package com.asmr.player.util

/**
 * R3-B5d：从 cache/AppCacheManager.kt 拆出的缓存状态纯类型（原默认值引用
 * AppCacheLimits，同属 util；不进 domain.model 以保持其零出边纯叶子——
 * 见 R3-B1e SCC 塌缩记录）。ui.settings 消费此类型。
 */
data class AppCacheState(
    val maxSizeMb: Int = AppCacheLimits.DefaultSizeMb,
    val usedSizeBytes: Long = 0L,
    val isClearing: Boolean = false,
)
