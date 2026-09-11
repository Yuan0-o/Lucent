package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lucent.app.data.FontStore
import com.lucent.app.i18n.S

/**
 * Desktop seam for the shared export picker. The PDF hint reflects the fonts currently available
 * to the desktop document exporter.
 */
@Composable
fun rememberExportPdfFontHint(): String? {
    val context = LocalContext.current
    val hasImportedFonts = remember { FontStore.fonts(context).isNotEmpty() }
    return if (hasImportedFonts) S.exportPdfFontHint else S.exportPdfNoFontHint
}
