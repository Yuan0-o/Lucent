package com.lucent.app.ui

import com.lucent.app.platform.PlatformContext
import java.util.Calendar

private fun startOfDayMillis(year: Int, month: Int, day: Int): Long =
    Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

actual fun showDateRangePicker(context: PlatformContext, currentStart: Long?, currentEnd: Long?, onPicked: (Long, Long) -> Unit) {
    DesktopDatePicker.open(currentStart, currentEnd) { start, end ->
        onPicked(start, maxOf(start, end))
    }
}

object DesktopDatePicker {
    data class Request(
        val initialStart: Long?,
        val initialEnd: Long?,
        val onPicked: (Long, Long) -> Unit
    )

    val request = kotlinx.coroutines.flow.MutableStateFlow<Request?>(null)

    fun open(initialStart: Long?, initialEnd: Long?, onPicked: (Long, Long) -> Unit) {
        request.value = Request(initialStart, initialEnd, onPicked)
    }

    fun dismiss() { request.value = null }

    fun startOfDay(year: Int, month: Int, day: Int): Long = startOfDayMillis(year, month, day)
}
