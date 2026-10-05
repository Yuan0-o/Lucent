package com.lucent.app.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.Attachment
import com.lucent.app.data.RichSpan
import com.lucent.app.platform.PlatformContext

@Composable
expect fun rememberAttachmentFilePicker(onPicked: (List<Any>) -> Unit): () -> Unit

expect fun attachmentSizeHint(context: PlatformContext, source: Any): Long

expect fun pickedFileToAttachment(context: PlatformContext, source: Any): Attachment?

expect fun templateToastContext(context: PlatformContext): PlatformContext

@Composable
expect fun OnAppHidden(action: () -> Unit)

expect fun shareText(context: PlatformContext, subject: String?, text: String, chooserTitle: String)

expect val notesGridColumns: Int

@Composable
expect fun rememberNotificationPermissionRequester(): () -> Unit

@Composable
expect fun rememberDateTimePicker(minMillis: Long, initialMillis: Long, onChange: (Long) -> Unit): () -> Unit

@Composable
expect fun AttachmentViewerDialog(att: Attachment, onDismiss: () -> Unit)

@Composable
expect fun AttachmentViewerDialog(attachments: List<Attachment>, initialIndex: Int, onDismiss: () -> Unit)

@Composable
expect fun rememberSaveAttachmentLauncher(): (Attachment) -> Unit

@Composable
expect fun ExpandableGlassTextField(
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
)
