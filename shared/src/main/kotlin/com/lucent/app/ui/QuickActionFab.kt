package com.lucent.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lucent.app.data.RichSpan
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class TextUndoStack(initial: String = "") {

    private val entries = ArrayDeque<String>().apply { addLast(initial) }
    private var cursor = 0
    private var lastRecordedAt = 0L

    val canUndo: Boolean get() = cursor > 0
    val canRedo: Boolean get() = cursor < entries.size - 1

    fun current(): String = entries[cursor]

    fun record(value: String, now: Long = System.currentTimeMillis()) {
        if (value == entries[cursor]) return

        while (entries.size > cursor + 1) entries.removeLast()

        val coalesce = now - lastRecordedAt < COALESCE_WINDOW_MS &&
            kotlin.math.abs(value.length - entries[cursor].length) <= COALESCE_MAX_DELTA &&
            cursor > 0
        if (coalesce) {
            entries[cursor] = value
        } else {
            entries.addLast(value)
            cursor = entries.size - 1
            while (entries.size > MAX_DEPTH) {
                entries.removeFirst()
                cursor--
            }
        }
        lastRecordedAt = now
    }

    fun undo(): String? {
        if (!canUndo) return null
        cursor--
        lastRecordedAt = 0L
        return entries[cursor]
    }

    fun redo(): String? {
        if (!canRedo) return null
        cursor++
        lastRecordedAt = 0L
        return entries[cursor]
    }

    private companion object {
        const val MAX_DEPTH = 100
        const val COALESCE_WINDOW_MS = 900L
        const val COALESCE_MAX_DELTA = 3
    }
}

