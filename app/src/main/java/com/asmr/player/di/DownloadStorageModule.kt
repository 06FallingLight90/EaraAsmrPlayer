package com.asmr.player.di

import com.asmr.player.data.download.DownloadStorageGateway
import com.asmr.player.data.local.library.DownloadStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * R3-B4 环8 端口绑定补全：LocalAlbumMergeService 依赖 DownloadStorage 端口，
 * 实现在 data.download.DownloadStorageGateway（@Inject 构造），经此 @Binds 进入组件图。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadStorageModule {
    @Binds
    abstract fun bindDownloadStorage(impl: DownloadStorageGateway): DownloadStorage
}
