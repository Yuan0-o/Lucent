package com.lucent.app.ui
import com.lucent.app.platform.applicationContext

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.lucent.app.AppScope
import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentAccess
import com.lucent.app.data.AttachmentStore
import com.lucent.app.platform.PlatformContext
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private fun toast(context: PlatformContext, msg: String) =
    LucentToast.show(context, msg)

fun openAttachmentExternally(context: PlatformContext, att: Attachment) {
    AppScope.io.launch {
        val uri = AttachmentAccess.contentUri(context, att)
        withContext(Dispatchers.Main) {
            if (uri == null) {
                toast(context, com.lucent.app.i18n.S.cantOpenFile)
                return@withContext
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, att.mime.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(intent, com.lucent.app.i18n.S.openWith))
            } catch (t: Throwable) {
                toast(context, com.lucent.app.i18n.S.noAppCanOpen)
            }
        }
    }
}

private object ShareReturnNotice {

    private const val MIN_AWAY_MS = 1200L

    private var pendingName: String? = null
    private var armedAt = 0L
    private var callbacks: android.app.Application.ActivityLifecycleCallbacks? = null

    fun arm(context: PlatformContext, name: String) {
        val app = context.applicationContext as? android.app.Application ?: return
        pendingName = name
        armedAt = System.currentTimeMillis()
        if (callbacks != null) return
        val cb = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                val name = pendingName ?: return
                if (System.currentTimeMillis() - armedAt < MIN_AWAY_MS) {
                    pendingName = null
                    detach(activity.application)
                    return
                }
                pendingName = null
                detach(activity.application)
                LucentToast.show(activity.applicationContext, com.lucent.app.i18n.S.sharedToast(name))
            }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        }
        callbacks = cb
        app.registerActivityLifecycleCallbacks(cb)
    }

    private fun detach(app: android.app.Application) {
        callbacks?.let { app.unregisterActivityLifecycleCallbacks(it) }
        callbacks = null
    }
}

fun shareAttachment(context: PlatformContext, att: Attachment) {
    AppScope.io.launch {
        val uri = AttachmentAccess.contentUri(context, att)
        withContext(Dispatchers.Main) {
            if (uri == null) {
                toast(context, com.lucent.app.i18n.S.cantShareFile)
                return@withContext
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = att.mime.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(intent, com.lucent.app.i18n.S.shareFileChooser))
                ShareReturnNotice.arm(context, att.name)
            } catch (t: Throwable) {
                toast(context, com.lucent.app.i18n.S.cantShareFile)
            }
        }
    }
}

@Composable
actual fun rememberSaveAttachmentLauncher(): (Attachment) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Attachment?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val att = pending
        pending = null
        if (uri == null || att == null) return@rememberLauncherForActivityResult
        AppScope.io.launch {
            val ok = try {
                context.contentResolver.openOutputStream(uri)?.let { out ->
                    AttachmentAccess.writeTo(context, att, com.lucent.app.PlatformOutputStream(out))
                } ?: false
            } catch (t: Throwable) {
                false
            }
            withContext(Dispatchers.Main) {
                toast(context, if (ok) com.lucent.app.i18n.S.savedToast else com.lucent.app.i18n.S.cantSaveFile)
            }
        }
    }
    return { att ->
        pending = att
        launcher.launch(att.name.ifBlank { "attachment" })
    }
}


@Composable
actual fun AttachmentViewerDialog(att: Attachment, onDismiss: () -> Unit) =
    AttachmentViewerDialog(listOf(att), 0, onDismiss)

