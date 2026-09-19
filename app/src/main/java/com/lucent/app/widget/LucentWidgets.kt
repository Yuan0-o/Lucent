@file:Suppress("DEPRECATION")

package com.lucent.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lucent.app.AppScope
import com.lucent.app.R
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Checklist
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking



private const val WIDE_MIN_DP = 176
private const val MEDIUM_MIN_DP = 100

private fun sizeBucketLayout(options: Bundle?, small: Int, medium: Int, wide: Int): Int {
    val w = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
    val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
    return when {
        w >= WIDE_MIN_DP -> wide
        w >= MEDIUM_MIN_DP || h >= MEDIUM_MIN_DP -> medium
        else -> small
    }
}

abstract class ResponsiveActionWidget(
    private val small: Int,
    private val medium: Int,
    private val wide: Int,
    private val action: String
) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) render(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?
    ) {
        render(context, appWidgetManager, appWidgetId)
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val layout = sizeBucketLayout(manager.getAppWidgetOptions(id), small, medium, wide)
        val views = RemoteViews(context.packageName, layout)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetActions.pendingIntent(context, action))
        manager.updateAppWidget(id, views)
    }
}

class NewNoteWidget : ResponsiveActionWidget(
    R.layout.widget_new_note_small, R.layout.widget_new_note, R.layout.widget_new_note_wide,
    WidgetActions.NEW_NOTE
)

class NewTaskWidget : ResponsiveActionWidget(
    R.layout.widget_new_task_small, R.layout.widget_new_task, R.layout.widget_new_task_wide,
    WidgetActions.NEW_TASK
)

class AssistantWidget : ResponsiveActionWidget(
    R.layout.widget_assistant_small, R.layout.widget_assistant, R.layout.widget_assistant_wide,
    WidgetActions.ASK
)

class QuickActionsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)
            views.setOnClickPendingIntent(R.id.cell_note, WidgetActions.pendingIntent(context, WidgetActions.NEW_NOTE))
            views.setOnClickPendingIntent(R.id.cell_task, WidgetActions.pendingIntent(context, WidgetActions.NEW_TASK))
            views.setOnClickPendingIntent(R.id.cell_ask, WidgetActions.pendingIntent(context, WidgetActions.ASK))
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

class TaskSummaryWidget : AppWidgetProvider() {

