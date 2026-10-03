package com.lucent.app.ui

import androidx.compose.ui.Modifier

expect fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier
