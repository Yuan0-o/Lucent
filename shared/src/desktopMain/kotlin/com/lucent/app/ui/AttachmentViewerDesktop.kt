package com.lucent.app.ui

import com.lucent.app.platform.PlatformContext
import com.lucent.app.platform.desktopPlatformContext
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.io.encoding.Base64
import com.lucent.app.AppScope
import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentAccess
import com.lucent.app.data.AttachmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Surface
import java.awt.BasicStroke
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

@Composable
actual fun AttachmentViewerDialog(att: Attachment, onDismiss: () -> Unit) =
    AttachmentViewerDialog(listOf(att), 0, onDismiss)

@Composable
actual fun AttachmentViewerDialog(attachments: List<Attachment>, initialIndex: Int, onDismiss: () -> Unit) {
    if (attachments.isEmpty()) return
    val context = desktopPlatformContext
    val save = rememberSaveAttachmentLauncher()
    var editing by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, attachments.size - 1)
    ) { attachments.size }
    var currentImageZoomed by remember { mutableStateOf(false) }
    val current = pagerState.currentPage.coerceIn(0, attachments.size - 1)
    LaunchedEffect(current) { currentImageZoomed = false }
    val att = attachments[current]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 96.dp, top = 56.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !currentImageZoomed,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val a = attachments[page]
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            a.isImage -> ZoomableImage(
                                a,
                                if (page == current) reloadKey else 0,
                                onZoomChanged = { zoomed -> if (page == current) currentImageZoomed = zoomed }
                            )
                            a.isPdf -> PdfViewer(a)
                            DocumentText.canExtract(a) -> TextPreview(a)
                            else -> NonPreviewableInfo(a)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(att.name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                if (attachments.size > 1) {
                    Text(
                        "${current + 1} / ${attachments.size}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = com.lucent.app.i18n.S.actionClose, tint = Color.White)
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 32.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (att.isImage) {
                    ViewerAction(Icons.Default.Edit, com.lucent.app.i18n.S.actionEdit) { editing = true }
                }
                ViewerAction(Icons.Default.Download, com.lucent.app.i18n.S.actionSave) { save(att) }
                ViewerAction(Icons.AutoMirrored.Filled.Launch, com.lucent.app.i18n.S.openWith) {
                    Thread {
                        val ok = AttachmentAccess.openExternally(context, att)
                        if (!ok) LucentToast.show(context, com.lucent.app.i18n.S.cantOpenFile)
                    }.start()
                }
            }
        }
    }

    if (editing) {
        ImageEditorDialog(
            att = att,
            onDismiss = { editing = false },
            onSaved = { editing = false; reloadKey++ }
        )
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun ZoomableImage(
    att: Attachment,
    reloadKey: Int = 0,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    val context = desktopPlatformContext
    var bitmap by remember(att.data, reloadKey) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(att.data, reloadKey) { mutableStateOf(false) }

    LaunchedEffect(att.data, reloadKey) {
        val bmp = withContext(Dispatchers.IO) {
            val bytes = readAttachmentBytes(context, att, maxBytes = 64L * 1024 * 1024)
            if (bytes != null) decodeSampledBitmap(bytes, maxDim = 2560) else null
        }
        if (bmp != null) bitmap = bmp else failed = true
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    fun panLimits(): Pair<Float, Float> {
        val bmp = bitmap ?: return 0f to 0f
        if (viewport.width == 0 || viewport.height == 0) return 0f to 0f
        val vw = viewport.width.toFloat()
        val vh = viewport.height.toFloat()
        val fit = minOf(vw / bmp.width.toFloat(), vh / bmp.height.toFloat())
        return ((bmp.width * fit * scale - vw).coerceAtLeast(0f) / 2f) to
            ((bmp.height * fit * scale - vh).coerceAtLeast(0f) / 2f)
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        val (maxX, maxY) = panLimits()
        offsetX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
        offsetY = (offsetY + panChange.y).coerceIn(-maxY, maxY)
    }

    val zoomed = scale > 1f
    LaunchedEffect(zoomed) { onZoomChanged(zoomed) }

    when {
        bitmap != null -> Image(
            bitmap = bitmap!!,
            contentDescription = att.name,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                .transformable(state = transformState, canPan = { scale > 1f })
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2f
                    })
                }
        )
        failed -> Text(com.lucent.app.i18n.S.cantLoadImage, color = Color.White)
        else -> CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun PdfViewer(att: Attachment) {
    val context = desktopPlatformContext
    var pages by remember(att.data) { mutableStateOf<List<ImageBitmap>?>(null) }
    var failed by remember(att.data) { mutableStateOf(false) }

    LaunchedEffect(att.data) {
        val rendered = withContext(Dispatchers.IO) {
            try {
                val bytes = readAttachmentBytes(context, att, maxBytes = 256L * 1024 * 1024) ?: return@withContext null
                org.apache.pdfbox.Loader.loadPDF(bytes).use { doc ->
                    val renderer = org.apache.pdfbox.rendering.PDFRenderer(doc)
                    val count = doc.numberOfPages.coerceAtMost(60)
                    (0 until count).mapNotNull { i ->
                        val image = renderer.renderImageWithDPI(i, 120f)
                        val baos = ByteArrayOutputStream()
                        javax.imageio.ImageIO.write(image, "png", baos)
                        decodeSampledBitmap(baos.toByteArray(), maxDim = 2000)
                    }
                }
            } catch (t: Throwable) {
                null
            }
        }
        if (rendered != null && rendered.isNotEmpty()) pages = rendered else failed = true
    }

    when {
        pages != null -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            pages!!.forEach { page ->
                Spacer(Modifier.height(8.dp))
                Image(
                    bitmap = page,
                    contentDescription = att.name,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        failed -> NonPreviewableInfo(att)
        else -> CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun NonPreviewableInfo(att: Attachment) {
    val context = desktopPlatformContext
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(att.name, color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(com.lucent.app.i18n.S.noPreviewForType, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .pointerInput(att.data) {
                    detectTapGestures(onTap = {
                        Thread {
                            val ok = AttachmentAccess.openExternally(context, att)
                            if (!ok) LucentToast.show(context, com.lucent.app.i18n.S.cantOpenFile)
                        }.start()
                    })
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(com.lucent.app.i18n.S.openWith, color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TextPreview(att: Attachment) {
    val context = desktopPlatformContext
    var result by remember(att.data) { mutableStateOf<DocumentText.Result?>(null) }
    var failed by remember(att.data) { mutableStateOf(false) }

    LaunchedEffect(att.data) {
        val extracted = withContext(Dispatchers.IO) { DocumentText.extract(context, att) }
        if (extracted != null) result = extracted else failed = true
    }

    when {
        result != null -> {
            val text = result!!.text
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(com.lucent.app.i18n.S.previewTextOnly, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                if (text.isEmpty()) {
                    Text(com.lucent.app.i18n.S.previewEmptyDocument, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                } else {
                    Text(text, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                if (result!!.truncated) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        com.lucent.app.i18n.S.previewTextTruncated,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        failed -> Text(com.lucent.app.i18n.S.cantLoadText, color = Color.White)
        else -> CircularProgressIndicator(color = Color.White)
    }
}

private fun decodeSampledBitmap(bytes: ByteArray, maxDim: Int = MAX_IMAGE_DIM): ImageBitmap? = try {
    val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
    val w = image.width
    val h = image.height
    if (w <= 0 || h <= 0) {
        null
    } else if (w <= maxDim && h <= maxDim) {
        image.toComposeImageBitmap()
    } else {
        val scale = maxDim.toFloat() / maxOf(w, h)
        val outW = maxOf(1, (w * scale).toInt())
        val outH = maxOf(1, (h * scale).toInt())
        val surface = Surface.makeRasterN32Premul(outW, outH)
        surface.canvas.drawImageRect(
            image,
            org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()),
            org.jetbrains.skia.Rect.makeWH(outW.toFloat(), outH.toFloat()),
            null
        )
        surface.makeImageSnapshot().toComposeImageBitmap()
    }
} catch (t: Throwable) {
    null
}

private const val MAX_IMAGE_DIM = 1600

private fun readAttachmentBytes(context: PlatformContext, att: Attachment, maxBytes: Long = 32L * 1024 * 1024): ByteArray? {
    return if (AttachmentStore.looksLikeId(att.data)) {
        AttachmentStore.readBytes(context, att.data, maxBytes)
    } else {
        val approx = estimateDecodedBase64Size(att.data)
        if (approx > maxBytes) return null
        try {
            Base64.Mime.decode(att.data)
        } catch (t: Throwable) {
            null
        }
    }
}

private fun estimateDecodedBase64Size(base64: String): Long {
    if (base64.isEmpty()) return 0
    val padding = when {
        base64.endsWith("==") -> 2
        base64.endsWith("=") -> 1
        else -> 0
    }
    return (base64.length.toLong() * 3 / 4) - padding
}

private object DocumentText {

    const val MAX_PREVIEW_CHARS = 120_000

    data class Result(val text: String, val truncated: Boolean)

    private val PLAIN_TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "mdown", "mkd", "rst", "log", "csv", "tsv", "json", "jsonc",
        "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties", "env", "tex", "bib",
        "srt", "vtt", "diff", "patch", "gitignore", "editorconfig",
        "kt", "kts", "java", "scala", "groovy", "gradle", "py", "pyi", "rb", "php", "pl", "lua",
        "r", "jl", "go", "rs", "swift", "m", "mm", "c", "h", "cc", "cpp", "cxx", "hpp", "hh",
        "cs", "fs", "vb", "dart", "js", "mjs", "cjs", "jsx", "ts", "tsx", "vue", "svelte",
        "html", "htm", "css", "scss", "sass", "less", "sql", "sh", "bash", "zsh", "fish",
        "bat", "cmd", "ps1", "psm1", "makefile", "mk", "cmake", "dockerfile", "proto", "graphql",
        "gql", "asm", "s", "v", "vhd", "sv", "clj", "ex", "exs", "erl", "hs", "ml", "nim", "zig"
    )

    private val OOXML_EXTENSIONS = setOf("docx", "docm", "pptx", "pptm", "xlsx", "xlsm")

    fun canExtract(att: Attachment): Boolean {
        if (att.isImage || att.isVideo || att.isAudio || att.isPdf) return false
        val ext = extensionOf(att.name)
        return ext in PLAIN_TEXT_EXTENSIONS ||
            ext in OOXML_EXTENSIONS ||
            ext == "rtf" ||
            looksTextualByMime(att.mime)
    }

    fun extract(context: PlatformContext, att: Attachment): Result? {
        val ext = extensionOf(att.name)
        val bytes = readAttachmentBytes(context, att, maxBytes = 32L * 1024 * 1024) ?: return null
        val raw = when {
            ext == "docx" || ext == "docm" -> extractDocx(bytes)
            ext == "pptx" || ext == "pptm" -> extractPptx(bytes)
            ext == "xlsx" || ext == "xlsm" -> extractXlsx(bytes)
            ext == "rtf" -> stripRtf(decodeText(bytes) ?: return null)
            else -> decodeText(bytes)
        } ?: return null
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return Result("", truncated = false)
        return if (cleaned.length > MAX_PREVIEW_CHARS) {
            Result(cleaned.take(MAX_PREVIEW_CHARS), truncated = true)
        } else {
            Result(cleaned, truncated = false)
        }
    }


    private fun decodeText(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""
        val sample = bytes.take(8000)
        if (sample.any { it.toInt() == 0 }) return null
        return try {
            val start = if (bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
            ) 3 else 0
            String(bytes, start, bytes.size - start, Charsets.UTF_8)
        } catch (t: Throwable) {
            null
        }
    }

    private fun looksTextualByMime(mime: String): Boolean =
        mime.startsWith("text/") ||
            mime == "application/json" ||
            mime == "application/xml" ||
            mime == "application/javascript" ||
            mime.endsWith("+json") ||
            mime.endsWith("+xml")


    private fun zipEntries(bytes: ByteArray, wanted: (String) -> Boolean): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && wanted(entry.name)) {
                        out[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                }
            }
        } catch (t: Throwable) {
        }
        return out
    }

    private fun extractDocx(bytes: ByteArray): String? {
        val xml = zipEntries(bytes) { it == "word/document.xml" }["word/document.xml"]
            ?.toString(Charsets.UTF_8) ?: return null
        val token = Regex("""<w:tab\s*/>|<w:br\s*/>|<w:cr\s*/>|</w:p>|<w:t(?:\s[^>]*)?>(.*?)</w:t>""",
            RegexOption.DOT_MATCHES_ALL)
        val sb = StringBuilder()
        for (m in token.findAll(xml)) {
            when {
                m.groups[1] != null -> sb.append(unescapeXml(m.groupValues[1]))
                m.value.startsWith("<w:tab") -> sb.append('\t')
                else -> sb.append('\n')
            }
        }
        return sb.toString()
    }

    private fun extractPptx(bytes: ByteArray): String? {
        val slidePattern = Regex("""ppt/slides/slide(\d+)\.xml""")
        val slides = zipEntries(bytes) { slidePattern.matches(it) }
        if (slides.isEmpty()) return null
        val ordered = slides.entries.sortedBy {
            slidePattern.find(it.key)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        }
        val sb = StringBuilder()
        ordered.forEachIndexed { index, (_, data) ->
            val xml = data.toString(Charsets.UTF_8)
            if (index > 0) sb.append("\n\n")
            sb.append("— ").append(index + 1).append(" —\n")
            val token = Regex("""</a:p>|<a:t(?:\s[^>]*)?>(.*?)</a:t>""", RegexOption.DOT_MATCHES_ALL)
            for (m in token.findAll(xml)) {
                if (m.groups[1] != null) sb.append(unescapeXml(m.groupValues[1])) else sb.append('\n')
            }
        }
        return sb.toString()
    }

    private fun extractXlsx(bytes: ByteArray): String? {
        val wanted = zipEntries(bytes) {
            it == "xl/sharedStrings.xml" || Regex("""xl/worksheets/sheet\d+\.xml""").matches(it)
        }
        val sheetName = wanted.keys.filter { it.startsWith("xl/worksheets/") }.minOrNull() ?: return null
        val sheet = wanted[sheetName]?.toString(Charsets.UTF_8) ?: return null

        val shared = wanted["xl/sharedStrings.xml"]?.toString(Charsets.UTF_8)?.let { xml ->
            Regex("""<si\b.*?</si>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
                Regex("""<t(?:\s[^>]*)?>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(si.value)
                    .joinToString("") { unescapeXml(it.groupValues[1]) }
            }.toList()
        } ?: emptyList()

        val sb = StringBuilder()
        for (row in Regex("""<row\b.*?</row>""", RegexOption.DOT_MATCHES_ALL).findAll(sheet)) {
            val cells = Regex("""<c\b([^>]*)(?:/>|>(.*?)</c>)""", RegexOption.DOT_MATCHES_ALL)
                .findAll(row.value)
                .map { cell ->
                    val attrs = cell.groupValues[1]
                    val body = cell.groupValues[2]
                    when {
                        attrs.contains("t=\"s\"") -> {
                            val idx = Regex("""<v>(\d+)</v>""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                            idx?.let { shared.getOrNull(it) } ?: ""
                        }
                        attrs.contains("t=\"inlineStr\"") ->
                            Regex("""<t(?:\s[^>]*)?>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
                                .findAll(body).joinToString("") { unescapeXml(it.groupValues[1]) }
                        else -> Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
                            .find(body)?.groupValues?.get(1)?.let { unescapeXml(it) } ?: ""
                    }
                }
                .toList()
            if (cells.any { it.isNotBlank() }) sb.append(cells.joinToString("\t")).append('\n')
        }
        return sb.toString()
    }


    private fun stripRtf(rtf: String): String = rtf
        .replace(Regex("""\{\\\*.*?}""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""\\par[d]?\b"""), "\n")
        .replace(Regex("""\\line\b"""), "\n")
        .replace(Regex("""\\tab\b"""), "\t")
        .replace(Regex("""\\'[0-9a-fA-F]{2}"""), "")
        .replace(Regex("""\\[a-zA-Z]+-?\d*\s?"""), "")
        .replace(Regex("""[{}]"""), "")


    private fun unescapeXml(s: String): String {
        if ('&' !in s) return s
        return s.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("""&#x([0-9a-fA-F]+);""")) { m ->
                m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            }
            .replace(Regex("""&#(\d+);""")) { m ->
                m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            }
            .replace("&amp;", "&")
    }

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()
}

private enum class EditTool { DOODLE, MOSAIC, CROP }

@Composable
private fun ImageEditorDialog(att: Attachment, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = desktopPlatformContext
    var working by remember(att.data) { mutableStateOf<BufferedImage?>(null) }
    var failed by remember(att.data) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(EditTool.DOODLE) }

    val undoStack = remember(att.data) { mutableStateListOf<BufferedImage>() }
    val canUndo = undoStack.isNotEmpty()

    LaunchedEffect(att.data) {
        val bmp = withContext(Dispatchers.IO) {
            val bytes = readAttachmentBytes(context, att, maxBytes = 96L * 1024 * 1024) ?: return@withContext null
            val decoded = try {
                javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
            } catch (t: Throwable) {
                null
            } ?: return@withContext null
            toArgb(decoded)
        }
        if (bmp != null) working = bmp else failed = true
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f))) {
            val bmp = working
            when {
                bmp != null -> {
                    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
                    var livePath by remember { mutableStateOf<List<Offset>>(emptyList()) }
                    var cropRect by remember(version) { mutableStateOf<Rect?>(null) }
                    val image = remember(version) { bmp.toBitmap() }

                    fun pushUndo(source: BufferedImage) {
                        val copy = try { deepCopy(source) } catch (t: Throwable) { null }
                        if (copy != null) {
                            undoStack.add(copy)
                            trimUndoStack(undoStack)
                        }
                    }

                    fun undo() {
                        if (undoStack.isEmpty()) return
                        val previous = undoStack.removeAt(undoStack.size - 1)
                        working = previous
                        livePath = emptyList()
                        cropRect = null
                        version++
                    }

                    fun toBitmapSpace(p: Offset): Offset {
                        if (canvasSize.width == 0 || canvasSize.height == 0) return p
                        val sx = bmp.width.toFloat() / canvasSize.width
                        val sy = bmp.height.toFloat() / canvasSize.height
                        return Offset(p.x * sx, p.y * sy)
                    }

                    Column(modifier = Modifier.fillMaxSize().padding(top = 48.dp, bottom = 96.dp)) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f).wrapContentHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                            ) {
                                Image(bitmap = image, contentDescription = att.name, modifier = Modifier.fillMaxSize())
                                ComposeCanvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(tool, bmp) {
                                            detectDragGestures(
                                                onDragStart = { pos ->
                                                    livePath = listOf(pos)
                                                    if (tool == EditTool.CROP) cropRect = Rect(pos, pos)
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    livePath = livePath + change.position
                                                    if (tool == EditTool.CROP && livePath.isNotEmpty()) {
                                                        cropRect = Rect(livePath.first(), change.position)
                                                    }
                                                },
                                                onDragEnd = {
                                                    when (tool) {
                                                        EditTool.DOODLE -> {
                                                            if (livePath.size > 1) pushUndo(bmp)
                                                            drawDoodle(bmp, livePath.map { toBitmapSpace(it) })
                                                            version++
                                                        }
                                                        EditTool.MOSAIC -> {
                                                            if (livePath.isNotEmpty()) pushUndo(bmp)
                                                            drawMosaic(bmp, livePath.map { toBitmapSpace(it) })
                                                            version++
                                                        }
                                                        EditTool.CROP -> {  }
                                                    }
                                                    livePath = emptyList()
                                                }
                                            )
                                        }
                                        .onSizeChanged { canvasSize = it }
                                ) {
                                    if (livePath.size > 1 && tool != EditTool.CROP) {
                                        val color = if (tool == EditTool.DOODLE) Color(0xFFFF3B30) else Color.White.copy(alpha = 0.5f)
                                        val width = if (tool == EditTool.DOODLE) DOODLE_STROKE else MOSAIC_BLOCK.toFloat()
                                        val sx = if (canvasSize.width == 0) 1f else canvasSize.width.toFloat() / bmp.width
                                        for (i in 1 until livePath.size) {
                                            drawLine(
                                                color = color,
                                                start = livePath[i - 1],
                                                end = livePath[i],
                                                strokeWidth = width * sx
                                            )
                                        }
                                    }
                                    cropRect?.let { r ->
                                        drawRect(
                                            color = Color.White,
                                            topLeft = Offset(minOf(r.left, r.right), minOf(r.top, r.bottom)),
                                            size = androidx.compose.ui.geometry.Size(kotlin.math.abs(r.width), kotlin.math.abs(r.height)),
                                            style = Stroke(width = 3f)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolChip(com.lucent.app.i18n.S.toolDraw, Icons.Default.Brush, tool == EditTool.DOODLE) { tool = EditTool.DOODLE }
                            ToolChip(com.lucent.app.i18n.S.toolMosaic, Icons.Default.GridOn, tool == EditTool.MOSAIC) { tool = EditTool.MOSAIC }
                            ToolChip(com.lucent.app.i18n.S.toolCrop, Icons.Default.Crop, tool == EditTool.CROP) { tool = EditTool.CROP }
                            if (tool == EditTool.CROP) {
                                Text(
                                    com.lucent.app.i18n.S.applyCrop,
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(Color.White)
                                        .clickable {
                                            val r = cropRect
                                            if (r != null) {
                                                val cropped = applyCrop(
                                                    bmp,
                                                    toBitmapSpace(Offset(minOf(r.left, r.right), minOf(r.top, r.bottom))),
                                                    toBitmapSpace(Offset(maxOf(r.left, r.right), maxOf(r.top, r.bottom)))
                                                )
                                                if (cropped != null) {
                                                    undoStack.add(bmp)
                                                    trimUndoStack(undoStack)
                                                    working = cropped
                                                    cropRect = null
                                                    version++
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = com.lucent.app.i18n.S.actionCancel,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp).clickable { onDismiss() }
                        )
                        Text(com.lucent.app.i18n.S.editImageTitle, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = if (canUndo) com.lucent.app.i18n.S.a11yUndoLastEdit else com.lucent.app.i18n.S.a11yNothingToUndo,
                            tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.30f),
                            modifier = Modifier.size(26.dp).clickable(enabled = canUndo) { undo() }
                        )
                        Spacer(Modifier.size(14.dp))
                        Text(
                            if (saving) com.lucent.app.i18n.S.savingEllipsis else com.lucent.app.i18n.S.actionSave,
                            color = Color.Black,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(Color.White)
                                .clickable(enabled = !saving) {
                                    saving = true
                                    val toSave = working
                                    AppScope.io.launch {
                                        val ok = toSave != null && saveEdited(context, att, toSave)
                                        withContext(Dispatchers.Main) {
                                            saving = false
                                            if (ok) onSaved() else onDismiss()
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
                failed -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(com.lucent.app.i18n.S.imageOpenFailed, color = Color.White)
                }
                else -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ToolChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Color.Black else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, color = if (selected) Color.Black else Color.White, fontSize = 13.sp)
    }
}


private fun toArgb(src: BufferedImage): BufferedImage {
    val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.drawImage(src, 0, 0, null)
    g.dispose()
    return out
}

private fun deepCopy(src: BufferedImage): BufferedImage {
    val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.drawImage(src, 0, 0, null)
    g.dispose()
    return out
}

private fun BufferedImage.toBitmap(): ImageBitmap {
    val baos = ByteArrayOutputStream()
    javax.imageio.ImageIO.write(this, "png", baos)
    return org.jetbrains.skia.Image.makeFromEncoded(baos.toByteArray())
        .toComposeImageBitmap()
}

private fun drawDoodle(bmp: BufferedImage, points: List<Offset>) {
    if (points.size < 2) return
    val g = bmp.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = java.awt.Color(255, 59, 48)
    g.stroke = BasicStroke(DOODLE_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    for (i in 1 until points.size) {
        g.drawLine(points[i - 1].x.toInt(), points[i - 1].y.toInt(), points[i].x.toInt(), points[i].y.toInt())
    }
    g.dispose()
}

private fun drawMosaic(bmp: BufferedImage, points: List<Offset>) {
    if (points.isEmpty()) return
    val block = MOSAIC_BLOCK
    val done = HashSet<Long>()
    val g = bmp.createGraphics()
    for (p in points) {
        val cellX = (p.x.toInt() / block) * block
        val cellY = (p.y.toInt() / block) * block
        for (dx in -1..1) for (dy in -1..1) {
            val bx = cellX + dx * block
            val by = cellY + dy * block
            if (bx < 0 || by < 0 || bx >= bmp.width || by >= bmp.height) continue
            val key = bx.toLong() * 100000L + by
            if (!done.add(key)) continue
            val w = minOf(block, bmp.width - bx)
            val h = minOf(block, bmp.height - by)
            if (w <= 0 || h <= 0) continue
            g.color = java.awt.Color(averageColor(bmp, bx, by, w, h), true)
            g.fillRect(bx, by, w, h)
        }
    }
    g.dispose()
}

private fun averageColor(bmp: BufferedImage, x: Int, y: Int, w: Int, h: Int): Int {
    var r = 0L; var green = 0L; var b = 0L; var count = 0L
    val stepX = maxOf(1, w / 6)
    val stepY = maxOf(1, h / 6)
    var yy = y
    while (yy < y + h) {
        var xx = x
        while (xx < x + w) {
            val c = bmp.getRGB(xx, yy)
            r += (c shr 16) and 0xFF
            green += (c shr 8) and 0xFF
            b += c and 0xFF
            count++
            xx += stepX
        }
        yy += stepY
    }
    if (count == 0L) return 0xFF000000.toInt()
    val rr = (r / count).toInt()
    val gg = (green / count).toInt()
    val bb = (b / count).toInt()
    return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
}

private fun applyCrop(bmp: BufferedImage, topLeft: Offset, bottomRight: Offset): BufferedImage? {
    val left = topLeft.x.toInt().coerceIn(0, bmp.width - 1)
    val top = topLeft.y.toInt().coerceIn(0, bmp.height - 1)
    val right = bottomRight.x.toInt().coerceIn(left + 1, bmp.width)
    val bottom = bottomRight.y.toInt().coerceIn(top + 1, bmp.height)
    val w = right - left
    val h = bottom - top
    if (w < 8 || h < 8) return null
    return try {
        val sub = bmp.getSubimage(left, top, w, h)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(sub, 0, 0, null)
        g.dispose()
        out
    } catch (t: Throwable) {
        null
    }
}

private fun saveEdited(context: PlatformContext, att: Attachment, bmp: BufferedImage): Boolean {
    return try {
        if (!AttachmentStore.looksLikeId(att.data)) return false
        val useJpeg = att.mime.equals("image/jpeg", true) || att.mime.equals("image/jpg", true)
        val bytes = if (useJpeg) encodeJpeg(bmp, 0.92f) else encodePng(bmp)
        AttachmentStore.writeBytes(context, att.data, bytes)
    } catch (t: Throwable) {
        false
    }
}

private fun encodePng(bmp: BufferedImage): ByteArray {
    val out = ByteArrayOutputStream()
    javax.imageio.ImageIO.write(bmp, "png", out)
    return out.toByteArray()
}

private fun encodeJpeg(bmp: BufferedImage, quality: Float): ByteArray {
    val rgb = BufferedImage(bmp.width, bmp.height, BufferedImage.TYPE_INT_RGB)
    val g = rgb.createGraphics()
    g.color = java.awt.Color.WHITE
    g.fillRect(0, 0, bmp.width, bmp.height)
    g.drawImage(bmp, 0, 0, null)
    g.dispose()

    val writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next()
    val param = writer.defaultWriteParam.apply {
        compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
        compressionQuality = quality
    }
    val out = ByteArrayOutputStream()
    javax.imageio.ImageIO.createImageOutputStream(out).use { ios ->
        writer.output = ios
        writer.write(null, javax.imageio.IIOImage(rgb, null, null), param)
    }
    writer.dispose()
    return out.toByteArray()
}

private fun trimUndoStack(stack: MutableList<BufferedImage>) {
    fun bytesOf(b: BufferedImage): Long = b.width.toLong() * b.height.toLong() * 4L
    while (stack.size > UNDO_MAX_STEPS) stack.removeAt(0)
    var total = stack.sumOf { bytesOf(it) }
    while (stack.size > 1 && total > UNDO_MAX_BYTES) {
        total -= bytesOf(stack.removeAt(0))
    }
}

private const val UNDO_MAX_STEPS = 12
private const val UNDO_MAX_BYTES = 64L * 1024 * 1024

private const val DOODLE_STROKE = 12f
private const val MOSAIC_BLOCK = 28

@Composable
private fun rememberSaveAttachmentLauncher(): (Attachment) -> Unit {
    val context = desktopPlatformContext
    return remember {
        { att ->
            Thread {
                try {
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, com.lucent.app.i18n.S.actionSave, java.awt.FileDialog.SAVE)
                    dialog.file = att.name.ifBlank { "attachment" }
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val name = dialog.file
                    if (dir != null && name != null) {
                        val ok = AttachmentAccess.writeTo(context, att, com.lucent.app.PlatformOutputStream(File(dir, name).outputStream()))
                        LucentToast.show(context, if (ok) com.lucent.app.i18n.S.savedToast else com.lucent.app.i18n.S.cantSaveFile)
                    }
                } catch (t: Throwable) {
                    LucentToast.show(context, com.lucent.app.i18n.S.cantSaveFile)
                }
            }.start()
        }
    }
}
