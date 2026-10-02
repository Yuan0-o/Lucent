package com.lucent.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.lucent.app.data.AttachmentStore
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap

@Composable
fun PlatformPhotoCover(colorKey: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val id = colorKey.removePrefix("photo:")
    val bitmap = remember(id) {
        try {
            AttachmentStore.openInputStream(context, id)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }?.asImageBitmap()
        } catch (e: Exception) { null }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
