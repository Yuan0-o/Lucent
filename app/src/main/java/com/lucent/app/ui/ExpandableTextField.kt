package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import com.lucent.app.data.RichSpan
import com.lucent.app.data.RichText
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * A multi-line text field with an expand toggle in its bottom-right corner. Collapsed, it behaves
 * like an ordinary [OutlinedTextField] bounded by [collapsedMinHeight]/[collapsedMaxHeight].
 * Tapping the expand icon opens a modal editor that fills **almost the entire screen** (edge to
 * edge inside the status/navigation bars) so long notes are comfortable to read and edit.
 *
 * The expanded editor is rendered in its own window (a [Dialog]) rather than inline, which keeps
 * it from squeezing or reflowing the rest of the composer (tags, attachments, the save button …):
 * those stay exactly where they were while the editor floats above them. The dialog window is made
 * transparent with its dim removed, so the app's live animated background still shows through and
 * the panel keeps the app's frosted-glass look. A [Dialog] (instead of the plain popup used
 * before) is what makes this robust: it is a normal focusable window that receives the IME insets
 * and redraws reliably, which fixes the occasional blank/half-drawn panel that could appear after
 * a lot of text had been typed and the editor was then expanded.
 *
 * Both the Notes and the Tasks composer use this one component, so their expanded editors are
 * pixel-for-pixel the same size.
 */
@Composable
fun ExpandableGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    expandedTitle: String,
    modifier: Modifier = Modifier,
    collapsedMinHeight: Dp = 120.dp,
    collapsedMaxHeight: Dp = 320.dp,
    // ---- INTEGRATION: C-group task 20 ----
    // All optional, all inert by default, so every existing call site is untouched and a user who
    // never turns rich text on gets byte-identical behaviour to before.
    spans: List<RichSpan> = emptyList(),
    onSelectionChange: (Int, Int) -> Unit = { _, _ -> },
    highlightColors: List<Color> = emptyList(),
    // PHASE 4: an optional action rendered in the field's top-right corner (the expand toggle owns
    // the bottom-right). Used for the dictation mic; default null keeps every existing call site
    // byte-compatible.
    extraAction: (@Composable () -> Unit)? = null,
) {
    val onGradientMuted = LocalOnGradientMuted.current
    var expanded by remember { mutableStateOf(false) }

    // The field still owns a plain String; this only carries the caret/selection so the formatting
    // buttons know what to act on. Re-synced from [value] whenever the text changes underneath us
    // (an undo, the assistant, a version restore) so the selection can never point past the end.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    if (fieldValue.text != value) {
        fieldValue = fieldValue.copy(
            text = value,
            selection = TextRange(fieldValue.selection.start.coerceIn(0, value.length),
                                  fieldValue.selection.end.coerceIn(0, value.length))
        )
    }
    val transformation = remember(spans, highlightColors) {
        if (spans.isEmpty() || highlightColors.isEmpty()) VisualTransformation.None
        else RichSpanTransformation(spans, highlightColors)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { updated ->
                fieldValue = updated
                onSelectionChange(updated.selection.min, updated.selection.max)
                if (updated.text != value) onValueChange(updated.text)
            },
            visualTransformation = transformation,
            placeholder = { Text(placeholder) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = collapsedMinHeight, max = collapsedMaxHeight)
        )
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(32.dp)
        ) {
            Icon(
                Icons.Default.OpenInFull,
                contentDescription = com.lucent.app.i18n.S.expandTextBox,
                tint = onGradientMuted,
                modifier = Modifier.size(18.dp)
            )
        }
        if (extraAction != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) { extraAction() }
        }
    }

    if (expanded) {
        ExpandedEditor(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            title = expandedTitle,
            onCollapse = { expanded = false },
            spans = spans,
            onSelectionChange = onSelectionChange,
            highlightColors = highlightColors
        )
    }
}

/**
 * The near-full-screen editor window. It is a [Dialog] whose own window has been made transparent
 * (and its dim removed) so the app's animated background still shows through our own scrim, exactly
 * like before — but as a real focusable window it receives the IME insets and redraws reliably, so
 * the panel no longer occasionally comes up blank/half-drawn after a long note.
 *
 * The content is a single column, inset only by the system bars (and the keyboard, via
 * [imePadding]), with the glass editor panel taking all the remaining height. That fills almost the
 * whole screen and guarantees the field is never hidden behind the IME. A slim margin around the
 * panel is a tap target that dismisses; the collapse button and the back gesture dismiss too.
 */
