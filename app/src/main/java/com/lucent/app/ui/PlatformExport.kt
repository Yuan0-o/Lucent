package com.lucent.app.ui

/**
 * Android seam for the shared export picker. Imported PDF fonts are a desktop-only library today,
 * so Android keeps the picker behaviour unchanged by providing no platform font hint.
 */
fun rememberExportPdfFontHint(): String? = null
