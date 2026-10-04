package androidx.compose.ui.platform

import com.lucent.app.platform.PlatformContext
import android.content.DesktopContext
import androidx.compose.runtime.staticCompositionLocalOf

val LocalContext = staticCompositionLocalOf<Context> { DesktopContext }
