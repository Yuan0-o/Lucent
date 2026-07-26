package com.lucent.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.lucent.app.data.Attachment
import com.lucent.app.data.AttachmentAccess
import com.lucent.app.data.Attachments
import com.lucent.app.data.DocumentText
import com.lucent.app.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Everything a user can do with a saved attachment beyond seeing it on the card: open it full-screen
 * inside Lucent (images and video), hand it to another app to view (PDFs, documents, anything),
 * **download** a decrypted copy to a location they pick, or share it out.
 *
 * All of it runs against the encrypted store through [AttachmentAccess], which produces the
 * decrypted, shareable copy on demand and keeps the originals sealed. Nothing here ever writes
 * plaintext anywhere the user didn't choose (a download) or anywhere another app can reach except
 * the one file, the one app, for the one action (a preview or a share).
 */

// ---------------------------------------------------------------------------------------
// Actions (open externally / share). Download is a Composable launcher, see below.
// ---------------------------------------------------------------------------------------

private fun toast(context: Context, msg: String) =
    LucentToast.show(context.applicationContext, msg)

/** Hand the attachment to whatever app the user picks to *view* it (ACTION_VIEW). */
fun openAttachmentExternally(context: Context, att: Attachment) {
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

/**
 * Task A25, replacement scope.
 *
 * The original ask — "after sending to another app, offer a way back to Lucent" — is not something
 * Lucent can deliver. `ACTION_SEND` hands control to the *target app's* task, and whether Back
 * returns here depends on how that app declared its launch mode. No flag, notification or chooser
 * extra makes that reliable across WeChat, Gmail, a gallery and a file manager.
 *
 * What is reliable, and what this does instead, is **confirming the send once the user is back**:
 * the file name is armed when the share sheet opens and reported the next time a Lucent screen
 * resumes. No permission, no notification, no manifest change, and nothing that alters what
 * sharing means.
 *
 * ### The one heuristic, stated plainly
 *
 * Lucent is paused by the chooser itself, so "we resumed" cannot by itself distinguish *sent* from
 * *dismissed the chooser*. [MIN_AWAY_MS] is the discriminator: choosing a target and coming back
 * involves at least another app drawing a screen, which does not happen in under a second, whereas
 * backing out of the chooser returns immediately. A misfire costs one wrong toast, so the trade is
 * a cheap one — but it is a heuristic and not a guarantee, which is why it is written down here.
 */
private object ShareReturnNotice {

    private const val MIN_AWAY_MS = 1200L

    /** Set while a share is in flight; the name is what the toast reports. */
    private var pendingName: String? = null
    private var armedAt = 0L
    private var callbacks: android.app.Application.ActivityLifecycleCallbacks? = null

    fun arm(context: Context, name: String) {
        val app = context.applicationContext as? android.app.Application ?: return
        pendingName = name
        armedAt = System.currentTimeMillis()
        if (callbacks != null) return
        val cb = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                val name = pendingName ?: return
                if (System.currentTimeMillis() - armedAt < MIN_AWAY_MS) {
                    // Straight back = the chooser was dismissed. Disarm silently; claiming a file
                    // was sent when it wasn't is worse than saying nothing.
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

    /** Unregister as soon as the one resume we care about has happened — no permanent listener. */
    private fun detach(app: android.app.Application) {
        callbacks?.let { app.unregisterActivityLifecycleCallbacks(it) }
        callbacks = null
    }
}

/** Share the attachment out through the system share sheet (ACTION_SEND, with the real bytes). */
fun shareAttachment(context: Context, att: Attachment) {
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
                // Task A25 (replacement): report the send when the user comes back.
                ShareReturnNotice.arm(context, att.name)
            } catch (t: Throwable) {
                toast(context, com.lucent.app.i18n.S.cantShareFile)
            }
        }
    }
}

/**
 * A remembered "Save to device" launcher. Call the returned lambda with an attachment to open the
 * system's Save-file dialog for it; the decrypted bytes are then streamed into the location the user
 * chose. Using the Storage Access Framework means the plaintext copy lands only where the user put
 * it (Downloads, Drive, wherever) — Lucent never writes it anywhere else.
 */
