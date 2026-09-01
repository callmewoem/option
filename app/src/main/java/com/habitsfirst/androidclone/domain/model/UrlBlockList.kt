package com.habitsfirst.androidclone.domain.model

/**
 * How a block can be lifted, if at all -- set per [UrlBlockList] (and read by
 * [com.habitsfirst.androidclone.service.AppBlockAccessibilityService] the same way for
 * every domain in that list).
 */
enum class BlockMode {
    /** Locked by the same rules as blocked apps: today's gating habits, the bedtime curfew, a redeemed grace token. */
    GATED,

    /** Never lifts -- no completed habit, grace token, or task-skip token unlocks it. */
    PERMANENT,
}

/** Where a [UrlBlockList]'s domains come from. */
enum class BlockListSource {
    /** The bundled "Porn & adult content" starter list. */
    PREMADE_PORN,

    /** The bundled "Social media" starter list. */
    PREMADE_SOCIAL,

    /** A list the user built themselves. */
    CUSTOM,
    ;

    val isPremade: Boolean get() = this != CUSTOM
}

/**
 * A named collection of blocked domains -- one of the two built-in blanket lists or a
 * list the user made themselves. [domainCount] is always live (the premade lists' size
 * comes from the bundled asset, a custom list's from how many domains it currently has).
 */
data class UrlBlockList(
    val id: String,
    val name: String,
    val source: BlockListSource,
    val blockMode: BlockMode,
    val isEnabled: Boolean,
    val domainCount: Int,
)
