package com.pdfmaster.app.di

import com.pdfmaster.app.billing.BillingScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Billing wiring. [BillingManager] and [PremiumManager] are constructor-injected
 * `@Singleton`s, so the only thing to provide here is the long-lived application-scoped
 * coroutine scope they use to run connection + reconciliation work.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    @BillingScope
    fun provideBillingScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
