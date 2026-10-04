package com.lucent.app.harness.plugins

data class PluginSource(
    val id: String,
    val label: String,
    val url: String,
    val official: Boolean = false,
    val sha256: String = "",
    val bytes: Long = 0L
)

data class PluginSpec(
    val id: String,
    val name: String,
    val summary: String,
    val android: Boolean,
    val desktop: Boolean,
    val bytes: Long,
    val sources: List<PluginSource>,
    val detectCommand: String,
    val installScript: String,
    val removeScript: String = "",
    val licence: String,
    val homepage: String,
    val needsShell: Boolean = true,
    val windowsDetect: String = "",
    val windowsInstall: String = "",
    val windowsRemove: String = ""
) {
    fun probeFor(android: Boolean): String = if (!android && windowsDetect.isNotBlank()) windowsDetect else detectCommand

    fun installFor(android: Boolean): String = if (!android && windowsInstall.isNotBlank()) windowsInstall else installScript

    fun removeFor(android: Boolean): String = if (!android && windowsRemove.isNotBlank()) windowsRemove else removeScript
}
