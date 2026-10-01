package com.asmr.player.di

import com.asmr.player.playback.PlaybackController
import com.asmr.player.service.PlaybackServiceController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlaybackModule {

    @Binds
    internal abstract fun bindPlaybackController(impl: PlaybackServiceController): PlaybackController
}
