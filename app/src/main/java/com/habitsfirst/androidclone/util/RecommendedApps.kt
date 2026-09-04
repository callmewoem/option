package com.habitsfirst.androidclone.util

/**
 * Powers the app picker's "Recommended" badge and sort, so a new user isn't starting
 * from a totally blank, alphabetically-sorted list of everything on their phone.
 *
 * The real signal is this phone's own screen time: once onboarding has read today's
 * per-app usage (see [InstalledAppsProvider.getTodayUsageMinutes]), whichever apps
 * have eaten the most minutes today are the ones flagged -- that's a stronger signal
 * than a generic guess, and it catches apps [PACKAGE_NAMES] doesn't know about.
 * [PACKAGE_NAMES] is only the fallback for when there's no usage data yet to go on:
 * usage access hasn't been granted, or it's early enough in the day that nothing has
 * crossed [HIGH_USAGE_MINUTES_THRESHOLD]. Matching is by package name, so this works
 * regardless of what a given OEM/region calls the app.
 */
object RecommendedApps {
    /** Today's minutes in the foreground for an app to earn "Recommended" on screen time alone. */
    private const val HIGH_USAGE_MINUTES_THRESHOLD = 15

    private val PACKAGE_NAMES = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok (alt region build)
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.twitter.android", // X
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.pinterest",
        "com.netflix.mediaclient",
        "tv.twitch.android.app",
        "com.discord",
        "com.zhiliaoapp.musically.go",
        "com.tencent.mm", // WeChat
        "com.whatsapp",
        "com.google.android.apps.tachyon", // Google Duo/Meet
        "com.king.candycrushsaga",
        "com.supercell.clashofclans",
        "com.roblox.client",
    )

    /** Static-list check only -- prefer [recommendedPackages] wherever today's usage is available. */
    fun isRecommended(packageName: String): Boolean = packageName in PACKAGE_NAMES

    /**
     * The set of packages to badge "Recommended", given today's per-app foreground
     * minutes (see [InstalledAppsProvider.getTodayUsageMinutes] -- empty if usage
     * access isn't granted). Screen time wins whenever it has something to say: any
     * app already at [HIGH_USAGE_MINUTES_THRESHOLD] minutes today is flagged, on the
     * theory that what's actually eating this phone's day beats a generic guess.
     * Only when nothing has crossed that bar yet -- no usage data, or too early in
     * the day for it to mean anything -- does this fall back to the curated list.
     */
    fun recommendedPackages(usageMinutesByPackage: Map<String, Int>): Set<String> {
        val highUsage = usageMinutesByPackage.filterValues { it >= HIGH_USAGE_MINUTES_THRESHOLD }.keys
        return highUsage.ifEmpty { PACKAGE_NAMES }
    }
}
