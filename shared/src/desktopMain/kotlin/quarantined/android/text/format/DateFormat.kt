
package android.text.format

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale

object DateFormat {

    @Suppress("UNUSED_PARAMETER")
    fun is24HourFormat(context: Context): Boolean = try {
        val df = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault())
        val pattern = (df as? SimpleDateFormat)?.toPattern().orEmpty()
        !pattern.contains('a', ignoreCase = true)
    } catch (t: Throwable) {
        true
    }
}
