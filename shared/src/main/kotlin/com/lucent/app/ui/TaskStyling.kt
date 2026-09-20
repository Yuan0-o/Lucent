package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.PushPin as PushPinOutlined
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.RepeatRule
import com.lucent.app.data.TaskPriority
import com.lucent.app.i18n.S
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val PriorityHighColor = Color(0xFFE57373)
val PriorityMediumColor = Color(0xFFFFB74D)
val PriorityLowColor = Color(0xFF64B5F6)

private val ComposerRowIconSize = 20.dp
private val ComposerRowIconGap = 8.dp
private val ComposerRowLineHeight = 20.sp

val OverdueColor = PriorityHighColor

fun TaskPriority.color(): Color = when (this) {
    TaskPriority.NONE -> Color.Transparent
    TaskPriority.LOW -> PriorityLowColor
    TaskPriority.MEDIUM -> PriorityMediumColor
    TaskPriority.HIGH -> PriorityHighColor
}

@Composable
fun PriorityDot(priority: TaskPriority, modifier: Modifier = Modifier, size: Dp = 9.dp) {
    if (priority == TaskPriority.NONE) return
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(priority.color())
            .clearAndSetSemantics { }
    )
}

@Composable
fun PriorityBadge(priority: TaskPriority, modifier: Modifier = Modifier) {
    if (priority == TaskPriority.NONE) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Flag,
            contentDescription = null,
            tint = priority.color(),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(S.priorityBadge(priority.uiLabel), color = priority.color(), fontSize = 12.sp)
    }
}

@Composable
fun PriorityPickerRow(selected: TaskPriority, onSelect: (TaskPriority) -> Unit, modifier: Modifier = Modifier) {
    val onGradient = LocalOnGradient.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(S.labelPriority, color = onGradient, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskPriority.entries.forEach { option ->
                PriorityChip(
                    option = option,
                    selected = option == selected,
                    onSelect = { onSelect(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PriorityChip(
    option: TaskPriority,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(10.dp)
    val accent = if (option == TaskPriority.NONE) onGradient else option.color()
    val fill = if (selected) accent.copy(alpha = 0.20f) else Color.Transparent
    val rim = if (selected) accent.copy(alpha = 0.85f) else onGradientMuted.copy(alpha = 0.40f)

    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(shape)
            .background(fill)
            .border(if (selected) 1.5.dp else 1.dp, rim, shape)
            .clickable {
                Haptics.tick(context)
                onSelect()
            }
            .padding(horizontal = 3.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (option != TaskPriority.NONE) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                tint = option.color(),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(
            option.uiLabel,
            color = if (selected) onGradient else onGradientMuted,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RepeatRuleRow(selected: RepeatRule, onSelect: (RepeatRule) -> Unit, modifier: Modifier = Modifier) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Repeat, contentDescription = null, tint = onGradientMuted, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(S.labelRepeat, color = onGradient, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RepeatRule.entries.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option.uiLabel) }
                )
            }
        }
    }
}

@Composable
fun ComposerRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = onGradientMuted,
            modifier = Modifier.size(ComposerRowIconSize)
        )
        Spacer(modifier = Modifier.width(ComposerRowIconGap))
        Text(
            label,
            color = onGradient,
            fontSize = 14.sp,
            lineHeight = ComposerRowLineHeight,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
fun ReminderToggleRow(
    enabled: Boolean,
    hasDueDate: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val armed = enabled && hasDueDate
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (armed) Icons.Default.NotificationsActive else Icons.Default.Notifications,
            contentDescription = null,
            tint = if (armed) onGradient else onGradientMuted
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(S.remindAtDueTime, color = if (hasDueDate) onGradient else onGradientMuted, fontSize = 14.sp)
            if (!hasDueDate) {
                Text(S.setDueDateToEnable, color = onGradientMuted, fontSize = 12.sp)
            }
        }
        Switch(checked = armed, enabled = hasDueDate, onCheckedChange = onToggle)
    }
}

@Composable
fun PinIconButton(pinned: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current
    IconButton(
        onClick = {
            Haptics.tick(context)
            onToggle()
        },
        modifier = modifier
    ) {
        Icon(
            if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPinOutlined,
            contentDescription = if (pinned) S.unpin else S.pinToTop,
            tint = if (pinned) onGradient else onGradientMuted
        )
    }
}

@Composable
fun PinnedMarker(modifier: Modifier = Modifier, size: Dp = 16.dp, onUnpin: (() -> Unit)? = null) {
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current
    Icon(
        Icons.Filled.PushPin,
        contentDescription = if (onUnpin == null) S.pinned else S.actionUnpin,
        tint = onGradientMuted,
        modifier = modifier
            .size(size)
            .then(
                if (onUnpin == null) Modifier
                else Modifier.clickable {
                    Haptics.tick(context)
                    onUnpin()
                }
            )
    )
}


private val dueDayFormatter get() = com.lucent.app.i18n.LDates.of(S.patternMonthDay)
private val dueTimeFormatter get() = com.lucent.app.i18n.LDates.of(S.patternTime)

fun isOverdue(dueAt: Long?, isDone: Boolean): Boolean {
    if (dueAt == null || isDone) return false
    return dueAt < System.currentTimeMillis()
}

fun friendlyDue(dueAt: Long): String {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val zoned = Instant.ofEpochMilli(dueAt).atZone(zone)
    val date = zoned.toLocalDate()
    val time = zoned.format(dueTimeFormatter)
    return when {
        date.isEqual(today) -> S.dueTodayAt(time)
        date.isEqual(today.plusDays(1)) -> S.dueTomorrowAt(time)
        date.isEqual(today.minusDays(1)) -> S.dueYesterdayAt(time)
        date.isBefore(today) -> S.dueOverdueOn(zoned.format(dueDayFormatter))
        else -> S.dueOn(zoned.format(dueDayFormatter), time)
    }
}

val TaskPriority.uiLabel: String
    get() = when (this) {
        TaskPriority.NONE -> S.priorityNone
        TaskPriority.LOW -> S.priorityLow
        TaskPriority.MEDIUM -> S.priorityMedium
        TaskPriority.HIGH -> S.priorityHigh
    }

val RepeatRule.uiLabel: String
    get() = when (this) {
        RepeatRule.NONE -> S.repeatNone
        RepeatRule.DAILY -> S.repeatDaily
        RepeatRule.WEEKLY -> S.repeatWeekly
        RepeatRule.MONTHLY -> S.repeatMonthly
        RepeatRule.YEARLY -> S.repeatYearly
    }
