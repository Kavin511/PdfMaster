package com.pdfmaster.app.billing

/**
 * Product IDs and the premium plan ladder.
 *
 * These IDs MUST match what you create in the Google Play Console:
 *  - One subscription product ([SUBS_PRODUCT_ID]) with two base plans:
 *      • "weekly"  — $4.99/week, with a 3-day free-trial offer attached
 *      • "annual"  — $39.99/year
 *  - One in-app (one-time) product ([LIFETIME_PRODUCT_ID]) — $79.99 lifetime.
 *
 * Pricing is defined in the Play Console, NOT here. The app reads the localized,
 * formatted price back from Play at runtime via [BillingManager.formattedPrices].
 */
object BillingConstants {

    /** Single subscription product that holds the weekly + annual base plans. */
    const val SUBS_PRODUCT_ID = "pdfmaster_premium"

    const val BASE_PLAN_WEEKLY = "weekly"
    const val BASE_PLAN_ANNUAL = "annual"

    /** One-time, non-consumable lifetime unlock. */
    const val LIFETIME_PRODUCT_ID = "pdfmaster_lifetime"

    val SUBSCRIPTION_PRODUCT_IDS = listOf(SUBS_PRODUCT_ID)
    val INAPP_PRODUCT_IDS = listOf(LIFETIME_PRODUCT_ID)

    /** Every product id that grants premium, used when reconciling purchases. */
    val PREMIUM_PRODUCT_IDS = SUBSCRIPTION_PRODUCT_IDS + INAPP_PRODUCT_IDS
}

/**
 * The user-selectable plans. Each maps to a concrete Play product + (for subs) a base plan.
 * This is the single bridge between the UI and the billing layer.
 */
enum class PremiumPlan(
    val productId: String,
    /** Base plan id for subscriptions; null for the one-time lifetime product. */
    val basePlanId: String?,
    val isSubscription: Boolean
) {
    WEEKLY(BillingConstants.SUBS_PRODUCT_ID, BillingConstants.BASE_PLAN_WEEKLY, true),
    ANNUAL(BillingConstants.SUBS_PRODUCT_ID, BillingConstants.BASE_PLAN_ANNUAL, true),
    LIFETIME(BillingConstants.LIFETIME_PRODUCT_ID, null, false);
}
