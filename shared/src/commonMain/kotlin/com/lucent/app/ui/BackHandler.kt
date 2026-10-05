package com.lucent.app.ui

import androidx.compose.runtime.Composable

@Composable
expect fun LucentBackHandler(enabled: Boolean = true, onBack: () -> Unit)
