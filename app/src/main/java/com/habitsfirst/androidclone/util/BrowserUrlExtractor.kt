package com.habitsfirst.androidclone.util

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads the page currently shown in a browser's address bar via the accessibility
 * tree. Android has no public API to inspect what a browser is displaying, so this
 * applies the same technique the rest of Locke's blocking relies on -- read what's on
 * screen, react to it -- to the one field that reliably carries the current URL.
 *
 * This is inherently best-effort: it depends on each browser's address-bar view id
 * staying stable across versions, and on the bar actually holding the full host (some
 * browsers elide the scheme or truncate a long path, which [extractHost] tolerates).
 * Like the rest of the app's blocking, the goal is friction, not an airtight filter.
 */
object BrowserUrlExtractor {

    /** Bound on the id-hint fallback's tree walk, so a deep/wide page can't stall the accessibility event thread. */
    private const val MAX_FALLBACK_NODES_VISITED = 400

    /** Package name -> view-id suffixes (after "<package>:id/") its address bar is known to use, tried in order. */
    private val ADDRESS_BAR_ID_SUFFIXES: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.chrome.dev" to listOf("url_bar"),
        "com.chrome.canary" to listOf("url_bar"),
        "com.microsoft.emmx" to listOf("url_bar"), // Edge (Chromium-based)
        "com.brave.browser" to listOf("url_bar"),
        "com.opera.browser" to listOf("url_field", "addressbarEdit"),
        "com.opera.browser.beta" to listOf("url_field", "addressbarEdit"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.opera.gx" to listOf("url_field"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view"),
        "org.mozilla.fenix" to listOf("mozac_browser_toolbar_url_view"),
        "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view", "display_url"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput", "customTabToolbarTitle"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text", "url_bar"),
        "com.sec.android.app.sbrowser.beta" to listOf("location_bar_edit_text"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
        "com.UCMobile.intl" to listOf("address_bar_edit_text"),
        "com.mi.globalbrowser" to listOf("address_editor_text"),
        "mark.via.gp" to listOf("search_box"),
        "com.yandex.browser" to listOf("bro_omnibar_address_title_text", "bro_omnibar_address_edit_text"),
    )

    /** Every package this can plausibly read a URL from -- used to skip non-browser accessibility events cheaply. */
    val KNOWN_BROWSER_PACKAGES: Set<String> = ADDRESS_BAR_ID_SUFFIXES.keys

    /** Best-effort current address-bar text for [packageName], or null if it can't be found at all. */
    fun findCurrentUrl(root: AccessibilityNodeInfo?, packageName: String): String? {
        if (root == null) return null
        ADDRESS_BAR_ID_SUFFIXES[packageName]?.forEach { suffix ->
            findFirstNonBlankText(root, "$packageName:id/$suffix")?.let { return it }
        }
        // The exact id above is a snapshot of one app version and does drift (a browser
        // update renames its address-bar view), which would otherwise silently stop
        // blocking for that browser until this list is updated. Fall back to any node
        // whose own resource id still *looks* address-bar-ish, rather than trusting only
        // an exact match -- still anchored to the widget's identity (not page content),
        // so this doesn't risk matching arbitrary text on the loaded page.
        return findFirstNonBlankTextByIdHint(root)
    }

    private fun findFirstNonBlankText(root: AccessibilityNodeInfo, viewId: String): String? {
        val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(viewId) }.getOrNull().orEmpty()
        try {
            return nodes.firstNotNullOfOrNull { node -> node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
        } finally {
            @Suppress("DEPRECATION")
            nodes.forEach { runCatching { it.recycle() } }
        }
    }

    /** Substrings of a resource-id's own name (after "<package>:id/") that read as an address bar across the browsers above. */
    private val ADDRESS_BAR_ID_HINTS = listOf(
        "url_bar", "url_field", "url_view", "url_edit", "url_title", "address_bar",
        "address_editor", "addressbar", "omnibar", "location_bar", "toolbar_url",
    )

    /** Breadth-first scan, capped so a pathological tree can't stall the accessibility event thread. */
    private fun findFirstNonBlankTextByIdHint(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_FALLBACK_NODES_VISITED) {
            val node = queue.removeFirst()
            visited++
            val id = node.viewIdResourceName
            if (id != null && ADDRESS_BAR_ID_HINTS.any { id.contains(it, ignoreCase = true) }) {
                node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /** Extracts a bare, lowercased host from address-bar text such as "https://x.com/path" or just "x.com". Null if nothing host-shaped is found. */
    fun extractHost(addressBarText: String): String? {
        var value = addressBarText.trim().lowercase()
        if (value.isEmpty()) return null
        // Some browsers show a leading site-info glyph as separate text, or append
        // trailing suggestion/search text while the bar is focused -- the host is
        // always the first whitespace-free token.
        value = value.substringBefore(' ')
        value = value.removePrefix("https://").removePrefix("http://")
        value = value.substringBefore('/').substringBefore('?').substringBefore(':')
        return value.takeIf { it.contains('.') }
    }
}
