package com.pdfmaster.app

import android.app.Application
import com.pdfmaster.app.analytics.Analytics
import com.pdfmaster.app.analytics.AnalyticsEvent
import com.pdfmaster.app.billing.PremiumManager
import com.pdfmaster.app.util.PdfUtils
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PdfMasterApp : Application() {

    /**
     * Injected so the billing connection + entitlement reconciliation start at app launch.
     * This also lets pending purchases (e.g. completed after a crash) get acknowledged promptly,
     * even if the user never opens the paywall this session.
     */
    @Inject lateinit var premiumManager: PremiumManager

    @Inject lateinit var analytics: Analytics

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Touch entitlement so its init block connects to Play Billing on startup.
        premiumManager.isPremiumNow()

        analytics.track(AnalyticsEvent.AppOpen)

        // Initialize PDFBox for PDF encryption/decryption
        PdfUtils.initPdfBox(this)
    }

    companion object {
        lateinit var instance: PdfMasterApp
            private set
    }
}
