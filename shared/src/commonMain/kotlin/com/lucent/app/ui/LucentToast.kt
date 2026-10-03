package com.lucent.app.ui

import com.lucent.app.platform.PlatformContext

expect object LucentToast {
    fun show(context: PlatformContext, message: String, longDuration: Boolean = false)
}
