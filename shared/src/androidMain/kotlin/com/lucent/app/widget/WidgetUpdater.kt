package com.lucent.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.lucent.app.platform.PlatformContext

object WidgetUpdater {
    fun refreshContent(context: PlatformContext) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val appContext = context.applicationContext

        val todayComponent = ComponentName(appContext, "com.lucent.app.widget.TodayTasksWidget")
        val todayIds = manager.getAppWidgetIds(todayComponent)
        if (todayIds.isNotEmpty()) {
            val listId = appContext.resources.getIdentifier("widget_today_list", "id", appContext.packageName)
            if (listId != 0) {
                todayIds.forEach { manager.notifyAppWidgetViewDataChanged(it, listId) }
            }
            appContext.sendBroadcast(
                Intent().apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    component = todayComponent
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, todayIds)
                }
            )
        }

        val summaryComponent = ComponentName(appContext, "com.lucent.app.widget.TaskSummaryWidget")
        val summaryIds = manager.getAppWidgetIds(summaryComponent)
        if (summaryIds.isNotEmpty()) {
            appContext.sendBroadcast(
                Intent().apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    component = summaryComponent
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, summaryIds)
                }
            )
        }

        val pinnedComponent = ComponentName(appContext, "com.lucent.app.widget.PinnedNoteWidget")
        val pinnedIds = manager.getAppWidgetIds(pinnedComponent)
        if (pinnedIds.isNotEmpty()) {
            appContext.sendBroadcast(
                Intent().apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    component = pinnedComponent
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, pinnedIds)
                }
            )
        }
    }
}
