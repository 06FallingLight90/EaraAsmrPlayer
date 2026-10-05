package com.asmr.player.di

import com.asmr.player.data.repository.StatisticsRepository
import com.asmr.player.util.NetworkTrafficSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class StatisticsModule {

    @Binds
    internal abstract fun bindNetworkTrafficSink(impl: StatisticsRepository): NetworkTrafficSink
}
