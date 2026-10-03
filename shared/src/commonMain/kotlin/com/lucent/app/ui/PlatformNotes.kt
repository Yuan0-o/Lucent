package com.lucent.app.ui

import androidx.compose.runtime.Composable
import com.lucent.app.data.Attachment
import com.lucent.app.platform.PlatformContext

expect typealias PlatformPickedFile

@Composable
expect fun rememberAttachmentFilePicker(onPicked: (List<PlatformPickedFile>) -> Unit): () -> Unit

expect fun attachmentSizeHint(context: PlatformContext, source: PlatformPickedFile): Long

expect fun pickedFileToAttachment(context: PlatformContext, source: PlatformPickedFile): Attachment?

expect fun templateToastContext(context: PlatformContext): PlatformContext

@Composable
expect fun OnAppHidden(action: () -> Unit)

expect fun shareText(context: PlatformContext, subject: String?, text: String, chooserTitle: String)

expect val notesGridColumns: Int

@Composable
expect fun rememberNotificationPermissionRequester(): () -> Unit

@Composable
expect fun rememberDateTimePicker(minMillis: Long, initialMillis: Long, onChange: (Long) -> Unit): () -> Unit