@Composable
fun rememberSaveAttachmentLauncher(): (Attachment) -> Unit {
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
                    AttachmentAccess.writeTo(context, att, out)
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
        // Suggest the real filename; the picker lets the user change it and pick a folder.
        launcher.launch(att.name.ifBlank { "attachment" })
    }
}

// ---------------------------------------------------------------------------------------
// Full-screen viewer
// ---------------------------------------------------------------------------------------

/**
 * A full-screen preview of one attachment, over a dark scrim, with a Save / Share / Open-with action
 * row across the bottom. Images are pinch-to-zoom and pan; video and audio play with standard
 * transport controls; anything not inline-viewable shows its details and leans on the action row to
 * open it elsewhere. [onDismiss] closes it.
 */
@Composable
fun AttachmentViewerDialog(att: Attachment, onDismiss: () -> Unit) =
    AttachmentViewerDialog(listOf(att), 0, onDismiss)

/**
 * The full-screen attachment viewer. When a note or task has several attachments they can be swiped
 * between with a horizontal pager (task E3); the chrome — title, page counter, and the
 * Save/Share/Edit/Open-with row — always reflects whichever attachment is currently on screen.
 */
@Composable
fun AttachmentViewerDialog(attachments: List<Attachment>, initialIndex: Int, onDismiss: () -> Unit) {
    if (attachments.isEmpty()) return
    val context = LocalContext.current
    val save = rememberSaveAttachmentLauncher()
    var editing by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var chromeVisible by remember { mutableStateOf(true) }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, attachments.size - 1)
    ) { attachments.size }
    val current = pagerState.currentPage.coerceIn(0, attachments.size - 1)
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
                // One page per attachment; swiping moves between them. A zoomed-in image or a playing
                // video consumes its own drags, so the pager only takes over the plain left/right swipe.
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val a = attachments[page]
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            a.isImage -> ZoomableImage(a, if (page == current) reloadKey else 0)
                            a.isVideo || a.isAudio -> InlineMediaPlayer(
                                att = a,
                                chromeVisible = chromeVisible,
                                onToggleChrome = { chromeVisible = !chromeVisible }
                            )
                            a.isPdf -> PdfViewer(a)
                            // Task A1: Markdown, Word, PowerPoint, Excel, RTF and every code
                            // format now preview as text instead of falling through to
                            // "no preview available". See [DocumentText].
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

/** Pinch-to-zoom, double-tap-to-reset image view. Decodes from the encrypted store off the main thread. */
@Composable
private fun ZoomableImage(att: Attachment, reloadKey: Int = 0) {
    val context = LocalContext.current
    var bitmap by remember(att.data, reloadKey) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(att.data, reloadKey) { mutableStateOf(false) }

    LaunchedEffect(att.data, reloadKey) {
        val bmp = withContext(Dispatchers.IO) {
            // A larger cap than the card thumbnail: this is a full-screen view, so allow more detail.
            val bytes = Attachments.readBytes(context, att, maxBytes = 64L * 1024 * 1024)
            if (bytes != null) decodeSampledBitmap(bytes, maxDim = 2560) else null
        }
        if (bmp != null) bitmap = bmp else failed = true
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    /**
     * Task A3. How far the picture may be dragged before its own edge would leave the screen:
     * half of whatever the scaled image overflows the viewport by, per axis. At fit scale the
     * overflow is zero, so the limit is zero and the image simply cannot be moved.
     *
     * The drawn size is derived the way ContentScale.Fit derives it (the smaller of the two
     * ratios), not from the viewport — clamping against the viewport would allow a landscape photo
     * to be dragged up and down through its own letterboxing, which is precisely the "the picture
     * wandered off" complaint.
     */
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
                // Task A3, the other half: while the image is at fit scale there is nothing to pan,
                // so panning must not be *claimed* either — canPan lets the drag fall through to the
                // pager and swiping to the next attachment works again. Zoomed in, the gesture is
                // the image's and stays clamped by panLimits().
                .transformable(state = transformState, canPan = { scale > 1f })
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        // Snap between fit and 2x, recentring on reset.
                        if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2f
                    })
                }
        )
        failed -> Text(com.lucent.app.i18n.S.cantLoadImage, color = Color.White)
        else -> CircularProgressIndicator(color = Color.White)
    }
}

