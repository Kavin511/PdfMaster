package com.pdfmaster.app.di

import com.pdfmaster.app.analytics.Analytics
import com.pdfmaster.app.analytics.ConsentAwareAnalytics
import com.pdfmaster.app.analytics.FirebaseAnalyticsTracker
import com.pdfmaster.app.analytics.RawAnalytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Analytics wiring:
 *  - [Analytics] (what the app injects) → [ConsentAwareAnalytics], which honors the opt-out.
 *  - [RawAnalytics] (the underlying tracker) → [FirebaseAnalyticsTracker].
 *
 * The Firebase tracker falls back to Logcat until `app/google-services.json` is added (see
 * `ANALYTICS.md`), so this binding is safe even before Firebase is configured.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Binds
    @Singleton
    @RawAnalytics
    abstract fun bindRawAnalytics(impl: FirebaseAnalyticsTracker): Analytics

    @Binds
    @Singleton
    abstract fun bindAnalytics(impl: ConsentAwareAnalytics): Analytics
}
