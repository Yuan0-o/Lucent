package com.lucent.app.ui

import androidx.compose.ui.graphics.Color

object LinkStyling {

    val ExternalLinkColor = Color(0xFF3F8E8E)

    val ExternalLinkColorDark = Color(0xFF6FD3D3)

    const val INTERNAL_HUE_SHIFT = 0.12f

    fun internalColor(accent: Color, onDarkBackdrop: Boolean): Color =
        if (onDarkBackdrop) accent.lighten(INTERNAL_HUE_SHIFT) else accent.darken(INTERNAL_HUE_SHIFT)

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

object ExternalLinks {

    private val URL_REGEX = Regex(
        """https?://[^\s<>"']+[^\s<>"'.,;:!?)\]}]"""
    )

    fun findAll(text: String): List<Pair<IntRange, String>> =
        URL_REGEX.findAll(text).map { it.range to it.value }.toList()

    fun any(text: String): Boolean = URL_REGEX.containsMatchIn(text)

    fun isOpenable(url: String): Boolean {
        val lower = url.trim().lowercase()
        return (lower.startsWith("http://") || lower.startsWith("https://")) &&
            !lower.contains('\n') &&
            !lower.contains('\r')
    }
}
