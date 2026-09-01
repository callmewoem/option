package com.habitsfirst.androidclone.domain.model

/**
 * The paid tiers Locke's entitlement system recognizes: two recurring subscription
 * lengths plus a single one-time-purchase lifetime tier. This is purely the shape of
 * what can be *purchased* -- whether a given tier actually unlocks premium features is
 * decided by [com.habitsfirst.androidclone.data.billing.EntitlementRepository], not by
 * this enum.
 */
enum class SubscriptionTier(val displayName: String) {
    NONE("Free"),
    MONTHLY("Monthly"),
    ANNUAL("Annual"),
    LIFETIME("Lifetime"),
    ;

    companion object {
        val DEFAULT = NONE

        fun fromId(id: String?): SubscriptionTier = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}
