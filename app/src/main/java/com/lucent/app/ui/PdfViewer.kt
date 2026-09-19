package com.lucent.app.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PdfViewer(att: Attachment) {
    val context = LocalContext.current
    var pages by remember(att.data) { mutableStateOf<List<Bitmap>?>(null) }
    var failed by remember(att.data) { mutableStateOf(false) }

    LaunchedEffect(att.data) {
        val rendered = withContext(Dispatchers.IO) {
            val file = AttachmentAccess.materialize(context, att) ?: return@withContext null
            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(descriptor)
                val out = ArrayList<Bitmap>(renderer.pageCount)
                val pageLimit = minOf(renderer.pageCount, MAX_PAGES)
                for (i in 0 until pageLimit) {
                    renderer.openPage(i).use { page ->
                        val scale = 2f
                        val width = (page.width * scale).toInt().coerceIn(1, MAX_DIM)
                        val height = (page.height * scale).toInt().coerceIn(1, MAX_DIM)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out.add(bmp)
                    }
                }
                out
            } catch (t: Throwable) {
                null
            } finally {
                try { renderer?.close() } catch (_: Throwable) {}
                try { descriptor?.close() } catch (_: Throwable) {}
            }
        }
        if (rendered != null) pages = rendered else failed = true
    }

    when {
        pages != null -> {
            val bitmaps = pages!!
            if (bitmaps.isEmpty()) {
                Text(com.lucent.app.i18n.S.pdfNoPages, color = Color.White)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(bitmaps) { index, bmp ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = com.lucent.app.i18n.S.pdfPageA11y(index + 1),
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                com.lucent.app.i18n.S.pdfPageOf(index + 1, bitmaps.size),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
        failed -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(com.lucent.app.i18n.S.pdfRenderFailed, color = Color.White)
        }
        else -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

private const val MAX_PAGES = 60
private const val MAX_DIM = 2200
