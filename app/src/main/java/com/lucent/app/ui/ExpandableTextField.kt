package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
fun ExpandableGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    expandedTitle: String,
    modifier: Modifier = Modifier,
    collapsedMinHeight: Dp = 360.dp,
    spans: List<RichSpan> = emptyList(),
    onSelectionChange: (Int, Int) -> Unit = { _, _ -> },
    highlightColors: List<Color> = emptyList(),
    textColors: List<Color> = emptyList(),
    sizeScaleBase: TextUnit = 16.sp,
    extraAction: (@Composable () -> Unit)? = null,
    tools: (@Composable BoxScope.() -> Unit)? = null,
) {
    val onGradientMuted = LocalOnGradientMuted.current
    var expanded by remember { mutableStateOf(false) }
    val spanBase = LocalTextStyle.current.fontSize.let { if (it.isUnspecified) sizeScaleBase else it }

    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    if (fieldValue.text != value) {
        fieldValue = fieldValue.copy(
            text = value,
            selection = TextRange(fieldValue.selection.start.coerceIn(0, value.length),
                                  fieldValue.selection.end.coerceIn(0, value.length))
        )
    }
    val transformation = remember(spans, highlightColors, textColors, spanBase) {
        if (spans.isEmpty() || highlightColors.isEmpty()) VisualTransformation.None
        else RichSpanTransformation(spans, highlightColors, textColors, spanBase)
    }
    val lastLineRequester = remember { BringIntoViewRequester() }
    var followedLength by remember { mutableStateOf(value.length) }
    var followedSelection by remember { mutableStateOf(fieldValue.selection.max) }
    LaunchedEffect(fieldValue.text.length, fieldValue.selection.max) {
        val length = fieldValue.text.length
        val selectionChanged = fieldValue.selection.max != followedSelection
        val caretAtEnd = fieldValue.selection.max >= length
        val caretWasAtEnd = fieldValue.selection.max >= followedLength
        if ((length > followedLength && length > 0 && (caretAtEnd || caretWasAtEnd)) || selectionChanged) {
            withFrameNanos { }
            lastLineRequester.bringIntoView()
        }
        followedLength = length
        followedSelection = fieldValue.selection.max
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
                .heightIn(min = collapsedMinHeight)
        )
        Spacer(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(0.dp)
                .bringIntoViewRequester(lastLineRequester)
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
            highlightColors = highlightColors,
            textColors = textColors,
            sizeScaleBase = sizeScaleBase,
            tools = tools
        )
    }
}

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
    textColors: List<Color> = emptyList(),
    sizeScaleBase: TextUnit = 16.sp,
    tools: (@Composable BoxScope.() -> Unit)? = null,
) {
    var expandedField by remember { mutableStateOf(TextFieldValue(value)) }
    if (expandedField.text != value) {
        expandedField = expandedField.copy(
            text = value,
            selection = TextRange(expandedField.selection.start.coerceIn(0, value.length),
                                  expandedField.selection.end.coerceIn(0, value.length))
        )
    }
    val spanBase = LocalTextStyle.current.fontSize.let { if (it.isUnspecified) sizeScaleBase else it }
    val expandedTransformation = remember(spans, highlightColors, textColors, spanBase) {
        if (spans.isEmpty() || highlightColors.isEmpty()) VisualTransformation.None
        else RichSpanTransformation(spans, highlightColors, textColors, spanBase)
    }
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val panelSurface = panelSurfaceColor(onGradient)

    Dialog(
        onDismissRequest = onCollapse,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.apply {
                setDimAmount(0f)
                setBackgroundDrawableResource(android.R.color.transparent)
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(panelSurface)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCollapse, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = com.lucent.app.i18n.S.actionBack,
                            tint = onGradient,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(title, color = onGradient, fontSize = 18.sp, modifier = Modifier.weight(1f))
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
            if (tools != null) tools()
        }
    }
}

private fun panelSurfaceColor(onGradient: Color): Color =
    if (onGradient.luminance() > 0.5f) {
        Color(0xFF20202B).copy(alpha = 0.92f)
    } else {
        Color(0xFFF4F4F8).copy(alpha = 0.92f)
    }

private class RichSpanTransformation(
    private val spans: List<RichSpan>,
    private val highlightColors: List<Color>,
    private val textColors: List<Color>,
    private val sizeBase: TextUnit
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (spans.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text.text)
            RichText.reconcile(spans, text.text.length).forEach { s ->
                val style = when (s.kind) {
                    RichSpan.Kind.LIGHT -> SpanStyle(fontWeight = FontWeight.Light)
                    RichSpan.Kind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    RichSpan.Kind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    RichSpan.Kind.SIZE -> SpanStyle(fontSize = sizeBase * RichText.textSizeScale(s.color))
                    RichSpan.Kind.HIGHLIGHT -> SpanStyle(
                        background = highlightColors[s.color.coerceIn(0, highlightColors.lastIndex)]
                            .copy(alpha = 0.45f)
                    )
                    RichSpan.Kind.COLOR ->
                        if (textColors.isEmpty()) SpanStyle()
                        else SpanStyle(color = textColors[s.color.coerceIn(0, textColors.lastIndex)])
                }
                addStyle(style, s.start, s.end)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
