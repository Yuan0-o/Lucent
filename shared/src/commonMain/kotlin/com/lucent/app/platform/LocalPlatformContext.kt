package com.lucent.app.platform

import androidx.compose.runtime.staticCompositionLocalOf

val LocalPlatformContext = staticCompositionLocalOf<PlatformContext> {
    error("LocalPlatformContext not provided")
}