/**
 * A real media player for video **and** audio attachments (task 1).
 *
 * ### What was wrong with the old one
 *
 * It handed a [VideoView] an `android.widget.MediaController` and hoped. `MediaController` is a
 * `PopupWindow` that anchors itself to a View, and this player lives inside a Compose `Dialog` —
 * i.e. its own window. A popup anchored into another window is exactly the case that arrangement
 * handles worst: the transport bar either never appeared or appeared somewhere unhelpful, which is
 * why there was no scrub bar to drag. Audio was worse still — a `VideoView` playing an audio file
 * draws nothing at all, so the "player" was a black rectangle with no controls, no position, and no
 * way to seek.
 *
 * ### What this does instead
 *
 * The transport is drawn in Compose, in the same window as everything else, so it simply cannot fail
 * to appear:
 *
 *  - **A real scrub bar.** A [Slider] bound to the player's position, draggable to seek. While the
 *    thumb is held the bar follows the finger rather than the playhead (otherwise the poll below
 *    fights the drag and the thumb jitters), and the seek is issued once, on release.
 *  - **Tap to hide.** One tap anywhere on the surface hides the controls *and* the viewer's own
 *    chrome for full-screen playback; another brings them back. While playing they also fade
 *    themselves out after a few seconds, the way every video player does.
 *  - **Position, duration, play/pause, replay**, and the playback-speed selector the old one had.
 *  - **Audio gets the same treatment**, with a music glyph and the file name standing in for the
 *    (empty) video surface, so an audio attachment is now a playable track rather than a void.
 *
 * [VideoView] is still the engine — it handles every format a phone records or downloads without a
 * media library dependency, and anything exotic still has "Open with" as a fallback.
 */
