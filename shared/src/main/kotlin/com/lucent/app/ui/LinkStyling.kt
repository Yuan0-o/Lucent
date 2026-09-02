package com.lucent.app.ui

import androidx.compose.ui.graphics.Color

/**
 * Link classification, colouring and opening (C-group tasks 6 and 11).
 *
 * ### Task 11: internal and external links must not look alike
 *
 * Both kinds of link were drawn in the theme accent. That made them indistinguishable, and the two
 * do *completely different things*: `[[Groceries]]` moves you inside your own notes and cannot
 * leak anything, while `https://…` hands your attention — and, once task 6 is on, your default
 * browser — to somebody else's server. A user deciding whether to tap is entitled to know which of
 * those they are about to do **before** they tap, not after.
 *
 * Three visually distinct states, and the third already existed:
 *
 *  - **Internal** ([INTERNAL_HUE_SHIFT] applied to the theme accent) — stays inside Lucent.
 *  - **External** ([ExternalLinkColor]) — leaves Lucent.
 *  - **Broken internal** (the existing overdue red) — points at a note that does not exist.
 *
 * External is deliberately the *cooler, less inviting* of the two: an outbound link should not be
 * the most attractive thing on the page. The colours are picked to hold their distinction on both
 * the light and dark backdrops, and neither is the pure accent, so a themed accent that happens to
 * land near one of them cannot collapse the difference.
 *
 * ### Task 6: opening a bare URL in the system browser
 *
 * With [com.lucent.app.data.SettingsRepository.openLinksExternally] on, any bare `http://` or
 * `https://` URL in a note, a task or an assistant reply becomes tappable and opens in the user's
 * default browser — no `[text](url)` syntax, no Markdown mode required.
 *
 * That last point is the reason this is an independent switch rather than a sub-toggle of Markdown.
 * The two answer different questions: Markdown asks "should my asterisks become bold", this asks
 * "should a URL I pasted be tappable". Someone who writes plain text and pastes links wants the
 * second without the first, and today they had to accept both or neither.
 *
 * ### The trade-off, which the Settings copy states plainly
 *
 * **For:** pasted links work the way they do everywhere else; no syntax to learn; the URL stays
 * readable as text, so what you are about to open is visible before you tap it.
 *
 * **Against:** tapping hands the URL to another app, which is a real privacy step — the browser
 * sees the address, the destination sees your IP and whatever your browser sends, and none of that
 * is covered by Lucent's own encryption. A mistyped or malicious URL in a shared note becomes one
 * tap from being opened. Blackout Mode therefore overrides this switch entirely: while Blackout is
 * on, nothing hands anything to a browser regardless of what is set here.
 */
object LinkStyling {

    /**
     * Colour for links that leave the app. A muted teal — clearly not the accent, legible on both
     * backdrops, and deliberately calmer than the internal colour.
     */
    val ExternalLinkColor = Color(0xFF3F8E8E)

    /** Same, lightened for dark backdrops where the base tone loses contrast. */
    val ExternalLinkColorDark = Color(0xFF6FD3D3)

    /**
     * How far the internal-link colour is pushed from the raw theme accent, 0..1.
     *
     * Internal links stay *derived from* the accent, so they still feel like part of the chosen
     * theme, but are nudged enough that they cannot be confused with the external colour whatever
     * accent the user picked.
     */
    const val INTERNAL_HUE_SHIFT = 0.12f

    /** The colour to draw an internal `[[wiki]]` link in, given the active accent. */
    fun internalColor(accent: Color, onDarkBackdrop: Boolean): Color =
        if (onDarkBackdrop) accent.lighten(INTERNAL_HUE_SHIFT) else accent.darken(INTERNAL_HUE_SHIFT)

    /** The colour to draw an external URL in. */
    fun externalColor(onDarkBackdrop: Boolean): Color =
        if (onDarkBackdrop) ExternalLinkColorDark else ExternalLinkColor

    private fun Color.lighten(amount: Float): Color = Color(
        red = red + (1f - red) * amount,
        green = green + (1f - green) * amount,
        blue = blue + (1f - blue) * amount,
        alpha = alpha
    )

    private fun Color.darken(amount: Float): Color = Color(
        red = red * (1f - amount),
        green = green * (1f - amount),
        blue = blue * (1f - amount),
        alpha = alpha
    )
}

/**
 * Finds bare URLs in plain text.
 *
 * Kept as its own object so the note renderer, the task renderer and the assistant transcript all
 * classify a URL the same way — three slightly different regexes is how "this link is tappable
 * here but not there" bugs are born.
 */
object ExternalLinks {

    /**
     * Bare `http(s)://…` runs.
     *
     * The trailing-character class is the fiddly part and is worth spelling out: a URL at the end of
     * a sentence is followed by punctuation that is *not* part of it (`see https://example.com.`),
     * and a URL inside brackets must not swallow the closing bracket. So the match stops before a
     * trailing `.,;:!?)]}'"` run, while still allowing those characters *inside* a URL, where they
     * are legal and common (query strings, fragments).
     */
    private val URL_REGEX = Regex(
        """https?://[^\s<>"']+[^\s<>"'.,;:!?)\]}]"""
    )

    /** Every bare URL in [text], as (range, url) pairs, in order of appearance. */
    fun findAll(text: String): List<Pair<IntRange, String>> =
        URL_REGEX.findAll(text).map { it.range to it.value }.toList()

    /** Whether [text] contains at least one bare URL — a cheap pre-check before doing real work. */
    fun any(text: String): Boolean = URL_REGEX.containsMatchIn(text)

    /**
     * Whether this URL is safe to hand to a browser.
     *
     * Only `http` and `https` are ever opened. The scheme is checked here rather than trusted from
     * the regex because this function is also the gate for URLs that arrive from *outside* the
     * regex — an assistant reply, an imported note, a shared payload — and `file://`, `intent://`
     * and `javascript:` are all things that must never reach a browser handoff from a text field
     * the user did not necessarily write.
     */
    fun isOpenable(url: String): Boolean {
        val lower = url.trim().lowercase()
        return (lower.startsWith("http://") || lower.startsWith("https://")) &&
            !lower.contains('\n') &&
            !lower.contains('\r')
    }
}
