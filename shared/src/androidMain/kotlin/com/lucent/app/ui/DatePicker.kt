package com.lucent.app.ui

import android.app.DatePickerDialog
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
    val startBase = Calendar.getInstance().apply { if (currentStart != null) timeInMillis = currentStart }
    DatePickerDialog(
        context,
        { _, sYear, sMonth, sDay ->
            val startMillis = startOfDayMillis(sYear, sMonth, sDay)
            val endBase = Calendar.getInstance().apply {
                timeInMillis = if (currentEnd != null && currentEnd >= startMillis) currentEnd else startMillis
            }
            DatePickerDialog(
                context,
                { _, eYear, eMonth, eDay ->
                    val endMillis = startOfDayMillis(eYear, eMonth, eDay)
                    onPicked(startMillis, maxOf(startMillis, endMillis))
                },
                endBase.get(Calendar.YEAR),
                endBase.get(Calendar.MONTH),
                endBase.get(Calendar.DAY_OF_MONTH)
            ).apply { datePicker.minDate = startMillis }.show()
        },
        startBase.get(Calendar.YEAR),
        startBase.get(Calendar.MONTH),
        startBase.get(Calendar.DAY_OF_MONTH)
    ).show()
}