    private data class Progress(val done: Int, val total: Int, val next: String?)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        renderAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle?
    ) {
        renderAll(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    private fun renderAll(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        AppScope.io.launch {
            val progress = try {
                val tasks = AppDatabase.getInstance(appContext).taskDao().getAllOnce()
                    .filter { it.trashedAt == null }
                val done = tasks.count { it.isDone }
                val open = tasks.filter { !it.isDone }
                val next = (open.filter { it.dueAt != null }.minByOrNull { it.dueAt!! }
                    ?: open.maxByOrNull { it.createdAt })
                    ?.title?.ifBlank { appContext.getString(R.string.widget_untitled_task) }
                Progress(done, tasks.size, next)
            } catch (t: Throwable) {
                null
            }
            try {
                for (id in appWidgetIds) {
                    val layout = sizeBucketLayout(
                        appWidgetManager.getAppWidgetOptions(id),
                        R.layout.widget_task_summary_small,
                        R.layout.widget_task_summary,
                        R.layout.widget_task_summary_wide
                    )
                    val views = RemoteViews(appContext.packageName, layout)
                    if (progress == null) {
                        views.setTextViewText(R.id.progress_fraction, "—")
                        views.setProgressBar(R.id.progress_bar, 100, 0, false)
                    } else {
                        views.setTextViewText(
                            R.id.progress_fraction,
                            appContext.getString(R.string.widget_fraction_fmt, progress.done, progress.total)
                        )
                        val pct = if (progress.total == 0) 0 else (progress.done * 100 / progress.total)
                        views.setProgressBar(R.id.progress_bar, 100, pct, false)
                        if (layout == R.layout.widget_task_summary_wide) {
                            val nextLine = when {
                                progress.total > 0 && progress.done == progress.total ->
                                    appContext.getString(R.string.widget_all_done)
                                progress.next != null ->
                                    appContext.getString(R.string.widget_next_fmt, progress.next)
                                else -> appContext.getString(R.string.widget_today_empty)
                            }
                            views.setTextViewText(R.id.progress_next, nextLine)
                        }
                    }
                    views.setOnClickPendingIntent(R.id.widget_root, WidgetActions.pendingIntent(appContext, WidgetActions.OPEN_TASKS))
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}


class TodayTasksWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_today_tasks).apply {
                setOnClickPendingIntent(R.id.widget_today_add, WidgetActions.pendingIntent(context, WidgetActions.NEW_TASK))
                setOnClickPendingIntent(R.id.widget_today_title, WidgetActions.pendingIntent(context, WidgetActions.OPEN_TASKS))

                val serviceIntent = Intent(context, TodayTasksWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    data = Uri.parse("lucent://widget/today/" + widgetId)
                }
                setRemoteAdapter(R.id.widget_today_list, serviceIntent)
                setEmptyView(R.id.widget_today_list, R.id.widget_today_empty)

                setPendingIntentTemplate(R.id.widget_today_list, WidgetActions.taskListTemplate(context))
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_today_list)
    }
}

class TodayTasksWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TodayTasksFactory(applicationContext)
}

private class TodayTasksFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private data class Row(val id: Long, val title: String, val subtitle: String, val isDone: Boolean)

    private var rows: List<Row> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        rows = try {
            runBlocking {
                AppDatabase.getInstance(context).taskDao().getAllOnce()
                    .filter { it.trashedAt == null }
                    .sortedWith(compareBy({ it.isDone }, { it.dueAt ?: Long.MAX_VALUE }, { -it.createdAt }))
                    .take(MAX_ROWS)
                    .map { task ->
                        val progress = Checklist.progress(task.subtasks)
                        val subtitle = when {
                            progress != null -> progress.first.toString() + "/" + progress.second + " subtasks"
                            task.notes.isNotBlank() -> task.notes.lineSequence().firstOrNull()?.trim().orEmpty()
                            else -> ""
                        }
                        Row(task.id, task.title.ifBlank { context.getString(R.string.widget_untitled_task) }, subtitle, task.isDone)
                    }
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    override fun onDestroy() { rows = emptyList() }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_today_tasks_item)
        return RemoteViews(context.packageName, R.layout.widget_today_tasks_item).apply {
            val title: CharSequence = if (row.isDone) {
                android.text.SpannableString(row.title).apply {
                    setSpan(
                        android.text.style.StrikethroughSpan(), 0, length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else row.title
            setTextViewText(R.id.widget_task_item_title, title)
            setTextColor(R.id.widget_task_item_title, if (row.isDone) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt())
            setImageViewResource(
                R.id.widget_task_item_check,
                if (row.isDone) R.drawable.ic_widget_check_on else R.drawable.ic_widget_check_off
            )
            setContentDescription(
                R.id.widget_task_item_check,
                context.getString(if (row.isDone) R.string.widget_a11y_reopen else R.string.widget_a11y_mark_done)
            )
            if (row.subtitle.isBlank()) {
                setViewVisibility(R.id.widget_task_item_subtitle, android.view.View.GONE)
            } else {
                setViewVisibility(R.id.widget_task_item_subtitle, android.view.View.VISIBLE)
                setTextViewText(R.id.widget_task_item_subtitle, row.subtitle)
            }
            val openFillIn = Intent().apply {
                putExtra(WidgetActions.EXTRA_ACTION, WidgetActions.OPEN_TASK_ITEM)
                putExtra(WidgetActions.EXTRA_ID, row.id)
            }
            setOnClickFillInIntent(R.id.widget_task_item_root, openFillIn)
            val toggleFillIn = Intent().apply {
                putExtra(WidgetActions.EXTRA_ACTION, WidgetActions.TOGGLE_TASK_ITEM)
                putExtra(WidgetActions.EXTRA_ID, row.id)
            }
            setOnClickFillInIntent(R.id.widget_task_item_check_area, toggleFillIn)
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = rows.getOrNull(position)?.id ?: position.toLong()
    override fun hasStableIds(): Boolean = true

    companion object { private const val MAX_ROWS = 25 }
}

class PinnedNoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val note = try {
            runBlocking {
                AppDatabase.getInstance(context).noteDao().getAllOnce()
                    .filter { it.pinned && !it.archived && it.trashedAt == null }
                    .maxByOrNull { it.updatedAt }
            }
        } catch (t: Throwable) {
            null
        }

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_pinned_note).apply {
                if (note == null) {
                    setTextViewText(R.id.widget_pinned_title, context.getString(R.string.widget_no_pinned_note))
                    setViewVisibility(R.id.widget_pinned_body, android.view.View.GONE)
                    setOnClickPendingIntent(R.id.widget_pinned_root, WidgetActions.pendingIntent(context, WidgetActions.NEW_NOTE))
                } else {
                    val preview = if (note.isChecklist) {
                        Checklist.parse(note.checklist).take(3).joinToString("\n") { "• " + it.text }
                    } else {
                        note.body
                    }
                    setTextViewText(R.id.widget_pinned_title, note.title.ifBlank { context.getString(R.string.widget_untitled) })
                    if (preview.isBlank()) {
                        setViewVisibility(R.id.widget_pinned_body, android.view.View.GONE)
                    } else {
                        setViewVisibility(R.id.widget_pinned_body, android.view.View.VISIBLE)
                        setTextViewText(R.id.widget_pinned_body, preview)
                    }
                    setOnClickPendingIntent(
                        R.id.widget_pinned_root,
                        WidgetActions.itemPendingIntent(context, WidgetActions.OPEN_NOTE_ITEM, note.id)
                    )
                }
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

object WidgetUpdater {
    fun refreshContent(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val appContext = context.applicationContext

        val todayIds = manager.getAppWidgetIds(ComponentName(appContext, TodayTasksWidget::class.java))
        if (todayIds.isNotEmpty()) {
            manager.notifyAppWidgetViewDataChanged(todayIds, R.id.widget_today_list)
            appContext.sendBroadcast(
                Intent(appContext, TodayTasksWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, todayIds)
                }
            )
        }

        val summaryIds = manager.getAppWidgetIds(ComponentName(appContext, TaskSummaryWidget::class.java))
        if (summaryIds.isNotEmpty()) {
            appContext.sendBroadcast(
                Intent(appContext, TaskSummaryWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, summaryIds)
                }
            )
        }

        val pinnedIds = manager.getAppWidgetIds(ComponentName(appContext, PinnedNoteWidget::class.java))
        if (pinnedIds.isNotEmpty()) {
            appContext.sendBroadcast(
                Intent(appContext, PinnedNoteWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, pinnedIds)
                }
            )
        }
    }
}