@Composable
private fun InlineMediaPlayer(
    att: Attachment,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit
) {
    val context = LocalContext.current
    var uri by remember(att.data) { mutableStateOf<Uri?>(null) }
    var failed by remember(att.data) { mutableStateOf(false) }
    // The prepared player, captured so the speed row can drive it after playback begins.
    var player by remember(att.data) { mutableStateOf<MediaPlayer?>(null) }
    // The View itself, so play/pause/seek can be issued without going through MediaPlayer directly
    // (VideoView owns its lifecycle and gets upset if you drive its player behind its back).
    var view by remember(att.data) { mutableStateOf<VideoView?>(null) }
    var speed by remember(att.data) { mutableStateOf(1f) }

    var durationMs by remember(att.data) { mutableIntStateOf(0) }
    var positionMs by remember(att.data) { mutableIntStateOf(0) }
    var playing by remember(att.data) { mutableStateOf(false) }
    var completed by remember(att.data) { mutableStateOf(false) }
    // While the user is dragging the scrub bar, this holds the finger's position and the poll below
    // stops writing to it — without this the two take turns setting the value and the thumb jumps
    // back and forth under the finger.
    var scrubbing by remember(att.data) { mutableStateOf(false) }
    var scrubMs by remember(att.data) { mutableStateOf(0f) }

    // ---- Task A23 gesture state ------------------------------------------------------------
    // True while a finger is held down and playback is temporarily doubled.
    var boosting by remember(att.data) { mutableStateOf(false) }
    // A long press ends in a pointer-up, which the tap detector would otherwise read as a tap and
    // use to toggle the chrome. This swallows exactly that one tap.
    var swallowNextTap by remember(att.data) { mutableStateOf(false) }
    // Transient readout for the brightness / volume slides: label to 0..1, null when idle.
    var slideReadout by remember(att.data) { mutableStateOf<Pair<String, Float>?>(null) }

    // Brightness belongs to the *dialog's* window, not the Activity's: the viewer is a Compose
    // Dialog, so it has a window of its own and dimming the Activity behind it would change
    // nothing the user can see. Volume is a device-wide stream and goes through AudioManager.
    val androidView = LocalView.current
    val dialogWindow = remember(androidView) { (androidView.parent as? DialogWindowProvider)?.window }
    val audio = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    }
    val maxVolume = remember(audio) {
        (audio?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 1).coerceAtLeast(1)
    }

    LaunchedEffect(att.data) {
        val u = withContext(Dispatchers.IO) { AttachmentAccess.contentUri(context, att) }
        if (u != null) uri = u else failed = true
    }

    // Poll the playhead. 200ms is imperceptible on a progress bar and costs nothing; a listener
    // would be nicer but MediaPlayer doesn't offer one for position.
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

    // Auto-hide while playing, the way a video player should. Any tap resets the timer by flipping
    // chromeVisible, which re-runs this effect.
    LaunchedEffect(chromeVisible, playing) {
        if (chromeVisible && playing) {
            delay(3500)
            if (playing) onToggleChrome()
        }
    }

    // Leaving the viewer must stop playback; a dialog that closes while still playing audio is a
    // genuinely alarming thing to have happen.
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
            // Some codecs refuse a rate change; playback simply keeps its current speed.
        }
    }

    /**
     * Task A23. Screen brightness is a window attribute in 0..1, with -1 meaning "follow the
     * system". The floor is deliberately just above zero: a video player that can be dragged to a
     * genuinely black screen leaves the user with no visible way back to the slider that did it.
     */
    fun applyBrightness(fraction: Float) {
        val w = dialogWindow ?: return
        try {
            w.attributes = w.attributes.apply { screenBrightness = fraction.coerceIn(0.02f, 1f) }
        } catch (_: Throwable) {
        }
    }

    fun currentBrightness(): Float {
        val v = dialogWindow?.attributes?.screenBrightness ?: -1f
        // -1 means "inherit"; there is no way to read the inherited value, so start from the middle
        // rather than guessing — the first drag then moves relative to something predictable.
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
                // Restarting after the end: rewind first, otherwise start() resumes at the end and
                // instantly completes again.
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
                            // No MediaController: the transport is Compose, below.
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

                // Audio has no picture, so it gets one: a glyph and the file name, which also keeps
                // the tap target meaningful instead of an invisible black rectangle.
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

                // ---- Task A23: the gesture surface -------------------------------------
                //
                // Four gestures share one surface, split across separate pointerInput nodes so
                // each detector keeps its own state machine instead of one hand-rolled block
                // trying to be all four at once:
                //
                //   single tap    show / hide the chrome (unchanged)
                //   double tap    play / pause — the thing you want most often, without hunting
                //                 for a button that may have faded out
                //   press & hold  2x while held, back to the previous rate on release
                //   vertical drag brightness on the left half, volume on the right half
                //
                // Only the drag detector consumes events. The hold detector deliberately does
                // not — it observes the same pointer the tap detector is watching, which is what
                // lets a quick tap still register as a tap while a long one becomes a boost.
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
                                // Still down when the timeout expires => a hold, not a tap.
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
                                    // A full-height swipe covers the full range, and up means more,
                                    // which is why the sign is flipped (y grows downward).
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

                // Task A23: the replay button. Playback used to end on a still frame with no
                // control on it — you had to tap to summon the chrome, then find the transport,
                // then press play. This is the one action anybody wants at the end of a clip, so
                // it sits where the eye already is, and it deliberately does NOT toggle the
                // chrome: it is a button in its own right, above the gesture surface, so its tap
                // never reaches the detector underneath.
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

                // Transient readouts for hold-to-boost and the brightness / volume slides. Both
                // sit above the gesture surface but are non-interactive, so they never intercept
                // the gesture that is currently producing them.
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
                                // Seeking back from the end re-arms play.
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

/** Milliseconds as m:ss (or h:mm:ss past an hour) for the transport's position/duration readout. */
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

/**
 * The in-app text preview for documents and code (task A1).
 *
 * Extraction happens off the main thread through [DocumentText] and the result is shown
 * monospaced, because most of what lands here — Markdown, JSON, source files, a spreadsheet dumped
 * as tab-separated rows — is written expecting a fixed pitch, and a proportional font turns the
 * alignment those formats rely on into noise.
 *
 * The header line is not decoration. A Word file previewed here has lost its layout, its images
 * and its styling, and a preview that quietly implies otherwise is worse than none: it invites the
 * user to believe they have checked the document when they have only read its words. Saying so
 * costs one line and keeps "Open with" an obviously useful button rather than a redundant one.
 */
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

/**
 * Task A23. How long a finger must stay down before it means "run at 2x" rather than "tap".
 * Android's own long-press threshold is 500ms; a shade under that feels responsive on a video
 * surface without firing on the way to an ordinary tap.
 */
private const val HOLD_TO_BOOST_MS = 400L
