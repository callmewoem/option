package com.locke.app.di

import com.locke.app.data.billing.DevModeEntitlementRepository
import com.locke.app.data.billing.EntitlementRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds [EntitlementRepository] to [DevModeEntitlementRepository], a thin wrapper around
 * the real Play-Billing-backed [com.locke.app.data.billing.PlayBillingEntitlementRepository]
 * that only ever changes behavior in a debug build with Settings -> Developer's toggle on
 * (see that class's own doc). See
 * [com.locke.app.data.billing.StubEntitlementRepository] for the simpler local-only
 * reference implementation this replaces -- kept around for tests, not bound here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    abstract fun bindEntitlementRepository(impl: DevModeEntitlementRepository): EntitlementRepository
}
