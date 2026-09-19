package androidx.compose.ui.platform

import android.content.Context
import android.content.DesktopContext
import androidx.compose.runtime.staticCompositionLocalOf

val LocalContext = staticCompositionLocalOf<Context> { DesktopContext }
