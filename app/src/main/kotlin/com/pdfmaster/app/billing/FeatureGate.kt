package com.pdfmaster.app.billing

import com.pdfmaster.app.analytics.Analytics
import com.pdfmaster.app.analytics.AnalyticsEvent
import com.pdfmaster.app.data.local.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The free-vs-premium policy for every gated action, in one place.
 *
 * Three kinds of gate:
 *  - [PremiumFeature]      — hard wall, premium only.
 *  - [DailyLimitedFeature] — free users get N uses per day.
 *  - [FreeTierLimits]      — per-operation magnitude caps (e.g. file count).
 *
 * ViewModels inject [FeatureGate], call the matching check, and on [GateResult.Blocked]
 * surface the [PremiumPrompt] to the UI (which routes the user to the paywall).
 */

/** Features that are entirely premium on the free tier. */
enum class PremiumFeature(val title: String, val pitch: String) {
    TEXT_EDITING("Edit PDF text", "Add, replace, and restyle text directly inside any PDF."),
    PASSWORD_PROTECT("Password protection", "Encrypt your PDFs with a password."),
    UNLOCK_PDF("Unlock PDF", "Remove passwords from PDFs you own."),
    OCR("Text recognition", "Extract and search text from scanned documents."),
    SIGNATURE("Signatures", "Create, save, and place signatures on documents."),
    FORM_FILLING("Form filling", "Fill and save interactive PDF forms."),
    ANNOTATE("Annotation tools", "Highlight, draw, underline, and add notes, then save them into your PDF."),
    REDACT("Redaction", "Permanently black out sensitive content."),
    EXPORT_OFFICE("Export to Word / Text", "Convert PDFs into editable formats."),
    REMOVE_WATERMARK("Remove watermark", "Export scans and edits without the PdfMaster watermark."),
    REMOVE_ADS("Remove ads", "Enjoy a faster, ad-free experience."),
}

/** Features free users can use a limited number of times per day. */
enum class DailyLimitedFeature(val key: String, val freeDailyLimit: Int, val title: String) {
    COMPRESS("compress", 1, "Compress PDF"),
    SPLIT("split", 1, "Split PDF"),
    PAGE_OPS("page_ops", 3, "Page editing"),
}

/** Per-operation magnitude caps for the free tier. */
object FreeTierLimits {
    const val MERGE_MAX_FILES = 2
    const val IMAGES_TO_PDF_MAX = 5
}

/** A ready-to-render reason the user hit a wall. */
data class PremiumPrompt(
    val title: String,
    val message: String,
)

sealed interface GateResult {
    data object Allowed : GateResult
    data class Blocked(val prompt: PremiumPrompt) : GateResult

    val isAllowed: Boolean get() = this is Allowed
}

@Singleton
class FeatureGate @Inject constructor(
    private val premiumManager: PremiumManager,
    private val userPreferences: UserPreferences,
    private val analytics: Analytics,
) {

    val isPremium: StateFlow<Boolean> = premiumManager.isPremium
    fun isPremiumNow(): Boolean = premiumManager.isPremiumNow()

    /** Hard premium wall. */
    fun require(feature: PremiumFeature): GateResult =
        if (isPremiumNow()) GateResult.Allowed
        else {
            analytics.track(AnalyticsEvent.GateBlocked(feature.name, "premium_feature"))
            GateResult.Blocked(
                PremiumPrompt(
                    title = "Unlock ${feature.title}",
                    message = "${feature.pitch}\n\nUpgrade to Premium to continue.",
                )
            )
        }

    /** Per-operation count cap (e.g. number of files in a merge). */
    fun requireCount(count: Int, freeLimit: Int, noun: String): GateResult =
        if (isPremiumNow() || count <= freeLimit) GateResult.Allowed
        else {
            analytics.track(AnalyticsEvent.GateBlocked(noun, "count_limit"))
            GateResult.Blocked(
                PremiumPrompt(
                    title = "Free limit reached",
                    message = "The free plan is limited to $freeLimit $noun per operation. " +
                        "Upgrade to Premium for unlimited $noun.",
                )
            )
        }

    /**
     * Daily quota. For free users this CONSUMES one use on success, so call it once,
     * right before performing the action, and only proceed on [GateResult.Allowed].
     */
    suspend fun consumeDaily(feature: DailyLimitedFeature): GateResult {
        if (isPremiumNow()) return GateResult.Allowed
        val allowed = userPreferences.tryConsumeDaily(feature.key, feature.freeDailyLimit)
        return if (allowed) {
            GateResult.Allowed
        } else {
            analytics.track(AnalyticsEvent.GateBlocked(feature.name, "daily_limit"))
            GateResult.Blocked(
                PremiumPrompt(
                    title = "Daily limit reached",
                    message = "The free plan allows ${feature.freeDailyLimit} " +
                        "${feature.title.lowercase()} per day. Upgrade to Premium for unlimited " +
                        "use, or try again tomorrow.",
                )
            )
        }
    }

    /**
     * Remaining free uses today for [feature]; emits a large number for premium users.
     * Reactive to entitlement: if the user upgrades mid-session this re-emits
     * [Int.MAX_VALUE] instead of staying stuck on the per-day counter.
     */
    fun remainingDaily(feature: DailyLimitedFeature): Flow<Int> =
        isPremium.flatMapLatest { premium ->
            if (premium) flowOf(Int.MAX_VALUE)
            else userPreferences.remainingDaily(feature.key, feature.freeDailyLimit)
        }

    /** True when free-tier output (scans, edits) should carry the PdfMaster watermark. */
    fun shouldWatermark(): Boolean = !isPremiumNow()
}