@Composable
fun QuickActionFab(
    scrollingUp: Boolean,
    scrollingDown: Boolean,
    expanded: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onScrollTop: () -> Unit,
    onScrollBottom: () -> Unit,
    onToggleExpanded: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
    richTextEnabled: Boolean = false,
    hasSelection: Boolean = false,
    onToggleStyle: (RichSpan.Kind, Int) -> Unit = { _, _ -> },
    onClearStyle: () -> Unit = {},
    onNeedSelection: () -> Unit = {},
    activeKinds: Set<RichSpan.Kind> = emptySet(),
    activeHighlight: Int? = null,
    activeColor: Int? = null,
    activeSize: Int? = null
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val reveal by animateFloatAsState(targetValue = if (expanded) 1f else 0f, label = "quickActionRing")

    var formatPage by remember { mutableStateOf(false) }
    var highlightOpen by remember { mutableStateOf(false) }
    var colorOpen by remember { mutableStateOf(false) }
    var sizeOpen by remember { mutableStateOf(false) }
    if (!expanded && (formatPage || highlightOpen || colorOpen || sizeOpen)) {
        formatPage = false
        highlightOpen = false
        colorOpen = false
        sizeOpen = false
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {

        if (reveal > 0.01f && (formatPage || highlightOpen || colorOpen || sizeOpen)) {
            FormatPanel(
                highlightOpen = highlightOpen,
                colorOpen = colorOpen,
                sizeOpen = sizeOpen,
                activeColor = activeColor,
                activeSize = activeSize,
                onOpenColors = { colorOpen = true },
                onCloseColors = { colorOpen = false },
                onOpenSizes = { sizeOpen = true },
                onCloseSizes = { sizeOpen = false },
                canUndo = canUndo,
                canRedo = canRedo,
                activeKinds = activeKinds,
                activeHighlight = activeHighlight,
                onUndo = onUndo,
                onRedo = onRedo,
                onToggleStyle = onToggleStyle,
                onClearStyle = onClearStyle,
                onOpenHighlights = { highlightOpen = true },
                onCloseHighlights = { highlightOpen = false }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Box(modifier = Modifier.size(RING_DIAMETER), contentAlignment = Alignment.BottomEnd) {
            if (reveal > 0.01f && !formatPage && !highlightOpen && !colorOpen && !sizeOpen) {
                RingAction(Icons.AutoMirrored.Filled.Undo, com.lucent.app.i18n.S.actionUndo, RING_ANGLES_3[0], reveal, canUndo, onUndo)
                RingAction(Icons.AutoMirrored.Filled.Redo, com.lucent.app.i18n.S.actionRedo, RING_ANGLES_3[1], reveal, canRedo, onRedo)
                if (richTextEnabled) {
                    RingAction(
                        Icons.Default.TextFormat,
                        com.lucent.app.i18n.S.a11yRichTextToolbar,
                        RING_ANGLES_3[2], reveal, true
                    ) { formatPage = true }
                }
            }

        val icon: ImageVector = when {
            expanded -> Icons.Default.Check
            scrollingUp -> Icons.Default.KeyboardArrowUp
            scrollingDown -> Icons.Default.KeyboardArrowDown
            else -> Icons.Default.Build
        }
        val label = when {
            expanded -> com.lucent.app.i18n.S.actionSave
            scrollingUp -> com.lucent.app.i18n.S.a11yScrollTop
            scrollingDown -> com.lucent.app.i18n.S.a11yScrollBottom
            else -> com.lucent.app.i18n.S.a11yQuickEdit
        }
            Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(expanded, scrollingUp, scrollingDown) {
                    detectTapGestures(onTap = {
                        Haptics.tick(context)
                        when {
                            highlightOpen -> highlightOpen = false
                            colorOpen -> colorOpen = false
                            sizeOpen -> sizeOpen = false
                            formatPage -> formatPage = false
                            expanded -> onToggleExpanded()
                            scrollingUp -> onScrollTop()
                            scrollingDown -> onScrollBottom()
                            else -> onToggleExpanded()
                        }
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = onGradient, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun RingAction(
    icon: ImageVector,
    label: String,
    angleDegrees: Float,
    reveal: Float,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val radians = Math.toRadians(angleDegrees.toDouble())
    val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { RING_RADIUS.toPx() }
    val dx = (cos(radians) * radiusPx * reveal).roundToInt()
    val dy = (sin(radians) * radiusPx * reveal).roundToInt()

    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(dx, dy) }
            .size(40.dp)
            .graphicsLayer { alpha = reveal * (if (enabled) 1f else 0.35f) }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.30f))
            .pointerInput(enabled) {
                detectTapGestures(onTap = { if (enabled) { Haptics.tick(context); onClick() } })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = onGradient, modifier = Modifier.size(20.dp))
    }
}


val RichHighlightColors: List<Color> = com.lucent.app.data.RichText.HIGHLIGHT_ARGB.map { Color(it) }

@Composable
fun richTextColors(): List<Color> {
    val onGradient = LocalOnGradient.current
    return remember(onGradient) {
        com.lucent.app.data.RichText.TEXT_COLOR_ARGB.mapIndexed { index, argb ->
            if (index == com.lucent.app.data.RichText.TEXT_COLOR_DEFAULT) onGradient else Color(argb)
        }
    }
}

private val RING_ANGLES_5 = listOf(180f, 202.5f, 225f, 247.5f, 270f)

private val RING_ANGLES_3 = listOf(180f, 225f, 270f)

private val RING_DIAMETER = 160.dp
private val RING_RADIUS = 72.dp

@Composable
private fun FormatPanel(
    highlightOpen: Boolean,
    colorOpen: Boolean,
    sizeOpen: Boolean,
    activeColor: Int?,
    activeSize: Int?,
    onOpenColors: () -> Unit,
    onCloseColors: () -> Unit,
    onOpenSizes: () -> Unit,
    onCloseSizes: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    activeKinds: Set<RichSpan.Kind>,
    activeHighlight: Int?,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleStyle: (RichSpan.Kind, Int) -> Unit,
    onClearStyle: () -> Unit,
    onOpenHighlights: () -> Unit,
    onCloseHighlights: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End
    ) {
        if (colorOpen) {
            val swatches = richTextColors()
            (0..swatches.size).chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { index ->
                        if (index < swatches.size) {
                            PanelCell(
                                label = com.lucent.app.i18n.S.richTextColor,
                                enabled = true,
                                active = activeColor == index,
                                onClick = { onToggleStyle(RichSpan.Kind.COLOR, index); onCloseColors() }
                            ) {
                                Box(
                                    modifier = Modifier.size(22.dp).clip(CircleShape).background(swatches[index])
                                )
                            }
                        } else {
                            PanelCell(
                                label = com.lucent.app.i18n.S.actionBack,
                                enabled = true,
                                active = false,
                                onClick = onCloseColors
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            return@Column
        }

        if (sizeOpen) {
            val labels = listOf(
                com.lucent.app.i18n.S.richTextSizeDefault,
                com.lucent.app.i18n.S.richTextSizeSmall,
                com.lucent.app.i18n.S.richTextSizeMedium,
                com.lucent.app.i18n.S.richTextSizeLarge,
                com.lucent.app.i18n.S.richTextSizeHuge
            )
            (0..com.lucent.app.data.RichText.TEXT_SIZES).chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { index ->
                        if (index < com.lucent.app.data.RichText.TEXT_SIZES) {
                            PanelCell(
                                label = com.lucent.app.i18n.S.richTextSize,
                                enabled = true,
                                active = activeSize == index,
                                onClick = { onToggleStyle(RichSpan.Kind.SIZE, index); onCloseSizes() }
                            ) {
                                Text(
                                    labels[index.coerceIn(0, labels.lastIndex)],
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
                            }
                        } else {
                            PanelCell(
                                label = com.lucent.app.i18n.S.actionBack,
                                enabled = true,
                                active = false,
                                onClick = onCloseSizes
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            return@Column
        }

        if (highlightOpen) {
            (0..RichHighlightColors.size).chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { index ->
                        if (index < RichHighlightColors.size) {
                            PanelCell(
                                label = com.lucent.app.i18n.S.richTextHighlight,
                                enabled = true,
                                active = activeHighlight == index,
                                onClick = { onToggleStyle(RichSpan.Kind.HIGHLIGHT, index); onCloseHighlights() }
                            ) {
                                Box(
                                    modifier = Modifier.size(22.dp).clip(CircleShape).background(RichHighlightColors[index])
                                )
                            }
                        } else {
                            PanelCell(
                                label = com.lucent.app.i18n.S.actionBack,
                                enabled = true,
                                active = false,
                                onClick = onCloseHighlights
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            return@Column
        }

        val cells: List<PanelItem> = listOf(
            PanelItem(Icons.Default.FormatBold, com.lucent.app.i18n.S.richTextBold, true,
                RichSpan.Kind.BOLD in activeKinds) { onToggleStyle(RichSpan.Kind.BOLD, 0) },
            PanelItem(Icons.Default.FormatItalic, com.lucent.app.i18n.S.richTextItalic, true,
                RichSpan.Kind.ITALIC in activeKinds) { onToggleStyle(RichSpan.Kind.ITALIC, 0) },
            PanelItem(Icons.Default.FormatSize, com.lucent.app.i18n.S.richTextLight, true,
                RichSpan.Kind.LIGHT in activeKinds) { onToggleStyle(RichSpan.Kind.LIGHT, 0) },
            PanelItem(Icons.Default.Brush, com.lucent.app.i18n.S.richTextHighlight, true,
                activeHighlight != null) { onOpenHighlights() },
            PanelItem(Icons.Default.TextFields, com.lucent.app.i18n.S.richTextSize, true,
                activeSize != null) { onOpenSizes() },
            PanelItem(Icons.AutoMirrored.Filled.Undo, com.lucent.app.i18n.S.actionUndo, canUndo, false) { onUndo() },
            PanelItem(Icons.AutoMirrored.Filled.Redo, com.lucent.app.i18n.S.actionRedo, canRedo, false) { onRedo() },
            PanelItem(Icons.Default.FormatClear, com.lucent.app.i18n.S.richTextClear, true, false) { onClearStyle() },
            PanelItem(Icons.Default.FormatColorText, com.lucent.app.i18n.S.richTextColor, true,
                activeColor != null && activeColor != com.lucent.app.data.RichText.TEXT_COLOR_DEFAULT) { onOpenColors() }
        )
        cells.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { item ->
                    PanelCell(item.label, item.enabled, item.active, item.onClick) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private data class PanelItem(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val active: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun PanelCell(
    label: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color.White.copy(alpha = 0.26f) else Color.Transparent)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .pointerInput(enabled, active) {
                detectTapGestures(onTap = { if (enabled) { Haptics.tick(context); onClick() } })
            }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) { content() }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 9.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
