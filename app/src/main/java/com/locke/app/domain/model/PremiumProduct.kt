package com.locke.app.domain.model

/**
 * One purchasable premium product, as reported live by Play Billing (price/period come
 * from the store, never hardcoded) -- see
 * [com.locke.app.data.billing.EntitlementRepository.products]. [productId]
 * matches a [com.locke.app.data.billing.SubscriptionProducts] constant.
 */
data class PremiumProduct(
    val productId: String,
    val tier: SubscriptionTier,
    /** Store-formatted, localized recurring price, e.g. "$4.99" -- the price that applies *after* [trialLabel]'s free period, if any. */
    val formattedPrice: String,
    /** e.g. "per month" / "per year" -- null for the one-time [SubscriptionTier.LIFETIME] product. */
    val billingPeriodLabel: String?,
    /**
     * e.g. "3 days free" -- set whenever Play Console currently offers this device a
     * free-trial phase on this subscription (it won't for a device that's already used
     * one; Play Billing itself decides eligibility, this just reflects whatever offer
     * came back). Null for [SubscriptionTier.LIFETIME], which has no trial concept.
     */
    val trialLabel: String? = null,
)

/**
 * A feature gated behind [com.locke.app.data.billing.EntitlementRepository.isPremium]
 * -- passed to the paywall (see `ui/paywall/PaywallScreen.kt`) so its pitch can name what
 * the user actually bounced off of, instead of a generic "go premium" (design spec §5:
 * be specific, not vague). There's no permanent free tier: every one of these requires
 * an active (trial or paid) subscription, full stop -- the pitch below always points at
 * starting the free trial, never at a "you've used up your free X" framing.
 */
enum class PremiumFeature(val headline: String, val pitch: String) {
    PHOTO_VERIFICATION(
        headline = "AI photo checking",
        pitch = "Photo-verified habits and the morning check-in use an AI model to check your proof photo -- " +
            "start your free trial to use it.",
    ),
    ACCOUNTABILITY_BUDDY(
        headline = "Accountability buddies",
        pitch = "Pair with a friend to share your daily progress and see theirs, synced through Locke's own " +
            "backend -- start your free trial to add one.",
    ),
    UNLIMITED_HABITS(
        headline = "More gating habits",
        pitch = "Start your free trial to gate on more than the habits you set up during onboarding.",
    ),
    GENERAL(
        headline = "Locke Premium",
        pitch = "Unlimited gating habits, accountability buddies, and AI photo checks -- start your free trial.",
    ),
}
