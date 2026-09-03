package com.lucent.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.i18n.S

/**
 * The shared two-level fold for the note and task composers (v2.7 UI reorganization).
 *
 * ### What it is
 *
 * A slim always-visible row ("More options" + chevron) that expands a glass card of secondary
 * controls directly beneath it — the same chevron-fold idiom the detail pages already use for
 * their action strips (hide behind a chevron, reveal in place), applied to the composers so the
 * first screenful of a new note/task is just what the item *is*: title, body, attachments, save.
 * Everything that *configures* the item (checklist/doodle/subtasks modes, pin, colour, tags,
 * priority, reminder/repeat) lives inside [content], one tap away.
 *
 * Deliberately NOT a dropdown or bottom sheet: this app's established fold is an in-place
 * expand/collapse (see the detail-page strips and Quillpad's expanding "Notebooks" drawer
 * section), it composes identically on Android and desktop, and it keeps the glass identity —
 * the revealed card uses [frostedGlass], the same surface as every other card in the app.
 *
 * The expanded state is owned by the caller (rememberSaveable at the call site) so the composer
 * keeps it across rotation within a session while a fresh composer always starts collapsed.
 */
@Composable
fun MoreOptionsFold(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = S.composerMoreOptions,
    content: @Composable ColumnScope.() -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(onGradient.copy(alpha = 0.06f))
                .clickable {
                    Haptics.tick(context)
                    onToggle()
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = onGradientMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = onGradient, fontSize = 14.sp)
            Box(modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = onGradientMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .frostedGlass()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                content()
            }
        }
    }
}
