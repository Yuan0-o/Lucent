package com.lucent.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.lucent.app.data.AttachmentStore

@Composable
fun PlatformPhotoCover(colorKey: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val id = colorKey.removePrefix("photo:")
    val bitmap = remember(id) {
        try {
            AttachmentStore.openInputStream(context, id)?.use {
                decodeCoverBitmap(it.readBytes())
            }
        } catch (e: Exception) { null }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
