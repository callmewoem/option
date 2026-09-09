package com.habitsfirst.androidclone.domain.model

/**
 * One purchasable premium product, as reported live by Play Billing (price/period come
 * from the store, never hardcoded) -- see
 * [com.habitsfirst.androidclone.data.billing.EntitlementRepository.products]. [productId]
 * matches a [com.habitsfirst.androidclone.data.billing.SubscriptionProducts] constant.
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
 * A feature gated behind [com.habitsfirst.androidclone.data.billing.EntitlementRepository.isPremium]
 * -- passed to the paywall (see `ui/paywall/PaywallScreen.kt`) so its pitch can name what
 * the user actually bounced off of, instead of a generic "go premium" (design spec §5:
 * be specific, not vague).
 */
enum class PremiumFeature(val headline: String, val pitch: String) {
    PHOTO_VERIFICATION(
        headline = "AI photo checking",
        pitch = "Photo-verified habits and the morning check-in both use an AI model to check your proof photo -- that costs real money per check, so it's a premium feature.",
    ),
    ACCOUNTABILITY_BUDDY(
        headline = "Accountability buddies",
        pitch = "Pair with a friend to share your daily progress and see theirs, synced through Locke's own backend.",
    ),
    UNLIMITED_HABITS(
        headline = "Unlimited gating habits",
        pitch = "The free tier caps how many gating habits you can add at once -- Premium removes the limit.",
    ),
    GENERAL(
        headline = "Locke Premium",
        pitch = "Unlimited gating habits, accountability buddies, and AI photo checking.",
    ),
}
