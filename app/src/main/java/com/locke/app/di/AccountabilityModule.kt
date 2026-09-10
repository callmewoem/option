package com.locke.app.di

import com.locke.app.data.remote.AccountabilityApiClient
import com.locke.app.data.remote.HttpAccountabilityApiClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds [AccountabilityApiClient] to its real OkHttp-based implementation. There is no
 * default backend today -- [HttpAccountabilityApiClient] talks to whatever base URL the
 * user configures in Settings, failing fast with a clear error when none is set, rather
 * than a hardcoded host. Mirrors [BillingModule]'s "seam behind an interface" shape.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AccountabilityModule {

    @Binds
    abstract fun bindAccountabilityApiClient(impl: HttpAccountabilityApiClient): AccountabilityApiClient
}
