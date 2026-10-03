package com.lucent.app.data

import com.lucent.app.platform.PlatformContext

expect object ShareIntegration {
    fun setEnabled(context: PlatformContext, enabled: Boolean)
}
