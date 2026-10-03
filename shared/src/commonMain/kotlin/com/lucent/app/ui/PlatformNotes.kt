package com.lucent.app.ui

import androidx.compose.runtime.Composable
import com.lucent.app.data.Attachment
import com.lucent.app.platform.PlatformContext

expect class PlatformPickedFile

@Composable
expect fun rememberAttachmentFilePicker(onPicked: (List<PlatformPickedFile>) -> Unit): () -> Unit

internal expect fun attachmentSizeHint(context: PlatformContext, source: PlatformPickedFile): Long

internal expect fun pickedFileToAttachment(context: PlatformContext, source: PlatformPickedFile): Attachment?

internal expect fun templateToastContext(context: PlatformContext): PlatformContext

@Composable
expect fun OnAppHidden(action: () -> Unit)

expect fun shareText(context: PlatformContext, subject: String? = null, text: String, chooserTitle: String)

internal expect val notesGridColumns: Int

@Composable
expect fun rememberNotificationPermissionRequester(): () -> Unit

@Composable
expect fun rememberDateTimePicker(minMillis: Long, initialMillis: Long, onChange: (Long) -> Unit): () -> Unit