@Composable
actual fun AttachmentViewerDialog(attachments: List<Attachment>, initialIndex: Int, onDismiss: () -> Unit) {
    if (attachments.isEmpty()) return
    val context = LocalContext.current
    val save = rememberSaveAttachmentLauncher()
    var editing by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var chromeVisible by remember { mutableStateOf(true) }
    var currentImageZoomed by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, attachments.size - 1)
    ) { attachments.size }
    val current = pagerState.currentPage.coerceIn(0, attachments.size - 1)
    LaunchedEffect(current) { currentImageZoomed = false }
    val att = attachments[current]
    val isMedia = att.isVideo || att.isAudio

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        bottom = if (chromeVisible) 96.dp else 0.dp,
                        top = if (chromeVisible) 56.dp else 0.dp
                    ),
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
                                onZoomChanged = { zoomed ->
                                    if (page == current) currentImageZoomed = zoomed
                                }
                            )
                            a.isVideo || a.isAudio -> InlineMediaPlayer(
                                att = a,
                                chromeVisible = chromeVisible,
                                onToggleChrome = { chromeVisible = !chromeVisible }
                            )
                            a.isPdf -> PdfViewer(a)
                            DocumentText.canExtract(a) -> TextPreview(a)
                            else -> NonPreviewableInfo(a)
                        }
                    }
                }
            }

            if (!isMedia || chromeVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    att.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
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
            }

            if (!isMedia || chromeVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (att.isImage) {
                    ViewerAction(Icons.Default.Edit, com.lucent.app.i18n.S.actionEdit) { editing = true }
                }
                ViewerAction(Icons.Default.Download, com.lucent.app.i18n.S.actionSave) { save(att) }
                ViewerAction(Icons.Default.Share, com.lucent.app.i18n.S.actionShare) { shareAttachment(context, att) }
                ViewerAction(Icons.AutoMirrored.Filled.Launch, com.lucent.app.i18n.S.openWith) {
                    openAttachmentExternally(context, att)
                }
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
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { Haptics.tick(context); onClick() }) }
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
    val context = LocalContext.current
    var bitmap by remember(att.data, reloadKey) { mutableStateOf<Bitmap?>(null) }
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
        val drawnW = bmp.width * fit * scale
        val drawnH = bmp.height * fit * scale
        return ((drawnW - vw).coerceAtLeast(0f) / 2f) to ((drawnH - vh).coerceAtLeast(0f) / 2f)
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
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = att.name,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
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
private fun InlineMediaPlayer(
    att: Attachment,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit
) {
    val context = LocalContext.current
    var uri by remember(att.data) { mutableStateOf<Uri?>(null) }
    var failed by remember(att.data) { mutableStateOf(false) }
    var player by remember(att.data) { mutableStateOf<MediaPlayer?>(null) }
    var view by remember(att.data) { mutableStateOf<VideoView?>(null) }
    var speed by remember(att.data) { mutableStateOf(1f) }

    var durationMs by remember(att.data) { mutableIntStateOf(0) }
    var positionMs by remember(att.data) { mutableIntStateOf(0) }
    var playing by remember(att.data) { mutableStateOf(false) }
    var completed by remember(att.data) { mutableStateOf(false) }
    var scrubbing by remember(att.data) { mutableStateOf(false) }
    var scrubMs by remember(att.data) { mutableStateOf(0f) }

    var boosting by remember(att.data) { mutableStateOf(false) }
    var swallowNextTap by remember(att.data) { mutableStateOf(false) }
    var slideReadout by remember(att.data) { mutableStateOf<Pair<String, Float>?>(null) }

    val androidView = LocalView.current
    val dialogWindow = remember(androidView) { (androidView.parent as? DialogWindowProvider)?.window }
    val audio = remember(context) {
        context.getSystemService(PlatformContext.AUDIO_SERVICE) as? android.media.AudioManager
    }
    val maxVolume = remember(audio) {
        (audio?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 1).coerceAtLeast(1)
    }

    LaunchedEffect(att.data) {
        val u = withContext(Dispatchers.IO) { AttachmentAccess.contentUri(context, att) }
        if (u != null) uri = u else failed = true
    }

    LaunchedEffect(view, playing, scrubbing) {
        while (playing && !scrubbing) {
            val vv = view
            if (vv != null) {
                positionMs = vv.currentPosition
                if (durationMs <= 0 && vv.duration > 0) durationMs = vv.duration
            }
            delay(200)
        }
    }

    LaunchedEffect(chromeVisible, playing) {
        if (chromeVisible && playing) {
            delay(3500)
            if (playing) onToggleChrome()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { view?.stopPlayback() } catch (_: Throwable) {}
        }
    }

    fun applySpeed(newSpeed: Float) {
        speed = newSpeed
        val mp = player ?: return
        try {
            val wasPlaying = mp.isPlaying
            mp.playbackParams = mp.playbackParams.setSpeed(newSpeed)
            if (!wasPlaying) mp.pause()
        } catch (_: Throwable) {
        }
    }

    fun applyBrightness(fraction: Float) {
        val w = dialogWindow ?: return
        try {
            w.attributes = w.attributes.apply { screenBrightness = fraction.coerceIn(0.02f, 1f) }
        } catch (_: Throwable) {
        }
    }

    fun currentBrightness(): Float {
        val v = dialogWindow?.attributes?.screenBrightness ?: -1f
        return if (v < 0f) 0.5f else v
    }

    fun applyVolume(fraction: Float) {
        val am = audio ?: return
        try {
            am.setStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                (fraction.coerceIn(0f, 1f) * maxVolume).toInt(),
                0
            )
        } catch (_: Throwable) {
        }
    }

    fun currentVolume(): Float {
        val am = audio ?: return 0.5f
        return try {
            am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        } catch (_: Throwable) {
            0.5f
        }
    }

    fun replay() {
        val vv = view ?: return
        try {
            vv.seekTo(0)
            vv.start()
            playing = true
            completed = false
            positionMs = 0
        } catch (_: Throwable) {
        }
    }

    fun togglePlay() {
        val vv = view ?: return
        try {
            if (vv.isPlaying) {
                vv.pause()
                playing = false
            } else {
                if (completed) {
                    vv.seekTo(0)
                    completed = false
                }
                vv.start()
                playing = true
            }
        } catch (_: Throwable) {}
    }

    when {
        uri != null -> {
            val mediaUri = uri!!
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(mediaUri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = false
                                player = mp
                                durationMs = duration.coerceAtLeast(0)
                                try { mp.playbackParams = mp.playbackParams.setSpeed(speed) } catch (_: Throwable) {}
                                start()
                                playing = true
                            }
                            setOnCompletionListener {
                                playing = false
                                completed = true
                                positionMs = durationMs
                            }
                            setOnErrorListener { _, _, _ ->
                                failed = true
                                true
                            }
                            view = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (att.isAudio) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(att.name, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(att.data) {
                            detectTapGestures(
                                onTap = {
                                    if (swallowNextTap) swallowNextTap = false else onToggleChrome()
                                },
                                onDoubleTap = { togglePlay() }
                            )
                        }
                        .pointerInput(att.data) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val held = withTimeoutOrNull(HOLD_TO_BOOST_MS) {
                                    waitForUpOrCancellation()
                                } == null
                                if (held) {
                                    val previous = speed
                                    swallowNextTap = true
                                    boosting = true
                                    applySpeed(2f)
                                    waitForUpOrCancellation()
                                    boosting = false
                                    applySpeed(previous)
                                }
                            }
                        }
                        .pointerInput(att.data, maxVolume) {
                            var onLeftHalf = false
                            var value = 0f
                            detectVerticalDragGestures(
                                onDragStart = { pos ->
                                    onLeftHalf = pos.x < size.width / 2f
                                    value = if (onLeftHalf) currentBrightness() else currentVolume()
                                },
                                onDragEnd = { slideReadout = null },
                                onDragCancel = { slideReadout = null },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val height = size.height.toFloat().coerceAtLeast(1f)
                                    value = (value - dragAmount / height).coerceIn(0f, 1f)
                                    if (onLeftHalf) {
                                        applyBrightness(value)
                                        slideReadout = com.lucent.app.i18n.S.a11yBrightness to value
                                    } else {
                                        applyVolume(value)
                                        slideReadout = com.lucent.app.i18n.S.a11yVolume to value
                                    }
                                }
                            )
                        }
                )

                if (completed && !att.isAudio) {
                    IconButton(
                        onClick = { Haptics.tick(context); replay() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            Icons.Default.Replay,
                            contentDescription = com.lucent.app.i18n.S.a11yReplay,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                if (boosting) {
                    Text(
                        com.lucent.app.i18n.S.videoSpeedBoost,
                        color = Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 28.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                slideReadout?.let { (label, value) ->
                    Text(
                        "$label  ${(value * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }

                if (chromeVisible) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val total = durationMs.coerceAtLeast(0)
                        val shown = if (scrubbing) scrubMs.toInt() else positionMs
                        Slider(
                            value = if (scrubbing) scrubMs else positionMs.toFloat().coerceIn(0f, total.toFloat()),
                            onValueChange = {
                                scrubbing = true
                                scrubMs = it
                            },
                            onValueChangeFinished = {
                                try { view?.seekTo(scrubMs.toInt()) } catch (_: Throwable) {}
                                positionMs = scrubMs.toInt()
                                if (scrubMs.toInt() < total) completed = false
                                scrubbing = false
                            },
                            valueRange = 0f..(if (total > 0) total.toFloat() else 1f),
                            enabled = total > 0,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (completed) replay() else togglePlay() }) {
                                Icon(
                                    when {
                                        playing -> Icons.Default.Pause
                                        completed -> Icons.Default.Replay
                                        else -> Icons.Default.PlayArrow
                                    },
                                    contentDescription = if (playing) com.lucent.app.i18n.S.a11yPause else com.lucent.app.i18n.S.a11yPlay,
                                    tint = Color.White
                                )
                            }
                            Text(
                                "${formatClock(shown)} / ${formatClock(total)}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.weight(1f))
                            listOf(0.5f, 1f, 1.5f, 2f).forEach { option ->
                                val selected = speed == option
                                val label = when (option) {
                                    0.5f -> "0.5×"
                                    1f -> "1×"
                                    1.5f -> "1.5×"
                                    else -> "2×"
                                }
                                Text(
                                    text = label,
                                    color = if (selected) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.15f))
                                        .clickable { applySpeed(option) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        failed -> Text(com.lucent.app.i18n.S.cantLoadMedia, color = Color.White)
        else -> CircularProgressIndicator(color = Color.White)
    }
}

private fun formatClock(millis: Int): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun PdfViewer(att: Attachment) {
    val context = LocalContext.current
    var pages by remember(att.data) { mutableStateOf<List<Bitmap>?>(null) }
    var failed by remember(att.data) { mutableStateOf(false) }

    LaunchedEffect(att.data) {
        val rendered = withContext(Dispatchers.IO) {
            val file = AttachmentAccess.materialize(context, att) ?: return@withContext null
            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                descriptor = ParcelFileDescriptor.open(java.io.File(file.absolutePath), ParcelFileDescriptor.MODE_READ_ONLY)
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

@Composable
private fun NonPreviewableInfo(att: Attachment) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Launch,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(att.name, color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            com.lucent.app.i18n.S.noPreviewForType,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun TextPreview(att: Attachment) {
    val context = LocalContext.current
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
                Text(
                    com.lucent.app.i18n.S.previewTextOnly,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp
                )
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

private const val HOLD_TO_BOOST_MS = 400L

private const val MAX_IMAGE_DIM = 1600

private fun decodeSampledBitmap(bytes: ByteArray, maxDim: Int = MAX_IMAGE_DIM): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (t: Throwable) {
        null
    }
}

private fun readAttachmentBytes(context: PlatformContext, att: Attachment, maxBytes: Long = 32L * 1024 * 1024): ByteArray? {
    return if (AttachmentStore.looksLikeId(att.data)) {
        AttachmentStore.readBytes(context, att.data, maxBytes)
    } else {
        val approx = estimateDecodedBase64Size(att.data)
        if (approx > maxBytes) return null
        try {
            android.util.Base64.decode(att.data, android.util.Base64.DEFAULT)
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

private enum class AttachmentViewerEditTool { DOODLE, MOSAIC, CROP }

@Composable
private fun ImageEditorDialog(att: Attachment, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var working by remember(att.data) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(att.data) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(AttachmentViewerEditTool.DOODLE) }

    val undoStack = remember(att.data) { mutableStateListOf<Bitmap>() }
    val canUndo = undoStack.isNotEmpty()

    LaunchedEffect(att.data) {
        val bmp = withContext(Dispatchers.IO) {
            val bytes = readAttachmentBytes(context, att, maxBytes = 96L * 1024 * 1024) ?: return@withContext null
            val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            decoded.copy(Bitmap.Config.ARGB_8888, true)
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
                    val image = remember(version) { bmp.asImageBitmap() }

                    fun pushUndo(source: Bitmap) {
                        val copy = try {
                            source.copy(Bitmap.Config.ARGB_8888, true)
                        } catch (t: Throwable) {
                            null
                        }
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

                    fun toBitmap(p: Offset): Offset {
                        if (canvasSize.width == 0 || canvasSize.height == 0) return p
                        val sx = bmp.width.toFloat() / canvasSize.width
                        val sy = bmp.height.toFloat() / canvasSize.height
                        return Offset(p.x * sx, p.y * sy)
                    }

                    Column(modifier = Modifier.fillMaxSize().padding(top = 48.dp, bottom = 96.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .wrapContentHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                            ) {
                                Image(
                                    bitmap = image,
                                    contentDescription = att.name,
                                    modifier = Modifier.fillMaxSize()
                                )
                                ComposeCanvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(tool, bmp) {
                                            detectDragGestures(
                                                onDragStart = { pos ->
                                                    livePath = listOf(pos)
                                                    if (tool == AttachmentViewerEditTool.CROP) cropRect = Rect(pos, pos)
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    livePath = livePath + change.position
                                                    if (tool == AttachmentViewerEditTool.CROP && livePath.isNotEmpty()) {
                                                        cropRect = Rect(livePath.first(), change.position)
                                                    }
                                                },
                                                onDragEnd = {
                                                    when (tool) {
                                                        AttachmentViewerEditTool.DOODLE -> {
                                                            if (livePath.size > 1) pushUndo(bmp)
                                                            drawDoodle(bmp, livePath.map { toBitmap(it) })
                                                            version++
                                                        }
                                                        AttachmentViewerEditTool.MOSAIC -> {
                                                            if (livePath.isNotEmpty()) pushUndo(bmp)
                                                            drawMosaic(bmp, livePath.map { toBitmap(it) })
                                                            version++
                                                        }
                                                        AttachmentViewerEditTool.CROP -> {  }
                                                    }
                                                    livePath = emptyList()
                                                }
                                            )
                                        }
                                        .onSizeChangedCompat { canvasSize = it }
                                ) {
                                    if (livePath.size > 1 && tool != AttachmentViewerEditTool.CROP) {
                                        val color = if (tool == AttachmentViewerEditTool.DOODLE) Color(0xFFFF3B30) else Color.White.copy(alpha = 0.5f)
                                        val width = if (tool == AttachmentViewerEditTool.DOODLE) DOODLE_STROKE else MOSAIC_BLOCK.toFloat()
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
                            ToolChip(com.lucent.app.i18n.S.toolDraw, Icons.Default.Brush, tool == AttachmentViewerEditTool.DOODLE) { tool = AttachmentViewerEditTool.DOODLE }
                            ToolChip(com.lucent.app.i18n.S.toolMosaic, Icons.Default.GridOn, tool == AttachmentViewerEditTool.MOSAIC) { tool = AttachmentViewerEditTool.MOSAIC }
                            ToolChip(com.lucent.app.i18n.S.toolCrop, Icons.Default.Crop, tool == AttachmentViewerEditTool.CROP) { tool = AttachmentViewerEditTool.CROP }
                            if (tool == AttachmentViewerEditTool.CROP) {
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
                                                val cropped = applyCrop(bmp, toBitmap(Offset(minOf(r.left, r.right), minOf(r.top, r.bottom))), toBitmap(Offset(maxOf(r.left, r.right), maxOf(r.top, r.bottom))))
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
                            modifier = Modifier
                                .size(26.dp)
                                .clickable(enabled = canUndo) { undo() }
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
private fun ToolChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
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

private fun Modifier.onSizeChangedCompat(block: (IntSize) -> Unit): Modifier =
    this.then(Modifier.onSizeChanged(block))

private fun drawDoodle(bmp: Bitmap, points: List<Offset>) {
    if (points.size < 2) return
    val canvas = Canvas(bmp)
    val paint = Paint().apply {
        color = AndroidColor.rgb(255, 59, 48)
        strokeWidth = DOODLE_STROKE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    for (i in 1 until points.size) {
        canvas.drawLine(points[i - 1].x, points[i - 1].y, points[i].x, points[i].y, paint)
    }
}

private fun drawMosaic(bmp: Bitmap, points: List<Offset>) {
    if (points.isEmpty()) return
    val block = MOSAIC_BLOCK
    val done = HashSet<Long>()
    val paint = Paint()
    val canvas = Canvas(bmp)
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
            val avg = averageColor(bmp, bx, by, w, h)
            paint.color = avg
            canvas.drawRect(bx.toFloat(), by.toFloat(), (bx + w).toFloat(), (by + h).toFloat(), paint)
        }
    }
}

private fun averageColor(bmp: Bitmap, x: Int, y: Int, w: Int, h: Int): Int {
    var r = 0L; var g = 0L; var b = 0L; var count = 0L
    val stepX = maxOf(1, w / 6)
    val stepY = maxOf(1, h / 6)
    var yy = y
    while (yy < y + h) {
        var xx = x
        while (xx < x + w) {
            val c = bmp.getPixel(xx, yy)
            r += AndroidColor.red(c); g += AndroidColor.green(c); b += AndroidColor.blue(c); count++
            xx += stepX
        }
        yy += stepY
    }
    if (count == 0L) return AndroidColor.BLACK
    return AndroidColor.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
}

private fun applyCrop(bmp: Bitmap, topLeft: Offset, bottomRight: Offset): Bitmap? {
    val left = topLeft.x.toInt().coerceIn(0, bmp.width - 1)
    val top = topLeft.y.toInt().coerceIn(0, bmp.height - 1)
    val right = bottomRight.x.toInt().coerceIn(left + 1, bmp.width)
    val bottom = bottomRight.y.toInt().coerceIn(top + 1, bmp.height)
    val w = right - left
    val h = bottom - top
    if (w < 8 || h < 8) return null
    return try {
        Bitmap.createBitmap(bmp, left, top, w, h)
    } catch (t: Throwable) {
        null
    }
}

private fun saveEdited(context: PlatformContext, att: Attachment, bmp: Bitmap): Boolean {
    return try {
        val useJpeg = att.mime.equals("image/jpeg", true) || att.mime.equals("image/jpg", true)
        val out = java.io.ByteArrayOutputStream()
        if (useJpeg) bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        else bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        val bytes = out.toByteArray()
        if (AttachmentStore.looksLikeId(att.data)) {
            AttachmentStore.writeBytes(context, att.data, bytes)
        } else {
            false
        }
    } catch (t: Throwable) {
        false
    }
}

private fun trimUndoStack(stack: MutableList<Bitmap>) {
    fun bytesOf(b: Bitmap): Long = b.width.toLong() * b.height.toLong() * 4L
    while (stack.size > UNDO_MAX_STEPS) {
        val evicted = stack.removeAt(0)
        if (!evicted.isRecycled) evicted.recycle()
    }
    var total = stack.sumOf { bytesOf(it) }
    while (stack.size > 1 && total > UNDO_MAX_BYTES) {
        val evicted = stack.removeAt(0)
        total -= bytesOf(evicted)
        if (!evicted.isRecycled) evicted.recycle()
    }
}

private const val UNDO_MAX_STEPS = 12
private const val UNDO_MAX_BYTES = 64L * 1024 * 1024

private const val DOODLE_STROKE = 12f
private const val MOSAIC_BLOCK = 28
