package com.asmr.player.di

import com.asmr.player.data.local.metadata.AudioMetadataReader
import com.asmr.player.data.local.metadata.MediaMetadataRetrieverReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** §8.3：本地音频元数据读取 seam 绑定；唯一调用点为扫描入库链（T3 接线）。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MetadataModule {
    @Binds
    abstract fun bindAudioMetadataReader(impl: MediaMetadataRetrieverReader): AudioMetadataReader
}
