package com.locke.app.di

import com.locke.app.data.remote.AnalyticsApiClient
import com.locke.app.data.remote.HttpAnalyticsApiClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds [AnalyticsApiClient] to its real OkHttp-based implementation. Mirrors [AccountabilityModule]'s shape. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Binds
    abstract fun bindAnalyticsApiClient(impl: HttpAnalyticsApiClient): AnalyticsApiClient
}
