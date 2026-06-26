package com.pdfmaster.app.di

import com.pdfmaster.app.analytics.Analytics
import com.pdfmaster.app.analytics.ConsentAwareAnalytics
import com.pdfmaster.app.analytics.LogcatAnalytics
import com.pdfmaster.app.analytics.RawAnalytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Analytics wiring:
 *  - [Analytics] (what the app injects) → [ConsentAwareAnalytics], which honors the opt-out.
 *  - [RawAnalytics] (the underlying tracker) → [LogcatAnalytics] by default.
 *
 * To send real data, follow `ANALYTICS.md`, then change ONLY the raw binding to:
 *
 *     @Binds @Singleton @RawAnalytics
 *     abstract fun bindRawAnalytics(impl: FirebaseAnalyticsTracker): Analytics
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Binds
    @Singleton
    @RawAnalytics
    abstract fun bindRawAnalytics(impl: LogcatAnalytics): Analytics

    @Binds
    @Singleton
    abstract fun bindAnalytics(impl: ConsentAwareAnalytics): Analytics
}
