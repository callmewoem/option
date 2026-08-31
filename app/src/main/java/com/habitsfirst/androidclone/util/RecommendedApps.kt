package com.habitsfirst.androidclone.util

/**
 * A curated list of commonly-blocked, attention-grabbing apps, used to power the app
 * picker's "Recommended" sort so a new user isn't starting from a totally blank,
 * alphabetically-sorted list of everything on their phone. Matching is by package name,
 * so this works regardless of what a given OEM/region calls the app.
 */
object RecommendedApps {
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

    fun isRecommended(packageName: String): Boolean = packageName in PACKAGE_NAMES
}
