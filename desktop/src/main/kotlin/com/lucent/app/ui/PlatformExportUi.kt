package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lucent.app.data.FontStore
import com.lucent.app.i18n.S
import com.lucent.app.platform.desktopPlatformContext

@Composable
fun rememberExportPdfFontHint(): String? {
    if (!exportPdfFontHintEnabled) return null
    val context = desktopPlatformContext
    val hasImportedFonts = remember { FontStore.fonts(context).isNotEmpty() }
    return if (hasImportedFonts) S.exportPdfFontHint else S.exportPdfNoFontHint
}
