@file:JvmName("PlatformNotebookCoverCommonKt")
package com.lucent.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.lucent.app.data.AttachmentStore
import com.lucent.app.platform.LocalPlatformContext

expect fun decodeCoverBitmap(bytes: ByteArray): ImageBitmap?

@Composable
fun PlatformPhotoCover(colorKey: String, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val id = colorKey.removePrefix("photo:")
    val bitmap = remember(id) {
        try {
            AttachmentStore.readBytes(context, id)?.let { decodeCoverBitmap(it) }
        } catch (_: Throwable) { null }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
