package com.lucent.app.ui

import androidx.compose.runtime.Composable

@Composable
actual fun LucentBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
