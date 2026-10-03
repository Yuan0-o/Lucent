
package com.lucent.desktop.platform

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lucent.app.i18n.S
import java.util.Calendar
import java.util.TimeZone

private const val DAY_MS = 24L * 60L * 60L * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LucentDatePickerDialog(
    initialDateMillis: Long,
    minMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val selectable = remember(minMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis + DAY_MS >= minMillis
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis,
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let(onConfirm) ?: onDismiss() }) {
                Text(S.actionOk)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.actionCancel) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LucentTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(S.actionOk) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.actionCancel) } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun LucentDateTimePickerFlow(
    initialMillis: Long,
    minMillis: Long,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var pickingTime by remember { mutableStateOf(false) }
    var chosenDayMillis by remember { mutableStateOf(initialMillis) }

    if (!pickingTime) {
        LucentDatePickerDialog(
            initialDateMillis = initialMillis,
            minMillis = minMillis,
            onDismiss = onDismiss,
            onConfirm = { dayMillis ->
                chosenDayMillis = dayMillis
                pickingTime = true
            },
        )
    } else {
        val base = remember { Calendar.getInstance().apply { timeInMillis = initialMillis } }
        LucentTimePickerDialog(
            initialHour = base.get(Calendar.HOUR_OF_DAY),
            initialMinute = base.get(Calendar.MINUTE),
            is24Hour = is24Hour,
            onDismiss = onDismiss,
            onConfirm = { hour, minute ->
                val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = chosenDayMillis
                }
                val local = Calendar.getInstance().apply {
                    clear()
                    set(Calendar.YEAR, utc.get(Calendar.YEAR))
                    set(Calendar.MONTH, utc.get(Calendar.MONTH))
                    set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                }
                onConfirm(local.timeInMillis.coerceAtLeast(minMillis))
            },
        )
    }
}
