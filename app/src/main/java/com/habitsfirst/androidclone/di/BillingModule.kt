package com.habitsfirst.androidclone.di

import com.habitsfirst.androidclone.data.billing.EntitlementRepository
import com.habitsfirst.androidclone.data.billing.StubEntitlementRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds [EntitlementRepository] to its current stub implementation. Once real Play
 * Billing is wired up, swap [StubEntitlementRepository] here for a
 * `PlayBillingEntitlementRepository` -- callers of [EntitlementRepository] need no
 * changes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    abstract fun bindEntitlementRepository(impl: StubEntitlementRepository): EntitlementRepository
}
