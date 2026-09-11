package com.locke.app.di

import com.locke.app.data.remote.ExperimentApiClient
import com.locke.app.data.remote.HttpExperimentApiClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds [ExperimentApiClient] to its real OkHttp-based implementation. Mirrors [AccountabilityModule]'s shape. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExperimentModule {

    @Binds
    abstract fun bindExperimentApiClient(impl: HttpExperimentApiClient): ExperimentApiClient
}
