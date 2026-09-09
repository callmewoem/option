package com.habitsfirst.androidclone.di

import com.habitsfirst.androidclone.data.billing.EntitlementRepository
import com.habitsfirst.androidclone.data.billing.PlayBillingEntitlementRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds [EntitlementRepository] to the real Play-Billing-backed implementation. See
 * [com.habitsfirst.androidclone.data.billing.StubEntitlementRepository] for the simpler
 * local-only reference implementation this replaces -- kept around for tests, not bound
 * here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    abstract fun bindEntitlementRepository(impl: PlayBillingEntitlementRepository): EntitlementRepository
}
