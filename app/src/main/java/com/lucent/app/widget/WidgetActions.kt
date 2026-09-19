package com.lucent.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lucent.app.MainActivity

object WidgetActions {

    const val EXTRA_ACTION = "com.lucent.app.widget.ACTION"

    const val NEW_NOTE = "new_note"
    const val NEW_TASK = "new_task"
    const val ASK = "ask"
    const val OPEN_TASKS = "open_tasks"

    const val OPEN_TASK_ITEM = "open_task_item"
    const val OPEN_NOTE_ITEM = "open_note_item"

    const val TOGGLE_TASK_ITEM = "toggle_task_item"

    const val EXTRA_ID = "com.lucent.app.widget.EXTRA_ID"

    fun pendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = Intent.ACTION_MAIN
            putExtra(EXTRA_ACTION, action)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, action.hashCode(), intent, flags)
    }

    fun itemPendingIntent(context: Context, action: String, id: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = Intent.ACTION_MAIN
            putExtra(EXTRA_ACTION, action)
            putExtra(EXTRA_ID, id)
            data = android.net.Uri.parse("lucent://widget/$action/$id")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, (action + id).hashCode(), intent, flags)
    }

    fun taskListTemplate(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getActivity(context, "task_list_template".hashCode(), intent, flags)
    }
}
