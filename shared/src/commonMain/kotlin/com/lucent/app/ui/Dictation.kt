package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun DictationButton(onText: (String) -> Unit, modifier: Modifier = Modifier)