@Composable
private fun ExpandedEditor(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    title: String,
    onCollapse: () -> Unit,
    spans: List<RichSpan> = emptyList(),
    onSelectionChange: (Int, Int) -> Unit = { _, _ -> },
    highlightColors: List<Color> = emptyList(),
) {
    // Same treatment as the collapsed field — see the comments there. The expanded editor is where
    // long-form writing actually happens, so it would be the wrong one to leave unstyled.
    var expandedField by remember { mutableStateOf(TextFieldValue(value)) }
    if (expandedField.text != value) {
        expandedField = expandedField.copy(
            text = value,
            selection = TextRange(expandedField.selection.start.coerceIn(0, value.length),
                                  expandedField.selection.end.coerceIn(0, value.length))
        )
    }
    val expandedTransformation = remember(spans, highlightColors) {
        if (spans.isEmpty() || highlightColors.isEmpty()) VisualTransformation.None
        else RichSpanTransformation(spans, highlightColors)
    }
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    // A shared, indication-free interaction source so the scrim/panel tap targets add no ripple.
    val noRipple = remember { MutableInteractionSource() }

    // usePlatformDefaultWidth = false lets the content decide the size, so fillMaxSize makes the
    // dialog span the whole screen. decorFitsSystemWindows = false makes it draw edge to edge (and
    // dispatch the status-bar/nav-bar/IME insets to the composition, which imePadding/
    // systemBarsPadding below then consume). dismissOnClickOutside is off because the content fills
    // the screen; dismissOnBackPress stays on so a back gesture collapses the editor first.
    Dialog(
        onDismissRequest = onCollapse,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Strip the dialog's platform dim and give it a transparent background so the app's live
        // animated background shows through behind our own scrim — keeping the frosted, see-through
        // look while using a robust, IME-aware window.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.apply {
                setDimAmount(0f)
                setBackgroundDrawableResource(android.R.color.transparent)
            }
        }

        // A darker full-screen scrim (edge to edge) so the panel stands out clearly from the busy
        // animated background behind it. Tapping the scrim (the slim area around the panel)
        // collapses the editor.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = noRipple, indication = null) { onCollapse() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .padding(10.dp)
            ) {
                // ---- The editor panel: fills essentially the whole screen ----
                // An opaque surface sits UNDER the glass so the content has a solid, high-contrast
                // backing (the moving background doesn't bleed through and wash out the text): the
                // surface is derived from the inverse of the text colour (a dark panel under light
                // text, a light panel under dark text), keeping the theme-aware look while staying
                // readable in every palette.
                val panelSurface = panelSurfaceColor(onGradient)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(panelSurface)
                        .frostedGlass()
                        // Swallow taps so pressing inside the panel never dismisses.
                        .clickable(interactionSource = noRipple, indication = null) {}
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(title, color = onGradient, fontSize = 18.sp)
                        IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.CloseFullscreen,
                                contentDescription = com.lucent.app.i18n.S.collapseTextBox,
                                tint = onGradientMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedTextField(
                        value = expandedField,
                        onValueChange = { updated ->
                            expandedField = updated
                            onSelectionChange(updated.selection.min, updated.selection.max)
                            if (updated.text != value) onValueChange(updated.text)
                        },
                        visualTransformation = expandedTransformation,
                        placeholder = { Text(placeholder, color = onGradientMuted) },
                        textStyle = LocalTextStyle.current.copy(color = onGradient),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = onGradient,
                            unfocusedTextColor = onGradient,
                            cursorColor = onGradient,
                            focusedBorderColor = onGradient.copy(alpha = 0.5f),
                            unfocusedBorderColor = onGradient.copy(alpha = 0.3f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Picks an opaque backing colour for the expanded editor panel based on the current on-gradient
 * text colour. When the text is light (drawn on dark palettes) we return a near-opaque dark panel;
 * when the text is dark we return a near-opaque light panel. Either way the note/task content sits
 * on a solid, high-contrast surface instead of showing the moving background through, while the
 * thin frosted-glass sheen layered on top keeps it consistent with the rest of the app.
 */
private fun panelSurfaceColor(onGradient: Color): Color =
    if (onGradient.luminance() > 0.5f) {
        // Light text -> dark surface.
        Color(0xFF20202B).copy(alpha = 0.92f)
    } else {
        // Dark text -> light surface.
        Color(0xFFF4F4F8).copy(alpha = 0.92f)
    }

/**
 * INTEGRATION (C-group task 20) — turn a sidecar span list into Compose styling.
 *
 * Kept as a [VisualTransformation] rather than by swapping the field for a rich editor: the field
 * keeps holding a plain [String], every existing caller keeps working, and the styling is applied
 * at draw time only. Offsets are unchanged (nothing is inserted or hidden), so the mapping is the
 * identity — which is what makes the cursor, selection handles and IME all behave exactly as they
 * did before.
 */
private class RichSpanTransformation(
    private val spans: List<RichSpan>,
    private val highlightColors: List<Color>
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (spans.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text.text)
            // Clamped against the text actually in the field: the spans come from the database and
            // the field may already have been edited this frame. A stale range would either throw
            // or, worse, style the wrong words.
            RichText.reconcile(spans, text.text.length).forEach { s ->
                val style = when (s.kind) {
                    RichSpan.Kind.LIGHT -> SpanStyle(fontWeight = FontWeight.Light)
                    RichSpan.Kind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    RichSpan.Kind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    RichSpan.Kind.HIGHLIGHT -> SpanStyle(
                        background = highlightColors[s.color.coerceIn(0, highlightColors.lastIndex)]
                            .copy(alpha = 0.45f)
                    )
                }
                addStyle(style, s.start, s.end)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
