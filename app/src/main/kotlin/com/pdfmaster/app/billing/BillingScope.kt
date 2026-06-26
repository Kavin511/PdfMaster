package com.pdfmaster.app.billing

import javax.inject.Qualifier

/** Qualifies the application-lifetime [kotlinx.coroutines.CoroutineScope] used by the billing layer. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BillingScope
