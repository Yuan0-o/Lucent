package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/** Copy ARGB scratch pixels to owned RGBA bytes before publishing an immutable Skia image. */
fun diffuseImageBitmap(pixels: IntArray, edge: Int): ImageBitmap {
    require(edge > 0 && edge.toLong() * edge <= pixels.size.toLong())
    val pixelCount = edge * edge
    val bytes = ByteArray(pixelCount * 4)
    for (index in 0 until pixelCount) {
        val argb = pixels[index]
        val offset = index * 4
        bytes[offset] = (argb ushr 16).toByte()
        bytes[offset + 1] = (argb ushr 8).toByte()
        bytes[offset + 2] = argb.toByte()
        bytes[offset + 3] = (argb ushr 24).toByte()
    }
    // makeRaster copies bytes into Skia-owned storage; neither array is shared with a displayed frame.
    return Image.makeRaster(
        ImageInfo(edge, edge, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
        bytes,
        edge * 4
    ).toComposeImageBitmap()
}

/** No window-focus heuristic: visible, non-minimized windows may animate even when unfocused. */
@Composable
fun rememberBackgroundEnvironment(active: Boolean): BackgroundEnvironment {
    var motionEnabled by remember {
        mutableStateOf(WindowsBackgroundMotion.readEnabled() ?: true)
    }
    LaunchedEffect(active) {
        if (!active || !WindowsBackgroundMotion.isWindows) return@LaunchedEffect
        // Avoid native window subclassing. A cheap read every two seconds notices accessibility
        // changes while visible; hiding/minimizing cancels this loop until the window returns.
        while (isActive) {
            val enabled = withContext(Dispatchers.IO) { WindowsBackgroundMotion.readEnabled() }
            if (enabled != null) motionEnabled = enabled
            delay(2_000L)
        }
    }
    return BackgroundEnvironment(active = active, motionEnabled = motionEnabled)
}

private object WindowsBackgroundMotion {
    val isWindows = System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)
    private const val SPI_GETCLIENTAREAANIMATION = 0x1042
    private val api: WindowsAnimationApi? by lazy {
        if (isWindows) runCatching {
            Native.load("user32", WindowsAnimationApi::class.java)
        }.getOrNull() else null
    }

    fun readEnabled(): Boolean? {
        val native = api ?: return null
        return runCatching {
            val enabled = IntByReference(1)
            if (native.SystemParametersInfoW(SPI_GETCLIENTAREAANIMATION, 0, enabled, 0)) {
                enabled.value != 0
            } else null
        }.getOrNull()
    }
}

/** JNA's bundled User32 omits this API; this binding only reads the system preference. */
private interface WindowsAnimationApi : StdCallLibrary {
    @Suppress("FunctionName")
    fun SystemParametersInfoW(action: Int, parameter: Int, value: IntByReference, flags: Int): Boolean
}
