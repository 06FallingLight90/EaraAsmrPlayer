package com.asmr.player.ui.common.cover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.cache.ImageCacheManager
import dagger.hilt.android.EntryPointAccessors

/**
 * R3-B5d：ui 侧取图片缓存管理器的唯一 seam（原 9 个 ui 文件各自
 * EntryPointAccessors.fromApplication(ctx, ImageCacheEntryPoint::class.java) 的
 * 模板收敛到此；ui→cache 穿透集中一处，其余 ui 文件不再 import cache 包）。
 */
@Composable
internal fun rememberAppImageCacheManager(): ImageCacheManager {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, ImageCacheEntryPoint::class.java)
            .imageCacheManager()
    }
}
