package com.lucent.app

import com.lucent.app.platform.PlatformContext

expect fun startGenerationService(context: PlatformContext, assistantName: String)

expect fun stopGenerationService(context: PlatformContext)
