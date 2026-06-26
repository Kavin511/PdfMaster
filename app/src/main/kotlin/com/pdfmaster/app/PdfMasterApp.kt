package com.pdfmaster.app

import android.app.Application
import android.util.Log
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

        // Initialize ComPDFKit SDK
        initComPdfKit()
    }

    private fun initComPdfKit() {
        try {
            // Get license key from manifest metadata
            val appInfo = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            val licenseKey = appInfo.metaData?.getString("compdfkit_key") ?: ""

            // ComPDFKit initialization - using reflection to avoid compile errors if SDK not properly configured
            val sdkClass = Class.forName("com.compdfkit.core.CPDFSdk")
            val initMethod = sdkClass.getMethod("init", android.content.Context::class.java, String::class.java, Boolean::class.javaPrimitiveType)

            if (licenseKey.isNotEmpty() && licenseKey != "YOUR_COMPDFKIT_LICENSE_KEY") {
                initMethod.invoke(null, this, licenseKey, false)
                Log.d(TAG, "ComPDFKit SDK initialized with license key")
            } else {
                // Try initializing without license for trial mode
                val initNoKeyMethod = sdkClass.getMethod("init", android.content.Context::class.java, Boolean::class.javaPrimitiveType)
                initNoKeyMethod.invoke(null, this, false)
                Log.d(TAG, "ComPDFKit SDK initialized in trial mode")
            }
        } catch (e: Exception) {
            Log.w(TAG, "ComPDFKit SDK not available or failed to initialize: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PdfMasterApp"
        lateinit var instance: PdfMasterApp
            private set
    }
}
