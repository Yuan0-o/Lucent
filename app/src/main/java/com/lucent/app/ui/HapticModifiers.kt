package com.lucent.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext

fun Modifier.hapticClickable(onClick: () -> Unit): Modifier = composed {
    val context = LocalContext.current
    clickable {
        Haptics.tick(context)
        onClick()
    }
}
