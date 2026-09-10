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
    /** Store-formatted, localized price, e.g. "$4.99". */
    val formattedPrice: String,
    /** e.g. "per month" / "per year" -- null for the one-time [SubscriptionTier.LIFETIME] product. */
    val billingPeriodLabel: String?,
)

/**
 * A feature gated behind [com.locke.app.data.billing.EntitlementRepository.isPremium]
 * -- passed to the paywall (see `ui/paywall/PaywallScreen.kt`) so its pitch can name what
 * the user actually bounced off of, instead of a generic "go premium" (design spec §5:
 * be specific, not vague). Free tier is deliberately generous, not a trial -- these
 * pitches say so honestly (the numbers here must match
 * [com.locke.app.data.repository.PreferencesRepository]'s `MAX_FREE_GATING_HABITS` /
 * `MAX_FREE_BUDDIES` / `FREE_VERIFICATIONS_PER_MONTH`, since a paywall that
 * exaggerates what's being taken away erodes trust rather than converting anyone).
 */
enum class PremiumFeature(val headline: String, val pitch: String) {
    PHOTO_VERIFICATION(
        headline = "AI photo checking",
        pitch = "Photo-verified habits and the morning check-in use an AI model to check your proof photo -- " +
            "3 checks a month are free, every month. You've used this month's free checks -- upgrade for unlimited.",
    ),
    ACCOUNTABILITY_BUDDY(
        headline = "Accountability buddies",
        pitch = "Pair with a friend to share your daily progress and see theirs, synced through Locke's own " +
            "backend. Free plan includes 1 buddy -- you've used it. Upgrade for more.",
    ),
    UNLIMITED_HABITS(
        headline = "Unlimited gating habits",
        pitch = "The free plan covers up to 5 gating habits at once -- you've reached that. Upgrade for no limit.",
    ),
    GENERAL(
        headline = "Locke Premium",
        pitch = "The free plan already covers 5 gating habits, 1 accountability buddy, and 3 AI photo checks " +
            "a month. Premium removes all three limits.",
    ),
}
