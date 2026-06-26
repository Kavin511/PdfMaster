package com.pdfmaster.app.analytics

/**
 * Provider-agnostic analytics seam. The whole app talks to this interface, never to a vendor
 * SDK directly, so we can swap Logcat → Firebase → anything without touching call sites.
 *
 * Default binding is [LogcatAnalytics] (zero deps, works today). To send real data, follow
 * `ANALYTICS.md` to enable Firebase and switch the Hilt binding to `FirebaseAnalyticsTracker`.
 */
interface Analytics {
    /** Log a typed product event. */
    fun track(event: AnalyticsEvent)

    /** Set a sticky user property (e.g. premium status) for segmentation. */
    fun setUserProperty(key: String, value: String?)

    /** Associate events with a stable (non-PII) user/install id, or null to clear. */
    fun setUserId(id: String?)

    /**
     * Enable/disable underlying collection (e.g. Firebase `setAnalyticsCollectionEnabled`).
     * Default no-op for backends (like Logcat) that have nothing to toggle.
     */
    fun setCollectionEnabled(enabled: Boolean) {}
}

/** Qualifier for the raw vendor tracker, wrapped by the consent-aware [Analytics]. */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RawAnalytics

/** Stable tool identifiers used as the `tool` parameter across feature events. */
object Tool {
    const val MERGE = "merge"
    const val SPLIT = "split"
    const val COMPRESS = "compress"
    const val IMAGES_TO_PDF = "images_to_pdf"
    const val SCAN = "scan"
    const val EDITOR = "editor"
    const val SEARCH = "search"
    const val ANNOTATE = "annotate"
    const val SIGNATURE = "signature"
    const val FORM_FILL = "form_fill"
    const val PROTECT = "protect"
    const val UNLOCK = "unlock"
    const val PDF_TO_IMAGES = "pdf_to_images"
    const val PAGE_OPS = "page_ops"
}

/** Standard parameter keys (keep names stable — dashboards depend on them). */
object Param {
    const val TOOL = "tool"
    const val PLAN = "plan"
    const val FEATURE = "feature"
    const val GATE_TYPE = "gate_type"
    const val SOURCE = "source"
    const val REASON = "reason"
    const val RESULT_COUNT = "result_count"
    const val QUERY_LENGTH = "query_length"
    const val PAGE_COUNT = "page_count"
    const val FILE_COUNT = "file_count"
    const val IMAGE_COUNT = "image_count"
    const val WATERMARKED = "watermarked"
    const val IS_PREMIUM = "is_premium"
    const val REPLACE_ORIGINAL = "replace_original"
}

/** User-property keys. */
object UserProp {
    const val IS_PREMIUM = "is_premium"
}

/**
 * Typed event catalog. `name` is the Firebase event name; `params` are the attributes.
 * Add new events here so the dictionary stays in one place.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any?> = emptyMap()) {

    // ---- App lifecycle / activation ----
    data object AppOpen : AnalyticsEvent("app_open")
    data object OnboardingStarted : AnalyticsEvent("onboarding_started")
    data object OnboardingCompleted : AnalyticsEvent("onboarding_completed")

    /** First successful tool use in a session/install — the key activation signal. */
    data class FeatureActivated(val tool: String) :
        AnalyticsEvent("feature_activated", mapOf(Param.TOOL to tool))

    // ---- Generic tool funnel (use for every tool) ----
    data class ToolOpened(val tool: String) :
        AnalyticsEvent("tool_opened", mapOf(Param.TOOL to tool))

    data class ToolCompleted(val tool: String, val extra: Map<String, Any?> = emptyMap()) :
        AnalyticsEvent("tool_completed", mapOf<String, Any?>(Param.TOOL to tool) + extra)

    data class ToolFailed(val tool: String, val reason: String?) :
        AnalyticsEvent("tool_failed", mapOf(Param.TOOL to tool, Param.REASON to reason))

    // ---- Feature-specific insight events ----
    data class SearchPerformed(val queryLength: Int, val resultCount: Int) :
        AnalyticsEvent(
            "search_performed",
            mapOf(Param.QUERY_LENGTH to queryLength, Param.RESULT_COUNT to resultCount),
        )

    data class ScanCompleted(val pageCount: Int, val watermarked: Boolean) :
        AnalyticsEvent(
            "scan_completed",
            mapOf(Param.PAGE_COUNT to pageCount, Param.WATERMARKED to watermarked),
        )

    data object EditorTextEdited : AnalyticsEvent("editor_text_edited")

    data class EditorSaved(val replaceOriginal: Boolean) :
        AnalyticsEvent("editor_saved", mapOf(Param.REPLACE_ORIGINAL to replaceOriginal))

    data class SharePdf(val source: String) :
        AnalyticsEvent("share_pdf", mapOf(Param.SOURCE to source))

    // ---- Conversion funnel (the money path) ----
    /** A free user hit a wall. The strongest purchase-intent signal we have. */
    data class GateBlocked(val feature: String, val gateType: String) :
        AnalyticsEvent("gate_blocked", mapOf(Param.FEATURE to feature, Param.GATE_TYPE to gateType))

    data class PaywallViewed(val source: String) :
        AnalyticsEvent("paywall_viewed", mapOf(Param.SOURCE to source))

    data class PlanSelected(val plan: String) :
        AnalyticsEvent("plan_selected", mapOf(Param.PLAN to plan))

    data class PurchaseStarted(val plan: String) :
        AnalyticsEvent("purchase_started", mapOf(Param.PLAN to plan))

    data object PurchaseSucceeded : AnalyticsEvent("purchase_succeeded")

    data object PurchaseCancelled : AnalyticsEvent("purchase_cancelled")

    data class PurchaseFailed(val reason: String?) :
        AnalyticsEvent("purchase_failed", mapOf(Param.REASON to reason))

    data class PurchaseRestored(val restoredPremium: Boolean) :
        AnalyticsEvent("purchase_restored", mapOf(Param.IS_PREMIUM to restoredPremium))

    /** Escape hatch for one-off events without a dedicated subclass. */
    class Custom(name: String, params: Map<String, Any?> = emptyMap()) : AnalyticsEvent(name, params)
}
