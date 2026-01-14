package com.pdfmaster.app

import android.app.Application
import com.pdfmaster.app.util.PdfUtils
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PdfMasterApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize PDFBox for PDF encryption/decryption
        PdfUtils.initPdfBox(this)
    }

    companion object {
        lateinit var instance: PdfMasterApp
            private set
    }
}
